# AVRCP Cover Art research / 앨범아트 연구

[Documentation](README.md) · [Experiments](../experimental/README.md) · [Build released apps](BUILD.md)

**Status reviewed 2026-11-02:** the private vehicle trial remains blocked at SDP discovery; no Cover Art image transfer or display has been confirmed. The following implementation/build notes were written before that vehicle follow-up and are retained as technical history. Statements about pending installation describe that earlier stage. See [current verification](STATUS.md).

**현재 상태:** 실차 시험에서 SDP 단계가 해결되지 않았습니다. 아래 구현·빌드 메모의 설치 전 표현은 당시 기록이며 이미지 수신 성공을 뜻하지 않습니다. 원래 연구 내용은 보존했습니다.

**Publication scope (2026-11-02):** The experimental sources remain local and are not included in this repository update. The preserved implementation notes and commands below describe that research workspace; they are not build instructions for a fresh public checkout.

**공개 범위:** 이번에는 연구 기록만 공개하며 실험 소스는 로컬에 남겼습니다. 아래 경로·명령은 당시 연구 작업 공간 기준입니다.

## Experimental AVRCP Cover Art core / 실험용 앨범아트 수신 코어

`experimental/avrcp-cover-art/` contains a transport-independent Java 7 receiver. It is **not included in the released APKs and is not an installable Bluetooth upgrade**. A separate experimental native/OEM adapter and signed local trial builds are now available as described below. Android/iPhone interoperability remains untested.

```sh
python3 experimental/avrcp-cover-art/build.py \
  --java /path/to/jdk17/bin/java \
  --out build/cover-art-core
```

The command builds `avrcp-cover-art-core.jar`, runs 20 host scenarios, and writes `validation.json` with source/artifact hashes. Tests use synthetic SDP/OBEX packets and a generated 200×200 JPEG; no phone, network or vehicle connection occurs. Optional `--r8-jar /path/to/r8.jar --android-jar /path/to/android.jar` checks single-file DEX035 encoding for min API17. D8 uses host JDK core types as well as the Android SDK; this is not an Android runtime/API compatibility test. Outputs go into a new directory and are never installed automatically.

Implemented:

- Bounded SDP ServiceAttributeList parsing: AVRCP TG/version/feature detection, dynamic Cover Art PSM selection, UUID16/32/128 handling.
- Directed OBEX CONNECT, connection-ID handling, BIP GetLinkedThumbnail with a seven-digit image handle, multi-packet GET and DISCONNECT.
- Packet framing over a deadline-aware channel; malformed/truncated packet rejection, 512 KiB image limit, 12-second receiver deadline and response-count limit. JPEG envelope validation is followed by bounded bitmap decoding in the eventual UI integration.
- One active transfer and one replaceable pending track. Cancellation closes active I/O; generation checks suppress stale results and queued UI updates. Handles are fetched after CONNECT and never cached across sessions.

For a raw-channel integration, implement `CoverArtReceiver.Backend.discover`, `open`, and `imageHandle` using the **same OEM Bluetooth stack**. The native trial below instead uses the OEM OBEX engine directly and does not pretend it is a raw `PacketTransport`. `discover` returns one assembled AVRCP TG SDP attribute list; `open` provides Classic BR/EDR L2CAP to its discovered PSM; `imageHandle` requests attribute 8 for the current player/track after OBEX connection. All methods must honor timeouts, and transport `close()` must be prompt, idempotent, and interrupt I/O. The downloaded OEM build currently filters attribute 8 in native builders/service callbacks and rejects app-facing L2CAP; no reflection fallback or version-only patch is provided. A compatible native/service extension is a prerequisite.

Use a track token containing phone-connection epoch, addressed-player identity and track UID/revision, not the displayed song title alone. Call `cancel()` on UID invalidation, disconnect, source change or screen suspension. Listener callbacks run on the worker and must return promptly. Post to the UI thread, then use `publishIfCurrent(generation, action)` to atomically discard stale work before passing valid JPEG bytes into the existing artwork path. The core is not yet wired to `MediaState` or `VehicleDash`.

아래 native 시험본에는 서비스·계기판 연결 코드를 추가했습니다. **실차 Bluetooth 연결과 화면 동작 검증은 남아 있습니다.** 기존 APK의 기능·설치 절차는 바뀌지 않습니다. 휴대폰의 실제 Cover Art 지원 여부는 연결 시 확인해야 하며 Android/iPhone 전체 기종 지원을 보장하지 않습니다.

