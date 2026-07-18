# common 인프라

여러 feature가 공유하는 앱 셸(화면 골격), 로컬 설정(DataStore), 하단 탭 순서, Firestore 공유
인스턴스·확장 함수, 테마, 공용 UI/유틸을 모아 둔다. 패키지 경로: `com.jkapp.common`

## 책임

- 한다: 로그인 후 앱 셸(`MainScreen`)과 로그인/스플래시 화면 제공, 로컬 기기 설정(`AppPreferences`,
  DataStore) 관리, 하단 탭 순서(`tab-orders`) 저장(재배열 UI는 settings 화면에서 제공),
  공유 `FirebaseFirestore` 인스턴스와 코루틴 확장(`await`, `snapshotFlow`) 제공,
  테마·공용 다이얼로그·포맷 유틸 제공.
- 하지 않는다: 각 feature의 도메인 로직·화면(→ 각 feature 패키지), 서버 사용자 설정(→ `user`).

## 공개 API

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `AppPreferences` | 클래스 | DataStore(`app_preferences`) 래퍼. `sharedRootFolderId`, `darkModeSetting`, `hapticIntensity`, `notificationMode`, `notificationSound`를 `Flow`로 노출하고 setter 제공. IOException 시 크래시 없이 기본값/무시 처리 |
| `DarkModeSetting`, `NotificationMode`, `NotificationSound` | enum | 설정 값 타입. `NotificationMode`는 `hasSound`/`hasVibration` 파생 속성 포함 |
| `AppFirestore` | object | 공유 `FirebaseFirestore` 인스턴스(`lazy`). 캐시 등 설정을 한 곳에서 구성 |
| `Task<T>.await()`, `Query.snapshotFlow`, `DocumentReference.snapshotFlow` | 확장 함수 | Firestore Task/리스너를 코루틴·`Flow`로 감싸는 공통 골격 (`FirestoreExtensions.kt`) |
| `TabOrderRepository` / `TabOrderRepositoryImpl` | 인터페이스/클래스 | `observeTabOrder(uid): Flow<List<String>?>`, `saveTabOrder(uid, tabNames)` |
| `TabOrderViewModel` | `ViewModel` | `tabOrder: StateFlow<List<MainTab>>` — 현재 적용 순서. `editTabOrder: StateFlow<List<MainTab>?>` — 편집 중 임시 순서(null이면 편집 비활성). `loadTabOrder(uid)`, `moveTab(from, to)`, `beginEdit()`, `applyEdit()`, `cancelEdit()`. `mergeTabOrder`로 신규/삭제 탭 정합성 유지. `applyEdit`는 dirty 판정(원본과 순서가 다를 때만) 후 Firestore 저장 |
| `MainTab` | enum | `HOME`/`ASSET`/`DIARY`/`TODO`/`CALENDAR` (라벨만, 아이콘 제거). 콘텐츠 탭 5개. `HOME`은 하단바 왼쪽에 아이콘으로 고정 노출되고 탭 순서 재배치 대상이 아니다(항상 선두). 나머지 4개(ASSET/DIARY/TODO/CALENDAR)만 재배치 대상 |
| `BottomTabItem` | sealed interface | 하단 탭 항목 타입. `Content(tab: MainTab)` — 콘텐츠 탭(선택 상태 있음), `Action.Logout` — 로그아웃 액션(선택 상태 없음, 확인 다이얼로그 경유), `Action.Settings` — 설정 액션(선택 상태 없음, 설정 화면 이동) |
| `MainScreen`, `HomeTabScreen`, `LoginScreen`, `SplashScreen` | `@Composable` | 앱 셸·진입 화면. GNB(TopAppBar) 제거. 하단바 3구역: **왼쪽 고정** 홈 아이콘(`Icons.Default.Home`, 선택 시 primary 강조) + **가운데 가로 스크롤** 영역(홈 제외 콘텐츠 탭 텍스트 + 로그아웃 텍스트, 구분선·선택 강조. 각 항목은 가운데 영역 폭의 1/`VISIBLE_SCROLL_TAB_COUNT`(=4) 고정폭이라 4개가 딱 보이고 나머지(로그아웃)는 가로 스크롤로 접근) + **오른쪽 고정** 설정 아이콘(`Icons.Default.Settings`). 양 끝 고정 셀은 배경을 하단바 기본색보다 조금 짙게(`PINNED_TAB_COLOR_LIGHT`/`DARK`). 전체 하단바에 `navigationBarsPadding` 적용. `TabOrderPanel` 제거됨 |
| `JkappTheme` | `@Composable` | Material3 테마(다크/다이나믹 컬러). `theme/` 하위 Color·Type 포함 |
| `toComposeColorOrNull()`, `Long.formatFileSize()`, `LoadingIndicator` | 확장/`@Composable` | 공용 유틸(`Extensions.kt`) |
| `DateUtils`, `IsoDatePickerDialog`, `IsoDateTimePickerDialog` | 유틸/`@Composable` | 날짜 포맷·ISO 날짜 선택 다이얼로그 |
| `MultiDeleteState<T>` | 클래스 | 다중선택 삭제 상태 홀더. `isSelectionMode`, `selectedIds: Set<T>`, `toggle(id)`, `exit()` 노출. 투자종목·벤치마크·TODO 세 화면이 공유 |
| `MultiDeleteBar` | `@Composable` | 선택 모드 하단 바. "선택 삭제"·"취소" 버튼만 포함. '전체 삭제'는 각 화면 고유로 남긴다 |

## 데이터 / 저장소

- DataStore Preferences: 이름 `app_preferences`. 키 — `shared_root_folder_id`,
  `dark_mode_setting`, `haptic_intensity`, `notification_mode`, `notification_sound`.
- Firestore 컬렉션 `tab-orders` (`TabOrderRepositoryImpl`):

| Firestore 필드 | 설명 |
|----------------|------|
| (문서 ID) | 사용자 `uid` |
| `tabs` | `List<String>` — `MainTab.name` 순서. 문서 없으면 `null` 방출(미지정 사용자 구분) |

- `AppFirestore.instance`는 저장소가 아니라 앱 전역이 공유하는 Firestore 클라이언트다.

## 의존 관계

- 사용하는 곳: 사실상 모든 feature. 각 `XxxFirestoreRepositoryImpl`이 `AppFirestore`·`await`·
  `snapshotFlow`를 사용하고, `drive`가 `AppPreferences.sharedRootFolderId`, `settings`가
  `AppPreferences`(다크모드·햅틱·알림)를 사용한다. `MainActivity`가 앱 셸·테마·`AppPreferences`를 연결.
- 의존하는 것: Cloud Firestore SDK, AndroidX DataStore, Jetpack Compose/Material3, 각 feature의
  화면·ViewModel(`MainScreen`이 조립하는 대상). 다른 인프라에 대한 역방향 의존은 없다.

## 관련 결정 (ADR)

- [`doc/adr/41/tab-order-firestore-schema.md`](../../adr/41/tab-order-firestore-schema.md) — 하단 탭 순서 저장 구조(`tab-orders`, `mergeTabOrder` 정합성)
- [`doc/adr/57/settings-preference-scope-and-timezone-storage.md`](../../adr/57/settings-preference-scope-and-timezone-storage.md) — 기기별 설정을 DataStore(로컬)에 두는 저장 범위 결정
- [`doc/adr/100/remove-gnb-and-tab-interaction-redesign.md`](../../adr/100/remove-gnb-and-tab-interaction-redesign.md) — GNB 제거, 하단 탭 텍스트 전용·가로 슬라이드, 탭 재배열을 설정 화면으로 이동한 결정
