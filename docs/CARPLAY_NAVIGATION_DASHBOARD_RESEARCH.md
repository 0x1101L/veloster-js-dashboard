# JS Dashboard에서 CarPlay 내비 정보를 받을 수 있는지 조사

원 조사일: 2026-11-13. 재대조·보강일: 2026-11-15. 범위는 `dashboard/js` 앱의 정적 소스와 이미 복원된 HyundaiB/JS 내비·framework 코드다. 이번 재대조에서 Dashboard 소스나 APK를 빌드·설치하지 않았고 AVD·차량을 실행하지 않았다. 소스에 수신 코드가 있다는 사실, OEM에서 데이터가 제공된다는 사실, 일반 앱 권한으로 읽힌다는 사실, 실차에서 값이 갱신된다는 사실을 각각 구분한다. 11월 15일의 재검토 결과는 아래 `2026-11-15 재대조` 절을 우선한다.

## CarPlay를 Dashboard에 연결하기 위한 핵심 결과

목표는 iPhone CarPlay의 지도 앱이 보내는 안내를 Dashboard에 표시하는 것이다. 현재까지 소스에서 확인한 경로는 다음과 같다.

```text
iPhone 지도 앱
  ├─ CarPlay 화면 영상 → AirPlay/DiPO 화면 Surface → 순정 CarPlay 화면
  ├─ 앱 상태 변화 → DiPO onModeChanged(turnByTurn) → 시스템 DiPOMode  [안내 중 여부 후보]
  ├─ 미디어 메타데이터 → iAP2 → DiPO widget broadcast → Dashboard MediaReceiver [이미 구현]
  └─ 구조화된 경로 안내 → JS에서 Dashboard까지의 생산자·구독 경로 미확인
```

두 번째 화살표의 `DiPOMode` 시스템 반영은 **HyundaiB AVD에서 확인한 흐름**이다. JS snapshot의 `DipoPlayer.onModeChanged(IIIIII)` 선언과 `turnByTurn` 로그는 직접 확인했지만, JS에 맞는 bootclasspath가 없어 quickened 명령까지 복원한 동일 흐름으로 확정하지 않았다. 아래 1차 구현 후보는 JS에 같은 `dipo` 서비스가 실제 제공되는지 확인해야 한다.

| 목표 | 소스상 가능한 입력 후보 | 현 단계 판단 |
|---|---|---|
| CarPlay 연결/종료 표시 | `com.daudio.app.dipo.start`, `.close`; DiPO 시스템 상태 | JS 앱에 action 문자열이 있다. 방송 등록·발생 조건과 앱 수신은 실차 미검증. 연결과 지도 안내 시작은 별개 |
| 지도 안내 중 표시 | `Context.getSystemService("dipo")` → `getCurrentDiPOMode()` → `getTurnByTurn()` | HyundaiB의 구체적인 읽기 후보. Dashboard 미구현, JS 서비스·권한·정확도 미검증 |
| 다음 회전 방향·거리·차선 | iAP2 RouteGuidance 등 구조화된 수신 데이터 | Gen5W 사례는 있지만, 조사한 JS iAP2 Java 계층에서 같은 API·분류를 찾지 못함 |
| 지도 화면의 화살표·거리 | CarPlay 화면 이미지에서 영상 분석 | 출력 Surface는 존재. Dashboard에서 프레임을 얻을 권한·API·보호 여부 및 OCR 정확도 미검증 |
| 곡 제목·가수·재생 정보 | `com.hkmc.dipoaudio.widgetupdate` | Dashboard에 이미 receiver 구현. CarPlay 음악 정보이며 내비 maneuver 데이터는 아님 |

### 직접 비교: Gen5W RouteGuidance와 JS iAP2

별도 조사 기록은 공개 `Chunwol/gen5w-kr`의 고정 commit `414404ef62cd8b2b5accdab6b0df7897152174ff`에서 `probe/src/kr/gen5w/probe/ProbeService.java`, `app/src/kr/gen5w/core/CoreService.java`, `docs/iap2-fields.md`를 비교했다. Gen5W V039의 사례는 다음 구조다.

