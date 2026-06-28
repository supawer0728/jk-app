# 첨부파일 업로드·삭제 병렬 처리

**상태**: 결정됨  
**날짜**: 2026-06-28

## 맥락

N개의 첨부파일을 업로드하거나 삭제할 때 순차 처리하면 파일 수에 비례해 소요 시간이 증가한다.
각 Drive API 호출은 독립적인 I/O 바운드 작업이므로 병렬 처리가 적합하다.

또한 Google Drive API에 벌크(batch) 삭제 엔드포인트가 존재하는지 검토가 필요했다.

## 결정

### 업로드: coroutineScope + async/awaitAll

여러 파일을 동시에 업로드하고 전체 결과가 모일 때까지 대기한다.

```kotlin
val results: List<FileUploadResult> = coroutineScope {
    localFiles.map { file ->
        async { /* driveRepository.uploadFile(...) */ }
    }.awaitAll()
}
```

- 인증 오류(`DriveAuthRequiredException`)가 발생한 파일은 `pendingRetries`에 보관하고
  OAuth 재동의 후 재시도한다.
- 일부 파일 업로드 실패가 전체 저장을 막지 않도록 결과를 분류한다.

### 삭제(GC): fire-and-forget 병렬 코루틴

GC는 best-effort이므로 완료를 기다리지 않는다.
파일마다 별도 `viewModelScope.launch`를 실행하여 병렬로 삭제한다.

```kotlin
private fun deleteFilesFromDrive(fileIds: List<String>, label: String) {
    fileIds.forEach { fileId ->
        viewModelScope.launch {
            runCatching { driveRepository.deleteFile(fileId) }
                .onFailure { e -> Log.w(TAG, "Drive GC 실패 ($label): fileId=$fileId", e) }
        }
    }
}
```

삭제 완료를 기다리지 않으므로 저장 완료 처리(`_saveCompleted`)가 GC에 의해 지연되지 않는다.

### 다중 파일 선택

파일 선택기를 `ActivityResultContracts.GetContent`(단일)에서
`ActivityResultContracts.GetMultipleContents`(다중)으로 변경한다.

## 근거

### Google Drive API 벌크 삭제 여부

Google Drive API v3는 개별 DELETE 요청만 제공한다(`files.delete`).
Batch API(`/batch/drive/v3`)를 사용하면 여러 요청을 하나의 HTTP multipart 요청으로 묶을 수
있으나:

- Android Drive SDK의 `BatchRequest`는 콜백 기반으로 Kotlin coroutine과 통합하려면
  `suspendCancellableCoroutine` 래퍼가 추가로 필요하다.
- Batch API의 네트워크 절감 효과는 요청 수가 수십 개 이상일 때 유의미하며,
  일반적인 일기 첨부 수(1~5개)에서는 코루틴 병렬 방식과 실질적 차이가 없다.
- 코루틴 병렬 방식이 현재 코드 구조와 일관성을 유지한다.

따라서 Batch API 대신 코루틴 병렬 처리를 선택한다.

## 검토한 대안

- **순차 처리 유지**: 파일 N개당 총 소요 시간 = 개별 업로드 시간의 합. 파일이 많을수록 사용자 대기 시간 증가.
- **Google Drive Batch API**: 코루틴 통합 복잡도 증가 대비 실제 이점이 미미.
- **WorkManager 백그라운드 업로드**: 앱 종료 후에도 업로드를 보장하나, 저장 완료 시점을 UI에서
  추적하기 어렵고 현재 요구사항(저장 버튼 클릭 즉시 완료 처리)과 맞지 않음.

## 예상 결과

N개 파일 업로드 시 벽시계 시간(wall-clock time)이 단일 파일 업로드 시간에 수렴한다.
GC 삭제가 저장 완료 처리를 지연시키지 않는다.
