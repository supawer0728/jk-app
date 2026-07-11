# diary(육묘일기) 기능

반려묘 기록을 날짜별로 남기고, 사진 등 파일을 첨부하며, 기록유형으로 분류·필터링한다.
기록유형은 사용자가 직접 추가·수정·삭제할 수 있다.

## 비즈니스 규칙

- **날짜별 묶음 표시**: 같은 날짜의 여러 기록을 하나로 묶어 날짜 내림차순으로 보여준다.
  강제 위치 `DiaryViewModel.groupedByDateDescending`, `recordsByMonth`. → ADR/10
- **시스템 필수 기록유형**: `DAILY_NOTE`, `HOSPITAL_VISIT`은 시스템 유형(`SYSTEM_TYPE_IDS`)으로
  사용자가 추가·삭제할 수 없다. 강제 위치 `DiaryViewModel.validateNewRecordType`/`validateDeleteRecordType`.
- **유형 삭제 시 재배정**: 기록유형을 삭제하면 그 유형을 쓰던 기록을 fallback 유형
  (`FALLBACK_RECORD_TYPE_ID = "DAILY_NOTE"`)으로 일괄 재배정한 뒤 유형 문서를 삭제한다.
  Firestore batch로 원자적으로 처리한다. 강제 위치 `deleteRecordTypeAndReassignRecords`.
- **첨부파일 저장 타이밍**: 파일 선택 시 즉시 업로드하지 않고 로컬(`pendingLocalFiles`)에만
  보관하며, 저장 시점에 `firestoreId` 기반 경로로 Drive에 업로드한다. → ADR/18/01·02·03
- **첨부파일 GC(best-effort)**: 기록 삭제 시 첨부 전체를, 수정 시 제거된 첨부만 Drive에서
  삭제한다. 삭제 실패는 로그만 남기고 UX를 막지 않는다. 강제 위치 `deleteFilesFromDrive`.
- **유형 필터**: 선택한 기록유형으로 목록을 필터링한다. 비교 시 `trim().lowercase()`로 정규화한다.
  강제 위치 `DiaryViewModel.filterRecords`.

## 계산 / 파생 값

- `availableMonths` — 기록들의 `date`에서 `YearMonth`를 추출·중복제거·내림차순 정렬한 목록.
  월 이동(이전/다음) 가능 여부(`canMovePrevious`/`canMoveNext`)의 기준. 계산 위치 `startDataCollection`.
- `recordsByMonth` — 유형 필터 + 월별 그룹 + 날짜별 묶음을 미리 계산해 캐시한다(탭 재진입 시
  재계산 방지, 이슈 #37). 계산 위치 `DiaryViewModel.recordsByMonth`.

## 유효성 검증

- 신규 기록유형 ID가 `SYSTEM_TYPE_IDS`에 속하면 거부 — "시스템 필수 유형 ID는 사용할 수 없습니다."
- 신규 기록유형 ID가 기존 ID와 중복이면 거부 — "이미 존재하는 기록유형 ID입니다: {id}"
- 시스템 유형 삭제 시도 시 거부 — "시스템 필수 유형은 삭제할 수 없습니다."
- 수정/삭제 대상 기록의 `firestoreId`가 없으면 `IllegalArgumentException`.

## 주요 플로우

1. **기록 목록 조회**: 로그인 상태 → `startDataCollection`이 `getRecordTypes`+`getRecords`를
   `combine`하여 `DiaryUiState.Success` 방출 → 월 자동 선택(`reconcileSelectedMonth`).
2. **신규 기록 저장**: `DiaryFormScreen` → `DiaryViewModel.addRecord` →
   ① 첨부 없이 `repository.addRecord`로 firestoreId 획득 → ② `firestoreId`로 Drive 업로드 →
   ③ 업로드된 첨부로 `updateRecord`. Drive 인증 필요 시 재시도 컨텍스트 보관 후 인증 복구.
3. **기록 수정**: `updateRecord(original, updated)` → 새 파일 업로드 → Firestore 업데이트 →
   제거된 첨부 Drive GC.
4. **첨부 다운로드**: `downloadAttachment` → Drive에서 스트림 다운로드. 인증 필요 시 복구 Intent 발행.

## 관련 결정 (ADR)

- [`doc/adr/10/group-cat-records-by-date.md`](../../adr/10/group-cat-records-by-date.md) — 날짜별 묶음 표시
- [`doc/adr/18/01-attachment-data-model.md`](../../adr/18/01-attachment-data-model.md) — 첨부파일 데이터 모델
- [`doc/adr/18/02-upload-timing-and-create-flow.md`](../../adr/18/02-upload-timing-and-create-flow.md) — 업로드 타이밍·생성 플로우
- [`doc/adr/18/03-parallel-upload-and-delete.md`](../../adr/18/03-parallel-upload-and-delete.md) — 병렬 업로드·삭제
- [`doc/adr/7/02-drive-auth-strategy.md`](../../adr/7/02-drive-auth-strategy.md) — Drive 인증 전략
- [`doc/adr/7/03-viewmodel-scoping.md`](../../adr/7/03-viewmodel-scoping.md) — ViewModel 스코핑