1. iAP2 listener support mask `0x1000`을 RouteGuidance 범주로 등록한다.
2. `Iap2SvcGetRouteGuidanceRegisteredComponentCount()`와 `Iap2SvcGetRouteGuidanceComponentNameIdList()`로 등록 가능한 component를 확인한다.
3. `Iap2SvcStartRouteGuidanceUpdate`로 해당 안내 갱신을 시작한다.
4. V039의 listener notification `what` 값 `0x78`, `0x79`, `0x7a`를 각각 상태·회전·차선 데이터로 처리한다.

이 숫자는 **그 Gen5W 구현에서 사용하는 Java notification 식별자**다. iAP2 USB 원선 메시지 ID로 검증한 값이 아니며, JS에서 같은 뜻이라고 가정할 수 없다. 일부 Gen5W 환경에서는 capability shim이 필요하다고 해당 조사에 기록돼 있다. 상세 필드 인덱스·스케일은 JS에서 검증되지 않았으므로 가져다 쓰지 않는다.

JS에 관해서는 서로 다른 두 종류의 증거가 있다.

- **JS snapshot 직접 재확인:** `HKMC_DiPO`의 `DipoPlayer$1`은 `Iap2ListenerInfo.onSetSupportType()`에 `0x89` 또는 `0x8d`를 설정하고 `Iap2SvcInit()`을 호출한다. 확인된 마스크에는 Gen5W의 `0x1000` bit가 없다. 분석 당시 보존한 smali에서 확인했으며 임시 파일 경로는 저장소 산출물로 제공되지 않는다.
- **JS snapshot 직접 재확인:** `Iap2HandleNoti.checkListenerType(what, ...)`의 packed-switch는 `0x00`부터 `0x43`까지 68개다. `what=0x78~0x7a`는 범위 밖에서 기본 `notiType=-1`에 남는다. 추가 대조에서 조사한 `Iap2Service` 전달부는 `notiType >= 0` 및 listener support-mask 검사를 요구한다. 그러므로 그 Java 전달 경로에서 미분류 값이 listener에 도달한다는 근거는 없다. native의 다른 경로·실제 송신은 미확인이다.
- **이전 동일 JS 기기 조사 기록:** `js_kr / eng.vmadmin.20260114.090656`의 `com.tcc.iap2.odex` SHA-256 `af24e389a8721acb7666e9d1a18e04beb025fe08a5b870edcb78fe2f552ad029`과 `Iap2ServerApp.odex`에서 위 세 RouteGuidance 메서드명·관련 명명된 알림 타입을 찾지 못했다. `libiap2.so`, `libIap2Acc.so`, `libiap2tcc.so`, `libtcciap_jni.so`의 문자열·동적 의존성 비교에서도 명명된 RouteGuidance 경로는 찾지 못했다. 해당 실기기 파일은 **이전 기록의 읽기 전용 관찰**이며 이번 조사에서 차량에 재접속하지 않았다. 정확한 native 파일별 해시와 제한은 연결된 비공개 프로젝트 메모리의 HUD·내비 연구 정본에 보존한다.

**작업상 의미:** Dashboard에 `0x1000` listener만 추가하는 것으로 Gen5W의 회전·차선 이벤트를 받는다는 근거는 없다. 별도의 capability 광고, 프로토콜/네이티브 파싱, Java notification 분류·전달까지 실제 생산 경로를 확인해야 한다. 이것은 단순 앱 설정이 아니라 펌웨어 구성요소의 심층 조사 대상이며, 그 변경 가능성이나 안전성을 아직 확인하지 않았다.

### 다른 제품의 성공 사례가 알려 주는 범위

기존 [HUD 연구](../../../HUD/README.md)는 공개 G70 브리지가 OEM `DiPO`/`Iap2ServerApp`을 펌웨어별로 패치해 iAP2 경로 안내를 받아 cluster API로 넘긴다고 기록한다. 같은 연구의 Gen5W 사례도 구조화된 CarPlay 내비 수신이 **가능한 구현 방식**임을 보여 준다. 다만 둘 다 별도 제품·펌웨어이며 G70의 callback이나 Gen5W의 `what`/필드·shim을 JS의 계약으로 사용할 수 없다. 이 사례들은 JS에서 Dashboard 단독 수신이 된다는 실증이 아니다. 다음은 기존 로컬 조사에 기록된 고정 버전이며 이번 조사에서 온라인 원본을 새로 검증하지 않았다.

