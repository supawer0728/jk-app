# detekt 도입 및 의존성 정리 방침

**상태**: 결정됨
**날짜**: 2026-07-11

## 맥락

여러 기능(TODO·벤치마크·재무·구글시트 연동)이 누적되며 코드가 늘었으나 자동화된 정적
분석 체계가 없었다(detekt/ktlint 미설정, Android Lint 미구성). 또한 일부 의존성이 동적
버전(`1.3.+`, `1.4.+`)을 사용하고, 실사용되지 않는 의존성(Room, camera, retrofit 등)이
남아 있었다. 이슈 #79에서 정적 분석 도구를 도입하고 의존성을 정리한다.

## 결정

1. **정적 분석 도구로 detekt를 도입**한다.
   - 플러그인은 소스가 있는 `:app` 모듈에 적용하고(root 는 `apply false`), 룰셋은
     `config/detekt/detekt.yml`(프로젝트 CODE_STYLE/CONVENTION 기준으로 조정)에 둔다.
   - **기존 지적은 `config/detekt/detekt-baseline.xml`로 스냅샷 관리**한다(baseline 우선).
     대규모 로직 리팩토링은 이번 범위에서 제외하고, 명백한 안전 수정만 즉시 반영한다.
2. **Android Lint 블록**을 `app` 에 설정하고 `lint-baseline.xml`로 기존 지적을 관리한다.
3. **미사용 의존성 제거**: 소스 전역 검색으로 참조 0건이 확인된 것만 제거한다.
   - 제거: Room(`room-ktx/runtime/compiler` + ksp 소비자), `androidx.camera.*`,
     `play-services-location`, `retrofit`, `okhttp`, `logging-interceptor`,
     `converter-moshi`(카탈로그에만 존재).
   - Room 이 유일한 ksp 소비자였으므로 **ksp 플러그인도 함께 제거**한다.
   - 유지: `material`(themes.xml 의 `Theme.MaterialComponents` 부모로 사용),
     `accompanist-permissions`(POST_NOTIFICATIONS), sheets/drive/work 등 실사용 확인됨.
4. **동적 버전 고정**: `1.3.+` → `1.3.0-rc01`, `material 1.4.+` → `1.4.0`.
   현재 해석되던 값을 그대로 고정해 동작을 보존한다.
5. **release 최적화**: `proguard-rules.pro`를 작성해 `proguardFiles`로 연결하되,
   R8 최적화(`optimization.enable`) 활성화는 이번 범위에서 하지 않는다.

## 근거

- baseline 방식은 도구 도입과 기존 부채 정리를 분리해 회귀 위험을 최소화한다. 이후
  신규/변경 코드에만 규칙이 실질적으로 강제되어 점진적 개선이 가능하다.
- 동적 버전은 빌드 재현성을 해치므로 현재 해석값으로 고정한다. 안정판이 아직 없어
  `1.3.0-rc01`을 그대로 사용한다(임의 다운그레이드 시 동작 변화 위험).
- Firebase/Firestore/Google API Client 는 리플렉션 의존도가 높아 R8 활성화 시 keep 규칙
  검증이 필요하다. 규칙만 선작성하고 활성화는 별도로 검증한다.

## 검토한 대안

- **적극 리팩토링**(detekt 지적을 실제 코드 수정으로 전부 해소): 변경 범위가 커지고
  회귀 검증 부담이 크므로 제외. 필요 시 후속 이슈로 분리.
- **의존성 적극 제거**(의심 항목까지 전부 제거): 오탐으로 빌드가 깨질 위험이 있어,
  참조 0건이 확인된 것만 제거하는 보수적 방침을 택함.
- **R8 최적화 즉시 활성화**: release 빌드가 리플렉션 이슈로 깨질 수 있어 이번 범위 제외.

## 예상 결과

- `./gradlew detekt`, `./gradlew lint` 로 코드 품질을 자동 검사할 수 있다.
- 모든 의존성이 고정 버전을 사용하고 미사용 의존성이 사라져 빌드가 가벼워진다.
- CI(GitHub Actions) 연동은 이번 범위에서 제외하며 필요 시 별도 이슈로 분리한다.

## 후속 과제 (이번 범위 밖)

- **detekt-formatting(ktlint) 룰셋**: `Indentation`, `ImportOrdering`, `NoWildcardImports`
  등 포맷 규칙은 ktlint 기반 `detekt-formatting` 으로 강제할 수 있으나, 기존 코드에서
  다수의 baseline 항목이 추가로 생겨 노이즈가 커진다. 이번에는 도입을 보류하고 IDE/
  editorconfig 에 의존한다. 필요 시 별도 이슈로 분리한다.
- **R8/ProGuard 최적화 활성화**: `proguard-rules.pro` 는 선작성했으나 광범위한 keep
  규칙(`com.jkapp.**`, Firebase/GMS 전체)을 포함한다. 활성화 시점에 Firestore 역직렬화
  대상 모델 패키지로 keep 범위를 좁히고 release 빌드 동작을 검증한다.
- **취약점 스캔 자동화**: 의존성 취약점 점검(예: OWASP dependency-check/gradle-versions)
  자동화는 CI 연동과 함께 별도로 다룬다.
