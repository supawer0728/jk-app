# investment(투자 종목) 도메인

명의별로 특정 날짜의 투자 종목 보유 현황을 관리하는 도메인. 하나의 문서(`DailyAssetInvestment`)는
`날짜 + 명의` 단위로 그 시점의 투자 종목 목록(`InvestmentItem`)을 담으며, 각 종목은 1주 가격·
평가금액·보유수량·매수금액(통화 포함)을 갖는다.

포트폴리오(`Portfolio`) 도메인이 함께 속한다. 포트폴리오는 N개의 그룹(`PortfolioGroup`)으로
구성되며, 각 그룹은 소유주·계좌·카테고리·종목 조건으로 종목을 필터링하고 목표 비율을 정의한다.
파이 차트를 통해 그룹별 평가금액 비율 대비 목표 비율을 시각화한다.

## 도메인 모델

### DailyAssetInvestment

`app/src/main/java/com/jkapp/finance/investment/DailyAssetInvestment.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `firestoreId` | `String?` | Firestore 문서 ID(`{date}_{owner}`). 신규 생성 전에는 `null` |
| `date` | `String` | 기준 날짜. ISO_LOCAL_DATE(`yyyy-MM-dd`) 문자열 |
| `owner` | `String` | 명의(투자자). `전지훈` / `권유경` 중 하나 (`INVESTMENT_OWNERS`) |
| `investments` | `List<InvestmentItem>` | 해당 날짜·명의의 투자 종목 목록. 기본값 `emptyList()` |

### InvestmentItem

`app/src/main/java/com/jkapp/finance/investment/DailyAssetInvestment.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `assetName` | `String` | 계좌(자산 이름). 화면·필터에서 그룹 기준으로 쓰인다 |
| `category` | `String` | 카테고리 |
| `investmentName` | `String` | 투자 종목명 |
| `pricePerShare` | `BigDecimal` | 1주 가격 |
| `valuationAmount` | `BigDecimal` | 평가금액(원화) |
| `quantity` | `BigDecimal` | 보유수량 |
| `purchaseAmount` | `CurrencyAmount` | 매수금액(통화 + 금액). 매수단가는 저장하지 않고 `purchaseAmount / quantity`로 파생 |

> `(assetName, category, investmentName)` 세 값의 조합이 한 명의·날짜 문서 안에서 종목을
> 식별하는 논리 키다(중복 불가). 매수단가는 별도 저장하지 않는다.

### CurrencyAmount (값 객체)

`app/src/main/java/com/jkapp/finance/investment/DailyAssetInvestment.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `currency` | `String` | 통화. `KRW` 또는 `USD` |
| `amount` | `BigDecimal` | 금액 |

### InvestmentItemMetrics (파생 값 객체)

`app/src/main/java/com/jkapp/finance/investment/DailyAssetInvestment.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `item` | `InvestmentItem` | 원본 종목 |
| `profit` | `BigDecimal` | 수익금 = `valuationAmount - purchaseAmount.amount`. 양수=이익, 음수=손실 |

확장 함수 `List<InvestmentItem>.withProfitMetrics(): List<InvestmentItemMetrics>`가
각 종목의 `profit`을 계산해 목록으로 변환한다.

### 시트 연동 모델

원본 구글시트에서 종목을 읽어와 미리보기·저장하기 위한 보조 모델이다.

| 모델 | 위치 | 설명 |
|------|------|------|
| `InvestmentSheetBlock` | `InvestmentSheetRepository.kt` | 한 명의(열 블록)에서 읽은 원본 셀 행. `rows[0]`=헤더 행, 이후=데이터 행 |
| `ParsedInvestmentRow` | `InvestmentSheetPaste.kt` | 시트 한 행의 파싱 결과. `item`(성공) 또는 `error`(실패) 중 하나 + `rawLine` |
| `InvestmentSheetImportBlock` | `DailyAssetInvestmentUiState.kt` | 한 명의 블록의 파싱 결과 묶음(`owner` + `rows`) |
| `InvestmentSheetAuthException` | `InvestmentSheetRepository.kt` | 시트 접근 동의가 필요할 때 복구 인텐트를 담아 던지는 예외 |

### Portfolio

