# 첨부파일 데이터 모델 및 Drive 연동 전략

**상태**: 결정됨
**날짜**: 2026-06-28

## 맥락

육묘일기에 이미지·파일을 첨부하는 기능을 추가한다. 에디터 툴(Toast UI Editor)은 사용하지
않기로 확정되어, 첨부파일 추가는 시스템 파일 선택기(ActivityResultContracts)로 처리한다.

이에 따라 첨부파일 메타데이터를 어디에, 어떤 구조로 저장할지 결정해야 한다.

## 결정

### 첨부파일 메타데이터 모델

`Attachment` 데이터 클래스를 별도로 정의하고 `CatRecord`에 `attachments: List<Attachment>`
필드를 추가한다. `Attachment`는 Drive 파일 관리에 필요한 최소 정보를 담는다.

```kotlin
data class Attachment(
    val fileId: String,
    val name: String,
    val mimeType: String,
    val size: Long,
)
```

`CatRecord`의 `attachments` 필드는 `emptyList()`를 기본값으로 하여 기존 Firestore
문서와 역호환성을 유지한다.

### Drive 파일 저장 경로

```
jkapp/cat-record/{CatRecord.firestoreId}/attachment/{Attachment.fileId}
```

### Drive 인증 시점

기존 ADR/7/02 결정을 계승한다: 육묘일기 탭 최초 진입 시 `drive.file` 스코프를 요청한다.
액세스 토큰은 `DiaryViewModel` 메모리에만 보관한다.

### 가비지 컬렉션(GC) 전략

| 이벤트 | GC 동작 |
|--------|---------|
| 일기 삭제 | `CatRecord.attachments` 전체 Drive에서 삭제 |
| 일기 수정 저장 | 기존 `attachments`에서 새 목록에 없는 항목 Drive에서 삭제 |
| 일기 작성 취소 | 작성 중 업로드된 `pendingAttachments` 전체 Drive에서 삭제 |

GC는 Firestore 저장 완료 후 Drive 삭제 순서로 실행한다. Drive 삭제 실패는 로그로 기록하되
사용자 UX를 블록하지 않는다(best-effort).

### 첨부파일 뷰어 (DiaryDetailScreen)

- 이미지(`image/*`): Coil로 Drive 다운로드 URL 로딩
- 동영상(`video/*`): 외부 앱으로 열기(`Intent.ACTION_VIEW`)
- 그 외 문서: 외부 앱으로 열기 또는 로컬 다운로드

## 근거

- `Attachment` 메타데이터를 Firestore에 함께 저장하면 Drive API 호출 없이 목록 화면을 렌더링할 수 있다.
- `firestoreId`를 Drive 경로에 포함하면 레코드와 파일 간 관계가 경로만으로 명확해진다.
- GC를 best-effort로 처리하면 Drive 일시 오류가 일기 저장/삭제 성공을 막지 않는다.
- Drive 인증을 탭 진입 시 미리 수행하면 첫 첨부 시 지연 없이 즉시 업로드 가능하다.

## 검토한 대안

- **Drive 폴더 경로만 저장, attachments 필드 제거**: Drive API 호출이 목록 화면에서도 필요해
  불필요한 네트워크 비용 발생. 오프라인 동작 불가.
- **Firebase Storage 사용**: 추가 서비스 비용 발생. 프로젝트 원칙(비용 절감)에 반함.
- **GC를 동기 처리**: Drive 오류 시 사용자 화면이 블록됨. UX 저하.

## 예상 결과

`DiaryViewModel`이 Drive 액세스 토큰과 `pendingAttachments`를 관리한다.
`DriveRepository`가 파일 업로드·삭제·다운로드를 담당한다.
`DiaryFormScreen`에 파일 선택 버튼과 첨부 목록이 추가된다.
`DiaryDetailScreen`에 첨부파일 목록과 뷰어가 추가된다.
