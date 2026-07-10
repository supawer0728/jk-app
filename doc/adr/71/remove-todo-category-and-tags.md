# TODO에서 카테고리·태그 기능 제거

**상태**: 결정됨
**날짜**: 2026-07-11

## 맥락

이슈 #71 초안은 카테고리 삭제 점검과 태그 전역 삭제를 포함했다. 그러나 실사용 검토 결과,
가족 2인용 간단한 할일 관리에는 카테고리·태그 분류가 과하다고 판단되어, 개발자가 명시적으로
`TodoItem.categoryId`·`TodoItem.tags` 속성과 `TodoCategory` 클래스 자체의 제거를 지시했다.

## 결정

- `TodoItem`에서 `categoryId: String?`, `tags: List<String>` 속성을 제거한다.
- `TodoCategory` 데이터 클래스와 `TodoCategoryManagementScreen`을 삭제한다.
- Repository(인터페이스·구현체·Fake)에서 카테고리 CRUD와 태그 삭제 API를 제거하고,
  `todo-categories` 컬렉션을 더 이상 사용하지 않는다.
- `TodoUiState.Success`에서 `categories` 필드를 제거한다.
- ViewModel의 카테고리/태그 필터, 목록/폼 화면의 카테고리 드롭다운·태그 입력, 관련 네비게이션
  라우트(`TodoCategoryManagementRoute`)와 배선을 모두 제거한다.
- 남는 필터는 담당자 필터만 유지한다.

## 근거

- 담당자·상태·우선순위·반복만으로 2인 협업 할일 관리에 충분하다.
- 분류 축(카테고리/태그)을 없애 목록·입력 UI가 단순해지고, 유지보수 표면이 줄어든다.

## 검토한 대안

- **카테고리/태그 유지 + 태그 전역 삭제만 추가**: 초안 방향이었으나, 분류 자체가 불필요하다는
  판단으로 폐기.

## 예상 결과

- 기존 `todo-items` 문서에 남아 있는 `categoryId`/`tags` 필드는 읽기에서 무시된다(매핑 제외).
  `update` 시 함께 삭제하지는 않으므로 물리적으로 남지만, 앱 동작에는 영향이 없는 잔여 데이터다.
- 별도의 데이터 삭제 마이그레이션은 수행하지 않는다.
