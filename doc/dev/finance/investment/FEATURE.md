# investment(투자 종목) 기능

명의별(전지훈·권유경)로 특정 날짜의 투자 종목을 등록·수정·삭제하고, 필터 모달로 소유주·계좌·
카테고리·종목을 다중 선택해 필터링하며 종목별 수익금을 확인한다. 원본 구글시트를 앱이 직접 읽어
종목 목록을 한 번에 가져올 수도 있다.

포트폴리오 기능을 통해 그룹별 목표 비율과 실제 평가금액 비율을 파이 차트로 비교할 수 있다.

## 비즈니스 규칙

### 투자 종목 기본 규칙

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

### 필터 통합 규칙 (이슈 #91)

- **날짜 자동 결정**: 날짜 네비게이터와 명의 탭은 제거한다. 표시할 날짜는 오늘(실행 시점) 이하로
  저장된 전체 명의 데이터 중 가장 최신 날짜 1개를 ViewModel이 자동으로 선택한다. 강제 위치
  `DailyAssetInvestmentViewModel.latestDate`.
- **다중 선택 필터**: 소유주·계좌·카테고리·종목 4개 축을 필터 모달에서 다중 선택한다. 각 축은
  `Set<String>`으로 관리하며 빈 Set은 "전체(제한 없음)"를 의미한다. 강제 위치
  `InvestmentFilter`, `DailyAssetInvestmentViewModel._filter`.
- **필터 매칭 규칙(축 간 AND, 축 내 OR)**: 소유주∈선택된 소유주 AND 계좌∈선택된 계좌 AND
  카테고리∈선택된 카테고리 AND 종목명∈선택된 종목명. 지정되지 않은(빈 Set) 축은 필터하지 않음.
  강제 위치 `InvestmentFilter.matches`.
- **필터 선택지**: 필터 모달에 노출하는 선택지는 필터링 전(latestDate 기준 전체 데이터)을 기준으로
  계산한다. 이미 필터를 적용한 결과로 선택지가 줄어들지 않도록 하기 위함.

### 포트폴리오 비즈니스 규칙 (이슈 #91)

- **그룹 매칭(축 간 AND, 축 내 OR)**: 한 `InvestmentItem`이 `PortfolioGroup`에 속하려면
  소유주·계좌·카테고리·종목명 모든 축에서 통과해야 한다. 각 축 안에서는 해당 값이 리스트에 포함되면
  통과(OR). `null` 또는 빈 리스트인 축은 제한 없음(전체 통과). 강제 위치
  `PortfolioGroupMatcher.matches`.
- **중복 합산 허용**: 한 종목이 여러 그룹 조건에 겹치면 각 그룹에 중복 합산된다.
- **미분류 종목 제외**: 어느 그룹에도 속하지 않는 종목은 파이 차트 계산에서 제외한다(합산 분모에서도
  제외). 강제 위치 `PortfolioGroupMatcher.computeGroupAmounts`.
- **목표 비율 합계 검증**: 포트폴리오 저장 시 모든 그룹의 `targetRatio` 합이 정확히 100이어야
  한다. 그렇지 않으면 저장을 차단한다. 강제 위치 `validatePortfolioGroups`.
- **목표 비율 타입**: 정수 `Int`(0~100). 소수점 없음. 강제 위치 `PortfolioGroup.targetRatio`.
- **파이 차트 계산**: 그룹별 평가금액 합계를 전체 분류된 종목 합계로 나눠 실제 비율을 계산하고,
  목표 비율과 비교한다. 강제 위치 `PortfolioGroupMatcher.computePieSlices`. 화면은 페이저의 각
  페이지(`PortfolioScreen.PortfolioDetail`)가 자신의 포트폴리오로 슬라이스를 계산한다.

### 포트폴리오·그룹 순서 규칙

- **순서는 사용자가 직접 입력하지 않는다**: `Portfolio.order`·`PortfolioGroup.order`는 화면
  조작(재정렬)으로만 바뀌며, 폼에 순서 입력 필드는 없다.
- **하위 호환(nullsFirst)**: 기존 데이터의 `order`는 `null`이다. 조회 시 `order`가 `null`인
  항목이 앞, 그다음 오름차순으로 정렬한다(동률·null 다수는 안정 정렬로 기존 순서 유지). 강제 위치
  `List<Portfolio>.sortedByOrder`/`List<PortfolioGroup>.sortedByOrder`(`compareBy(nullsFirst())`),
  `PortfolioFirestoreRepositoryImpl.getPortfolios`.
