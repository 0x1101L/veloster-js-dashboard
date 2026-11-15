# JS CarPlay TBT 조사 후속 시험 계획 — 2026-11-15

대상 가정: Veloster JS `js_kr`, Android 4.2.2/API 17, iPhone T맵, 순정 유선 CarPlay. USB 포트 하나를 ADB와 iPhone이 번갈아 쓴다. 이 문서는 기존 조사와 현재 소스를 대조해, 원본 iAP2 수신 후보를 시험하고 파일을 앱 내부에 보관하는 순서를 정리한다. 2026-11-15에는 API17 전체 Dashboard의 unsigned APK를 컴파일했으나 설치하지 않았다. OEM 변경·HUD 송신은 수행하지 않았다.

## 우선 경로: native 원본 후보를 회수하고 앱 내부에 보관

진단 화면의 CarPlay 탭에는 `libiap2.so`의 파일 해시와 `/proc/*/maps` 로드 흔적에 대한 읽기 전용 조회, `캡처 파일 가져오기` 동작이 있다. 프로세스 접근이 제한되면 미확인으로 표시한다. 앱은 Frida 상태를 전달받지 않으므로 후킹/실시간 raw 수신 상태는 표시할 수 없다. 검증된 CPRAW1 파일을 고르면 Dashboard가 원본 bytes를 `files/diagnostics/carplay-raw/`에 그대로 저장한다. 이 저장소는 DCT/HSCAN과 HUD 요약 파일에서 분리된다. 수신 관측도 callback, `libiap2.so` 호출자의 libc `read()` 반환, 둘의 동시 비교 모드 세 가지를 넣었다. 모드별 기록 형식은 host에서 확인했으며 API17 실기기 동작은 미검증이다.

```text
실차 Frida callback / libc read / 둘 다 비교 → 차량 로컬 bounded CPRAW1
    → USB를 ADB로 전환 → 파일 회수/공유 저장소에 배치
    → Dashboard 진단 > CarPlay > 캡처 파일 가져오기
    → files/diagnostics/carplay-raw/*.cpraw
```

MacBook에서 대상 PID와 경로를 확인한 뒤 실행 도구에 모드를 지정한다. 예: `python3 scripts/start_carplay_iap2_capture.py <PID> /data/local/tmp/cpraw-<unique>.bin --mode both`. `callback`, `read`, `both` 세 값이 준비되어 있다. 파일명을 매번 바꾸고, 먼저 짧은 기준 구간에서 생성·footer·회수부터 검증한다. `read`는 모듈 내부에서 호출된 libc read를 모으므로 iAP2 이외 데이터 가능성을 보존하고 후처리에서 분류한다.

실차에서는 먼저 정확한 프로세스·UID·실제 라이브러리 SHA/RVA와 Frida 권한을 확인한다. 일치하지 않으면 hook을 실행하지 않는다. callback은 iAP2 parser 전 조각이고, read는 해당 라이브러리 안에서 호출한 libc read 반환 바이트다. read 모드는 다른 `libiap2.so` read도 포함할 수 있으므로 source marker로 분리해 대조한다. 둘 다 USB 물리 원시 패킷 또는 TBT로 확정된 메시지가 아니다. 현재 MacBook Frida API 호환성, 안전한 시작·정지·파일 회수 동작이 확인되지 않아 이 문서는 아직 “바로 실차 캡처 가능” 상태를 뜻하지 않는다. 먼저 실차 준비 세션에서 세 모드의 짧은 무경로 기준 캡처와 파일 생성·정지를 검증하고, 이후에만 T맵 안내 구간 표본을 모은다.

## 먼저 정정할 점

기존 `run_carplay_tbt_preflight.sh`는 제품·빌드·API와 허용 목록 패키지·서비스 존재만 확인한다. CarPlay 세션이나 티맵 안내, TBT 수신, 권한, HUD 표시는 확인하지 못한다. ADB 연결 전후 package/service inventory를 비교하는 단계도 TBT 시험으로서 정보 가치가 없으므로 이 계획에서 제거한다.

대신 현재 코드로 가능한 한 가지 유효한 단일 USB 시험은 **CarPlay 사용 중 앱이 살아 있고 기존 CarPlay 미디어 브로드캐스트를 기록하는지** 확인하는 것이다. 이 시험은 CarPlay 연결 구간의 관찰 지속성과 사후 로그 회수만 검증한다. 미디어 이벤트는 방향·거리 데이터가 아니다.

