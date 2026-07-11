# benchmark(벤치마크) 기능

투자자산의 원금·현재금액을 월별로 기록하고, 같은 시점의 KOSPI/S&P500/나스닥 대비 수익률을 한 표에서
비교한다. 데이터는 고정된 원본 구글시트(`JK-APP raw`)를 앱이 직접 읽어와 미리보기로 확인한 뒤 저장한다.

## 비즈니스 규칙

- **날짜가 곧 문서 ID(upsert)**: 저장 시 문서 ID로 `date`를 쓰므로 같은 날짜를 다시 저장하면 덮어쓴다.
  강제 위치 `BenchmarkFirestoreRepositoryImpl.upsertBenchmark`/`upsertBenchmarks`.
- **숫자는 문자열로 저장**: 모든 금액·지수는 `BigDecimal.toPlainString()` 문자열로 저장하고 읽을 때
  `toBigDecimalOrNull()`로 복원한다. 강제 위치 `BenchmarkFirestoreRepositoryImpl.toMap`/`toBenchmark`.
- **손상 문서 스킵(읽기)**: 숫자 필드가 하나라도 없거나 파싱 실패면 그 문서를 목록에서 제외하고 로그만 남긴다.
  강제 위치 `BenchmarkFirestoreRepositoryImpl.toBenchmark`.
- **전체 삭제는 컬렉션 직접 조회**: `getBenchmarks()`의 필터링된 목록이 아니라 컬렉션을 직접 조회해
  삭제 대상을 정하므로, 표에 안 보이는 손상 문서도 함께 지운다. 강제 위치 `deleteAllBenchmarks`.
- **일괄 쓰기는 원자적 배치**: 여러 날짜 저장/삭제는 Firestore 배치로 묶어 전체 성공/실패로만 귀결된다.
  강제 위치 `upsertBenchmarks`/`deleteBenchmarks`.
- **쓰기 실패는 표를 덮지 않음**: 저장/삭제 실패는 `_uiState`가 아니라 별도 `actionError`로 알린다.
  스냅샷 리스너는 데이터가 실제로 바뀔 때만 재발행되므로 쓰기 실패로 화면을 Error로 덮으면 표가 사라진 채
  고착되기 때문. 강제 위치 `BenchmarkViewModel.saveBenchmark`/`deleteBenchmark` 등.
- **시트는 읽기 전용 직접 연동**: 사용자 붙여넣기 대신 고정된 원본 시트를 Google Sheets API로 직접
  읽는다. 읽기 전용 scope(`SPREADSHEETS_READONLY`)만 사용한다. 강제 위치 `BenchmarkSheetRepositoryImpl`. → ADR/73
- **시트 접근 동의 복구**: 계정 미선택 또는 권한 미동의 시 `BenchmarkSheetAuthException`을 던지고,
  ViewModel이 복구 인텐트를 UI에 노출한다(Drive 인증 패턴과 동일). 강제 위치 `readBenchmarkRows`, `importFromSheet`.
- **시트 가져오기 타임아웃**: 시트 응답이 30초를 넘으면(`SHEET_IMPORT_TIMEOUT_MS`) 로딩에 갇히지 않도록
  중단하고 안내한다. 강제 위치 `BenchmarkViewModel.importFromSheet`.
- **미래 날짜 제외 최신값**: "투자자산" 배너용 최신 금액은 오늘 이하 날짜 중 가장 최신 항목의 `currentAmount`다.
  강제 위치 `BenchmarkViewModel.latestCurrentAmount`(`common.latestNotFuture`).

## 계산 / 파생 값

모든 계산은 조회 시 수행하며 저장하지 않는다. 계산 위치는 `Benchmark.kt`(`withRowMetrics`,
`computeIndexMetrics`, `percentChange`)이고, `BenchmarkViewModel.rowMetrics`가 데이터가 바뀔 때만
한 번 계산해 캐시한 뒤 최신 날짜부터 보이도록 `reversed()`한다.

