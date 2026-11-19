# Material 3 · Android Auto 영감 테마 리서치 및 구현 규격

- 작성일: 2026-11-19 (KST)
- 대상 저장소: `dashboard/js`, 브랜치 `dev-theme`
- 목적: Google 공식 디자인 자료를 토대로 Dashboard용 테스트 테마와 공용(CoWork) 테마 엔진의 작업 규격을 제공한다.
- 상태: 리서치 근거와 구현 계약. 구현·APK·실차 검증 결과는 작업 기록에 별도로 남긴다.
- 테마 작성 질문표: [테마 생성 질문표](THEME_CREATION_QUESTIONNAIRE.md). 엔진·schema·renderer 계약이 바뀌면 질문표도 같은 변경에서 동기화한다.

## 방향

Material 3의 의미 기반 색상·타이포그래피·형태 체계와 Android Auto 공식 디자인 자료의 빠른 판독, 명확한 계층, 큰 조작 영역 원칙을 참고한다. 결과물은 Android Auto 화면의 복제나 Android Auto 인증 UI가 아니라, 800×480 Dashboard에 맞춘 독립 테마다. Android Auto용 앱 디자인 가이드를 OEM 대시보드에 그대로 적용 가능한 인증 규격처럼 취급하지 않는다.

새 테스트 테마는 Google Material의 차분한 파랑 계열 강조색과 어두운 차량용 표면을 조합한다. Material과 Liquid Glass 포인트 컬러 목록은 엔진별로 이름과 값이 분리된다. Liquid Glass 목록은 iOS named system colors를 참조하되 값이 OS 환경마다 바뀌는 system color API를 재현한다고 주장하지 않는다. 기존 Original과 Liquid Glass 테마는 유지하고, 새 테마 ID를 별도로 추가해 적용·취소 및 렌더링 회귀를 확인한다. 테마가 바꿀 수 있는 것은 표시 스타일과 허용된 화면 배치뿐이다. 차량 데이터, 단위 변환, 신호 유효성, 경고 판정, 입력 동작 및 설정 저장 계약은 변경하지 않는다.

## 공식 자료에서 가져올 원칙

