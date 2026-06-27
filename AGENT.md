# AGENT.md (CLAUDE.md)

이 파일은 Claude Code 및 Gemini 등의 AI 에이전트가 이 저장소에서 작업할 때 필요한 가이드를 제공합니다.

- **Claude Code**: 이 파일을 `CLAUDE.md`로 인식하여 프로젝트 컨텍스트로 사용합니다.
- **Gemini**: `GEMINI.md`와 함께 이 파일의 규칙을 따릅니다.

## 프로젝트 개요

가족(본인 + 배우자) 공용 생활 편의 앱. **구글 드라이브를 데이터베이스로 사용**한다 —
별도의 서버/DB 없이 공유된 구글 드라이브 폴더의 JSON 파일이 영구 저장소다.

### 기능 목록

| 기능 | 설명 |
|------|------|
| 자산 관리 | 가계 자산(예금, 투자, 부채 등) 입력·조회·수정 |
| 육묘 기록 | 씨앗 파종부터 정식까지의 모종 성장 일지 기록·조회 |

### 사용자 / 데이터 공유 구조

- 사용자: 본인과 배우자 2인.
- 각자의 기기에서 각자의 구글 계정으로 로그인해 **동일한 구글 드라이브 공유 폴더**에 접근한다.
  멀티유저 서버 계정 시스템은 없고, "구글 계정 = ㅎ용자" 구조다.
- 공유 폴더 내 도메인별 JSON 파일을 앱이 읽고 쓴다. 두 사람 모두 해당 폴더의 편집 권한을 갖는다.
- 앱은 적절한 주기(실행 시 fetch + 저장 시 upload)로 JSON을 동기화한다.

> 상태: 그린필드. 소스 코드가 생기면 이 문서를 갱신한다.

## 개발자 컨텍스트

- **담당 개발자**: Spring/Java·Kotlin BE 개발자. 안드로이드 경험 없음.
- **원칙**: 안드로이드 관련 모든 판단(프로젝트 구조, Gradle 설정, 라이프사이클, Compose 패턴,
  AndroidManifest, ProGuard 등)은 Claude Code에 위임한다. 개발자가 직접 결정하지 않는다.
- BE 관점에서의 도메인·비즈니스 로직(자산 계산, 데이터 구조 설계)은 개발자가 주도할 수 있다.

## 코드 스타일

AI가 코드를 작성할 때는 아래 두 문서를 따른다.

| 문서 | 범위 |
|------|------|
| [`doc/CODE_STYLE.md`](doc/CODE_STYLE.md) | 포맷·명명 규칙 (Android 공식 Kotlin 스타일 가이드 기반) |
| [`doc/CODE_CONVENTION.md`](doc/CODE_CONVENTION.md) | 관용 패턴·설계 관행 (Kotlin 공식 코딩 컨벤션 기반) |

**핵심 요약**

- 들여쓰기: 4 스페이스 / 줄 길이: 100자 / 중괄호: K&R 스타일
- 명명: 클래스 PascalCase / 함수·변수 camelCase / 상수 UPPER_SNAKE_CASE
- `@Composable` (Unit 반환): PascalCase 명사
- 약어는 일반 단어처럼 처리 (`XmlHttpRequest`, `newCustomerId`)
- `val` 우선, `var`·`!!` 최소화
- 상태/결과: `sealed interface` + exhaustive `when`
- 컬렉션 변환: 함수형 체인 (`filter`, `map`, `sumOf` …)
- 코루틴: `GlobalScope` 금지, `viewModelScope` 사용, 오류 처리 필수

## 기술 스택 (확정)

- 언어/UI: **Kotlin + Jetpack Compose** (안드로이드 네이티브)
- 데이터 접근: **Google Drive API v3 + OAuth** — 공유 폴더 내 JSON 파일을 직접 읽고/쓴다.
  읽기 전용이 아니며, 앱에서 데이터 생성·수정·삭제까지 모두 수행한다.
- 빌드: Gradle (Kotlin DSL `build.gradle.kts`)

## IDE: Android Studio

Android Studio를 사용한다. Kotlin, Gradle, Compose, 에뮬레이터, 브레이크포인트 디버깅이 모두 기본 내장되어 있다.

### 빌드 / 테스트

Android Studio 툴바의 ▶ 버튼으로 빌드 및 기기/에뮬레이터 실행이 가능하다.
터미널에서 직접 실행할 경우, 먼저 JBR(JetBrains Runtime 21)을 지정해야 한다.
시스템 기본 Java가 21이 아닌 경우 Gradle 래퍼가 버전 파싱 오류로 실패한다.

