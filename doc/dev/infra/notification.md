# notification 인프라

Firebase Cloud Messaging(FCM) 푸시 메시지를 수신·표시하고, 알림 채널을 생성하며, 기기별 푸시
토큰을 관리한다. 패키지 경로: `com.jkapp.notification`

## 책임

- 한다: FCM 메시지 수신 시 알림 표시, 새 토큰 발급(`onNewToken`) 시 갱신, 앱 시작 시 알림 채널
  등록, 리마인더 설정(방식·알림음) 조합별 채널 지연 생성, 현재 토큰과 다를 때만 Firestore에 저장.
- 하지 않는다: 푸시 토큰의 실제 Firestore 저장(→ `user` 인프라 경유), 리마인더 스케줄링(→ `todo`
  의 WorkManager), 알림 설정값의 저장(→ `common.AppPreferences`), 서버측 푸시 발송.

## 공개 API

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `JkFirebaseMessagingService` | `FirebaseMessagingService` | `onMessageReceived`로 알림 표시(POST_NOTIFICATIONS 권한 확인), `onNewToken`으로 토큰 갱신 |
| `registerNotificationChannels(context)` | 함수 | 앱의 기본 알림 채널(할일 배정)을 한 곳에서 생성 |
| `ensureReminderChannel(context, mode, sound)` | 함수 | 방식·알림음 조합의 리마인더 채널을 없으면 생성하고 채널 id 반환. 채널은 생성 후 소리/진동이 불변이라 조합별로 분리 |
| `CHANNEL_ID_TODO_ASSIGNMENT`, `CHANNEL_ID_TODO_REMINDER` | 상수 | 채널 id(리마인더는 prefix) |
| `PushTokenManager` | 클래스 | `refreshTokenIfNeeded(uid)`, `updateTokenIfChanged(uid, token)` — 기존 토큰과 같으면 write를 건너뛰어 불필요한 Firestore 쓰기 방지 |

## 데이터 / 저장소

- 자체 컬렉션 없음.
- 푸시 토큰은 `user` 인프라의 `UserRepository`를 통해 `users` 문서의 `pushToken` 필드에
  저장·조회한다(→ [`user.md`](user.md)).

## 의존 관계

- 사용하는 곳: `JkApp`(앱 시작 시 `registerNotificationChannels`, 포그라운드 전환 시
  `PushTokenManager.refreshTokenIfNeeded`), `todo`의 리마인더 워커(`ensureReminderChannel`),
  `JkFirebaseMessagingService`는 매니페스트에 등록되어 FCM이 직접 호출.
- 의존하는 것: Firebase Messaging SDK, `user` 인프라(`UserRepository`), `common`의 알림 설정
  enum(`NotificationMode`, `NotificationSound`)과 `await` 확장, 앱 리소스(채널 이름/설명 문자열).

## 관련 결정 (ADR)

- [`doc/adr/55/use-workmanager-for-todo-reminders.md`](../../adr/55/use-workmanager-for-todo-reminders.md) — 리마인더 스케줄러로 WorkManager 채택(설정별 알림 채널과 연동)
