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
- **자산 기간수익률** `rₜ = (오늘 currentAmount − 직전 currentAmount − 오늘 additionalInvestment) / 직전 currentAmount × 100`
  — TWR(시간가중수익률) 계산의 기본 단위. 직전 `currentAmount`가 0이면 `rₜ = 0`으로 간주(성과지수 연속성 유지).
  **최초 행은 직전값 없으므로 `rₜ` 미정의 → `returnRateChangePercent`·`assetMdd` 모두 `null`.**
- **자산 상승률** `returnRateChangePercent = rₜ` — 직전 행이 없으면(최초 행) `null`.
  추가투자만 있고 시장 변동 없는 날(`currentAmount = 직전 currentAmount + additionalInvestment`)은 정확히 `0.00%`.
- **TWR 성과지수** `Iₜ = I₀ × ∏(1 + rₜ / 100)` — I₀ = 1. 내부 계산 변수이며 공개 필드로 노출하지 않는다.
  각 기간수익률 rₜ는 소수 2자리로 반올림한 값을 팩터(`1 + rₜ/100`)로 사용하며(화면 표시 rₜ와 성과지수·MDD가
  같은 값 기반이라 사용자 검산 가능), 성과지수 누적 곱 연산 자체는 중간 반올림 없이 `BigDecimal` 고정밀도로 수행한다.
- **자산 MDD** `assetMdd = (Iₜ − 지금까지 Iₜ 고점) / 고점 × 100` (%, 0 이하). 최초 행은 `null`.
  최종 결과만 소수 2자리 `HALF_UP` 반올림. 기존 %p 뺄셈 방식이 아닌 % 나눗셈으로 계산한다.
- **지수 지표(`IndexMetrics`, KOSPI/S&P500/나스닥 각각)** — `computeIndexMetrics` 함수 담당, 변경 금지:
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
5. **다중선택 삭제 공통화(이슈 #88)**: 선택 모드의 상태 관리(`isSelectionMode`, `selectedDates`)와
   하단 "선택 삭제"·"취소" 바는 `com.jkapp.common.MultiDeleteState`·`MultiDeleteBar`로 대체한다.
   '전체 삭제' 버튼은 벤치마크 화면 고유로 남긴다. 강제 위치 `BenchmarkTab`(`FinanceScreen.kt`).
6. **차트 보기(이슈 #98)**: 벤치마크 탭 우하단 FAB 그룹의 차트 버튼 클릭 → 수익률/MDD 선택 드롭다운 →
   `BenchmarkChartRoute(chartType)` 라우트로 전체화면 진입.
   - **기간 필터**: 프리셋 4종(최근 3개월/6개월/1년/전체). 기준일은 **데이터 최신 날짜**에서 역산한다
     (오늘 날짜가 아님). `BenchmarkChartUtils.filterByPeriod` 순수 함수로 슬라이싱.
   - **x축 방향**: 슬라이싱 결과를 `asReversed()`해 **최신 날짜가 왼쪽, 과거가 오른쪽**으로 표시한다
     (`filterByPeriod`는 오름차순 반환이므로 화면 계층에서 뒤집는다).
   - **수익률 차트(이중 축)**: 막대(`Benchmark.currentAmount`, 오른쪽 Y축, K/M/G 단위 축약) +
     선 4종(자산 `BenchmarkRowMetrics.returnRatePercent`, KOSPI/S&P500/나스닥 `IndexMetrics.returnRatePercent`,
     왼쪽 Y축 %). 차트는 `BenchmarkViewModel.rowMetrics`를 기간 필터로 슬라이싱해 소비하며
     **새 계산 없음**. null 값(최초 행 `returnRatePercent` 등)은 해당 포인트를 0으로 대체.
   - **좌·우 세로축 눈금 정렬**: 두 축 모두 눈금 8개(`AXIS_TICK_COUNT`)로 맞춰 가로 격자선을 정렬한다.
     - 왼쪽(수익률 %): 최댓값을 50% 단위로 올림(`percentAxisMax`, 최소 50), 손실이 있으면 50% 단위로 내림한
       값을 바닥으로(`percentAxisMin`, 없으면 0).
     - 오른쪽(현재금액): 0을 바닥으로, 7구간 나눗셈이 1/2/5×10ⁿ 형태가 되도록 상단을 올림(`amountAxisMax`).
       상단은 항상 데이터 최댓값 이상이라 막대가 최상단 눈금을 넘지 않는다.
     - 범위는 Vico `CartesianLayerRangeProvider.fixed`, 눈금 개수는 `VerticalAxis.ItemPlacer.count`로 고정.
   - **MDD 차트(영역 4종)**: 자산 `assetMdd`, KOSPI/S&P500/나스닥 `IndexMetrics.mdd`.
     범례 표기는 `자산`/`KOSPI`/`S&P500`/`나스닥`(MDD 접미어 생략). null 값(최초 행 `assetMdd`,
     고점 0인 지수 `mdd`)은 해당 포인트를 0으로 대체.
   - **K/M/G 단위 축약**: 오른쪽 Y축 금액 레이블에 적용. `BenchmarkChartUtils.formatAmount` 순수 함수.
     1,000 미만 → 그대로, 1,000 이상 → K, 1,000,000 이상 → M, 1,000,000,000 이상 → G.
   - **가로모드**: 차트 화면 진입 시 landscape 자동 고정, 뒤로가기 시 원래 방향 복원
     (`DisposableEffect` + `activity.requestedOrientation`).
   - 차트 라이브러리: Vico 3.2.3 (`compose-m3`). 결정 근거 → ADR #98.

## 관련 결정 (ADR)

- [`doc/adr/73/google-sheets-api-readonly.md`](../../../adr/73/google-sheets-api-readonly.md) — 벤치마크 시트 연동에
  Google Sheets API v4(읽기 전용) 사용, Drive 인증 스택 재사용
- [`doc/adr/98/vico-chart-library.md`](../../../adr/98/vico-chart-library.md) — 벤치마크 차트에
  Vico 3.x(`compose-m3`) 도입, 이중 Y축 및 영역 차트 지원
