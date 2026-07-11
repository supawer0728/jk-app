## 변경 요약

<!-- 무엇을 왜 바꿨는지 간단히 -->

-

## docs as code 체크리스트

> 코드와 문서는 하나의 변경 단위다. 자세한 규칙은 [`AGENT.md`](../AGENT.md)의 "docs as code" 섹션 참고.

- [ ] 변경된 도메인 모델 속성·Firestore 필드를 해당 `doc/dev/<feature>/DOMAIN.md`에 반영했다
- [ ] 변경된 비즈니스 규칙·계산·상태 전이를 해당 `doc/dev/<feature>/FEATURE.md`에 반영했다
- [ ] 공유 인프라 공개 API가 바뀐 경우 `doc/dev/infra/<name>.md`를 갱신했다
- [ ] 소스코드와 문서 내용이 일치한다 (필드명·시그니처·규칙)
- [ ] 문서만으로는 담기 어려운 중요 결정(아키텍처·데이터 모델·라이브러리·보안)은 `doc/adr/<이슈>/`에 ADR로 남겼다 (해당 시)
- [ ] 문서 변경이 없다면, 이 PR이 문서에 영향을 주지 않음을 확인했다

## 테스트 방법

- [ ] `.\gradlew.bat test` 통과 확인
- [ ] `.\gradlew.bat lint` 통과 확인

## 주요 의사결정

<!-- ADR 링크 또는 요약. 없으면 생략 -->

-

Closes #
