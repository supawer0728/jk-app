# 첨부파일 업로드 타이밍 및 신규 생성 플로우

**상태**: 결정됨  
**날짜**: 2026-06-28

## 맥락

초기 구현에서는 사용자가 파일을 선택하는 즉시 Drive 업로드를 수행하였다.
그러나 이 방식에는 두 가지 문제가 있었다.

1. **garbage 파일 발생**: 파일을 선택한 후 저장 없이 취소하면 Drive에 파일이 남는다.
   ADR 01의 GC 전략(취소 시 Drive 삭제)을 적용하더라도, 취소 직전 네트워크 오류나
   앱 종료가 발생하면 GC가 실행되지 않아 고아 파일이 생긴다.

2. **신규 생성 시 Drive 경로 문제**: Drive 경로가 `jkapp/cat-record/{firestoreId}/attachment/`
   인데, 파일 선택 시점에는 아직 Firestore 저장이 이루어지지 않아 `firestoreId`를 알 수
   없다. 따라서 임시 UUID를 경로에 사용한 뒤 저장 후 이동하는 복잡한 로직이 필요했다.

## 결정

### 업로드 타이밍

파일 선택 시 Drive에 즉시 업로드하지 않는다.
선택된 파일은 `PendingLocalFile`로 메모리에만 보관하고, **저장 버튼 클릭 시** 업로드를 수행한다.

```
[파일 선택] → PendingLocalFile 보관 (Drive 업로드 없음)
[저장 클릭] → Drive 업로드 → Firestore 저장/업데이트
[취소]      → PendingLocalFile 해제 (Drive GC 불필요)
```

### 신규 생성 플로우

```
1. Firestore에 CatRecord 저장 (attachments 빈 상태) → firestoreId 획득
2. firestoreId를 경로로 사용하여 Drive에 파일들 업로드 → Attachment 목록 획득
3. Firestore CatRecord.attachments 업데이트
```

`FirestoreRepository.addRecord()` 반환 타입을 `Unit` → `String`(firestoreId)으로 변경한다.

### 수정 플로우

```
1. 신규 선택된 파일들을 Drive에 업로드
2. 기존 attachments + 새 Attachment 목록으로 Firestore 업데이트
3. 제거된 첨부파일을 Drive에서 GC (ADR 01 전략 유지)
```

### 취소 플로우

Drive 업로드가 발생하지 않았으므로 GC 불필요.
`PendingLocalFile` 목록만 초기화한다.

## 근거

- 저장 전까지 Drive에 아무것도 쓰지 않으면 취소 시 고아 파일이 구조적으로 생기지 않는다.
- Firestore 저장 후 `firestoreId`를 받아 Drive 경로를 결정하면 임시 UUID 경로가 필요 없다.
- 업로드 실패 시 Firestore 레코드도 없으므로 `attachments`가 빈 상태로 저장되지 않는다.
  (단, Firestore 저장 성공 후 Drive 업로드 전 앱 종료 시 attachments가 빈 레코드가 남을 수
  있으나, 이는 다음 수정 저장으로 복구 가능하다.)

## 검토한 대안

- **파일 선택 즉시 업로드 (기존 방식)**: 취소 시 GC 필요, 신규 생성 시 임시 경로 필요.
  GC가 실패하면 고아 파일 발생 가능성 존재.
- **저장 전 별도 임시 Drive 폴더 사용 후 이동**: Drive API에 파일 이동(rename) 지원은 되나,
  Firestore 저장과 Drive 이동 두 단계 트랜잭션이 필요하여 복잡도 증가.

## 예상 결과

저장 취소 시 Drive에 고아 파일이 남지 않는다.
신규 생성과 수정 모두 `firestoreId` 기반 일관된 Drive 경로를 사용한다.