- `percentChange(from, to) = (to − from) / from × 100` — `from`이 0이면 `null`. `to−from/from`은 소수 4자리,
  최종 결과는 소수 2자리 반올림(`HALF_UP`).
- **누적 원금** `principal` — 날짜 오름차순으로 `additionalInvestment`를 누적한 합.
- **수익금** `profit = currentAmount − principal`.
- **자산 수익률** `returnRatePercent = percentChange(principal, currentAmount)` — 원금 0이면 `null`.
- **수익률 변화** `returnRateChangePercent = 이번 수익률 − 직전 수익률` (%p) — 둘 중 하나라도 없으면 `null`.
- **자산 MDD** `assetMdd = 이번 수익률 − 지금까지의 수익률 고점` (%p, 0 이하).
- **지수 지표(`IndexMetrics`, KOSPI/S&P500/나스닥 각각)**:
  - `returnRatePercent = percentChange(첫 값, 현재 값)` — 최초 항목은 0.
  - `changePercent = percentChange(직전 값, 현재 값)` — 직전 항목 없으면 `null`.
  - `mdd = percentChange(지금까지의 고점, 현재 값)` — 고점 0이면 `null`.

## 유효성 검증

시트 파싱(`parseBenchmarkRows`/`parseBenchmarkRow`, `BenchmarkSheetPaste.kt`) 규칙:

- 비어 있지 않은 행이 2개(헤더+데이터 1행) 미만이면 빈 결과.
- 필요한 6개 열(날짜·추가투자·현재금액·KOSPI·S&P500·나스닥)을 **고정 위치가 아니라 헤더 이름**으로 찾는다.
  헤더는 공백 제거+대문자로 정규화해 비교하고, `현재금액`은 `계`, `S&P500`은 `SNP500`을 별칭으로 허용한다.
- 하나라도 못 찾은 열이 있으면 모든 데이터 행을 "다음 열을 찾을 수 없습니다" 오류로 표시.
- 날짜가 `yyyy-MM-dd`(`ISO_DATE_REGEX`)에 맞지 않으면 오류.
- 금액 셀은 숫자와 소수점만 추출해 재조합하고, `-`가 있으면 음수로 본다(출금 보존). 값이 비면
  `추가투자`는 0으로 취급하고 나머지 필수 필드는 "값이 비어 있습니다" 오류. 글자가 섞여 숫자로 못 바꾸면 오류.
- **같은 입력 내 날짜 중복**은 어느 값이 맞는지 알 수 없으므로 해당 날짜 행들을 모두 오류로 표시.

## 주요 플로우

1. **목록 조회**: `getBenchmarks()`를 구독 → `BenchmarkUiState.Success` → `rowMetrics`가 파생 지표를
   계산(최신 날짜 우선)해 `BenchmarkTab`(`finance/FinanceScreen.kt`)이 표로 표시.
2. **시트에서 가져오기**: 사용자가 버튼 클릭 → `BenchmarkViewModel.importFromSheet` →
   `BenchmarkSheetRepository.readBenchmarkRows`(계정 세팅·페이지 100행 단위 읽기) →
   `parseBenchmarkRows`로 파싱 → `BenchmarkSheetImportState.Preview`(성공/오류 혼재) 표시 →
   `confirmSheetImport` → `importBenchmarks` → `upsertBenchmarks`(원자적 배치 저장).
   접근 동의 필요 시 복구 인텐트 노출 → 동의 후 `onSheetAccountSelected`로 재시도.
3. **단건 저장**: `saveBenchmark(benchmark)` → `upsertBenchmark`(문서 ID=`date`).
4. **삭제**: 단건 `deleteBenchmark(date)` / 선택 `deleteBenchmarks(dates)`(배치) / 전체
   `deleteAllBenchmarks`(손상 문서 포함). 실패는 `actionError`로 안내.

## 관련 결정 (ADR)

- [`doc/adr/73/google-sheets-api-readonly.md`](../../../adr/73/google-sheets-api-readonly.md) — 벤치마크 시트 연동에
  Google Sheets API v4(읽기 전용) 사용, Drive 인증 스택 재사용
