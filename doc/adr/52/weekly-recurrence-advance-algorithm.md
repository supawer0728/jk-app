# WEEKLY 반복(daysOfWeek 다중 요일)의 다음 회차 전진 알고리즘

**상태**: 결정됨
**날짜**: 2026-07-08

## 맥락

`RecurrenceRule.frequency == WEEKLY`이고 `daysOfWeek`(예: 월/수/금)가
2개 이상 지정된 경우, 완료 체크 시 `dueAt`을 어떤 요일로 전진시킬지가
불명확했다. 단순히 `dueAt.plusWeeks(interval)`만 적용하면 "주 3회" 같은
반복 패턴을 표현할 수 없다.

## 결정

같은 주(interval 배수 주) 내에서 `daysOfWeek` 중 현재 `dueAt`보다 뒤에
오는 가장 가까운 요일로 전진한다. `daysOfWeek`의 마지막 지정 요일을 지나면
`interval`주 뒤 첫 지정 요일로 이동한다. 요일 인코딩은 `java.time.DayOfWeek`
(`getValue()`, 1=월요일~7=일요일) 값을 그대로 사용한다.

예: `daysOfWeek = {MON, WED, FRI}`, `interval = 1` → 월요일 완료 시 같은 주
수요일로 전진, 금요일 완료 시 다음 주(1주 뒤) 월요일로 전진.

## 근거

- "주 3회" 같은 실제 반복 UX(Todoist, Google Tasks 등)와 일치하는 동작이다.
- `daysOfWeek`가 비어 있으면 단순히 `dueAt.plusWeeks(interval)`로 폴백해
  기존 단일 요일 반복과 동일하게 동작한다.

## 검토한 대안

- **daysOfWeek 무시, interval 주 단위로만 전진**: 구현은 단순하지만
  `daysOfWeek` 필드가 저장은 되어도 실제 반복 계산에 반영되지 않아 이슈
  본문의 "WEEKLY만" 필드 취지와 맞지 않는다.

## 예상 결과

- `TodoFirestoreRepositoryImpl`(또는 별도 유틸)에 요일 인덱스 기반 전진
  계산 함수가 추가되고, 월말/연말 경계 케이스와 함께 유닛테스트로 검증한다.
