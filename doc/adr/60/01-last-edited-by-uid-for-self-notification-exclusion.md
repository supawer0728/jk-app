# 자기 알림 제외를 위해 TodoItem에 편집자 UID(lastEditedByUid)를 저장한다

**상태**: 결정됨
**날짜**: 2026-07-11

> **후속 (이슈 #89)**: 이 필드(`lastEditedByUid`)와 주입 로직(`withEditor()`)은 유지되나, 그
> 1차 존재 근거는 이슈 #89에서 "서버가 편집자를 식별하기 위한 값"에서 "마지막 편집자 감사(audit)
> 기록"으로 전환되었다. 근거 전환의 상세는 [ADR 89/01](../89/01-pushes-collection-send-only-functions.md)을
> 참조한다. 아래 본문은 결정 시점(이슈 #60)의 기록이며 이후 고치지 않는다.

## 맥락

이슈 #60에서 담당자에게 배정 푸시를 보낼 때 "자기 자신에게는 알림을 보내지 않는다"는 정책을
구현해야 한다. 발송 주체는 `todo-items` 쓰기에 반응하는 Firestore 트리거(Cloud Functions)다.
그런데 Firestore 문서 쓰기 트리거는 **누가 그 문서를 썼는지(어느 사용자가 저장 요청을 보냈는지)**
를 알 수 없다. 트리거 컨텍스트에는 변경 전/후 문서 데이터만 있고 호출자 신원이 없다.

## 결정

`TodoItem`에 마지막으로 저장한 사용자의 Firebase Auth `uid`를 담는 `lastEditedByUid: String?`
필드를 추가하고, Firestore `todo-items` 문서에 같은 이름으로 저장한다. 앱의
`TodoFirestoreRepositoryImpl`은 모든 쓰기 경로(`addTodoItem`/`updateTodoItem`/`completeTodoItem`)
에서 현재 로그인 사용자의 uid를 이 필드에 주입한다. 주입 지점은 생성자 파라미터
`currentUidProvider: () -> String?`(기본값 `FirebaseAuth.getInstance().currentUser?.uid`)로 분리해
테스트에서 대체할 수 있게 한다.

발송 로직은 대상 사용자 집합에서 `after.lastEditedByUid`에 해당하는 사용자를 제외한다.

## 근거

- 트리거가 호출자 신원을 모르는 제약을, "문서 자체에 편집자 신원을 남긴다"는 방식으로 우회한다.
  문서에 이미 실린 데이터만으로 대상 계산이 완결되므로 별도 조회나 Auth 컨텍스트가 필요 없다.
- uid는 기기·세션과 무관한 안정적 사용자 식별자다. 이메일도 후보였으나, 발송 대상 계산이
  이메일→uid 조회를 이미 수행하므로(담당자 이메일 매핑) 제외 판정도 uid로 통일하는 편이 단순하다.
- 주입을 Repository 계층에 두면 UI/폼(`TodoFormScreen`)이 편집자 개념을 몰라도 되고, 모든 쓰기
  경로가 한 곳에서 일관되게 uid를 남긴다.

## 검토한 대안

- **Auth 트리거·호출자 컨텍스트 사용**: Firestore `on_document_written`은 호출자 uid를 제공하지
  않는다. Firestore 보안 규칙의 `request.auth`는 규칙 평가 시점에만 있고 트리거로 전달되지 않는다.
  채택 불가.
- **클라이언트가 발송까지 담당**: 앱이 상대 토큰을 직접 조회해 FCM을 보내면 서버 없이 되지만,
  발송용 서버 키를 클라이언트에 두는 보안 문제와 오프라인/다중 기기 일관성 문제가 있다. 배정 로직을
  서버(Functions)에 두는 [ADR 02](02-assignment-push-via-cloud-functions.md)와 상충한다.
- **편집자 정보를 별도 컬렉션/이벤트 로그에 기록**: 트리거가 추가 조회를 해야 하고, 문서와 편집자
  기록의 정합성(경합) 문제가 생긴다. 문서 필드 한 개로 끝나는 방식이 더 단순하다.

## 예상 결과

- `todo-items` 문서에 `lastEditedByUid` 필드가 늘어난다. 이 필드가 없는 레거시 문서는 읽을 때
  `null`이 되며, 발송 로직은 "제외 대상 없음"으로 처리하므로(편집자를 못 빼서 자기 자신도 대상에
  포함될 수 있음) 안전한 폴백이다. 신규/수정 문서부터는 항상 채워진다.
- 상태 순환·완료 전진 등 배정과 무관한 쓰기도 `lastEditedByUid`를 갱신하지만, 이런 쓰기는
  `assignee`·`title`이 그대로라 발송 로직이 스킵하므로 알림에 영향을 주지 않는다.