- **포트폴리오 재정렬(드래그&드롭·부분 업데이트)**: 상단 포트폴리오 칩을 길게 눌러 드래그하면 순서가
  바뀐다. 드래그 중 다른 칩이 실시간으로 자리를 비켜(`animateItem`) 어디로 이동하는지 보이며, 손을
  떼면 그 순서가 저장된다. 재정렬 결과 목록에 `order = index`를 재부여하되 **값이 실제로 바뀌는
  문서만** `updatePortfolioOrders`로 batch 업데이트한다. 현재 전부 `null`이므로 첫 재정렬에서는 모든
  포트폴리오에 값이 들어가고, 이후에는 이동에 영향받은 문서만 갱신된다. 강제 위치
  `computePortfolioOrderUpdates`, `PortfolioViewModel.reorderPortfolios`, `PortfolioSelectorBar`,
  `PortfolioFirestoreRepository.updatePortfolioOrders`.
- **그룹 재정렬(저장 시 부여)**: 포트폴리오 추가·수정 다이얼로그에서 그룹을 길게 눌러 재정렬 모드로
  진입하고 ▲▼로 상하 이동한다. 그룹 순서는 저장 시점에 목록 위치대로 `order = index`가 부여되어
  포트폴리오 문서 전체(`upsertPortfolio`)와 함께 저장된다. 강제 위치 `PortfolioViewModel.savePortfolio`.

## 계산 / 파생 값

- `profit = valuationAmount - purchaseAmount.amount` — 종목별 수익금. 계산 위치
  `List<InvestmentItem>.withProfitMetrics` (`DailyAssetInvestment.kt`). ViewModel의
  `investmentRowMetrics`가 필터 적용 후 이를 계산해 StateFlow로 캐시한다(탭 재진입 시 재계산 방지).
- `groupedInvestmentRowMetrics` — `investmentRowMetrics`를 `assetName`으로 그룹화하고 계좌
  이름 오름차순 정렬(`toSortedMap`). 계산 위치 `DailyAssetInvestmentViewModel`.
- `latestDate` — 오늘 이하 전체 명의 데이터 중 가장 최신 날짜. 계산 위치
  `DailyAssetInvestmentViewModel`. `DailyAssetInvestmentUiState.Success.investments`에서
  `date <= todayDate()`인 것의 `maxOrNull()`로 파생.
- `latestOwnerItemPairs` — `latestDate` 기준 전체 명의 `(owner, InvestmentItem)` 쌍 목록
  (타입 `List<Pair<String, InvestmentItem>>`). 필터 선택지 계산의 원본. 계산 위치
  `DailyAssetInvestmentViewModel`.
- `latestDateTotalValuationAmount` — `latestDate` 기준 모든 명의 종목 `valuationAmount` 합계.
  벤치마크의 전체 포트폴리오 금액과 대응. 계산 위치 `DailyAssetInvestmentViewModel`.
- 그룹별 평가금액 합산 — `PortfolioGroupMatcher.computeGroupAmounts`가
  `(Portfolio, List<Pair<String, InvestmentItem>>)`를 받아 `Map<String, BigDecimal>`
  (그룹명 → 합계)로 반환한다. 파이 차트 슬라이스는 `PortfolioGroupMatcher.computePieSlices`가
  `List<PieSlice>`(그룹명·평가금액·실제 비율·목표 비율)로 반환한다.
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
- **포트폴리오 저장 유효성**: 그룹 `targetRatio` 합이 정확히 100이 아니면 저장 차단.
  강제 위치 `validatePortfolioGroups`.

## 주요 플로우

1. **목록 조회**: 로그인 상태 → `getDailyAssetInvestments` 구독 → `Success` 방출.
   `latestDate`(오늘 이하 전체 명의 최신 날짜)가 자동 결정되고, `latestOwnerItemPairs`로
   필터 적용 전 전체 `(owner, item)` 쌍 목록이 파생된다. 필터(`InvestmentFilter`) 적용으로
   `investmentRowMetrics`(= 필터를 통과한 종목의 수익금 계산)와
   `groupedInvestmentRowMetrics`(계좌별 그룹화, owner 포함)가 파생된다.