## 소스 대조 결과와 시험에 미치는 영향

| 확인한 코드 동작 | 근거 | 시험에 주는 의미 |
|---|---|---|
| `DashboardActivity.onPause()`가 `JsHudProbe.close()`를 호출한다. 이 호출은 순정 내비 상태 observer와 route-status receiver를 해제한다. | `apps/dashboard/src/main/java/com/veloster/dashboard/DashboardActivity.java`; `JsHudProbe.close()` | CarPlay가 Activity를 pause시키는 기기 동작이라면 pause 이후 앱의 기존 순정 내비 상태 관측으로 CarPlay TBT를 증명할 수 없다. 그리고 해당 입력은 애초 CarPlay TBT가 아니다. |
| JS 프로필에서는 `DashboardView.pauseStandalone()`이 media receiver를 중지하지 않는다. `onDestroy()`에서 중지한다. MediaReceiver는 `onResume()`에 동적 등록된다. | `DashboardView.java`의 `resumeStandalone`, `pauseStandalone`, `stopStandaloneMedia`; `MediaReceiver.java`; `AndroidManifest.xml`에 별도 process/service 선언 없음 | Activity가 pause되더라도 프로세스가 살아 있는 동안 기존 CarPlay 미디어 수신과 이벤트 기록은 계속될 수 있다. Activity pause와 process 종료는 구별해야 한다. 이것은 소스의 lifecycle 근거이며 CarPlay 전환 시 실제 pause 여부·프로세스 생존은 차량에서 확인되지 않았다. |
| JS Activity는 진단 sink와 `HudEventLog` writer thread를 `onCreate()`에서 만들고 `onPause()`에서 닫지 않는다. 알려진 허용 이벤트를 `files/diagnostics/hud-events.log`에 비동기 기록한다. | `DashboardActivity.java`, `HudEventLog.java` | 숨겨진 동안 receiver가 이벤트를 받으면 내부 파일에 남길 설계는 있다. 기록은 HandlerThread에 큐잉되므로 갑작스러운 프로세스 종료 직전 이벤트의 영구 기록은 보장되지 않는다. `onDestroy()`/프로세스 종료, 설치 APK 버전 차이, 저장 실패와 로그 회전은 사후 확인에서 별도로 판정해야 한다. |
| `MediaReceiver`의 source 3은 media action 수신·처리 카운터와 mode/playback/visibility 요약만 남긴다. 제목·가수·이미지·원시 iAP2 payload는 `HudEventLog`에 기록하지 않는다. | `MediaReceiver.java`, `HudEventLog.value()` | 해당 이벤트가 CarPlay 중 기록돼도 TBT 데이터가 있었다고 말할 수 없다. 제목 같은 개인 메타데이터는 회수 스크립트가 출력하지 않도록 제한한다. |
| 기존 점검 당시 `CarPlayRawLog`는 producer/호출부가 없었다. 현재는 v21 진단 탭에서 CPRAW1 가져오기를 호출한다. | `CarPlayRawLog.importCapture()`, `DashboardActivity.onActivityResult()`, CarPlay 탭의 선택 버튼 | 앱의 저장 경로는 준비됐지만, native 수신 hook과 USB/ADB 회수는 별도 단계이며 아직 실차에서 검증되지 않았다. |
| Dashboard의 `JsRouteStatusObserver`/`JsHudProbe`는 MNsoft 순정 내비 상태를 기록한다. `MediaReceiver`는 미디어 위젯만 처리한다. 새 raw import와 native hook candidate는 별도다. | 각 클래스의 action, URI, extras 및 호출 위치; `carplay_iap2_capture.js` | 두 기존 입력을 CarPlay의 다음 회전·거리로 이름만 바꿔 쓰면 안 된다. 새 hook 결과도 실제 maneuver 의미가 확인되기 전까지는 raw bytes다. |

## 현재 가장 유용한 단일 USB 시험: background media-log 회수

