# diary(육묘일기) 도메인

반려묘의 건강·생활 기록을 남기는 도메인. 하나의 기록(`CatRecord`)은 날짜·기록유형·본문·
첨부파일로 구성되고, 기록유형(`CatRecordType`)은 사용자가 관리하는 분류 체계다.

## 도메인 모델

### CatRecord

`app/src/main/java/com/jkapp/diary/CatRecord.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `firestoreId` | `String?` | Firestore 문서 auto-ID. 신규 생성 전에는 `null` |
| `date` | `String` | 기록 날짜. ISO_LOCAL_DATE(`yyyy-MM-dd`) 문자열 |
| `recordType` | `String` | 기록유형 ID (`CatRecordType.id` 참조) |
| `record` | `String` | 기록 본문 |
| `attachments` | `List<Attachment>` | 첨부파일 메타데이터. 기본값 `emptyList()` (역호환) |

### CatRecordType

`app/src/main/java/com/jkapp/diary/CatRecordType.kt`

| 속성 | 타입 | 설명 |
|------|------|------|
| `id` | `String` | 기록유형 논리 ID (예: `DAILY_NOTE`). `CatRecord.recordType`와 매칭 |
| `name` | `String` | 표시 이름 |
| `emoji` | `String` | 배지 이모지 (기본 `📝`) |
| `fontColor` | `String` | 글자색 hex (기본 `#000000`) |
| `backgroundColor` | `String` | 배경색 hex (기본 `#FFFFFF`) |
| `docId` | `String` | Firestore 문서 ID. 기본값 `""` (조회 시 문서 id로 채움) |

## 기능 (메서드)

`DiaryFirestoreRepository`가 노출하는 동작.

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `getRecordTypes` | `(): Flow<List<CatRecordType>>` | 기록유형 실시간 구독 (name 오름차순) |
| `getRecords` | `(): Flow<List<CatRecord>>` | 기록 실시간 구독 |
| `addRecord` | `(CatRecord): String` | 기록 추가, 생성된 firestoreId 반환 |
| `updateRecord` | `(CatRecord): Unit` | 기록 수정 (firestoreId 필수) |
| `deleteRecord` | `(firestoreId: String): Unit` | 기록 삭제 |
| `addRecordType` | `(CatRecordType): Unit` | 기록유형 추가 |
| `updateRecordType` | `(CatRecordType): Unit` | 기록유형 수정 |
| `deleteRecordTypeAndReassignRecords` | `(typeDocId, affectedRecordIds, fallbackTypeId): Unit` | 유형 삭제 + 영향받는 기록을 fallback 유형으로 일괄 재배정 (batch) |

## 타 도메인과의 연관성

- `drive.Attachment` — `CatRecord.attachments`로 포함. 첨부파일 실제 바이트는 Google Drive에
  저장되고 여기에는 메타데이터(fileId/name/mimeType/size)만 담는다. [`infra/drive.md`](../infra/drive.md)
- `CatRecord.recordType` → `CatRecordType.id` — 문자열 참조(외래키 성격). FK 제약은 없고,
  유형 삭제 시 재배정으로 정합성을 유지한다(FEATURE.md 참고).

## Firestore 컬렉션

담당 Repository: `DiaryFirestoreRepositoryImpl`

### `cat-records` (CatRecord)

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| (문서 ID) | `firestoreId` | auto-ID |
| `date` | `date` | |
| `record_type` | `recordType` | snake_case |
| `record` | `record` | |
| `attachments` | `attachments` | 맵 배열 (`fileId`/`name`/`mimeType`/`size`) |

### `cat-record-types` (CatRecordType)

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| (문서 ID) | `docId` | |
| `id` | `id` | 없으면 문서 id로 대체 |
| `name` | `name` | |
| `emoji` | `emoji` | |
| `fontColor` | `fontColor` | 읽기 시 `font_color`(snake)도 폴백 지원, 쓰기는 camelCase |
| `backgroundColor` | `backgroundColor` | 읽기 시 `background_color`(snake)도 폴백 지원, 쓰기는 camelCase |
