# drive 인프라

다이어리 기록에 첨부되는 파일(사진 등)을 Google Drive에 업로드/다운로드/삭제하고, Firestore에
저장할 메타데이터(`Attachment`)만 반환한다. 패키지 경로: `com.jkapp.drive`

## 책임

- 한다: OAuth 계정 선택(`setAccount`), 공유 루트 폴더 지정(`setSharedRootFolderId`), 기록별
  폴더 계층 자동 생성 후 파일 업로드, 파일 다운로드(`InputStream`)·삭제, 인증 동의가 필요할 때
  복구 `Intent`를 담은 예외(`DriveAuthRequiredException`) 발생.
- 하지 않는다: 첨부 메타데이터의 Firestore 저장(→ `diary` 도메인의 `CatRecord.attachments`),
  계정 선택 UI 표시(복구 `Intent`를 호출부에 넘겨 위임), 파일 바이트의 로컬 캐싱.

## 공개 API

| 구성요소 | 종류 | 설명 |
|----------|------|------|
| `DriveRepository` | 인터페이스 | `setAccount(accountName)`, `setSharedRootFolderId(id)`, `uploadFile(recordId, inputStream, fileName, mimeType): Attachment`, `deleteFile(fileId)`, `downloadFile(fileId): InputStream`. `companion object`에 `NoOp`(계정 미초기화 시 안전한 기본 구현) 제공 |
| `DriveRepositoryImpl` | 클래스 | `GoogleAccountCredential`(OAuth) + Drive API v3 클라이언트 구현. 폴더 id를 캐시(`folderIdCache`)하고 `Dispatchers.IO`에서 실행 |
| `Attachment` | data class | `fileId`, `name`, `mimeType`, `size: Long` — Firestore에 저장되는 메타데이터 |
| `DriveAuthRequiredException` | 예외 | `recoveryIntent: Intent`를 담아, 계정 선택/권한 동의가 필요함을 호출부에 알림 |

폴더 계층은 `<sharedRoot 또는 "jkapp">/cat-record/<recordId>/attachment` 순으로 없으면
생성한다. 삭제는 best-effort로, 인증 미완료(`UserRecoverableAuthIOException`) 시 무시한다.

## 데이터 / 저장소

- Firestore 컬렉션 없음.
- 외부 저장소: Google Drive. 공유 루트 폴더 id는 `common.AppPreferences.sharedRootFolderId`
  (DataStore)에서 주입받으며, 기본값이 소스에 상수로 하드코딩되어 있다.

## 의존 관계

- 사용하는 곳: `diary` — `DiaryViewModel`이 `DriveRepositoryImpl`을 주입받아 `DiaryScreen`/
  `DiaryFormScreen`/`DiaryDetailScreen`의 첨부 업로드·조회에 사용. `MainActivity`가 구현체를 생성.
- 의존하는 것: Google Drive API v3 클라이언트, `GoogleAccountCredential`(OAuth, `DriveScopes.DRIVE`),
  `common.AppPreferences`(공유 폴더 id).

## 관련 결정 (ADR)

- [`doc/adr/7/01-drive-api-client.md`](../../adr/7/01-drive-api-client.md) — Google Drive API 클라이언트 선택
- [`doc/adr/7/02-drive-auth-strategy.md`](../../adr/7/02-drive-auth-strategy.md) — Drive 인증(OAuth 계정 선택·복구 Intent) 전략
