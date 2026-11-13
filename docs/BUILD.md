# 공개 소스 빌드와 테스트

[Documentation / 문서 안내](README.md) · [Status / 확인 범위](STATUS.md)

## 설치 파일과 빌드 결과 구분

바로 설치할 파일은 `apks/`의 기존 서명된 배포본입니다. 아래 공개 빌더는 **서명 전 APK**까지만 만듭니다. 개인키·OEM SDK 구현·순정 APK·차량 로그가 필요하거나 포함되지 않으며 차량에 연결하지 않습니다.

새 코드를 실제 차량에 설치하려면 해당 기기가 허용하는 서명과 Android 4.2 호환 V1 서명 형식이 필요합니다. 아무 로컬 키로 서명하면 되는 것으로 확인하지 않았습니다. 기존 설치본 업데이트에는 서명 일치도 필요합니다. 이 배포에는 키 발급·OTA 서명·차량 신뢰정책 변경 기능이 없습니다.

## 준비물

- Python 3.9 이상과 호환되는 JDK (`java`, 같은 디렉터리의 `javac`). JDK 17을 필수 버전으로 요구하지 않습니다.
- 외부 Android SDK `android.jar` API 16 또는 17. 공개 준비 검증은 기존 API 16 JAR로 컴파일하고 min/target SDK는 17로 지정했습니다.
- AAPT2 실행 파일과 D8가 포함된 R8 JAR. 도구는 사용자 설치 경로를 인자로 전달하며 이 폴더에 배포하지 않습니다.

기본 빌드 스크립트는 javac에 Java 7 source/target 옵션을 전달하고 D8 `--min-api 17`을 사용합니다. 설치된 JDK가 이 옵션을 지원하지 않으면 해당 빌드의 실제 컴파일 설정을 명시하고 호환성을 검증합니다. JDK 24에서는 이전 시험처럼 외부 래퍼로 source/target을 8로 바꿀 수 있으며, 이 경우 Java 7 컴파일을 주장하지 않습니다. Android API 17은 JDK 버전과 별개입니다. 제조사 API 선언은 별도 클래스 디렉터리에 컴파일해 javac의 classpath에만 넣고, 앱 DEX에 패키징하지 않습니다. 실제 실행에는 차량의 OEM `automotive` 라이브러리가 필요합니다.

## 호스트 테스트

```sh
python3 scripts/test.py --java /path/to/jdk/bin/java
python3 -m unittest discover -s tests -p 'test_*.py'
python3 scripts/verify_release.py
```

테스트는 임시 폴더에서 컴파일하고 끝나면 삭제합니다. Telemetry 유효성·60초 만료·TPMS·추가 신호·프레임 분할/오류 처리와 Bluetooth/무선 시험/재시작의 상태 정책을 검사합니다. 합성 입력만 사용하며 실차 로그, Android 실행 환경, ADB가 필요하지 않습니다. UI·실제 Bluetooth 하드웨어·실차 통신을 재현하는 테스트는 아닙니다.

## 빌드

공개 폴더에서 아래 `/path/to/...`를 본인의 도구 경로로 바꿉니다. 기존 출력 폴더는 덮어쓰지 않으므로 다시 빌드할 때 새 `--out`을 지정합니다.

```sh
python3 scripts/build.py dashboard \
  --java /path/to/jdk/bin/java \
  --android-jar /path/to/android.jar \
  --aapt2 /path/to/aapt2 \
  --r8-jar /path/to/r8.jar \
  --out build/dashboard

python3 scripts/build.py hub \
  --java /path/to/jdk/bin/java \
  --android-jar /path/to/android.jar \
  --aapt2 /path/to/aapt2 \
  --r8-jar /path/to/r8.jar \
  --out build/hub
```

결과는 각각 `dashboard-UNSIGNED.apk`, `hub-UNSIGNED.apk`입니다. APK의 리소스·DEX 구성은 확인하지만 ZIP 정렬과 서명은 수행하지 않습니다. 기존 배포본과 바이트 단위 재현을 보장하지 않습니다. 도구 버전·타임스탬프·서명 과정이 영향을 줍니다.

