# AGENT.md (CLAUDE.md)

이 파일은 Claude Code 및 Gemini 등의 AI 에이전트가 이 저장소에서 작업할 때 필요한 가이드를 제공합니다.

- **Claude Code**: 이 파일을 `CLAUDE.md`로 인식하여 프로젝트 컨텍스트로 사용합니다.
- **Gemini**: `GEMINI.md`와 함께 이 파일의 규칙을 따릅니다.

## 프로젝트 개요

가족(본인 + 배우자) 공용 생활 편의 앱. **Firebase Auth + Cloud Firestore**가 데이터 계층의 중심이다.
Google Drive는 별도 DB가 아니라, 다이어리 기록에 첨부되는 파일(사진 등)을 저장하는 용도로만 쓰인다.

### 기능 목록

| 기능 | 설명 |
|------|------|
| 자산 관리 (asset) | 가계 자산(예금, 카드, 계좌 등) 입력·조회·수정 |
| 투자 종목 (investment) | 명의별 투자 종목 입력·조회·평가금액/수익률 관리 |
| 벤치마크 (benchmark) | 투자자산 대비 KOSPI/S&P500/나스닥 수익률 비교 |
| 다이어리 (diary) | 반려동물 건강·생활 기록(사진 첨부 포함) 기록·조회, 기록 유형 관리 |
| 할일 (todo) | 오늘의 할일 관리. 상태 3단계·우선순위·담당자·반복(RecurrenceRule)·마감 리마인더(WorkManager) |
| 캘린더 (calendar) | 일정 관리 (자리표시자, 구현 예정) |
| 설정 (settings) | 다크모드, 햅틱 강도, 하단 탭 순서 등 앱 환경 설정 |
| 알림 (notification) | FCM 푸시 알림 수신·채널·토큰 관리 (공유 인프라) |
| 푸시 (push) | `pushes` 컬렉션 생성·30일 정리, 발송은 Cloud Functions (공유 인프라) |
| 사용자 (user) | 사용자 프로필·로그인 이력·푸시 토큰(이메일 기반 조회 포함) (공유 인프라) |

### 사용자 / 데이터 공유 구조

- 사용자: 본인과 배우자 2인.
- 각자의 기기에서 **Firebase Authentication**(Google 로그인)으로 인증한다.
- 인증된 사용자는 **Cloud Firestore**의 공유 컬렉션을 통해 동일한 데이터를 실시간으로 읽고 쓴다
  (도메인별 컬렉션은 아래 "Firestore 컬렉션 구조" 참고). 별도의 서버는 두지 않는다.
- 다이어리 기록에 첨부하는 파일(사진 등)은 Google Drive API로 업로드하고, Firestore 문서에
  Drive `fileId` 등 메타데이터만 저장한다.

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

## docs as code

소스코드의 도메인·비즈니스 로직은 [`doc/dev/`](doc/dev/README.md)에 문서로 관리한다.
코드와 문서는 하나의 변경 단위이며, 문서는 코드의 **현재 상태**를 반영해야 한다.
문서 체계·지도·템플릿 링크는 [`doc/dev/README.md`](doc/dev/README.md)를 참고한다.

**3원칙 (반드시 지킨다)**

1. **변경 전 문서 반영**: 소스코드를 바꾸기 전에 관련 문서(DOMAIN/FEATURE/infra)를 먼저 갱신한다.
2. **변경 전 모순 검증**: 소스코드를 바꾸기 전에 문서 간·문서와 기존 코드 간 모순이 없는지 검증한다.
3. **PR 전 정합성 검증**: PR을 올리기 전에 소스코드와 문서 내용이 일치하는지 검증한다
   ([PR 템플릿](.github/PULL_REQUEST_TEMPLATE.md)의 체크리스트로 강제).

**문서 종류** (템플릿 원본: `doc/template/`)

