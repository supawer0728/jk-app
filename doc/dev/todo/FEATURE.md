# todo(오늘의 할일) 기능

가족 공용 할일을 3단계 상태로 관리하고, 담당자·우선순위·마감일시·반복·마감 리마인더를 붙여
목록에서 필터링·정렬해 본다. 반복 항목은 완료 시 다음 회차로 자동 전진한다.

## 비즈니스 규칙

- **3단계 상태**: 완료/미완료 대신 `NOT_STARTED → IN_PROGRESS → DONE`을 쓴다. `isCompleted`는
  `status == DONE`의 파생값이며, 마이그레이션 중 구버전 코드와 어긋나지 않게 Firestore에 병기한다.
  강제 위치 `TodoItem.isCompleted`, `TodoFirestoreRepositoryImpl.toMap`. → ADR/71
- **레거시 상태 유도**: `status` 필드가 없는 옛 문서는 `isCompleted`로부터 상태를 유도한다
  (완료면 `DONE`, 아니면 `NOT_STARTED`; `IN_PROGRESS`는 신규 개념이라 과거 데이터에 없다).
  강제 위치 `todoStatusFromLegacyCompleted`, `DocumentSnapshot.toTodoItem`.
- **담당자 고정 목록**: 담당자는 `SHARED`/`KWON_YUKYEONG`/`JEON_JIHOON` 3종 enum으로 고정한다.
  기본값은 `SHARED`. 강제 위치 `TodoAssignee`. → ADR/71
- **생성 시각 불변**: `createdAt`은 `addTodoItem`에서만 부여하고 이후 `toMap`에 포함하지 않아
  수정/완료 시 덮어쓰이지 않는다. 강제 위치 `TodoFirestoreRepositoryImpl.addTodoItem`/`toMap`.
- **anchorDay 고정**: `MONTHLY`/`YEARLY` 반복을 처음 저장할 때 `dueAt`의 day-of-month를
  `anchorDay`로 박아 말일 클램프 누적(drift)을 막는다. 강제 위치 `TodoItem.withRecurrenceAnchored`
  (add/update 진입점에서 호출). → ADR/52
- **리마인더 예약 조건**: `!isCompleted && dueAt != null && reminderOffsetMinutes != null`일 때만
  예약한다. 강제 위치 `TodoViewModel.shouldScheduleReminder`.
- **리마인더 재예약 규칙**: 상태 변경·수정 시 이전 예약을 항상 `cancel`한 뒤, 결과가 미완료로 남을
  때만(반복 다음 회차 포함) 다시 `schedule`한다. 삭제 시에는 `cancel`만 한다.
  강제 위치 `TodoViewModel.advanceStatus`/`updateTodoItem`/`deleteTodoItem`.
- **과거 시각 예약 스킵**: 예약 시각(마감 − 오프셋)이 이미 지났으면 예약하지 않고 남은 이전 예약만
  취소한다. 강제 위치 `TodoReminderSchedulerImpl.schedule`, `shouldEnqueueReminder`.
- **알림 권한**: 리마인더를 켜는 프리셋 선택 시, 그리고 저장 시점에 `POST_NOTIFICATIONS` 권한을
  요청한다. 거부해도 저장은 진행하고 알림만 표시되지 않으며 스낵바로 경고한다.
  강제 위치 `TodoFormScreen`.
- **편집자 UID 주입**: 모든 쓰기(`addTodoItem`/`updateTodoItem`/`completeTodoItem`) 시 현재 로그인
  사용자의 `uid`를 `lastEditedByUid`에 주입한다. 이 필드는 "마지막으로 이 할일을 저장한 사람"을
  남기는 감사(audit) 기록이며, 담당자 배정 push를 생성할 때 대상에서 편집자 본인을 제외하는
  판별에도 함께 쓰인다. 강제 위치 `TodoFirestoreRepositoryImpl`(`currentUidProvider`). → ADR/60/01(근거 갱신)
- **담당자 배정 push 생성**: `addTodoItem`/`updateTodoItem`이 저장 직후 이전 문서(`before`, 없으면
  신규 생성)와 저장된 문서(`after`)를 비교해 `assignee` 또는 `title`이 실제로 바뀐 경우에만
  `push.PushRepository.createPush`로 `pushes` 문서를 만든다. 상태 순환·완료 전진(`completeTodoItem`
  경로 포함)은 `assignee`·`title`을 바꾸지 않으므로 이 비교에서 자연히 걸러져 push가 생성되지
  않는다. 삭제(`deleteTodoItem`)도 push를 만들지 않는다.
  발송 대상은 `assignee.emails`로 `user.UserRepository.getPushTokensByEmails`가 조회한 사용자
  (공동=`SHARED`이면 두 사용자, 개인 배정이면 1인)에서 `lastEditedByUid`(이 저장을 수행한 편집자)를
  뺀 집합이다. 결과적으로 `SHARED`는 편집자를 제외한 상대방만, 개인 배정은 편집자가 아닌 담당자만
  알림을 받는다(편집자 자신을 지정하면 대상이 없어 push를 만들지 않는다). 신규 생성이면 제목
  "새 할일이 등록되었습니다", 수정이면 "할일이 수정되었습니다"로 `PushMessage.title`을 채운다.
  강제 위치 `TodoFirestoreRepositoryImpl`(`maybeCreateAssignmentPush`,
  `shouldCreateAssignmentPush`/`assignmentPushTitle`). → ADR/89(구 Cloud Functions 로직을 앱으로 이식)
