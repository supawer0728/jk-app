# Firebase Authentication + Google Sign-In (GMS) 선택

**상태**: 결정됨
**날짜**: 2026-06-27

## 맥락

사용자 인증 기능의 첫 번째 수단으로 Google 로그인을 추가해야 한다.
백엔드 인증 처리 방식과 클라이언트 SDK를 결정해야 했다.

## 결정

Firebase Authentication + `play-services-auth` (GoogleSignInClient) 조합을 사용한다.
`GoogleSignInClient`는 `AndroidViewModel`이 소유하여 로그아웃 시 Google 세션도 함께 해제한다.

## 근거

- 이슈에서 명시한 기술 스택(Firebase Auth + GMS)을 그대로 따른다.
- Firebase Auth는 세션 영속성(앱 재실행 시 로그인 유지)을 자동 처리한다.
- `AndroidViewModel`이 `GoogleSignInClient`를 소유하면 sign-out 시 Firebase 세션과
  Google 세션을 하나의 메서드에서 모두 해제할 수 있다.

## 검토한 대안

**Credential Manager API (`androidx.credentials`)**
- Android 14+ 권장 방식이며 기기 저장 자격증명을 지원한다.
- 이미 의존성이 추가되어 있어 마이그레이션 경로가 확보되어 있다.
- 단, GoogleSignIn API와 인터페이스가 달라 별도 학습 비용이 발생하고,
  이슈에서 GMS 방식을 명시하여 이번에는 채택하지 않았다.

## 예상 결과

- `GoogleSignInClient`의 deprecated 경고가 컴파일 시 발생한다 (기능 이상 없음).
- 향후 Credential Manager로 마이그레이션 시 `AuthViewModel`과 `LoginScreen`을
  교체하면 되며, `HomeScreen`과 라우팅 로직은 변경이 불필요하다.
- `google-services.json`에 올바른 Android OAuth 클라이언트(SHA-1 등록)가
  포함되어야 런타임에 idToken이 정상 발급된다.
