# TODO 완료 여부를 3단계 상태로 확장 및 마이그레이션

**상태**: 결정됨
**날짜**: 2026-07-11

## 맥락

기존 TODO는 완료 여부를 `TodoItem.isCompleted: Boolean` 하나로 표현했다(#53). 실사용에서
"아직 시작 안 함 / 진행 중 / 완료" 3단계 구분이 필요해졌다. Firestore `todo-items` 컬렉션에는
이미 `isCompleted` 필드로 저장된 기존 문서가 존재하므로, 스키마를 바꾸면서도 기존 데이터가
정상 표시되어야 한다(이슈 #71 완료 기준).

## 결정

- `TodoStatus { NOT_STARTED, IN_PROGRESS, DONE }` enum을 도입한다.
- `TodoItem.isCompleted: Boolean` 저장 필드를 `TodoItem.status: TodoStatus`로 대체하고,
  `isCompleted`는 `status == DONE` **파생 프로퍼티**로 유지한다(기존 호출부 호환).
- Firestore에는 새 필드 `status`(enum name 문자열)를 쓴다. 하위 호환을 위해 쓰기 시
  `isCompleted`(파생 Boolean)도 **함께** 기록한다.
- 읽기 시 마이그레이션: 문서에 `status`가 있으면 그대로 파싱하고, 없으면 `isCompleted`로부터
  `todoStatusFromLegacyCompleted()`로 유도한다(true→DONE, false→NOT_STARTED).
- 반복 항목 완료 로직(`completeOccurrence`)은 종료되지 않은 회차 전진 시 `NOT_STARTED`로
  리셋하고, 반복이 종료되면 `DONE`으로 고정한다.

## 근거

- enum + exhaustive `when`은 프로젝트 컨벤션(상태/결과는 sealed/enum)에 부합한다.
- `isCompleted` 파생 유지로 리마인더 예약 조건, 완료 필터 등 기존 로직 수정 범위를 최소화한다.
- `status`/`isCompleted` 동시 기록은, 마이그레이션 중 구버전 코드가 남아 있어도(다른 기기)
  완료 여부가 어긋나지 않게 하는 안전장치다. 별도 일괄 백필 배치가 필요 없다(지연 마이그레이션).

## 검토한 대안

- **문자열 상태값 대신 Boolean 2개(`isCompleted`, `isInProgress`)**: 상태가 상호배타적인데
  두 Boolean은 (완료 && 진행중) 같은 불가능한 조합을 허용해 버려 부적합.
- **일괄 마이그레이션 배치로 전체 문서에 status 백필**: 2인용 앱에 서버/함수가 없어 과하다.
  지연 마이그레이션(읽을 때 유도)으로 충분하다.

## 예상 결과

- 기존 문서는 읽는 순간 `status`로 해석되고, 수정/토글 시 `status` 필드가 기록된다.
- 향후 `isCompleted` 필드는 완전 전환이 끝나면 제거를 검토할 수 있다(현재는 병기 유지).
