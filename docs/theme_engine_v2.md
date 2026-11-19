# Dashboard 테마 규격 v2

> 이 문서는 v2 당시 기록이다. 현재 로더와 번들은 [v3 규격](theme_engine_v3.md)을 따른다.

기록: 2026-11-18 KST. 사용자가 승인한 테마 규격·엔진 확장과 기존 테마 마이그레이션이다. F1-2 디자인의 정식 카탈로그 등록은 별도 검수 대상으로 유지한다.

## 변경과 호환성

`ThemeSpec.VERSION`과 번들 catalog/manifest의 `schemaVersion`을 1에서 2로 올렸다. 기존 필수 필드(`id`, `displayName`, `tokens`, `layout`, `fonts`, `assets`)를 유지하고 `typography`, `spacing`, `material`을 추가했다. 작성 도구용 JSON Schema는 [manifest.schema.json](../theme/manifest.schema.json), 런타임 검증의 정본은 `ThemeCatalog`다. 알 수 없는 키, 누락, 타입·범위 오류, 지원하지 않는 버전은 기존과 같이 카탈로그 전체를 기본 Original 하나로 되돌린다.

v1 매니페스트를 자동으로 추측해 읽지 않는다. v1 작성자는 버전을 2로 올리고 아래 기본값을 추가해야 한다. `original`, `aurora_focus`, `carplay_glass_light`, `carplay_glass_dark`는 모두 이 변경에서 이관했다. 저장된 `dashboard_theme/theme_id`는 버전을 저장하지 않으므로 기존 ID와 선택이 유지된다. Original은 새 필드를 포함한 기본값 변경도 거부한다.

```json
{
  "typography": {"bodyScale":100,"numericScale":100,"displayScale":100,"gearSize":184,"combinedGearSize":130},
  "spacing": {"buttonPadding":12,"cardPadding":10,"settingsPadding":24,"pedalPadding":14,"f1SidePadding":4,"gearPadding":0,"gridGapX":8,"gridGapY":6},
  "material": {"style":"legacy","radius":0,"topAlpha":255,"bottomAlpha":255,"highlight":"#D9FFFFFF","shadow":"#0F2859A0","backdropEnd":"#FFE5EFFF"}
}
```

테마의 전체 조망 그리드는 항상 5×4 한 화면으로 유지한다. 테마별 간격은 `gridGapX`와 `gridGapY`로 조정할 수 있지만 열·행 수와 페이지 수는 바꾸지 않는다.

## 폰트와 크기

| 필드 | 범위·의미 | 적용 |
|---|---|---|
| `fonts.body/numeric/display` | 기존 `fonts/*.ttf` 또는 `*.otf`; 빈 문자열은 플랫폼 기본 | 미리 로드한 역할별 폰트, 실패 시 기본 폰트 |
| `bodyScale/numericScale/displayScale` | 정수 60–140, 기준 크기에 대한 백분율 | 공통 헤더, 설정, 전체 조망, F1·미디어, 게이지, 그래프, 퍼포먼스, JS 진단의 Canvas 텍스트 |
| `gearSize` | 64–220, 800×480 기준 px | F1 중앙 기어 |
| `combinedGearSize` | 64–160, 기준 px | F1·미디어 결합 화면 기어 |

현재 역할 분류를 공통화했다. ASCII 숫자가 포함된 문자열은 numeric, 그 외 굵은 문자열은 display, 나머지는 body다. 따라서 `F1`도 numeric이며, 기어 미수신 `—`는 숫자를 포함하지 않으면 display다. 새 글꼴 이름을 지정하는 것만으로 웹 폰트가 Android에 설치되는 것은 아니다. 번들 경로에 실제 파일이 있어야 한다.

측정·가운데 정렬·폭 맞춤·말줄임은 확대된 크기로 계산하고 그리기에서 다시 배율을 곱하지 않는다. 새 flat/glass 기어는 패널 폭과 `gearPadding`에 맞춰 축소한다. legacy와 gearPadding=0 조합은 이전 출력 크기를 유지한다. 이 규격은 화면별 모든 좌표·문자열 크기의 자유로운 재배치를 제공하는 레이아웃 에디터가 아니다. 고정 화면에 큰 배율을 쓰면 세로 여유·이웃 요소와의 간격은 별도 검수가 필요하다.

## 간격

| 필드 | 범위 | 실제 소비 범위 |
|---|---|---|
| `buttonPadding` | 4–24 | 공통 헤더, F1, JS 진단 버튼의 가로 텍스트 inset와 폭 맞춤; 좁은 +/- 버튼에서는 유효 폭으로 제한 |
| `cardPadding` | 4–24 | 전체 조망 카드의 가로 라벨·값·바 inset |
| `settingsPadding` | 8–40 | 설정 행의 좌우 콘텐츠 여백 |
| `pedalPadding` | 8–28 | F1 페달 패널의 라벨·값·단위 가로 여백 |
| `f1SidePadding` | 4–24 | F1 좌우 영역의 제목·단위·보조 라벨 가로 여백 |
| `gearPadding` | 0–24 | F1·미디어 결합 화면 기어 글자의 가로 폭 제한 |
| `gridGapX/gridGapY` | 0–24 | 전체 조망 카드 사이 간격; 고정 slot pitch 안에서 카드 크기를 줄임 |

