# 사용자 프로필 upsert 전략 및 로그인 실패 처리

**상태**: 결정됨
**날짜**: 2026-07-07

## 맥락

이슈 #56에서 로그인 성공 시 `users/{uid}` 프로필을 upsert하고 `login-history`에 기록을 남기는 로직을 `AuthViewModel.firebaseAuthWithGoogle`에 연결해야 했다. 이 과정에서 세 가지 설계 판단이 필요했다.

## 결정

1. **UserPreference 축소**: 이슈 스펙의 `darkMode`/`hapticIntensity`는 제외하고 `language`, `timeZone`만 `UserPreference`에 남긴다. 다크모드·햅틱 강도는 기기별로 사용자가 다르게 설정하길 원할 수 있는 값이라, 이미 로컬 `AppPreferences`(DataStore)가 담당하는 영역을 Firestore 프로필로 옮기지 않는다.
2. **users/{uid} upsert는 병합(merge) 방식**: `email`/`displayName`/`lastLoginAt`만 매 로그인마다 갱신하고, `pushToken`/`preference`는 기존 값이 있으면 보존한다(`SetOptions.merge()`). 문서가 최초 생성될 때만 `preference`에 기본값을 채운다. get-then-set(비트랜잭션) 방식을 쓰며, 트랜잭션으로 감싸지 않았다.
3. **프로필 upsert/로그인 기록 실패 시 로그인 자체도 실패로 처리**: `UserRepository.upsertUserProfile` 또는 `LoginHistoryRepository.recordLogin`이 실패하면 `onResult(false)`를 반환하고, `auth.signOut()`으로 Firebase 세션을 롤백한다.

## 근거

1. 다크모드/햅틱은 이미 기기 로컬 설정으로 존재하고, 사용자가 기기마다 다르게 쓰길 원할 수 있는 값이라는 피드백을 반영했다.
2. 이 앱은 본인과 배우자 2명만 로그인하므로, 동시에 같은 uid로 최초 로그인이 겹칠 확률은 무시할 수준이다. 트랜잭션 없이 get-then-set으로 구현해 코드를 단순하게 유지했다.
3. `_user` StateFlow 갱신을 authStateListener에만 맡기면, Firebase가 `signInWithCredential` 성공 시 비동기로 `authStateListener`를 발화시켜 `_user`를 먼저 갱신해 버릴 수 있다. 그 상태에서 프로필 upsert가 실패해도 `_user`는 이미 로그인 상태로 남아 `onResult(false)`와 실제 화면 상태(홈으로 네비게이션)가 어긋난다. 이를 막기 위해 실패 시 `auth.signOut()`을 호출해 세션을 실제로 롤백하고 `_user.value`도 명시적으로 `null`로 되돌린다.

## 검토한 대안

- **preference를 매 로그인마다 기본값으로 재설정**: 구현은 단순하지만 사용자가 저장한 설정(다크모드 등 향후 값)이 로그인마다 초기화되는 문제가 있어 기각.
- **upsert/기록 실패해도 로그인은 성공 처리**: Firebase Auth 인증 자체는 이미 성공했으므로 부가 기능 실패로 사용자를 막지 않는 방향도 검토했으나, 프로필 없는 uid가 존재할 수 있는 상태를 허용하지 않기로 하고 기각.
- **`upsertUserProfile`을 Firestore 트랜잭션으로 구현**: 엄밀하지만 2인용 앱 규모에 비해 과한 복잡도로 판단해 기각. 향후 동시 로그인 시나리오가 생기면 재검토.

## 예상 결과

- `users/{uid}`는 최초 로그인 시 `preference` 기본값을 포함해 생성되고, 이후 로그인마다 `email`/`displayName`/`lastLoginAt`만 갱신된다.
- 프로필 upsert나 로그인 기록이 실패하면 사용자는 로그인 실패로 인지하고(`onResult(false)`), Firebase 세션도 함께 롤백되어 다음 로그인 시도 시 일관된 상태에서 다시 시작한다.
- `UserPreference`에 다크모드/햅틱 설정을 동기화하는 기능은 이 이슈의 범위가 아니다(추가로 필요해지면 별도 이슈에서 재검토).
