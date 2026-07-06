# androidx.core:core-splashscreen 도입

**상태**: 결정됨
**날짜**: 2026-07-06

## 맥락

커스텀 풀스크린 이미지 스플래시(`SplashRoute` + `SplashScreen.kt`)를 추가하면서,
기존 상태(테마에 `windowSplashScreen*` 속성 미설정)에서는 Android 12(API 31)+ 기기에서
OS가 강제로 그리는 기본 시스템 스플래시(런처 아이콘 + 배경색)가 먼저 보이고,
그 뒤에 `MainActivity`가 기동해 커스텀 Compose 스플래시(`being.png`)가 이어서 나타난다.
두 화면이 스타일 없이 이어지면 "이중 스플래시"처럼 부자연스럽게 보일 수 있고,
API 레벨에 따라 시스템 스플래시의 동작(아이콘 크기, 배경색 결정 방식)이 제조사/버전별로 달라질 수 있다.

## 결정

**`androidx.core:core-splashscreen`**(버전 1.0.1)을 도입한다.

- `themes.xml`에 `Theme.Jkapp.Splash`(parent: `Theme.SplashScreen`)를 추가하고
  `windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon`(정적 아이콘),
  `postSplashScreenTheme`(`Theme.Jkapp`)을 지정한다.
- `AndroidManifest.xml`의 `MainActivity` 테마를 `Theme.Jkapp.Splash`로 변경한다.
- `MainActivity.onCreate()`에서 `super.onCreate()` 호출 전에 `installSplashScreen()`을 호출한다.
- `setKeepOnScreenCondition`은 사용하지 않는다 — 첫 Compose 프레임이 그려지는 즉시
  시스템 스플래시가 사라지도록 하여 노출 시간을 최소화한다.
- 커스텀 Compose 스플래시(`SplashScreen.kt`, 1.5초 고정 노출 후 로그인 상태별 전환)는 그대로 유지한다.

## 근거

- API 21부터 31까지 하위 호환 방식으로 동일한 스플래시 API를 사용할 수 있어,
  기기/제조사별 기본 동작 편차를 없애고 동작을 명시적으로 통제할 수 있다.
- `postSplashScreenTheme`으로 스플래시 종료 후 앱 본연의 테마(`Theme.Jkapp`)로
  자동 전환되어 별도의 `setTheme()` 호출이 필요 없다.
- `keepOnScreenCondition`을 걸지 않음으로써 시스템 스플래시 노출 시간을 OS가 허용하는
  최소치(첫 프레임 드로우까지)로 유지하고, 이어지는 커스텀 스플래시와의 전환 체감 지연을 줄인다.
- 정적 아이콘(`windowSplashScreenAnimatedIcon`에 애니메이션 아님)을 사용해
  `windowSplashScreenAnimationDuration`에 의한 강제 최소 노출 시간이 발생하지 않도록 한다.

## 검토한 대안

**시스템 기본 동작 유지(라이브러리 미도입)**
- 코드 변경이 없다는 장점은 있으나, API 레벨/제조사별로 시스템 스플래시의 배경색·아이콘
  크기 결정 로직이 달라 예측 가능한 UX를 보장하기 어렵다. 코드 리뷰에서 "이중 스플래시"
  체감 문제로 지적되어 채택하지 않았다.

**`windowBackground`에 `being.png`를 직접 지정(레거시 트릭)**
- Android 12 미만에서는 유효하지만, 12 이상에서는 시스템이 SplashScreen API를 강제
  적용하며 `windowBackground`가 이미지여도 무시하고 아이콘+단색 배경만 사용한다.
  즉 이슈의 근본 배경("아이콘 240dp 제약")을 API 31+에서 해결하지 못해 폐기했다.

## 예상 결과

- `app/build.gradle.kts`, `gradle/libs.versions.toml`에 `androidx.core:core-splashscreen:1.0.1`
  의존성이 추가된다.
- 시스템 스플래시는 앱 실행 즉시(첫 프레임 드로우 시점) 사라지고, 곧바로 커스텀 스플래시
  (`being.png`, 1.5초)로 이어져 기존보다 자연스러운 전환을 제공한다.
- 향후 스플래시 배경색/아이콘을 바꾸고 싶다면 `themes.xml`의 `Theme.Jkapp.Splash`만 수정하면 된다.
