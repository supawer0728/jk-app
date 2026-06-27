# Claude Code 하네스 설정 가이드

이 문서는 **이 프로젝트에서 Claude Code가 효과적으로 동작하기 위한** 권한(permissions) · 훅(hooks) · 환경변수(env) 설정을 정의한다.

---

## 하네스란

Claude Code 하네스 = `.claude/` 디렉토리의 설정 레이어. 세 가지 축으로 구성된다.

| 축 | 역할 |
|----|------|
| **permissions** | Claude가 사용자 승인 없이 실행할 수 있는 도구/명령 목록 |
| **hooks** | 특정 도구 실행 전후에 자동으로 실행되는 셸 명령 또는 안내 문구 |
| **env** | Claude 세션에 주입되는 환경 변수 |

### 설정 파일 구분

```
.claude/
├── settings.json        ← 팀 공유 (git 커밋 가능)
└── settings.local.json  ← 개인 로컬 오버라이드 (git 제외, .gitignore 필수)
```

두 파일의 `permissions.allow` 배열은 **병합**되어 적용된다. 충돌 시 `settings.local.json`이 우선한다.

---

## 권한(Permissions)

### 공유 설정 — `.claude/settings.json`

팀 전체(본인·배우자 기기 모두)에 동일하게 적용할 권한.

```json
{
  "permissions": {
    "allow": [
      "Bash(.\\gradlew.bat *)",
      "Bash(adb *)",
      "mcp__claude_ai_Google_Drive__read_file_content",
      "mcp__claude_ai_Google_Drive__get_file_metadata",
      "mcp__claude_ai_Google_Drive__list_recent_files",
      "mcp__claude_ai_Google_Drive__search_files"
    ]
  }
}
```

| 권한 | 이유 |
|------|------|
| `Bash(.\gradlew.bat *)` | Gradle 빌드·테스트·Lint 자동 실행 |
| `Bash(adb *)` | 기기 연결 확인, Logcat 필터 실행 |
| `mcp__claude_ai_Google_Drive__*` | Google Sheets 시트 구조 조회 (코드 생성 전 스키마 확인) |

### 개인 설정 — `.claude/settings.local.json`

기기별로 다를 수 있는 권한. git에 커밋하지 않는다.

```json
{
  "permissions": {
    "allow": [
      "mcp__claude_ai_Google_Drive__read_file_content",
      "mcp__claude_ai_Google_Drive__get_file_metadata",
      "Bash(Remove-Item *)"
    ]
  }
}
```

> **주의**: `settings.local.json`은 반드시 `.gitignore`에 추가한다.
> OAuth 토큰·클라이언트 ID 등 민감 정보가 향후 이 파일에 추가될 수 있다.

---

## 훅(Hooks)

훅은 도구 실행 전후에 안내 문구를 주입하거나 셸 명령을 실행한다.
Claude Code는 훅 출력을 컨텍스트로 읽어 행동을 조정한다.

### 권장 프로젝트 훅

현재 이 프로젝트에는 프로젝트 레벨 훅이 설정되어 있지 않다.
필요 시 `settings.json`의 `hooks` 키에 추가한다.

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "prompt",
            "prompt": "코드를 수정했습니다. Kotlin 컴파일 오류가 없는지 확인이 필요하면 ./gradlew.bat compileDebugKotlin 을 실행하세요."
          }
        ]
      }
    ]
  }
}
```

> **전역 OMC 훅**: oh-my-claudecode(OMC)에서 Edit·Read·Agent 도구에 대한 전역 훅이 이미 동작 중이다.
> 프로젝트 훅과 전역 훅은 중첩 적용된다.

---

## 환경 변수(env)

Android 개발에 필요한 환경 변수는 Android Studio 설치 시 대부분 자동으로 설정된다.

| 변수 | 기본값 (Android Studio) | 용도 |
|------|------------------------|------|
| `ANDROID_HOME` | `C:\Users\<사용자>\AppData\Local\Android\Sdk` | SDK 경로 |
| `JAVA_HOME` | Android Studio 번들 JDK | Gradle 실행 |

Claude 세션 내에서 현재 값을 확인하려면:
```powershell
$env:ANDROID_HOME
$env:JAVA_HOME
```

설정이 필요하다면 `settings.json`의 `env` 키에 추가:
```json
{
  "env": {
    "ANDROID_HOME": "C:\\Users\\supaw\\AppData\\Local\\Android\\Sdk"
  }
}
```

---

## 설정 편집 방법

### 직접 편집
```
.claude/settings.json        # 텍스트 에디터로 직접 수정
.claude/settings.local.json  # 텍스트 에디터로 직접 수정
```

### Claude Code /config 명령
Claude Code 프롬프트에서 `/config` 입력 → GUI로 편집 가능.

### 적용 시점
설정 변경은 **Claude Code 재시작 없이 즉시 적용**된다.

---

## 현재 설정 상태

```
.claude/
└── settings.local.json   ← 존재 (개인 설정)
    settings.json         ← 미생성 (공유 설정 필요)
```

**다음 단계**: 위의 "공유 설정" 예시를 `.claude/settings.json`으로 작성하면
Gradle·ADB 명령에 대한 매 작업마다의 사용자 승인 프롬프트가 사라진다.
