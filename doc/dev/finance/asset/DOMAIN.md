# asset(자산 관리) 도메인

가족의 가계 자산(예금·계좌·카드 등)을 날짜별로 기록하는 도메인. 하나의 스냅샷
(`DailyAsset`)은 특정 날짜의 자산 항목(`AssetItem`) 목록을 담고, 각 항목은 이름·명의·
금액과 순자산 계산 포함 여부(`hidden`)를 가진다.

## 도메인 모델

### DailyAsset

`app/src/main/java/com/jkapp/finance/asset/DailyAsset.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `firestoreId` | `String?` | Firestore 문서 ID. 이 도메인은 문서 ID로 `date`를 그대로 쓴다. 신규 생성 전에는 `null` |
| `date` | `String` | 자산 스냅샷 날짜. ISO_LOCAL_DATE(`yyyy-MM-dd`) 문자열 |
| `assets` | `List<AssetItem>` | 해당 날짜의 자산 항목 목록. 기본값 `emptyList()` |

### AssetItem

`app/src/main/java/com/jkapp/finance/asset/DailyAsset.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `name` | `String` | 자산 이름 (필수). 없으면 읽기 시 항목이 건너뛰어진다 |
| `owner` | `String` | 명의 (필수). 보통 `전지훈`/`권유경`/`공동` |
| `institution` | `String?` | 금융기관/계좌명. 없으면 `null` |
| `accountNumber` | `String?` | 계좌번호. 없으면 `null` |
| `card` | `String?` | 카드 정보. 시트 가져오기에서는 채우지 않고 사용자가 직접 관리 |
| `amount` | `BigDecimal?` | 금액. 없으면 `null` |
| `hidden` | `Boolean` | 순자산(netWorth) 계산 제외 여부. 기본값 `false` |

### DEFAULT_HIDDEN_ASSET_NAMES

`app/src/main/java/com/jkapp/finance/asset/DailyAsset.kt`의 최상위 상수(`Set<String>`).
순자산 계산에서 기본 제외되는 자산 이름 집합(공용 계좌·부동산·전세보증금·용돈 등 개인
순자산과 무관한 항목). 시트 가져오기 시 이름이 이 집합에 속하면 `hidden = true`로 파싱된다.

## 기능 (메서드)

`AssetFirestoreRepository`가 노출하는 동작.

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `getDailyAssets` | `(): Flow<List<DailyAsset>>` | 자산 스냅샷 실시간 구독 (date 내림차순 정렬) |
| `upsertDailyAsset` | `(DailyAsset): Unit` | 날짜 문서를 통째로 덮어쓰기(set). `suspend` |
| `deleteDailyAsset` | `(date: String): Unit` | 날짜 문서 삭제. `suspend` |

`AssetSheetRepository`(구글시트 직접 연동)가 노출하는 동작.

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `setAccount` | `(accountName: String): Unit` | 시트 접근에 쓸 구글 계정 지정 |
| `readAssetRows` | `(): List<List<String>>` | 원본 시트에서 헤더(0번째)+데이터 행을 셀 문자열 목록으로 읽기. 동의 필요 시 `AssetSheetAuthException`. `suspend` |

## 타 도메인과의 연관성

- `com.jkapp.auth.AuthRepository` — `DailyAssetViewModel`이 로그인된 구글 계정 이메일을
  구독해 `AssetSheetRepository.setAccount`로 전달한다. 이미 동의한 사용자는 계정 선택 없이
  시트를 읽는다.
- `com.jkapp.common.latestNotFuture` — 순자산 계산 시 오늘 이하의 가장 최신 날짜 스냅샷을
  고르는 데 사용한다(미래 날짜 오입력 제외). `finance.benchmark`와 동일 패턴.
- `finance.investment`, `finance.benchmark`와는 데이터 참조 관계가 없다. 시트 직접 연동
  방식(Google Sheets API 읽기 전용)만 공유한다.

## Firestore 컬렉션

담당 Repository: `AssetFirestoreRepositoryImpl`

### `daily-assets` (DailyAsset)

문서 ID = `date`. `amount`는 `BigDecimal`을 `toPlainString()`으로 문자열 저장하고, 읽을 때
`toBigDecimalOrNull()`로 복원한다(부동소수 오차 방지). `name`/`owner`가 없는 항목은 읽기 시
경고 로그와 함께 건너뛴다.

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| (문서 ID) | `firestoreId` / `date` | ID가 곧 date. 읽기 시 `date` 필드가 없으면 문서 ID로 대체 |
| `date` | `date` | |
| `assets` | `assets` | 맵 배열 |
| `assets[].name` | `AssetItem.name` | 없으면 항목 건너뜀 |
| `assets[].owner` | `AssetItem.owner` | 없으면 항목 건너뜀 |
| `assets[].institution` | `AssetItem.institution` | camelCase |
| `assets[].accountNumber` | `AssetItem.accountNumber` | camelCase |
| `assets[].card` | `AssetItem.card` | |
| `assets[].amount` | `AssetItem.amount` | 문자열로 저장(`toPlainString`), 읽기 시 `BigDecimal` 복원 |
| `assets[].hidden` | `AssetItem.hidden` | 없으면 `false` |
