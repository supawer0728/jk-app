# Firebase Authentication + Credential Manager 기반 Google 로그인

**상태**: 결정됨 (2026-06-27 초안 → 2026-06-27 갱신)
**날짜**: 2026-06-27

## 맥락

사용자 인증 기능의 첫 번째 수단으로 Google 로그인을 추가해야 한다.
백엔드 인증 처리 방식, 클라이언트 SDK, 화면 전환 구조를 결정해야 했다.

코드 리뷰(PR #2) 피드백을 반영하여 초기 결정(GMS GoogleSignInClient)에서
Credential Manager 방식으로 전환하고 Navigation 3을 도입했다.

## 결정

### 인증 SDK

**Firebase Authentication + `androidx.credentials` (Credential Manager)** 조합을 사용한다.

- 로그인: `LoginScreen`에서 `CredentialManager.getCredential()` + `GetGoogleIdOption`으로
  Google ID 토큰을 취득한 뒤 `AuthViewModel.firebaseAuthWithGoogle(idToken)`으로 Firebase 인증
- 로그아웃: `AuthViewModel.signOut()`에서 `auth.signOut()` 즉시 실행 후
  `viewModelScope`에서 `credentialManager.clearCredentialState()` 비동기 처리

### 화면 라우팅

**Jetpack Navigation 3** (`NavDisplay` + `@Serializable` routes)을 사용한다.

- `LoginRoute`, `HomeRoute`를 `@Serializable data object`로 선언
- `MainActivity`에서 `authViewModel.user` StateFlow를 `LaunchedEffect`로 관찰하여
  로그인·로그아웃 시 `backStack`을 자동 갱신

## 근거

**Credential Manager 채택**
- `play-services-auth`의 `GoogleSignInClient`는 deprecated. Credential Manager가 현재 Android 권장 표준이다.
- 로그인 UI가 시스템 Bottom Sheet로 통합되어 UX가 일관적이다.
- `loginScreen`이 Activity context(LocalContext)를 갖고 있어 `getCredential()` 호출이 자연스럽다.
- Firebase Auth는 세션 영속성(앱 재실행 시 로그인 유지)을 자동 처리하므로 그대로 유지한다.

**Navigation 3 채택**
- 프로젝트 요구사항(`navigation3-runtime`, `navigation3-ui` 의존성 기명시)에 명시되어 있다.
- `entryProvider` DSL로 화면 선언이 타입 안전하다.
- 인증 상태(StateFlow) → LaunchedEffect → backStack 갱신 패턴으로
  외부 이벤트(토큰 만료 등)에도 자동 반응한다.

## 검토한 대안

**GMS `GoogleSignInClient` (초기 결정, 폐기)**
- 이슈 초안에서 명시한 스택이었으나 deprecated로 코드 리뷰에서 교체 권고를 받았다.
- `AndroidViewModel`이 `GoogleSignInClient`를 소유하는 구조로 구현했으나
  Credential Manager 전환 시 ViewModel에서 Activity context 의존을 제거할 수 있어 더 깔끔해졌다.

**조건문 기반 라우팅 `if (user != null)` (초기 결정, 폐기)**
- 간단하지만 확장성이 없고, 프로젝트 요구사항인 Navigation 3을 활용하지 못한다.

## 예상 결과

- `LoginScreen`이 로그인 UI를 직접 그리지 않고 시스템 Credential Picker에 위임한다.
- `AuthViewModel`에서 `GoogleSignInClient` 의존이 제거되어 Android context 결합도가 낮아진다.
- `google-services.json`에 `com.jkapp` 패키지의 Android OAuth 클라이언트(SHA-1 등록)가
  포함되어야 런타임에 idToken이 정상 발급된다.
- `default_web_client_id` 문자열은 google-services 플러그인이 `google-services.json`으로부터
  자동 생성한다. 파일이 없으면 빌드가 실패한다.
