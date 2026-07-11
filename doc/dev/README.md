# 개발 문서 (docs as code)

이 디렉토리는 `com.jkapp` 소스코드의 **현재 상태**를 사람이 읽을 수 있게 기술한 개발 문서다.
코드와 문서를 하나의 변경 단위로 함께 관리하는 `docs as code` 원칙을 따른다(→ [`AGENT.md`](../../AGENT.md)의 "docs as code" 섹션).

## 문서의 3개 축

문서는 서로 다른 질문에 답하며, 같은 사실을 두 곳에 적지 않는다.

| 축 | 답하는 질문 | 위치 | 성격 |
|----|-------------|------|------|
| **Why (결정)** | 왜 이렇게 했나 | [`doc/adr/`](../adr) | 불변 스냅샷. 결정 시점 기록, 이후 고치지 않음 |
| **What / How-now (현재 사실)** | 지금 무엇이 어떻게 동작하나 | `doc/dev/<feature>/` | 코드와 함께 계속 갱신 |
| **Where / Rules (지도·규칙)** | 전체 구조·문서 규칙 | 이 README + [`AGENT.md`](../../AGENT.md) | 신규/갱신 |

> **경계 원칙**: ADR은 "왜"(근거), DOMAIN/FEATURE는 "무엇/어떻게"(현재). 예를 들어
> "cat-records를 날짜별로 묶는다"는 결정의 근거는 [ADR/10](../adr/10/group-cat-records-by-date.md)에,
> 그 결과 지금 동작하는 규칙은 [diary/FEATURE.md](diary/FEATURE.md)에 기술한다.

## 디렉토리 구조

```
doc/
├── template/          # 문서 템플릿 원본 (아래 "템플릿" 참고)
├── dev/               # 개발 문서 (이 디렉토리)
│   ├── README.md
│   ├── <feature>/
│   │   ├── DOMAIN.md
│   │   └── FEATURE.md
│   └── infra/         # 공유 인프라 경량 단일 문서
└── adr/               # 결정 기록 (Architecture Decision Records)
```

## 문서 종류와 템플릿

새 문서를 만들 때는 `doc/template/`의 템플릿을 복사해 작성한다.

| 문서 | 담는 것 | 템플릿 |
|------|---------|--------|
| `DOMAIN.md` | 도메인 모델의 속성·기능(메서드)·타 도메인 연관성·Firestore 컬렉션 | [`DOMAIN.template.md`](../template/DOMAIN.template.md) |
| `FEATURE.md` | 비즈니스 규칙·계산·상태 전이·유효성·주요 플로우 | [`FEATURE.template.md`](../template/FEATURE.template.md) |
| `infra/<name>.md` | 공유 인프라의 책임·공개 API·의존 관계 (DOMAIN/FEATURE 분리 없이 단일 문서) | [`INFRA.template.md`](../template/INFRA.template.md) |
| `adr/<이슈>/<slug>.md` | 결정의 맥락·결정·근거·대안·결과 | [`ADR.template.md`](../template/ADR.template.md) |

## feature 지도

| feature | 문서 | 도메인 모델 | Firestore 컬렉션 |
|---------|------|-------------|-------------------|
| diary (육묘일기) | [DOMAIN](diary/DOMAIN.md) · [FEATURE](diary/FEATURE.md) | `CatRecord`, `CatRecordType` | `cat-records`, `cat-record-types` |
| finance/asset (자산) | [DOMAIN](finance/asset/DOMAIN.md) · [FEATURE](finance/asset/FEATURE.md) | `DailyAsset`, `AssetItem` | `daily-assets` |
| finance/investment (투자) | [DOMAIN](finance/investment/DOMAIN.md) · [FEATURE](finance/investment/FEATURE.md) | `DailyAssetInvestment`, `InvestmentItem` | `daily-asset-investments` |
| finance/benchmark (벤치마크) | [DOMAIN](finance/benchmark/DOMAIN.md) · [FEATURE](finance/benchmark/FEATURE.md) | `Benchmark` | `benchmarks` |
| todo (할일) | [DOMAIN](todo/DOMAIN.md) · [FEATURE](todo/FEATURE.md) | `TodoItem`, `RecurrenceRule` | `todo-items` |
| settings (설정) | [DOMAIN](settings/DOMAIN.md) · [FEATURE](settings/FEATURE.md) | `AppPreferences`(DataStore), `UserPreference` | DataStore(다크모드/햅틱 등), `users/{uid}.preference`(언어/시간대) |
| calendar (캘린더) | [README](calendar/README.md) | (자리표시자, 미구현) | — |

## 공유 인프라 지도

| 인프라 | 문서 | 책임 |
|--------|------|------|
| auth | [auth.md](infra/auth.md) | Firebase Auth(Google 로그인) |
| drive | [drive.md](infra/drive.md) | Google Drive 첨부파일 저장 |
| notification | [notification.md](infra/notification.md) | FCM 푸시 알림 수신·표시 |
| functions | [functions.md](infra/functions.md) | Cloud Functions(Python) 서버측 로직·담당자 배정 푸시 발송 |
| user | [user.md](infra/user.md) | 사용자 프로필·로그인 이력·푸시 토큰 |
| common | [common.md](infra/common.md) | 앱 환경설정(DataStore)·하단 탭 순서·공용 화면/유틸 |

## 문서 갱신 규칙

코드를 바꿀 때 문서도 같은 PR에서 함께 바꾼다. 상세 절차는 [`AGENT.md`](../../AGENT.md)의
"docs as code" 섹션과 [PR 템플릿](../../.github/PULL_REQUEST_TEMPLATE.md)을 따른다. 요약:

1. 소스 변경 **전** 관련 문서(DOMAIN/FEATURE/infra)에 먼저 반영한다.
2. 소스 변경 **전** 문서 간 모순이 없는지 검증한다.
3. PR 작성 **전** 소스코드와 문서가 일치하는지 검증한다.

아키텍처·데이터 모델·외부 라이브러리·보안 등 중요한 **결정**은 추가로 `doc/adr/<이슈>/`에 ADR을 남긴다.
