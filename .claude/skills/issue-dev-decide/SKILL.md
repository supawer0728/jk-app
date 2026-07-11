---
name: issue-dev-decide
description: issue-dev 오케스트레이터의 Phase 4 하위 스킬. 구현 중 발생한 의사결정을 일반/중요로 분류하고, 중요 결정(아키텍처·데이터 모델·외부 라이브러리·보안)은 ADR 초안과 이슈 코멘트 문구를 준비한다. 주로 issue-dev가 Agent(Opus)로 위임한다. 최종 승인은 오케스트레이터가 담당한다.
version: 1.0.0
model: opus
---

# issue-dev / Phase 4: 의사결정 처리

구현 중 판단이 필요한 지점을 분류하고, 중요 결정의 근거를 ADR로 남길 수 있게 준비한다.
결정 판단은 **추론 집약적**이므로 Opus로 실행한다.

## 위치

`issue-dev` 오케스트레이터가 의사결정이 필요할 때 `Agent`(model: opus)에 위임하는 하위 스킬이다.
Phase 3 구현 루프와 맞물려 호출된다. 전체 사이클은 [`issue-dev`](../issue-dev/SKILL.md) 참고.

## 입력 (오케스트레이터가 전달)

- `ISSUE_NUMBER`
- `decision_context` — 결정이 필요한 상황(무엇을, 왜 정해야 하는가)

## 절차

### 1. 의사결정 분류

| 항목 | 개발자 확인 | Claude 자율 |
|------|:-----------:|:-----------:|
| 아키텍처 변경 | ✅ | |
| 데이터 모델 · 시트 스키마 | ✅ | |
| 외부 라이브러리 추가 | ✅ | |
| 보안 · OAuth 스코프 | ✅ | |
| Android 구조 · Gradle · Compose 패턴 | | ✅ |
| 코드 스타일 세부 사항 | | ✅ |
| 테스트 구조 | | ✅ |

- **Claude 자율** 항목(AGENT.md에 "Claude에 위임" 명시): 자율 결정하고 근거만 반환한다.
- **개발자 확인** 항목(중요 의사결정): 아래 2를 수행한다.

### 2. 중요 의사결정 — ADR·코멘트 준비

- **ADR 초안**을 준비한다. 파일 경로: `doc/adr/$ISSUE_NUMBER/{kebab-case-title}.md`.
  한 이슈에 여러 결정이면 `01-`, `02-` 접두사를 붙인다.
  - 템플릿 원본: [`doc/template/ADR.template.md`](../../../doc/template/ADR.template.md)
    (섹션: 맥락 / 결정 / 근거 / 검토한 대안 / 예상 결과, 선택적 후속 과제).
  - ADR은 "왜"(결정 근거)의 불변 스냅샷이다. 그 결정으로 지금 동작하는 규칙은 별도로
    해당 `FEATURE.md`(무엇/어떻게)에도 반영해야 한다(docs as code 원칙 1 → Phase 3-1).
- **이슈 코멘트 문구**를 준비한다.

## 반환 (오케스트레이터에게)

- `decision_type` — `autonomous`(자율) | `important`(개발자 확인)
- `adr_path`, `adr_content` — 중요 결정일 때 ADR 파일 경로와 본문 초안
- `feature_doc_update` — ADR 결정으로 갱신할 FEATURE.md 등 현재-사실 문서(해당 시)
- `issue_comment_body` — 이슈에 남길 코멘트 문구
- `question` — 사용자 승인을 받기 위해 오케스트레이터가 물을 질문

## 오케스트레이터가 담당 (이 스킬은 하지 않음)

- **`AskUserQuestion`**: 중요 결정의 최종 승인.
- 승인 후 **ADR 파일 기록**과 **이슈 코멘트 게시**(`gh issue comment $ISSUE_NUMBER --body "..."`).
  오케스트레이터가 직접 수행하거나 이 스킬에 재위임한다. FEATURE.md 등 현재-사실 문서 반영은
  Phase 3-1(`issue-dev-implement`)에서 처리한다.

## 단독 실행 시

`Agent` 위임 없이 직접 실행되면, 분류 후 중요 결정은 `AskUserQuestion`으로 직접 승인받고
ADR 파일 작성·이슈 코멘트 게시까지 수행해도 된다(오케스트레이터가 없을 때의 폴백).
