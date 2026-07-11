---
name: issue-dev
description: This skill should be used when the user says "issue-dev", "이슈 작업", "이슈 번호로 개발", "이슈 기반 개발", or provides a GitHub issue number to start working on. Runs the full cycle: fetch issue → create worktree → implement → test → review → draft PR.
version: 2.0.0
---

# Issue-Driven Development

GitHub 이슈 번호를 받아 **격리된 워크트리 생성**부터 Draft PR 작성까지 전체 개발 사이클을 수행한다.

각 이슈 작업을 독립된 git 워크트리에서 진행하므로, **여러 세션에서 서로 다른 이슈를
동시에 작업**해도 작업 트리가 충돌하지 않는다.

## 입력

`$ARGUMENTS` — GitHub 이슈 번호 (예: `/issue-dev 42`)

---

## Phase 1: 이슈 조회 및 워크트리 생성

1. 이슈 내용과 코멘트를 조회한다.
   ```bash
   gh issue view $ISSUE_NUMBER --json number,title,body,labels,assignees,comments
   ```

2. 이슈 제목에서 영문 slug를 만든다 (소문자·하이픈, 최대 4단어).
   - 예) "Add Google Sheets OAuth flow" → `add-sheets-oauth-flow`

3. **`EnterWorktree` 도구**로 격리된 워크트리를 생성하고 세션을 그 안으로 전환한다.
   - `name` 인자로 브랜치명을 그대로 넘긴다: `feature/$ISSUE_NUMBER-$SLUG`
   - 이 도구는 `.claude/worktrees/` 아래에 새 워크트리를 만들고, 동명의 브랜치를
     생성한 뒤(기본 base ref = `origin/main`), 현재 세션의 작업 디렉터리를 그곳으로 옮긴다.
   - 이렇게 하면 이 세션은 격리된 작업 트리에서 동작하므로, 다른 세션이 메인 디렉터리나
     다른 워크트리에서 별도 이슈를 동시에 작업해도 서로 간섭하지 않는다.
   - `git checkout -b`나 `git worktree add`를 직접 호출하지 말고 반드시 `EnterWorktree`를 쓴다.
     (이 스킬 지침이 워크트리 작업을 명시적으로 지시하므로 `EnterWorktree` 사용 조건을 충족한다.)

4. 이슈 내용 요약을 사용자에게 보여주고, 이해한 작업 범위를 한 문단으로 확인받는다.

---

## Phase 2: 요구사항 분석

1. 이슈 본문과 **코멘트**에서 수용 기준(Acceptance Criteria), 작업 계획, 할 일, 제약 조건을 추출한다. 코멘트에 구현 가이드나 변경 파일 목록이 있으면 우선적으로 반영한다.
2. 불명확한 부분은 **AskUserQuestion**으로 질문한다. 답변을 받은 후 다음 단계로 진행한다.
3. 확정된 할 일 목록을 **TaskCreate**로 등록한다.

---

## Phase 3: 구현 루프

아래 4단계를 문제가 없을 때까지 반복한다.

### 3-1. 코드 수정

- 변경 전 관련 코드 패턴을 반드시 먼저 파악한다 (Explore 에이전트 활용).
- `AGENT.md`(= `CLAUDE.md`)의 코드 스타일·컨벤션을 준수한다.
- 변경 범위는 이슈 범위로 한정한다. 범위 외 리팩터링은 사용자에게 별도 확인 후 진행한다.

### 3-2. 테스트 코드 작성

- 변경된 비즈니스 로직에 대한 단위 테스트를 작성한다 (`app/src/test`).
- 기기 연결이 필요한 경우에만 계측 테스트(`app/src/androidTest`)를 사용한다.

### 3-3. 테스트 검증

```powershell
.\gradlew.bat test          # JVM 단위 테스트
.\gradlew.bat lint          # Android Lint
```

- 실패 시 원인을 분석하고 **3-1**로 돌아간다.
- 연속 2회 이상 같은 오류가 반복되면 사용자에게 보고하고 방향을 확인한다.

### 3-4. 코드 리뷰

- `oh-my-claudecode:code-reviewer` 에이전트로 리뷰를 수행한다.
- **High/Critical** 지적: 수정 후 **3-1**로 복귀.
- **Low/Medium** 지적: 사용자에게 보고 후 결정에 따라 처리.
- 리뷰 통과 시 루프를 종료하고 Phase 4로 진행한다.

---

## Phase 4: 의사결정 처리

### 일반 의사결정

구현 중 판단이 필요한 경우 **AskUserQuestion**으로 질문한다.  
단, AGENT.md에 "Claude에 위임" 명시된 Android 관련 판단(프로젝트 구조, Gradle, Compose 패턴 등)은 자율 결정한다.

### 중요 의사결정 (아키텍처 · 데이터 모델 · 외부 라이브러리 · 보안)

1. ADR 파일을 작성한다: `doc/adr/$ISSUE_NUMBER/{kebab-case-title}.md`

   ```markdown
   # {제목}

   **상태**: 결정됨
   **날짜**: {YYYY-MM-DD}

   ## 맥락
   {결정이 필요했던 상황과 배경}

   ## 결정
   {선택한 방향}

   ## 근거
   {선택 이유}

   ## 검토한 대안
   {선택하지 않은 방안과 이유}

   ## 예상 결과
   {이 결정으로 인한 영향}
   ```

2. GitHub 이슈에 해당 내용을 코멘트로 남긴다.
   ```bash
   gh issue comment $ISSUE_NUMBER --body "..."
   ```

### 의사결정 기준 요약

| 항목 | 개발자 확인 | Claude 자율 |
|------|:-----------:|:-----------:|
| 아키텍처 변경 | ✅ | |
| 데이터 모델 · 시트 스키마 | ✅ | |
| 외부 라이브러리 추가 | ✅ | |
| 보안 · OAuth 스코프 | ✅ | |
| Android 구조 · Gradle · Compose 패턴 | | ✅ |
| 코드 스타일 세부 사항 | | ✅ |
| 테스트 구조 | | ✅ |

---

## Phase 5: Draft PR 작성

1. 변경 파일을 스테이징하고 커밋한다 (OMC 커밋 프로토콜 준수).
   ```bash
   git add <changed files>
   git commit -m "..."
   ```

2. 원격 브랜치에 push한다. (워크트리 안에서 실행하면 워크트리의 브랜치가 그대로 push된다.)
   ```bash
   git push -u origin feature/$ISSUE_NUMBER-$SLUG
   ```

3. Draft PR을 생성한다.
   ```bash
   gh pr create --draft \
     --title "feat: {이슈 제목} (#$ISSUE_NUMBER)" \
     --body "$(cat <<'EOF'
   ## 변경 요약
   - ...

   ## 테스트 방법
   - [ ] `.\gradlew.bat test` 통과 확인
   - [ ] `.\gradlew.bat lint` 통과 확인

   ## 주요 의사결정
   - (ADR 링크 또는 요약, 없으면 생략)

   Closes #$ISSUE_NUMBER
   EOF
   )"
   ```

4. PR URL을 사용자에게 보고한다.

5. 워크트리 정리는 **사용자가 명시적으로 요청할 때만** `ExitWorktree` 도구로 수행한다.
   - 이어서 리뷰 반영 등 후속 작업을 할 수 있으므로 기본적으로는 워크트리를 그대로 둔다.
   - 사용자가 "워크트리 정리/제거"를 요청하면 `ExitWorktree`(`action: "keep"` 또는 `"remove"`)로
     메인 디렉터리로 복귀한다. 세션 종료 시에도 유지/삭제 여부를 사용자에게 묻는다.
