# common 인프라

여러 feature가 공유하는 앱 셸(화면 골격), 로컬 설정(DataStore), 하단 탭 순서, Firestore 공유
인스턴스·확장 함수, 테마, 공용 UI/유틸을 모아 둔다. 패키지 경로: `com.jkapp.common`

## 책임

- 한다: 로그인 후 앱 셸(`MainScreen`)과 로그인/스플래시 화면 제공, 로컬 기기 설정(`AppPreferences`,
  DataStore) 관리, 하단 탭 순서(`tab-orders`) 저장·재배열, 공유 `FirebaseFirestore` 인스턴스와
  코루틴 확장(`await`, `snapshotFlow`) 제공, 테마·공용 다이얼로그·포맷 유틸 제공.
- 하지 않는다: 각 feature의 도메인 로직·화면(→ 각 feature 패키지), 서버 사용자 설정(→ `user`).

## 공개 API

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `AppPreferences` | 클래스 | DataStore(`app_preferences`) 래퍼. `sharedRootFolderId`, `darkModeSetting`, `hapticIntensity`, `notificationMode`, `notificationSound`를 `Flow`로 노출하고 setter 제공. IOException 시 크래시 없이 기본값/무시 처리 |
| `DarkModeSetting`, `NotificationMode`, `NotificationSound` | enum | 설정 값 타입. `NotificationMode`는 `hasSound`/`hasVibration` 파생 속성 포함 |
| `AppFirestore` | object | 공유 `FirebaseFirestore` 인스턴스(`lazy`). 캐시 등 설정을 한 곳에서 구성 |
| `Task<T>.await()`, `Query.snapshotFlow`, `DocumentReference.snapshotFlow` | 확장 함수 | Firestore Task/리스너를 코루틴·`Flow`로 감싸는 공통 골격 (`FirestoreExtensions.kt`) |
| `TabOrderRepository` / `TabOrderRepositoryImpl` | 인터페이스/클래스 | `observeTabOrder(uid): Flow<List<String>?>`, `saveTabOrder(uid, tabNames)` |
| `TabOrderViewModel` | `ViewModel` | `tabOrder: StateFlow<List<MainTab>>`, `isEditMode`, `loadTabOrder`, `moveTab`, `toggleEditMode`. `mergeTabOrder`로 신규/삭제 탭 정합성 유지 |
| `MainTab` | enum | `HOME`/`ASSET`/`DIARY`/`TODO`/`CALENDAR` (라벨·아이콘). `MAIN_TAB_ROW_SIZE` 상수 공유 |
| `MainScreen`, `HomeTabScreen`, `LoginScreen`, `SplashScreen`, `TabOrderPanel` | `@Composable` | 앱 셸·진입 화면·탭 재배열 패널 |
| `JkappTheme` | `@Composable` | Material3 테마(다크/다이나믹 컬러). `theme/` 하위 Color·Type 포함 |
| `toComposeColorOrNull()`, `Long.formatFileSize()`, `LoadingIndicator` | 확장/`@Composable` | 공용 유틸(`Extensions.kt`) |
| `DateUtils`, `IsoDatePickerDialog`, `IsoDateTimePickerDialog` | 유틸/`@Composable` | 날짜 포맷·ISO 날짜 선택 다이얼로그 |

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

- [`doc/adr/41/tab-order-firestore-schema.md`](../../adr/41/tab-order-firestore-schema.md) — 하단 탭 순서 저장 구조(`tab-orders`) 및 재배열 UI 방식
- [`doc/adr/57/settings-preference-scope-and-timezone-storage.md`](../../adr/57/settings-preference-scope-and-timezone-storage.md) — 기기별 설정을 DataStore(로컬)에 두는 저장 범위 결정
