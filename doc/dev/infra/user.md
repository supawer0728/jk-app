# user 인프라

사용자 프로필, 앱 설정(preference), 푸시 토큰, 로그인 이력을 Firestore에 저장·조회한다. 특정
feature에 속하지 않고 auth·notification·settings가 공유한다. 패키지 경로: `com.jkapp.user`

## 책임

- 한다: 사용자 프로필 upsert(`users/{uid}`), 설정 실시간 구독·갱신, 푸시 토큰 조회·갱신, 로그인
  이력 추가(`login-history`). 최초 생성 시에만 설정 기본값을 넣어 기존 사용자 설정을 보존.
- 하지 않는다: 인증/로그인 자체(→ `auth`), 설정 UI(→ `settings`), 기기별 로컬 설정(다크모드·햅틱
  강도는 `common.AppPreferences`가 DataStore로 담당하며 여기서 제외).

## 공개 API

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `UserRepository` | 인터페이스 | `upsertUserProfile(uid, email, displayName)`, `observePreference(uid): Flow<UserPreference>`, `updatePreference(uid, preference)`, `getPushToken(uid): PushToken?`, `updatePushToken(uid, pushToken)` |
| `UserRepositoryImpl` | 클래스 | `users` 컬렉션 구현체. upsert는 `SetOptions.merge()` 사용 |
| `LoginHistoryRepository` / `LoginHistoryRepositoryImpl` | 인터페이스/클래스 | `recordLogin(uid, device)` — `login-history`에 append |
| `User`, `UserPreference`, `PushToken`, `LoginDevice`, `LoginHistory` | data class | 도메인 모델 |

도메인 모델 필드: `UserPreference(language="ko", timeZone="Asia/Seoul")`,
`PushToken(token, updatedAt, platform="android")`,
`LoginDevice(os="Android", osVersion, deviceModel, appVersion)`.

## 데이터 / 저장소

담당 구현체가 다루는 Firestore 컬렉션.

### `users` (`UserRepositoryImpl`)

| Firestore 필드 | 설명 |
|----------------|------|
| (문서 ID) | Firebase Auth `uid` |
| `email` | 이메일 |
| `displayName` | 표시 이름 |
| `lastLoginAt` | 마지막 로그인 시각(epoch ms) |
| `preference` | 맵: `language`, `timeZone` (최초 생성 시에만 기본값 주입, 이후 merge 보존) |
| `pushToken` | 맵: `token`, `updatedAt`, `platform` |

### `login-history` (`LoginHistoryRepositoryImpl`)

| Firestore 필드 | 설명 |
|----------------|------|
| (문서 ID) | auto-ID (`add`) |
| `uid` | 사용자 uid |
| `loginAt` | 로그인 시각(epoch ms) |
| `device` | 맵: `os`, `osVersion`, `deviceModel`, `appVersion` |

## 의존 관계

- 사용하는 곳: `auth`(`AuthViewModel`이 로그인 성공 시 `upsertUserProfile`·`recordLogin`),
  `notification`(`PushTokenManager`가 `getPushToken`/`updatePushToken`),
  `settings`(설정 화면이 `observePreference`/`updatePreference`).
- 의존하는 것: Cloud Firestore SDK, `common`의 `AppFirestore`(공유 인스턴스)·`await`·`snapshotFlow`.

## 관련 결정 (ADR)

- [`doc/adr/56/user-profile-upsert-strategy.md`](../../adr/56/user-profile-upsert-strategy.md) — 프로필 upsert 전략(merge, 기본값 최초 1회) 및 로그인 실패 처리
- [`doc/adr/57/settings-preference-scope-and-timezone-storage.md`](../../adr/57/settings-preference-scope-and-timezone-storage.md) — 설정 동기화 범위 축소(기기별 설정 제외) 및 시간대 저장 원칙