버튼·설정 행·F1 패널의 바깥 터치 영역은 유지한다. 전체 조망 카드 간격은 `ThemeLayout`을 통해 그리기와 hit test가 같은 크기를 사용한다. 세로 패딩, 화면 전체 재배치, 게이지·그래프 내부 모든 좌표는 이번 규격에 포함하지 않는다.

## 재질

`ThemeSurface`가 API17 Canvas 명령으로 표면을 그린다. `legacy`는 기존 렌더러의 그리기 경로와 픽셀을 유지하며 새 재질 필드를 사용하지 않는다. `flat`은 색상·테두리·반경을 적용하고, `glass`는 투명 그라데이션·가장자리 빛·그림자를 더한다. 헤더·버튼, F1 패널·기어, 설정, 전체 조망, 퍼포먼스 패널, 게이지 카드, 그래프 패널 및 JS 진단의 공통 패널이 지원한다. 기존 특수 게이지 표시 전환 칩은 기존 표현을 사용한다.

- `radius`: 0–36, 표면 크기의 절반을 상한으로 하는 반경.
- `topAlpha/bottomAlpha`: 0–255, glass 표면 색상의 상단·하단 알파. 이 두 값이 채움 토큰의 알파를 대체한다.
- `highlight/shadow/backdropEnd`: `#AARRGGBB`. 반사 테두리·그림자·glass 전체 배경 그라데이션 종점.
- 표면의 색상은 `panel`, `panelRaised`, 활성 강조색 등 기존 토큰에서 얻는다. flat/glass F1에서는 과거 고정 어두운 표면색과 LED 비활성색도 이 토큰을 따른다.

실시간 배경 블러, 광학 굴절, 움직임에 따른 반사는 구현하지 않았다. Apple 네이티브 Liquid Glass와 동일한 재질로 취급하지 않는다.

## 제작·검수 경로

Astryx의 색상은 [export_draft.mjs](../theme/export_draft.mjs)에서 변환한다. Android 고유 설정은 [F1-2 dashboard.json](../theme/themes/f1-2/dashboard.json)과 [F1 Blue dashboard.json](../theme/themes/f1-blue/dashboard.json)에 둔다. `preview_f1.py`는 앱 소스를 치환하지 않고 실제 `DashboardView`·렌더러·엔진·리소스 로더를 실행한다. 호스트에서 실제 OTF 파일을 Java2D로 로드하되 Android와 글꼴 래스터 결과가 완전히 같다는 뜻은 아니다. 검수 중인 테마는 임시 자산 사본의 카탈로그에만 추가한다.

F1-2: Pretendard, numeric 96%, display 105%, 기어 128px, 버튼 여백 10px, 페달 여백 20px, F1 측면 16px, glass radius 24px. Astryx 웹 시안의 Figtree와 별개인 Android용 선택이다. 생성 이미지에는 고정 합성 데이터(124 km/h, D4, 4560 rpm)를 사용한다.

## 검증

- `python3 scripts/test.py`: 24개 합성 Java 호스트 프로그램 통과. v2 범위·타입 오류, 변조된 Original, v1 거부, 역할별 스케일, 카드 크기와 hit test 포함.
- `python3 tests/test_theme_v2_renderer.py`: 실제 렌더러에서 글자 크기·폭 맞춤·가로 여백·설정 hit 영역·OTF 로딩·glass 픽셀 검사.
- `python3 tests/test_performance_renderer.py`, `python3 tests/test_js_diagnostics_renderer.py`: 기존 데이터 상태·다국어·차종별 표시 검사.
- 변경 전 `00af5f6`과 기존 4개 테마 × JS/PD/JSN × 7개 화면(전체 조망/F1/미디어/퍼포먼스/게이지/그래프/설정), 총 84개 호스트 출력의 픽셀 해시가 동일했다. 현재 시각 영역만 비교에서 제외했다. 이는 고정된 미수신 표본의 회귀 검사이며 모든 실차 상태의 픽셀 동일성을 뜻하지 않는다.
- F1 Blue·F1-2 검수 명령은 실제 설정 페이지 이동과 F1 복귀, 퍼포먼스 이동, 목표 RPM +/- 처리를 검사한다. 설정 진입에 모달이 열리지 않음을 확인한다.
- APK 빌드·설치와 Android 기기·실차 실행은 이 작업에서 수행하지 않았다.
