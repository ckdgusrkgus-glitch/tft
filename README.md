# 오토배틀러 (Auto Battler)

자체 세계관의 유닛을 상점에서 뽑아 헥스 보드에 배치하고, 라운드마다 AI 상대와 자동전투를 벌이는
라운드 기반 오토배틀러. 싱글 디바이스에서 플레이어 1명과 AI 봇 7명이 겨룬다.

설계 기준 문서는 프로젝트 명세서(11단계 로드맵)이며, 이 저장소는 그 로드맵을 1단계부터 순서대로 구현한다.

## 현재 진행 상황

| 단계 | 내용 | 상태 |
|---|---|---|
| 1 | 프로젝트 초기 세팅, Room 엔티티, 마스터 데이터 입력 | 완료 |
| 2 | 상점/골드/구매/판매/벤치 UI | 예정 |
| 3 | 헥스보드 배치 | 예정 |
| 4 | 전투 로직 | 예정 |
| 5 | 시너지 계산 엔진 | 예정 |
| 6 | 스타업(합성) | 예정 |
| 7 | 아이템 조합 | 예정 |
| 8 | AI 봇 구매 로직 | 예정 |
| 9 | 증강 시스템 | 예정 |
| 10 | PvE 크립 라운드 | 예정 |
| 11 | 밸런스 튜닝 | 예정 |

## 개발 환경

| 항목 | 값 |
|---|---|
| applicationId | `com.leechanghyun.autobattler` |
| minSdk | 26 (Android 8.0) |
| compileSdk / targetSdk | 35 |
| Kotlin | 2.0.21 |
| AGP | 8.7.2 |
| Gradle Wrapper | 8.11.1 |
| JDK | 17 |
| 빌드 스크립트 | Kotlin DSL (`build.gradle.kts`) 로 통일 |

## 기술 스택

| 영역 | 선택 |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose (선택 근거는 아래 참고) |
| 아키텍처 | MVVM (ViewModel + StateFlow) |
| DI | Hilt |
| 로컬 저장소 | Room |
| 비동기 | Coroutine + Flow |
| 테스트 | JUnit4, Turbine, Robolectric |
| 아트 에셋 | Vector Drawable 색상/도형 placeholder (실제 일러스트 없음) |

### UI 스택을 Compose 로 고른 이유

명세서가 XML+ViewBinding 과 Compose 중 하나를 고르고 근거를 남기라고 했다. Compose 를 골랐다.

1. **헥스 보드가 이 게임의 핵심 화면이다.** 육각형 격자는 표준 레이아웃으로 표현할 수 없어 어느 쪽을
   골라도 직접 그려야 한다. Compose 의 `Canvas` + `pointerInput` 조합이 커스텀 `View` 를 만들어
   `onDraw` / `onTouchEvent` 를 재정의하는 것보다 코드량이 적고, 드래그앤드롭(로드맵 3단계)도
   `detectDragGestures` 하나로 끝난다.
2. **전투가 100ms 틱으로 상태를 계속 갱신한다.** StateFlow 가 바뀔 때마다 알아서 다시 그려지는
   선언형 UI 가, 틱마다 어떤 View 를 어떻게 고쳐야 하는지 직접 관리하는 방식보다 버그가 적다.
3. **Kotlin 2.0 부터 Compose 컴파일러가 Kotlin 플러그인에 포함됐다.** 과거 Compose 를 어렵게 만들던
   "Kotlin 버전과 Compose 컴파일러 버전 맞추기" 문제가 사라져서 초기 세팅 비용이 거의 없다.

단점도 있다. 국내 실무에는 아직 XML 기반 레거시 코드가 많아서, XML 을 아예 안 다뤄보는 건 손해다.
다만 그건 이 프로젝트가 아니라 다른 곳에서 익히는 편이 낫다고 판단했다.

## 모듈 구조

```
autobattler/
├─ core-game/     순수 Kotlin(JVM) 모듈. 안드로이드 의존성이 전혀 없다.
│   ├─ model/         UnitDef, BoardUnit, HexCoord, PlayerState 등 도메인 모델
│   ├─ masterdata/    명세서 4장 표 전체 (유닛 14, 시너지 8, 컴포넌트 9, 증강 10, 몬스터 4)
│   ├─ economy/       상점 확률표, 공용 유닛 풀, 상점 롤 로직
│   └─ persistence/   Room TypeConverter 가 쓰는 인코딩 로직
└─ app/           안드로이드 모듈
    ├─ data/local/       Room 엔티티, DAO, DB, 마스터 데이터 시더
    ├─ data/repository/  GameDataRepository
    ├─ di/               Hilt 모듈
    └─ ui/shop/          상점 화면 (Compose)
```

### 왜 `core-game` 을 별도 모듈로 뺐는가

명세서 6장의 레이어 구성은 `app/domain/` 아래에 전투·경제·AI 로직을 두는 그림이었다.
여기서는 그 부분을 `core-game` 이라는 **별도 Gradle 모듈**로 분리했다.

- 명세서 4-6, 9-4 가 요구하는 "UI 와 완전히 분리된 순수 Kotlin 로직 + 단위테스트 필수"를
  컴파일 단계에서 강제한다. `core-game` 에는 안드로이드 의존성이 없으므로, 실수로 `Context` 나
  `ViewModel` 을 끌어다 쓰면 빌드가 깨진다.
- 안드로이드 SDK 없이 JVM 만으로 테스트가 돌아 실행이 몇 초로 끝난다.
  전투 시뮬레이션(4단계)과 AI 스코어링(8단계)처럼 반복 실행이 많은 로직에서 차이가 크다.

패키지 이름과 레이어 경계는 명세서 6장과 같고, 물리적 위치만 다르다.

## 빌드와 테스트

```bash
# 순수 Kotlin 로직 테스트 (안드로이드 SDK 불필요)
./gradlew :core-game:test

# Room 왕복 테스트 포함 앱 단위테스트 (Robolectric)
./gradlew :app:testDebugUnitTest

# 디버그 APK 빌드
./gradlew :app:assembleDebug
```

## 1단계에서 정한 값에 대한 주의

명세서에 수치가 없어 **임의 초기값**으로 채운 항목이다. 로드맵 11단계 밸런스 튜닝에서 조정한다.

- 유닛의 `attackSpeed` (명세서 4-4 표에 열이 없음)
- 스킬의 마나 비용 / 시작 마나 / 기본 위력 / 범위 (명세서 4-6 이 임의값으로 채우라고 명시)
- 아이템 컴포넌트의 스탯 증가량 (명세서 4-5 는 이름만 정의)
- 연승·연패 보너스 골드 표, 라운드당 자동 지급 경험치 (명세서 4-1, 4-2 에 수치 없음)
- 벤치 칸 수 9칸, 시작 체력 100

명세서가 정의하지 않아 **아직 구현하지 않은** 항목이다.

- 완성 아이템 목록과 조합 매트릭스: 명세서 4-5 는 컴포넌트 9종의 이름만 정의하고 완성 아이템은
  이름도 효과도 정하지 않았다. 로드맵 7단계에서 채운다.
- 경험치표 해석: 명세서 4-2 의 열 제목은 "누적 경험치"지만 행이 `1→2`, `2→3` 형태라
  **레벨업 1회에 필요한 양**으로 읽었다.
