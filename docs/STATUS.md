# Compatibility and verification / 호환성·확인 범위

[Documentation](README.md) · [한국어](#한국어)

Documentation reviewed **2026-11-02**. Vehicle evidence through **2026-10-28**. No new vehicle session was performed for this documentation update.

## Supported target

| Item | Target |
| --- | --- |
| Vehicle | Korean Veloster N JSN |
| Software shown by the vehicle | `JSN.KOR.SOP.V141.250902.STD_H` |
| Installer identity | `ro.product.name=jsn_kr` |
| Android | 4.2.2 / API 17 |
| Incremental build | `eng.vmadmin.20250902.081552` |
| Platform | Telechips `tcc893x`, factory `automotive` library |

The installer checks the live product, SDK and incremental build. A generic firmware `build.prop`, matching Android version, or a Gen5W label does not establish compatibility. Other models/regions/builds are unverified; this project does not install NEMESIS.

## Released apps

| Artifact | Evidence | Still unverified |
| --- | --- | --- |
| N Dashboard v6 | 2026-10-27 guided install; readback SHA-256 matched the released APK | Full power-off restoration and extended reception |
| My Apps v10 | Same installation/readback check; owner separately confirmed HOME edge-button reopening | Shortcut restoration after full power-off |
| Vehicle signals | Earlier v5 initial telemetry; later research-car checks include RPM, temperature and TPMS reception | Active ABS/TCS/ESC transitions, exhaust temperature and e-LSD |

The private installer receipt records `apps_verified_launch_accepted`. Its screen/reboot flags remain false: the separate owner screen observation is not a machine-recorded acceptance. No emulator validation is claimed. Private device identifiers and logs are not published.

Readback hashes:

```text
N Dashboard v6  e2bf05c2fa955b7a60dc74705e48de67582956a9e3c4ee45473b88a9093830e2
My Apps v10     6351e5d0806fecf2cf5e5901a67c33a7a5ab337513dce1a62b30c9318d352680
```

These match [release.json](../release.json). The `v2026.10.26-i18n` archive remains unchanged and retains its original prerelease status and documentation. Later vehicle observations do not certify every feature.

## Unreleased source changes (2026-11-02)

[Code adapted after NEMESIS review](NEMESIS_REVIEW.md#implemented-in-our-code-unreleased): OEM screen-state handling and opt-in QuickBoot restoration in My Apps; synchronized installation receipt writes; complete missing-subscriber retries and bounded app-private error records. An optional [Performance page](PERFORMANCE.md) was also added from the owner’s visual reference. 50 Python tests, 13 Java host programs and unsigned API17-targeted builds of both apps passed. This is not a new vehicle-validated APK release. Published v6/v10 bytes and their verification scope above remain unchanged.

## Experimental work

The research vehicle used a private Cover Art dashboard trial after the public v6 installation. Its later screenshots/logs must not be presented as an unchanged public v6 test. Bluetooth Cover Art remains blocked at SDP discovery; image transfer/display has not succeeded. Fixed-volume experiments failed and the prior Bluetooth trial was restored. SystemUI/fullscreen/bottom-bar candidates remain uninstalled. None is a released feature. [Experimental projects](../experimental/README.md)

## 한국어

**11/02 코드 반영:** 내 앱의 OEM 화면 복귀·QuickBoot 방송 처리와 설치 기록 저장을 보강했습니다. 추가로 차량 구독 재시도 누락·실패 정리·앱 내부 오류 기록을 반영하고 선택형 퍼포먼스 화면을 추가했습니다. Python50·Java13 검사와 두 앱 API17 대상 미서명 빌드는 통과했으며 새 소스의 실차 시험은 미실시입니다. 배포 v6/v10 APK는 그대로입니다.

문서 점검일은 **2026-11-02**, 실차 근거는 **2026-10-28까지**입니다. 이번 문서 정리에서 차량을 새로 연결하거나 시험하지 않았습니다.

- 대상은 국내 벨로스터 N JSN의 `250902 / STD_H`, Android 4.2.2/API17, `tcc893x`입니다. 설치기는 실차의 `jsn_kr`, API17, `eng.vmadmin.20250902.081552`를 대조합니다. 공용 펌웨어의 `build.prop`만으로 차량을 식별하지 않습니다.
- **10/27 공개 v6·v10 설치 후 readback 해시 일치를 확인**했습니다. 사용자는 HOME 가장자리 버튼으로 내 앱 재실행을 별도로 확인했습니다. 설치 영수증의 화면·재부팅 확인 플래그는 false이므로 사용자 관찰과 자동 검사 결과를 합치지 않습니다.
- **완전 전원 종료 후 복원·장시간 수신·ABS/TCS/ESC 실제 개입 전환은 미검증**입니다. 배기온·e-LSD는 미확정입니다. 다른 차종·지역·빌드의 호환성을 추가하지 않습니다.
- 이후 연구 차량의 계기판은 비공개 Cover Art 시험본입니다. 해당 로그·화면을 공개 v6 그대로의 실차 검증으로 해석하지 않습니다.
- Bluetooth 앨범아트는 SDP 단계에서 미해결이며 고정 볼륨 시험도 실패했습니다. SystemUI·하단 바 후보는 미설치입니다. 공개 배포 기능과 구분합니다.
- 기존 ZIP·APK는 변경하지 않았습니다. 날짜가 붙은 배포 문서의 “미시험”은 배포 당시 상태이며 이후 확인은 이 문서를 기준으로 합니다. 개인 식별자·실차 원본 로그는 공개하지 않습니다.
