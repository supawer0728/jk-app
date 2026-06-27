# Kotlin 코딩 컨벤션

포맷·명명 규칙은 [`CODE_STYLE.md`](CODE_STYLE.md)를 따른다.
이 문서는 **관용적인 Kotlin 패턴과 설계 관행**을 다룬다.
참고: [Kotlin 공식 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html)

---

## 1. 불변성 우선

```kotlin
// val 우선, var는 불가피할 때만
val items = listOf("a", "b")   // OK
var count = 0                   // 반드시 변경이 필요할 때만

// 불변 컬렉션 타입 사용
val names: List<String> = listOf()    // OK
val names: MutableList<String> = mutableListOf()  // 변경 필요 시만
```

---

## 2. 데이터 클래스

값 객체(VO)에는 `data class`를 사용한다. `equals`, `hashCode`, `copy`, `toString`이 자동 생성된다.

```kotlin
data class Asset(
    val id: String,
    val name: String,
    val amount: Long,
)

// 일부 필드만 변경할 때 copy 사용
val updated = asset.copy(amount = 1_000_000L)
```

- 상속이 필요하지 않으면 `data class` 우선
- 불변 상태 변경은 항상 `copy()`로 처리

---

## 3. sealed class / sealed interface

상태나 결과를 표현할 때 sealed 계층을 사용한다.

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

// when은 모든 분기를 강제 처리
when (state) {
    is UiState.Loading -> showSpinner()
    is UiState.Success -> render(state.data)
    is UiState.Error   -> showError(state.message)
}
```

- `else` 분기 없이 `when`을 exhaustive하게 작성 → 새 케이스 추가 시 컴파일 오류로 누락 방지

---

## 4. Null 안전

```kotlin
// !! 사용 금지 — NPE를 감추는 것이 아니라 런타임으로 미룰 뿐
val length = str!!.length        // WRONG

// 엘비스 연산자로 기본값 제공
val length = str?.length ?: 0   // OK

// let으로 null이 아닐 때만 실행
str?.let { process(it) }

// 표준 함수 활용
if (list.isNullOrEmpty()) return
val trimmed = str.orEmpty().trim()
```

- 플랫폼 타입(자바 코드 반환값)은 반드시 nullable로 받아 처리한다.

---

## 5. 스코프 함수

| 함수 | 수신 객체 참조 | 반환값 | 주요 용도 |
|------|--------------|--------|-----------|
| `let` | `it` | 람다 결과 | null 체크 후 변환 |
| `run` | `this` | 람다 결과 | 객체 설정 + 결과 반환 |
| `apply` | `this` | 수신 객체 | 객체 초기화·설정 |
| `also` | `it` | 수신 객체 | 부수 효과(로깅 등) |
| `with` | `this` | 람다 결과 | 이미 있는 객체에 여러 연산 |

```kotlin
// apply: 객체 초기화
val request = SpreadsheetValues().apply {
    range = "Sheet1!A1:D"
    majorDimension = "ROWS"
}

// let: null safe 변환
val result = nullableValue?.let { transform(it) } ?: default

// also: 디버깅 로그
return fetchData()
    .also { Log.d(TAG, "fetched: $it") }
```

- 중첩 스코프 함수는 2단계 이하로 제한한다.

---

## 6. 확장 함수

기존 클래스에 기능을 추가할 때 상속 대신 확장 함수를 사용한다.

```kotlin
fun String.toAmountOrNull(): Long? = toLongOrNull()?.takeIf { it >= 0 }

fun List<Asset>.totalAmount(): Long = sumOf { it.amount }
```

- 유틸리티 클래스(`StringUtils`, `ListHelper`) 대신 확장 함수로 작성한다.
- 확장 함수가 많아지면 `Extensions.kt` 파일로 모은다.

---

## 7. 기본값 파라미터 / 이름 있는 인수

오버로드 대신 기본값 파라미터를 사용한다.

```kotlin
// WRONG: 오버로드 남발
fun fetchAssets() = fetchAssets(forceRefresh = false)
fun fetchAssets(forceRefresh: Boolean) { }

// OK: 기본값 파라미터
fun fetchAssets(forceRefresh: Boolean = false) { }

// 인수가 3개 이상이면 이름 있는 인수 사용
parseRow(
    values = row,
    startIndex = 1,
    includeHeader = false,
)
```

---

## 8. when 표현식

`if-else` 체인 대신 `when`을 사용한다.

```kotlin
// WRONG
fun label(code: Int): String {
    if (code == 1) return "주식"
    else if (code == 2) return "채권"
    else return "기타"
}