`app/src/main/java/com/jkapp/finance/investment/Portfolio.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `firestoreId` | `String?` | Firestore 문서 ID. 신규 생성 전에는 `null` |
| `name` | `String` | 포트폴리오 이름 |
| `groups` | `List<PortfolioGroup>` | 그룹 목록. 각 그룹은 조건+목표 비율을 정의 |
| `order` | `Int?` | 표시 순서. 사용자가 직접 입력하지 않는다. 하위 호환을 위해 nullable이며 정렬은 nullsFirst(null이 앞). 재정렬 시에만 값이 부여된다 |

### PortfolioGroup

`app/src/main/java/com/jkapp/finance/investment/Portfolio.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `name` | `String` | 그룹 이름 |
| `owners` | `List<String>` | 포함할 소유주 목록. 비어 있으면 필터 없음 |
| `accounts` | `List<String>` | 포함할 계좌(`assetName`) 목록. 비어 있으면 필터 없음 |
| `categories` | `List<String>?` | 포함할 카테고리 목록. `null` 또는 빈 리스트이면 필터 없음 |
| `stockNames` | `List<String>?` | 포함할 종목명(`investmentName`) 목록. `null` 또는 빈 리스트이면 필터 없음 |
| `targetRatio` | `Int` | 목표 비율(%). 0 이상 100 이하 정수 |
| `order` | `Int?` | 그룹 표시 순서. 사용자가 직접 입력하지 않는다. 하위 호환을 위해 nullable이며 정렬은 nullsFirst. 포트폴리오 저장 시 목록 위치(index)로 부여된다 |

> 그룹 매칭 규칙:
> - 축 내 OR: `owners`에 해당 명의가 하나라도 있으면 통과 (비어 있으면 전체 통과)
> - 축 간 AND: `owners` AND `accounts` AND `categories` AND `stockNames` 모두 통과해야 해당 그룹에 속함
> - `null` 또는 빈 리스트인 축은 "제한 없음(전체 허용)"으로 처리
> - 한 종목이 여러 그룹 조건에 겹치면 각 그룹에 중복 합산 허용
> - 어느 그룹에도 속하지 않는 종목은 파이 차트에서 제외(합산 분모에서도 제외)

### InvestmentFilter (필터 모달 상태)

`app/src/main/java/com/jkapp/finance/investment/InvestmentFilter.kt`

필터 모달에서 사용자가 선택한 다중 필터 조건. 각 축은 선택된 값의 `Set`이며 빈 Set은 "전체(제한 없음)"를 의미한다.

| 속성 | 타입 | 설명 |
|------|------|------|
| `owners` | `Set<String>` | 선택된 소유주. 비어 있으면 전체 |
| `accounts` | `Set<String>` | 선택된 계좌(`assetName`). 비어 있으면 전체 |
| `categories` | `Set<String>` | 선택된 카테고리. 비어 있으면 전체 |
| `stockNames` | `Set<String>` | 선택된 종목명. 비어 있으면 전체 |

> 기존 단일 선택(`selectedAssetNameFilter`, `selectedCategoryFilter`)은 이 다중 선택 모델로 대체된다.
> "오늘 이하 가장 최신 날짜"는 전체 명의 통틀어 1개로 ViewModel이 자동 결정한다(사용자가 날짜를 선택하지 않음).

## 기능 (메서드)

`InvestmentFirestoreRepository`가 노출하는 동작.

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `getDailyAssetInvestments` | `(): Flow<List<DailyAssetInvestment>>` | 전체 문서 실시간 구독 (date 내림차순 정렬) |
| `upsertDailyAssetInvestment` | `(DailyAssetInvestment): Unit` | `{date}_{owner}` 문서를 통째로 `set`(upsert) |
| `deleteDailyAssetInvestment` | `(date: String, owner: String): Unit` | `{date}_{owner}` 문서 삭제 |

`PortfolioFirestoreRepository`가 노출하는 동작.

`app/src/main/java/com/jkapp/finance/investment/PortfolioFirestoreRepository.kt`

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `getPortfolios` | `(): Flow<List<Portfolio>>` | 전체 포트폴리오 실시간 구독. `order` nullsFirst로 정렬해 반환하며, 각 포트폴리오의 `groups`도 `order` nullsFirst로 정렬한다 |
| `upsertPortfolio` | `(Portfolio): String` | 포트폴리오 생성 또는 전체 교체(`set`). 저장한 문서 ID 반환(신규는 자동 생성 ID) |
| `updatePortfolioOrders` | `(orders: Map<String, Int>): Unit` | 여러 포트폴리오의 `order` 필드만 batch로 부분 업데이트(firestoreId → order). 재정렬 시 값이 실제로 바뀐 문서만 전달받는다 |
| `deletePortfolio` | `(firestoreId: String): Unit` | 포트폴리오 삭제 |

