# 코드 스타일 가이드

Android 공식 Kotlin 스타일 가이드(https://developer.android.com/kotlin/style-guide)를 기반으로 한다.
AI가 코드를 생성할 때는 아래 규칙을 반드시 따른다.

---

## 1. 소스 파일

### 인코딩
- 모든 소스 파일은 **UTF-8**로 저장한다.

### 파일 명명
- 최상위 클래스가 하나인 경우: 클래스명과 동일한 **PascalCase** + `.kt`
- 최상위 선언이 여러 개인 경우: 내용을 설명하는 PascalCase 이름 (예: `Extensions.kt`)

### 파일 구조 순서
1. 저작권/라이선스 (멀티라인 주석 `/* */`, KDoc·단일 라인 주석 사용 금지)
2. 패키지 선언 (줄 바꿈 없이 한 줄)
3. import 문 (ASCII 순 정렬, 와일드카드 `*` 금지, 줄 바꿈 없음)
4. 최상위 선언

각 섹션은 빈 줄 하나로 구분한다.

### 특수 문자
- 들여쓰기에 탭 사용 금지 — 스페이스만 사용
- 이스케이프 시퀀스(`\n`, `\t` 등)를 유니코드 이스케이프(`
`)보다 우선 사용
- 출력 가능한 문자에 유니코드 이스케이프 사용 금지

---

## 2. 포맷팅

### 중괄호 (K&R 스타일)
- 여는 중괄호: 앞에 줄 바꿈 없음, 뒤에 줄 바꿈
- 닫는 중괄호: 앞에 줄 바꿈, 문장/함수/클래스 종료 시 뒤에 줄 바꿈

```kotlin
// 한 줄에 들어오면 중괄호 생략 가능
if (string.isEmpty()) return

// 여러 줄이면 반드시 중괄호 사용
val value = if (string.isEmpty()) {
    0
} else {
    1
}

// 빈 블록
try {
    doSomething()
} catch (e: Exception) {
}
```

### 들여쓰기
- 블록 레벨당 **4 스페이스**

### 줄 길이
- **최대 100자**
- 예외: KDoc 내 URL, 패키지·import 문, 주석 내 셸 명령

### 줄 바꿈 우선순위
- 가능한 한 높은 문법 수준에서 줄을 나눈다.
- 연산자·중위 함수: 연산자 **뒤**에서 줄 바꿈
- `.`, `?.`, `::` : 기호 **앞**에서 줄 바꿈
- `->`, `,` : 기호 **뒤**에서 줄 바꿈

```kotlin
val result = object
    .method()
    .anotherMethod()

fun joinToString(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
): String { }
```

### 함수
```kotlin
// 단일 표현식 함수
override fun toString(): String = "Hey"

// 여러 줄 파라미터
fun <T> process(
    param1: String,
    param2: Int,
): T { }
```

### 공백 (수직)
- 클래스 멤버(프로퍼티·생성자·함수) 사이: 빈 줄 **하나**
- 연속된 프로퍼티끼리는 빈 줄 생략 가능
- 연속된 빈 줄 2개 이상 금지

### 공백 (수평)
```kotlin
// 키워드 뒤 여는 괄호 앞
for (i in 0..1) { }    // OK
for(i in 0..1) { }     // WRONG

// else/catch 전후
} else {               // OK
}else {                // WRONG

// 이항 연산자 주변
val two = 1 + 1        // OK

// 공백 없음: ::, ., .., ?., 람다 ->(화살표 앞뒤는 공백 있음)
val fn = Any::toString
it.toString()
for (i in 1..4) { }
ints.map { value -> value.toString() }
```

### 어노테이션
```kotlin
// 인자 없는 단일 어노테이션: 같은 줄 허용
@Volatile var count = 0

// 여러 어노테이션 또는 인자 있는 경우: 각각 별도 줄
@Retention(SOURCE)
@Target(FUNCTION)
annotation class MyAnnotation
```

---

## 3. 명명 규칙

모든 식별자는 ASCII 문자와 숫자만 사용한다 (백킹 프로퍼티 접두사 `_` 제외).

| 요소 | 스타일 | 예시 |
|------|--------|------|
| 패키지 | 소문자, 단어 연속 (언더스코어 없음) | `com.jkaki.finance` |
| 클래스 / 인터페이스 | PascalCase | `AssetRepository` |
| 함수 | camelCase (동사/동사구) | `fetchAssets()` |
| 상수 (`const val`, 불변 컬렉션) | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 일반 변수 / 프로퍼티 | camelCase | `totalAmount` |
| 타입 파라미터 | 대문자 한 글자 또는 `T` 접미사 | `T`, `RequestT` |
| 테스트 함수 | camelCase, 언더스코어로 논리 구분 허용 | `pop_emptyStack()` |
| `@Composable` (Unit 반환) | PascalCase 명사 | `AssetCard` |
| 백킹 프로퍼티 | `_` + 프로퍼티명 | `_assets` |

### 카멜케이스 변환 규칙
약어도 일반 단어처럼 처리한다 (첫 글자만 대문자, 나머지 소문자).

| 구 | 올바름 | 잘못됨 |
|----|--------|--------|
| XML HTTP request | `XmlHttpRequest` | `XMLHTTPRequest` |
| new customer ID | `newCustomerId` | `newCustomerID` |
| supports IPv6 on iOS | `supportsIpv6OnIos` | `supportsIPv6OnIOS` |

---

## 4. 문서화 (KDoc)

```kotlin
/**
 * 여러 줄 KDoc은 이렇게 작성한다.
 *
 * @param name 파라미터 설명
 * @return 반환값 설명
 * @throws IOException I/O 오류 발생 시
 */
fun method(name: String): String { }

/** 한 줄에 들어오면 이렇게 쓴다. */
```

### 필수 대상
- 모든 `public` 타입
- 모든 `public` / `protected` 멤버
- 예외: 이름만으로 명백한 단순 getter/setter, override 메서드

### 블록 태그 순서
`@constructor` → `@receiver` → `@param` → `@property` → `@return` → `@throws` → `@see`

---

## 5. 프로그래밍 관행

### 제어 구조
```kotlin
// 단순 단일 문장: 중괄호 생략 가능
if (string.isEmpty()) return

when (value) {
    0 -> return
}

// 복합/다중 문장: 중괄호 필수
if (condition) {
    foo()
    bar()
}
```

### 타입 추론
맥락에서 타입이 명확하면 명시적 타입 선언 생략 가능.

```kotlin
override fun toString() = "Hey"   // 반환 타입 생략 OK
private val icon = IconLoader.getIcon("/icons/app.png")  // 타입 생략 OK
```

### 상수
스칼라 상수에는 `const` 수식어를 사용한다.
```kotlin
const val SPREADSHEET_ID = "1jiaeZjgLAW5..."
val RETRY_DELAYS = listOf(1_000L, 2_000L, 5_000L)
```

### enum
```kotlin
// 단순 enum: 한 줄 허용
enum class SyncStatus { IDLE, RUNNING, ERROR }

// 본문이 있는 경우: 각각 별도 줄
enum class SyncStatus {
    IDLE,
    RUNNING,
    ERROR {
        override fun toString() = "동기화 오류"
    }
}
```
