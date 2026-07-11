---
name: issue-dev-fetch
description: issue-dev 오케스트레이터의 Phase 1 하위 스킬. GitHub 이슈를 조회하고 제목에서 영문 slug를 만든다. 주로 issue-dev가 Agent(Haiku)로 위임해 실행하며, 단독 실행도 가능하다. 워크트리 생성은 오케스트레이터가 담당한다.
version: 1.0.0
model: haiku
---

# issue-dev / Phase 1: 이슈 조회 및 slug 생성

이슈 번호를 받아 내용·코멘트를 조회하고, 워크트리 브랜치명에 쓸 영문 slug를 만든다.
**기계적 작업**이므로 Haiku로 실행한다.

## 위치

`issue-dev` 오케스트레이터가 첫 단계로 `Agent`(model: haiku)에 위임하는 하위 스킬이다.
전체 사이클은 [`issue-dev`](../issue-dev/SKILL.md) 참고.

## 입력 (오케스트레이터가 전달)

- `ISSUE_NUMBER` — GitHub 이슈 번호

## 절차

1. 이슈 내용과 코멘트를 조회한다.
   ```bash
   gh issue view $ISSUE_NUMBER --json number,title,body,labels,assignees,comments
   ```

2. 이슈 제목에서 영문 slug를 만든다 (소문자·하이픈, 최대 4단어).
   - 예) "Add Google Sheets OAuth flow" → `add-sheets-oauth-flow`

3. 제안 브랜치명을 만든다: `feature/$ISSUE_NUMBER-$SLUG`

## 반환 (오케스트레이터에게)

다음을 구조화해 반환한다.

- `number`, `title`, `body`
- `labels`, `assignees`
- `comments_summary` — 코멘트에 담긴 구현 가이드·변경 파일 목록이 있으면 요약(없으면 "없음")
- `slug`
- `proposed_branch` — `feature/$ISSUE_NUMBER-$SLUG`
- `scope_summary` — 이슈로 이해한 작업 범위 한 문단

## 오케스트레이터가 담당 (이 스킬은 하지 않음)

- **워크트리 생성**(`EnterWorktree`): 세션 작업 디렉터리를 바꾸므로 subagent가 아니라
  오케스트레이터가 `feature/$ISSUE_NUMBER-$SLUG`로 직접 수행한다.
- **작업 범위 확인**: 반환한 `scope_summary`를 사용자에게 보여주고 확인받는 것은 오케스트레이터.

## 단독 실행 시

`Agent` 위임 없이 직접 실행되면, 위 절차를 수행해 조회 결과와 slug/브랜치명을 사용자에게
보여준다. 워크트리 생성은 별도 단계(오케스트레이터 또는 사용자)에서 진행하도록 안내한다.
