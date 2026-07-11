# 벤치마크 시트 연동에 Google Sheets API v4(읽기 전용)를 사용

**상태**: 결정됨
**날짜**: 2026-07-11

## 맥락

벤치마크 데이터 입력을 사용자의 수동 복사/붙여넣기에서, 고정된 원본 구글시트
(`JK-APP raw`)를 앱이 직접 읽어오는 방식으로 바꾼다(#73). 이를 위해 외부 라이브러리
추가와 OAuth scope 확장이라는 두 가지 중요 의사결정이 필요하다.

이미 다이어리 첨부파일용으로 Google Drive API(`google-api-services-drive` +
`google-api-client-android`, `GoogleAccountCredential.usingOAuth2`)를 사용 중이라,
동일한 인증/HTTP 스택을 재사용할 수 있다.

## 결정

- 라이브러리: `com.google.apis:google-api-services-sheets`(v4)를 추가한다.
  기존 Drive 클라이언트와 같은 `2.0.0` 계열 리비전을 사용해 `google-api-client-android`
  스택을 공유한다.
- OAuth scope: `SheetsScopes.SPREADSHEETS_READONLY`만 요청한다(읽기 전용).
- 인증: 기존 Drive와 동일하게 `GoogleAccountCredential.usingOAuth2` +
  `UserRecoverableAuthIOException` → 복구 인텐트 패턴을 그대로 따른다.

## 근거

- Drive에서 검증된 인증/HTTP 스택을 재사용해 새로운 인증 인프라를 도입하지 않는다.
- 앱은 시트에서 데이터를 읽기만 하고 쓰지 않으므로, 최소 권한 원칙에 따라 읽기 전용
  scope만 요청한다(시트 수정 위험 제거).
- 시트 ID가 고정이라 파일 탐색/쓰기 권한이 필요 없다.

## 검토한 대안

- **Sheets `values.get` REST를 Retrofit으로 직접 호출**: OAuth 토큰 획득 로직을 직접
  구현해야 해 Drive와 인증 방식이 이원화된다. 유지보수 비용이 크다.
- **공개 링크 + API Key**: 시트를 전체 공개해야 하므로 보안상 부적합.
- **`SPREADSHEETS`(읽기/쓰기) scope**: 불필요한 쓰기 권한을 요구해 최소 권한 원칙 위배.

## 예상 결과

- 사용자는 로그인된 구글 계정으로 시트 접근 동의(1회)만 하면 되고, 이후 버튼 한 번으로
  벤치마크를 동기화한다.
- Drive/Sheets가 같은 `GoogleAccountCredential`/`google-api-client-android`를 공유해
  의존성 트리가 단순하게 유지된다.
- 읽기 전용이라 앱이 원본 시트를 훼손할 가능성이 없다.