선행 조건은 **이미 설치된 Dashboard APK**에 JS `MediaReceiver`와 `HudEventLog`가 들어 있고, 진단 로그 회수가 허용되는 것이다. 2026-11-15 저장소 manifest의 `versionCode=20`은 설치된 APK 버전을 증명하지 않는다. release 기록상 v20은 호스트 검증만 했고 설치는 확인되지 않았다. 2026-11-13에 회수된 과거 로그는 versionCode 15였고 Dashboard가 전면인 상태에서 관찰됐다. 그 과거 표본은 이번 background 시험의 성공 증거로 재사용하지 않는다. 현재 설치본이 기능을 갖췄는지 모르면 `events-current`가 `unknown`/실패일 수 있으며, 이는 TBT 없음이나 CarPlay 수신 실패 판정이 아니다.

1. **ADB 기준 로그(선택):** ADB 연결 중 `hud-events.log`와 `hud-events.previous.log`를 보관하려면 명시적으로 각각 확인한다. 아직 CarPlay 시험 창 전 기록이 있으면 시작 시점을 구분할 수 있도록 결과를 별도 보관한다. 자동 삭제·앱 데이터 초기화는 하지 않는다.

   ```sh
   sh diag/js_kr/run_carplay_tbt_preflight.sh 'ADB_SERIAL' events
   ```

2. **USB 전환:** ADB를 끊고 케이블을 뺀 뒤 동일 차량 USB 포트에 iPhone을 연결한다. ADB와 CarPlay를 동시에 연결하지 않는다. Dashboard가 이미 설치·실행되어 있고, T맵 안내가 시작된 구간의 시각을 사람이 별도 메모한다. Dashboard를 실행하는 과정은 CarPlay 세션을 끊을 수 있으므로, 실행 필요 여부와 순서는 실제 UI 상태에 따라 정차 중 정한다.

3. **CarPlay 구간:** 티맵의 `경로 없음 → 경로 안내 시작 → 안내 진행 → 경로 취소/종료`를 구분해 진행하고, 안내 시작·종료의 대략적인 시각과 CarPlay 화면 전환을 메모한다. 같은 구간에서 음악 재생/일시정지 등 기존 미디어 방송이 발생하는 조건을 유지한다. 안내를 실제로 시작할 수 없으면 해당 구간은 시험하지 않은 것으로 기록한다.

4. **사후 회수:** CarPlay를 정상 종료하고 iPhone을 분리한 뒤 ADB를 재연결한다. 제품·빌드·API inventory를 다시 TBT 결과처럼 비교하지 않는다. 대신 두 bounded event log를 회수한다.

   ```sh
   sh diag/js_kr/run_carplay_tbt_preflight.sh 'ADB_SERIAL' events
   ```

   이 검사는 `run-as com.veloster.dashboard cat files/diagnostics/<허용 파일>`을 사용하고, 허용된 `HUD_EVENT`의 elapsed time/event/value만 출력한다. raw log, 제목·주소·목적지, 임의 파일은 읽지 않는다. Android 4.2 기기에서 과거 `adb shell run-as ... cat`은 성공했고 `adb exec-out run-as ...`는 `error: closed`였다. 사후 회수도 같은 shell 경로가 실패할 수 있으므로 `denied/error/unknown`을 그대로 남긴다.

5. **해석:** 안내 구간에 겹치는 `binding.media.3 received_N`/`processed_N` 증가와 media mode/playback 이벤트가 있으면, 해당 설치본·해당 실행에서 백그라운드 receiver와 private log 경로가 동작했다는 증거다. 이것은 TBT input이 아니다. 기록이 없으면 앱이 죽었음, receiver가 중지됐음, 설치본에 로거가 없음, 파일 회전·저장 오류, CarPlay 미디어 방송 없음, 회수 실패 등이 구별되지 않으므로 TBT 부재로 해석하지 않는다.

## 단계별 TBT 검증 게이트

