# asset(자산 관리) 기능

가계 자산을 날짜별로 기록·조회하고, 명의/숨김 필터로 살펴보며, 순자산을 자동 집계한다.
개별 입력·수정 외에 고정된 원본 구글시트에서 자산 행을 직접 읽어 미리보기 후 일괄
반영(가져오기)할 수 있다.

## 비즈니스 규칙

- **날짜 = 문서 ID**: 하나의 날짜가 하나의 자산 스냅샷 문서다. 같은 날짜에 저장하면 그
  문서를 통째로 덮어쓴다(set). 강제 위치 `AssetFirestoreRepositoryImpl.upsertDailyAsset`.
- **항목이 비면 문서 삭제**: 변경 결과 자산 목록이 비면 upsert 대신 날짜 문서 자체를
  삭제한다. 강제 위치 `DailyAssetViewModel.mutateAssets`.
- **변경 직렬화(lost-update 방지)**: 추가/수정/삭제/가져오기의 read-modify-write를
  `Mutex`로 직렬화해, 서로 다른 요청이 같은 stale 스냅샷을 읽고 덮어쓰는 것을 막는다.
  강제 위치 `DailyAssetViewModel.assetMutationMutex` + `mutateAssets`.
- **내용 기반 식별**: 수정/삭제는 리스트 index가 아니라 항목 내용 일치(`indexOf`/`in`)로
  대상을 찾는다. 다이얼로그가 열린 사이 목록 순서가 바뀌어도(다른 기기 동시 수정) 엉뚱한
  항목이 바뀌지 않는다. 대상이 없으면 `check`로 실패시킨다. 강제 위치
  `DailyAssetViewModel.updateAsset`/`deleteAsset`.
- **명의 목록 고정 + 확장**: 입력 폼·필터의 기본 명의는 `ASSET_OWNERS`(`전지훈`/`권유경`/
  `공동`)이며, 과거 데이터에 그 외 명의가 있으면 필터 옵션에 함께 노출한다. 강제 위치
  `DailyAssetViewModel.ownerFilterOptions`.
- **시트 직접 연동(읽기 전용)**: 수동 붙여넣기 대신 고정된 원본 구글시트(`JK-APP raw`)를
  앱이 직접 읽는다. `SPREADSHEETS_READONLY` scope만 사용하고, Drive와 동일한
  `GoogleAccountCredential` OAuth2 + 복구 인텐트 패턴을 따른다. 강제 위치
  `AssetSheetRepositoryImpl`. → ADR/73
- **시트 읽기 실패는 자산 표를 덮지 않음**: 시트 읽기 실패/타임아웃은 `_uiState`가 아니라
  `actionError`로만 안내한다. Firestore 스냅샷 리스너는 데이터가 실제 바뀔 때만 재발행되므로,
  일회성 읽기 실패로 `uiState`를 Error로 덮으면 자산 표가 사라진 채 고착되기 때문이다.
  강제 위치 `DailyAssetViewModel.importFromSheet`.
- **가져오기 병합 규칙**: 시트 가져오기는 `(이름, 명의)`가 같은 기존 항목은 필드 단위로
  갱신하고 없으면 추가한다. 이름이 같아도 명의가 다르면 별개 항목이다. 사용자가 직접
  관리하는 필드(`card`, `hidden`)는 기존 값을 유지해 재가져오기로 지워지지 않게 한다.
  강제 위치 `DailyAssetViewModel.importAssets`.
- **시트 계정 자동 설정**: 로그인된 구글 계정 이메일을 구독해 시트 저장소에 전달하므로,
  이미 동의한 사용자는 계정 선택 없이 바로 읽는다. 강제 위치 `DailyAssetViewModel.init`.

## 계산 / 파생 값

- `netWorth = 오늘 이하 최신 날짜 스냅샷의 hidden=false 항목 amount 합(null은 0)` — 데이터가
  없거나 전부 숨김이면 `null`. 미래 날짜 오입력은 `latestNotFuture`로 제외한다. 계산 위치
  `DailyAssetViewModel.netWorth`.
- `availableDates` — 자산 스냅샷들의 `date`를 내림차순 정렬한 목록. 날짜 선택/보정 기준.
- `currentDailyAsset` — `selectedDate`(없으면 최신 날짜)에 해당하는 스냅샷. `uiState`가
  Loading→Success로 바뀌는 프레임의 한 틱 null을 없애기 위해 date가 null일 때 동일 fallback을
  즉시 계산한다.
