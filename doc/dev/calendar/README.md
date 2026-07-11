# calendar(캘린더) — 자리표시자

캘린더 기능은 아직 **미구현(자리표시자)** 상태다. 도메인 모델, ViewModel, Repository, Firestore
컬렉션이 모두 없다.

현재 존재하는 것은 `CalendarTabScreen`(`app/src/main/java/com/jkapp/calendar/CalendarTabScreen.kt`)
하나뿐이며, 화면 중앙에 자리표시자 문구(`R.string.tab_calendar_placeholder`)만 표시한다.

실제 일정 관리 기능을 구현할 때 이 README를 [`DOMAIN.md`](../diary/DOMAIN.md)/[`FEATURE.md`](../diary/FEATURE.md)
형식(→ [`doc/template/`](../../template))으로 분리해 작성하고, [`doc/dev/README.md`](../README.md)의
feature 지도를 갱신한다.
