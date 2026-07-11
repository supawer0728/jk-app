# investment(투자 종목) 도메인

명의별로 특정 날짜의 투자 종목 보유 현황을 관리하는 도메인. 하나의 문서(`DailyAssetInvestment`)는
`날짜 + 명의` 단위로 그 시점의 투자 종목 목록(`InvestmentItem`)을 담으며, 각 종목은 1주 가격·
평가금액·보유수량·매수금액(통화 포함)을 갖는다.

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

## 기능 (메서드)

`InvestmentFirestoreRepository`가 노출하는 동작.

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `getDailyAssetInvestments` | `(): Flow<List<DailyAssetInvestment>>` | 전체 문서 실시간 구독 (date 내림차순 정렬) |
| `upsertDailyAssetInvestment` | `(DailyAssetInvestment): Unit` | `{date}_{owner}` 문서를 통째로 `set`(upsert) |
| `deleteDailyAssetInvestment` | `(date: String, owner: String): Unit` | `{date}_{owner}` 문서 삭제 |

`InvestmentSheetRepository`가 노출하는 동작.

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `setAccount` | `(accountName: String): Unit` | 시트 읽기에 쓸 구글 계정 지정 |
| `readInvestmentBlocks` | `(): List<InvestmentSheetBlock>` | 명의별 열 블록을 헤더+데이터 행으로 읽어옴. 동의 필요 시 `InvestmentSheetAuthException` |

## 타 도메인과의 연관성

- `finance.benchmark.Benchmark` — 벤치마크 탭이 `Benchmark.currentAmount`(명의 구분 없는 전체
  포트폴리오 금액)와 비교하기 위해 같은 날짜 전체 명의의 평가금액 합계를 참조한다. 그래서
  ViewModel은 명의별 `currentInvestment`와 별개로 `selectedDateTotalValuationAmount`
  (같은 날짜 모든 명의의 `valuationAmount` 합계)를 따로 계산한다.
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
