# ADR: 벤치마크 차트 라이브러리 선택 — Vico

- **이슈**: #98 벤치마크 차트 보기(수익률·MDD) 추가
- **상태**: 확정
- **결정일**: 2026-07-18

## 컨텍스트

벤치마크 화면에 수익률 이중 축 차트(막대 + 선 4종)와 MDD 영역 차트(4종)를 추가해야 한다.
Jetpack Compose 전용이며 Material3 테마를 자연스럽게 따르는 라이브러리가 필요하다.

## 결정

**Vico** (`com.patrykandpatrick.vico`, Apache-2.0) 3.x 를 도입한다.
Maven Central 좌표: `com.patrykandpatrick.vico:compose-m3`.
도입 버전: **3.2.3** (빌드 검증 완료).

## 근거

| 기준 | Vico | MPAndroidChart | Compose Charts(Google) |
|------|------|----------------|------------------------|
| Compose 전용 | O | X (View 기반) | O |
| Material3 통합 | O (`compose-m3`) | X | 일부 |
| 이중 Y축 지원 | O (3.x `startAxisItemPlacer` + `endAxisItemPlacer`) | O | X |
| 영역 차트 | O (`LineCartesianLayer.fill`) | O | △ |
| 라이선스 | Apache-2.0 | Apache-2.0 | Apache-2.0 |
| 활발한 유지보수 | O | △ | △ |

MPAndroidChart는 View 기반이라 Compose interop 비용이 크고, Google Compose Charts는 이중 Y축을 지원하지 않는다.

## 결과

- `gradle/libs.versions.toml`에 `vico = "3.2.3"` 버전 항목과 `vico-compose-m3` 라이브러리 항목 추가.
- `app/build.gradle.kts`에 `implementation(libs.vico.compose.m3)` 추가.
- 3.x API(`CartesianChartHost`, `CartesianChart`, `LineCartesianLayer`, `ColumnCartesianLayer`, `rememberCartesianChart` 등)로 구현한다. 2.x API와 호환되지 않음에 주의.