```powershell
$env:JAVA_HOME = "E:\Android\Android Studio\jbr"  # Android Studio 재설치 시 경로 갱신
.\gradlew.bat assembleDebug        # 디버그 APK 빌드
.\gradlew.bat installDebug         # 연결된 기기/에뮬레이터에 설치 및 실행
.\gradlew.bat test                 # JVM 단위 테스트 (app/src/test)
.\gradlew.bat connectedAndroidTest # 계측 테스트 (실기기/에뮬레이터 연결 필요)
.\gradlew.bat lint                 # Android Lint
```

단일 테스트 실행:
```powershell
.\gradlew.bat test --tests "com.jkapp.패키지.클래스명.메서드명"
```

### 디버깅

- **브레이크포인트 디버깅**: Android Studio에서 직접 지원. 라인 클릭 후 🐞 버튼으로 디버그 모드 실행.
- **Logcat**: Android Studio 하단 `Logcat` 탭에서 실시간 확인. 태그 필터로 `jkapp` 입력.
- **adb** (터미널에서 직접 확인할 경우):

```powershell
adb devices                        # 기기 연결 확인
adb logcat -s "jkapp"      # 태그 필터 로그
adb logcat *:E                     # 오류 로그만
```

앱 내에서 `android.util.Log.d("jkapp", "메시지")` 로 태그를 통일하면 필터링이 쉽다.

## Claude Code 하네스

이 프로젝트의 하네스 설정(권한·훅·환경변수)은 [`doc/HARNESS.md`](doc/HARNESS.md)를 참고한다.

**빠른 요약**:
- 공유 설정: `.claude/settings.json` (git 커밋 가능)
- 개인 설정: `.claude/settings.local.json` (git 제외)
- 필수 권한: `Bash(.\gradlew.bat *)`, `Bash(adb *)`, Google Drive MCP 도구

## 아키텍처 핵심

데이터 계층이 이 앱의 본질이다. 일반 앱이 Room/REST를 쓰는 자리에 **Google Drive JSON 파일이 들어간다.**
BE의 Repository 패턴과 동일한 개념을 적용한다.

```
UI (Compose) → ViewModel → Repository (인터페이스)
                                ↓
                    DriveDataSource (구현체)
                                ↓
                    Google Drive API v3
                                ↓
                    공유 드라이브 폴더 (파일 = 테이블)
                    ├── assets.json        (자산 관리)
                    └── seedlings.json     (육묘 기록)
```

- **OAuth 흐름**: 앱 첫 실행 시 구글 계정 선택 → 사용자 동의 → 토큰 발급. 본인·배우자 각자의
  기기에서 각자 계정으로 수행하면 되므로 앱 내 계정 전환 기능은 불필요.
- **필수 OAuth 스코프**: `https://www.googleapis.com/auth/drive.file`
  (앱이 생성한 파일만 읽기+쓰기. 전체 드라이브 접근 불필요.)
- **JSON ↔ 도메인 모델 변환**: JSON을 앱 도메인 객체로 역직렬화하는 로직을 `DriveDataSource`
  한 곳에 집중. JSON 스키마가 바뀌면 이 파일과 스키마 정의만 수정하면 된다.
- **동기화 전략**: 앱 실행 시 최신 JSON을 fetch → 로컬 캐시(메모리)에 보관 → 저장 시 전체 JSON을
  upload(덮어쓰기). 두 사람이 동시에 편집할 가능성이 낮으므로 낙관적 동시성으로 충분하다.
  충돌 감지가 필요하다면 Drive의 `modifiedTime`을 활용한다.

## JSON 스키마 (설계 필요)

앱의 데이터 모델은 각 JSON 파일의 스키마에 직접 묶인다. **코드 작성 전에 각 도메인의 필드명,
타입, 필수 여부를 확정하고 여기에 기록할 것.** 필드 이름을 추측해서 하드코딩하지 않는다.

### assets.json — 자산 관리

기존 구글 시트의 자산 데이터를 참고해 스키마를 설계한다. 확정 전까지 코드 작성 보류.

### seedlings.json — 육묘 기록

기존 구글 시트의 육묘 기록 데이터를 참고해 스키마를 설계한다. 확정 전까지 코드 작성 보류.

## 보안 / 자격 증명

- `google-services.json`, OAuth 클라이언트 ID, 토큰 등은 커밋하지 않는다 (`.gitignore` 필수).
- Google Cloud Console에서 OAuth 동의 화면 설정 + Android 앱용 OAuth 2.0 클라이언트 ID를
  발급해야 한다. 테스트 사용자에 본인·배우자 계정(2개)을 추가하면 충분하다.
- 공유 드라이브 폴더 ID는 앱 내에 하드코딩하거나 별도 설정 파일로 관리한다 (커밋 가능한 상수).
  폴더 자체의 접근 제어는 구글 드라이브 공유 설정으로 관리한다.
