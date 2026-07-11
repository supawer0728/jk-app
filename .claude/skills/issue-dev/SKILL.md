---
name: issue-dev
description: This skill should be used when the user says "issue-dev", "이슈 작업", "이슈 번호로 개발", "이슈 기반 개발", or provides a GitHub issue number to start working on. Orchestrates the full cycle by delegating each phase to a dedicated sub-skill (issue-dev-fetch/analyze/implement/decide/pr) run as an Agent with a phase-appropriate model. Enforces the docs as code 원칙(문서 우선 반영·정합성 검증).
version: 3.0.0
---

# Issue-Driven Development (오케스트레이터)

GitHub 이슈 번호를 받아 **격리된 워크트리 생성**부터 Draft PR 작성까지 전체 개발 사이클을 수행한다.
이 스킬은 각 Phase를 담당하는 **하위 스킬을 `Agent`(subagent)로 위임**하는 오케스트레이터다.
각 하위 스킬은 작업 난도에 맞는 model로 실행된다.

각 이슈 작업을 독립된 git 워크트리에서 진행하므로, **여러 세션에서 서로 다른 이슈를
동시에 작업**해도 작업 트리가 충돌하지 않는다.

> 이 구조의 결정 근거: [`doc/adr/85/issue-dev-skill-split-and-model-mapping.md`](../../../doc/adr/85/issue-dev-skill-split-and-model-mapping.md)

## 하위 스킬과 model 매핑

| Phase | 하위 스킬 | model | 성격 |
|-------|-----------|-------|------|
| 1 | [`issue-dev-fetch`](../issue-dev-fetch/SKILL.md) | Haiku | 이슈 조회·slug (기계적) |
| 2 | [`issue-dev-analyze`](../issue-dev-analyze/SKILL.md) | Opus | AC 추출·문서 모순 검증 (추론) |
| 3 | [`issue-dev-implement`](../issue-dev-implement/SKILL.md) | Sonnet | 문서 우선·코드·테스트 (실행) |
| 3-5 | (리뷰) `oh-my-claudecode:code-reviewer` | Opus | 코드·문서 정합성 리뷰 (추론) |
| 4 | [`issue-dev-decide`](../issue-dev-decide/SKILL.md) | Opus | 의사결정·ADR (추론) |
| 5 | [`issue-dev-pr`](../issue-dev-pr/SKILL.md) | Haiku | 커밋·push·PR (기계적) |

## 위임 규약

- 각 단계는 `Agent`(`subagent_type: general-purpose`, `model:` = 위 표)로 실행한다.
- Agent 프롬프트에는 **하위 스킬 경로를 읽고 실행하라는 지시 + 단계 간 상태**만 전달한다
  (지침 본문을 인라인하지 않는다 — 하위 스킬 SKILL.md가 단일 진실). 예:
  > "`.claude/skills/issue-dev-analyze/SKILL.md`를 읽고 그 절차대로 이슈 #85를 분석한 뒤,
  > 그 스킬의 '반환' 항목을 구조화해 반환하라. 워크트리 경로는 `<WORKTREE_PATH>`."
- subagent는 워크트리(파일시스템 공유)에서 파일을 편집하며, 구조화된 결과를 반환한다.

### 오케스트레이터가 직접 수행 (subagent에 위임하지 않는다)

도구의 실제 동작상 subagent가 하면 안 되는 것들이다.

- **`EnterWorktree` / `ExitWorktree`** — 세션 작업 디렉터리 전환. subagent가 호출하면 이 세션이
  워크트리로 이동하지 않는다.
- **`AskUserQuestion`** — 사용자 상호작용. subagent는 자율 실행되어 직접 질문할 수 없다.
- **`TaskCreate` / `TaskUpdate`** — 세션 할 일 목록.
- **최종 승인** — 작업 범위 확인, 중요 의사결정 승인, PR 생성 전 확인.

### 단계 간 상태

오케스트레이터가 다음 최소 상태를 각 단계에 전달한다.
`ISSUE_NUMBER`, `SLUG`, `BRANCH`, `WORKTREE_PATH`, 그리고 직전 단계의 반환값.

## docs as code 원칙 (이 사이클 전반에 적용)

코드와 문서는 하나의 변경 단위다. 이 스킬은 다음 3원칙을 강제한다
(체계·템플릿: [`doc/dev/README.md`](../../../doc/dev/README.md), `AGENT.md`의 "docs as code" 섹션).

1. **변경 전 문서 반영**: 소스코드를 바꾸기 전에 관련 문서(DOMAIN/FEATURE/infra)를 먼저 갱신한다. (Phase 3)
2. **변경 전 모순 검증**: 문서 갱신 시 기존 문서·코드와 모순이 없는지 검증한다. (Phase 2, 3)
3. **PR 전 정합성 검증**: PR 작성 전 소스코드와 문서가 일치하는지 검증한다. (Phase 3-5, Phase 5)

## 입력

`$ARGUMENTS` — GitHub 이슈 번호 (예: `/issue-dev 42`)

---

## Phase 1: 이슈 조회 및 워크트리 생성

1. **위임(Haiku)**: `issue-dev-fetch`를 `Agent`로 실행해 이슈 데이터와 `slug`,
   `proposed_branch`(`feature/$ISSUE_NUMBER-$SLUG`), `scope_summary`를 받는다.

