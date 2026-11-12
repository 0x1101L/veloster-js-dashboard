# JS HUD 데이터 경로와 순정 화면 담당 조사

조사일: 2026-11-12. 이 문서는 분석한 순정 이미지의 정적 근거, 보존 AVD 관찰, Dashboard에 적용한 소스와 미검증 사항을 구분한다. 별도 사용자 HUD Bridge 프로젝트는 순정 구성요소가 아니다.

## 조사 결과

공통 HyundaiB 시스템 이미지의 앱 APK 84개 manifest와 ODEX 117개(클래스 정의 57,003개)의 타입·문자열·참조를 조사하고 후보 본문을 확인했다. 이 범위에서 앞유리 HUD를 그리는 독립 순정 Android 앱은 식별하지 못했다. 다른 이미지나 외부 장치에 그런 소프트웨어가 없다는 증명은 아니다.

| 후보 | 확인 결과 |
|---|---|
| `HKMC_NModeApp` | `IndividualModeType.HUD` 및 `NModeUtils.getHUDEnable/setHUDEnable`은 설정 관련 코드다. `CANInformationAppService.setHUDSettingsValue(int,int)`는 빈 구현이다. HUD 렌더러의 근거는 아니다. |
| `HKMC_SaveFrame`의 `HudLayer` | 확대·축소, 슬라이드쇼 등 사진 뷰어 조작 오버레이다. |
| `HKMC_EngineerMode`의 `HUDtc` | HU DTC, 즉 헤드유닛 진단이다. |
| `VariantCoding$HUD_CODES`, `VARIANT_HUD` | HUD 옵션 표현이며 화면을 그리는 코드의 근거는 아니다. |

## 확인한 데이터 경로

순정 내비의 `NativeCall`은 `com.mnsoft.navi.action.Route_Guide_Status` sticky 방송에 정수 `ROUTE_GUIDE_STATE`를 담는다. 이는 경로 상태 관찰 경로이며 방향·거리 데이터가 아니다. 순정 내비 앱의 TBT 호출은 다음 통신 경로로 이어진다.

```text
NaviClusterManager.sendCluster_TBT_INFO
→ NavigationCluster.updateTBTInfo
→ IClusterService.updateNavigationTBTInfo
→ ClusterService$NaviCluster.updateTBTInfo / sendTBTInfo
→ ClusterService.sendPacket → MicomService.sendPacket
→ MicomPacketRunner.sendPacket / send
→ LocalSocket("micom_mux", ABSTRACT) → OutputStream.write
```

순정 DiPO의 음악 경로에는 `DipoClusterManager` → `ClusterManager.onUpdateClusterInfo` → `IClusterService.updateClusterInfo` → `ClusterService`의 `sendILPacket`/`updateFilenameDelayed` 및 MICOM 패킷 계층이 있다. 제목·트랙·재생 시간용 클러스터 계약이며 mode·통화·전원 등의 조건에 따라 처리된다. 이것만으로 특정 HUD 출력이나 일반 앱의 호출 권한을 보장하지 않는다.

동일 부트클래스 의존성에 맞춘 API17 deodex로 `ClusterService`, `ClusterService$NaviCluster`, `MicomService`, `MicomPacketRunner`의 관련 호출을 복구했다. `ClusterService`는 `Build.PRODUCT`가 `js`로 시작하면 `JSClusterProtocol`을 선택한다. 정적 분기가 실제 차량에서 활성화됐다는 뜻은 아니다.

`system.ext4`의 `/bin/micomd`를 LLVM Thumb 명령으로 정적 분석했다. `socket_local_server`가 `micom_mux`를 열고 소켓 수신 버퍼가 대기열을 거쳐 `write`로 전달된다. 기본 인자 경로는 `/dev/ttyTCC2`를 열어 같은 통신 상태의 fd에 저장한다. 따라서 확인 범위는 **framework → 로컬 소켓 → daemon → tty 쓰기**까지다. `/dev/ttyTCC2`가 HUD 자체라는 근거는 아니며, 후단 버스·제어기·실제 렌더링 펌웨어는 미확인이다. 별도 `/dev/daudio_pinctl` 접근은 UART 출력 장치로 혼동하지 않는다.

업데이트의 `micom/js/micom.inf`에는 `TARGET = MICOM`과 `micom_sw.bin`이 있다. 파일명만으로 해당 바이너리를 HUD 전용 펌웨어로 판정할 수 없다.

## 원본과 확인 범위

원본 펌웨어·추출물은 이 저장소에 포함하지 않는다. 다음 해시는 분석한 로컬 이미지와 그 내부 멤버를 식별한다.

