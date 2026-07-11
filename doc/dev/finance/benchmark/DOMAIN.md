# benchmark(벤치마크) 도메인

월별로 기록한 투자자산의 원금·현재금액과 같은 시점의 KOSPI/S&P500/나스닥 지수 값을 함께 담아,
투자 성과를 시장 지수와 비교하는 도메인. 하나의 기록(`Benchmark`)은 한 날짜의 스냅샷이며,
수익률·상승률·MDD 등 파생 지표는 저장하지 않고 조회 시점에 계산한다.

## 도메인 모델

### Benchmark

`app/src/main/java/com/jkapp/finance/benchmark/Benchmark.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `firestoreId` | `String?` | Firestore 문서 ID. 이 도메인에서는 문서 ID로 `date`를 그대로 쓴다. 신규 생성 전에는 `null` |
| `date` | `String` | 기록 날짜. ISO_LOCAL_DATE(`yyyy-MM-dd`) 문자열 |
| `additionalInvestment` | `BigDecimal` | 해당 날짜의 추가 납입액. 음수면 출금을 의미한다 |
| `currentAmount` | `BigDecimal` | 해당 날짜의 투자자산 평가 총액 |
| `kospi` | `BigDecimal` | 같은 날짜의 KOSPI 지수 값 |
| `snp500` | `BigDecimal` | 같은 날짜의 S&P500 지수 값 |
| `nasdaq` | `BigDecimal` | 같은 날짜의 나스닥 지수 값 |

### IndexMetrics (파생 값 객체)

`app/src/main/java/com/jkapp/finance/benchmark/Benchmark.kt`

KOSPI/S&P500/나스닥처럼 순수 가격 시리즈에 대해 공통으로 계산하는 파생 지표. 저장되지 않고 계산으로만 존재한다.

| 속성 | 타입 | 설명 |
|------|------|------|
| `value` | `BigDecimal` | 원본 지수 값 |
| `returnRatePercent` | `BigDecimal` | 가장 빠른 날짜의 값 대비 수익률(%). 최초 항목은 0 |
| `changePercent` | `BigDecimal?` | 직전 날짜 대비 상승률(%). 직전 항목이 없으면 `null` |
| `mdd` | `BigDecimal?` | 지금까지의 고점 대비 하락폭(%, 0 이하). 고점이 0이면 `null` |

### BenchmarkRowMetrics (파생 값 객체)

`app/src/main/java/com/jkapp/finance/benchmark/Benchmark.kt`

한 행(한 날짜)에 대해 표에 필요한 투자자산 지표와 세 지수의 `IndexMetrics`를 묶은 결과. 저장되지 않는다.

| 속성 | 타입 | 설명 |
|------|------|------|
| `benchmark` | `Benchmark` | 원본 기록 |
| `principal` | `BigDecimal` | 누적 원금. 날짜 오름차순으로 `additionalInvestment`를 누적한 합 |
| `profit` | `BigDecimal` | 수익금 = `currentAmount - principal` |
| `returnRatePercent` | `BigDecimal?` | 누적 원금 대비 수익률(%). 원금이 0이면 `null` |
| `returnRateChangePercent` | `BigDecimal?` | 수익률의 직전 날짜 대비 변화(%p). 계산 불가하면 `null` |
| `assetMdd` | `BigDecimal?` | 수익률 시리즈 기준 고점 대비 하락폭(%p, 0 이하). 계산 불가하면 `null` |
| `kospi` | `IndexMetrics` | KOSPI 파생 지표 |
| `snp500` | `IndexMetrics` | S&P500 파생 지표 |
| `nasdaq` | `IndexMetrics` | 나스닥 파생 지표 |

### ParsedBenchmarkRow (시트 파싱 결과)

`app/src/main/java/com/jkapp/finance/benchmark/BenchmarkSheetPaste.kt`

구글시트 한 행을 파싱한 결과. 성공(`benchmark` 채움)과 실패(`error` 채움)가 미리보기에 혼재한다.

| 속성 | 타입 | 설명 |
|------|------|------|
| `benchmark` | `Benchmark?` | 파싱 성공 시 채워지는 도메인 객체. 실패 시 `null` |
| `error` | `String?` | 파싱 실패 사유. 성공 시 `null` |
| `rawLine` | `String` | 원본 셀들을 탭으로 이은 표시용 문자열 |

## 기능 (메서드)

### BenchmarkFirestoreRepository

`app/src/main/java/com/jkapp/finance/benchmark/BenchmarkFirestoreRepository.kt`

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `getBenchmarks` | `(): Flow<List<Benchmark>>` | 실시간 구독. 역직렬화 실패 문서는 걸러내고 `date` 내림차순 정렬 |
| `upsertBenchmark` | `(Benchmark): Unit` | 단건 저장(문서 ID = `date`) |
| `upsertBenchmarks` | `(List<Benchmark>): Unit` | 여러 날짜를 하나의 원자적 배치로 저장 |
| `deleteBenchmark` | `(date: String): Unit` | 단건 삭제 |
| `deleteBenchmarks` | `(dates: List<String>): Unit` | 여러 날짜를 하나의 원자적 배치로 삭제 |
| `deleteAllBenchmarks` | `(): Unit` | 컬렉션을 직접 조회해 손상 문서까지 전부 삭제 |

### BenchmarkSheetRepository

`app/src/main/java/com/jkapp/finance/benchmark/BenchmarkSheetRepository.kt`

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `setAccount` | `(accountName: String): Unit` | 시트를 읽을 구글 계정 지정 |
| `readBenchmarkRows` | `(): List<List<String>>` | 원본 시트의 헤더(0번째)+데이터 행을 셀 문자열로 읽음. 동의 필요 시 `BenchmarkSheetAuthException` |

### 파생 지표 계산 (도메인 확장 함수)

`app/src/main/java/com/jkapp/finance/benchmark/Benchmark.kt`

| 함수 | 시그니처 | 설명 |
|------|----------|------|
| `withRowMetrics` | `List<Benchmark>.(): List<BenchmarkRowMetrics>` | 날짜 오름차순으로 정렬해 원금 누적·수익률·MDD와 세 지수 지표를 계산한 뒤 입력과 같은 순서로 반환 |

### 시트 페이지네이션

`app/src/main/java/com/jkapp/finance/benchmark/BenchmarkSheetPaging.kt`

| 함수 | 시그니처 | 설명 |
|------|----------|------|
| `collectPagedRows` | `(pageSize, firstRow, readPage): List<List<String>>` | 데이터 행을 100행 단위로, 페이지 크기 미만이 올 때까지 끝까지 읽는 순수 함수 |

## 타 도메인과의 연관성

- **investment(투자 종목)** — 벤치마크의 `currentAmount`는 특정 시점 투자자산 평가 총액을 뜻하며,
  투자 종목 도메인이 관리하는 자산의 시장 성과 비교 기준이 된다. 벤치마크 화면(`BenchmarkTab`)은
  투자 탭(`InvestmentTab`) 아래에서 함께 노출된다(`finance/FinanceScreen.kt`). 두 도메인은 컬렉션이
  분리되어 있고 FK 참조는 없으며, 화면 구성에서 나란히 배치되는 관계다.
- **투자자산 배너** — `BenchmarkViewModel.latestCurrentAmount`(미래 날짜를 제외한 최신 `currentAmount`)를
  `common.MainScreen`의 `NetWorthBanner`가 "투자자산" 값으로 소비한다(`common/DateUtils.latestNotFuture`).
- **KOSPI/S&P500/나스닥** — 별도 도메인 모델이 아니라 `Benchmark`의 지수 필드로 함께 저장되는 시장 지수 값이다.

## Firestore 컬렉션

담당 Repository: `BenchmarkFirestoreRepositoryImpl`

### `benchmarks` (Benchmark)

문서 ID로 `date`를 사용하므로 같은 날짜는 자동으로 덮어쓴다(upsert). 모든 숫자 필드는
`BigDecimal.toPlainString()` 문자열로 저장하고, 읽을 때 `toBigDecimalOrNull()`로 되돌린다.

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| (문서 ID) | `firestoreId` / `date` | 문서 ID = `date`. 읽기 시 `date` 필드가 없으면 문서 ID로 대체 |
| `date` | `date` | |
| `additionalInvestment` | `additionalInvestment` | 문자열로 저장(`toPlainString`) |
| `currentAmount` | `currentAmount` | 문자열로 저장 |
| `kospi` | `kospi` | 문자열로 저장 |
| `snp500` | `snp500` | 문자열로 저장 |
| `nasdaq` | `nasdaq` | 문자열로 저장 |

숫자 필드가 하나라도 없거나 파싱에 실패하면 해당 문서는 `getBenchmarks()` 결과에서 제외되고 로그만 남는다.