- `groupedAssets` — `currentDailyAsset`의 항목을 명의 필터(`selectedOwners`)와 숨김
  표시(`showHidden`)로 거른 뒤 명의별로 그룹핑한 `Map`(원본 index 보존). 파생 State는
  ViewModel StateFlow로 옮겨 데이터가 실제 바뀔 때만 재계산한다(탭 재진입 재계산 방지, 이슈 #37).

## 시트 파싱 규칙

`AssetSheetPaste.parseAssetRows` — 시트 열 순서는 고정: 이름/명의/계좌(institution)/
계좌번호/카드/금액(6열). 카드 열은 파싱에서 사용하지 않는다.

- **명의 코드 매핑**: `J`→`전지훈`, `K`→`권유경`. 그 외 값(빈 값, `-`, 오타 등)은 모두 `공동`.
- **금액 파싱**: 문자열에서 숫자(0-9)와 소수점만 추출해 재조합한다. 통화기호·콤마·공백·괄호는
  무시하며, 괄호(회계상 음수)도 음수로 해석하지 않는다. 숫자가 없고 글자가 섞이면 오류,
  기호만 있으면 "값 없음"(`null`).
- **빈 값 처리**: institution/accountNumber는 빈 문자열이나 `-`면 `null`.
- **hidden 자동 지정**: 이름이 `DEFAULT_HIDDEN_ASSET_NAMES`에 속하면 `hidden = true`로 파싱.
- **페이징**: 데이터 행을 2행부터 100행 단위로 끝까지 읽고, 한 페이지가 100행 미만이면
  마지막 페이지로 보고 멈춘다. 강제 위치 `AssetSheetPaging.collectPagedRows`.

## 유효성 검증

- 시트 파싱 시 `name`이 비면 "이름이 비어 있습니다" 오류 행으로 표시(저장 대상 제외).
- 금액을 숫자로 변환할 수 없으면 "금액을 숫자로 변환할 수 없습니다: {값}" 오류 행.
- 같은 입력 안에서 `(이름, 명의)`가 중복되면 어느 값이 맞는지 알 수 없으므로 자동 선택하지
  않고 "같은 입력 안에 이름과 명의가 중복되었습니다" 오류 행으로 표시.
- 읽기 시 `name`/`owner`가 없는 Firestore 항목은 경고 로그 후 건너뜀.
- 수정/삭제 대상 항목이 목록에 없으면 `check` 실패 → `actionError`로 안내.
- 시트 응답이 `SHEET_IMPORT_TIMEOUT_MS`(30초)를 넘으면 로딩 해제 후 시간 초과 안내.

## 상태 전이

시트 가져오기 흐름(`AssetSheetImportState`):

- `Idle → Loading` — 사용자가 가져오기 실행(`importFromSheet`).
- `Loading → Preview(rows)` — 시트 읽기·파싱 성공. 성공/오류 행이 섞인 미리보기.
- `Loading → Idle` — 타임아웃/오류/취소(`dismissSheetImport`). 오류는 `actionError` 또는
  동의 필요 시 `sheetAuthRecoveryIntent`로 별도 안내.
- `Preview → Idle` — 미리보기 확인 후 저장(`confirmSheetImport`) 또는 닫기.

## 주요 플로우

1. **자산 목록 조회**: 로그인 상태 → `init`의 `getDailyAssets` 구독 → `DailyAssetUiState.Success`
   방출 → `availableDates` collector가 유효한 `selectedDate` 보정 → `currentDailyAsset`/
   `groupedAssets`로 화면 렌더.
2. **항목 추가/수정/삭제**: 화면 → `addAsset`/`updateAsset`/`deleteAsset` → `mutateAssets`가
   Mutex 안에서 현재 스냅샷을 읽어 변형 → 결과가 비면 `deleteDailyAsset`, 아니면
   `upsertDailyAsset`.
3. **구글시트 가져오기**: `importFromSheet` → `AssetSheetImportState.Loading` →
   `sheetRepository.readAssetRows`(30초 타임아웃) → `parseAssetRows`로 파싱 →
   `Preview(rows)` 미리보기 → 사용자 확인 시 `confirmSheetImport(date, items)` →
   `importAssets`가 `(이름, 명의)` 기준 병합 upsert.
4. **시트 접근 동의 복구**: 계정 미선택/동의 필요 시 `AssetSheetAuthException` →
   `sheetAuthRecoveryIntent` 노출 → UI가 계정 선택/동의 화면 실행 → 돌아온 계정을
   `onSheetAccountSelected`로 반영.

## 관련 결정 (ADR)

- [`doc/adr/73/google-sheets-api-readonly.md`](../../../adr/73/google-sheets-api-readonly.md) — 시트 연동에 Google Sheets API v4 읽기 전용 scope 채택(Drive 인증 스택 재사용)
