# haptic 인프라

햅틱(진동) 피드백을 제공하는 공유 인프라. 패키지 경로: `com.jkapp.haptic`

## 책임

- 한다: 기기의 Vibrator를 감싸 단일 진입점(`HapticController`)을 제공하고, 사용자 설정 강도(0~`MAX_HAPTIC_INTENSITY`)에 따라 `tick` 진동을 발생시킨다. `LocalHapticController`로 Compose 트리에 주입한다. 강도→진폭 변환·단계 변경 판정 등 UI/컨트롤러가 공유하는 순수 로직은 `HapticFeedbackUtils.kt`의 파일-레벨 함수로 분리해 JVM 단위테스트로 보증한다.
- 하지 않는다: 햅틱 강도 설정값의 저장(→ `common.AppPreferences`), 슬라이더 UI(→ `settings.SettingsScreen`), 강도 0 이외의 무진동 결정(강도 판단은 호출 시 전달된 `intensity` 파라미터 기준).

## 공개 API

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `HapticController` | 클래스 | `Context`로 생성. `intensity` 필드(저장값)와 두 `tick` 오버로드를 제공 |
| `HapticController.intensity` | `var Int` | `MainActivity`가 DataStore 구독 후 갱신. 기본값 `MAX_HAPTIC_INTENSITY / 2` |
| `HapticController.tick()` | 함수 | 저장된 `intensity` 필드로 진동. 기존 호출부 하위호환 유지 |
| `HapticController.tick(intensity: Int)` | 함수 | 전달받은 `intensity`로 진동. 진폭은 `hapticAmplitude(intensity)`로 계산하며 `null`(강도 0 이하)이면 무동작. 지속시간: `TICK_DURATION_MS = 20ms` |
| `LocalHapticController` | `ProvidableCompositionLocal<HapticController?>` | `staticCompositionLocalOf { null }`. `MainActivity`가 `CompositionLocalProvider`로 주입. null-safe(`?.tick()`)로 호출한다 |
| `hapticAmplitude(intensity: Int): Int?` | 파일-레벨 함수 (`HapticFeedbackUtils.kt`) | 강도(0~`MAX_HAPTIC_INTENSITY`)를 진폭(1~255)으로 변환. `intensity <= 0`이면 `null`(무진동). 공식: `(intensity * 255 / MAX_HAPTIC_INTENSITY).coerceIn(1, 255)`. `HapticController.tick(intensity)`가 사용 |
| `hapticStepChanged(prevValue: Float, newValue: Float): Boolean` | 파일-레벨 함수 (`HapticFeedbackUtils.kt`) | 두 슬라이더 위치값이 서로 다른 정수 단계에 있는지 반환(`toInt()` 비교). 같은 단계 내 미세 이동은 `false`. `SettingsScreen`의 드래그 중 진동 판정이 사용 |

## 데이터 / 저장소

없음. 진동 강도 설정값은 `common.AppPreferences`(DataStore)에 저장되며, `MainActivity`가 구독해 `HapticController.intensity`를 갱신한다.

## 의존 관계

- 사용하는 곳: `common.MainScreen`(탭 전환 탭 피드백 2곳), `settings.TabOrderEditScreen`(드래그 피드백 2곳), `settings.SettingsScreen`(햅틱 강도 슬라이더 드래그 중 실시간 피드백)
- 의존하는 것: Android Vibrator/VibratorManager API(`android.os.Vibrator`), `common.MAX_HAPTIC_INTENSITY` 상수

## 관련 결정 (ADR)

없음.
