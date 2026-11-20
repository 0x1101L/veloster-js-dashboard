# Material Auto · CoWork 테마 구현 기록

- 작성일: 2026-11-19 (KST)
- 브랜치: `dev-theme`
- 기준 규격: [Material Auto 테마 구현 규격](MATERIAL_AUTO_THEME_RESEARCH_SPEC_20261119.md), [Theme Engine v3](theme_engine_v3.md)
- 배포 상태: 커밋·푸시·APK 패키징·설치 없음

## 정식 테마 상태

`material_auto_cowork`는 테스트용 임시 항목이 아니라 번들 테마 카탈로그의 정식 기본 제공 테마이며, 설정의 테마 선택 화면에서 `Material Auto`로 선택할 수 있다. Material Auto 테스트 테마를 정식 카탈로그 항목으로 승격하는 작업은 이미 카탈로그 등록으로 완료된 상태다. 이는 소스 내 정식 등록 상태를 뜻하며, APK 배포·설치나 실차 검증을 뜻하지 않는다.

Java2D 호스트 프리뷰 캡처는 [Material Auto 화면 모음](../theme/theme-previews/material-auto/)에 둔다. 이미지는 현재 소스에서 다시 렌더링한 합성 데이터이며 차량 기기 캡처가 아니다.

## 변경

- v3 catalog에 `material_auto_cowork` ID와 `Material Auto` 이름을 추가했다. manifest에는 `extends`를 두지 않고 자체 색상·폰트 위계·간격·표면·shell·F1·overview 레이아웃 파일을 선언한다.
- 어두운 화면/표면 계층, 밝은 전경색, 파란 강조색과 별도 warning/critical/unavailable 토큰, 고정 5×4 단일 화면 Overview, 왼쪽 navigation dock, 자체 F1 grid를 구성한다. 시스템 기본 sans 글꼴을 명시적으로 선택하며 새 폰트·외부 자산은 추가하지 않았다.
- Android Auto의 시각 요소만 참고해 메뉴 폭을 92에서 80 논리 단위로 조정하고 좌우 내부 여백을 10으로 줄였다. 공용 dock 표면은 flat 재질로 유지하며, 시계는 엔진의 상단 숫자 표시를 사용한다. 실제 Dashboard 화면으로 연결되는 기존 메뉴 아이콘만 유지하고, 이 앱에 없는 런처·어시스턴트·알림 동작은 추가하지 않는다.
- 바탕은 `#FF090D12` 단색, 표면은 `#FF171B20`/`#FF222931`로 계층화하고 기존 Material 파랑 강조색과 상태색을 유지한다. 스캔라인·장식 그라데이션·기본 배경 이미지는 사용하지 않는다. Overview의 5×4 단일 화면과 카드 배치는 유지한다.
- 설정 포인트 컬러는 엔진별로 분리했다. Material Auto에는 key-color 스타일의 Blue/Teal/Purple/Green/Amber/Rose를, Liquid Glass 테마에는 Apple HIG의 iOS named system colors Blue/Indigo/Purple/Pink/Orange/Green을 제공한다. Liquid Glass의 light/dark RGB는 Apple HIG가 디자인 참고용으로 공개한 default system color 값이며 UIKit dynamic color 구현이나 Apple이 정한 Liquid Glass 팔레트가 아니다. 기본값은 각 테마 manifest 색을 복원하고 warning/critical/unavailable 색은 유지한다.
- 공용 `ThemeSpec`에 v3 색 토큰을 Material 역할로 읽는 accessor를 추가하고 `ThemeSurface` 메뉴 보조색에서 재사용했다. 기존 저장값과 의미 토큰은 변경하지 않았다. 표면은 기존 역할별 `ThemeSurface` 경로를 거쳐 그려지며 별도 렌더러를 만들지 않았다.
- `tools/js_ui_preview.py`가 앱의 번들 catalog/manifest를 읽게 해 Original, Aurora Focus, Material Auto를 동일한 renderer 및 고정 synthetic fixture로 비교한다.
- `ThemeManifestTest`는 신규 테마가 `extends` 없이 자체 토큰·타이포그래피·간격·shell·레이아웃을 읽는지, 고정 5×4 한 화면에서 신호/hit-test를 유지하는지, 카드의 `page` 속성을 거부하는지 확인한다. 전역 wallpaper 갤러리 기대 개수는 신규 테마의 기본 색상 항목을 반영한다.

## 검증

- `python3 scripts/test.py` — PASS, synthetic Java host test program 24개. v3 자산 로딩·기존 테마 회귀 및 새 semantic color accessor 포함.
- `python3 tests/test_theme_v3_shell.py` — PASS. 실제 DashboardView 호스트 대역에서 custom Overview 5×4의 카드 순서·span·단일 화면 표시와 터치 흐름을 확인했다.
- `python3 tests/test_js_diagnostics_renderer.py` — PASS.
- `python3 tools/js_ui_preview.py --check` — PASS, 870개 화면 조합. JS·JSN·PD·CN7, 지원 페이지, waiting/received/invalid/held/expired 상태, 한국어·영어, 세 테마를 실제 Java 렌더러로 렌더링했다. 프리뷰에는 `합성 데이터 · 미리보기` 표기를 넣었다. Material Auto의 800×480 Overview 출력을 임시 디렉터리에서 눈으로 확인했다. 나머지 페이지는 렌더 완료/크기 및 테마 선택 검증으로 확인했으며 개별 캡처를 모두 육안 검토한 것은 아니다.
- `python3 tests/test_performance_renderer.py` — FAIL. JS F1 mode 16 + expanded media 경로의 색상 픽셀 검사에서 기대 `0xffa4d9ef`, 관측 `0xff101315`. 이는 F1 렌더 검증이며, 고정 5×4 Overview 검증은 위 테스트에서 통과했다.
- 테마 샘플 JSON, overview schema, 제안 SVG 구문 검사 — PASS.
- `git diff --check` — PASS.

## 미검증과 차이

- 호스트 Java/Java2D 결과는 합성 데이터 검증이다. Android 4.2.2/API 17 전체 빌드, 실제 Canvas/Typeface 동작, 대상 기기 터치·가독성·프레임 비용 및 실차 동작은 검증하지 않았다.
- 규격에 열거한 낮/밤 대비 캡처와 장치별 터치 겹침 점검은 수행하지 않았다. Android Auto 투사 앱 가이드의 dp 기준을 800×480 좌표에 적용하지 않았다.
- 기존 v3 `ThemeSurface`/`LiquidGlassEngine`/`ThemeSpec` 계약은 요청 기능(역할 기반 재질과 semantic 색 역할)을 이미 제공해 재질 파서나 스키마는 확장하지 않았다. 기존 경로를 새 테마 설정으로 구동하고 semantic color accessor만 추가했다. 새 효과나 동적 동작은 없다.