| 단계 | 질문/시험 | 필요한 관측 | 결과가 말해 주는 것 | 다음 단계 조건 |
|---|---|---|---|---|
| A. 대상 고정 | ADB로 product/build/API와 설치된 Dashboard 버전을 특정하고, 읽을 파일 계약을 확인 | 기존 probe의 getprop/package/service; 설치 버전은 별도 허용된 명령으로 확인 필요 | 정확한 기기·APK 출처만 고정. 패키지·서비스 존재는 접근권이나 TBT 증거가 아님 | 계획·앱 소스와 설치본이 다른지 기록 |
| B. 기록 경로 양성 대조 | 위 background media-log 시험 | media receiver 등록, CarPlay media received/processed, 사후 private log readback | 숨긴 Activity 동안 현재 미디어 이벤트를 기록·회수할 수 있는지 확인 | TBT producer 시험의 기록 인프라 검토. 이 단계 성공만으로 TBT 진행 승인 아님 |
| C. 수동 OEM log 확인(조건부) | CarPlay 경로 시작 전부터 시작/전환/취소 구간을 포함한 제한된 logcat 버퍼를 사후 ADB로 읽고, 허용 문자열·앱 tag만 로컬 필터링 | 구간 marker, 로그 시각/버퍼, producer PID/tag 및 값 유형. ring-buffer 크기와 보존은 시험 당시 확인 | 상관된 명시적 maneuver 구조 데이터가 발견되면 producer 후보. 문자열 미발견은 손실/필터/버퍼 덮어쓰기 때문에 부재 증명 아님 | 구조·출처·단위·취소 이벤트를 정적으로 따라갈 충분한 단서가 있어야 후속 수신 설계. 이번 호스트 스크립트에는 미포함 |
| D. 수신 계약·권한 확인 | 특정 OEM Binder/API가 확인된 경우 문서·manifest·permission·서비스 구현을 먼저 대조하고 별도 권한으로 최소 읽기 수신 PoC 계획 | service descriptor, transaction/method, UID/permission check, listener semantics, lifecycle, 실제 producer caller | 공개 읽기 API인지, OEM 서명/권한 또는 system process가 필요한지 판정 | 일반 앱에서 안전한 read-only 구독이 가능한 근거가 있을 때만 앱 구현/기기 시험 요청 |
| E. 실제 maneuver 양성 대조 | 실행 가능한 reader가 준비된 뒤 정차·동일 빌드·동일 iPhone에서 경로 없음/시작/기본 안내/방향 변경/거리 감소/취소/CarPlay 종료를 반복 | timestamped type+fields, raw-value 보존 정책, 수신·저장 오류, 별도 관찰자가 본 화면 조작시각 | 수신 값이 시나리오 변화와 반복 상관되는지; 같은 payload를 다른 앱/세션으로 오인하는지 | 2회 반복과 누락/취소/종료 처리 확인 후에만 변환 설계 |
| F. 앱 변환 검증 | 수신 값을 maneuver 상태 모델로 바꾸되 HUD 출력 없이 저장/진단 화면만 확인 | source/session, raw enum, 변환 enum, 거리 단위, timestamp, stale/unknown/clear | 입력 계약과 앱 변환의 일치 및 stale 값 제거 확인 | 미정의 enum/단위/종료 동작이 남으면 구현 불가 |
| G. HUD 출력 | 입력 검증과 별도 작업 | 사용자가 명시적으로 허용한 정차 시험, output API, 순정 안내 소유권, 취소/복원 동작, 실제 표시 | 출력 계약과 물리 화면 효과 | 별도 승인 전 호출 금지. `NavigationCluster.updateTBTInfo`는 방향 데이터 입력 API가 아니라 클러스터 쪽 송신 API다. |

### C 단계의 제한과 사전조건

이전 조사에서 한 번 제한된 logcat 조회가 있었으나 당시 실시간 CarPlay 안내 여부와 ring-buffer 보존 범위가 확인되지 않아 결과는 미검출로만 기록됐다. 다음 logcat 검토를 하더라도 지금 작성하는 스크립트에서 자동으로 실행하지 않는다. 독립적인 다음 단계로 명시해 구간 설계·필터·보존 한계를 먼저 검토한다. logcat은 TBT 전문 저장소가 아니며, **관련 로그가 안 보임 ≠ 차량이 받지 않음**이다. payload·개인정보를 포함할 수 있는 원문은 우선 보관·외부 출력하지 않는다.

## 구현 가능성 판정

