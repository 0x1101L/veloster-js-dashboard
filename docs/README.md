# Documentation / 문서 안내

JS·JSN·PD 세 프로필과 통합 앱 목표 및 현재 판정은 연결된 로컬 `@memory` 프로젝트 정본의 색인과 포팅 근거를 따릅니다. `@memory`에는 비공개 연구 기록과 차량 자료가 있어 이 공개 저장소에 포함되지 않습니다. 저장소 단독 사용자는 아래 버전 관리 문서를 기준으로 하며 로컬 정본 링크는 체크아웃에 연결된 경우에만 열립니다. 실제 공개 설치기는 아직 JSN 기준입니다. 세 프로필 통합은 미구현 목표이며, 소스 분기만으로 실차 호환이 확인된 것은 아닙니다.

[English home](../README.md) · [한국어 홈](../README_ko.md)

For the existing JSN app, this guide covers compatibility, ADB, installation, and entry shortcuts. Those instructions do not establish JS support. Upstream documentation and paths are retained. The local `@memory` research corpus is private and is not required to use this repository.

## Install and use / 설치·사용

| Step | English | 한국어 |
| --- | --- | --- |
| 01 · Supported target and verified behavior | [Status](STATUS.md) | [확인 범위](STATUS.md#한국어) |
| 02 · ADB, engineering mode and USB — upstream JSN baseline | [Connection](ADB_EN.md) | [연결](ADB.md) |
| 03 · Install, update, uninstall and recover — upstream JSN baseline | [Installation](INSTALL_EN.md) | [설치·복귀](INSTALL.md) |
| 04 · Open apps and configure shortcuts | [My Apps](MY_APPS.md), [Quick entry](QUICK_ENTRY.md) | [내 앱](MY_APPS_ko.md), [바로가기](QUICK_ENTRY_ko.md) |
| 05 · Choose a display language | [Languages](LANGUAGES.md) | [언어](LANGUAGES.md#한국어) |
| Optional · Exact-build OEM menu entry | [Menu entry](MENU_ENTRY.md) | [OEM 메뉴](MENU_ENTRY_ko.md) |

[Detailed limitations / 상세 한계](LIMITATIONS.md) · [Release history / 배포 기록](RELEASE.md) · [Signing and dependencies / 서명·의존성](../NOTICE.md)

## Development and research

| Document | Purpose / 용도 |
| --- | --- |
| [Build and test](BUILD.md) | Existing upstream build instructions; they do not validate the new JS/PD profiles / 기존 upstream 빌드 안내이며 JS·PD 신규 분기 검증과는 별개 |
| [Research walkthrough](RESEARCH.md) · [한국어](RESEARCH_ko.md) | Firmware research and the standalone-app approach / 분석 과정 |
| [Performance dashboard](PERFORMANCE.md) | Unreleased optional page and actual-code previews / 새 선택형 화면·미리보기 |
| [Private error records](DIAGNOSTICS.md) | Bounded app diagnostics and read-only retrieval / 오류 기록·읽기 |
| [JS telemetry capture handoff](JS_DEBUG_CAPTURE.md) | Structured sample format and offline extraction for a separate debugging agent / JS 값 추출·인계 |
| [JS diagnostics screen](JS_DIAGNOSTICS.md) | HUD/navigation, signal reception and Wi-Fi/DCT observations / JS 디버깅 화면·수신 상태와 경과 |
| [JS debugging guide](JS_DEBUGGING_GUIDE.md) | ADB tools, in-app instrumentation, host tests and evidence boundaries / ADB 도구·앱 내부 계측·호스트 테스트의 구분 |
| [JS HUD research](JS_HUD_RESEARCH.md) | OEM components, MICOM path and applied observations / 순정 구성요소·MICOM 경로·적용 내역 |
| [CarPlay navigation research](CARPLAY_NAVIGATION_DASHBOARD_RESEARCH.md) | Current-source feasibility and evidence review / 현 소스 기준 구현 가능성·근거 검토 |
| [CarPlay TBT test plan](CARPLAY_TBT_TEST_PLAN_20261115.md) | Ordered read-only ADB preflight and gates for any later TBT test / ADB 사전 확인 순서와 후속 TBT 시험 게이트 |
| [NEMESIS review](NEMESIS_REVIEW.md) | Platform differences and reusable ideas / 적용 가능성·참고 코드 |
| [Experiments](../experimental/README.md) | AVRCP and SystemUI work outside released apps / 미배포 실험 |
| [Installation audit, 2026-10-26](INSTALL_AUDIT.md) | Historical v9 entry-point findings / 당시 진입점 개선 기록 |
| [Release metadata](../release.json) · [Validation history](../VALIDATION.json) | Artifact identities and recorded check scope / 산출물·검사 이력 |

Current verification is maintained in [STATUS.md](STATUS.md). Dated release/audit records describe their original versions and are not evidence that newer work passed vehicle checks.

현재 배포본의 확인 범위는 [STATUS.md](STATUS.md#한국어)를 기준으로 합니다. 연구는 주제별 최신 판정 문서에 모으고, 날짜별 시험·수집 기록은 해당 문서의 출처로 남깁니다. 날짜가 붙은 배포·감사 기록은 당시 버전의 이력입니다. 준비·호스트 검사·실차 설치·재부팅 검증을 구분합니다. 연구 문서 안내는 [RESEARCH.md](RESEARCH.md)와 [RESEARCH_ko.md](RESEARCH_ko.md)를 따릅니다.
