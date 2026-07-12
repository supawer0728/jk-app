# 트러블슈팅 로그 (실패 경험 축적)

디버깅·장애·삽질의 경험을 구조화해 쌓는 곳이다. 목적은 하나다 — **같은 증상을 다시
만났을 때 원인에 빨리 도달하는 것.** 특히 겉으로 보이는 에러 메시지와 실제 근본 원인이
어긋나는(오해하기 쉬운) 사례를 남기는 것이 가장 값지다.

## ADR과의 차이

| 문서 | 담는 것 |
|------|---------|
| `doc/adr/` | **왜** 그렇게 결정했나 (설계 결정의 근거) |
| `doc/troubleshooting/` | **무엇이 잘못됐고 어떻게 고쳤나** (실패·디버깅의 경험) |

## 작성 규칙

1. 원인을 규명하고 해결한 **직후**, 기억이 선명할 때 남긴다.
2. 파일명: `doc/troubleshooting/<YYYY-MM-DD>-<kebab-case-slug>.md`
   (예: `2026-07-12-google-login-sha1-mismatch.md`)
3. 템플릿: [`doc/template/TROUBLESHOOTING.template.md`](../template/TROUBLESHOOTING.template.md)
   — "증상 → 환경 → 진단 과정 → 근본 원인 → 해결 → 재발 방지" 구조를 따른다.
4. 헛다리(세운 가설과 기각 이유)도 남긴다. 다음 사람의 시간을 아낀다.
5. 작성 후 아래 인덱스 표에 한 줄을 추가한다.

## 인덱스

| 날짜 | 영역 | 증상 → 근본 원인 | 문서 |
|------|------|------------------|------|
| 2026-07-12 | auth / build | Google 로그인 시 계정 선택 모달만 닫히고 홈 미전환 (`[16] Account reauth failed`) → 디버그 키스토어 SHA-1이 Firebase에 미등록 | [google-login-sha1-mismatch](2026-07-12-google-login-sha1-mismatch.md) |
| 2026-07-12 | ui / investment | 포트폴리오 저장 시 앱 종료(데이터는 저장됨) → deprecated `ScrollableTabRow`가 탭 증가 프레임에서 인덱스 초과 크래시 | [portfolio-save-scrollabletabrow-crash](2026-07-12-portfolio-save-scrollabletabrow-crash.md) |
