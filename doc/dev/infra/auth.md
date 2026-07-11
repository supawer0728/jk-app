# auth 인프라

Firebase Authentication(Google 로그인)으로 사용자를 식별하고, 인증 상태·현재 사용자 정보를
앱 전역에 노출한다. 패키지 경로: `com.jkapp.auth`

## 책임

- 한다: Google 자격 증명(idToken)으로 Firebase 로그인/로그아웃, 인증 상태(`Boolean`)·현재
  사용자 이메일·uid를 `Flow`로 노출, 로그인 성공 시 사용자 프로필 upsert와 로그인 이력 기록을
  하나의 트랜잭션처럼 묶어 처리, 세션 복원 완료 여부(`isAuthReady`) 판단.
- 하지 않는다: 사용자 프로필/설정/로그인 이력의 실제 Firestore 저장(→ `user` 인프라에 위임),
  Google Sign-In UI(Credential Manager 호출)의 화면 표시(→ `common.LoginScreen`), 계정 전환.

## 공개 API

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `AuthRepository` | 인터페이스 | `observeAuthState(): Flow<Boolean>`, `observeCurrentUserEmail(): Flow<String?>`, `getCurrentUserEmail(): String?`, `observeCurrentUserId(): Flow<String?>` |
| `FirebaseAuthRepository` | 클래스 | `AuthRepository` 구현체. `FirebaseAuth.AuthStateListener`를 `callbackFlow`로 감싸 상태 변경을 실시간 방출 |
| `AuthViewModel` | `AndroidViewModel` | `user: StateFlow<FirebaseUser?>`, `isAuthReady: StateFlow<Boolean>`, `firebaseAuthWithGoogle(idToken, onResult)`, `signOut()` |

`AuthViewModel.firebaseAuthWithGoogle`는 `signInWithCredential` 성공 후 같은 `runCatching`
안에서 프로필 upsert·로그인 기록을 수행하고, 그중 하나라도 실패하면 `auth.signOut()`으로 세션을
롤백해 로그인 자체를 실패로 취급한다. `isAuthReady`는 세션 복원 전 `currentUser`가 잠시 `null`을
반환하는 문제를 피하려고 `AuthStateListener`의 첫 발화 시점(또는 5초 타임아웃)으로 판단한다.

## 데이터 / 저장소

- 자체 컬렉션/저장소 없음. 인증 정보는 Firebase Auth SDK가 관리한다.
- 로그인 성공 시 `user` 인프라의 `users`·`login-history` 컬렉션에 기록한다(→ [`user.md`](user.md)).

## 의존 관계

- 사용하는 곳:
  - `AuthViewModel` — `MainActivity`, `common.LoginScreen`, `common.MainScreen`(로그아웃·프로필).
  - `AuthRepository` — `diary`, `finance.asset`, `finance.investment`, `finance.benchmark`,
    `todo`, `settings`의 ViewModel이 현재 사용자 uid/이메일을 얻는 진입점으로 기본 주입한다.
- 의존하는 것: Firebase Auth SDK, AndroidX Credential Manager(`signOut` 시 자격 증명 상태
  정리), `user` 인프라(`UserRepository`, `LoginHistoryRepository`), `BuildConfig.VERSION_NAME`.

## 관련 결정 (ADR)

- [`doc/adr/1/firebase-auth-google-sign-in.md`](../../adr/1/firebase-auth-google-sign-in.md) — Firebase Authentication + Credential Manager 기반 Google 로그인 채택
- [`doc/adr/56/user-profile-upsert-strategy.md`](../../adr/56/user-profile-upsert-strategy.md) — 프로필 upsert/로그인 기록 실패 시 세션 롤백으로 로그인 실패 처리
