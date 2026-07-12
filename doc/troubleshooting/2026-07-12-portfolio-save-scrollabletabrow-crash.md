# 포트폴리오 저장 시 앱이 종료(크래시)되지만 데이터는 저장됨

**상태**: 해결됨
**날짜**: 2026-07-12
**영역**: ui (Compose Material3) / investment(portfolio)

## 증상

포트폴리오 편집 다이얼로그에서 그룹 목록을 지정하고 **저장**을 누르면 **앱이 즉시 종료**된다.
그런데 앱을 다시 켜 포트폴리오로 들어가면 **방금 저장한 포트폴리오가 목록에 추가돼 있다**
(즉 Firestore 저장 자체는 성공). "저장 시 크래시"라는 증상과 달리 저장 로직은 정상이었다.

## 환경

- 실기기(Android 15 / API 35), debug 빌드.
- Compose BOM `2026.06.00`, Material3.
- 포트폴리오가 여러 개가 되는 상황(탭이 늘어나는 순간)에서 재현.

## 진단 과정

1. 저장 → 파이 차트 재계산·렌더 경로가 유력하다고 보고 breadcrumb 로그를 심었다
   (`savePortfolio` 진입/`upsert 성공`/`pieSlices 계산`/`PortfolioPieChart 진입` …).
2. 로그상 `savePortfolio 진입 → upsert 시작`까지는 정상. 이후 파이 차트 로그가 찍히기 전에
   `ComposeInternal: Error was captured in composition` + `AndroidRuntime FATAL EXCEPTION`이 떴다.
3. 스택 트레이스가 정확한 위치를 지목했다 — **파이 차트가 아니라 탭.**

```
java.lang.IndexOutOfBoundsException: Index 4 out of bounds for length 4
    at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(TabRow.kt:1409)
    at ...RecomposeScopeImpl.compose ... Recomposer.performRecompose ...
```

`length 4`(탭 위치 배열 크기) < `Index 4`(선택 인덱스) → 선택 인덱스가 아직 측정되지 않은
탭 위치를 벗어나 접근.

## 근본 원인

`ScrollableTabRow`(**deprecated**)는 탭 개수가 동적으로 바뀔 때 내부에서 `tabPositions`를
인덱스로 접근하며 clamp하지 않는다. 포트폴리오 저장 성공 시 두 상태가 서로 다른 프레임에 도착한다.

- `PortfolioViewModel.savePortfolio` → `onSuccess` 에서 `_selectedPortfolioId = 새 ID` (선택 인덱스 → 마지막 탭)
- Firestore snapshot 리스너 → 포트폴리오 목록 N → N+1 (탭 개수 증가)

이 둘이 어긋나는 프레임에서 **선택 인덱스(예: 4)가 `ScrollableTabRow`가 아직 측정하지 못한
탭 위치 배열(길이 4)을 벗어나** `tabPositions[4]` 접근 → `IndexOutOfBoundsException` →
컴포지션 중 예외라 앱이 종료됐다. **증상(저장 크래시)과 원인(TabRow 인덱스)이 어긋난 사례.**

## 해결

`ScrollableTabRow` → **`SecondaryScrollableTabRow`**(동적 탭 개수에서 인덱스를 안전하게
처리하는 비-deprecated 버전)로 이관하고, 선택 인덱스를 방어적으로 clamp 했다.

```kotlin
// before: androidx.compose.material3.ScrollableTabRow (deprecated)
// after:
SecondaryScrollableTabRow(
    selectedTabIndex = state.portfolios.indexOfFirst {
        it.firestoreId == selectedPortfolio?.firestoreId
    }.coerceIn(0, (state.portfolios.size - 1).coerceAtLeast(0)),
    edgePadding = 0.dp,
) { /* tabs */ }
```

- 파일: `app/src/main/java/com/jkapp/finance/investment/PortfolioScreen.kt`
- 이 수정은 코드리뷰에서 Low로 지적됐던 "deprecated `ScrollableTabRow`" 항목도 함께 해소한다.

## 재발 방지 / 다음에 빠르게 확인하는 법

- **"저장/추가 시 크래시"라도 저장 로직부터 의심하지 말고 스택 트레이스를 먼저 본다.**
  데이터가 남아 있으면(저장 성공) 크래시는 그 **직후 재구성(recomposition)** 에서 났을 확률이 높다.
- 탭·리스트 등 **개수가 동적으로 바뀌는 Compose 컴포넌트**는 deprecated `ScrollableTabRow` 대신
  `SecondaryScrollableTabRow`/`PrimaryScrollableTabRow`를 쓴다(인덱스 clamp 내장).
- 선택 인덱스는 항상 현재 항목 수에 맞춰 `coerceIn(0, size-1)`으로 방어한다.
- `ComposeInternal: Error was captured in composition` 로그가 보이면 그 아래 스택이 정확한 지점이다.

## 관련

- 코드: `com.jkapp.finance.investment.PortfolioScreen`(포트폴리오 탭), `PortfolioViewModel.savePortfolio`
- 이슈/PR: #91 / PR #92 (포트폴리오 기능)
