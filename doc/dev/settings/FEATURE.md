# settings(설정) 기능

사용자가 앱 환경(다크모드, 햅틱 강도, 알림 방식·알림음, 언어, 시간대, 탭 순서)을 조정한다.
설정마다 저장소가 달라, 기기별로 유지할 값은 로컬(DataStore)에, 계정 간 공유할 값은
Firestore(`users/{uid}.preference`)에 저장된다. 탭 순서는 별도 화면(`TabOrderEditScreen`)에서
드래그앤드롭으로 변경하며, 홈은 항상 선두 고정이라 홈을 제외한 콘텐츠 탭만 재배치 대상이다.

## 비즈니스 규칙

- **저장소 이원화**: 다크모드·햅틱·알림 방식·알림음은 기기 로컬(DataStore), 언어·시간대는
  계정 동기화(Firestore)로 저장한다. 강제 위치 `SettingsViewModel`(DataStore는 `AppPreferences`,
  Firestore는 `UserRepository`로 위임). → ADR/57
- **다크모드·햅틱은 기기 로컬 유지**: 기기마다 진동/다크모드 경험을 다르게 두고 싶을 수 있어
  `users/{uid}.preference`로 동기화하지 않는다. 강제 위치 `AppPreferences`, `UserPreference`
  (해당 필드 부재). → ADR/57, ADR/56
- **언어는 정보 표시 전용**: 드롭다운에 "한국어"(`"ko"`) 단일 옵션만 노출하고, 실제 로케일 전환
  로직은 구현하지 않는다(향후 다국어 확장용 필드). 강제 위치 `SettingsScreen.LanguageOption`. → ADR/57
- **시간대는 고정 목록 선택**: `Asia/Seoul`, `UTC` 중 선택한다. 현재 시간대 변환이 필요한 필드가
  없어 값만 저장·동기화되고 실제 변환은 아직 적용되지 않는다. 강제 위치 `SettingsScreen.TimeZoneOption`. → ADR/57
- **햅틱 슬라이더 저장 타이밍**: 드래그 중에는 로컬 상태(`sliderPosition`)로만 즉시 반영하고,
  손을 뗄 때(`onValueChangeFinished`)만 DataStore에 저장한다. 매 프레임 비동기 저장으로 엄지
  위치가 어긋나 보이는 문제를 피한다. 강제 위치 `SettingsScreen`.
- **햅틱 슬라이더 드래그 중 실시간 진동 피드백**: 슬라이더 드래그 중 정수 단계(0~`MAX_HAPTIC_INTENSITY`)가
  변경될 때마다 그 단계 강도로 즉시 진동한다. **저장은 손을 뗄 때**, **진동 피드백은 드래그 중
  정수 단계가 바뀐 시점**으로 타이밍이 분리된다. 강도 0(끄기)에서는 진동하지 않는다.
  같은 정수 단계 내 미세 이동(예: 3.1 → 3.9)에서는 중복 진동이 발생하지 않는다(정수
  단계가 실제로 바뀐 경우에만). 강제 위치 `SettingsScreen`(`onValueChange` 내 단계 변경 감지),
  `HapticController.tick(intensity: Int)`. → 이슈 #103
- **알림음은 소리 모드에서만 노출**: `notificationMode.hasSound`가 참일 때만 알림음 드롭다운을
  표시한다. 강제 위치 `SettingsScreen`.
- **로그아웃 시 기본값 표시**: uid가 없으면 Firestore 대신 `UserPreference()` 기본값을 방출하고,
  언어·시간대 변경도 무시된다(uid null이면 `updatePreference` 미호출). 강제 위치
  `SettingsViewModel.preference`, `updatePreference`.
- **Firestore 반영은 fire-and-forget**: 언어·시간대 변경은 `viewModelScope`에서 `updatePreference`를
  호출하고 결과를 기다리지 않는다. 실제 화면 값은 `preference` 실시간 구독으로 갱신된다.
  강제 위치 `SettingsViewModel.updatePreference`.
- **저장소 장애 내성**: DataStore가 `IOException`을 방출하면 읽기는 빈 설정(기본값)으로 대체하고
  쓰기는 무시해 크래시를 막는다. Firestore preference 구독 오류는 로그 후 기본값을 방출한다.
  강제 위치 `AppPreferences.safePreferencesData`/각 setter, `SettingsViewModel.preference.catch`.
- **로그아웃은 설정 화면 항목**: 로그아웃은 하단 탭이 아니라 설정 화면 목록의 항목이다. 누르면
  확인 다이얼로그("정말 로그아웃하시겠습니까?")를 띄우고, 확인 시에만 `AuthViewModel.signOut()`을
  호출한다(취소 시 무동작). `SettingsScreen`은 `onSignOut` 콜백으로 주입받아 호출한다. → ADR/100 후속
- **설정 항목 좌측 라벨 고정폭 정렬**: 모든 설정 항목 행의 좌측 라벨은 동일한 고정폭으로 맞춰
  우측 컨트롤(드롭다운·슬라이더·이동 화살표)이 세로로 정렬된다. 고정폭은 하드코딩이 아니라
  `SubcomposeLayout`으로 모든 라벨을 measure해 얻은 **최대 폭**을 적용한다. 강제 위치
  `SettingsScreen`(`AlignedLabelSettings`/라벨 폭 측정 레이아웃). 로그아웃은 버튼 형태라 라벨 정렬
  대상에서 제외한다.

