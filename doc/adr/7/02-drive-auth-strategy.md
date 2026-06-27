# Drive 인증 전략

**상태**: 결정됨
**날짜**: 2026-06-28

## 맥락

앱은 이미 CredentialManager + Firebase Auth로 Google Sign-In을 구현하고 있다.
Google Drive API 호출에는 별도의 OAuth2 액세스 토큰(`drive.file` 스코프)이 필요하다.

## 결정

`com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient`를 사용하여
육묘일기 탭 최초 진입 시 `drive.file` 스코프 권한을 요청한다.
동의 화면 실행은 Compose의 `rememberLauncherForActivityResult`로 처리하며,
발급된 액세스 토큰은 `DiaryViewModel` 메모리에 보관한다.

## 근거

- `play-services-auth`가 이미 의존성에 포함되어 있어 추가 라이브러리 불필요
- ViewModel에 Activity 참조를 두지 않아 메모리 누수 방지
- 필요 시점(탭 진입)에만 권한을 요청해 불필요한 사전 요청 없음
- 액세스 토큰을 메모리에만 보관하여 디스크 저장에 따른 보안 위험 제거

## 검토한 대안

- **로그인 시 Drive 스코프 포함**: UX 마찰이 더 일찍 발생하고 Drive를 사용하지 않는 경우에도 권한 요청
- **DataStore에 토큰 저장**: 토큰 만료 갱신 로직이 필요해 복잡도 증가, 현재 불필요
- **GoogleSignIn 레거시 API**: 현재 CredentialManager 기반 신규 API와 혼용 시 충돌 가능

## 예상 결과

`DiaryScreen`이 처음 표시될 때 Drive 인증 흐름이 트리거된다.
사용자가 동의하면 액세스 토큰이 ViewModel에 저장되고 데이터 로드가 시작된다.
앱 재시작 시 토큰이 소멸되어 다음 탭 진입 시 재인증이 필요하다.