- **실제 발송**: `pushes` 문서 생성을 Cloud Functions가 감지해 `tokens` 목록으로 FCM을 발송한다.
  강제 위치 `functions/main.py`(→ [infra/functions.md](../infra/functions.md), [infra/push.md](../infra/push.md)). → ADR/89

## 계산 / 파생 값

- **다음 회차 `dueAt`** — `RecurrenceRule.nextDueAt(currentDueAt)`. 계산 위치 `RecurrenceRule.kt`.
  - `DAILY`: `currentDueAt + interval일`.
  - `WEEKLY` (`daysOfWeek` 비어 있음): `currentDueAt + (interval × 7)일`.
  - `WEEKLY` (요일 지정): 같은 주기 내 현재 요일보다 큰 첫 지정 요일로 전진. 없으면(마지막 지정
    요일을 지났거나 현재 요일이 비지정) `interval`주 뒤의 첫 지정 요일로 이동. 강제 위치
    `nextWeeklyDueAt`. → ADR/52
  - `MONTHLY`/`YEARLY`: `LocalDate.plusMonths/plusYears`로 전진 후 `anchorDay`(없으면 직전
    day-of-month)를 전진된 달의 실제 말일에 맞춰 클램프해 복원. 강제 위치 `plusCalendarUnit`. → ADR/52
- **반복 종료 판정** — 다음 회차 `nextDueAt`이 `endAt`을 넘으면 반복 종료로 보고 `DONE` 고정.
  계산 위치 `TodoItem.completeOccurrence`.
- **`isCompleted`** — `status == DONE`. 계산 위치 `TodoItem.isCompleted`.
- **목록 정렬** — 1) 마감시각 가까운 순(`nullsLast`), 2) 상태 `ordinal`(미진행→진행중→완료).
  계산 위치 `TodoViewModel.sortItems`. → ADR/71
- **리마인더 발화 시각** — `reminderTriggerAt = dueAt − (offsetMinutes × 60초)`.
  계산 위치 `TodoReminderScheduler.kt`.

## 상태 전이

목록의 상태 사이클 버튼은 `advanceStatus`로 다음처럼 순환한다(강제 위치 `TodoViewModel.advanceStatus`).

- `NOT_STARTED → IN_PROGRESS` — 사이클 버튼 클릭(단순 상태 변경).
- `IN_PROGRESS → DONE` (비반복) — 사이클 버튼 클릭. `completedAt = now`로 완료 고정.
- `IN_PROGRESS → (다음 회차)` (반복) — `completeOccurrence` 적용. `dueAt`을 다음 회차로 전진,
  `status`는 `NOT_STARTED`로 리셋, 지나간 `dueAt`을 `completionHistory`에 적재, `completedAt = now`.
  단, 다음 회차가 `endAt`을 넘으면 반복 종료로 `dueAt`을 현재 값으로 유지한 채 `DONE` 고정.
- `DONE → NOT_STARTED` — 사이클 버튼 클릭(완료 재개). `completedAt = null`.

`completeTodoItem`(Repository) 경로는 항상 `completeOccurrence`를 적용하며, 반복이 없으면
`DONE`, 있으면 다음 회차 전진(또는 종료 시 `DONE`) 규칙을 동일하게 따른다.

## 유효성 검증

- 제목이 공백이면 저장 버튼 비활성화 — `TodoFormScreen.isValid`(`title.isNotBlank()`).
- 수정 모드에서 기존 항목이 로드되기 전에는 저장 비활성화 — `isDataReady`.
- 마감일시 입력은 12자리(`yyyyMMddHHmm`)가 모두 채워지고 유효한 날짜/시각일 때만 `dueAt`으로
  파싱, 그 전에는 `null` — `digitsToInstant`. 12자리인데 파싱 실패면 에러 표시.
- 마감일시가 없으면 리마인더 프리셋 선택 불가(드롭다운 비활성), 저장 시 `reminderOffsetMinutes`를
  `null`로 강제 — `TodoFormScreen`.
- 반복 `interval`은 최소 1로 보정(`coerceAtLeast(1)`) — `RecurrenceSettingDialog`.
- `updateTodoItem`은 `firestoreId`가 없으면 `IllegalArgumentException` — `TodoFirestoreRepositoryImpl`.

## 주요 플로우

1. **목록 조회**: 로그인 상태 → `TodoViewModel`이 `authRepository.observeAuthState()` 구독 →
   로그인 시 `startDataCollection`이 `getTodoItems()`를 수집해 `TodoUiState.Success` 방출 →
   `visibleItems`가 상태/담당자 필터 + 정렬을 적용해 `TodoScreen`이 렌더.
