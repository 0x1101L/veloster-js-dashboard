# 테마 생성 질문표

- 목적: 사용자 의도와 참고자료를 엔진이 지원하는 테마 선택으로 연결한다.
- 적용 기준: 현재 테마 엔진 v3와 실제 schema/runtime 계약
- 연동 문서: [테마 엔진 v3](theme_engine_v3.md), [Material 3·Android Auto 리서치 규격](MATERIAL_AUTO_THEME_RESEARCH_SPEC_20261119.md), [Apple Typography HIG](https://developer.apple.com/design/human-interface-guidelines/typography)
- 상태: 작성용 기준 문서. 기입한 답은 디자인 승인이나 구현·기기 검증 완료를 뜻하지 않는다.

## 작성 원칙

이 질문표는 Android Auto나 Material 화면을 복제하기 위한 것이 아니다. 사용자가 지정한 시각 언어와 참고자료를 이 Dashboard의 기존 기능·화면 구조에 맞춰 독립 테마로 만든다. 참고 UI의 기능을 이 프로젝트가 제공하지 않으면 아이콘과 동작을 흉내 내지 않는다.

새 테마는 별도 theme ID와 파일로 자체 색상·형태·타이포그래피·배치 스타일을 선언한다. 기존 Original의 외형을 테마 상속으로 가져오지 않는다. 각 테마의 Overview는 5열×4행, 한 화면으로 고정한다. 이 규칙은 질문이나 선택 항목이 아니다.

각 응답에는 선택값, 근거, 상태를 기록한다. 상태는 **미정**, **제안**, **확정**, **해당 없음**, **엔진 확장 필요** 중 하나를 쓴다. 엔진에 없는 속성은 지원되는 것처럼 질문하거나 JSON에 임의로 추가하지 않는다.

## 참고자료와 시각 근거

스타일, 이미지, 명확한 출처를 참고자료에 넣으면 출처 링크와 이미지 출력물을 함께 남긴다. 이미지는 문서에서 바로 볼 수 있게 첨부 파일을 저장소에 두고 상대 경로로 삽입한다. 출처 페이지·이미지 원본 URL, 작성자/게시자, 자료 제목, 확인 날짜, 사용 범위나 라이선스를 같이 기록한다. 저작권·라이선스를 확인하지 못한 이미지의 경우 임의로 저장소에 복제하지 말고 출처 페이지 링크와 직접 확인 가능한 이미지 미리보기를 제공한다.

아래 링크는 Android Auto 시스템 UI의 시각적 계층을 참고하기 위한 외부 자료다. Dashboard의 기능·위치·치수 규격을 정의하지 않는다.

[Google 공식 Android Auto 시스템 UI 개요](https://developers.google.com/cars/design/android-auto/product-experience/system-ui/overview) · [탐색 바 구성](https://developers.google.com/cars/design/android-auto/product-experience/system-ui/nav-bar) · [상태 표시줄 구성](https://developers.google.com/cars/design/android-auto/product-experience/system-ui/status-bar)

참고 링크: [Navigation bar](https://developers.google.com/cars/design/android-auto/product-experience/system-ui/nav-bar), [Status bar](https://developers.google.com/cars/design/android-auto/product-experience/system-ui/status-bar), [Widgets](https://developers.google.com/cars/design/android-auto/product-experience/system-ui/widgets). Google 자료는 별도 표기가 없는 한 CC BY 4.0이며, 적용 범위는 시각적 요소와 계층 참고에 한정한다.

## 질문표

| 구분 | 질문 | 응답에 기록할 것 | v3 연결 및 제약 |
|---|---|---|---|
| 목표 | 테마 이름과 만들려는 분위기는 무엇인가? | theme ID, 표시명, 한 문장 콘셉트, 사용 환경 | manifest와 catalog 규칙 확인 |
| 참조 범위 | 참고자료에서 가져올 시각 요소는 무엇인가? | 색, 대비, 표면, 도형, 글꼴, 아이콘 스타일, 정보 위계별로 출처 지정 | 기능 복제와 시각 참고를 구분 |
| 화면 | 어떤 화면을 설계하는가? | Overview, F1, 게이지, 그래프, 퍼포먼스, 설정, 진단 중 대상 | 각 화면별 JSON 역할 및 schema 확인 |
| Overview | 20개 항목을 어떤 우선순위로 보여줄까? | 카드 ID/source, 배치 순서, span, 생략할 항목 | 5×4 단일 화면 고정. 4×3·페이지 이동 금지 |
| 메뉴 필요성 | 항상 보이는 페이지 메뉴가 필요한가? | 필요 여부, 표시할 실제 페이지, 보조 진입점 | 메뉴 기능과 hit area는 기존 동작 유지 |
| 메뉴 위치·폭 | 상단 메뉴와 왼쪽 메뉴 중 어디에 둘까? 왼쪽이면 폭과 안쪽 여백은? | 위치, 폭, 정렬, 세로 padding, 선택 상태 | `shell.navigationPosition`, `sideMenuBar.width/alignment/padding`. 화면 바깥 margin은 두지 않음 |
| 메뉴 간격 | 시계·메뉴·돌아가기 묶음과 메뉴 버튼 사이를 얼마나 띄울까? | 그룹 간 gap, 버튼 간 itemGap, 논리 단위 | `sideMenuBar.gap`은 그룹 사이, `sideMenuBar.itemGap`은 메뉴 버튼 사이 |
| 메뉴 외형 | 버튼형인가, 공통 배경의 dock형인가? | buttons/dock, 공통 배경·버튼 배경, 밑줄/선택 강조 | dock은 왼쪽 메뉴에만 지원. `sideMenuBar.appearance`·surfaceStyles·fills·decorations |
| 메뉴 요소 | 시계·페이지 아이콘·설정·돌아가기를 어떻게 보일까? | 실제 기능이 있는 요소, 순서, 아이콘 굵기/크기/선택 표시 | 런처·마이크·알림 등 미지원 동작을 가짜로 표현하지 않음. 아이콘별 자산 설정 지원 여부 확인 |
| 색상 | 바탕·표면·전경·강조·상태색은 무엇인가? | 역할별 색상, 주야간 대비, 선택·비활성·경고·위험·미수신 예 | colors schema의 의미 토큰 사용. 상태 의미는 데이터·렌더러 계약 보존 |
| 배경 | 단색·그라데이션·무늬·이미지 중 무엇을 쓸까? 기본 배경은 포인트 컬러에 연동할까? | 기본 배경, 이미지/썸네일, 색상별 변형, 자르기 의도, 수동 선택 우선순위, 텍스트 가독성 | backdrop·assets.background·wallpapers. 엔진별 accent-linked default는 명시적 palette key로 연결하고 사용자 지정 배경은 우선 보존한다. 파일 형식과 경로 schema 준수 |
| 표면 재질 | 카드·메뉴·패널마다 어떤 면 처리를 쓸까? | flat/glass/legacy 역할 매핑, 투명도·블러·하이라이트 | material·surfaceStyles·surfaceBlur. Glass 설정이 없는 역할에는 효과를 가정하지 않음 |
| 모서리와 테두리 | 카드·메뉴·버튼·화면 외곽의 radius와 테두리는 어떤 스타일인가? | 곡선(circle/squircle), 반경, 외곽선 색/존재, 각 표면의 예시 | `shell.frame.cornerShape/cornerRadius`, material.radius, F1 전용 radius, colors.stroke 확인 |
| 간격·치수 | 프레임과 영역 사이 간격, 각 영역 안쪽 padding, 버튼 크기, 카드 padding과 grid gap은? | 논리 단위 값, 사용 화면 크기, 밀집/여유의 우선순위 | 큰 영역은 `shell.frame.gap`, 프레임 안쪽 가로·세로 여백은 `shell.frame.padding.horizontal/vertical`, 각 영역 안쪽은 해당 영역 `padding`, 반복 항목은 영역별 `itemGap`. `layout.padding`은 사용하지 않음 |
| 타이포그래피·글꼴 | 본문·숫자·디스플레이에 같은 글꼴을 쓸까? 가변 폰트가 있으면 사용할까? | 역할별 글꼴 파일/출처/라이선스, variable/static 여부, 한글·숫자·단위 글리프와 fallback | `fonts.body/numeric/display`, `fallbackWeights`. 가변 폰트와 API 17용 정적 굵기 파일 지원 |
| 타이포그래피·굵기 | 각 역할의 일반 텍스트와 강조 텍스트를 어느 굵기로 표시할까? | body/numeric/display별 normal·emphasis. Regular 400, Medium 500, Semibold 600, Bold 700 중 선택 | `fontStyles`가 역할별 400/500/600/700 지원. `weights`는 기존 auto/regular/bold 호환용. Thin/Light는 선택값으로 쓰지 않음 |
| 타이포그래피·크기 | 본문·숫자·강조 표시를 기준 크기 대비 어느 정도 키우거나 줄일까? 기어 숫자는 별도 크기 조정이 필요한가? | body/numeric/display 배율(%), gearSize/combinedGearSize(%), 작게 보이는 핵심 텍스트와 최소 가독성 검수 기준 | `typography` JSON에 배율 지정 가능. 본문·숫자·디스플레이 60–140%, 기어 64–220%, 결합 기어 64–160%. 기준 글자 크기 자체(px/pt), 행간·자간은 직접 지정 불가(엔진 확장 필요) |
| 타이포그래피·검수 | 어떤 실제 문구와 상태에서 글꼴 굵기·크기를 확인할까? | 한글/영문/숫자/단위 표본, 일반·강조 비교, 축소·잘림·겹침·대비 확인, 800×480 및 지원 변형 | Apple HIG는 가는 굵기를 피하고 Regular/Medium/Semibold/Bold를 권장한다. iOS 17pt/11pt를 차량 화면의 px로 그대로 환산하지 말고 실제 렌더러에서 가독성을 확인 |
| 미디어·위젯 | 미디어 바와 앨범 영역을 어떻게 보일까? | 공통 바 배경, 버튼별 배경, 화면별 표시 밀도 | mediaFooter·mediaControls와 공통 표시 설정 분리 |
| 상속·독립성 | 새 테마의 스타일을 어떻게 자체 선언할까? | 자체 선언 파일 목록과 신규 ID | 이번 작업의 신규 테마는 extends 없이 자체 선언 |
| 검수 | 어떤 화면·상태·언어·크기에서 확인할까? | 800×480, 지원 변형, 한/영, 기본·선택·경고·무효·미수신, 대비 | 실제 renderer 프리뷰, schema/runtime 검사, 합성 데이터 표기. Android/실차 결과와 호스트 결과 분리 |

## 기록 양식

아래 표를 테마별로 복사해 질문에 답한다. 미정 항목은 합리적 제안과 미정 이유를 적고, 사용자의 확정 전에는 승인된 디자인으로 표시하지 않는다.

| 항목 | 답변/선택 | 상태 | 근거·이유 | 엔진/schema 위치 | 참고 링크·이미지 |
|---|---|---|---|---|---|
| 테마 ID/이름 |  | 미정 |  | manifest/catalog |  |
| 참조 범위 |  | 미정 |  | 해당 없음 또는 관련 역할 |  |
| 메뉴 위치/폭/dock |  | 미정 |  | shell, components |  |
| 색상/상태색 |  | 미정 |  | colors |  |
| 배경/벽지 |  | 미정 |  | colors, components assets |  |
| 표면/radius/테두리 |  | 미정 |  | components, shell, 화면 schema |  |
| 글꼴 파일/출처/라이선스 및 variable/static 여부 |  | 미정 |  | fonts, fallbackWeights |  |
| body 글꼴·normal/emphasis 굵기 |  | 미정 |  | fonts, fontStyles.body |  |
| numeric 글꼴·normal/emphasis 굵기 |  | 미정 |  | fonts, fontStyles.numeric |  |
| display 글꼴·normal/emphasis 굵기 |  | 미정 |  | fonts, fontStyles.display |  |
| 본문/숫자/디스플레이 배율(%) |  | 미정 |  | typography.bodyScale/numericScale/displayScale |  |
| 기어/결합 기어 배율(%) |  | 미정 |  | typography.gearSize/combinedGearSize |  |
| 한글·숫자·단위 가독성 및 잘림 검수 |  | 미정 |  | renderer preview/tests |  |
| 간격/화면 배치 |  | 미정 |  | 화면별 layouts |  |
| 검수 범위 |  | 미정 |  | preview/tests |  |

## 엔진 지원 상태 기록

모든 질문은 아래 중 하나로 분류한다.

| 상태 | 뜻 | 작성·진행 규칙 |
|---|---|---|
| 지원 | 현재 schema와 실제 런타임이 읽고 그린다 | 파일/속성 및 유효 범위를 질문표에 적는다 |
| 조합 가능 | 한 전용 속성은 없지만 기존 역할·토큰 조합으로 표현 가능하다 | 조합법과 제약을 명시하고 런타임 결과를 확인한다 |
| 고정 규칙 | 사용자 선택을 받지 않는 공통 계약이다 | 질문으로 열지 않고 규칙과 검수 항목에 둔다 |
| 엔진 확장 필요 | schema, 파서/ThemeSpec, 렌더러, fallback 또는 테스트 중 필요한 지원이 없다 | 선택 가능한 기능처럼 제시하지 않는다. 별도 엔진 변경 제안과 승인을 거친 뒤 질문 항목을 활성화한다 |
| 미확인 | 문서와 코드의 지원 여부가 일치하는지 확인되지 않았다 | 코드를 확인하기 전에는 선택을 확정하지 않는다 |

예: 현재 v3에는 재질별 공통 radius와 화면 외곽 곡선 설정이 있다. 그러나 테두리 두께를 표면마다 독립 지정하는 필드는 확인되지 않았다. 따라서 radius는 지원 항목으로 묻고, 개별 border 두께는 엔진 확장 필요로 표시한다. 이 구분은 실제 schema와 구현이 바뀌면 다시 검증한다.

타이포그래피에서 사용자가 고르는 크기는 렌더러가 정한 기준 글자 크기에 곱하는 배율이다. 각 텍스트의 기준 px/pt를 직접 지정하거나 행간·자간을 조정하는 기능은 현재 v3 schema에 없다. Apple HIG의 17pt 기본·11pt 최소 크기는 iOS 포인트 단위이므로 Android 차량 화면에 숫자만 옮겨 적용하지 않는다. HIG의 가독성 원칙과 권장 굵기(Regular, Medium, Semibold, Bold)를 참고하고, 실제 Dashboard 해상도와 화면에서 확인한다. [Apple Typography HIG](https://developer.apple.com/design/human-interface-guidelines/typography)

## 동기화 규칙

질문표는 고정된 독립 체크리스트가 아니라 테마 엔진의 작성 인터페이스다. 다음 파일이나 동작이 바뀌면 같은 변경 작업에서 질문표를 검토하고 갱신한다.

1. `theme_engine_v3.md` 또는 후속 엔진 규격의 필드·허용값·기본값·제약·화면 계약
2. `theme/manifest.schema.json`, `theme/schema/*.schema.json`의 필드·범위·enum
3. ThemeSpec/ThemeCatalog의 파싱·상속·검증·fallback 동작
4. ThemeSurface와 화면 renderer의 표면·색상·radius·테두리·레이아웃 적용
5. 테마 생성·미리보기·검증 절차와 결과물 형식

변경 시 순서는 다음과 같다.

1. 코드와 schema를 확인해 실제 지원 계약을 갱신한다.
2. 질문표의 관련 질문, 예시 답, 지원 상태, 제약을 함께 수정한다.
3. 새 필드를 추가하면 지원 여부와 유효 범위, 검수 질문을 같은 변경에 넣는다.
4. 필드가 삭제·폐기되면 선택지에서 제거하고 마이그레이션/대체 경로를 적는다.
5. 이 문서와 양방향 링크를 유지하는 [테마 엔진 v3](theme_engine_v3.md), [리서치 규격](MATERIAL_AUTO_THEME_RESEARCH_SPEC_20261119.md), [문서 색인](README.md)을 확인한다.
6. 완료 기록에는 함께 검토한 schema·코드와 수행한 문서 링크/이미지 검사를 남긴다.

새 질문을 추가했는데 schema/runtime을 바꾸지 않았다면 **엔진 확장 필요** 또는 **미확인**으로 남긴다. 반대로 새 schema 필드를 추가하면서 질문표를 검토하지 않은 엔진 변경은 동기화 완료로 보지 않는다.

## 현재 예시 테마 자료

이 저장소의 `material_auto_cowork`는 질문표를 채울 때 참고할 수 있는 시안이다. 이 값들은 공통 기본값이나 사용자의 사전 승인으로 취급하지 않는다. manifest는 부모 테마를 상속하지 않으며, Overview는 자체 5×4 단일 화면으로 선언한다.

- [테마 리서치·구현 규격](MATERIAL_AUTO_THEME_RESEARCH_SPEC_20261119.md)
- [테마 구현 기록](MATERIAL_AUTO_THEME_IMPLEMENTATION_20261119.md)
- [테마 자산: catalog 및 manifest](../apps/dashboard/src/0x1101L/assets/themes/catalog.json)
- [테마 색상](../apps/dashboard/src/0x1101L/assets/themes/material_auto_cowork/colors.json)
- [테마 재질·간격·배경](../apps/dashboard/src/0x1101L/assets/themes/material_auto_cowork/components.json)
- [테마 메뉴·shell](../apps/dashboard/src/0x1101L/assets/themes/material_auto_cowork/layouts/shell.json)
- [테마 글꼴·타이포그래피](../apps/dashboard/src/0x1101L/assets/themes/material_auto_cowork/fonts.json)

새 테마 작업 때 이 문서를 작성하고, 확정된 답과 참고 이미지/출처를 구현 보고서에 연결한다.
