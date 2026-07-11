# functions(Cloud Functions) 인프라

Firebase Cloud Functions(Python)로 서버측 로직을 실행한다. 현재는 `todo-items` 쓰기 트리거로
담당자 배정 푸시(FCM)를 발송하는 단일 함수를 갖는다. 소스 경로: `functions/`(앱 코드와 별도).

## 책임

- 한다: `todo-items/{itemId}` 문서 쓰기(생성·수정) 시 담당자에게 FCM 푸시 발송. 발송 대상 계산
  (담당자 이메일 → `users` 문서 조회 → uid 집합), 편집자 본인 제외, 대상별 `pushToken.token` 조회,
  `messaging.send(...)`(firebase-admin Python) 호출.
- 하지 않는다: 푸시 수신·표시(→ Android `notification` 인프라), 토큰 저장(→ 앱이 `users` 문서에 저장),
  리마인더(→ `todo`의 WorkManager). 문서 필드의 원본 스키마 정의(→ 앱의 `TodoFirestoreRepositoryImpl`).

## 공개 API (트리거)

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `on_todo_item_written` | Firestore `on_document_written` 트리거 | `document="todo-items/{itemId}"`, `region="asia-northeast3"`. 담당자 배정 푸시 발송 |

런타임: Python 3.14(`firebase.json`의 `runtime: python314`). 의존성: `firebase_functions`,
`firebase-admin`(`functions/requirements.txt`). 추가 의존성 없음.

## 발송 대상 계산 규칙

앱의 `TodoAssignee`(이메일 매핑)·`TodoItem.lastEditedByUid`와 규칙이 일치해야 한다.

```
대상 = assignee(enum 이름) → 이메일 목록 → users 문서(email 일치)의 uid 집합
      # SHARED = 두 사용자, 개인 배정 = 해당 1인
대상 -= after.lastEditedByUid            # 편집자 본인 제외
각 대상의 users/{uid}.pushToken.token 이 있으면 발송(없으면 스킵)
```

- **스킵 조건**: 삭제(`after` 없음)면 종료. `before`와 `after`의 `assignee`·`title`이 모두 같으면
  발송하지 않는다(상태 순환·완료 전진 등 배정과 무관한 쓰기 제외).
- **생성/수정 분기**: `before`가 없으면 생성("새 할일이 등록되었습니다"), 있으면 수정
  ("할일이 수정되었습니다").
- **담당자 이메일 매핑**(앱 `TodoAssignee`와 동일): `SHARED` = 두 사용자,
  `JEON_JIHOON` = `supawer0728@gmail.com`, `KWON_YUKYEONG` = `fmx.yu.k@gmail.com`.
  이 매핑을 바꿀 때는 앱 `TodoAssignee.kt`와 `functions/main.py`를 함께 고친다.

## FCM 메시지 형식

앱의 `JkFirebaseMessagingService.onMessageReceived`는 `message.notification` 블록만 읽고,
백그라운드/종료 상태에서는 FCM이 `notification` 페이로드를 시스템 트레이에 자동 표시한다. 따라서
함수는 다음을 포함해 보낸다.

- `notification`(title/body) — 포그라운드·백그라운드·종료 모든 상태에서 표시되도록.
- `android.notification.channel_id = "todo_assignment"`(= `CHANNEL_ID_TODO_ASSIGNMENT`) — 종료
  상태 자동 표시 시 생성된 채널로 라우팅.

## 데이터 / 저장소

- 자체 컬렉션 없음. 읽기: `todo-items`(트리거 문서), `users`(email로 대상 조회, `pushToken` 읽기).

## 의존 관계

- 트리거 소스: `todo`의 `todo-items` 컬렉션 쓰기(앱 `TodoFirestoreRepositoryImpl`).
- 의존하는 것: Firebase Admin SDK(Firestore·Messaging), Cloud Functions for Firebase(Python).
- 배포: `firebase deploy --only functions`. 로그: `firebase functions:log`.

## 관련 결정 (ADR)

- [`doc/adr/60/01-last-edited-by-uid-for-self-notification-exclusion.md`](../../adr/60/01-last-edited-by-uid-for-self-notification-exclusion.md) — 편집자 UID 필드로 자기 알림 제외
- [`doc/adr/60/02-assignment-push-via-cloud-functions.md`](../../adr/60/02-assignment-push-via-cloud-functions.md) — 담당자 배정 푸시를 Cloud Functions로 발송(메시지 형식 포함)