2. **목록 필터**: 상태 필터(오늘/전체/반복/완료) + 담당자 필터(다중 선택). 강제 위치
   `filterByStatus`/`filterByAssignee`.
   - 오늘: `status != DONE` && (`dueAt` 없음 또는 오늘). 전체: `status != DONE`.
   - 반복: `recurrence != null`. 완료: `recurrence == null && status == DONE`
     (반복 항목은 완료 시 리셋되므로 완료 탭에서 제외).
3. **신규 저장**: `TodoScreen` FAB → `TodoFormScreen` → `TodoViewModel.addTodoItem` →
   `repository.addTodoItem`(anchorDay 고정 + `createdAt` 부여) → 예약 조건 충족 시
   `reminderScheduler.schedule` → `saveCompleted`로 폼 닫기.
4. **수정**: 카드 클릭 → `TodoFormScreen`(수정 모드) → `updateTodoItem` →
   이전 리마인더 `cancel` 후 필요 시 재 `schedule`.
5. **상태 순환**: 카드의 사이클 버튼 → `advanceStatus` → `updateTodoItem` →
   리마인더 `cancel` 후, 결과가 미완료면 재 `schedule`("상태 전이" 참고).
6. **삭제**: 삭제 아이콘 → 확인 다이얼로그 → `deleteTodoItem` → 리마인더 `cancel`.
7. **리마인더 발화**: WorkManager가 예약 시각(`dueAt − offset`)에 `TodoReminderWorker` 실행 →
   `getTodoItemOnce`로 항목 재조회 → 여전히 존재하고 미완료면(`shouldShowReminderNotification`)
   알림 표시. 조회 실패 시 최대 3회까지만 재시도(낡은 알림 방지). 재부팅/앱 종료 후에도
   WorkManager가 예약을 유지한다. → ADR/55
8. **담당자 배정 푸시**: 기기 A가 담당자를 지정해 저장(`lastEditedByUid = A`) →
   `TodoFirestoreRepositoryImpl`이 저장 전 `before` 문서를 읽고 저장 후 `after`와 비교 →
   `assignee`·`title` 변경이 있으면 `user.getPushTokensByEmails(assignee.emails)`로 대상 조회 →
   편집자(A) 제외 → 남은 토큰으로 `push.PushRepository.createPush`가 `pushes` 문서 생성 →
   Cloud Functions가 `pushes` 문서 생성을 감지해 FCM 발송 → 상대 기기에 문서의 `channelId`
   (`todo_assignment`) 채널로 시스템 알림 도착. 상세는 [infra/push.md](../infra/push.md),
   [infra/functions.md](../infra/functions.md). → ADR/89

## 관련 결정 (ADR)

- [`doc/adr/71/todo-status-3state-migration.md`](../../adr/71/todo-status-3state-migration.md) — 완료 여부를 3단계 상태로 확장·마이그레이션
- [`doc/adr/71/remove-todo-category-and-tags.md`](../../adr/71/remove-todo-category-and-tags.md) — 카테고리·태그 제거
- [`doc/adr/52/firestore-instant-timestamp-storage.md`](../../adr/52/firestore-instant-timestamp-storage.md) — 날짜 필드 Firestore Timestamp 저장
- [`doc/adr/52/weekly-recurrence-advance-algorithm.md`](../../adr/52/weekly-recurrence-advance-algorithm.md) — WEEKLY 다중 요일 다음 회차 전진 알고리즘
- [`doc/adr/52/monthly-yearly-anchor-day-drift.md`](../../adr/52/monthly-yearly-anchor-day-drift.md) — MONTHLY/YEARLY 말일 클램프 누적(anchor drift) 방지
- [`doc/adr/55/use-workmanager-for-todo-reminders.md`](../../adr/55/use-workmanager-for-todo-reminders.md) — 마감 리마인더 스케줄러로 WorkManager 채택
- [`doc/adr/60/01-last-edited-by-uid-for-self-notification-exclusion.md`](../../adr/60/01-last-edited-by-uid-for-self-notification-exclusion.md) — 편집자 UID 필드 도입(근거 갱신: 알림 제외 → 감사 기록 + push 생성 시 제외 판별)
- [`doc/adr/60/02-assignment-push-via-cloud-functions.md`](../../adr/60/02-assignment-push-via-cloud-functions.md) — (대체됨 → ADR/89) 담당자 배정 푸시를 Cloud Functions가 대상까지 계산해 발송하던 구 아키텍처
- [`doc/adr/89/01-pushes-collection-send-only-functions.md`](../../adr/89/01-pushes-collection-send-only-functions.md) — `pushes` 컬렉션 기반으로 단순화, 대상 계산(assignee/title 변경 비교)을 앱으로 이관
- [`doc/adr/89/02-30-day-push-cleanup-policy.md`](../../adr/89/02-30-day-push-cleanup-policy.md) — `pushes` 30일 정리 정책
