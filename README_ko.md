# Veloster Dashboard — 독립 차량 대시보드

*from [Veloster N Dashboard](https://github.com/reikop/veloster-n-dashboard)*

[English](README.md) | **한국어**

Veloster JS·JSN과 i30 PD 순정 헤드유닛에서 사용할 대시보드 프로젝트입니다. 앱 실행 도우미 **‘내 앱’**도 포함합니다. 한국어·영어를 지원합니다. 현재 공개 설치기와 배포본은 JSN 기준이며, JS·JSN·PD 통합 설치는 목표이지 아직 제공되는 기능이 아닙니다.

[다운로드](#다운로드) · [설치](docs/INSTALL.md) · [호환성·확인 범위](docs/STATUS.md#한국어) · [문서](docs/README.md)

<img src="docs/images/dashboard-overview-in-car.jpg" alt="국내 벨로스터 N 순정 내비에서 실행한 N 계기판 전체조망" width="480">

차주가 촬영한 실차 화면입니다. 수신하지 못한 값은 `-`로 표시합니다. 위치·촬영기기 메타데이터를 제거했으며 사진 픽셀은 변경하지 않았습니다.


JS 소스는 PD 디코더를 복제한 전용 처리 경로와 JS 전용 화면을 사용합니다. 무효 입력 시 느린 값은 마지막 유효값을 최대 60초 동안 `LAST`와 경과 시간으로 표시합니다. JS 외기온과 TPMS는 시간 경과로 만료하지 않고 시동 상태 초기화까지 유지합니다. 빠르게 변하는 값은 2초 후 만료되며 보관값을 표시하지 않습니다. 시간은 앱의 임시 정책이며 순정 규격으로 확인된 값이 아닙니다. JSN·PD 기존 동작은 유지합니다. 아직 설치하지 않은 소스이며 실차 확인이 필요합니다.

JS에서 유효 단수를 수신하면 숫자 칸에는 실제 단수만 표시하고, 자동(D)·시퀀셜(M) 모드는 별도로 표시합니다. JS 시퀀셜 신호 매핑은 실차 확인이 필요합니다.

JS 전체 조망의 오일 온도 칸은 홀수단·짝수단 클러치 온도와 세로 막대 두 개를 표시하는 DCT 온도 카드로 바뀝니다. 화면과 응답 해석은 소스에 반영했으며, 실차 진단 수신은 JS에서 검증하기 전까지 연결하지 않습니다. 값이 없으면 `—`로 표시합니다.

## 기능

| N 계기판 | 내 앱 |
| --- | --- |
| F1 화면·20개 카드 전체조망 | 계기판·설치 앱 실행 |
| 편집 가능한 6칸/12칸 게이지·그래프 | 선택형 가장자리 버튼·알림 바로가기 |
| RPM·속도·수신 가능한 온도·네 바퀴 TPMS 공기압 | 엔지니어링 모드·USB 디버깅·Android 도구 |
| 미디어 제어·음원에서 제공하는 앨범아트 표시 | Bluetooth 페어링·확인창을 거치는 재시작 |
| 화면 배치 저장·한국어/영어 선택 | 앱 목록 편집·한국어/영어 선택 |

차량 데이터는 기존 수신 경로를 사용하며 ECU 진단 요청을 새로 보내지 않습니다. 미수신·만료 값은 알 수 없음으로 남깁니다. Bluetooth 앨범아트 수신·배기온·e-LSD는 미해결이며 무선 CarPlay·자동 업데이트는 구현하지 않았습니다. [상세 한계](docs/LIMITATIONS.md)

## 차량 프로필과 출처

| 차량 | 코드네임 | `Build.PRODUCT` | 헤드유닛 소프트웨어 버전 |
| --- | --- | --- | --- |
| Veloster N | JSN | `jsn_kr` | SOP V141 · 2026-01-14 · STD_H |
| Veloster | JS | `js_kr` | SOP V141 · 2026-01-14 · STD_H |
| i30 | PD | `pd_kr` | SOP V141 · 2026-01-14 · STD_H |

표의 읽기 쉬운 버전은 차종별 `*.KOR.SOP.V141.260114.STD_H` 소프트웨어 식별자를 줄여 쓴 것입니다. 소스의 프로필 판별은 프로젝트 메모리에 기록한 정확한 incremental 빌드값을 사용합니다. JS 화면은 작업 완료 전까지 **Veloster (WIP)**로 표시합니다. 현재 소스 분기는 호스트·정적 검사만 했으며 설치 호환성을 보증하지 않습니다.

- **JSN 앱 원본:** [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard)
- **PD 프로필·신호표 참고 (PD-1):** [Linuchan5468/i30-pd-dashboard](https://github.com/Linuchan5468/i30-pd-dashboard). JS 디코더도 이 PD 신호표를 출발점으로 삼습니다.
- **JS 차량 식별 근거:** 사용자가 제공한 헤드유닛 사진과 일치하는 로컬 펌웨어 카탈로그입니다. 펌웨어 파일은 이 저장소에 포함하지 않습니다.

세부 근거와 결정은 로컬 프로젝트 메모리 링크의 [색인](@memory/_index.md)을 참고하세요.

JS 시험 빌드 런타임에서 PD 기준 공용 개요 레이아웃의 리매핑 신호를 역조회하지 못해 화면 렌더링 예외가 발생한 문제를 수정했습니다. versionCode 7 수정본을 같은 JS 헤드유닛에 설치해 짧게 실행했고 프로세스가 유지됐습니다. 장시간 동작과 신호 정확도는 아직 확인하지 않았습니다.

## 호환성

기존 설치기 지원 대상은 **국내 벨로스터 N JSN**, product `jsn_kr`, **Android 4.2.2 / API 17**, incremental `eng.vmadmin.20250902.081552` (`250902`, `STD_H`)입니다. 계기판 실행에는 순정 `automotive` 라이브러리가 필요합니다. JS·PD 소스 분기가 있다는 사실은 해당 기기의 설치·실차 호환성 확인을 뜻하지 않습니다. 다른 차종·지역·펌웨어·일반 Android 기기의 호환성은 확인하지 않았습니다.

**2026-10-27:** 공개 N 계기판 v6·내 앱 v10 설치 후 APK를 다시 읽어 해시 일치를 확인했습니다. 사용자는 HOME 가장자리 버튼으로 내 앱을 다시 여는 것도 확인했습니다. **완전 전원 종료 후 복원·장시간 동작은 미검증**입니다. 이후의 비공개 시험본은 이 배포본과 구분합니다. [근거·현재 확인 범위](docs/STATUS.md#한국어)

## 다운로드

**[v2026.10.26-i18n 전체 ZIP 받기](https://github.com/reikop/veloster-n-dashboard/releases/download/v2026.10.26-i18n/veloster-n-dashboard-v2026.10.26-i18n.zip)** — 서명된 앱 두 개, 설치 도구, 안내를 포함합니다. 사전 배포 상태를 유지합니다.

| 현재 APK | 버전 |
| --- | --- |
| [N 계기판](apks/N-Dashboard-v6-API17.apk) | v6 · 1.5-i18n |
| [내 앱](apks/My-Apps-v10-API17.apk) | v10 · 1.9-i18n |

설치에는 전체 ZIP이나 저장소를 사용하세요. APK를 SD 카드에 복사하는 것만으로 설치되지 않습니다. 날짜가 붙은 ZIP의 문서는 배포 당시 상태이며 이후 검증은 이 저장소에 반영합니다. [이전 버전·배포 기록](docs/RELEASE.md) · [파일 해시·패키지 정보](release.json)

## 빠른 시작

설치·화면 조작은 정차 상태에서 진행합니다. 최초 설치에는 Python 3.9 이상과 동작하는 [ADB 연결](docs/ADB.md)이 필요합니다.

1. 전체 묶음을 풀고 프로젝트 폴더에서 터미널을 엽니다.
2. 파일을 검사하고 `adb devices -l`에 표시된 본인 기기의 일련번호로 설치합니다.

   ```sh
   python3 scripts/verify_release.py
   python3 scripts/install.py --serial YOUR_DEVICE_SERIAL --parked
   ```

3. 내 앱에서 **실행 바로가기 → 가장자리 버튼 + 알림 → 저장**을 선택합니다.
4. PC를 분리하기 전에 **HOME → 가장자리 버튼 → 내 앱 → N 계기판**을 확인합니다.
5. 해당 차량의 완전 종료·재시동 후 재실행은 따로 확인합니다.

설치기는 매번 새 APK 백업을 만들고 설치된 파일을 다시 검증합니다. 설치·실행 없이 점검·백업만 하려면 `--parked` 대신 `--check`를 사용합니다. 실행 요청 수락과 실제 화면 표시는 별개입니다. 바로가기 기본값은 OFF이며 순정 메뉴는 변경하지 않습니다. [설치·복귀](docs/INSTALL.md) · [바로가기 문제 해결](docs/QUICK_ENTRY_ko.md)

설치 후 평소 사용에는 PC가 필요하지 않습니다. 가장자리 버튼과 알림 모두 사용할 수 없다면 [지원 빌드 전용 OEM 메뉴 절차](docs/MENU_ENTRY_ko.md)를 선택적으로 확인하세요.

## 문서

| 순서 | 한국어 | English |
| --- | --- | --- |
| 01 · 호환성·검증 | [확인 범위](docs/STATUS.md#한국어) | [Status](docs/STATUS.md) |
| 02 · ADB·USB 연결 | [ADB 연결](docs/ADB.md) | [ADB](docs/ADB_EN.md) |
| 03 · 설치·업데이트·복귀 | [설치](docs/INSTALL.md) | [Install](docs/INSTALL_EN.md) |
| 04 · 내 앱·바로가기 | [내 앱](docs/MY_APPS_ko.md) · [바로가기](docs/QUICK_ENTRY_ko.md) | [My Apps](docs/MY_APPS.md) · [Quick entry](docs/QUICK_ENTRY.md) |
| 05 · 언어 설정 | [언어](docs/LANGUAGES.md#한국어) | [Languages](docs/LANGUAGES.md) |
| 06 · 개발·연구 | [개발·연구 색인](docs/README.md#development-and-research) | [Documentation index](docs/README.md#development-and-research) |

## 개발

[`apps/`](apps/)는 두 앱, [`shared/`](shared/)는 계기판 UI·데이터 처리 소스입니다. [`compile-stubs/`](compile-stubs/)는 빌드 전용 OEM 선언이고, [`scripts/`](scripts/)·[`tests/`](tests/)는 빌드·무결성·호스트 검사 도구입니다. [`experimental/`](experimental/README.md)는 별도 로컬 연구 안내입니다. 해당 실험 소스는 이번 공개 변경에 포함하지 않습니다.

[빌드·테스트](docs/BUILD.md) · [개발 과정](docs/RESEARCH_ko.md) · [JS 값 수집 인계](docs/JS_DEBUG_CAPTURE.md) · [NEMESIS 검토](docs/NEMESIS_REVIEW.md)

자체 코드·문서는 [MIT](LICENSE)입니다. [의존성·서명 안내](NOTICE.md)를 참고하세요. 공개 AOSP 시험 인증서는 독점 배포자 신원이나 OTA 키가 아닙니다. 소스 빌드 결과는 서명 전 APK입니다. 제조사 펌웨어·APK, 개인 로그·식별자·개인키는 배포하지 않습니다. 제조사와 무관한 비공식 프로젝트입니다.
