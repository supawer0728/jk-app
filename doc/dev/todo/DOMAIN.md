# todo(오늘의 할일) 도메인

가족(본인·배우자) 공용 할일 관리 도메인. 하나의 할일(`TodoItem`)은 제목·메모와 함께
3단계 상태, 담당자, 우선순위, 마감일시, 마감 리마인더, 반복 규칙(`RecurrenceRule`)을 가진다.
카테고리·태그는 없다(이슈 #71에서 제거).

1-depth 자식 항목(`TodoType.SUB`)을 지원한다(이슈 #88). 자식은 부모의 펼침 영역 안에서만
노출되며, 목록 필터·정렬에 독립 항목으로 나타나지 않는다.

## 도메인 모델

### TodoType

자기참조 없이 1-depth 만 지원하는 계층 구분자.

`app/src/main/java/com/jkapp/todo/TodoType.kt`

| 값 | 의미 |
|----|------|
| `MAIN` | 독립 항목(기본값). 자식을 가질 수 있다 |
| `SUB` | 자식 항목. `mainTodoId`로 부모를 참조한다. 자신이 다시 부모가 될 수 없다 |

### TodoItem

`app/src/main/java/com/jkapp/todo/TodoItem.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `firestoreId` | `String?` | Firestore 문서 auto-ID. 신규 생성 전에는 `null` |
| `type` | `TodoType` | 항목 종류. 기본값 `MAIN` |
| `mainTodoId` | `String?` | `SUB`일 때 부모 문서 ID. `MAIN`이면 `null` |
| `title` | `String` | 할일 제목 (필수) |
| `memo` | `String` | 메모. 기본값 `""` |
| `status` | `TodoStatus` | 3단계 상태. 기본값 `NOT_STARTED` |
| `assignee` | `TodoAssignee` | 담당자. 기본값 `DEFAULT`(= `SHARED`) |
| `dueAt` | `Instant?` | 마감일시. 없으면 `null`. **SUB 항목에는 사용하지 않는다** |
| `reminderOffsetMinutes` | `Int?` | 마감 몇 분 전에 알릴지. 없으면 `null`. **SUB 항목에는 사용하지 않는다** |
| `priority` | `TodoPriority` | 우선순위. 기본값 `NONE`. **SUB 항목에는 사용하지 않는다** |
| `recurrence` | `RecurrenceRule?` | 반복 규칙. 반복 아님이면 `null`. **SUB 항목에는 사용하지 않는다** |
| `completionHistory` | `List<Instant>` | 반복 항목이 완료 처리된 지난 회차의 `dueAt` 이력. 기본값 `emptyList()` |
| `createdAt` | `Instant?` | 생성 시각. `addTodoItem`에서만 부여되는 불변 필드 |
| `completedAt` | `Instant?` | 마지막 완료 처리 시각 |
| `lastEditedByUid` | `String?` | 마지막으로 저장한 사용자의 Firebase Auth `uid`를 남기는 감사(audit) 필드. Repository가 사용자 편집 쓰기(add/update/complete·자식 생성) 시 주입한다. 단, **자식 생성에 따른 완료 부모 자동 되돌림(IN_PROGRESS)은 사용자 편집이 아니므로 기존 값을 보존**한다. 담당자 배정 push 생성 시 대상에서 편집자 본인을 제외하는 판별에도 쓰인다(→ ADR/60/01 근거 갱신) |
| `isCompleted` | `Boolean` | 파생 프로퍼티(`get()`). `status == DONE`과 동치. 저장 필드 아님 |

#### TodoStatus (`TodoStatus.kt`)

완료/미완료 이분법을 대체하는 3단계 상태. `ordinal` 순서를 목록 정렬의 2차 키로 쓰므로 순서를 바꾸지 않는다.

| 값 | 의미 |
|----|------|
| `NOT_STARTED` | 미진행 |
| `IN_PROGRESS` | 진행중 |
| `DONE` | 완료 |

#### TodoPriority (`TodoPriority.kt`)

| 값 | 의미 |
|----|------|
| `NONE` | 없음(기본값) |
| `LOW` | 낮음 |
| `MEDIUM` | 보통 |
| `HIGH` | 높음 |

#### TodoAssignee (`TodoAssignee.kt`)

가족 2인 협업용 담당자. 목록을 enum으로 고정하며 별도 사용자 관리 화면은 없다. 각 엔트리는
알림 대상 식별용 이메일 목록을 가진다.

| 값 | emails | 의미 |
|----|--------|------|
| `SHARED` | 두 사용자 모두 | 공동. `DEFAULT`이자 담당자 개념이 없던 기존 문서의 기본값 |
| `KWON_YUKYEONG` | `fmx.yu.k@gmail.com` | 배우자 |
| `JEON_JIHOON` | `supawer0728@gmail.com` | 본인 |

- `DEFAULT = SHARED`
- `fromNameOrDefault(name)` — enum 이름 문자열을 파싱하되 알 수 없으면 `DEFAULT` 반환

### RecurrenceRule

`app/src/main/java/com/jkapp/todo/RecurrenceRule.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `frequency` | `RecurrenceFrequency` | 반복 주기 (`DAILY`/`WEEKLY`/`MONTHLY`/`YEARLY`) |
| `interval` | `Int` | 주기 간격. 기본값 `1` (예: 2주마다 = WEEKLY interval 2) |
| `daysOfWeek` | `Set<Int>` | `WEEKLY`에서만 의미. `java.time.DayOfWeek.getValue()`(1=월~7=일). 기본값 `emptySet()` |
| `endAt` | `Instant?` | 반복 종료 시각. 이 시각을 넘는 회차는 생성하지 않는다 |
| `anchorDay` | `Int?` | `MONTHLY`/`YEARLY`에서만 의미. 원래 지정 일자(1~31)를 고정해 말일 클램프 후 원일자를 복원 |

#### RecurrenceFrequency

`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`.

## 기능 (메서드)

`TodoFirestoreRepository`가 노출하는 동작.

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `getTodoItems` | `(): Flow<List<TodoItem>>` | 할일 실시간 구독 (MAIN + SUB 모두 포함) |
| `getTodoItemOnce` | `(firestoreId: String): TodoItem?` | 단건 1회 조회 (Worker의 완료 여부 재확인용) |
| `addTodoItem` | `(TodoItem): String` | 추가, 생성된 firestoreId 반환. `createdAt`을 부여하고 반복이면 anchorDay 고정 |
| `addSubTodoItem` | `(parentId: String, item: TodoItem, notify: Boolean = true): String` | 자식 항목 추가. `type=SUB`, `mainTodoId=parentId` 자동 주입. 완료 부모는 IN_PROGRESS로 되돌림(상태변경 무알림, `lastEditedByUid` 보존). `notify=true`면 자식 생성 push 1건 발송, `notify=false`면 무알림(반복 복제용) |
| `updateTodoItem` | `(TodoItem): Unit` | 수정 (firestoreId 필수, 없으면 `IllegalArgumentException`) |
| `deleteTodoItem` | `(firestoreId: String): Unit` | 단건 삭제. MAIN이면 자식(SUB)도 cascade 삭제 |
| `deleteTodoItems` | `(ids: List<String>): Unit` | 다중 삭제. 각 id가 MAIN이면 자식(SUB)도 cascade 삭제 |
| `completeTodoItem` | `(firestoreId: String): Unit` | 완료 처리. 현재 문서를 읽어 `completeOccurrence` 적용 후 저장. 결과가 DONE(비반복 완료·반복 종료)이면 자식을 모두 DONE으로 cascade(무알림). 목록 사이클 완료(`advanceStatus`)와 리마인더 완료가 모두 이 메서드를 탄다 |

자식(SUB) 목록 조회는 별도 Repository 메서드 없이 `getTodoItems()`가 반환하는 MAIN+SUB 전체에서
`TodoScreen`이 `mainTodoId`로 그룹핑(`subsByParent`)해 부모별 자식을 화면에 전달한다.

도메인 모델(`TodoItem.kt`)의 확장 함수:

| 함수 | 설명 |
|------|------|
| `TodoItem.completeOccurrence(Instant)` | 완료 1회 처리. 반복이면 다음 회차로 in-place 전진(상태 리셋), 아니면 `DONE` 고정 (FEATURE 참고) |
| `TodoItem.withRecurrenceAnchored()` | `MONTHLY`/`YEARLY`를 처음 저장할 때 `dueAt`의 day-of-month를 `anchorDay`로 박아둔다 |
| `RecurrenceRule.nextDueAt(Instant)` | 완료된 회차 기준 다음 회차 `dueAt` 계산 |

## 타 도메인과의 연관성

- `auth.AuthRepository` — `TodoViewModel`이 로그인 상태를 구독해 로그인 시에만 데이터 수집을 시작한다.
- `common.AppPreferences` — `TodoReminderWorker`가 알림 모드/사운드 설정을 읽어 알림 채널을 구성한다.
- `notification.ensureReminderChannel` — 리마인더 알림 채널 생성(공유 인프라).
- `user.UserRepository.getPushTokensByEmails` — 담당자 배정 push 생성 시 `assignee.emails`로
  대상 사용자의 토큰을 조회한다(→ [infra/user.md](../infra/user.md)).
- `push.PushRepository.createPush` — 조회한 토큰으로 `pushes` 문서를 만든다(→ [infra/push.md](../infra/push.md)).
- `functions`(Cloud Functions) — `pushes` 문서 생성 트리거가 실제 FCM 발송을 담당한다
  (대상 계산은 이제 이 도메인이 하므로 `functions`는 `assignee`·`lastEditedByUid`를 더 이상
  읽지 않는다, → [infra/functions.md](../infra/functions.md)).
- 다른 도메인 모델을 직접 참조하거나 참조당하지는 않는다(카테고리·태그 없음).

## Firestore 컬렉션

담당 Repository: `TodoFirestoreRepositoryImpl`

카테고리·태그가 제거되어(이슈 #71) `todo-items` 단일 컬렉션만 사용한다. `todo-categories`는
코드에 존재하지 않는다.

SUB 항목도 같은 `todo-items` 컬렉션에 평탄(flat)하게 저장한다. 자기참조(서브컬렉션) 없음.

### `todo-items` (TodoItem)

날짜/시각 필드는 모두 Firestore `Timestamp`로 저장되고, 읽을 때 `Instant`로 변환한다.
변환: 쓰기 `Timestamp(epochSecond, nano)`, 읽기 `Instant.ofEpochSecond(seconds, nanoseconds)`.

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| (문서 ID) | `firestoreId` | auto-ID |
| `type` | `type` | enum `name`. 없으면 `MAIN`(레거시 하위 호환) |
| `mainTodoId` | `mainTodoId` | 부모 문서 ID. `MAIN`이면 필드 없음 |
| `title` | `title` | |
| `memo` | `memo` | |
| `status` | `status` | enum `name` 문자열. 없으면 `isCompleted`로부터 유도(레거시 마이그레이션) |
| `assignee` | `assignee` | enum `name` 문자열. 알 수 없으면 `DEFAULT` |
| `isCompleted` | `isCompleted`(파생) | 쓰기 전용 미러(항상 `status == DONE`). status 없는 레거시 문서 읽기 폴백용 |
| `dueAt` | `dueAt` | `Timestamp` ↔ `Instant` |
| `reminderOffsetMinutes` | `reminderOffsetMinutes` | Firestore `Long` → `Int` |
| `priority` | `priority` | enum `name` 문자열. 알 수 없으면 `NONE` |
| `recurrence` | `recurrence` | 중첩 맵(아래 표). 없으면 `null` |
| `completionHistory` | `completionHistory` | `List<Timestamp>` ↔ `List<Instant>` |
| `createdAt` | `createdAt` | `Timestamp` ↔ `Instant`. `addTodoItem`에서만 기록, 이후 갱신 안 함 |
| `completedAt` | `completedAt` | `Timestamp` ↔ `Instant` |
| `lastEditedByUid` | `lastEditedByUid` | 편집자 Firebase Auth `uid` 문자열. 모든 쓰기(add/update/complete) 시 주입. 서버 Cloud Functions가 자기 알림 제외에 사용(→ [infra/functions.md](../infra/functions.md)) |

### `todo-items.recurrence` (중첩 맵, RecurrenceRule)

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| `frequency` | `frequency` | enum `name`. 알 수 없으면 recurrence 전체를 `null`로 무시(로그 경고) |
| `interval` | `interval` | Firestore `Long` → `Int`. 없으면 `1` |
| `daysOfWeek` | `daysOfWeek` | `List<Long>` → `Set<Int>`. 없으면 `emptySet()` |
| `endAt` | `endAt` | `Timestamp` ↔ `Instant` |
| `anchorDay` | `anchorDay` | Firestore `Long` → `Int` |
