# investment(투자 종목) 기능

명의별(전지훈·권유경)로 특정 날짜의 투자 종목을 등록·수정·삭제하고, 계좌·카테고리로 필터링하며
종목별 수익금을 확인한다. 원본 구글시트를 앱이 직접 읽어 종목 목록을 한 번에 가져올 수도 있다.

## 비즈니스 규칙

- **명의별 문서 분리**: 데이터는 `{date}_{owner}` 문서 단위로 나뉜다. 같은 날짜라도 명의마다
  별도 문서이며, 명의별로 데이터가 있는 날짜가 다를 수 있다. 강제 위치
  `InvestmentFirestoreRepositoryImpl.investmentDocId`.
- **고정 명의 목록**: 명의는 `전지훈`, `권유경` 두 개로 고정이다(자산 관리와 달리 "공동"은 없다).
  강제 위치 `INVESTMENT_OWNERS`.
- **종목 논리 키 중복 금지**: 한 문서 안에서 `(assetName, category, investmentName)` 조합은
  유일해야 한다(중복 시 화면 LazyColumn key 충돌로 크래시). 추가·수정·시트 파싱 모두에서 강제한다.
  강제 위치 `addInvestment`/`updateInvestment`의 `isSameInvestmentKey` 검사, `parseInvestmentRows`의
  `duplicateKeys`.
- **빈 목록이면 문서 삭제**: 변경 결과 종목이 하나도 없으면 문서를 upsert하지 않고 삭제한다.
  "전체 삭제"는 현재 목록 전체를 넘겨 이 규칙을 이용한다. 강제 위치 `mutateInvestments`.
- **read-modify-write 직렬화**: add/update/delete를 `Mutex`로 순차 처리해, 동시 요청이 같은
  stale 스냅샷을 읽고 서로의 변경을 덮어쓰는 lost-update를 막는다. 강제 위치
  `investmentMutationMutex` + `mutateInvestments`.
- **upsert = 문서 전체 교체**: 저장은 문서를 통째로 `set`한다. 읽을 때 걸러진(손상된) 종목이 있으면
  다시 저장하는 순간 영구히 사라지므로, Repository는 걸러낼 때 반드시 로그를 남긴다. 강제 위치
  `upsertDailyAssetInvestment`, `toInvestmentItem`.
