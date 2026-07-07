# MONTHLY/YEARLY 반복의 말일 클램프 누적(anchor drift) 방지

**상태**: 결정됨
**날짜**: 2026-07-08

## 맥락

완료 체크 시 `dueAt`을 in-place로 다음 회차로 전진시키는 구조(`RecurrenceRule.nextDueAt`)에서
MONTHLY/YEARLY는 매번 "직전 dueAt의 day-of-month"를 기준으로 `plusMonths`/`plusYears`를 호출한다.
그런데 이 값은 이전 클램프의 결과일 수 있어, 짧은 달을 연속으로 지나면 원래 지정한 일자가
영구적으로 사라진다. 예: 매월 31일 반복 → 1/31 완료 시 2/28(클램프) → 2/28 완료 시 3/28(원래
31일을 복원하지 못함). 코드 리뷰에서 이 누적 클램프(anchor drift)가 MEDIUM 이슈로 지적되었다.

## 결정

`RecurrenceRule`에 `anchorDay: Int?` 필드를 추가한다. MONTHLY/YEARLY 반복을 저장할 때
(`TodoItem.withRecurrenceAnchored()`, `TodoFirestoreRepositoryImpl.addTodoItem`/`updateTodoItem`
에서 호출) `dueAt`의 day-of-month를 `anchorDay`에 한 번 고정해두고, 이후 `nextDueAt`은 전진된
달의 실제 말일에 맞춰 `anchorDay`로 day-of-month를 복원한다. 예: anchorDay=31 → 1/31 → 2/28
(클램프) → 3/31(복원) → 4/30(클램프) → 5/31(복원). `anchorDay`가 없는 기존 데이터는 직전
동작(클램프 누적)을 그대로 유지한다.

## 근거

- 사용자가 "매월 31일"처럼 지정한 의도가 짧은 달을 한 번 지났다고 영구히 사라지면 안 된다.
- 일반적인 반복 규칙(RRULE 등)도 원래 지정일을 기준으로 매번 클램프를 다시 계산하는 방식을
  쓴다.
- `anchorDay`가 없을 때는 기존 동작을 그대로 유지하므로 하위 호환에 문제가 없다.

## 검토한 대안

- **클램프 누적을 그대로 두고 문서화만**: 구현은 더 단순하지만, 매월 말일 반복 같은 실제
  사용 사례에서 반복 주기가 계속 하루씩 앞당겨지는 체감상 버그로 남는다.

## 예상 결과

- `RecurrenceRule.anchorDay`가 Firestore `todo-items.recurrence.anchorDay` 필드로 저장된다.
- `TodoFirestoreRepositoryImpl.addTodoItem`/`updateTodoItem`이 저장 전 `withRecurrenceAnchored()`
  를 호출해 최초 저장 시점에 `anchorDay`를 채운다.
