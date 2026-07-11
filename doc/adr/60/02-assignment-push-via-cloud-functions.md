# 담당자 배정 푸시를 Cloud Functions에서 발송하고, notification 페이로드 + 채널 라우팅으로 보낸다

**상태**: 결정됨
**날짜**: 2026-07-11

## 맥락

이슈 #60에서 TODO 담당자에게 실제 FCM 푸시를 발송해야 한다. 클라이언트 인프라(토큰 저장, 수신·표시
채널)와 담당자 모델(`TodoAssignee` 이메일 매핑)은 이미 완료되어 있었고, 남은 것은 "누구에게 언제
어떤 형식으로 보내는가"의 서버측 발송 로직이었다. 앱 별도 서버는 두지 않는 프로젝트 원칙 아래,
어디서 발송할지와 어떤 메시지 형식을 쓸지를 정해야 했다.

## 결정

### 발송 위치: Cloud Functions Firestore 트리거

`functions/main.py`의 `on_todo_item_written`(`on_document_written`,
`document="todo-items/{itemId}"`, `region="asia-northeast3"`)에서 발송한다. 대상 계산 규칙:

```
대상 = assignee(enum 이름) → 이메일 목록 → users 문서(email 일치)의 uid 집합
      # SHARED = 두 사용자, 개인 배정 = 1인
대상 -= after.lastEditedByUid            # 편집자 제외 (ADR 01)
각 대상의 users/{uid}.pushToken.token 이 있으면 발송
```

- 삭제(`after` 없음)면 종료. `before`·`after`의 `assignee`·`title`이 모두 같으면 발송 생략.
- 생성(`before` 없음)이면 제목 "새 할일이 등록되었습니다", 수정이면 "할일이 수정되었습니다".
- 담당자 이메일 매핑은 앱 `TodoAssignee`와 동일하게 유지한다(양쪽을 함께 수정).

### 메시지 형식: notification 페이로드 + Android 채널 라우팅

`messaging.Message`에 `notification`(title/body)을 싣고,
`android.notification.channel_id = "todo_assignment"`를 지정해 보낸다.

## 근거

- **트리거 발송**: 앱 서버가 없고, "담당자 지정 시 발송"이라는 트리거가 곧 `todo-items` 쓰기이므로
  Firestore 트리거가 자연스럽다. 발송용 서버 키를 클라이언트에 두지 않아 보안상 안전하다.
- **notification 페이로드**: 앱 `JkFirebaseMessagingService.onMessageReceived`는 포그라운드에서만
  호출되고 `message.notification`만 읽는다. 앱이 백그라운드/종료 상태면 `onMessageReceived`가
  호출되지 않으므로, FCM이 `notification` 페이로드를 시스템 트레이에 자동 표시하도록 해야
  "앱 강제종료 상태에서도 알림 도착"(완료 기준)을 만족한다. data-only 메시지는 종료 상태에서 앱을
  깨워 표시하는 것을 보장하지 못한다.
- **channel_id 지정**: Android 8+에서 백그라운드 자동 표시 시 채널이 필요하다. 앱이 만든
  `todo_assignment` 채널(`CHANNEL_ID_TODO_ASSIGNMENT`)로 라우팅해 일관된 중요도/표시를 얻는다.

## 검토한 대안

- **data-only 메시지 + 앱에서 표시**: 표시 로직을 앱이 완전히 통제할 수 있으나, 앱 종료 상태에서
  전달·표시가 보장되지 않아 완료 기준을 못 채운다. 채택하지 않음.
- **클라이언트 직접 발송**: [ADR 01 검토한 대안](01-last-edited-by-uid-for-self-notification-exclusion.md)
  참고. 서버 키 노출·일관성 문제로 제외.
- **HTTPS Callable/스케줄 함수**: 배정은 문서 쓰기에 종속된 이벤트이므로 별도 호출 API보다 쓰기
  트리거가 단순하고 누락이 없다.

## 예상 결과

- 서버측 푸시 발송 책임이 앱(`notification` 인프라)에서 Cloud Functions(`functions`)로 이동한다.
  `notification` 인프라 문서의 "서버측 푸시 발송 안 함" 경계를 갱신했다.
- 담당자 이메일 매핑이 앱 `TodoAssignee.kt`와 `functions/main.py` 두 곳에 존재한다. 매핑 변경 시
  두 곳을 함께 고쳐야 하는 중복 비용이 생기지만, 클라이언트-서버 언어가 달라 불가피하다. 각 파일
  주석과 `doc/dev/infra/functions.md`에 이 동기화 규칙을 명시했다.
- 발송 실패는 대상별로 격리해 로깅하며(한 대상 실패가 다른 대상 발송을 막지 않음),
  `firebase functions:log`로 실행/스킵/실패 사유를 추적한다.

## 후속 과제 (이번 범위 밖)

- 담당자 이메일 매핑의 단일 출처화(예: Firestore 설정 문서에서 앱·함수가 함께 읽기)는 이번 범위에
  포함하지 않는다. 가족 2인 고정 매핑이라 현재는 하드코딩 중복의 비용이 낮다.
