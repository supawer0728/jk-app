# settings(설정) 도메인

앱 환경설정 값을 다루는 도메인. 설정 화면은 값을 소유하지 않고, 저장소가 다른 두 종류의
환경설정 값을 한 화면에서 읽고 쓴다. **기기 로컬 설정**은 DataStore(`common.AppPreferences`)에,
**계정 동기화 설정**은 Firestore `users/{uid}.preference`(`user.UserRepository`)에 저장된다.

## 도메인 모델

설정 화면이 다루는 값과 그 저장소는 다음과 같다.

| 설정 값 | 타입 | 저장소 | 기본값 |
|---------|------|--------|--------|
| `darkModeSetting` | `DarkModeSetting` | DataStore (`dark_mode_setting`) | `SYSTEM` |
| `hapticIntensity` | `Int` (0..10) | DataStore (`haptic_intensity`) | `5` |
| `notificationMode` | `NotificationMode` | DataStore (`notification_mode`) | `SOUND_AND_VIBRATE` |
| `notificationSound` | `NotificationSound` | DataStore (`notification_sound`) | `DEFAULT` |
| `language` | `String` | Firestore `users/{uid}.preference.language` | `"ko"` |
| `timeZone` | `String` | Firestore `users/{uid}.preference.timeZone` | `"Asia/Seoul"` |

### DarkModeSetting (enum)

`app/src/main/java/com/jkapp/common/AppPreferences.kt` (소유: common 인프라)

| 값 | 설명 |
|----|------|
| `SYSTEM` | 시스템 설정을 따름 |
| `ON` | 항상 다크 모드 |
| `OFF` | 항상 라이트 모드 |

### NotificationMode (enum)

`app/src/main/java/com/jkapp/common/AppPreferences.kt` (소유: common 인프라)

| 값 | 설명 |
|----|------|
| `SOUND` | 소리만 |
| `VIBRATE` | 진동만 |
| `SOUND_AND_VIBRATE` | 소리 + 진동 |
| `OFF` | 무음(소리·진동 없음, 알림은 조용히 표시) |

파생 속성: `hasSound`(SOUND/SOUND_AND_VIBRATE), `hasVibration`(VIBRATE/SOUND_AND_VIBRATE).

### NotificationSound (enum)

`app/src/main/java/com/jkapp/common/AppPreferences.kt` (소유: common 인프라)

| 값 | 설명 |
|----|------|
| `DEFAULT` | 기본음 |
| `ALARM` | 알람음 |
| `RINGTONE` | 벨소리 |

실제 사운드 URI는 notification 계층에서 시스템 기본음으로 해석하며, 번들 오디오는 없다.

### UserPreference (data class)

`app/src/main/java/com/jkapp/user/UserPreference.kt` (소유: user 인프라)

| 속성 | 타입 | 설명 |
|------|------|------|
| `language` | `String` | 언어 코드. 기본값 `"ko"` |
| `timeZone` | `String` | 시간대 ID. 기본값 `"Asia/Seoul"` |

`darkMode`/`hapticIntensity`는 기기별로 다르게 유지되어야 하므로 `UserPreference`에서 의도적으로
제외되어 있다(DataStore 담당).

## 기능 (메서드)

설정 화면의 상태·동작은 `SettingsViewModel`이 노출한다.

| 멤버 | 시그니처 | 설명 |
|------|----------|------|
| `darkModeSetting` | `StateFlow<DarkModeSetting>` | DataStore 값 구독 |
| `hapticIntensity` | `StateFlow<Int>` | DataStore 값 구독 |
| `notificationMode` | `StateFlow<NotificationMode>` | DataStore 값 구독 |
| `notificationSound` | `StateFlow<NotificationSound>` | DataStore 값 구독 |
| `preference` | `StateFlow<UserPreference>` | 로그인 uid의 Firestore preference 실시간 구독 |
| `setDarkModeSetting` | `(DarkModeSetting): Unit` | DataStore 저장 |
| `setHapticIntensity` | `(Int): Unit` | DataStore 저장 |
| `setNotificationMode` | `(NotificationMode): Unit` | DataStore 저장 |
| `setNotificationSound` | `(NotificationSound): Unit` | DataStore 저장 |
| `setLanguage` | `(String): Unit` | Firestore preference 갱신 |
| `setTimeZone` | `(String): Unit` | Firestore preference 갱신 |

ViewModel이 위임하는 저장소 API(상세는 각 인프라 문서):
`AppPreferences`의 `darkModeSetting`/`setDarkModeSetting`, `hapticIntensity`/`setHapticIntensity`,
`notificationMode`/`setNotificationMode`, `notificationSound`/`setNotificationSound`(DataStore)와
`UserRepository`의 `observePreference`/`updatePreference`(Firestore).

## 타 도메인과의 연관성

- `common.AppPreferences` (DataStore) — 다크모드·햅틱·알림 설정을 기기 로컬에 보관. 설정 화면은
  이를 소비만 한다. [`infra/common.md`](../infra/common.md)
- `user.UserPreference` / `user.UserRepository` (Firestore) — 언어·시간대를 계정에 동기화.
  [`infra/user.md`](../infra/user.md)
- `auth` — `AuthRepository.observeCurrentUserId()`로 얻은 uid가 있어야 Firestore preference를
  읽고 쓴다. uid가 없으면(로그아웃) 기본값을 표시한다. [`infra/auth.md`](../infra/auth.md)
- **하단 탭 순서** — 앱 환경설정의 일종이지만 설정 화면이 아니라 `common`의 홈 탭 편집 UI
  (`common.TabOrderViewModel`)가 다룬다. Firestore `tab-orders` 컬렉션에 사용자별로 저장된다
  (설정 화면 코드에는 포함되지 않음). [`infra/common.md`](../infra/common.md)

## Firestore 컬렉션

설정 값 중 **언어·시간대만** Firestore에 저장된다. 나머지(다크모드·햅틱·알림)는 DataStore
로컬 저장이라 Firestore 컬렉션이 없다.

담당 Repository: `user.UserRepositoryImpl`

### `users` (UserPreference — 문서 내 `preference` 맵)

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| (문서 ID) | uid | Firebase Auth uid |
| `preference.language` | `language` | 없으면 기본값 `"ko"` 폴백 |
| `preference.timeZone` | `timeZone` | 없으면 기본값 `"Asia/Seoul"` 폴백 |

`updatePreference`는 `preference` 맵을 `SetOptions.merge()`로 부분 갱신한다(다른 사용자 필드
보존). `users` 문서 자체(프로필·푸시 토큰 등)의 전체 스키마는 [`infra/user.md`](../infra/user.md)에 있다.