2. **오케스트레이터 수행**: `EnterWorktree` 도구로 격리된 워크트리를 생성하고 세션을 그 안으로
   전환한다.
   - `name` 인자로 브랜치명을 넘긴다: `feature/$ISSUE_NUMBER-$SLUG`
   - `.claude/worktrees/` 아래에 워크트리와 동명 브랜치를 만들고(기본 base = `origin/main`)
     현재 세션 작업 디렉터리를 그곳으로 옮긴다. 다른 세션과 간섭하지 않는다.
   - `git checkout -b`나 `git worktree add`를 직접 호출하지 말고 반드시 `EnterWorktree`를 쓴다.
   - 전환 후 실제 브랜치명을 `git branch --show-current`로 확인해 `BRANCH`로 보관한다
     (도구가 슬래시를 정규화할 수 있다). 이후 push는 이 실제 브랜치명을 쓴다.

3. **오케스트레이터 수행**: `scope_summary`를 사용자에게 보여주고, 이해한 작업 범위를 한 문단으로
   확인받는다(`AskUserQuestion` 또는 텍스트 확인).

---

## Phase 2: 요구사항 분석

1. **위임(Opus)**: `issue-dev-analyze`를 `Agent`로 실행해 `acceptance_criteria`, `constraints`,
   `affected_docs`, `contradictions`, `open_questions`, `proposed_tasks`를 받는다.

2. **오케스트레이터 수행**:
   - `contradictions`가 있으면 착수 전에 **`AskUserQuestion`**으로 사용자와 합의한다(원칙 2).
   - `open_questions`를 **`AskUserQuestion`**으로 질문하고 답을 받는다.
   - 확정된 할 일(`proposed_tasks` + 문서 갱신 항목)을 **`TaskCreate`**로 등록한다.

---

## Phase 3: 구현 루프 (docs as code)

`issue-dev-implement`의 절차(3-1 문서 우선 → 3-2 코드 → 3-3 테스트 → 3-4 검증)를 문제가 없을
때까지 반복하고, 3-5 리뷰로 마무리한다. **중첩 subagent를 피하기 위해 리뷰는 오케스트레이터가
별도 위임**한다.

1. **위임(Sonnet)**: `issue-dev-implement`를 `Agent`로 실행해 3-1~3-4를 수행하게 하고,
   `changed_files`, `test_results`, `low_medium_findings`, `decisions_needed`를 받는다.
   (`high_critical_remaining`은 단독 실행용 필드로, 위임 모드에서는 아래 2의 리뷰가 대체하므로 무시한다.)

2. **위임(Opus, 리뷰 3-5)**: `oh-my-claudecode:code-reviewer` 에이전트로 리뷰한다.
   **소스코드와 문서(DOMAIN/FEATURE/infra)가 일치하는지** 함께 검증한다(필드명·시그니처·규칙·컬렉션).
   - **High/Critical** 또는 **문서-코드 불일치**: 지적을 담아 다시 1(Sonnet 구현)로 위임해 수정 →
     문서 우선(3-1)부터 재적용.
   - **Low/Medium**: 오케스트레이터가 사용자에게 보고 후 결정에 따라 처리(`AskUserQuestion`).
   - 리뷰 통과 시 루프를 종료하고 Phase 5로 진행한다.

3. **의사결정 발생 시**: `decisions_needed`가 있으면 Phase 4로 처리한 뒤 구현 루프를 재개한다.

---

## Phase 4: 의사결정 처리

1. **위임(Opus)**: `issue-dev-decide`를 `Agent`로 실행해 `decision_type`과, 중요 결정이면
   `adr_path`/`adr_content`/`feature_doc_update`/`issue_comment_body`/`question`을 받는다.

2. **오케스트레이터 수행**:
   - `decision_type == autonomous`(AGENT.md에 "Claude에 위임"): 그대로 진행한다.
   - `decision_type == important`(아키텍처·데이터 모델·외부 라이브러리·보안):
     **`AskUserQuestion`**으로 `question`을 물어 승인받는다.
     - 승인 후 **ADR 파일 기록**(`adr_path`에 `adr_content`)과 **이슈 코멘트 게시**를 수행한다.
       ```bash
       gh issue comment $ISSUE_NUMBER --body "..."
       ```
     - `feature_doc_update`가 있으면 그 현재-사실 문서 반영은 Phase 3-1로 처리한다.

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

1. **위임(Haiku)**: `issue-dev-pr`를 `Agent`로 실행한다. 이 스킬은 0단계 정합성 최종 검증(원칙 3)
   후 커밋(코드+문서 같은 커밋)·push·Draft PR 생성을 수행하고 `pr_url`을 반환한다.
   - `consistency_ok`가 `false`로 반환되면 커밋하지 말고 Phase 3-1로 되돌아간다.
   - push 브랜치는 Phase 1에서 보관한 실제 `BRANCH`(= `git branch --show-current`)를 쓴다.

2. **오케스트레이터 수행**: `pr_url`을 사용자에게 보고한다.

3. **오케스트레이터 수행**: 워크트리 정리는 **사용자가 명시적으로 요청할 때만** `ExitWorktree`
   도구로 수행한다.
   - 이어서 리뷰 반영 등 후속 작업을 할 수 있으므로 기본적으로는 워크트리를 그대로 둔다.
   - 사용자가 "워크트리 정리/제거"를 요청하면 `ExitWorktree`(`action: "keep"` 또는 `"remove"`)로
     메인 디렉터리로 복귀한다. 세션 종료 시에도 유지/삭제 여부를 사용자에게 묻는다.

---

## 하위 호환

- 입력(`/issue-dev <번호>`)과 최종 산출물(Draft PR)은 기존과 동일하다. 내부 실행 구조만 바뀐다.
- 개별 단계는 하위 스킬로 단독 실행할 수도 있다(각 하위 스킬의 "단독 실행 시" 참고).
