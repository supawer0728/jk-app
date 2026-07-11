---
name: issue-dev-implement
description: issue-dev 오케스트레이터의 Phase 3 하위 스킬. docs as code 구현 루프 — 문서 우선 반영 → 코드 수정 → 테스트 작성 → 테스트 검증. 주로 issue-dev가 Agent(Sonnet)로 위임해 실행한다. 코드·문서 리뷰(3-5)는 오케스트레이터가 code-reviewer(Opus)로 별도 위임한다.
version: 1.0.0
model: sonnet
---

# issue-dev / Phase 3: 구현 루프 (docs as code)

**문서를 코드보다 먼저 반영**하며 구현한다. 실행 중심 작업이므로 Sonnet으로 실행한다.
리뷰(3-5)만 추론 집약적이라 Opus로 분리한다.

## 위치

`issue-dev` 오케스트레이터가 구현 단계로 `Agent`(model: sonnet)에 위임하는 하위 스킬이다.
전체 사이클은 [`issue-dev`](../issue-dev/SKILL.md) 참고.

## 입력 (오케스트레이터가 전달)

- `ISSUE_NUMBER`, `acceptance_criteria`, `proposed_tasks`(확정된 할 일) — Phase 2 반환값
- `WORKTREE_PATH` — 격리 워크트리 경로. subagent는 이 트리에서 파일을 편집한다(공유 파일시스템).

## 절차

문제가 없을 때까지 3-1 ~ 3-4를 반복하고, 3-5 리뷰로 마무리한다.

### 3-1. 문서 우선 반영 (docs as code 원칙 1)

- 코드를 바꾸기 전에 관련 문서를 먼저 갱신한다.
  - 도메인 모델 속성·Firestore 필드 변경 → 해당 `doc/dev/<feature>/DOMAIN.md`
  - 비즈니스 규칙·계산·상태 전이 변경 → 해당 `doc/dev/<feature>/FEATURE.md`
  - 공유 인프라 공개 API 변경 → `doc/dev/infra/<name>.md`
  - 새 feature/infra 추가 → `doc/template/`의 템플릿을 복사해 신규 문서 작성 + `doc/dev/README.md` 지도 갱신
- 문서를 갱신하면서 기존 문서·코드와 **모순이 없는지 검증**한다. 모순이 있으면 진행하지 말고
  `decisions_needed`에 담아 반환한다(사용자 합의는 오케스트레이터가 처리).
- 문서 전용 이슈면 이 단계가 곧 구현이며, 3-2~3-4는 생략할 수 있다.

### 3-2. 코드 수정

- 변경 전 관련 코드 패턴을 반드시 먼저 파악한다 (Explore 에이전트 활용).
- `AGENT.md`(= `CLAUDE.md`)의 코드 스타일·컨벤션을 준수하고, **3-1에서 갱신한 문서와 일치하도록** 구현한다.
- 변경 범위는 이슈 범위로 한정한다. 범위 외 리팩터링은 `decisions_needed`에 담아 반환한다.

### 3-3. 테스트 코드 작성

- 변경된 비즈니스 로직에 대한 단위 테스트를 작성한다 (`app/src/test`).
- 기기 연결이 필요한 경우에만 계측 테스트(`app/src/androidTest`)를 사용한다.

### 3-4. 테스트 검증

```powershell
.\gradlew.bat test          # JVM 단위 테스트
.\gradlew.bat lint          # Android Lint
```

- 실패 시 원인을 분석하고 **3-2**로 돌아간다.
- 연속 2회 이상 같은 오류가 반복되면 `decisions_needed`에 담아 반환한다(방향 확인은 오케스트레이터).
- 문서 전용 변경(코드 미변경)이면 테스트는 실질 no-op이므로 생략하고 그 사실을 기록한다.

### 3-5. 코드·문서 리뷰 (docs as code 원칙 3)

- **오케스트레이터 위임 실행 시**: 이 단계는 구현 subagent가 직접 수행하지 않는다.
  중첩 subagent를 피하기 위해 **오케스트레이터가 `code-reviewer`(Opus)로 별도 위임**하고
  구현↔리뷰 루프를 돌린다. 구현 subagent는 3-1~3-4까지 마친 뒤 반환한다.
- **단독 실행 시**: 이 스킬이 `oh-my-claudecode:code-reviewer` 에이전트로 리뷰를 직접 수행한다.
- 리뷰는 **소스코드와 문서(DOMAIN/FEATURE/infra)가 일치하는지** 함께 검증한다
  (필드명·시그니처·규칙·컬렉션).
- **High/Critical** 지적 또는 **문서-코드 불일치**: 수정 후 **3-1**로 복귀.
- **Low/Medium** 지적: 오케스트레이터에 보고(사용자 결정 대상).
- 리뷰 통과 시 루프를 종료한다.

## 반환 (오케스트레이터에게)

- `changed_files[]` — 변경한 파일 목록(문서·코드)
- `test_results` — `test`/`lint` 결과 요약(또는 문서 전용 no-op 사유)
- `high_critical_remaining[]` — 남은 High/Critical(있으면 루프 미완)
- `low_medium_findings[]` — 사용자 결정이 필요한 Low/Medium 지적
- `decisions_needed[]` — 모순·범위 외 변경·반복 오류 등 사용자 합의가 필요한 항목

## 오케스트레이터가 담당 (이 스킬은 하지 않음)

- **3-5 리뷰의 Opus 위임**과 구현↔리뷰 루프 제어.
- **`AskUserQuestion`**: `low_medium_findings`·`decisions_needed` 결정.
