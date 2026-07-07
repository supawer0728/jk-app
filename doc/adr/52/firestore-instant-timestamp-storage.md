# TODO 날짜 필드의 Firestore 저장 방식: Timestamp

**상태**: 결정됨
**날짜**: 2026-07-08

## 맥락

`TodoItem`/`RecurrenceRule`은 `dueAt`, `endAt`, `createdAt`, `completedAt`,
`completionHistory` 등 여러 `java.time.Instant` 필드를 갖는다. 프로젝트 내 기존
Repository(Diary/Asset/Investment/Benchmark)는 날짜를 전부 `date: String`
(`yyyy-MM-dd`) 형태로만 저장해왔고, Firestore 네이티브 `Timestamp` 타입을 쓴
선례가 없어 이번에 새로 정해야 했다.

## 결정

`Instant` 필드는 `com.google.firebase.Timestamp`로 변환해 저장한다
(`Timestamp(instant.epochSecond, instant.nano)` ↔
`Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())`).
`toDate()`를 거치지 않고 `seconds`/`nanoseconds`를 직접 사용해 나노초 정밀도를 보존한다.

## 근거

- Firestore 콘솔에서 날짜/시간으로 바로 표시되어 디버깅이 쉽다.
- 이후 리마인더 Worker나 "마감 임박 항목" 조회처럼 서버 측 range 쿼리
  (`whereGreaterThan`/`whereLessThan`)가 필요한 시점에 인덱스를 그대로 활용할 수 있다.

## 검토한 대안

- **Epoch millis(Long)**: 변환 로직은 더 단순하지만 콘솔에서 숫자로만 보이고,
  range 쿼리 시에도 결과적으로 동일한 인덱스가 필요해 이점이 크지 않다.

## 예상 결과

- `TodoFirestoreRepositoryImpl`에 `Instant ↔ Timestamp` 변환 private
  extension이 추가된다.
- `AGENT.md`의 Firestore 컬렉션 표에 `todo-items`/`todo-categories` 행을
  추가할 때 이 저장 방식을 함께 기록한다.