| 주제 | 공식 근거 | 이 프로젝트에서의 해석 |
|---|---|---|
| Material 색 역할과 포인트 컬러 | Google Material 3 문서는 key color와 tonal palette에서 light/dark scheme의 역할 색을 만드는 방식을 설명한다. | Material Auto 팔레트는 key-color 방식에 맞춘 별도의 source-color 선택지로 둔다. 현재 런타임 색 토큰이 `accent` 하나이므로 선택은 primary/accent 역할에 적용하고 warning/critical/unavailable은 고정한다. 이 간이 매핑을 완전한 Material tonal scheme이라고 부르지 않는다. |
| Liquid Glass 색과 tint | Apple HIG는 Liquid Glass가 뒤 콘텐츠의 색을 받아들이며 tint는 강조가 필요한 일부 요소에 절제해 사용하도록 안내한다. iOS named system colors는 appearance에 맞춘 변형을 제공하며 Apple은 system color 값을 하드코딩하지 말라고 명시한다. | Liquid Glass 전용 선택지는 Apple iOS named system colors Blue, Indigo, Purple, Pink, Orange, Green을 참조한다. Android Canvas의 light/dark 값은 HIG의 system color 표를 참고한 디자인 시안이며 UIKit dynamic color 구현이나 Liquid Glass 고정 팔레트가 아니다. |
| Liquid Glass 배경 이미지 | Apple HIG의 Materials 가이드는 Liquid Glass를 탐색·제어와 상호작용 레이어에 두고, 콘텐츠 영역에는 내용과 조화를 이루는 표준 재질을 사용하도록 구분한다. HIG는 특정 Liquid Glass 배경화면의 색상별 파일 규격을 제시하지 않는다. | 배경 이미지는 콘텐츠 레이어의 독립 자산으로 유지하고 Liquid Glass 카드·버튼·블러를 이미지에 합성하지 않는다. 기존 Blue/Green/Red/Mono의 곡선 구도와 밝기를 기준으로 색상 파생본을 만들며, 실제 Glass 표면은 런타임이 위에 렌더링한다. |
| 대비와 차량 가독성 | Android Auto 공식 색상 가이드는 검정 바탕, 회색 명도로 표면 계층을 표현하고 강조색을 절제해 쓰며, 전경/배경 대비 4.5:1 이상을 제시한다. | 이 수치를 텍스트/아이콘 대비의 목표로 참고해 자동 검사한다. 낮/밤 프리뷰에서 경고·미수신·무효 상태는 기존 텍스트/아이콘 의미도 유지한다. 색상 변화만으로 상태 의미를 바꾸지 않는다. |
| 타이포그래피 | Android Auto 공식 가이드는 본문 최소 24dp, 중요하지 않은 보조문만 그보다 작게 쓰고 4dp 격자에 맞추도록 안내한다. | 24dp는 Android Auto 투사 UI 문서의 단위이므로 800×480 Dashboard에 그대로 대입하지 않는다. 속도·온도·전압 위계와 기존 축소 로직을 유지하고, 한국어·숫자·단위·긴 경고 문구의 잘림을 실제 출력으로 확인한다. |
| 크기·간격·조작 | Android Auto 공식 sizing 가이드는 최소 터치 대상 76×76dp 및 겹치지 않는 대상, layout 가이드는 8dp 간격 격자와 반응형 구성을 제시한다. | dp/8dp를 800×480 논리 좌표에 그대로 대입하지 않는다. 현재 Dashboard의 hit geometry와 기존 조작을 보존하고, 대상 차량 화면에서 겹침·오조작 가능성을 확인한다. |
| 표현·개인화 | Google은 2026-05-12 발표에서 차세대 Android Auto에 Material 3 Expressive, 표현력 있는 글꼴, 부드러운 애니메이션, 배경화면 및 위젯을 소개했다. | 부드러운 표면, 명료한 선택 강조, 절제된 전환과 개인화를 참고한다. 제품 발표의 방향을 세부 구현 API나 OEM 화면 규격으로 추정하지 않는다. |

### 색상 리서치 출처