| 후보 경로 | 현재 근거 | 현실성/필요한 추가 작업 | 주요 실패 조건 |
|---|---|---|---|
| 일반 Dashboard가 기존 JS iAP2 binder에 listener 추가 | JS DiPO가 `Iap2SvcInit`에 support type `0x89`(일부 조건 `0x8d`)와 listener 이름 `DiPO`를 넣는다. `Iap2HandleNoti.checkListenerType()`의 packed-switch는 `what=0..0x43`이고 기본값은 `-1`. 조사한 `Iap2Service` dispatcher는 `notiType >= 0` 및 listener support-mask 검사 후 callback 전달한다. | **현재 알려진 API만 붙이는 접근은 낮은 가능성.** 일반 Dashboard manifest에 전용 iAP2 권한·service binding이 확인되지 않았고, JS에 RouteGuidance API/mask도 발견되지 않았다. 새 코드보다 우선 현재 빌드의 binder ACL/UID 및 exact caller 경로를 확인해야 한다. | 권한 거절, `-1` 분류의 전달 제외, capability 부재, native가 데이터 생산 안 함. 임의 listener는 등록 부작용이 있어 읽기 전용이라 가정할 수 없다. |
| **native iAP2 수신 callback hook (우선 검증)** | 별도 보존본 `libiap2.so` SHA-256 `7916f0136adf617e74817cfd0b54fe3e1959d6a8be2b7ea2a0a20c214e2f8224`, ARM32. Export `_ZN7android25iAP2ClientTransportRecvCBEPvPhi`, RVA `0x912d` (Thumb code `0x912c`). `iAP2TransportRecvThread`는 `read(fd, buffer, capacity)` 성공 뒤 읽은 길이와 버퍼를 callback으로 넘긴다. Callback 본문은 `arg1` 바이트와 `arg2` 길이를 `iAP2PacketParseBuffer`에 전달하고, 완성 packet만 `iAP2LinkRunLoopHandleReadyPacket`으로 넘긴다. | **parser 전 수신 iAP2 callback 바이트를 보존할 구체적인 hook 지점이 정적으로 확인됨.** 사용자 지시에 따라 callback 전용 read-only Frida capture 초안을 [`carplay_iap2_capture.js`](../diag/js_kr/carplay_iap2_capture.js)에 추가했다. 시작은 명시적 RPC 호출만 허용하고 파일은 신규 생성, queue 4 MiB·단일 조각 64 KiB 제한, 기록 순번/시각/원문 bytes 및 drop footer를 보존한다. 이 지점은 물리 USB URB/token 캡처가 아니다. 실제 장착본의 hash/RVA, 프로세스·UID, callback 호출, iPhone T맵 구간의 기록, 파일 쓰기 권한은 MacBook 실차 세션에서 확인해야 한다. | OEM 프로세스에서 Frida attach 불가, symbol/RVA 또는 라이브러리 hash 차이, callback이 해당 세션에서 호출되지 않음, 파일 권한/용량 문제, capture queue drop, 계층 이전에서 패킷 변환/암호화가 있음. callback 기록만으로 TBT 의미·T맵 출처를 단정할 수 없음. |
| `DiPO` 시스템 상태에서 안내 중 여부만 읽기 | 별도 HyundaiB 코드에서 `getCurrentDiPOMode().getTurnByTurn()` 후보가 있었음. 동일 JS API/권한/값 갱신은 확인되지 않음. | **상태 indicator 후보, maneuver는 아님.** JS artifact에서 service/parcel/API 및 권한을 맞춰 보고, 권한이 낮은 독립 읽기인지 확인한 뒤 정차 중 raw 값 변화만 시험. | 서비스 없음/null/권한거절/다른 enum 의미. true라도 방향·거리·목적지를 제공하지 않음. |
| Dashboard 화면/Surface 캡처와 OCR | JS DiPO는 Surface를 native로 전달하는 정적 경로가 있음. 과거 동일 기기 조사는 일반 Dashboard 별도 UID, `READ_FRAME_BUFFER` 미요청 및 API17 `MediaProjection` 부재를 기록. | **일반 앱 경로로는 매우 낮음.** 캡처 permission/capability 자체가 선행되어야 하고, 지도 UI가 화면에 보일 때만 얻는 픽셀은 structured input보다 취약하다. 사용자가 과거 화면 capture/OCR 중단 결정을 내렸으므로 이 조사에서는 재개하지 않는다. | CarPlay 화면이 다른 Surface/하드웨어 plane에 렌더링됨, 권한, 화면 회전/레이아웃 변화, 가림·지연, OCR 오인식. |
| 순정 내비 broadcast/provider를 CarPlay용으로 재사용 | 기존 observer는 MNsoft 순정 내비의 route-state/destination 상태뿐. | **CarPlay maneuver input으로 부적절.** 별도 진단 신호로만 유지. | 같은 시각에 순정 내비 상태가 바뀌어도 CarPlay가 발신했다는 출처 연결이 없음. |
| 다른 지도 앱 SDK/휴대폰 companion/별도 동글 | 해당 앱/제품과 계약 조사 기록 없음. | **현환경 우선순위 낮음.** 실제 iOS 앱 API 계약이나 별도 수신 hardware가 확인될 때만 대체안. 사용자의 주 경로인 티맵+순정 유선 CarPlay를 대체하지 않는 조건에서 비교한다. | 임의 앱이 Tmap 경로를 노출한다고 가정할 수 없음. 동글은 추가 하드웨어·다른 세션·호환성 검증이 필요. |