// OK
fun label(code: Int) = when (code) {
    1    -> "주식"
    2    -> "채권"
    else -> "기타"
}

// 범위·타입 조건도 활용
when {
    amount < 0       -> "음수"
    amount == 0L     -> "제로"
    amount > 1_000_000L -> "백만 이상"
    else             -> "일반"
}
```

---

## 9. 컬렉션 API

루프 대신 표준 컬렉션 함수를 사용한다.

```kotlin
// WRONG: 수동 루프
val result = mutableListOf<String>()
for (asset in assets) {
    if (asset.amount > 0) result.add(asset.name)
}

// OK: 함수형 체인
val result = assets
    .filter { it.amount > 0 }
    .map { it.name }

// 자주 쓰는 함수
assets.sumOf { it.amount }
assets.groupBy { it.type }
assets.sortedByDescending { it.amount }
assets.firstOrNull { it.id == targetId }
assets.any { it.amount < 0 }
assets.all { it.amount >= 0 }
```

- 중간 리스트가 많이 생성되는 체인에는 `asSequence()`를 붙인다.

---

## 10. 코루틴

```kotlin
// GlobalScope 사용 금지
GlobalScope.launch { }    // WRONG

// ViewModel에서는 viewModelScope
viewModelScope.launch {
    _state.value = UiState.Loading
    _state.value = try {
        UiState.Success(repository.fetchAssets())
    } catch (e: Exception) {
        UiState.Error(e.message ?: "알 수 없는 오류")
    }
}

// 스트림은 Flow
fun observeAssets(): Flow<List<Asset>> = flow {
    while (true) {
        emit(sheetsDataSource.fetchAll())
        delay(POLL_INTERVAL_MS)
    }
}

// Dispatcher 선택
withContext(Dispatchers.IO)   { /* I/O, 네트워크 */ }
withContext(Dispatchers.Default) { /* CPU 집약 연산 */ }
// UI 업데이트는 viewModelScope가 자동으로 Main에서 처리
```

- `suspend` 함수는 항상 구조화된 동시성(structured concurrency) 내에서 호출한다.
- `launch` 결과를 무시하면 예외가 묻힌다 — 오류 처리를 반드시 포함한다.

---

## 11. object / companion object

```kotlin
// 싱글턴
object SheetConfig {
    const val SPREADSHEET_ID = "1jiaeZjgLAW5..."
    const val ASSETS_RANGE   = "자산!A2:F"
}

// 팩토리 메서드 / 상수
class AssetMapper private constructor() {
    companion object {
        fun from(row: List<Any>): Asset { }
    }
}
```

---

## 12. 사전 조건 검사

```kotlin
fun setAmount(amount: Long) {
    require(amount >= 0) { "금액은 0 이상이어야 합니다: $amount" }
}

fun process(data: List<String>?) {
    checkNotNull(data) { "데이터가 초기화되지 않았습니다" }
    check(data.isNotEmpty()) { "데이터가 비어 있습니다" }
}
```

- `require`: 잘못된 인수 → `IllegalArgumentException`
- `check`: 잘못된 상태 → `IllegalStateException`
- `error(msg)`: 도달 불가 코드 마킹

---

## 13. 문자열

```kotlin
// 템플릿 사용 (연결 연산자 대신)
val msg = "자산: ${asset.name}, 금액: ${asset.amount}원"  // OK
val msg = "자산: " + asset.name + ", 금액: " + asset.amount + "원"  // WRONG

// 여러 줄 문자열
val query = """
    SELECT *
    FROM assets
    WHERE amount > 0
""".trimIndent()

// toXxx 대신 안전한 변환
val amount = str.toLongOrNull() ?: 0L
```

---

## 14. 타입 캐스팅

```kotlin
// 안전 캐스트
val asset = obj as? Asset ?: return   // OK
val asset = obj as Asset              // ClassCastException 위험 — 타입 확인 후만 사용

// is로 스마트 캐스트 활용
if (result is UiState.Success) {
    render(result.data)    // 캐스트 불필요
}
```

---

## 15. 가시성

- 가능한 좁은 가시성을 기본으로 한다: `private` → `internal` → `protected` → `public`
- 외부에 노출할 필요가 없는 클래스·함수는 `private` 또는 `internal`
- `internal`은 모듈 경계 캡슐화에 적극 활용한다

```kotlin
internal class SheetsDataSource { }   // 모듈 내부 전용
private fun parseRow(row: List<Any>): Asset { }
```