| 원본 또는 이미지 내부 멤버 | SHA-256 |
|---|---|
| `nav_5gen/extracted/system.ext4` | `b67e593dac04b4102a06d0f8eb2a5208724cf1a90243b7b150e55a0ca67dafda` |
| `nav_5gen/avd_lab/oem-system/snapshot.raw.img` | `e11572349f4b1ca5abf28ad56e1dd360e103771466a8b4db873b350efe2557f8` |
| `system.ext4:/framework/automotive.odex` | `20aa2ec4575bb8637437c4d07ad6716bc7c6858b7edcdc364a9e0f58d01739c9` |
| `system.ext4:/framework/automotive-service.odex` | `5023a70da081a5443db9f5ec0c2b9779d3aacc606b6e11fd0a211d7be382c3e8` |
| `system.ext4:/bin/micomd` | `853f1436c0e3f9fd76c8fdf76871dadc622e5c1cfc2e7943d8b9a40b5faf436b` |
| `snapshot.raw.img:/app/HKMC_Navi_ExSL.odex` | `bdf47a84720518696b0c5d4894c2c9378aea3b0ce4b68120d6deb31af067e581` |
| `snapshot.raw.img:/app/HKMC_DiPO.odex` | `f2ee97de9005c0841f5dfb8de5f7090091821c21aa9b4dd1750c514518704789` |
| `snapshot.raw.img:/app/HKMC_NModeApp.odex` | `cbdf134fdd63f18eeb45b50d0446ec1da60c7e69f24caafa80ab1952687f5d27` |
| `snapshot.raw.img:/app/HKMC_SaveFrame.odex` | `c4505ff9868b31c5da2a79a63143dd566cbde27af2184dc8f70f8e725a3475df` |
| `firmware/STDGEN5/kor/hyundaiB/update/micom/js/micom_sw.bin` | `6f9353675cb84fce42fb1d0a0dac0a903cd6150137434f37cdad43f620297b41` |

`snapshot.raw.img`의 해시는 `update.zip`에 포함된 `snapshot.sparse.img`를 전개한 결과와 일치한다. 공통 HyundaiB 이미지와 JS 실차의 활성 빌드·옵션은 별개다.

보존된 `hmd5-repro.j1LquyfL` AVD 기록에서 cluster Binder 등록, 기본 800×480 디스플레이, HUD 옵션 `UNKNOWN`, `MicomService_P: socket is not connected`를 확인했다. AVD의 Binder 등록은 MICOM이나 실제 HUD 출력 성공의 증거가 아니다. 이번 조사에서는 AVD를 새로 실행하지 않았고 실차에도 연결하지 않았다.

## Dashboard에 적용한 내용

| 항목 | 적용 범위 |
|---|---|
| 경로 상태 방송 | `JsRouteStatusObserver`가 `ROUTE_GUIDE_STATE` 정수를 수동 수신한다. JS 제품·빌드 gate 뒤에서 시작하고 Activity 중지 시 해제한다. 초기 sticky 여부·수신 횟수·앱 수신 후 경과를 표시한다. sticky 원 발신 시각은 미확인이고 발신자도 인증하지 않는다. |
| 클러스터 서비스 | `JsHudProbe`가 `com.hkmc.micom.cluster` 조회와 `com.hkmc.cluster.IClusterService` descriptor 일치를 확인한다. 메서드 권한이나 출력 성공은 판정하지 않는다. |
| 진단 화면 | `HUD · 내비 / 차량 신호 / Wi-Fi · DCT` 세 탭으로 구분한다. 방송·provider 계측과 신호별 미수신·무효·유효·보관·만료 상태, 수신 횟수 및 경과를 표시한다. Dashboard의 헤드유닛 화면이며 순정 HUD 렌더러가 아니다. [화면 사용과 상태 의미](JS_DIAGNOSTICS.md)를 참고한다. |

기존 미디어 위젯과 내비 provider 관찰은 유지했다. 자동 TBT·음악 송신은 추가하지 않았다. 기존 코드의 명시적 `sendTbt` 시험 메서드는 실제 출력 검증을 뜻하지 않는다.

이 변경은 호스트 Java 합성 프로그램 16개, Python 테스트 56개, API17 대상 probe 컴파일로 확인했다. 변경이 포함된 APK는 생성·설치하지 않았고 실차 방송 수신이나 HUD 출력은 검증하지 않았다. 후속 확인은 동일 JS 빌드의 실제 방송·Binder 권한, 차량의 daemon 기동 인자와 MICOM 연결, 후단 HUD 표시 제어기·펌웨어에 필요하다.