`Iap2HandleNoti` 범위의 default `-1`과 `Iap2Service`의 nonnegative/mask gate 연결은 이전 보고서의 “알림 처리 위치가 불명확”한 부분을 좁힌 새 정적 증거다. 이 사실은 **해당 Java 알림 전달 경로에서 분류되지 않은 `what`가 listener로 통과하지 않는다**는 뜻이다. native의 다른 notification producer, 다른 session, 실제 iPhone 송신 유무까지 부재를 증명하지 않는다.

## 다음 순서와 산출물

1. **MacBook `실차 데이터 준비` 세션**에서 장착본 build fingerprint, 프로세스와 로드된 `libiap2.so` hash/RVA, UID 및 Frida attach 권한을 ADB 연결 때 읽기 전용으로 대조한다. 보존본 hash와 다르면 RVA hook을 실행하지 말고 장착본을 다시 분석한다.
2. 후킹이 가능한 경우 `carplay_iap2_capture.js`를 해당 프로세스에 주입하고, 저장 경로 쓰기 권한과 신규 파일 생성·회수만 정차 상태에서 먼저 검증한다. 명시적으로 `startCapture(path)`를 호출하기 전에는 hook이 비활성 상태여야 한다. 테스트 파일은 원문이므로 비공개 로컬에만 둔다.
3. ADB를 분리하고 동일 USB 포트로 iPhone을 연결한다. **대기 → CarPlay 연결 → T맵 경로 안내 전 → 안내 시작/진행 → 경로 취소 → CarPlay 해제** 각 구간 시각을 별도 기록한다. iAP2 bytes는 모두 capture하고 TBT로 사전 필터링하지 않는다.
4. USB를 다시 ADB로 바꾸고 capture를 정지·회수한다. 파일 길이/header/footer, 순번 gap/drop 수, SHA-256, 조각 원문과 시간순서를 검증한다. drop 또는 프로세스 종료가 있으면 해당 구간은 불완전으로 표시한다.

   회수본은 원본 파일을 그대로 보존한 채 호스트에서 확인한다. payload를 하나의 연속 stream으로 풀 때는 반드시 새 경로를 지정한다.

   ```sh
   python3 scripts/decode_carplay_iap2_capture.py /private/tmp/carplay-iap2.capture \
     --payload-out /private/tmp/carplay-iap2.stream.bin
   ```
5. 안내 전·안내 중·취소 뒤 바이트를 비교해 후보 message/frame을 찾아 재현 가능한 원문 구간을 특정한다. 그 뒤에만 재조립/메시지 해석을 하고, iPhone T맵 출처와 실제 maneuver 의미는 독립적인 반복 관측으로 검증한다. raw iAP2 packet 존재만으로 TBT라고 부르지 않는다.
6. 실패 시 순서는 **실제 프로세스/UID 및 권한 → callback 도달 여부 → 파일 쓰기/USB 전환 생존성 → queue overflow/지연 → 메시지 식별**이다. logcat 요약·미디어 이벤트·지도 앱 비교로 주 경로를 대체하지 않는다. HUD 출력은 입력 캡처와 분리하며 단계 G 제한을 유지한다.

기존 probe의 host-only 테스트는 `python3 -m unittest tests.test_carplay_adb_probe`로 실행한다. 새 `--check events-current`/`events-previous`는 TBT 검사기가 아니라 bounded private event-log 회수기다. 현재 파일이 비어 있거나 `run-as`가 막혀 있으면 `unknown`/`denied`로 처리하고 데이터 부재를 만들지 않는다.
