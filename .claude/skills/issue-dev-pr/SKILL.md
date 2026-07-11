---
name: issue-dev-pr
description: issue-dev 오케스트레이터의 Phase 5 하위 스킬. 정합성 최종 검증 후 코드·문서를 같은 커밋에 담아 커밋·push하고 Draft PR을 생성한다. 주로 issue-dev가 Agent(Haiku)로 위임한다. 워크트리 정리(ExitWorktree)는 오케스트레이터가 담당한다.
version: 1.0.0
model: haiku
---

# issue-dev / Phase 5: Draft PR 작성

정합성 검증 후 커밋·push·Draft PR을 만든다. **기계적 작업**이므로 Haiku로 실행한다.

## 위치

`issue-dev` 오케스트레이터가 마지막 단계로 `Agent`(model: haiku)에 위임하는 하위 스킬이다.
전체 사이클은 [`issue-dev`](../issue-dev/SKILL.md) 참고.

## 입력 (오케스트레이터가 전달)

- `ISSUE_NUMBER`, `SLUG`, `BRANCH` — 워크트리 브랜치명(실제 값은 `git branch --show-current`로 확인)
- `WORKTREE_PATH` — 격리 워크트리 경로. 이 트리 안에서 git 명령을 실행한다.
- `change_summary` — 변경 요약. 오케스트레이터가 Phase 3(`changed_files`/`test_results`)과
  Phase 4(`adr_path`) 반환값을 요약해 전달한다.
- `adr_links` — 작성된 ADR 경로/요약(있으면). 오케스트레이터가 Phase 4 `adr_path`에서 전달.

## 절차

### 0. 정합성 최종 검증 (docs as code 원칙 3)

커밋 전 변경된 소스코드와 문서(DOMAIN/FEATURE/infra)가 일치하는지 확인한다. 도메인 속성·
메서드 시그니처·Firestore 컬렉션·비즈니스 규칙이 문서와 어긋나면 커밋하지 말고
`consistency_ok = false`(사유 포함)로 반환한다(오케스트레이터가 Phase 3-1로 되돌린다).

### 1. 커밋

변경 파일을 스테이징하고 커밋한다 (OMC 커밋 프로토콜 준수). **코드와 문서를 같은 커밋에** 담는다.
```bash
git add <changed files>
git commit -m "..."
```

### 2. push

원격 브랜치에 push한다. (워크트리 안에서 실행하면 워크트리의 브랜치가 그대로 push된다.)
실제 브랜치명은 `git branch --show-current`로 확인한 값을 쓴다.
```bash
git push -u origin <current-branch>
```

### 3. Draft PR 생성

```bash
gh pr create --draft \
  --title "feat: {이슈 제목} (#$ISSUE_NUMBER)" \
  --body "$(cat <<'EOF'
## 변경 요약
- ...

## docs as code 체크리스트
- [ ] 변경된 도메인 모델 속성·Firestore 필드를 해당 `doc/dev/<feature>/DOMAIN.md`에 반영했다
- [ ] 변경된 비즈니스 규칙·계산·상태 전이를 해당 `doc/dev/<feature>/FEATURE.md`에 반영했다
- [ ] 공유 인프라 공개 API가 바뀐 경우 `doc/dev/infra/<name>.md`를 갱신했다
- [ ] 소스코드와 문서 내용이 일치한다 (필드명·시그니처·규칙)
- [ ] 문서만으로는 담기 어려운 중요 결정(아키텍처·데이터 모델·라이브러리·보안)은 doc/adr/$ISSUE_NUMBER/ 에 ADR로 남겼다 (해당 시)
- [ ] 문서 변경이 없다면, 이 PR이 문서에 영향을 주지 않음을 확인했다

## 테스트 방법
- [ ] `.\gradlew.bat test` 통과 확인
- [ ] `.\gradlew.bat lint` 통과 확인

## 주요 의사결정
- (ADR 링크 또는 요약, 없으면 생략)

Closes #$ISSUE_NUMBER
EOF
)"
```

> 리포지토리에 [`/.github/PULL_REQUEST_TEMPLATE.md`](../../../.github/PULL_REQUEST_TEMPLATE.md)가 있으므로,
> 위 `--body`의 docs as code 체크리스트는 그 템플릿의 6개 항목과 그대로 맞춘다.

## 반환 (오케스트레이터에게)

- `consistency_ok` — 0단계 정합성 통과 여부(boolean). `false`면 사유를 함께 반환하고
  아래 커밋 이후 단계는 수행하지 않는다.
- `commit_sha`, `pushed_branch`
- `pr_url` — 생성된 Draft PR URL

## 오케스트레이터가 담당 (이 스킬은 하지 않음)

- **PR URL 보고**: 사용자에게 최종 보고.
- **`ExitWorktree`**: 워크트리 정리는 **사용자가 명시적으로 요청할 때만** 오케스트레이터가
  수행한다. 기본적으로는 후속 작업(리뷰 반영 등)을 위해 워크트리를 그대로 둔다.

## 단독 실행 시

`Agent` 위임 없이 직접 실행되면, 위 절차로 커밋·push·PR 생성까지 수행하고 PR URL을 직접
보고한다. 워크트리 정리는 사용자 요청 시에만 진행한다.
