# functions(Cloud Functions) 인프라

Firebase Cloud Functions(Python)로 서버측 로직을 실행한다. 이슈 #89부터 이 인프라는 **발송 전담**이다.
`pushes` 컬렉션에 문서가 생성되면 그 문서에 이미 적재된 대상(토큰) 목록으로 FCM을 발송하고 결과를
문서에 기록하는 단일 함수만 갖는다. 발송 대상 계산(담당자 해석, 편집자 제외)은 앱의 `push`/`todo`가
담당한다(→ [push.md](push.md)). 소스 경로: `functions/`(앱 코드와 별도).

## 책임

- 한다: `pushes/{pushId}` 문서 생성 시 `status`가 `"pending"`이면, 문서의 `tokens` 목록 각각에
  `messaging.send(...)`(firebase-admin Python) 호출. 발송 후 문서에 `status`(`sent`/`failed`)·
  `sentAt`(서버 타임스탬프)·`results`(토큰별 결과)를 기록.
- 하지 않는다: 발송 대상(토큰) 계산·담당자 해석(→ 앱 `todo.TodoFirestoreRepositoryImpl`), 편집자
  본인 제외 판단(→ 앱), `pushes` 문서 생성(→ 앱 `push.PushRepositoryImpl`), 30일 지난 `pushes` 문서
  정리(→ 앱 `push.PushCleanupScheduler`), 푸시 수신·표시(→ Android `notification` 인프라). `users`
  컬렉션을 읽지 않는다(대상 조회 자체가 앱으로 이관되었으므로).

## 공개 API (트리거)

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `on_push_created` | Firestore `on_document_created` 트리거 | `document="pushes/{pushId}"`, `region="asia-northeast3"`. `status == "pending"`일 때만 발송 처리 |

런타임: Python 3.14(`firebase.json`의 `runtime: python314`). 의존성: `firebase_functions`,
`firebase-admin`(`functions/requirements.txt`). 추가 의존성 없음. 비용 제어를 위해
`set_global_options(max_instances=10)`으로 동시 실행 컨테이너 상한을 10으로 둔다.

## 발송 처리 규칙

```
문서.status != "pending" 이면 종료(스킵, 재처리 중복 방지)
각 tokens[i]에 대해:
  messaging.send(notification=title/body, android.notification.channel_id=문서의 channelId)
  결과를 results[]에 {token, success, error} 로 적재
  (한 토큰 실패가 다른 토큰 발송을 막지 않는다)
성공이 1건 이상이면 status="sent", 전부 실패(또는 tokens가 비어있음)면 status="failed"
문서 갱신: status, sentAt(SERVER_TIMESTAMP), results
```

앞 단계(담당자 이메일→uid 해석, 편집자 제외, `pushToken.token` 조회)는 이미 문서 생성 시점에
앱이 끝낸 상태이므로, 이 함수는 별도로 `users`나 `todo-items`를 조회하지 않는다.

## FCM 메시지 형식

`messaging.Message`에 `notification`(title/body)을 싣고, `android.notification.channel_id`을
지정해 보낸다(그래야 백그라운드/종료 상태에서도 FCM이 시스템 트레이에 자동 표시한다). title/body/
channelId는 모두 `pushes` 문서 필드값을 그대로 쓴다 — 이 함수는 채널 id의 의미(예: `todo_assignment`
= `notification.CHANNEL_ID_TODO_ASSIGNMENT`)를 모르며, 문서에 적힌 문자열을 그대로 전달할 뿐이다.

## 데이터 / 저장소

- 자체 컬렉션 없음. 읽기: `pushes`(트리거 문서). 쓰기: 같은 `pushes` 문서의 `status`/`sentAt`/`results`
  필드만 갱신(문서 생성은 하지 않음).

## 의존 관계

- 트리거 소스: `push`의 `pushes` 컬렉션 생성(앱 `PushRepositoryImpl.createPush`, → [push.md](push.md)).
- 의존하는 것: Firebase Admin SDK(Firestore·Messaging), Cloud Functions for Firebase(Python).
- 배포: `firebase deploy --only functions`. 로그: `firebase functions:log`.

## 관련 결정 (ADR)

- [`doc/adr/89/01-pushes-collection-send-only-functions.md`](../../adr/89/01-pushes-collection-send-only-functions.md) — `pushes` 컬렉션 기반으로 단순화, 대상 계산을 앱으로 이관
- [`doc/adr/60/02-assignment-push-via-cloud-functions.md`](../../adr/60/02-assignment-push-via-cloud-functions.md) — (대체됨 → ADR/89) 담당자 배정 푸시를 Cloud Functions가 대상까지 계산해 발송하던 구 아키텍처
- [`doc/adr/60/01-last-edited-by-uid-for-self-notification-exclusion.md`](../../adr/60/01-last-edited-by-uid-for-self-notification-exclusion.md) — 편집자 UID 필드(근거 갱신: 알림 제외 → 감사 기록. 이 함수는 더 이상 이 필드를 읽지 않는다)
