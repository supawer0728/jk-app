# cat-records 날짜별 묶음 표시

**상태**: 결정됨
**날짜**: 2026-06-28

## 맥락

Firestore `cat-records` 컬렉션은 같은 날짜에 여러 기록 유형(HOSPITAL_VISIT, DAILY_NOTE 등)이 독립 문서로 존재할 수 있다. 예를 들어 병원 방문일에 HOSPITAL_VISIT과 DAILY_NOTE가 각각 별도 문서로 기록된다.

리스트 화면에서 이를 어떻게 표시할지 결정이 필요했다.

## 결정

날짜를 기준으로 묶어 하나의 카드로 표시한다. 카드 안에 해당 날짜의 모든 기록 유형 배지(emoji + 이름)를 나열하고, 첫 번째 기록의 첫 줄을 미리보기로 표시한다. 상세 화면은 `DiaryDetailRoute(date)`로 이동해 해당 날짜의 모든 기록을 한 화면에 보여준다.

## 근거

- 사용자가 날짜를 기준으로 사건을 기억하는 방식에 부합한다
- 같은 날짜에 여러 기록이 있어도 리스트가 길어지지 않는다
- `DiaryDetailRoute`의 key가 `date` 하나로 단순하게 유지된다

## 검토한 대안

**각각 별개 아이템으로 표시**: 리스트에서 date + recordType 조합을 key로 사용하고, `DiaryDetailRoute`에 `recordType` 파라미터를 추가하는 방식. 기록별 독립 탐색이 가능하지만 같은 날짜가 반복돼 리스트가 어수선해질 수 있다.

## 예상 결과

- `DiaryDetailRoute(date: String)` — 변경 없음, 해당 날짜 기록 전체를 한 화면에 표시
- `DiaryFormRoute(firestoreId: String? = null)` — auto-ID로 기록을 특정해 편집
- 상세 화면에서 개별 기록의 수정/삭제가 firestoreId 기준으로 동작
- 모든 기록이 삭제되면 상세 화면이 자동으로 뒤로 이동
