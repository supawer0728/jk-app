# Google 로그인 시 계정 선택 후 모달만 닫히고 홈으로 넘어가지 않음

**상태**: 해결됨
**날짜**: 2026-07-12
**영역**: auth / build

## 증상

로그인 화면에서 "Google로 로그인"을 눌러 계정 선택 모달에서 계정을 고르면,
**모달만 닫히고 아무 일도 일어나지 않는다.** 홈으로 전환되지 않고, 화면에 에러 메시지도
표시되지 않는다. 여러 번 시도해도 동일하게 재현된다.

Logcat에는 다음이 반복해서 찍혔다(오해하기 쉬운 메시지).

```
CredManProvService  I  GetCredentialResponse error returned from framework
LoginFlow           W  취소됨(GetCredentialCancellation): [16] Account reauth failed.
```

## 환경

- 실기기(Android 15 / API 35), **debug 빌드**(`installDebug`).
- `~/.android/debug.keystore` 파일이 그날 새로 생성/교체된 상태였다(파일 수정 시각이 당일).

## 진단 과정

1. **Firestore 롤백 가설 → 기각.** 로그인 플로우 전 구간에 임시 로그를 심어 확인.
   `firebaseAuthWithGoogle`의 `signInWithCredential`(4단계)까지 **도달조차 못 했다.**
   즉 프로필 upsert/로그인 기록 실패로 인한 롤백이 아니었다.
2. **네비게이션 가설 → 기각.** `_user` 상태 갱신이 없었으니 `MainActivity`의 화면 분기
   `LaunchedEffect`가 동작하지 않은 것은 당연한 결과. 네비게이션 코드 문제 아님.
3. 실제로 끊긴 지점은 `credentialManager.getCredential(...)` **자체가 예외로 실패**하는
   것이었고, 그 예외가 `GetCredentialCancellationException`이라 앱이 "사용자 취소"로
   간주해 조용히 삼키고 있었다. 메시지 `[16]`은 Google Identity의 `CANCELED` 코드다.
4. `[16] Account reauth failed`가 **매번** 재현 → 일시적 계정 문제가 아니라 **구성 문제**로
   방향 전환. debug 키스토어 SHA-1과 `google-services.json`에 등록된 지문을 비교했다.

```bash
# 실제 앱을 서명한 debug 키스토어의 SHA-1
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
#   SHA1: F1:BE:52:0E:59:7B:30:D3:CA:AF:C7:29:C2:10:E2:19:04:A6:52:55

# google-services.json(com.jkapp)에 등록된 Android OAuth 클라이언트 지문
#   sha1 = b0860c3c028b8f86c68e9a3a2cdce82c88e498df  (= B0:86:0C:3C:...)
```

두 지문이 **불일치**했다.

## 근본 원인

앱을 서명한 **debug 키스토어의 SHA-1 지문이 Firebase/Google Cloud에 등록돼 있지 않았다.**
Google이 앱을 신뢰하지 못해 로그인을 거부했고, 그 거부가 Credential Manager에서
`[16] Account reauth failed`라는 오해하기 쉬운 메시지로 나타났다.

그날 `debug.keystore`가 새로 생성되며 SHA-1이 바뀌었고(`b0860c3c…` → `f1be520e…`),
바뀐 지문이 등록돼 있지 않아 그 시점부터 로그인이 깨졌다.
**증상(reauth 실패)과 원인(서명 지문 미등록)이 어긋나 있었던 것이 이 사례의 핵심.**

## 해결

새 debug 키스토어의 SHA-1을 Firebase에 등록하고 설정 파일을 갱신했다.

1. [Firebase Console](https://console.firebase.google.com) → **프로젝트 설정** → **내 앱**에서
   Android 앱 `com.jkapp` 선택
2. **디지털 지문 추가(Add fingerprint)** → 현재 debug SHA-1 등록
   (`F1:BE:52:0E:59:7B:30:D3:CA:AF:C7:29:C2:10:E2:19:04:A6:52:55`)
3. 갱신된 **`google-services.json` 다운로드** → `app/google-services.json` 교체
4. 앱 재빌드·재설치 후 로그인 정상 확인

> 대안: 이전 debug 키스토어(SHA `b0860c3c…`)를 백업해 뒀다면 `~/.android/debug.keystore`로
> 되돌려도 된다. PC 교체·Android Studio 재생성 등으로 키스토어가 바뀌면 재발한다.

## 재발 방지 / 다음에 빠르게 확인하는 법

- **로그인이 갑자기 안 되면 SHA-1부터 대조한다.** 특히 새 PC, `debug.keystore` 재생성,
  릴리스 키/Play 앱 서명 도입 직후가 의심 1순위다.

  ```bash
  keytool -list -v -keystore ~/.android/debug.keystore \
    -alias androiddebugkey -storepass android -keypass android | grep SHA1
  ```
  이 값이 `app/google-services.json`의 `oauth_client`(client_type=1, Android) 지문과
  일치하는지 확인한다. release 빌드는 릴리스 키스토어(또는 Play 앱 서명)의 SHA-1도 등록돼야 한다.
- `[16] Account reauth failed` / `CANCELED`를 **무조건 "사용자 취소"로만 해석하지 않는다.**
  서명 지문·OAuth 구성 문제일 수 있다.
- (관련 UX 개선 여지) 로그인 실패가 조용히 삼켜지면 원인 파악이 늦어진다.
  reauth·취소 외의 실패는 사용자에게 안내 메시지를 보여주는 편이 낫다.

## 관련

- 인증 인프라: `com.jkapp.auth`(`AuthViewModel`), 로그인 화면 `com.jkapp.common.LoginScreen`
- 보안/자격 증명 원칙: [`AGENT.md`](../../AGENT.md)의 "보안 / 자격 증명"