배포 APK는 API 17, DEX 035, V1/JAR 서명 형식입니다. V2 전용 서명은 Android 4.2에 맞지 않습니다. 서명 배경은 [AOSP 앱 서명 문서](https://source.android.com/docs/security/features/apksigning)를 참고하세요. 새 서명본의 설치 성공과 차량 동작은 별도 검증해야 합니다.

## JS 서명 시험본 규칙

JS Dashboard 시험본은 외부의 공개 AOSP Android 4.2.2 platform 테스트 키로 V1 서명하며, `apks/JS-Dashboard-build-YYYYMMDD-HHMMSS.apk`에 저장합니다. 시각은 한국 시각입니다. 기본 `build.py`의 unsigned 빌드는 유지합니다. 서명 빌드는 다음 전용 래퍼를 사용합니다.

```sh
python3 scripts/build_js_apk.py \
  --java /path/to/jdk/bin/java \
  --android-jar /path/to/android-17/android.jar \
  --build-tools /path/to/android-sdk/build-tools/36.1.0 \
  --key /outside/repository/platform.pk8 \
  --cert /outside/repository/platform.x509.pem
```

키·인증서는 `menu-entry/tool-sources.json`의 고정 해시와 일치해야 하며 저장소 밖에 둡니다. 래퍼는 빌드·ZIP 정렬·API17 V1 서명·인증서 확인 후 APK만 `apks/`에 복사하고 기존 파일을 덮어쓰지 않습니다. `N-Dashboard-v*-API17.apk`는 rollback 폴더를 포함해 이 저장소에 두지 않습니다. 과거 JSN APK는 원본 업스트림 ZIP을 사용합니다.

JS 설치 후보를 새로 빌드할 때는 확인된 기존 설치본보다 `versionCode`를 높입니다. 설치본을 확인할 수 없으면 마지막으로 확인된 설치본을 기준으로 하고, 실제 설치 전 대상 버전을 다시 확인합니다. 빌드 시각(KST), 파일명, SHA-256, 패키지, versionCode/versionName, 기준 Git 커밋과 빌드에 반영된 미커밋 입력을 기록합니다. 커밋만 기록해 변경된 작업 트리가 커밋과 동일하다고 오인하지 않도록 합니다. APK별 항목은 `release.json`의 `js_trial_apks`, `SHA256SUMS`, `VALIDATION.json`과 연결된 프로젝트의 조사 기록에 반영합니다. 빌드와 기기 설치·실행 검증은 구분합니다.

JS 시험본은 `release.json`의 `js_trial_apks`에 분리합니다. 기존 JSN 설치기는 이 APK를 선택하지 않으며 원본 JSN 묶음을 안내하고 중단합니다. 이 빌드에는 실차 설치·실행 결과가 없습니다. versionCode 9는 과거 별도 versionCode 8 시험본과도 구분합니다.

## 공개 폴더를 유지할 때

개인키, 원본 차량 로그/스크린샷, VIN·기기 일련번호·페어링 주소, 순정 APK/ODEX/펌웨어, 개인 백업은 추가하지 않습니다. `build/`, `private-backups/`, 키 확장자는 `.gitignore`에 제외되어 있습니다. 새 바이너리를 배포하면 `release.json`, `SHA256SUMS`, `VALIDATION.json`과 확인 범위를 함께 갱신합니다.

## 실행 바로가기와 선택형 메뉴 도구

현재 Hub 소스는 v10 이후의 미배포 후보이며 OEM 화면 복귀·QuickBoot 방송 처리를 추가했습니다. 매니페스트 버전은 릴리스 준비 전까지 v10으로 유지하므로 새 빌드와 기존 배포 APK를 해시로 구분합니다. 부팅 시 직접 켜 둔 바로가기만 복원합니다. 호스트 검사는 실제 서비스·리시버를 작은 Android 스텁과 실행하지만 실제 차량 표시/부팅 전달을 대신하지 않습니다.

[단일 설치 명령](INSTALL.md)은 Python 표준 라이브러리와 ADB만 사용합니다. 빌드 도구나 키가 필요하지 않습니다. [선택형 OEM 메뉴 재구성](MENU_ENTRY_ko.md)에만 별도 JDK·Androguard·smali·개인 원본이 필요합니다. 관련 소스는 `menu-entry/`와 `scripts/build_menu_entry.py`이며, 외부 키를 받아 해당 로컬 메뉴 APK를 서명하는 경로입니다. OEM 코드가 포함된 생성물을 공개하지 않습니다.

다국어 문자열은 `translations/en.json`, `ko.json`을 수정하고 `python3 scripts/generate_translations.py`로 Java 카탈로그를 갱신합니다. 빌더는 카탈로그 일치 여부를 검사하고 `res/values`, `res/values-ko`의 앱 이름 리소스도 컴파일합니다. 두 앱 소스에는 [v6/v10 이후 변경](NEMESIS_REVIEW.md#implemented-in-our-code-unreleased)이 포함됩니다. 과거 JSN v6/v10은 원본 업스트림 ZIP 기준이며, 이 저장소의 JS 시험본과 구분합니다.

## Experimental AVRCP Cover Art core / 실험용 앨범아트 수신 코어

Implementation, native transport, private trial builds and validation details are now in [AVRCP Cover Art research](EXPERIMENTAL_AVRCP.md). The existing experimental notes have been preserved there. These components are outside the released APKs; see [current verification](STATUS.md) and the [experiment index](../experimental/README.md).

앨범아트 구현·시험본 빌드의 기존 상세 내용은 [별도 연구 문서](EXPERIMENTAL_AVRCP.md)로 옮겼습니다. 일반 앱 빌드는 위 절차를 사용합니다.

<a id="offline-oem-system-experiments--오프라인-시스템-수정본"></a>
<a id="native-transport-and-private-trial-apks--native-전송부와-비공개-시험-apk"></a>
