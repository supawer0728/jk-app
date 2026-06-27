# GEMINI.md

이 파일은 Gemini (Android Studio 내장 또는 API)가 이 저장소에서 작업할 때 필요한 가이드를 제공합니다.
기존 `AGENT.md` (CLAUDE.md)의 규칙을 계승하며, Gemini의 특성에 맞춘 추가 설정을 포함합니다.

## 프로젝트 가이드라인
모든 아키텍처, 코드 스타일, 기술 스택 결정은 **[AGENT.md](./AGENT.md)**를 최우선으로 따릅니다.

## 가용 기술 (Skills)
Claude Code에서 사용하던 `.claude/skills` 폴더의 스킬들을 Gemini에서도 다음과 같이 수행합니다.

### 1. 이슈 기반 개발 (issue-dev)
사용자가 "이슈 작업", "issue-dev", 또는 이슈 번호를 제공하면 `[.claude/skills/issue-dev/SKILL.md](.claude/skills/issue-dev/SKILL.md)`에 정의된 워크플로우를 실행합니다.

**워크플로우 요약:**
1. 이슈 조회 (`gh issue view`)
2. 브랜치 생성 (`feature/이슈번호-슬러그`)
3. 요구사항 분석 및 Task 생성
4. 구현 및 테스트 (`.\gradlew.bat test`, `lint`)
5. 코드 리뷰 및 Draft PR 생성 (`gh pr create --draft`)

## 도구 활용 (Tools)
이 환경에서 제공되는 도구들을 적극 활용하십시오:
- **빌드/테스트**: `gradle_build`, `run_shell_command` (.\gradlew.bat)
- **디바이스 제어**: `adb_shell_input`, `take_screenshot`, `ui_state`
- **파일 수정**: `replace_file_content`, `multi_replace_file_content`, `write_file`
- **탐색**: `find_usages`, `find_declaration`, `code_search`

## 응답 원칙
- **도움이 되되 간결하게**: 핵심 위주로 설명합니다.
- **안드로이드 전문가**: 담당 개발자가 BE 개발자이므로, 안드로이드 특화 결정(Compose, Lifecycle 등)은 AI가 주도합니다.
- **컨벤션 준수**: `doc/CODE_STYLE.md` 및 `doc/CODE_CONVENTION.md`를 엄격히 준수합니다.
