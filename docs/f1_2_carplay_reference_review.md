# F1-2: iOS 26 CarPlay 공개 이미지 검수

2026-11-18 KST. 공개 이미지와 Apple 문서를 사용했다. 시뮬레이터는 실행하지 않았다.

[Apple CarPlay HIG](https://developer.apple.com/design/human-interface-guidelines/carplay)는 800×480px(5:3)을 흔한 화면 크기 중 하나로 열거한다. [Live Activities HIG](https://developer.apple.com/design/human-interface-guidelines/live-activities#CarPlay-dimensions)도 표준 표시 구성으로 800×480을 제시한다. **공개 자료에서 iOS 26 이상과 800×480 실제 화면이 함께 확인되는 이미지 5장은 확보하지 못했다.** 5:3이라 해도 패널 해상도, CarPlay 출력, 웹에 게시된 이미지 크기는 다를 수 있다. [Digital.ai의 테스트 사례](https://docs.digital.ai/continuous-testing/docs/te/test-execution-home/troubleshooting/smart-zoom-error-carplay)에서는 Smart Display Zoom으로 요청 800×480과 캡처 850×510이 달랐다.

[9to5Mac의 iOS 26 실사용 갤러리](https://9to5mac.com/2025/06/09/carplay-in-ios-26-liquid-glass-app-widgets-refreshed-apps/)에서 아래 다섯 화면을 열어 비교했다. 브라우저에 전달된 이미지의 자연 크기는 모두 **1280×720(16:9)**. 실제 차량 출력 모드는 확인되지 않았으므로 구조를 비교하는 자료로 썼다.

| 공개 이미지 | 확인한 패딩·정렬·표면 |
| --- | --- |
| [앱 그리드](https://i0.wp.com/9to5mac.com/wp-content/uploads/sites/6/2025/06/CarPlay-iOS-26-26.jpeg?ssl=1) | 네 열과 두 행이 같은 피치를 쓴다. 작은 이름표는 아이콘 바로 아래 중앙에 있고, 페이지 표시는 별도 하단 행이다. 왼쪽 도크는 같은 배경 위의 세로 캡슐 하나에 시계, 최근 앱, 홈을 묶는다. |
| [외관 선택](https://i0.wp.com/9to5mac.com/wp-content/uploads/sites/6/2025/06/CarPlay-iOS-26-9.jpeg?ssl=1) | 하나의 둥근 팝오버 안에서 세 가지 외관과 세 가지 밝기 옵션의 열 중심을 공유한다. 선택 항목에만 내부 캡슐을 사용하며, 뒤쪽 앱 그리드는 유지한다. |
| [달력 위젯](https://i0.wp.com/9to5mac.com/wp-content/uploads/sites/6/2025/06/CarPlay-iOS-26-6.jpeg?ssl=1) | 월 제목과 날짜 묶음이 가까이 있고, 날짜는 7열과 일정한 행 간격으로 잡는다. 날짜마다 상자를 두르지 않고 현재 날짜만 원으로 강조한다. |
| [대시보드와 음악 타일](https://i0.wp.com/9to5mac.com/wp-content/uploads/sites/6/2025/06/CarPlay-iOS-26-23.jpeg?ssl=1) | 지도 한 장 옆에서 우측 두 카드의 좌우선이 맞고, 카드 사이에 좁고 연속된 틈이 있다. 앨범·제목·아티스트·진행 바·이전/재생/다음이 한 타일 안에 있으며 제어마다 외곽선이 없다. |
| [설정 목록](https://i0.wp.com/9to5mac.com/wp-content/uploads/sites/6/2025/06/CarPlay-iOS-26-16.jpeg?ssl=1) | 같은 그룹의 행이 아이콘, 라벨, 오른쪽 화살표·스위치 열을 공유한다. 줄마다 독립 카드 반경을 적용하지 않고 그룹의 바깥에만 둥근 모서리를 둔다. |

약 341×192 CSS px로 표시한 자료에서 눈금으로 잰 근삿값: 앱 도크 폭 약 36px(화면 폭의 10~11%), 앱 아이콘 폭 약 48px, 중심 간격 약 65px, 아이콘 아래 이름표까지 약 3px. 대시보드는 지도·카드 사이와 우측 카드의 위아래 틈이 약 3~4px(화면 폭의 약 1%), 모서리의 곡선이 시작되는 지점은 카드 가장자리에서 약 11~14px. 표시본과 육안 판독 기준이므로 실제 해상도의 px/pt나 Apple 토큰으로 환산하지 않았다. 사진·배율이 다른 화면 간에는 이 수치를 직접 비교하지 않는다.

[Apple의 Liquid Glass 설명](https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass)과 [WWDC25 디자인 세션](https://developer.apple.com/videos/play/wwdc2025/356/?time=726)은 조작 계층을 콘텐츠에서 구분하고 중첩 모서리의 중심을 맞추는 원리를 설명한다. 텍스트 인셋과 반경의 고정 비율은 확인되지 않았다. 같은 중심에서 안쪽 모서리를 그릴 때 바깥 반경에서 인셋을 빼고, 텍스트는 실제 글자 폭·높이에 맞춰 별도로 배치했다.

## F1-2 적용과 검증

앱에 포함되는 F1-2의 전체 조망은 오리지널과 같은 5열×4행에 같은 카드 순서를 사용한다. 시안의 3열×3행 배치는 디자인 원본에만 남긴다. Liquid Glass와 Liquid Glass (Flat)은 같은 그라데이션 배경, F1 레이아웃과 좌측 메뉴를 사용한다. Flat은 Glass를 상속하며 박스 재질만 `flat`으로 지정한다.

이전에는 F1 8개 사각형이 모두 본문 안에 들어왔지만, 위아래 카드가 맞닿고 음악 바의 좌우선도 본문과 달랐다. 엔진과 속도는 설계 좌표상 y=120, 높이=158로 같았다. 속도에만 추가 패딩 4/10/6/20과 별도 가운데 정렬이 있어 숫자와 라벨 기준선은 달랐다.

- JSON에는 좌표 대신 6개 열, 4개 행 가중치, 8개 요소의 시작 셀과 차지할 행·열 수를 적었다. 변속 LED는 전체 폭, 기어는 두 행, 엔진과 속도는 같은 행, 페달과 브레이크는 반씩 차지한다. 엔진과 속도의 실제 높이는 부모 크기와 음악 바 표시 여부에 따라 함께 계산된다.
- 외곽 16, 카드 사이 10, 가로 텍스트 인셋 14는 **F1-2의 800×480 설계값**이다. 세로 인셋은 높이의 18%까지로 제한한다. 엔진·속도의 라벨, 단위, 숫자 기준선을 맞추고, 온도·부스트와 페달·브레이크는 라벨과 값을 각 행 좌우에 정렬했다.
- 미디어 바는 본문과 공통 외곽 16을 공유한다. 재생 제어는 각 터치 영역 중앙에 놓인다. 현재 [공통 테마 엔진](theme_engine_v3.md#역할별-표면-재질)은 F1 카드, 메뉴, 미디어 버튼과 설정 그룹에 동일한 재질 규칙을 적용한다. 그리드 반경 18은 레거시 표면의 배치값이며, flat과 glass의 반경은 공통 재질에서 읽는다.
- 스타일과 입력은 실제 Android 소스와 같은 배치 계산을 사용하는 Java2D 정적 렌더로 확인했다. 기본, 음악 결합, 하단 바 숨김에서 8/8 경계 포함·겹침 없음·엔진/속도 높이와 라벨/숫자 기준선 일치·하단 정렬을 확인한다. 다른 화면 비율에서는 설계 화면을 균일 축소한다. Android 기기와 실차에서는 검증하지 않았다.