- [Gen5W 필드 조사](https://github.com/Chunwol/gen5w-kr/blob/414404ef62cd8b2b5accdab6b0df7897152174ff/docs/iap2-fields.md), [ProbeService](https://github.com/Chunwol/gen5w-kr/blob/414404ef62cd8b2b5accdab6b0df7897152174ff/probe/src/kr/gen5w/probe/ProbeService.java), [CoreService](https://github.com/Chunwol/gen5w-kr/blob/414404ef62cd8b2b5accdab6b0df7897152174ff/app/src/kr/gen5w/core/CoreService.java)
- [G70 브리지 기술 문서](https://github.com/BeegSchmooze/Lambda-Carplay-AA-Bridge/blob/a8d19729fa50d7825f2cb79c4dda1c1f4db0414e/docs/TECHNICAL.md)

JS 알림 전달부는 listener의 support 검사와 `IIap2Listener.notify(...)` 호출을 포함한다. 별도 보존 분해본 대조에서 해당 경로는 `notiType >= 0`과 support-mask 통과를 요구한다. native 측 producer와 실제 티맵 알림 송신은 확인하지 못했다. 분석 당시 임시 파일은 이 저장소에 포함되지 않으며, 공개 재현 가능한 바이너리 분석 산출물도 아니다.

### 지금 연결할 수 있는 다른 CarPlay 데이터

다음 항목은 내비 회전 정보를 대신하지 않지만 Dashboard의 CarPlay 화면을 구성할 때 사용할 후보들이다. 방송 발신 조건, 앱 UID의 수신 권한 및 실차 갱신은 별도로 확인해야 한다.

| 경로 / extra | 내용과 Dashboard 처리 | 주의점 |
|---|---|---|
| `com.hkmc.dipoaudio.widgetupdate` / `filename`, `artist` | 곡명·가수 문자열. 기존 `MediaReceiver`가 수신해 `MediaState`에 반영하도록 구현 | 부분 갱신이므로 extra 존재 여부를 각각 확인한다. 미포함 필드로 기존 값을 덮어쓰지 않는다. |
| 같은 action / `playstate` | 기존 receiver는 값 `0`, `1`만 상태에 반영 | `-1`은 OEM 부분 갱신에서 필드 생략을 의미하므로 정지 상태로 바꾸지 않는다. |
| 같은 action / `audio_current_time`, `audio_total_time` | 현재 재생 위치와 전체 시간 `long` | 기존 모델의 시간 처리 기준 및 실제 방송 단위·갱신 주기를 기기에서 재확인한다. |
| 같은 action / `artwork_uri`, `invisible` | 앨범아트 참조와 위젯 숨김 신호. 기존 receiver에 처리 코드 존재 | `artwork_uri` 값과 파일 접근 권한은 기기 확인이 필요하다. `artwork_update`는 별도 내부 갱신 신호로 관찰되며 이미지 데이터 자체로 단정하지 않는다. |
| `com.hkmc.carplay.call.widgetupdate` / `phone_call_state`, `phone_call_name` | JS DiPO 발신 코드에서 확인한 통화 상태·이름 후보 | Dashboard에 수신 구현을 확인하지 못했다. `phone_call_number` 상수는 있지만 해당 JS 발신 메서드에서 기록하는 것을 확인하지 못했다. 이름은 개인정보이므로 필요할 때만 표시한다. |
| `com.daudio.app.dipo.start`, `com.daudio.app.dipo.close` | 연결 세션 전환을 다시 조회하는 힌트 후보 | 각 방송의 등록·발생 조건과 수신 가능성 미검증. 안내 시작·종료 이벤트가 아니다. |

근거: Dashboard `shared/src/com/veloster/vehicle/MediaReceiver.java`와 `MediaState.java`, JS 분해본 `HKMC_DiPO/.../sendtowidget/WidgetSender.smali`, `WidgetSenderCall.smali`. 앱의 receiver 코드가 있다는 것과 실제 방송 수신이 된다는 것은 다른 증거다.

### Dashboard 1차 적용 후보: 안내 상태만 읽기

HyundaiB framework에서 `ContextImpl`은 `"dipo"` 서비스를 등록하고, `ContextImpl$35.createService()`는 `ServiceManager.getService("dipo")`와 `IDiPOManager.Stub.asInterface()`로 `DiPOManager`를 만든다. 즉 반사 호출 후보는 다음처럼 구체화할 수 있다. 아래는 **설계용 의사코드**이며 현재 Dashboard에 추가하거나 실행한 코드는 아니다.

```java
// worker thread; 각 단계 실패/null은 UNKNOWN으로 보관한다.
Object manager = context.getSystemService("dipo");
Object stat = manager.getClass().getMethod("getCurrentDiPOStat").invoke(manager);
Object mode = manager.getClass().getMethod("getCurrentDiPOMode").invoke(manager);
Object tbt = mode.getClass().getMethod("getTurnByTurn").invoke(mode);
// raw stat / raw tbt 및 관찰 시각을 남긴 뒤, 기기별 의미 검증 후에만 표시한다.
```

실제 구현에는 manager 또는 mode null, class/method 부재, `SecurityException`, reflection 예외, Binder 장애를 각각 처리해야 한다. HyundaiB의 service fetcher는 Binder가 null이어도 manager 객체 생성을 시도하므로 **`getSystemService("dipo") != null`만으로 서비스 접근 성공을 판정할 수 없다.** `getCurrentDiPOStat()` wrapper는 `RemoteException`에 `-1`, `getCurrentDiPOMode()`는 null을 반환한다. 정상 `0`·`1`로 조용히 바꾸면 연결 실패와 안내 종료를 혼동한다.

HyundaiB `ModeManagerService`는 stat `1`과 turnByTurn `1`을 함께 검사한다. `DipoModeChecker`는 새 값 `1`을 ENABLE, 다른 값으로 바뀐 경우 DISABLE로 취급한다. 그 밖의 프로토콜 enum, 앱별 송신 조건 및 JS에서의 일치 여부는 미확인이다. 따라서 제품 화면의 확정적인 ‘안내 중’보다는 진단 화면에서 `service 상태 / raw stat / raw turnByTurn / 마지막 성공 시각`을 우선 보여 주는 설계가 적절하다.

기존 Dashboard 구조에 연결한다면 `DashboardActivity`의 `onResume/onPause`와 별도 worker/lifecycle을 사용하고, 상태는 `JsDiagState` 또는 별도의 CarPlay 상태 모델에 넣어 UI 스레드에 전달한다. `start/close` 방송은 즉시 재조회 힌트로만 취급하고, callback 등록이 없는 API이므로 화면이 보이는 동안 제한된 주기의 조회가 필요하다. 조회 주기와 배터리·성능 영향은 측정 전 값으로 확정하지 않는다. 연결 해제·서비스 재시작·화면 종료 시 이전 값이 계속 표시되지 않게 `UNKNOWN`/`DISCONNECTED`와 값 유효 시각을 구분한다. 이는 제안이지 이미 구현되거나 기기에서 수신된 기능이 아니다.

### 회전 방향·거리까지 가기 위한 후속 조사 순서

1. **JS artifact 재현:** 동일 빌드의 `framework.odex`/`services.odex`/`com.tcc.iap2.odex`, `HKMC_DiPO.odex`, native 라이브러리의 해시와 bootclasspath를 맞춰 `DiPOManager`, `DiPOService`, iAP2 callback을 다시 복원한다. 기존 JS smali의 `invoke-virtual-quick`만으로 호출 대상을 추정하지 않는다.
2. **생산자 존재 확인:** iPhone이 route guidance capability를 볼 수 있는지, OEM native의 초기 capability 구성과 message dispatch에 대응 코드가 실제 있는지 조사한다. 이름 검색뿐 아니라 숫자 dispatch와 delegate 연결을 살피되, Gen5W 상수를 JS wire ID로 가정하지 않는다.
3. **생산자에서 앱까지 계약 확인:** 데이터가 발견되면 메시지 종류, 필드 타입·단위·갱신·취소·빈 값·앱별 차이, notification mask와 listener fan-out을 증거와 함께 정의한다. 현재 `what=0x78~0x7a`를 임의로 JS listener에 연결할 근거가 없다.
4. **Dashboard 변환:** 검증된 입력만 `source=CARPLAY`, session ID, 안내 상태, maneuver 종류, 거리와 단위, 수신 시각, 유효 기간을 가진 별도 모델로 변환한다. 값이 없거나 오래되면 미표시/unknown으로 처리한다. 순정 내비 state나 현재 재생곡을 경로 데이터의 대체값으로 쓰지 않는다.
5. **조건부 수신 검증:** 별도 승인을 받은 후 동일 JS 빌드·정차 상태에서 iPhone 지도 앱별 `연결 → 경로 없음 → 경로 시작 → 안내 변화 → 경로 취소 → 연결 해제`를 비교한다. CarPlay 화면의 관찰 결과와 수신 timestamp를 대조하고, 다른 지도 앱·동글·iOS 버전은 별도 표본으로 기록한다. 이는 실차 수신 검증 계획일 뿐 이번 조사에서 수행하지 않았다.

영상 분석이나 외부 휴대폰 companion은 독립 대안이다. 전자는 API17에서 화면 확보·권한·보호 콘텐츠·좌표/크기 변화·지연을 먼저 확인해야 한다. 후자는 지도 앱이 다른 앱에 경로를 노출하는 별도 공식 계약이 있는지 확인해야 하며, iPhone의 임의 지도 앱 경로에 자동 접근할 수 있다고 가정하지 않는다.

## 결론

Dashboard는 이미 **순정 MNsoft 내비의 경로 상태**를 방송과 ContentProvider로 관찰한다. 이 경로들은 CarPlay 내비 앱의 다음 회전 방향·회전 거리·목적지·도착시간을 주는 인터페이스라는 근거가 없다. 별도 AVD 정적 조사에서는 CarPlay의 `turnByTurn` 안내 활성 상태를 보관하는 시스템 API 후보가 확인됐지만, Dashboard가 그 Binder를 조회하지 않고 있으며 JS 실차의 같은 API 존재·접근 권한·갱신도 검증되지 않았다.

CarPlay 경로 안내 상태는 시스템 DiPO 상태 조회가 가능하면 추가 판별할 후보이고, 방향·거리·목적지·ETA는 현재 확인한 Dashboard 수신 계약으로 사용할 수 없다. 기존 순정 내비 상태는 별도 진단 정보이며 CarPlay 내비 데이터의 대체 입력으로 사용하지 않는다.

| 데이터 후보 | 앱에서 리스너 등록 | 실제로 받는 의미 | CarPlay 경로 안내로 사용 |
|---|---|---|---|
| `com.mnsoft.navi.action.Route_Guide_Status` | 가능하도록 동적 receiver 구현됨 | 순정 MNsoft 경로 안내 상태 정수. sticky 초기값일 수 있음 | 직접 사용 불가. CarPlay 방송이라는 근거 없음 |
| `content://com.mnsoft.navi/State_Table` | `ContentObserver` 등록과 query 구현됨 | `State_Destination` 목적지 메뉴/선택 상태로 보이는 정수 | 직접 사용 불가. 경로 안내 활성이나 CarPlay 목적지라는 근거 없음 |
| `IDiPOManager.getCurrentDiPOMode().getTurnByTurn()` | Dashboard 미구현. 시스템 Binder 조회 후보 | 별도 HyundaiB AVD 코드에서 CarPlay 안내 활성 상태 판정에 이용 | 조건부 유력 후보. JS 권한과 실차 갱신 미확인 |
| `NavigationCluster.addListener()` | API에 listener 등록 메서드 존재 | 확인한 listener 콜백은 내비 alive, day/night, SCC sync, CAN 및 SLIF 정보 | TBT 제공 listener가 아님 |
| `NavigationCluster.updateTBTInfo(...)` | Dashboard에 명시적 송신 시험 메서드만 존재 | 앱이 클러스터 쪽으로 TBT 값을 보내는 API | 수신 API가 아님. 잘못된 입력은 표시·차량 상태에 영향을 줄 수 있어 수신 경로로 전용하지 않음 |
| iAP2 / DiPO 내부 listener | Dashboard에 없음 | OEM 프로세스 내부 iPhone 메타데이터·세션 callback | 일반 앱에 노출된 CarPlay route callback으로 확인되지 않음 |

## 2026-11-15 재대조

이전 문서의 핵심 프로토콜 결론은 기존 연구와 겹치며 새 실차 수신 결과가 아니다. 이번에 더한 근거는 현재 앱 lifecycle과 listener dispatch gate를 직접 연결한 것이다.

- `DashboardActivity.onPause()`에서 `JsHudProbe.close()`가 호출돼 JS 순정 내비 상태 observer는 pause 중 해제된다. 반면 `DashboardView.pauseStandalone()`은 JS 프로필에서 `MediaReceiver.stop()`을 건너뛰고, `HudEventLog` writer는 Activity pause에서 닫히지 않는다. 앱 프로세스가 살아 있고 media broadcast가 오면 bounded 요약 로그를 CarPlay 종료 뒤 ADB로 회수하는 시험은 성립할 수 있다. 2026-11-13의 versionCode 15 foreground 로그는 background 성공을 증명하지 않으며, 이 요약 로그도 TBT를 검증하지 않는다.
- 보존된 JS smali에서 `Iap2HandleNoti.checkListenerType()`는 지원 범위 밖 notification에 `-1`을 반환하고, 조사한 `Iap2Service` 전달 경로는 `notiType >= 0`과 support-mask 검사를 통과해야 Binder listener에 전달한다. 그러므로 **그 Java notification dispatcher에서 분류되지 않은 값이 listener에 전달될 수 있다는 주장은 근거가 없다.** 다른 native producer/session 및 티맵 송신 여부는 여전히 미확인이다.
- 기존 단계 계획의 ADB 전후 package/service inventory 비교는 TBT 수신을 관찰하지 않아 시험 항목에서 제거했다. 유효한 다음 단계는 사전 inventory와 분리된 background media-log 양성 대조이며, 이후 실제 TBT 시험에는 먼저 입증된 producer와 권한이 필요하다.

단계별 시험, 결과별 판정, 후보 경로의 현실성 및 사후 요약 로그 회수 스크립트는 [`CARPLAY_TBT_TEST_PLAN_20261115.md`](CARPLAY_TBT_TEST_PLAN_20261115.md)를 따른다. 이번 재대조는 정적 분석 및 호스트 모의검증만 수행했고, 실제 TBT 수신 가능성은 아직 확인되지 않았다.

## Dashboard가 지금 하는 일

`DashboardActivity.onResume()`은 JS profile에서 `JsHudProbe.startNavigationObservation()`을 호출하고, `onPause()`에서 `close()`로 해제한다. 별도로 기존 `VehicleDash`는 화면이 재개될 때 `MediaReceiver`를 시작한다. 이 receiver가 받는 CarPlay 관련 데이터는 `com.hkmc.dipoaudio.widgetupdate`의 미디어 위젯 필드(곡명, 가수, 재생상태, 재생시간 등)다. 내비 안내 데이터가 아니다.

`JsRouteStatusObserver`는 `registerReceiver()`로 `com.mnsoft.navi.action.Route_Guide_Status`를 등록하고 정수 `ROUTE_GUIDE_STATE`를 기록한다. 초기 sticky 전달인지 이후 방송인지 구분하고, 화면이 정지되면 unregister한다. 이 방송의 발신 코드는 순정 MNsoft 내비 소스에서 확인됐으며 CarPlay receiver가 이 action을 발신한다는 연결은 찾지 못했다. `registerReceiver()` 호출 자체는 Dashboard 코드에서 구현돼 있으나, 임의 빌드/일반 UID에서의 실제 수신 성공은 이 정적 분석만으로 보장되지 않는다.

`JsHudProbe`는 `content://com.mnsoft.navi/State_Table`에 `ContentObserver`를 달고 변경 때 query해 `State_Destination` 열만 읽는다. 순정 내비 provider의 DB 정의에는 해당 표 외에도 `CurrentLoc_Table`의 좌표·방향·행정구역, `Destination_Table`의 이름·주소·전화·총거리·총시간·좌표가 있다. 그러나 이 표들은 순정 내비의 VR/provider 데이터베이스 계약이다. **표에 좌표·거리 열이 있다는 사실은 현재 주행 중인 route state를 계속 갱신하거나 CarPlay의 목적지를 저장한다는 뜻이 아니다.** Dashboard는 그 테이블을 query하지 않는다. provider manifest의 exported/read permission 및 일반 앱 UID 접근도 별도 확인이 필요하다. 특히 좌표·주소·목적지 값은 사용자의 위치·목적지 정보이므로 필요 이상의 테이블을 읽지 않아야 한다.

관찰은 Activity가 화면에 있는 동안만 유지된다. 서비스가 계속 돌거나 화면을 내린 뒤에도 수신한다는 동작은 없다. `ContentObserver`는 변경 알림을 받으면 작업 스레드에서 250ms 지연 후 상태를 query하며, 화면 pause 시 observer와 worker thread를 닫는다.

## CarPlay 안내 상태 수신 후보

별도 HyundaiB API17 정적 복원에서 `DipoPlayer.onModeChanged()`는 `turnByTurn`을 포함한 `DiPOMode`를 만들고 DiPO 서비스로 전달했다. 시스템 `DiPOService`는 이를 현재 `DiPOMode`에 보관하며 `IDiPOManager.getCurrentDiPOMode()`를 제공한다. automotive `ModeManagerService.isNaviGuidanceStatus()`는 `getCurrentDiPOStat() == 1` 및 `getCurrentDiPOMode().getTurnByTurn() == 1`을 확인하고, 두 조건이 맞을 때 CarPlay 안내 중인 상태로 처리한다.

이 자료는 **AVD HyundaiB 이미지에 존재한 정적 구현**의 근거다. Dashboard 대상인 JS와는 다른 제품·이미지·빌드이므로 동일한 framework 계약이라고 가정하지 않는다. Dashboard에는 현재 `IDiPOManager` lookup/binding이나 `DiPOMode` read가 없다. 공개 Java listener인지, Binder 조회에 caller permission이 필요한지, JS의 같은 서비스가 존재하는지, 사용자 앱이 `1/0` 변화를 읽는지는 확인되지 않았다. 구현한다면 기존 receiver처럼 관찰 전용으로 접근 가능 여부와 값 변화만 기록하고, iAP2 초기화·통화/미디어 시작 호출은 복제하지 않는 것이 맞다.

이 값이 제공되더라도 범위는 안내 상태(on/off)다. 방향·거리·maneuver 종류·도로명·목적지·ETA를 포함하는 객체는 아니다. Apple/CarPlay가 표준화한 route guidance payload를 Android listener로 전달한다는 증거도 이번 조사에서 확인되지 않았다.

## NavigationCluster listener와 TBT API는 방향이 다르다

OEM `NavigationCluster`에는 `addListener(NavigationClusterListener)`와 `removeListener(...)`가 있다. 확인한 `NavigationClusterListener` callback은 `onHandleNaviAliveStatus`, `onHandleNaviDayNightStatus`, `onHandleNaviSCCSync`, `onHandleCANStatusCheck`, 두 형태의 `onHandleNaviSLIFInfo`다. SLIF는 제한속도 표지 관련 정보다. 확인된 callback 계약에는 CarPlay route maneuver/distance 이벤트가 없다. 라이브러리 안에 listener 패턴이 있다는 이유만으로 그 listener를 CarPlay TBT receiver라고 볼 수 없다.

`updateTBTInfo(int,int,float,float,int,int,int,int,int)`는 반대 방향의 API다. 순정 내비가 데이터를 **클러스터 서비스에 보내는** 호출이며 Dashboard의 `JsHudProbe.sendTbt(...)` 역시 호출부가 명시적으로 실행될 때 값을 보내는 코드다. `sendTbt()`는 현재 자동 실행되지 않는다. 이 API를 listener로 사용하거나, 임의의 기본값을 보내 수신 여부를 시험하면 안 된다. 호출 성공 반환도 MICOM/계기판/HUD의 실제 표시를 입증하지 않는다.

## iAP2/DiPO 경로의 적용성

CarPlay iAP2 리스너와 DiPO 네이티브 callback은 `Iap2ServerApp`/`HKMC_DiPO` OEM 프로세스에서 사용된다. iAP2 listener는 서버가 정한 notification mask와 OEM AIDL/Binder 계약으로 호출되며, Dashboard에는 해당 서비스 bind 및 `IIap2Listener` 구현이 없다. DiPO의 `DipoPlayer`/`DipoListener` callback은 OEM 앱 내부 객체다. 앱 내부 단일 listener 필드나 JNI callback이 일반 앱에 공개된 subscription point라는 근거는 없다.

이 경로는 iPhone이 보내는 위치 관련 iAP2 알림과도 혼동하면 안 된다. 조사한 OEM 구현에서 `LocationStart/Stop`은 iPhone이 헤드유닛 위치 정보를 요청하는 쪽이고, HU의 GPS/NMEA를 iPhone으로 보내는 코드가 발견됐다. 이는 CarPlay 지도앱이 현재 회전 방향·목적지·거리 정보를 Android 앱에 푸시하는 경로가 아니다.

## 활용 가능성 및 남은 검증

1. **이미 있는 receiver/provider 관찰은 순정 내비 상태 진단에 한정해 사용 가능하다.** CarPlay 상태로 이름을 바꾸거나 목적지·maneuver 값으로 해석하지 않는다. sticky 방송은 이전 값일 수 있고 발신자 진위를 인증하지 않는다.
2. **CarPlay 안내 on/off는 `IDiPOManager`의 현재 mode 조회가 가장 직접적인 미검증 후보**다. JS 대상에서 API 존재, 서비스명, Binder descriptor, 앱 UID read 권한, 값 변화 갱신을 각각 확인해야 한다. 단순한 class/reflection 성공은 권한 또는 값 수신 성공의 증거가 아니다.
3. **방향·거리·목적지·ETA는 수신 계약이 발견되지 않았다.** 화면 프레임에서 읽는 방식은 독립적인 영상 접근·OCR 문제이고, 현재 Dashboard의 `Surface` 접근권한이나 구현은 없다.
4. 실제 테스트가 승인되면 주행 중이 아닌 정차 조건에서 보존된 동일 JS 빌드/차량을 대상으로, 우선 read-only service lookup과 receiver/provider 결과만 비교한다. 목적지 provider 전체를 무차별 query하거나 `updateTBTInfo()`를 보내는 검증은 별도 범위다.

## 소스 근거

| 위치 | 근거 |
|---|---|
| `apps/dashboard/src/main/java/com/veloster/dashboard/DashboardActivity.java` | JS profile에서 resume 시 probe/observer 시작, pause 시 해제 |
| `apps/dashboard/src/0x1101L/java/com/veloster/dashboard/listener/JsRouteStatusObserver.java` | 순정 MNsoft route status broadcast 등록, sticky 여부와 정수 extra 기록 |
| `apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/JsHudProbe.java` | `State_Table` ContentObserver/query, `State_Destination` 단일 열, TBT send 시험 메서드, framework type/service inspect |
| `apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/MediaReceiver.java`, `MediaState.java` | 동적 미디어 widget receiver; CarPlay는 AV media source로만 모델링 |
| `apps/dashboard/AndroidManifest.xml` | `automotive` uses-library, 현재 manifest에 DiPO/iAP2 전용 permission·service 등록 없음 |
| `docs/JS_HUD_RESEARCH.md` | JS HUD/순정 cluster/MICOM 경로와 적용 범위, AVD·실차 evidence 경계 |
| HyundaiB deodex `HKMC_Navi_ExSL/com/mnsoft/navi/NaviVRProvider*.smali` | 순정 내비 VR provider의 State/CurrentLoc/Destination schema와 query 구현 |
| HyundaiB automotive `com/hkmc/cluster/NavigationCluster*.smali` | cluster listener callback과 TBT 송신 API 계약 |
| HyundaiB services/automotive-service `DiPOService`, `ModeManagerService` | `turnByTurn` 보관과 CarPlay 안내 상태 판정. JS 앱 접근성/실차 동작의 근거는 아님 |

## 근거 강도와 한계

- **Dashboard에 observer 코드가 있음:** 소스에서 직접 확인.
- **Dashboard observer가 CarPlay route를 받음:** 확인 안 됨. 현재 source/action/data는 순정 MNsoft 내비에 속함.
- **provider에 목적지·좌표 표가 있음:** HyundaiB 정적 DB schema에서 확인. Dashboard query 권한, JS 실차의 실제 row, CarPlay route와의 관계는 확인 안 됨.
- **CarPlay `turnByTurn` 상태 필드가 AVD 시스템에서 사용됨:** HyundaiB 정적 코드에서 확인. JS 대응 빌드와 일반 앱 접근성은 미확인.
- **방향·거리 TBT를 Dashboard listener로 수신:** 확인 안 됨. cluster `updateTBTInfo`는 송신 API이고 확인된 listener callback 목록에도 TBT route payload가 없다.
- **실차 수신/화면·HUD 표시:** 이번 조사에서 수행하지 않음.