- **저장/삭제 실패는 목록을 지우지 않음**: 실패는 `uiState`를 `Error`로 덮지 않고 별도
  `actionError`로만 노출한다. 실패로 화면 목록이 사라져 고착되는 것을 막기 위함(이슈 #37 맥락).
  강제 위치 `_actionError`, `mutateInvestments.onFailure`.
- **시트 직접 연동(읽기 전용)**: 고정 원본 시트(`JK-APP raw`)의 명의별 열 블록(전지훈 `H:N`,
  권유경 `P:V`)을 Google Sheets API v4로 읽어온다. 앱은 쓰지 않으므로 `SPREADSHEETS_READONLY`
  scope만 사용한다. 강제 위치 `InvestmentSheetRepositoryImpl`, `readInvestmentBlocks`.
  → [`../../../adr/73/google-sheets-api-readonly.md`](../../../adr/73/google-sheets-api-readonly.md)
- **시트 헤더 이름 기반 열 인식**: 시트에는 파생 계산 열이 섞여 있고 순서도 달라질 수 있어, 고정 열
  위치가 아니라 헤더 행의 이름(+별칭)으로 필요한 7개 열만 찾는다. 강제 위치 `INVESTMENT_COLUMNS`,
  `parseInvestmentRows`.
- **"계" 행 제외**: 계좌(`assetName`)가 `계`인 행은 소계/합계이므로 종목으로 가져오지 않는다.
  강제 위치 `TOTAL_ROW_MARKER`, `parseInvestmentRows`의 `filteredRows`.

## 계산 / 파생 값

- `profit = valuationAmount - purchaseAmount.amount` — 종목별 수익금. 계산 위치
  `List<InvestmentItem>.withProfitMetrics` (`DailyAssetInvestment.kt`). ViewModel의
  `investmentRowMetrics`가 필터 적용 후 이를 계산해 StateFlow로 캐시한다(탭 재진입 시 재계산 방지).
- `groupedInvestmentRowMetrics` — `investmentRowMetrics`를 `assetName`으로 그룹화하고 계좌
  이름 오름차순 정렬(`toSortedMap`). 계산 위치 `DailyAssetInvestmentViewModel`.
- `selectedDateTotalValuationAmount` — 선택 날짜의 **모든 명의** 종목 `valuationAmount` 합계
  (`sumOf`). 명의별 `currentInvestment`와 달리 명의 구분이 없어, 벤치마크의 전체 포트폴리오
  금액과 대응된다. 계산 위치 `DailyAssetInvestmentViewModel`.
- 매수단가(파생) — `purchaseAmount / quantity`로 계산 가능하므로 저장하지 않는다(모델 주석 근거).

## 유효성 검증

- 추가 시 같은 종목 논리 키가 이미 있으면 거부 — "이미 같은 계좌·카테고리·투자종목 조합이 존재합니다".
- 수정 시 대상 종목을 찾지 못하면 거부 — "수정하려는 투자 종목을 찾을 수 없습니다(...)"; 다른 종목과
  키가 겹치면 위 중복 메시지로 거부.
- 삭제 시 대상 종목이 목록에 없으면 거부 — "삭제하려는 투자 종목을 찾을 수 없습니다(...)".
- 시트 파싱: 헤더에서 7개 열을 못 찾으면 "헤더에서 다음 열을 찾을 수 없습니다: ..."; 계좌·카테고리·
  투자 종목이 비면 "OO 값이 비어 있습니다"; 금액 열이 비면 "OO 값이 비어 있습니다", 숫자 변환 실패 시
  "OO 값을 숫자로 변환할 수 없습니다: ..."; 같은 입력 내 종목 키 중복 시 "같은 입력 안에
  계좌·카테고리·투자종목이 중복되었습니다". 검증 위치 `parseInvestmentRow`, `parseInvestmentRows`.
- 금액 파싱: 통화 기호·콤마·공백을 무시하고 숫자와 소수점만 추출한다. `-` 포함 시 음수, `$` 포함 시
  해당 셀을 USD로 인식(매수금액이 USD면 `purchaseAmountCurrency = "USD"`). 위치
  `parseInvestmentAmountCell`.

## 주요 플로우

1. **목록 조회**: 로그인 상태 → `getDailyAssetInvestments` 구독 → `Success` 방출. 명의 탭·날짜
   네비게이터 선택에 따라 `availableDates`/`currentInvestment`가 파생되고, 선택 날짜가 현재 명의
   목록에 없으면 그 명의의 최신 날짜로 자동 보정한다(`init`의 `availableDates.collect`).
2. **개별 추가**: `InvestmentTab`(`FinanceScreen.kt`)의 폼 다이얼로그 → `addInvestment(date, owner, item)`
   → `mutateInvestments`가 뮤텍스 하에 현재 목록에 추가 후 upsert → 저장한 날짜로 화면 전환.
3. **수정/삭제**: 폼/삭제 UI → `updateInvestment`/`deleteInvestment`/`deleteInvestments`
   → `mutateInvestments`(내용 기준으로 대상 식별) → upsert 또는 (빈 목록이면) 문서 삭제.
4. **시트에서 가져오기**: 버튼 → `importFromSheet` → `Loading` → `readInvestmentBlocks`
   (30초 타임아웃) → 명의별 블록을 `parseInvestmentRows`로 파싱 → `Preview` 상태로 미리보기 표시
   (성공/오류 혼재). 접근 동의 필요 시 `InvestmentSheetAuthException`의 복구 인텐트를
   `sheetAuthRecoveryIntent`로 노출.
5. **가져오기 확정**: 미리보기 확인 → `confirmSheetImport(date)` → 명의 블록마다
   `importInvestments`로 저장. 같은 종목 키는 시세·수량·매수금액을 갱신(merge), 없으면 추가한다.
   저장 후 사용자가 보던 명의를 유지한 채 오늘 날짜로 전환.

## 관련 결정 (ADR)

- [`../../../adr/73/google-sheets-api-readonly.md`](../../../adr/73/google-sheets-api-readonly.md) —
  시트 직접 연동에 Google Sheets API v4(읽기 전용) 채택 및 Drive와 동일한 OAuth 인증 패턴 재사용.
