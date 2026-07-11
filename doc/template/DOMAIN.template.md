<!--
DOMAIN 템플릿 — feature의 "현재 도메인 모델"을 기술한다.

- 저장 위치: doc/dev/<feature>/DOMAIN.md
- 담는 것: 도메인 모델의 속성, 기능(메서드), 타 도메인과의 연관성, 대응 Firestore 컬렉션.
- 담지 않는 것: "왜 이렇게 설계했나"(→ ADR), 비즈니스 규칙·계산·플로우(→ FEATURE.md).
- 원본은 코드다. data class와 Repository의 companion object 상수가 진실이며,
  이 문서는 그것을 사람이 읽을 수 있게 요약한 것이다. 코드가 바뀌면 이 문서도 갱신한다.
- 아래 주석(<!-- -->)은 작성 후 모두 제거한다.
-->
# <feature 이름> 도메인

<!-- 이 feature가 다루는 도메인을 1~2문장으로 요약. -->

## 도메인 모델

### <ModelName>

<!-- data class 위치를 명시: app/src/main/java/com/jkapp/<path>/<ModelName>.kt -->

| 속성 | 타입 | 설명 |
|------|------|------|
| `field` | `Type` | 무엇을 담는가 (기본값·제약이 있으면 함께) |

<!-- 값 객체·enum이 있으면 하위 표로 추가한다. -->

## 기능 (메서드)

<!-- Repository/도메인 모델이 노출하는 주요 동작. 시그니처와 한 줄 설명. -->

| 메서드 | 시그니처 | 설명 |
|--------|----------|------|
| `observeXxx` | `(): Flow<List<Model>>` | 실시간 구독 |
| `addXxx` | `(model): Result<Unit>` | 추가 |

## 타 도메인과의 연관성

<!-- 다른 feature/infra 모델을 참조하거나 참조당하는 관계. 없으면 "없음". -->

- `<OtherModel>` — 어떤 관계인가 (예: attachments로 drive.Attachment를 포함)

## Firestore 컬렉션

<!-- 이 도메인이 매핑되는 컬렉션과 필드명 원본(Impl의 companion object 상수). -->

- 컬렉션: `<collection-name>`
- 담당 Repository: `<XxxFirestoreRepositoryImpl>`

| Firestore 필드 | 도메인 속성 | 비고 |
|----------------|-------------|------|
| `fieldName` | `field` | 타입 변환 등 |