| 문서 | 담는 것 |
|------|---------|
| `doc/dev/<feature>/DOMAIN.md` | 도메인 모델의 속성·기능(메서드)·타 도메인 연관성·Firestore 컬렉션 |
| `doc/dev/<feature>/FEATURE.md` | 비즈니스 규칙·계산·상태 전이·유효성·주요 플로우 |
| `doc/dev/infra/<name>.md` | 공유 인프라(auth/drive/notification/user/common)의 책임·공개 API·의존 관계 |
| `doc/adr/<이슈>/<slug>.md` | 중요한 결정의 근거(왜). DOMAIN/FEATURE(무엇/어떻게)와 역할을 분리한다 |

**언제 무엇을 갱신하나**

| 변경 종류 | 갱신할 문서 |
|-----------|-------------|
| 도메인 모델 속성·Firestore 필드 | 해당 feature `DOMAIN.md` |
| 비즈니스 규칙·계산·상태 전이 | 해당 feature `FEATURE.md` |
| 공유 인프라 공개 API | `doc/dev/infra/<name>.md` |
| 아키텍처·데이터 모델·라이브러리·보안 결정 | `doc/adr/<이슈>/` 신규 ADR ([Phase 4 참고](.claude/skills/issue-dev/SKILL.md)) |

## 기술 스택 (확정)

- 언어/UI: **Kotlin + Jetpack Compose** (안드로이드 네이티브)
- 인증: **Firebase Authentication** (Google 로그인)
- 데이터 접근: **Cloud Firestore** — 도메인별 컬렉션을 실시간 리스너(`addSnapshotListener`)로
  구독하고, 읽기·쓰기·삭제를 모두 Firestore SDK로 직접 수행한다.
- 파일 저장: **Google Drive API v3 + OAuth** — 다이어리 첨부파일(사진 등) 업로드/다운로드 전용.
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

**feature-based 패키지 구조**를 쓴다. 레이어(ui/data)가 아니라 도메인(feature)이 최상위
경계이며, 각 feature 패키지가 자신의 UI(Compose)·ViewModel·Firestore Repository를 함께 갖는다.

```
com.jkapp
├── diary/          기록(사진 첨부 포함) — ui + DiaryFirestoreRepository
├── finance/
│   ├── asset/       자산 관리 — ui + AssetFirestoreRepository
│   ├── investment/  투자 종목 — ui + InvestmentFirestoreRepository
│   └── benchmark/   벤치마크 — ui + BenchmarkFirestoreRepository
├── settings/       설정 화면/ViewModel
├── todo/           오늘의 할일 — 화면/ViewModel/Repository + 반복·리마인더(WorkManager)
├── calendar/       캘린더 (자리표시자, 구현 예정)
├── common/         MainScreen/HomeTabScreen/TabOrder*, AppPreferences, theme 등 여러 feature가 공유하는 것
├── auth/           Firebase Auth — 공유 인프라, 특정 feature에 속하지 않음
├── drive/          Google Drive(첨부파일 저장) — 공유 인프라
├── notification/   FCM 푸시 알림(수신·채널·토큰) — 공유 인프라
├── push/           pushes 컬렉션 생성·30일 정리(대상 계산은 각 feature) — 공유 인프라
├── user/           사용자 프로필·로그인 이력·푸시 토큰(이메일 기반 조회) — 공유 인프라
├── haptic/         햅틱 피드백 컨트롤러
└── nav/            네비게이션 라우트 정의
```

각 feature는 god interface 없이 자신의 도메인만 다루는 `XxxFirestoreRepository` 인터페이스를
갖는다(예: `DiaryFirestoreRepository`, `AssetFirestoreRepository`). ViewModel은 기본 파라미터로
자신의 Repository 구현체를 생성한다(별도 DI 컨테이너 없음).

```
UI (Compose) → ViewModel → XxxFirestoreRepository (인터페이스)
                                ↓
                    XxxFirestoreRepositoryImpl (구현체)
                                ↓
                    Cloud Firestore (컬렉션 = 테이블, 실시간 리스너)
```

- **인증 흐름**: Firebase Auth(Google 로그인)로 사용자를 식별한다. 본인·배우자 각자의 기기에서
  각자 계정으로 로그인하면 되므로 앱 내 계정 전환 기능은 불필요하다.
