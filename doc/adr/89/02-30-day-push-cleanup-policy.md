# `pushes` 문서를 createdAt 30일 경과 시 상태 무관 삭제하고, 하루 1회로 실행을 제한한다

**상태**: 결정됨 (2026-07-11 초안 → 2026-07-12 확정)
**날짜**: 2026-07-11

## 맥락

이슈 #89에서 `pushes` 컬렉션이 발송 요청 이력을 영속 문서로 남기기 시작하면서, 이 문서들이
무한정 쌓이지 않도록 정리 정책이 필요하다. 별도 서버가 없는 프로젝트 원칙상 정기 배치(Cloud
Scheduler 등 추가 인프라)보다는 앱이 이미 갖고 있는 진입 지점(시작/포그라운드 복귀)에서 처리하는
편이 새 인프라를 늘리지 않는다.

## 결정

### 삭제 기준: createdAt 30일 경과, status 무관

`pushes` 문서는 `createdAt`이 현재로부터 30일을 넘으면 `status`(`pending`/`sent`/`failed`)와
무관하게 삭제한다. 발송 실패(`failed`)로 남은 문서도 30일 후에는 함께 정리된다 — 재시도 로직이
없으므로 실패 문서를 더 오래 보존할 이유가 없다.

### 실행 시점: 앱 시작/포그라운드 복귀, 하루 1회 가드

`JkApp`의 `ProcessLifecycleOwner` 관찰자(onStart)에서 `PushCleanupScheduler.runIfNeeded()`를
호출한다. 이 콜백은 앱 최초 시작 시와 포그라운드로 돌아올 때마다 발생하므로, 매번 Firestore 쿼리를
실행하지 않도록 `common.AppPreferences`에 마지막 실행 날짜(`LocalDate`, DataStore)를 캐싱해 오늘
이미 실행했으면 건너뛴다.

## 근거

- **새 인프라 없이 정리**: Cloud Scheduler + 별도 Function을 추가하는 대신, 앱이 이미 관찰하는
  `ProcessLifecycleOwner` 이벤트에 얹으면 배포·비용 부담이 없다.
- **status 무관 삭제**: 이 컬렉션은 발송 이력일 뿐 참조 무결성이 걸린 데이터가 아니므로, 성공/실패
  구분 없이 시간 기준으로만 정리하는 것이 가장 단순하다.
- **하루 1회 가드**: 포그라운드 전환은 하루에도 여러 번 발생할 수 있어(잠금 해제·앱 전환 등), 매번
  쿼리하면 불필요한 Firestore 읽기 비용이 든다. 날짜만 비교하면 되므로 가벼운 `LocalDate` 캐시로
  충분하다.
- **콜백 주입 설계**: `PushCleanupScheduler`가 `AppPreferences`를 직접 참조하지 않고 get/set
  suspend 콜백만 받게 해, Android `Context` 없이 JVM 단위 테스트로 가드 로직을 검증할 수 있다.

## 검토한 대안

- **Cloud Scheduler + 별도 Cloud Function**: 별도 서버/스케줄 인프라를 추가하는 것은 "서버를 두지
  않는다"는 프로젝트 원칙과 배포 비용에 비해 이득이 적어 채택하지 않음.
  Firestore TTL 정책(자동 문서 만료)도 대안이었으나, 콘솔에서 컬렉션별 TTL 필드를 수동 설정해야
  하고 삭제 시점이 정확히 보장되지 않아, 이번에는 앱 주도 정리를 택하고 후속 과제로 남긴다.
- **매번 정리 실행(가드 없음)**: 포그라운드 전환마다 쿼리하면 정리 시점은 더 정확해지지만 불필요한
  Firestore 읽기 비용이 누적돼 채택하지 않음.
- **status별 보존 기간 차등(예: failed는 더 오래 보존)**: 재시도·수동 재발송 기능이 없는 현재
  범위에서는 실패 문서를 더 오래 남겨둘 이유가 없어 채택하지 않음.

## 예상 결과

- `common.AppPreferences`에 `lastPushCleanupDate` 키가 추가된다.
- `PushCleanupScheduler`가 `JkApp`에서 `PushTokenManager`와 유사한 방식으로 생성·연결된다.
- 이 정책은 `pushes`에만 적용되며, 다른 컬렉션(`todo-items` 등)의 보존 정책과는 무관하다.

## 후속 과제 (이번 범위 밖)

- Firestore TTL 정책으로 전환할지 여부(콘솔에서 `createdAt` 필드에 TTL 설정)는 이번 범위에
  포함하지 않는다. 앱 주도 정리로 충분한지 운영하며 재검토한다.
