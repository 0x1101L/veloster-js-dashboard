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

## Development and project records / 개발·프로젝트 기록

| Document | Purpose / 용도 |
| --- | --- |
| [Build and test](BUILD.md) | Public build prerequisites and commands / 공개 빌드의 준비와 명령 |
| [Common display settings](common_display_settings.md) · [Media bar](f1_media_bar.md) | Shared display, media controls and verification limits / 공통 화면·미디어 제어·검증 한계 |
| [Performance page](PERFORMANCE.md) | Optional page behavior and host previews / 선택형 화면과 호스트 미리보기 |
| [Theme engine v3](theme_engine_v3.md) | Current theme file, inheritance, layout and input contract / 현재 테마 파일·상속·배치·입력 규격 |
| [Theme creation questionnaire](THEME_CREATION_QUESTIONNAIRE.md) | Theme review against the v3 contract / v3 규격에 따른 테마 검수 질문 |
| [Theme implementation record](THEME_ENGINE_IMPLEMENTATION_20261117.md) · [Material Auto](MATERIAL_AUTO_THEME_IMPLEMENTATION_20261119.md) | Dated implementation and host-check records / 날짜별 구현·호스트 검사 기록 |
| [Experiments](../experimental/README.md) | Source and status of local experiments / 로컬 실험의 소스와 상태 |
| [Installation audit](INSTALL_AUDIT.md) | Historical v9 entry-point findings / v9 진입점 감사 이력 |

Detailed research, vehicle evidence, and diagnostic procedures are maintained in the connected private project-memory workspace under its research and documents indexes. Those records are not part of the public checkout. Use [STATUS.md](STATUS.md) for current public verification; dated records do not establish later device or vehicle behavior.

연구·차량 근거·진단 절차는 연결된 비공개 프로젝트 메모리의 연구·문서 색인에서 관리하며 공개 checkout에는 포함하지 않습니다. 공개 확인 범위는 [STATUS.md](STATUS.md#한국어)를 기준으로 합니다. 날짜가 붙은 기록은 이후 기기나 실차 동작을 입증하지 않습니다.