`InvestmentSheetRepository`가 노출하는 동작.

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `setAccount` | `(accountName: String): Unit` | 시트 읽기에 쓸 구글 계정 지정 |
| `readInvestmentBlocks` | `(): List<InvestmentSheetBlock>` | 명의별 열 블록을 헤더+데이터 행으로 읽어옴. 동의 필요 시 `InvestmentSheetAuthException` |

### `portfolios` (Portfolio)

담당 Repository: `PortfolioFirestoreRepositoryImpl`

최상위 Firestore 컬렉션. 문서 ID는 Firestore 자동 생성 ID.

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| (문서 ID) | `firestoreId` | Firestore 자동 생성 |
| `name` | `name` | 포트폴리오 이름 문자열 |
| `groups` | `groups` | 맵 배열. 각 원소는 아래 그룹 필드 |
| `order` | `order` | 정수(Long) 또는 없음. 없으면(구 데이터) `null`로 읽어 nullsFirst 정렬. `updatePortfolioOrders`가 이 필드만 부분 업데이트 |

`groups` 배열의 각 원소(PortfolioGroup) 필드:

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| `name` | `name` | 그룹 이름 |
| `owners` | `owners` | 문자열 배열 |
| `accounts` | `accounts` | 문자열 배열 |
| `categories` | `categories` | 문자열 배열. 없으면 `null`로 저장 |
| `stockNames` | `stockNames` | 문자열 배열. 없으면 `null`로 저장 |
| `targetRatio` | `targetRatio` | 정수(Long). `0` 이상 `100` 이하 |
| `order` | `order` | 정수(Long) 또는 없음. 없으면(구 데이터) `null`로 읽어 nullsFirst 정렬. 포트폴리오 저장 시 목록 위치로 부여 |

## 타 도메인과의 연관성

- `finance.benchmark.Benchmark` — 벤치마크 탭이 `Benchmark.currentAmount`(명의 구분 없는 전체
  포트폴리오 금액)와 비교하기 위해 최신 날짜 전체 명의의 평가금액 합계를 참조한다. 명의 탭 제거
  이후 ViewModel은 `latestDateTotalValuationAmount`(`latestDate` 기준 모든 명의의
  `valuationAmount` 합계)를 계산해 이 비교에 쓴다.
- 원본 구글시트(`JK-APP raw`) — 별도 DB가 아닌 외부 데이터 소스. 명의별 열 블록을 읽어(읽기 전용)
  종목 목록을 채운다. → [`../../../adr/73/google-sheets-api-readonly.md`](../../../adr/73/google-sheets-api-readonly.md)

## Firestore 컬렉션

담당 Repository: `InvestmentFirestoreRepositoryImpl`

### `daily-asset-investments` (DailyAssetInvestment)

문서 ID는 `{date}_{owner}` 형식이며, 명의별로 문서가 분리된다.

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| (문서 ID) | `firestoreId` | `{date}_{owner}`. 읽을 때 `date`/`owner` 필드가 없으면 문서 ID를 `_` 기준으로 분해해 폴백 |
| `date` | `date` | |
| `owner` | `owner` | |
| `investments` | `investments` | 맵 배열. 각 원소는 아래 종목 필드 |

`investments` 배열의 각 원소(InvestmentItem) 필드:

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| `assetName` | `assetName` | |
| `category` | `category` | |
| `investmentName` | `investmentName` | |
| `pricePerShare` | `pricePerShare` | `BigDecimal.toPlainString()` 문자열로 저장 |
| `valuationAmount` | `valuationAmount` | 문자열로 저장 |
| `quantity` | `quantity` | 문자열로 저장 |
| `purchaseAmount` | `purchaseAmount.amount` | 문자열로 저장 |
| `purchaseAmountCurrency` | `purchaseAmount.currency` | 없으면 `KRW`로 간주(통화 구분 이전 문서 역호환) |

> `BigDecimal` 필드는 정밀도 손실을 피하려 모두 `toPlainString()` 문자열로 저장한다. 읽을 때
> 필수 필드(`purchaseAmountCurrency` 제외)가 하나라도 없거나 숫자 변환에 실패하면 해당 종목은
> 건너뛰고 로그만 남긴다(잘못된 값을 보여주지 않기 위함).