## 유효성 검증

- `hapticIntensity` — 읽기·쓰기 모두 `coerceIn(0, MAX_HAPTIC_INTENSITY=10)`로 범위 보정.
  강제 위치 `AppPreferences.hapticIntensity`/`setHapticIntensity`.
- enum 저장값(`darkModeSetting`/`notificationMode`/`notificationSound`) — 알 수 없는 문자열이면
  `runCatching { valueOf() }` 실패 시 기본값으로 폴백. 강제 위치 `AppPreferences`.
- Firestore `preference` 맵 필드 부재/타입 불일치 — 각 필드 기본값으로 폴백.
  강제 위치 `UserRepositoryImpl.toUserPreference`.
- 드롭다운 선택 표시값이 목록에 없으면 지정된 기본 옵션으로 표시. 강제 위치 `SettingsScreen`
  (`firstOrNull { ... } ?: 기본 옵션`).

## 주요 플로우

1. **설정 화면 진입**: `SettingsScreen`이 `SettingsViewModel`의 StateFlow들을 구독 →
   DataStore 4개 값 + Firestore `preference`(로그인 시 `users/{uid}` 실시간 구독)를 표시.
   목록 하단에 **탭 순서 변경**과 **로그아웃** 항목이 있다. 각 설정 항목의 좌측 라벨은
   최대 폭으로 정렬되어 우측 컨트롤이 세로로 맞춰진다.
2. **기기 로컬 설정 변경**(다크모드/알림 방식/알림음): 드롭다운 선택 →
   `SettingsViewModel.setXxx` → `AppPreferences.setXxx`(DataStore 저장) → StateFlow 재방출.
3. **햅틱 강도 변경**:
   - **드래그 중 실시간 피드백**: 슬라이더 드래그 → 정수 단계 변경 감지(`onValueChange` 내
     `hapticStepChanged(sliderPosition, newValue)`) → `HapticController.tick(intensity = newValue.toInt())`
     호출(강도 0이면 무진동, 같은 단계 내 미세 이동은 무시).
   - **저장**: 손 뗌(`onValueChangeFinished`) → `SettingsViewModel.setHapticIntensity` →
     `AppPreferences.setHapticIntensity`(DataStore).
4. **언어/시간대 변경**: 드롭다운 선택 → `SettingsViewModel.setLanguage`/`setTimeZone` →
   `updatePreference`(uid 필요) → `UserRepository.updatePreference`로 Firestore
   `users/{uid}.preference` merge → `preference` 구독이 새 값을 밀어줌.
5. **탭 순서 변경**: '탭 순서 변경' 항목 탭 → `TabOrderEditScreen`으로 이동 →
   드래그앤드롭으로 순서 변경 → **적용** 버튼 탭.
   - 적용: 변경이 있을 때만 `tab-orders` Firestore에 저장하고 하단 탭에 즉시 반영.
     변경이 없으면 no-op. 저장 후 이전 화면(설정)으로 복귀.
   - 취소: 변경을 버리고 이전 화면(설정)으로 복귀.
   - 편집 중 Firestore 스냅샷 도착은 무시(사용자 드래그 순서 보호). → ADR/100
   - **재배열 대상은 홈을 제외한 콘텐츠 탭(자산관리·육묘일기·TODO·캘린더)뿐**이다. 홈은
     하단바 왼쪽에 고정되므로 재배치 화면 목록에 나타나지 않고, 저장 시 항상
     `[HOME] + 재배치된 나머지` 순서로 저장되어 선두를 유지한다.
6. **로그아웃**: 설정 목록의 '로그아웃' 항목 탭 → 확인 다이얼로그("정말 로그아웃하시겠습니까?")
   → **확인** 시 `onSignOut()`(→ `AuthViewModel.signOut()`) 호출, **취소** 시 무동작.

## 관련 결정 (ADR)

- [`doc/adr/57/settings-preference-scope-and-timezone-storage.md`](../../adr/57/settings-preference-scope-and-timezone-storage.md) — 설정 동기화 범위 축소(다크모드·햅틱 로컬 유지), 언어 표시 전용, 시간대 저장 원칙
- [`doc/adr/56/user-profile-upsert-strategy.md`](../../adr/56/user-profile-upsert-strategy.md) — 사용자 프로필 upsert 전략(다크모드·햅틱을 preference에서 제외한 최초 결정)
- [`doc/adr/41/tab-order-firestore-schema.md`](../../adr/41/tab-order-firestore-schema.md) — 하단 탭 순서 저장 구조(`tab-orders` 스키마, `mergeTabOrder` 정합성 유지)
- [`doc/adr/100/remove-gnb-and-tab-interaction-redesign.md`](../../adr/100/remove-gnb-and-tab-interaction-redesign.md) — GNB 제거·탭 재배열을 설정 화면으로 이동한 결정
