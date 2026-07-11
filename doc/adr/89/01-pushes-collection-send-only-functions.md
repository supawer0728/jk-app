# 담당자 배정 푸시 발송을 `pushes` 컬렉션 기반으로 단순화하고, 대상 계산을 앱으로 이관한다

**상태**: 결정됨 (2026-07-11 초안 → 2026-07-12 확정)
**날짜**: 2026-07-11

## 맥락

이슈 #60(ADR 60/01, 60/02)에서는 Cloud Functions의 `on_todo_item_written` 트리거가 `todo-items`
쓰기를 감지해 담당자 이메일→`users` 문서 조회로 uid를 얻고, 편집자를 제외한 뒤 `pushToken.token`으로
FCM을 직접 발송했다. 즉 "대상이 누구인가"와 "어떻게 보내는가"가 모두 Cloud Functions 한 함수에
있었다. 이슈 #89는 이 구조를 다음 두 가지 이유로 재검토한다.

- 대상 계산 로직(담당자 이메일 매핑, 편집자 제외, 변경 비교)이 앱의 `TodoAssignee`·
  `TodoItem.lastEditedByUid`와 사실상 같은 도메인 지식인데도 Python(`functions/main.py`)에
  중복 구현되어 있어, 규칙을 바꿀 때 두 언어로 동기화해야 했다(ADR 60/02의 "후속 과제"에서도
  지적한 비용).
- Cloud Functions가 매 쓰기마다 `users` 컬렉션을 이메일로 조회하는 구조라, 발송 여부를 재현·감사
  하려면 Functions 로그(`firebase functions:log`)를 봐야 했고 발송 요청 자체가 영속 문서로 남지
  않았다.

## 결정

### 책임 재배치: 대상 계산은 앱, 발송은 Functions

- **대상 계산**(담당자 이메일 매핑 해석, 이전 문서와의 `assignee`/`title` 변경 비교, 편집자 본인
  제외, 이메일→토큰 조회)은 앱의 `todo.TodoFirestoreRepositoryImpl`과 `user.UserRepository`
  (`getPushTokensByEmails` 신설)가 수행한다.
- 앱은 계산이 끝난 최종 결과(제목/본문/채널/토큰 목록)를 새 `com.jkapp.push` 패키지의
  `PushRepository.createPush`로 `pushes` 컬렉션에 `status="pending"` 문서로 적재한다.
- `functions/main.py`는 `pushes/{id}` 문서 생성(`on_document_created`)만 트리거로 받아,
  `status == "pending"`이면 문서의 `tokens`로 그대로 FCM을 발송하고 `status`/`sentAt`/`results`를
  기록한다. 담당자·이메일·`users` 컬렉션을 더 이상 알지 못한다.
- 컬렉션 이름은 복수형 kebab-case 컨벤션(`todo-items`, `daily-assets` 등)에 맞춰 `pushes`로 하고,
  패키지 이름은 `com.jkapp.push`로 한다.

### 변경 비교 로직은 그대로 앱으로 이식

기존 Cloud Functions의 "before/after의 `assignee`·`title`이 모두 같으면 발송하지 않는다" 규칙을
동일하게 `TodoFirestoreRepositoryImpl`이 저장 전 `before` 문서를 읽어 비교하는 방식으로 이식한다.
상태 순환·완료 전진·삭제는 이 비교에서 자연히 걸러진다(별도 분기 불필요).

### `lastEditedByUid`는 필드·주입 로직 유지, 근거만 갱신

