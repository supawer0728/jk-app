# 하단 탭 순서 저장 구조 및 재배열 UI 구현 방식

**상태**: 결정됨
**날짜**: 2026-07-05

## 맥락

이슈 #41은 하단 탭 확장(TODO, CALENDAR 추가) 및 사용자별 탭 순서 커스터마이징(스와이프 패널, 드래그앤드롭 재배열, Firestore 영속화)을 요구한다. 기존 `FirestoreRepository`/`FirestoreRepositoryImpl`은 모든 컬렉션이 전역(global)이며 사용자(uid)로 스코프된 데이터가 없었다. 또한 프로젝트에는 드래그앤드롭 재배열 UI를 위한 외부 라이브러리(`compose-reorderable` 등)가 없었다.

## 결정

1. **신규 Repository 분리**: `tab-orders` 컬렉션을 사용자별로 분리된 `TabOrderRepository`/`TabOrderRepositoryImpl`로 구현했다. 기존 모놀리식 `FirestoreRepository`에 메서드를 추가하지 않고 별도 클래스로 둔 이유는, uid로 스코프되는 문서 구조(문서ID=uid, 필드 `tabs: List<String>`)가 기존의 전역 컬렉션들과 근본적으로 다른 접근 패턴이기 때문이다. 이슈 본문에서도 "신규 Repository"를 명시적으로 요구했다.
2. **탭 순서 표현**: `MainTab` enum의 `name`(문자열)을 순서대로 저장한다. 저장된 이름 중 더 이상 존재하지 않는 탭은 무시하고, `MainTab.entries`에 있지만 저장된 목록에 없는 신규 탭은 맨 뒤에 자동으로 붙는다 (`TabOrderViewModel.mergeTabOrder`).
3. **드래그앤드롭 재배열**: 외부 라이브러리 없이 `pointerInput`/`detectDragGestures` + `LazyVerticalGrid`의 `animateItem()`을 조합해 직접 구현했다. 4열 그리드 기준으로 드래그 오프셋을 행/열 인덱스로 환산해 `moveTab(from, to)`를 호출하고, 드래그 중인 아이템만 `animateItem()`을 건너뛰어(수동 오프셋과 레이아웃 애니메이션이 겹치지 않도록) 나머지 아이템은 자동으로 자리를 양보하는 애니메이션이 적용되도록 했다.
4. **편집 모드 중 스냅샷 무시**: `TabOrderViewModel`은 편집 모드(`isEditMode == true`)인 동안 Firestore 스냅샷 리스너의 값을 무시한다. 그렇지 않으면 느린 초기 로드나 다른 기기의 변경이 사용자의 드래그 중인 순서를 덮어쓸 수 있다. '완료' 시점에만 현재 순서를 저장한다.
5. **하단바는 4개까지만 노출**: 탭이 계속 늘어날 것을 대비해 collapsed `NavigationBar`는 `tabOrder`의 앞 4개만 보여주고, 전체 목록은 스와이프 업 패널(4열 그리드)에서 확인/재배열한다.

## 근거

- 기존 `FirestoreRepositoryImpl`의 컨벤션(컬렉션 상수, `callbackFlow`+`addSnapshotListener`, `suspendCancellableCoroutine`+리스너)을 그대로 따르면서도, uid 스코프 데이터라는 새로운 축을 별도 클래스로 분리해 기존 코드에 영향을 주지 않았다.
- 외부 reorder 라이브러리를 추가하지 않은 이유는 프로젝트에 선례가 없고, 요구되는 상호작용(4열 그리드, jiggle 애니메이션)이 `pointerInput` 기반으로 충분히 구현 가능한 범위였기 때문이다.

## 검토한 대안

- **기존 `FirestoreRepository`에 탭 순서 메서드 추가**: 모놀리식 패턴과의 일관성은 있지만, uid 스코프라는 이질적인 데이터 모델이 섞여 인터페이스가 혼란스러워질 것으로 판단해 제외했다.
- **`LazyVerticalGrid` 대신 `FlowRow` 기반 비-Lazy 레이아웃**: 애니메이션(`animateItem()`)을 활용할 수 없어 재배열 시 자리 양보 애니메이션을 별도로 구현해야 했으므로 제외했다. `LazyVerticalGrid`에 탭 개수에 따라 계산한 고정 높이(`TAB_ITEM_SIZE * rows`)를 주어 무한 높이 제약 문제를 해결했다.

## 예상 결과

- 탭이 5개 이상으로 늘어나도 하단바는 항상 4개만 노출되고, 나머지는 스와이프 업 패널에서 접근한다.
- 사용자별 탭 순서는 기기/재실행 간 유지되며, 신규 탭 추가 시 자동으로 맨 뒤에 붙는다.
- 드래그앤드롭 재배열 로직(2D 그리드 오프셋 계산)은 그리드 열 수(`TAB_GRID_COLUMNS`)가 바뀌면 함께 검토가 필요하다.