- **실시간 동기화**: 각 Repository는 `addSnapshotListener`로 컬렉션을 구독하는 `Flow`를 노출한다.
  로컬 캐시나 수동 새로고침 없이 Firestore가 변경을 실시간으로 밀어준다.
- **첨부파일**: 다이어리 기록의 사진 등은 Google Drive에 업로드하고, Firestore 문서에는
  `Attachment`(fileId/name/mimeType/size) 메타데이터만 저장한다.
- **새 feature 추가 시**: `com.jkapp.<feature>/` 패키지를 새로 만들고 그 안에 화면/ViewModel/
  Repository를 함께 둔다. 여러 feature가 공유하는 것만 `common/`에 둔다.

## Firestore 컬렉션 구조

각 feature의 `XxxFirestoreRepositoryImpl`이 다루는 컬렉션이다. 필드명은 각 Impl의
`companion object` 상수와 `data class`(도메인 모델) 정의가 원본이므로, 스키마를 바꿀 때는
그 두 곳만 함께 수정하면 된다.

| 컬렉션 | 담당 | 도메인 모델 |
|--------|------|-------------|
| `cat-record-types` | `diary.DiaryFirestoreRepository` | `CatRecordType` |
| `cat-records` | `diary.DiaryFirestoreRepository` | `CatRecord` (attachments: `drive.Attachment` 목록) |
| `daily-assets` | `finance.asset.AssetFirestoreRepository` | `DailyAsset` (assets: `AssetItem` 목록) |
| `daily-asset-investments` | `finance.investment.InvestmentFirestoreRepository` | `DailyAssetInvestment` (investments: `InvestmentItem` 목록) |
| `benchmarks` | `finance.benchmark.BenchmarkFirestoreRepository` | `Benchmark` |
| `todo-items` | `todo.TodoFirestoreRepository` | `TodoItem` (recurrence: `RecurrenceRule`, 날짜 필드는 Firestore `Timestamp`) |
| `todo-categories` | `todo.TodoFirestoreRepository` | `TodoCategory` |
| `tab-orders` | `common.TabOrderRepository` | 사용자별 하단 탭 순서(`List<String>`) |
| `pushes` | `push.PushRepository` | `PushMessage` (title/body/channelId/tokens, Functions가 status/sentAt/results 기록) |

## graphify

이 프로젝트는 `graphify-out/`에 god nodes, community structure, 파일 간 관계를 담은 지식 그래프를 갖고 있다.

규칙:
- 코드베이스 관련 질문에는 `graphify-out/graph.json`이 존재할 경우 먼저 `graphify query "<question>"`을 실행한다. 관계 파악에는 `graphify path "<A>" "<B>"`, 특정 개념 파악에는 `graphify explain "<concept>"`을 사용한다. 이들은 GRAPH_REPORT.md나 원시 grep 결과보다 훨씬 작은 범위의 서브그래프를 반환한다.
- `graphify-out/wiki/index.md`가 존재하면 원시 소스 탐색 대신 이를 활용해 전체 구조를 파악한다.
- `graphify-out/GRAPH_REPORT.md`는 폭넓은 아키텍처 리뷰가 필요하거나 query/path/explain으로 충분한 컨텍스트를 얻지 못할 때만 읽는다.
- 코드 수정 후에는 `graphify update .`을 실행해 그래프를 최신 상태로 유지한다 (AST 기반, API 비용 없음).

## 보안 / 자격 증명

- `google-services.json`, OAuth 클라이언트 ID, 토큰 등은 커밋하지 않는다 (`.gitignore` 필수).
- Google Cloud Console에서 OAuth 동의 화면 설정 + Android 앱용 OAuth 2.0 클라이언트 ID를
  발급해야 한다. 테스트 사용자에 본인·배우자 계정(2개)을 추가하면 충분하다.
- 공유 드라이브 폴더 ID는 앱 내에 하드코딩하거나 별도 설정 파일로 관리한다 (커밋 가능한 상수).
  폴더 자체의 접근 제어는 구글 드라이브 공유 설정으로 관리한다.
