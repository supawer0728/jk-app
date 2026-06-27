# DiaryViewModel 스코프 전략

**상태**: 결정됨
**날짜**: 2026-06-28

## 맥락

육묘일기 기능은 목록(DiaryScreen), 상세(DiaryDetailScreen), 추가/수정(DiaryFormScreen) 세 화면이 동일한 데이터 상태를 공유해야 한다.
이 세 화면은 Navigation3의 분리된 NavDisplay 엔트리로 구현된다.

## 결정

`DiaryViewModel`을 `MainActivity`에서 `by viewModels()`로 생성하여 Activity 범위로 스코핑한다.
`authViewModel`과 동일한 방식으로 각 NavDisplay 엔트리에 파라미터로 전달한다.

## 근거

- 세 화면이 동일한 ViewModel 인스턴스를 참조하여 데이터 동기화 문제 없음
- 목록 → 상세 → 수정 → 저장 후 목록 복귀 시 상태가 자연스럽게 공유됨
- Navigation3에서 엔트리 간 ViewModel 공유를 위한 별도 메커니즘이 필요 없음
- `authViewModel` 패턴과 일관성 유지

## 검토한 대안

- **각 화면 독립 ViewModel**: 화면 간 데이터 동기화를 위한 별도 메커니즘(공유 StateFlow 등) 필요
- **Navigation3 엔트리 범위 ViewModel**: 엔트리 이동 시 ViewModel이 재생성되어 Drive 인증 상태 소멸

## 예상 결과

`MainActivity`에 `diaryViewModel: DiaryViewModel by viewModels()` 추가.
`MainScreen`, `DiaryDetailScreen`, `DiaryFormScreen` 모두 동일한 인스턴스를 사용.
Drive 액세스 토큰과 레코드 캐시가 세 화면 간 공유된다.
