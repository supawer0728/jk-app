---
name: issue-dev-analyze
description: issue-dev 오케스트레이터의 Phase 2 하위 스킬. 이슈 본문·코멘트에서 수용 기준과 제약을 추출하고, 영향받는 개발 문서(DOMAIN/FEATURE/infra)와의 모순을 검증한다. 주로 issue-dev가 Agent(Opus)로 위임해 실행한다. 질문과 TaskCreate는 오케스트레이터가 담당한다.
version: 1.0.0
model: opus
---

# issue-dev / Phase 2: 요구사항 분석

이슈에서 무엇을 해야 하는지 확정하고, docs as code 원칙 2(변경 전 모순 검증)를 수행한다.
**추론 집약적**이므로 Opus로 실행한다.

## 위치

`issue-dev` 오케스트레이터가 두 번째 단계로 `Agent`(model: opus)에 위임하는 하위 스킬이다.
전체 사이클은 [`issue-dev`](../issue-dev/SKILL.md) 참고.

## 입력 (오케스트레이터가 전달)

- `ISSUE_NUMBER`, 이슈 데이터(제목/본문/코멘트) — Phase 1(`issue-dev-fetch`) 반환값
- `WORKTREE_PATH` — 현재 작업 중인 격리 워크트리 경로(파일시스템 공유)

## 절차

1. 이슈 본문과 **코멘트**에서 수용 기준(Acceptance Criteria), 작업 계획, 할 일, 제약 조건을
   추출한다. 코멘트에 구현 가이드나 변경 파일 목록이 있으면 우선적으로 반영한다.
2. **영향받는 문서를 식별**한다. 변경 대상 feature/infra의 `doc/dev/<feature>/DOMAIN.md`·`FEATURE.md`
   또는 `doc/dev/infra/<name>.md`를 읽고, 현재 문서 내용과 이슈 요구사항 사이에 모순이 없는지
   검증한다. (docs as code 원칙 2)
3. 불명확한 부분과 문서 모순은 **해결하지 말고 목록으로 정리**한다(질문은 오케스트레이터가 한다).
4. 확정 가능한 할 일 목록(문서 갱신 항목 포함)을 **제안 형태로 정리**한다.

## 반환 (오케스트레이터에게)

- `acceptance_criteria[]` — 수용 기준·완료 기준
- `constraints[]` — 제약 조건
- `affected_docs[]` — 갱신 대상 문서 경로(DOMAIN/FEATURE/infra). 없으면 빈 목록
- `contradictions[]` — 문서-이슈 또는 문서-코드 모순(있으면 각 항목에 근거)
- `open_questions[]` — 사용자에게 물어야 할 불명확 지점
- `proposed_tasks[]` — 오케스트레이터가 `TaskCreate`로 등록할 할 일 후보

## 오케스트레이터가 담당 (이 스킬은 하지 않음)

- **`AskUserQuestion`**: `open_questions`·`contradictions`를 사용자와 합의하는 것은 오케스트레이터.
  모순이 있으면 착수 전에 반드시 합의한다(docs as code 원칙 2).
- **`TaskCreate`**: `proposed_tasks`를 세션 할 일로 등록하는 것은 오케스트레이터.

## 단독 실행 시

`Agent` 위임 없이 직접 실행되면, 위 절차로 분석한 뒤 열린 질문은 `AskUserQuestion`으로
직접 묻고 할 일은 `TaskCreate`로 등록해도 된다(오케스트레이터가 없을 때의 폴백).