2. **필터 모달**: `InvestmentTab`의 필터 버튼 → `InvestmentFilterModal` 시트 →
   소유주/계좌/카테고리/종목명 다중 선택 → 확인 → `viewModel.applyFilter(filter)`.
   취소/백 → 선택 취소, 기존 필터 유지.
3. **개별 추가**: `InvestmentTab`의 폼 다이얼로그 → `addInvestment(date, owner, item)`
   → `mutateInvestments`가 뮤텍스 하에 현재 목록에 추가 후 upsert → 저장한 날짜로 화면 전환.
4. **수정/삭제**: 폼/삭제 UI → `updateInvestment`/`deleteInvestment`/`deleteInvestments`
   → `mutateInvestments`(내용 기준으로 대상 식별) → upsert 또는 (빈 목록이면) 문서 삭제.
5. **시트에서 가져오기**: 버튼 → `importFromSheet` → `Loading` → `readInvestmentBlocks`
   (30초 타임아웃) → 명의별 블록을 `parseInvestmentRows`로 파싱 → `Preview` 상태로 미리보기 표시
   (성공/오류 혼재). 접근 동의 필요 시 `InvestmentSheetAuthException`의 복구 인텐트를
   `sheetAuthRecoveryIntent`로 노출.
6. **가져오기 확정**: 미리보기 확인 → `confirmSheetImport(date)` → 명의 블록마다
   `importInvestments`로 저장. 같은 종목 키는 시세·수량·매수금액을 갱신(merge), 없으면 추가한다.
   저장 후 사용자가 보던 명의를 유지한 채 오늘 날짜로 전환.
7. **포트폴리오 화면 진입**: `InvestmentTab`의 '포트' 버튼 → `onNavigateToPortfolio()` 콜백
   → `PortfolioRoute` 전체 화면으로 전환.
8. **포트폴리오 조회/편집**: `PortfolioScreen` → 저장된 포트폴리오 목록(순서대로) → 상단 탭 선택
   또는 **좌우 스와이프**(`HorizontalPager`, 탭·선택 상태 동기화)로 포트폴리오 전환 → 그룹별 파이
   차트(실제 비율 vs 목표 비율) 표시 → 편집 다이얼로그 → `upsertPortfolio`. 파이 차트는 링 스트로크가
   잘리지 않도록 반지름을 스트로크 두께만큼 인셋해 상하가 온전히 보이게 그린다.
8-1. **포트폴리오 재정렬**: 상단 포트폴리오 칩을 길게 눌러 드래그 → 드롭 위치로 순서 변경(다른 칩이
   실시간으로 비켜남) → `reorderPortfolios(orderedIds)` → 값이 바뀐 문서만 `updatePortfolioOrders`로 저장.
9. **그룹 조건 입력**: `PortfolioGroupFormDialog`에서 소유주·계좌·카테고리·종목명을 `FilterChip`
   다중 선택으로 지정한다(`InvestmentFilterModal`과 동일 패턴). 소유주 옵션은 `INVESTMENT_OWNERS`
   고정 집합, 계좌·카테고리·종목명 옵션은 `PortfolioViewModel`이 `latestOwnerItemPairs`에서
   도출해 노출한다(`accountOptions`/`categoryOptions`/`stockNameOptions`). 카테고리·종목명은
   미선택 시 해당 축을 필터하지 않는 nullable 시맨틱을 유지한다(빈 선택 → `null` 저장).
9-1. **그룹 재정렬**: 포트폴리오 추가·수정 다이얼로그에서 그룹을 길게 눌러 재정렬 모드로 진입 →
   각 그룹의 ▲▼로 상하 이동(다이얼로그 로컬 목록 순서 변경) → 저장 시 목록 위치대로 그룹 `order`가
   부여된다.
10. **포트폴리오 저장**: 그룹 `targetRatio` 합이 100이 아니면 저장 버튼 비활성화.
    `PortfolioViewModel.savePortfolio`가 그룹에 `order = index`를 부여한 뒤
    `PortfolioFirestoreRepository.upsertPortfolio`로 저장한다.

## 관련 결정 (ADR)

- [`../../../adr/73/google-sheets-api-readonly.md`](../../../adr/73/google-sheets-api-readonly.md) —
  시트 직접 연동에 Google Sheets API v4(읽기 전용) 채택 및 Drive와 동일한 OAuth 인증 패턴 재사용.
