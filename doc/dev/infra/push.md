# push 인프라

FCM 푸시 발송 요청을 `pushes` 컬렉션에 적재하는 공유 인프라. 발송 **대상(토큰) 계산**은 이 인프라가
아니라 각 feature(현재는 `todo`)가 담당하고, 이 인프라는 이미 계산된 결과(제목/본문/채널/토큰 목록)를
`pushes` 문서로 쓰는 것과 오래된 문서 정리만 담당한다. 실제 FCM 발송은 Cloud Functions
(`functions` 인프라, → [functions.md](functions.md))가 한다. 패키지 경로: `com.jkapp.push`

## 책임

- 한다: `pushes` 문서 생성(`status="pending"`), `createdAt` 30일 경과 `pushes` 문서 정리
  (`status` 무관 삭제), 정리 실행을 하루 1회로 제한하는 가드.
- 하지 않는다: 발송 대상(토큰) 계산·담당자 해석(→ feature의 Repository, 예
  `todo.TodoFirestoreRepositoryImpl`), 이메일→토큰 조회(→ `user.UserRepository`), 실제 FCM
  발송(→ `functions`), 정리를 "언제" 호출할지의 앱 라이프사이클 연동(→ `JkApp`), 마지막 정리
  날짜의 영속 저장(→ `common.AppPreferences`, 이 인프라는 get/set 콜백만 주입받는다).

## 공개 API

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `PushMessage` | data class | `title`, `body`, `channelId`, `tokens` — 대상이 이미 해석·제외 처리된 발송 요청 |
| `PushRepository` | 인터페이스 | `createPush(message: PushMessage): String`, `deleteExpiredPushes(threshold: Instant): Int` |
| `PushRepositoryImpl` | 클래스 | `pushes` 컬렉션 구현체 |
| `PushCleanupScheduler` | 클래스 | `runIfNeeded()` — 주입받은 `getLastCleanupDate`/`setLastCleanupDate` 콜백으로 마지막 실행일을 확인해 오늘 이미 실행했으면 건너뛰고, 아니면 `deleteExpiredPushes(30일 전)` 호출 후 오늘 날짜로 갱신 |

## 데이터 / 저장소

### `pushes` (`PushRepositoryImpl`)

| Firestore 필드 | 타입 | 쓰는 주체 | 설명 |
|----------------|------|-----------|------|
| (문서 ID) | | | auto-ID |
| `title` | String | 앱(`createPush`) | 알림 제목 |
| `body` | String | 앱 | 알림 본문 |
| `channelId` | String | 앱 | Android 알림 채널 id(예: `"todo_assignment"` = `notification.CHANNEL_ID_TODO_ASSIGNMENT`) |
| `tokens` | `List<String>` | 앱 | 발송 대상 FCM 토큰 목록. 편집자 제외 등 대상 계산이 끝난 최종 목록 |
| `status` | String | 앱: 생성 시 `"pending"` / Functions: 발송 후 `"sent"` 또는 `"failed"`로 갱신 | 소문자 문자열로 저장 |
| `createdAt` | Timestamp | 앱 | 생성 시각. 30일 정리 기준 필드 |
| `sentAt` | Timestamp? | Functions | 발송 처리 시각(서버 타임스탬프) |
| `results` | `List<Map>` | Functions | 토큰별 결과: `{token: String, success: Boolean, error: String?}`. 성공 시 `error`는 `null` |

`status` 판정 규칙(Functions가 기록): 토큰 중 1건 이상 성공하면 `"sent"`, 전부 실패하면(또는
`tokens`가 비어 있었다면) `"failed"`. `"pending"`은 앱이 생성한 직후이자 아직 Functions가 처리하지
않은 상태로, 정상 상황에서는 매우 짧게만 존재한다.

## 정리(30일) 정책

- `createdAt` 기준 30일이 지난 `pushes` 문서는 `status`와 무관하게 삭제한다
  (`PushRepositoryImpl.deleteExpiredPushes`). Firestore write batch 한도(500 연산)를 넘지 않도록
  오래된 순으로 500개씩 조회·삭제를 남은 문서가 없을 때까지 반복한다(장기 미실행 후 대량 누적 대비).
- 실행 시점: 앱 시작 또는 포그라운드 복귀 시, **로그인 상태에서 하루 1회** 시도 — `JkApp`의
  `ProcessLifecycleOwner` 관찰자가 호출한다. `pushes` 보안 규칙이 인증을 요구하므로 미로그인
  상태에서는 실행하지 않으며, 실행 중 예외(권한·네트워크 등)는 `JkApp`이 삼켜 앱을 크래시시키지 않는다.
- 하루 1회 가드: `PushCleanupScheduler.runIfNeeded()`가 `common.AppPreferences.lastPushCleanupDate`
  (`LocalDate`, DataStore)를 읽어 오늘 날짜와 같으면 즉시 반환한다. 다르면 정리를 실행하고
  `setLastPushCleanupDate(오늘)`로 갱신한다. 스케줄러 자체는 `AppPreferences`를 직접 참조하지 않고
  get/set 콜백(`suspend () -> LocalDate?`, `suspend (LocalDate) -> Unit`)만 주입받아, Android
  Context 의존 없이 JVM 단위 테스트가 가능하다.

## 이메일→토큰 조회와의 경계

`push` 패키지는 이메일이나 담당자 개념을 모른다. "이 할일의 담당자가 어떤 토큰을 가진 사용자인가"는
`user.UserRepository.getPushTokensByEmails(emails)`가 답하고, "그중 편집자 본인은 제외한다"는
호출한 feature(`todo.TodoFirestoreRepositoryImpl`)가 판단한다. `push`는 그렇게 완성된 토큰
목록만 받아 `pushes` 문서를 만든다.

## 의존 관계

- 사용하는 곳: `todo.TodoFirestoreRepositoryImpl`(담당자 배정 push 생성 시 `createPush` 호출),
  `JkApp`(`PushCleanupScheduler` 생성·라이프사이클 연결).
- 의존하는 것: Cloud Firestore SDK, `common.AppFirestore`(공유 인스턴스)·`await`, `common.AppPreferences`
  (정리 날짜 캐시는 `push` 바깥에서 콜백으로 주입받아 간접 의존).

## 관련 결정 (ADR)

- [`doc/adr/89/01-pushes-collection-send-only-functions.md`](../../adr/89/01-pushes-collection-send-only-functions.md) — `pushes` 컬렉션 도입과 대상 계산의 앱 이관 근거
- [`doc/adr/89/02-30-day-push-cleanup-policy.md`](../../adr/89/02-30-day-push-cleanup-policy.md) — 30일 정리 정책과 하루 1회 가드 방식
