# 파생 쓰기의 푸시 억제를 앱 `TodoFirestoreRepositoryImpl`에서 강제하고, "단일 변경당 푸시 ≤1건"을 최우선 규칙으로 둔다

**상태**: 결정됨
**날짜**: 2026-07-18

## 맥락

이슈 #88은 자식 TODO 도입으로 파생 쓰기가 여럿 생긴다. cascade(부모 완료→자식 DONE,
부모 삭제→자식 삭제), 반복 부모 완료 시 자식 복제, 완료된 부모의 되돌림 등 한 번의 사용자
행동이 여러 `todo-items` 문서 쓰기를 유발한다.

이슈 #88 본문은 담당자 푸시가 Cloud Functions의 `on_todo_item_written`(문서 쓰기 트리거)에서
발송된다는 구 아키텍처를 전제로, "파생 쓰기가 푸시를 중복 유발하지 않게 트리거에서 걸러야
한다"고 기술했다. 그러나 이 구 아키텍처는 이슈 #89 머지로 **폐기**되었다(ADR 89/01). 현재는
대상 계산과 발송 트리거가 모두 앱으로 이관되어, 앱 `TodoFirestoreRepositoryImpl`이
`user.UserRepository`로 토큰을 조회하고 `push.PushRepository.createPush`로 `pushes` 문서를
생성하면, `functions/main.py`는 그 문서를 감지해 **이미 계산된 토큰 목록으로 발송만** 한다.

따라서 "파생 쓰기가 푸시를 중복시키지 않게 한다"는 요구를 어느 계층에서 강제할지 다시 정해야
했다.

## 결정

푸시 억제를 **앱 `TodoFirestoreRepositoryImpl`에서 강제**한다. `functions/main.py`는 발송
전담이므로 수정하지 않는다.

- 자식 생성 API는 알림 여부를 파라미터로 받는다: `addSubTodoItem(parentId, item, notify: Boolean = true)`.
  사용자가 직접 자식을 추가하면 `notify=true`로 푸시 1건, 반복 복제 등 파생 생성은 `notify=false`로
  무알림.
- cascade 완료(`cascadeCompleteChildren`)·cascade 삭제(`deleteTodoItem`/`deleteTodoItems`의 자식
  삭제)·반복 자식 복제·완료 부모 되돌림 등 **파생 쓰기 경로는 어떤 경우에도 `createPush`를 호출하지
  않는다**.
- 규칙 우선순위: **"어떤 단일 사용자 변경도 푸시 ≤1건"이 다른 모든 알림 규칙보다 우선한다.**
  담당자 지정 알림 등 개별 규칙이 푸시를 내보내려 해도, 그 변경이 파생 연쇄의 일부라면 억제가 이긴다.

## 근거

- **발송 지점이 앱이므로 억제 지점도 앱**: 현 아키텍처에서 푸시를 만드는 주체는 앱
  Repository(`createPush` 호출부)다. 억제를 여기서 하면 "푸시를 만들 수 있는 유일한 곳에서 만들지
  여부를 결정"하는 단일 지점이 되어, 규칙이 한 파일에 모인다.
- **Functions 무수정**: `functions/main.py`는 ADR 89/01에서 "이미 계산된 토큰으로 발송만" 하는
  역할로 축소됐다. 억제 로직을 Functions에 두면 발송 전담이라는 경계가 다시 무너지고, 앱이 만든
  `pushes` 문서를 서버가 재해석해야 해 책임이 이원화된다.
- **명시적 `notify` 플래그**: 같은 "자식 생성" 동작이라도 사용자 직접 추가와 복제는 알림 정책이
  다르다. 호출부가 의도를 플래그로 드러내면, 파생 경로에서 무알림을 빠뜨리는 실수를 코드 리뷰에서
  잡기 쉽다.
- **≤1 원칙 최우선화**: 가족 2인용 앱에서 한 번의 조작으로 여러 알림이 쏟아지는 것은 명백한 UX
  손상이다. 개별 알림 규칙보다 "한 변경 = 최대 한 알림"을 상위 불변식으로 두는 편이 규칙 충돌을
  단순하게 해소한다.

## 검토한 대안

- **Functions 트리거에서 억제(이슈 본문 전제)**: 근거 대상 아키텍처(`on_todo_item_written`)가 이미
  폐기되어 성립하지 않는다. 되살리면 ADR 89/01 결정을 뒤집는 것이라 제외.
- **파생 쓰기에 특수 마커 필드를 심고 발송 측에서 무시**: `pushes`/`todo-items` 문서에 "무알림"
  플래그를 심어 발송 계층이 거르는 방식. 앱-서버 양쪽이 같은 규약을 공유해야 하고, 앱이 처음부터
  `createPush`를 안 하면 되는 일을 굳이 서버까지 끌고 간다. 제외.
- **ViewModel에서 억제**: cascade·복제는 Repository 내부에서 원자적으로 일어나므로, ViewModel이
  모든 파생 쓰기 경로를 알기 어렵다. 억제를 쓰기 주체와 같은 계층(Repository)에 두는 편이 누락이
  적다. 제외.

## 예상 결과

- `TodoFirestoreRepository` 인터페이스에 `addSubTodoItem(..., notify)`가 추가되고, 파생 쓰기 경로는
  `createPush`를 호출하지 않는다는 불변식이 `TodoFirestoreRepositoryImpl`에 국소화된다.
- 담당자 푸시 억제·복제 무알림 규칙의 현재-사실은 `doc/dev/todo/FEATURE.md`에 반영한다.
- `functions/main.py`는 이번 이슈에서 변경되지 않는다. ADR 89/01의 "발송 전담" 경계가 유지된다.
- 테스트(`TodoAssignmentPushTest`)로 파생 경로가 푸시를 만들지 않음을 검증한다.
