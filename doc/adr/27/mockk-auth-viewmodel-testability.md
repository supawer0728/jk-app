# AuthViewModel 의존성 주입 및 mockk 도입

**상태**: 결정됨
**날짜**: 2026-06-30

## 맥락

이슈 #27 수정 과정에서 `AuthViewModel.firebaseAuthWithGoogle()` 내 버그를 발견했다.
`addOnSuccessListener` 콜백에서 `auth.currentUser`(전역 싱글톤)를 읽었는데, Firebase SDK가
해당 콜백 실행 시점에 `currentUser`를 아직 갱신하지 않은 경우 `null`이 반환되어
`_user.value = null` → `LaunchedEffect(user)` 가 LoginRoute를 유지하는 문제가 발생했다.

수정 코드(`authResult.user` 사용)를 검증하는 단위 테스트를 작성하려 했으나,
`AuthViewModel`이 `AndroidViewModel`을 상속하고 `FirebaseAuth.getInstance()` / `CredentialManager.create(app)`를
내부에서 직접 생성하여 JVM 환경에서 테스트가 불가능했다.

## 결정

1. `AuthViewModel`에 `auth`, `credentialManager`를 받는 주 생성자를 추가하고,
   기존 인수 없는 생성 방식은 보조 생성자(`constructor(app: Application)`)로 위임한다.
2. `testImplementation` 범위로 `io.mockk:mockk:1.13.12`를 추가한다.

## 근거

- `ViewModelProvider.AndroidViewModelFactory`는 리플렉션으로 `(Application)` 시그니처 생성자를 탐색한다.
  Kotlin 기본값 파라미터는 이 시그니처를 생성하지 않으므로, 보조 생성자가 유일하게 안전한 방법이다.
- mockk는 Kotlin 친화적 mock 라이브러리로 프로젝트의 기존 코드 스타일(`suspend`, coroutine, slot 등)에 잘 맞는다.
- `testImplementation` 범위이므로 배포 바이너리에 영향 없다.

## 검토한 대안

| 대안 | 미선택 이유 |
|------|------------|
| Robolectric 추가 | 빌드 속도 저하, Firebase 초기화 별도 설정 필요로 범위 과다 |
| mockito-kotlin 사용 | Kotlin coroutine/slot 지원이 mockk보다 불편 |
| 테스트 없이 PR 진행 | 버그 재발 방지 불가 |

## 예상 결과

- `AuthViewModelTest`에서 Firebase 없이 `firebaseAuthWithGoogle` 성공/실패 시나리오를 검증한다.
- `auth.currentUser` 대신 `authResult.user`를 사용하는 핵심 수정이 회귀 테스트로 보호된다.
- 프로덕션 동작(`viewModels()` 기본 팩토리)은 보조 생성자가 그대로 유지하므로 변경 없다.
