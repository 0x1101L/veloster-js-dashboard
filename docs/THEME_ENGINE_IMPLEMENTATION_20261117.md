# Dashboard 테마 엔진 구현 기록

- 작성일: 2026-11-17 (KST)
- 브랜치 / 시작 HEAD: `dev-theme` / `4e4313316dab1f54ebd45f44a8b99412cb095619`
- 범위: 테마 엔진 계획 1~6단계의 구현과 호스트 검증
- 배포 상태: APK 패키징·설치·배포, 커밋·푸시 없음

## 구현 결과

| 단계 | 결과 |
|---|---|
| 1. 기준선·경계 | 지정 브랜치와 HEAD에서 시작했다. 차량 신호·단위·유효성·경고 판정·사용자 게이지 설정은 테마에서 분리했다. 시작 당시 존재한 미리보기 도구 변경을 보존하면서, 이후 요청에 따라 선택 테마 컨트롤과 렌더 입력만 덧붙였다. 관련 README 안내도 해당 기능에 맞춰 갱신했다. `dist/`, 조사 문서·이미지·macOS 앱 도구의 기존 변경은 보존했다. |
| 2. 계약·저장 | JSON catalog와 manifest를 실제 APK asset 경로에서 읽고 버전, 필수·중복 키, ID, 색, 허용 그리드, 리소스 경로와 JSON 문서 크기를 검사한다. 오류는 canonical Original 하나로 fallback한다. `ThemeSpec`, palette, 저장소, 별도 테마 preference, apply/cancel draft를 연결했다. JSON 중첩 깊이와 숫자 형식도 제한한다. |
| 3. 렌더러 연결 | 공통·JS/JSN/PD overview, F1, 게이지, 그래프, 성능, JS 진단 페이지에 테마 색·패널 모서리·폰트 역할을 전달한다. 테마별 정적 자산은 해당 번들 경로에서 로드하고 인스턴스에 캐시하며, 누락 시 Android 기본 폰트와 배경으로 돌아간다. 기존 신호·단위·표시 문구는 Focus 렌더 검사에서 유지됨을 확인했다. |
| 4. 레이아웃·선택 | Original과 Aurora Focus 모두 기존 5×4 슬롯과 터치 hit-test를 유지한다. 기존 언어 설정 진입에서 Language/Theme를 선택하고, 테마 선택은 초안·미리보기 뒤 Apply에서만 저장한다. Cancel과 시스템 취소는 이전 테마를 복원한다. |
| 5. 고정 데이터 미리보기 | 앱의 `ThemePreviewController`가 JSN·PD·JS별 합성 텔레메트리를 기존 `PitRenderer`/`JsPitRenderer`에 전달한다. 선택 창 안에 “예시 데이터” 표기와 비율 기반 미리보기를 연결했다. Mac Java2D 프리뷰어에도 Original/Aurora Focus 선택을 추가했다. 미리보기는 운영 텔레메트리를 쓰거나 갱신하지 않는다. |
| 6. 회귀 검증 | manifest·리소스·apply/cancel, 화면 레이아웃, 렌더러 상태와 Focus 스타일을 호스트에서 검사했다. 시작 HEAD에서 생성한 Original fixture 이미지 120개와 현재 이미지를 비교해 변하는 시계 영역을 제외한 모든 픽셀이 같음을 확인했다. 실제 Android 17 장치·차량 검증은 수행하지 않았다. |

## 주요 계약

- 번들 catalog는 `apps/dashboard/src/main/assets/themes/catalog.json`, 각 manifest는 `themes/<id>/manifest.json`이다. 현재 catalog는 `original`과 `aurora_focus`를 포함한다. 파서나 manifest 오류가 있으면 두 테마 일부만 노출하지 않고 canonical Original만 제공한다.
- 현재 번들 테마는 모두 기존 5×4를 사용한다. 렌더러는 기존 4×3 geometry와 overview 페이징 경로도 유지하며, catalog 검증은 두 지원 grid만 허용한다.
- 테마 ID는 기존 차량 UI/신호 preference와 분리해 보관한다. Apply에서만 ID를 저장하며 draft 변경과 페이지 이동은 운영 설정을 변경하지 않는다.
- 리소스 로더의 경로는 `themes/<id>/fonts/...` 및 `themes/<id>/backgrounds/...`로 제한한다. 현재 두 manifest의 font/background 값은 비어 있어 실제 추가 폰트·배경 파일은 번들되지 않았다. 로드 성공 경로와 누락 fallback은 호스트 대역으로 검사했다. 글리프 범위·라이선스·Android 폰트 렌더 품질은 확인하지 않았다.
- Preview의 fixture 값은 화면 시안 전용이며 실차 측정값이나 차량 호환성 증거가 아니다.

## 검증 기록

- `python3 scripts/test.py` — PASS, synthetic Java host test program 24개. JSON manifest, canonical Original fallback, 테마 리소스 경로/폰트 fallback·캐시, 선택 draft를 포함한다.
- `python3 tests/test_performance_renderer.py` — PASS. JSN·PD·JS overview 두 페이지와 기존 페이지 상태를 렌더하고, overview/F1/performance/gauge/graph에서 Focus 스타일 변화와 표시 문구·값 보존을 검사한다.
- `python3 tests/test_js_diagnostics_renderer.py` — PASS. 100개 상태·언어·탭 화면과 다섯 진단 탭의 Focus 스타일·문구 보존을 검사한다.
- `python3 tools/js_ui_preview.py --check` — PASS, 두 테마 × 화면·상태·언어 조합 200개를 Java2D 프리뷰어 입력 경로로 렌더했다.
- `python3 -m unittest discover -s tests -p 'test_*.py'` — PASS, Python 테스트 88개.
- Android 35 `android.jar` 및 저장소 OEM compile stubs로 Dashboard/shared Java 전체를 `javac` 컴파일 — PASS. APK를 만들지 않았다.
- 시작 HEAD의 공통 renderer fixture 이미지 20개와 진단 이미지 100개를 비교 — PASS, 시계 텍스트 영역을 제외하고 픽셀 차이 0.
- `git diff --check` — PASS.

검증은 PC Java2D 호스트 shim과 합성 fixture 기준이다. 실제 Android Canvas/Typeface, AlertDialog 배치·터치, API 17 호환, 실차 데이터·성능은 검증되지 않았다. Mac 프리뷰의 테마 값은 앱 manifest와 맞춘 내장 `ThemeSpec`이며, Android asset 로더 동작 검증을 대신하지 않는다.
