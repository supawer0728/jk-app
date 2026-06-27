# Google Drive API 클라이언트 선택

**상태**: 결정됨
**날짜**: 2026-06-28

## 맥락

Google Drive에 저장된 JSON 파일을 읽고 쓰는 API 클라이언트가 필요하다.
프로젝트에는 Retrofit과 Google Drive SDK(`google-api-services-drive`) 두 가지가 이미 의존성으로 포함되어 있다.

## 결정

Google Drive SDK(`com.google.apis:google-api-services-drive`)를 직접 사용한다.

## 근거

- Drive SDK가 이미 의존성에 포함되어 있어 추가 설정 불필요
- 파일 다운로드(`executeMediaAsInputStream`)·업로드(`InputStreamContent`) API가 타입 안전
- Retrofit을 쓰면 Drive multipart upload 직접 구현이 필요해 복잡도 증가

## 검토한 대안

- **Retrofit + OkHttp**: 이미 프로젝트에 있으나 Drive 파일 업/다운로드는 별도 구현 필요
- **Drive REST API + OkHttp 직접 호출**: 유연하지만 보일러플레이트 과다

## 예상 결과

`DriveRepositoryImpl`이 Drive SDK를 통해 파일 내용을 `InputStream`으로 읽고 JSON 문자열로 쓴다.
SDK 내부 HTTP 전송은 `AndroidHttp.newCompatibleTransport()`를 사용한다.