`lastEditedByUid` 필드와 `TodoFirestoreRepositoryImpl.withEditor()`는 그대로 둔다. 다만 이제
Cloud Functions가 이 필드를 읽지 않으므로(대상 계산이 앱으로 이동해, push 생성 시점에 앱이 이미
`currentUidProvider()`가 반환한 값을 알고 있다), 이 필드의 1차 존재 근거를 "서버가 편집자를 식별하기
위한 값"에서 "마지막 편집자를 남기는 감사(audit) 기록"으로 바꾼다. 편집자 제외 판별에 쓰이는 것은
여전히 사실이므로 그 용도는 부기 사실로 남긴다. [ADR 60/01](../60/01-last-edited-by-uid-for-self-notification-exclusion.md)은
결정 자체(필드 도입)가 번복된 것이 아니라 근거 서술만 갱신하는 것이라, 새 ADR로 대체하지 않고 해당
문서를 직접 갱신한다. [ADR 60/02](../60/02-assignment-push-via-cloud-functions.md)는 "Cloud
Functions가 대상까지 계산해 발송한다"는 아키텍처 자체가 바뀌므로 상태를 대체됨(→ 이 ADR)으로 표시한다.

## 근거

- **중복 로직 단일화**: 담당자 해석·변경 비교·편집자 제외를 Kotlin(앱) 한 곳에만 두면, Python
  코드(`functions/main.py`)를 함께 고칠 필요가 없어진다. Functions는 "무엇을 보낼지 이미 정해진
  문서를 그대로 보낸다"는 단순한 책임만 남는다.
- **감사 가능한 발송 요청**: `pushes` 문서 자체가 "언제 누구에게 무엇을 보내려 했는가"의 영속
  기록이 되어, Functions 로그 없이도 Firestore 콘솔에서 발송 이력을 확인할 수 있다.
- **테스트 용이성**: 대상 계산이 Kotlin으로 오면서 JVM 단위 테스트(`app/src/test`)로 변경 비교·
  편집자 제외 로직을 검증할 수 있다. 기존에는 이 로직이 Python에 있어 앱 테스트 스위트에서 검증할
  수 없었다.
- **앱 서버리스 원칙 유지**: 여전히 별도 서버는 두지 않는다. 발송(FCM 서버 키가 필요한 부분)만
  Cloud Functions에 남기고, 서버 키가 필요 없는 대상 계산은 클라이언트로 내려도 보안상 문제가 없다.

## 검토한 대안

- **현행 유지(Cloud Functions가 전부 계산)**: 로직 중복·테스트 불가 문제가 이슈 #89의 동기이므로
  채택하지 않음.
- **대상 계산을 Functions에 남기고 컬렉션만 `pushes`로 개명**: 이름만 바뀔 뿐 중복·테스트 문제가
  그대로라 목적을 달성하지 못함.
- **토큰 대신 uid 목록을 `pushes`에 저장하고 Functions가 토큰을 재조회**: Functions가 다시 `users`를
  읽어야 해 "발송 전담" 경계가 흐려지고, 앱이 이미 아는 토큰을 굳이 서버에서 다시 조회하는 낭비가 있어
  채택하지 않음. 앱이 이미 편집자 제외까지 끝낸 토큰 목록을 문서에 직접 담는 편이 더 단순하다.

## 예상 결과

- `functions/main.py`가 `users`/`todo-items`를 더 이상 읽지 않고, `pushes` 문서 갱신만 한다.
  담당자 이메일 매핑(`ASSIGNEE_EMAILS`)도 Python에서 제거된다.
- `TodoFirestoreRepositoryImpl`, `UserRepositoryImpl`, 신규 `com.jkapp.push` 패키지가 대상 계산·
  push 생성 책임을 진다.
- `pushes` 컬렉션의 보안 규칙·인덱스는 Firebase 콘솔에서 수동 관리한다(저장소에 `firestore.rules`/
  `firestore.indexes.json`을 추가하지 않는다는 기존 방침 유지). 필요한 규칙·인덱스는 PR 본문에
  메모한다.

## 후속 과제 (이번 범위 밖)

- 담당자 이메일 매핑의 단일 출처화(예: Firestore 설정 문서)는 이번 범위에 포함하지 않는다
  (ADR 60/02의 동일 과제가 여전히 유효).