Protocol references: [Bluetooth SIG AVRCP 1.6.3](https://files.bluetooth.com/wp-content/uploads/dlm_uploads/2024/10/AVRCP_v1.6.3.pdf), [AOSP BIP server](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/1afe5d2553821e2651510fd7b4f5cbaadf567bd4/android/app/src/com/android/bluetooth/avrcp/AvrcpBipObexServer.java). This module is independently written project code under the repository MIT license; OEM implementation files are not included.

### Offline OEM system experiments / 오프라인 시스템 수정본

The `experimental/avrcp-cover-art/tools/` scripts prepare two **incomplete research patches**, using privately supplied, exact-hash inputs from the reviewed downloaded 260114 firmware. They reject other builds. These outputs are not release APKs or an update package; they do not implement the image transport or change the advertised AVRCP version. No install command is provided.

- `patch_native_attributes.py` changes four instruction bytes across the two HAL entry wrappers and `AVRC_BldCommand`, allowing requested attribute ID 8 in GetElementAttributes and GetItemAttributes. It saves an exact original copy beside the experimental library. It does not add automatic requests.
- `patch_oem_callbacks.py` preserves existing Mobis metadata processing and adds delivery through three already-declared raw Binder callbacks. It produces one smali class; APK rebuilding, ODEX handling, signing and Android runtime validation remain separate work. Existing callback registration and permission checks are unchanged.
- `emulate_native_attributes.py` uses Unicorn and pyelftools to execute the actual original/patched ARM builder. Forty executions compare the complete outgoing payload against expected packets: existing IDs, ID 8, invalid IDs, mixed requests and OEM metadata requests. This tests the preallocated packet path only, not JNI, response buffers, allocation or radio behavior.

```sh
# Read-only validation and patch plan; no output files without --out.
python3 experimental/avrcp-cover-art/tools/patch_native_attributes.py \
  --source /private/path/bluetooth.default.so

# Optional offline native execution; requires pyelftools and unicorn.
python3 experimental/avrcp-cover-art/tools/emulate_native_attributes.py \
  --source /private/path/bluetooth.default.so \
  --report /private/path/new-emulation-report.json

python3 -m unittest discover -s experimental/avrcp-cover-art/tools -p 'test_native_attributes.py'
python3 -m unittest discover -s experimental/avrcp-cover-art/tests -p 'test_patch_oem_callbacks.py'
```

Java patch tests use `AVRCP_OEM_SMALI=/private/path/AvrcpControllerService.smali` for optional exact-input tests; without it four original-fixture tests are skipped. The private validation also assembled and disassembled the patched class at API17: 248 existing methods were instruction-identical, three were changed, and three helpers were added. Assembly is not Android verifier or vehicle validation.

Integration must preserve normal metadata. Requesting only `[8]` through the existing Element callback can clear OEM title/artist fields. The candidate request is the OEM five IDs plus 8: `[1,2,3,7,6,8]`. Do not append 8 to all seven original attributes: the response dispatcher appears to have seven-entry temporary storage, and full JNI/buffer validation is pending. A successful builder test does not establish end-to-end request safety.

The internal native OBEX/ERTM-L2CAP path is a possible integration route, but it requires stack-thread scheduling, verified SDP/session ownership and lifecycle handling. It is not the app-facing L2CAP socket interface, which this firmware rejects. Internal OBEX already implements framing, so it cannot simply be passed to the Java raw `PacketTransport` interface; a native protocol adapter or a separately verified raw channel is still required.

속성8 요청 제한을 푸는 라이브러리와 응답 전달용 서비스 클래스의 **오프라인 시험본**을 만들었습니다. 실제 이미지 전송부, 실차 설치본과의 대조, Android/iPhone 연결 검증은 남아 있습니다. 기존 공개 APK와 설치 절차에는 변경이 없습니다. 순정 코드가 포함된 생성물은 공개 저장소에 넣지 않습니다.


### Native transport and private trial APKs / native 전송부와 비공개 시험 APK

The experimental tree now contains a buildable same-process native adapter, OEM lifecycle integration and a staged dashboard receiver. Local complete Bluetooth and dashboard APKs were assembled and signed with an external key matching the previously captured vehicle signer. This is **experimental implementation and offline validation**, not a tested Bluetooth upgrade or a release. No OEM bytes or keys are distributed in this source tree.

`native/backend.c` uses the existing `bta_dmexecutecallback` queue, SDP discovery and OBEX/ERTM-L2CAP functions. It connects before requesting a session-specific image handle. Callbacks copy bounded packet contents and defer subsequent commands until the OEM state transition completes. Allocation failures, pending track replacement, late responses and deadlines are handled without freeing an unconfirmed active session. `native/wire.c` validates complete OBEX packets and bounded JPEG assembly.

`native/sdp_tree.c` serializes only pointers and nodes bounded within the reviewed discovery DB. The OEM raw-copy route remains disabled because its empty-response branch can overread. Tests executed the actual OEM ARM SDP parser and passed its resulting DB to this serializer for five UUID/integer cases. Host native tests also run with AddressSanitizer and UndefinedBehaviorSanitizer, including concurrent shutdown versus queue submission. OEM operations are mocked in host lifecycle tests; radio and Android runtime behavior are not simulated.

`android-service/` hooks source/device/track lifecycle and correlates metadata responses with the actual transaction label. The six-attribute request preserves ordinary OEM metadata. Java verifies the exact patched HAL file SHA256 before loading; native initialization checks that HAL is already mapped and that all four patched instructions are present in memory. The extension never initializes a competing Bluetooth stack.

`dashboard/` adds the receiver to a private source copy. It requires a signature permission owned by `com.android.bluetooth`, an explicit package target, a fresh subscription nonce, and matching track/generation state. Valid JPEG bytes enter the existing artwork loader; USB artwork remains on its existing path. The experimental dashboard manifest and resources are identical to released v6.

Reproduce native tests and an ARM32/API17 library with a native host Clang/LLD and an external NDK toolchain sysroot supporting API17:

```sh
python3 experimental/avrcp-cover-art/tools/build_native.py \
  --clang /path/to/clang --lld /path/to/ld.lld \
  --ndk-toolchain /path/to/ndk/toolchains/llvm/prebuilt/host \
  --out /private/new-native-build

python3 experimental/avrcp-cover-art/dashboard/build_apk.py \
  --java /path/to/jdk17/bin/java --android-jar /path/to/android.jar \
  --r8-jar /path/to/r8.jar --aapt2 /path/to/aapt2 \
  --out /private/new-dashboard-trial

python3 experimental/avrcp-cover-art/tools/build_oem_trial.py --help
python3 experimental/avrcp-cover-art/tools/sign_trial.py --help
```

The local native build used LLVM/LLD23 and the r22b API17 sysroot; the [Android NDK archive](https://github.com/android/ndk/wiki/Unsupported-Downloads) identifies the official archive/checksum. This older sysroot is used for the vehicle API level. These tools do not alter system toolchain defaults. `build_oem_trial.py` requires private exact-hash originals and a built native library, then preserves original resources/JNI and audits all original classes/methods. `sign_trial.py` takes external key/certificate files and a signed reference APK, verifies matching signer identity, and preserves all non-signature payload bytes. Neither tool installs anything.

**Known lifecycle limitation:** after OEM service stop/cleanup, the extension stays disabled until the Bluetooth process restarts. This prevents queued work from reaching a torn-down OEM stack. A session with no confirmed terminal close remains bounded and quarantined; it is not forcibly freed. Firmware version advertisement remains unchanged; this is not a declaration of full AVRCP1.6 compliance.

Before any vehicle installation, compare the currently installed HAL, Java/ODEX/framework/native dependencies, resolve the target package/ODEX and native-library deployment route, and preserve originals. Read-only collection is provided:

```sh
# Plan only: invokes no ADB.
python3 experimental/avrcp-cover-art/tools/collect_device.py

# Explicitly selected connected device; local output must remain private.
python3 experimental/avrcp-cover-art/tools/collect_device.py \
  --collect --adb /path/to/adb --serial ACTUAL_SERIAL \
  --out /private/new-bluetooth-capture
```

The collector reads system components only; it does not install, reboot, change radio settings, copy pairing records or issue ECU requests. A missing/mismatched component is reported, not automatically patched. Parked Android/iPhone tests must cover artwork, unsupported peers, track changes, calls, source switching, reconnects and full reboot before any release claim.

native 이미지 전송부, 서비스 연결, 계기판 수신 코드와 **서명된 로컬 시험 APK**까지 준비했습니다. 실제 차량 설치·휴대폰 연결·표시 검증은 미실시입니다. Bluetooth 서비스 종료 뒤에는 프로세스 재시작이 필요하다는 제한이 있습니다. 공개 배포 APK·일반 설치 절차는 바뀌지 않았고, 시험용 순정 수정 APK는 공개하지 않습니다.

Final path audit also found attribute filters in both HAL entry wrappers before the command builder. The final generator patches all four sites. The earlier two-byte experiment cannot send attribute 8 end to end and is superseded; its old hash must not be used with the current service. The final patched HAL SHA256 is `d58a5ec283b2cf21e377e0eea2f29dcf654a20559f1b9f51ca180fb99947dae3`.

The final HAL-wrapper audit executes both original ARM wrapper functions through the actual command builder: 26 cases confirm that the original rejects attribute 8, the four-site patch emits the six requested IDs, and invalid IDs/count limits remain rejected. Static frame analysis also confirms that the six-entry request/response fits the reviewed stack locations. This does not harden the original stack against arbitrary oversized peer responses.