- Google Android Developers, [Material Design 3 in Compose: Color scheme](https://developer.android.com/develop/ui/compose/designsystems/material3#color-scheme): 다섯 key color와 13-tone 팔레트, source color에서 light/dark scheme 생성, primary 역할 설명.
- Apple Human Interface Guidelines, [Color](https://developer.apple.com/design/human-interface-guidelines/color): named system color의 appearance별 RGB 참고값, system color 값은 환경/릴리스에 따라 달라지므로 앱 코드에서 하드코딩하지 말라는 주의, Liquid Glass의 기본 색과 tint 사용 지침.
- Apple Human Interface Guidelines, [Materials](https://developer.apple.com/design/human-interface-guidelines/materials): Liquid Glass의 제어·탐색 레이어 역할과 콘텐츠 레이어와의 구분.

Apple의 documented RGB는 디자인 참고값이다. 이 Android Canvas 프로젝트는 iOS UIColor/SwiftUI dynamic color API를 실행하지 않으므로 Liquid Glass용 색 선택은 system-color 참고값을 고정한 시안이며, iOS와 같은 환경 적응이나 접근성 대비 전체를 구현한 것으로 간주하지 않는다.

### Liquid Glass 포인트 컬러와 기본 배경 연결 규칙

- Apple HIG의 색/재질 지침을 배경 아트의 복제 규격으로 취급하지 않는다. Liquid Glass 테마의 배경은 컨텐츠를 받쳐주는 별도 wallpaper이며, Glass는 해당 이미지 위에 엔진이 그리는 탐색·제어 표면이다.
- Liquid Glass 엔진에만 accent-key 기반 기본 wallpaper를 연결한다: `blue`, `indigo`, `purple`, `pink`, `orange`, `green`. 여섯 변형은 기존 Blue/Green 자산을 스타일 참조로 삼아 같은 곡선 구도/밝기 계열로 새 파일에 생성한다. 기존 Blue/Red/Green/Mono wallpaper는 직접 선택용으로 유지한다.
- 사용자가 wallpaper를 `테마 기본값`으로 두면 현재 accent key의 wallpaper를 렌더링한다. 포인트 컬러를 바꾸면 이 기본 wallpaper도 같이 바뀐다. 사용자가 특정 wallpaper를 고르면 그 선택이 accent-linked 기본값보다 우선하고, 포인트 컬러 변경은 수동 wallpaper를 덮어쓰지 않는다. `테마 기본값`으로 돌아오면 현재 accent에 맞는 이미지가 다시 적용된다.
- Material Auto, Original 및 비-Liquid Glass 테마에는 이 연결을 적용하지 않는다. 기존 단색/기본 배경 동작을 유지한다.
- `components.json`의 전용 `accentBackgrounds` map으로 palette key와 이미지 경로를 연결하고 `ThemeSpec`/`ThemeResources`가 이를 기본 background로 해석한다. 이 자동 배경은 수동 wallpaper 목록에 중복 노출하지 않는다. 키가 없거나 파일을 읽지 못하면 기존 테마 기본 wallpaper로 안전하게 fallback한다. 수동 wallpaper 선택 계약은 유지한다.
- 현행 Liquid Glass 원본 이미지 크기인 1920×1152 PNG를 기준으로 생성하고, 런타임에서 기존 background renderer가 화면 비율에 맞춰 표시하는 방식은 유지한다. 각 variant의 전체 평균 밝기와 기존 텍스트·게이지 대비를 프리뷰에서 확인한다.
- Apple HIG는 밝은 콘텐츠 위의 `clear` Glass 요소에 대해 필요하면 요소 뒤에 35% 어두운 dimming layer를 고려하고, 텍스트가 많거나 배경이 읽기 어려우면 `regular` Glass를 쓰라고 안내한다. 현행 엔진의 blur/gradient는 자체 시각 구현이므로 새 배경 프리뷰에서 최소·최대 투명도와 고대비 설정을 함께 검수한다. 실제 대비 문제가 재현될 때만 renderer에 surface-local dimming을 추가한다.

Android Auto 공식 문서는 투사 앱 설계 기준이다. 이 Dashboard는 Android API 17 대상 Canvas UI이므로 Compose Material 3 라이브러리나 최신 Android Dynamic Color를 런타임 의존성으로 가져오지 않는다. 필요한 디자인 역할을 기존 JSON 스키마와 Java 렌더 계약으로 번역한다. 신뢰할 수 있는 색 대비 숫자나 터치 치수는 적용 대상/단위가 동일한 공식 문서에서 확인한 경우에만 규격에 추가한다.

## 대상 코드와 변경 경계

- 앱 테마 자산: `apps/dashboard/src/0x1101L/assets/themes/`
- 테마 엔진: `apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/`
- 기존 공용 재질 처리: `ThemeSurface`와 `LiquidGlassEngine`; 기존 `ThemeSpec`, catalog, 상속/검증 경로를 확장한다.
- 현재 버전 규격: `docs/theme_engine_v3.md` 및 `theme/schema/`의 실제 schema가 정본이다. 이 문서는 새 schema 버전을 임의로 만들지 않는다.
- 조사 배경: [기존 테마 엔진 조사 보고서](THEME_ENGINE_REPORT_20261117.md), [v3 규격](theme_engine_v3.md), [구현 기록](THEME_ENGINE_IMPLEMENTATION_20261117.md).

CoWork 테마 엔진은 독립 렌더 스택이나 두 번째 재질 구현으로 분기하지 않는다. 테마 공통 인터페이스가 여러 surface 역할에 같은 토큰·Canvas 계약을 제공하도록 기존 공용 경로를 확장한다. `LiquidGlassEngine`은 glass 효과 구현자로 유지하고 새 테스트 테마는 flat/glass 등 현재 지원 스타일을 조합해 엔진 계약을 검증한다. 기존 테마의 렌더링 결과가 바뀌지 않게 한다.

## 구현 규격

### 테스트 테마

1. catalog에 안정적인 신규 ID `material_auto_cowork`를 등록하고, 표시명은 번역 규칙에 맞춰 `Material Auto`로 둔다. 기존 테마 ID와 사용자 설정 키는 변경하지 않는다.
2. manifest와 theme JSON은 v3 규격을 따르며 **`extends`를 선언하지 않는다.** 팔레트, 타이포그래피, 간격, 재질, surface 역할, shell, overview 및 F1 레이아웃을 자체 파일에 완결되게 선언한다. 엔진 기본 폰트는 글꼴 자산이 비었을 때의 플랫폼 fallback일 뿐, Original 스타일을 상속하는 수단으로 삼지 않는다. 스키마 허용 목록 밖의 필드, 임의 실행 코드, 외부 URL, 동적 네트워크 자산을 추가하지 않는다.
3. 색상은 어두운 배경/표면 계층, 읽기 쉬운 전경색, 하나의 주 강조색, 구분되는 경고/위험/미수신 상태색으로 구성한다. 상태 기호와 문자열은 기존 렌더러 정책을 보존한다.
4. 기존 v3가 지원하는 폰트 역할·배율을 우선 사용한다. 새 폰트를 번들링할 때만 라이선스, 파일 크기, 한글/영문/숫자/단위 글리프와 API 17 로딩을 확인한다. 확인되지 않은 서체를 Google 공식 서체라고 표기하지 않는다.
5. 표면과 카드에는 현재 엔진의 `surfaceStyles`, radius, fill, border, backdrop, spacing/layout 필드를 사용한다. 재질이 없는 새 필드가 꼭 필요하다면 먼저 schema·파서·fallback을 함께 추가하고 문서화한다.
6. **모든 테마의 Overview는 5열×4행 단일 화면으로 고정한다. 4×3을 포함해 다른 격자 크기를 만들지 않으며, 이전/다음 Overview 페이지나 페이징 상태를 추가하지 않는다. 20개 슬롯 안에서 모든 항목을 구성한다.** 이는 테마 작업의 필수 규칙이다.
7. 테스트 테마는 주간/야간에 해당하는 밝기 조건, 기본/선택/비활성 버튼, 정상/경고/무효/미수신 값 상태를 확인할 수 있는 프리뷰 구성을 제공한다. 프리뷰 값은 합성 데이터라고 표시한다.

### CoWork 공용 엔진

- 엔진 입력은 검증된 불변 `ThemeSpec`과 현재 화면/상태이며 JSON 파싱·병합·리소스 읽기는 프레임 그리기 밖에서 완료한다.
- 색/표면/형태/폰트 역할을 일관된 accessor로 제공한다. 기존 렌더 경로가 이미 가진 역할은 재사용하고 동일한 값을 다른 이름으로 중복 저장하지 않는다.
- 렌더와 터치 판정은 같은 레이아웃 geometry를 사용한다. 테마 변경으로 hit 영역, 메뉴 진입점, 미디어 조작, 저장된 사용자 게이지 설정이 달라지지 않도록 한다.
- theme asset, Typeface, Bitmap의 로드와 캐시는 적용/화면 전환 시 수행한다. 프레임마다 파일 접근, Typeface 생성, JSON 파싱 또는 bitmap decode를 하지 않는다.
- 누락/손상/미지원 값은 현재 v3 계약대로 필드 기본값 또는 canonical Original로 복구한다. 테마 로딩 실패가 화면 시작이나 기존 테마 선택을 막지 않는다.
- `Original` 출력과 기존 테마 4종은 현재 기준선과 비교한다. 테스트 테마만 새 외형을 내고, 다른 테마의 픽셀 차이는 의도된 기존 수정과 분리해 설명한다.
- API 17 호환성을 유지한다. blur/gradient/alpha 등은 기존 호스트·기기 경로에서 지원 여부와 프레임 비용을 확인하고, 미지원 효과는 명확한 정적 fallback을 둔다.

### 미리보기와 UI 안전성

- 앱의 실제 테마 선택 진입점을 유지한다. draft 미리보기는 저장된 선택값을 변경하지 않고 Apply만 저장한다. Cancel/뒤로가기는 draft를 버린다.
- 고정 telemetry fixture만 사용하고 차량 리스너, 미디어 수신기, 명령 송신을 프리뷰에 연결하지 않는다.
- Overview는 모든 테마에서 5×4 한 화면을 유지한다. 모든 카드가 20개 슬롯에 들어가야 하며 렌더와 hit-test의 geometry가 일치해야 한다.
- 800×480 출력에서 한국어/영어, 긴 라벨, 숫자와 단위, 경고/무효/미수신, 선택 상태를 확인한다. 어두운 환경과 밝은 주변광을 각각 모사한 대비 점검 이미지를 남긴다.
- 과한 애니메이션, 깜박임, 빠른 반복 전환은 추가하지 않는다. 상태 변화는 기존 갱신 빈도와 눈에 띄는 의미 있는 차이를 유지한다.

## 완료 기준

1. catalog가 새 테마를 읽으며 기존 theme ID와 설정 키가 유지된다.
2. 신규 테마 manifest에 `extends`가 없고, 자체 색·타이포그래피·간격·재질·레이아웃을 명시했음을 테스트한다. Original 값을 상속해 외형을 구성하지 않는다.
3. v3 schema/런타임 검증, 기존 테마의 상속 경로, 잘못된 manifest fallback 테스트가 통과한다.
4. 새 테마의 색·surface·타이포그래피가 공용 엔진 경로에 적용되고 기존 엔진/렌더러 구조를 재사용한다.
5. 테마 선택 Apply/Cancel, 이전 테마 복원, 프리뷰의 합성 데이터 표시가 동작한다.
6. Original 기준 화면과 기존 테마의 회귀 테스트가 통과하고, 신규 테마는 최소 Overview, F1, gauge, graph, settings 및 state fixture에서 프리뷰된다.
7. API 17 대상 compile/build 결과와 실제 기기 결과를 구분해 기록한다. 기기 검증이 없으면 미검증으로 명시한다.
8. 구현 기록에 변경 파일, 실행한 테스트와 결과, 남은 위험/미검증 항목을 기록한다. 커밋·푸시·APK 배포는 별도 요청 없이는 하지 않는다.

## 공식 참고 자료

- [Material 3 design system](https://m3.material.io/)
- [Material 3 color system](https://m3.material.io/styles/color/overview)
- [Android Auto design system: color](https://developers.google.com/cars/design/android-auto/design-system/color)
- [Android Auto design system: typography](https://developers.google.com/cars/design/android-auto/design-system/typography)
- [Android Auto design system: sizing](https://developers.google.com/cars/design/android-auto/design-system/sizing)
- [Android Auto design system: layout](https://developers.google.com/cars/design/android-auto/design-system/layout)
- [Google: Android in cars updates (2026-05-12)](https://blog.google/products-and-platforms/platforms/android/android-in-cars-updates/)

공식 페이지는 변경될 수 있으므로 구현 시 다시 확인한다. 블로그의 제품 방향 발표와 개발자 가이드의 구체적인 규칙은 근거 수준을 나눠 사용한다.
