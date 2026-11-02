# Experimental projects / 실험 프로젝트

[Project](../README.md) · [문서](../docs/README.md) · [Current verification / 확인 범위](../docs/STATUS.md)

This directory publishes research documentation only. The AVRCP/Bluetooth and SystemUI experimental sources remain in the local research workspace and are not included in this update. Paths and build commands in the research notes refer to that workspace. These projects are separate from the signed release APKs and the normal installer. Source/build readiness, device installation and working vehicle behavior are different stages. 연구 소스와 빌드 산출물이 있다는 사실을 실차 기능 완성으로 해석하지 않습니다.

| Project | Contents | Status through 2026-10-28 |
| --- | --- | --- |
| [AVRCP Cover Art](../docs/EXPERIMENTAL_AVRCP.md) | Java receiver, native transport, private OEM integration tools | Private vehicle trial blocked at SDP; no image transfer confirmed |
| [Statusbar / fullscreen media](statusbar-media/README.md) | Media UI and system-layout experiments | New fullscreen/bottom-bar candidates prepared; not installed |

## 한국어

이번 공개 변경에는 연구 안내 문서만 포함합니다. Bluetooth·SystemUI 실험 소스는 로컬에 남겼으며, 연구 문서의 소스 경로와 명령은 해당 작업 공간을 전제로 합니다.

- **앨범아트:** 수신 코어·native 연동·비공개 OEM 시험본 도구입니다. 실차에서는 SDP 단계가 미해결이며 이미지 전송·표시 성공 근거가 없습니다.
- **미디어 바·전체화면:** 화면·시스템 배치 실험입니다. 새 전체화면·전체 하단 바 후보는 미설치입니다.

OEM originals, modified OEM APKs, keys and private captures are not distributed here. Build instructions use privately supplied inputs where required. 일반 설치 명령은 이 실험 코드를 배포하거나 설치하지 않습니다.
