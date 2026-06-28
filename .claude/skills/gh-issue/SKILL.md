---
name: gh-issue
description: GitHub 이슈 생성/수정 어시스턴트. number가 있으면 수정, 없으면 생성. 인터뷰를 통해 내용을 수집하고 gh CLI로 실행한다. 트리거: "gh-issue", "이슈 생성", "이슈 수정", "github issue"
version: 1.0.0
---

# GitHub Issue Assistant (`/gh-issue`)

인터뷰를 통해 GitHub 이슈를 생성하거나 수정한다. `gh` CLI로 최종 실행한다.

## 입력

`$ARGUMENTS` — `[number] [content]`

- 첫 번째 토큰이 **숫자**이면 → **수정 모드** (해당 이슈 번호 수정)
- 그렇지 않으면 → **생성 모드** (새 이슈 생성)
- 숫자 이후의 나머지 텍스트(또는 숫자가 없을 때 전체 텍스트) → `content` (사전 정보)

예시:
- `/gh-issue` → 생성 모드, content 없음
- `/gh-issue 버그: 로그인 실패` → 생성 모드, content="버그: 로그인 실패"
- `/gh-issue 42` → 수정 모드, issue #42
- `/gh-issue 42 제목을 바꾸고 싶다` → 수정 모드, issue #42, content="제목을 바꾸고 싶다"

---

## Phase 0: 사전 확인 (블로킹)

다음을 확인하고, 실패 시 안내 후 중단한다.

1. `gh auth status` — GitHub CLI 인증 확인
2. `gh repo view --json name,owner --jq '"\(.owner.login)/\(.name)"'` — 현재 리포지토리 확인

인증되지 않은 경우 `gh auth login`을 안내하고 중단한다.
확인 후 사용자에게 리포지토리를 고지한다:
> 현재 리포지토리: `{owner}/{repo}`

---

## 수정 모드 (`number` 있음)

### Step 1: 현재 이슈 조회 및 표시

```bash
gh issue view {number} --json number,title,body,labels,assignees,milestone,state
```

조회 결과를 마크다운으로 보여준다.

### Step 2: 변경 항목 선택

`content`가 있으면 변경 의도 파악에 우선 활용한다.

`AskUserQuestion` (multiSelect: true)으로 수정 항목 선택:
- 제목 (title)
- 본문 (body)
- 레이블 추가/제거 (labels)
- 담당자 추가/제거 (assignees)
- 마일스톤 변경 (milestone)

### Step 3: 선택 항목별 인터뷰

각 항목을 순서대로 `AskUserQuestion`으로 묻는다.
`content`에 이미 명확히 포함된 항목은 확인만 한다.

### Step 4: 수정 내용 미리보기 및 확인

변경 요약을 보여준다:
```
수정 예정: #{number}
  제목: "기존 제목" → "새 제목"
  담당자 추가: @username
```

`AskUserQuestion`으로 확인:
- 이대로 수정
- 다시 검토
- 취소

### Step 5: 이슈 수정 실행

```bash
gh issue edit {number} [--title "..."] [--body "..."] \
  [--add-label "..."] [--remove-label "..."] \
  [--add-assignee "..."] [--remove-assignee "..."] \
  [--milestone "..."]
```

완료 후 이슈 URL을 출력한다.

---

## 생성 모드 (`number` 없음)

### Step 1: 이슈 유형 파악

`content`에서 유형이 명확히 추론되면 제안하고 확인만 받는다.
불확실하면 `AskUserQuestion`으로 선택:

- **버그 (Bug)** — 예상치 못한 동작, 오류, 크래시
- **기능 (Feature)** — 새로운 기능 요청
- **개선 (Enhancement)** — 기존 기능 개선, UX 향상
- **작업 (Chore)** — 리팩토링, 기술 부채, 설정 변경
- **문서 (Docs)** — 문서화, README, 주석

### Step 2: 제목 수집

`content`로부터 제목을 추론할 수 있으면 제안하고 확인받는다.
추론 불가 시 `AskUserQuestion`으로 직접 입력받는다.

좋은 제목 기준: 50자 이내, "무엇이" "어떻게"인지 구체적으로 표현.

### Step 3: 핵심 내용 인터뷰

**질문은 한 번에 하나씩** `AskUserQuestion`으로 진행한다.
`content`에 이미 충분한 답이 있는 항목은 건너뛴다.

**버그 유형:**
1. 어떤 문제가 발생하는가? (현상, 에러 메시지)
2. 어떻게 재현할 수 있는가? (단계별 재현 방법)
3. 기대했던 동작은 무엇인가?
4. 환경 정보가 필요한가? (필요 시만: OS, 앱 버전 등)

**기능/개선 유형:**
1. 어떤 문제를 해결하거나 어떤 가치를 제공하는가? (Why)
2. 구체적으로 어떻게 동작해야 하는가? (What)
3. 완료 기준이 있는가? (없으면 함께 도출)

**작업/문서 유형:**
1. 무엇을 해야 하는가? (목적과 범위)
2. 완료 기준은 무엇인가?

### Step 4: 메타데이터 수집 (선택)

```bash
gh label list --json name --jq '.[].name'
```

`AskUserQuestion`으로 레이블, 담당자, 마일스톤 추가 여부를 묻는다 (건너뛸 수 있음).

### Step 5: 이슈 본문 작성 및 미리보기

인터뷰 결과를 유형별 템플릿으로 포맷팅한다.

**버그 템플릿:**
```markdown
## 문제 설명
{description}

## 재현 방법
{steps}

## 기대 동작
{expected}

## 실제 동작
{actual}

## 환경
{environment}
```

**기능/개선 템플릿:**
```markdown
## 배경 및 동기
{background}

## 제안 내용
{proposal}

## 완료 기준
- [ ] {criteria_1}
- [ ] {criteria_2}
```

**작업/문서 템플릿:**
```markdown
## 목적
{purpose}

## 완료 기준
- [ ] {criteria_1}
- [ ] {criteria_2}
```

전체 이슈 내용(제목 + 본문 + 메타데이터)을 사용자에게 미리 보여준다.

`AskUserQuestion`으로:
- **이대로 생성** — 바로 실행
- **내용 수정** — 어떤 부분을 고칠지 묻고 수정 후 다시 미리보기
- **취소** — 중단

### Step 6: 이슈 생성 실행

```bash
gh issue create \
  --title "{title}" \
  --body "{body}" \
  [--label "{label}"] \
  [--assignee "{assignee}"] \
  [--milestone "{milestone}"]
```

생성된 이슈 URL을 출력한다.

---

## 규칙

- 모든 인터뷰 질문은 `AskUserQuestion`을 사용한다
- 질문은 **한 번에 하나씩** 진행한다
- `content`에 이미 포함된 정보는 재질문하지 않되, 불충분하면 보완 질문한다
- 최종 실행 전 **반드시 미리보기를 보여주고 확인을 받는다**
- `gh` CLI가 없거나 인증이 안 된 경우 Phase 0에서 즉시 안내하고 중단한다
- 이슈 생성/수정 완료 후 URL을 항상 출력한다
- 취소 요청 시 즉시 중단하고 어떤 작업도 실행하지 않는다