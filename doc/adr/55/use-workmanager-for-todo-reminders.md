# TODO 마감일 리마인더 스케줄러로 WorkManager 채택

**상태**: 결정됨
**날짜**: 2026-07-11

## 맥락

TODO에 마감일 리마인더가 필요하다. 앱에는 알림 예약 인프라(AlarmManager/WorkManager/NotificationManager)가
전혀 없었고, `minSdk 35 / targetSdk 37`로 최신 정책(런타임 알림 권한 필수, 정확 알람 정책 강화)이 적용된다.
요구사항은 "마감일 몇 분/시간/일 전" 수준의 리마인더로, 초 단위 정밀도는 필요하지 않다.

## 결정

리마인더 예약을 **WorkManager**(`androidx.work:work-runtime-ktx`)로 구현한다.

- `TodoReminderSchedulerImpl`이 `enqueueUniqueWork(name = "todo-reminder-<itemId>", REPLACE)`로 예약,
  `cancelUniqueWork`로 취소한다. 예약 시각(`dueAt - offset`)이 이미 과거면 예약하지 않고 기존 예약만 취소한다.
- `TodoReminderWorker : CoroutineWorker`가 예약 시각에 실행되어 Firestore에서 항목을 다시 조회하고,
  여전히 존재하며 미완료일 때만 알림을 표시한다(예약 후 삭제/완료/다른 기기 변경분 반영).
- 예약/취소 판정 로직은 순수 함수로 추출해 JVM 단위 테스트로 검증한다.

## 근거

- **재부팅/앱 종료 후 재등록을 라이브러리가 자동 처리**한다. AlarmManager는 `BOOT_COMPLETED` 수신 후
  직접 재등록을 구현해야 한다.
- `SCHEDULE_EXACT_ALARM`은 Android 14+ 정책상 알람시계/캘린더 앱이 아니면 심사 리스크가 크다. 마감일
  리마인더는 정확 알람이 필요할 만큼 시간 임계적이지 않다.
- `enqueueUniqueWork` + `ExistingWorkPolicy.REPLACE`로 재예약이 단순해진다.

## 검토한 대안

- **AlarmManager (`setExactAndAllowWhileIdle`)**: 정확한 시각 보장. 그러나 정확 알람 권한 정책 리스크와
  `BOOT_COMPLETED` 재등록 직접 구현 부담이 크고, 요구되는 정밀도를 초과한다.
- **FCM 서버 푸시 예약**: 별도 서버/스케줄러가 필요하다. 이 앱은 "별도 서버를 두지 않는다"는 원칙이라 부적합.

## 예상 결과

- 알림 시각은 **근사치**다. Doze/앱 대기 상태에서 `setInitialDelay`는 최소 지연이라, 유지보수 창까지
  지연되어 예약 시각보다 늦게 표시될 수 있다. 마감일 리마인더 용도로는 수용 가능한 트레이드오프다.
- 조회 실패 시 재시도는 `runAttemptCount`로 상한을 둬(초기 시도 포함 최대 3회), 낡은 알림이 수 시간 뒤
  뜨는 것을 막는다.
- 오프라인 상태에서는 Firestore 영속 캐시를 읽으므로, 다른 기기에서 완료된 항목이 캐시에 미반영이면
  일시적으로 알림이 뜰 수 있다(온라인 시 자가 교정).
