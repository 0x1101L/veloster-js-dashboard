# Dashboard 테마 엔진 v3

기록: 2026-11-18 KST. v3는 번들 JSON을 실제 앱 로더가 읽는 형식이다. 선택 가능한 테마는 Original, Aurora Focus, Liquid Glass(`f1_2`), Liquid Glass (Flat)(`f1_2_flat`), Liquid Glass (Optics)(`f1_2_optics`)다. CarPlay Glass Light/Dark는 제거했다. 기존 사용자 설정 키와 남은 테마 ID는 유지하며 기본 선택은 Original이다.

테마의 시각 선택과 참고자료는 [테마 생성 질문표](THEME_CREATION_QUESTIONNAIRE.md)를 작성한다. 이 질문표는 본 규격과 `theme/schema/` 및 실제 ThemeSpec/renderer 계약에 맞춰 유지한다. 엔진·schema·렌더링 계약을 바꾸는 경우 같은 변경에서 질문, 지원 상태, 범위와 참고 예시를 함께 동기화한다.

## 파일과 상속

`themes/catalog.json`은 `schemaVersion: 3`과 등록할 `themeIds`를 가진다. 각 테마의 `manifest.json`은 `schemaVersion`, `id`, `displayName`, 선택적인 `extends`, `files`만 가진다. 부모는 같은 catalog에 등록된 ID다. 파일은 각 테마 디렉터리의 `name.json` 또는 `layouts/name.json`만 참조한다. `..`, 절대 경로, 외부 URL은 허용하지 않는다.

`files`에는 `colors`, `fonts`, `components`, `shell`, `f1`, `overview`, `gauges`, `graphs`, `performance`, `settings`, `diagnostics` 역할을 지정한다. 각 JSON의 최상위 속성과 값은 `theme/schema/<역할>.schema.json`과 기존 번들 및 `theme/themes/f1-2/layouts/`를 참고한다. manifest schema는 `theme/manifest.schema.json`, v2 호환 schema는 `theme/manifest_v2.schema.json`이다. Schema는 개별 문서 작성 보조이며, 병합 후 필수 속성, 카드 충돌, 여유 공간 등 최종 검증은 런타임이 수행한다.

부모를 먼저 해석하고 자식의 객체 속성을 재귀적으로 덮어쓴다. 배열은 통째로 교체하며 빈 배열은 목록을 비운다. 생략은 상속이고 `null`은 거부한다. 각 테마 파일은 부모를 수정하지 않는다. Aurora Focus는 Original을 상속하고 색상·간격·전체 조망만 덮어쓴다. Liquid Glass는 부모 없이 색상·폰트·구성·레이아웃을 자체 파일에 선언한다. 상속된 폰트·배경은 해당 속성을 마지막으로 지정한 테마의 asset 디렉터리에서 로드한다. 8단계를 넘는 상속, 순환, 카탈로그 밖 부모, 중복 ID, 중복 JSON 키, 잘못된 경로·타입·값·알 수 없는 속성, 누락 또는 과도한 문서는 전체 catalog를 내장 Original 하나로 복구한다. 각 문서는 최대 32 KiB, catalog는 16 KiB, 테마 문서 합계는 2 MiB다. 폰트·이미지를 찾지 못하면 기존과 같이 플랫폼 기본 폰트·배경을 사용한다.

v2 catalog와 v2 단일 manifest는 명시적 호환 경로로 읽으며 레거시 좌표를 사용한다. v3 catalog는 v2 부모를 참조할 수 있다. v1과 미지원 버전은 복구한다. 내장 Original은 기존 색상·구조로 검증하고 변경을 거부한다. Original을 부모로 사용하는 다른 ID에는 override가 허용된다. `dashboard_theme/theme_id`와 언어·게이지·그래프·전체 조망 설정 키를 바꾸지 않는다.

`fonts.json`의 선택적인 `weights`는 역할별 `auto/regular/bold`를 지정한다. `auto`는 기존 렌더러의 굵기 요청을 유지한다. 폰트 파일 경로와 `typography` 크기 배율은 v2와 동일하며, 굵기는 리소스 로더의 캐시된 face 선택에 적용한다.

## 역할별 표면 재질

`components.json`의 선택 속성 `surfaceStyles`에서 역할별로 `legacy`, `flat`, `glass`를 지정한다. 부모의 역할 설정은 키별로 상속한다. 상속을 포함해 지정이 없는 역할은 `material.style`을 따른다.

```json
{
  "surfaceStyles": {
    "navigationButton": "glass",
    "controlButton": "flat",
    "mediaButton": "glass",
    "dataCard": "flat",
    "panel": "flat",
    "settingsGroup": "flat"
  }
}
```

| 역할 | 적용 대상 |
|---|---|
| `navigationButton` | 상단·좌측 페이지 전환, 설정, 돌아가기 |
| `controlButton` | 게이지 보기 전환, 그래프 기간, JS 진단 탭과 동작 버튼 |
| `mediaButton` | 하단 바와 와이드 플레이어의 미디어 조작 버튼 |
| `mediaContainer` | 하단 바와 와이드 플레이어의 공통 배경. 미지정 시 각각 `panel`, `dataCard` 재질을 따름 |
| `navigationDock` | 왼쪽 시계·메뉴·돌아가기를 감싸는 공통 배경 |
| `dataCard` | F1 데이터 카드, 전체 조망 카드, 와이드 미디어 카드 |
| `gearCard` | F1 기어 카드. 미지정 시 `dataCard` 재질을 따름 |
| `panel` | 게이지·그래프·퍼포먼스·진단 본문 패널, 하단 미디어 바 배경 |
| `settingsGroup` | 설정 목록의 행 묶음 배경 |

`flat`은 공통 단색 채움과 단일 테두리, `glass`는 Liquid Glass 협력 엔진의 알파 그라데이션·단일 하이라이트·그림자를 사용한다. 같은 스타일은 역할에 관계없이 같은 `material.radius`와 재질 값을 사용한다. 반경은 박스 크기에 맞게 제한한다. 테마 색상과 선택 강조, 콘텐츠 및 터치 영역은 각 요소가 유지한다. `legacy`는 역할의 기존 그리기 방식을 사용한다. `f1Grid.radius`는 레거시 F1 카드와 하단 바의 모서리에 사용된다.

`ThemeSurface`는 역할 재질을 선택하고 `glass` 표면을 전용 `LiquidGlassEngine`에 위임한다. 엔진은 공통 테마 엔진과 같은 `ThemeSpec`·캔버스·역할 계약을 사용하며 별도 렌더링 스택을 만들지 않는다. Glass 재질을 하나라도 사용하는 테마에서는 UI 설정에 투명도, 블러 강도, 하이라이트 단계를 표시한다. 투명도는 감소·덜 투명·표준·더 투명·매우 투명 다섯 단계이며, 블러는 끔·낮음·표준·높음·매우 높음이다. 블러는 테마가 해당 역할에 지정한 강도에 0·0.5·1·1.7·3배를 적용하고 36으로 제한하므로, 테마의 기본 강도가 0인 역할은 흐려지지 않는다. 투명도 감소는 기존처럼 블러를 끄고 표면을 불투명하게 한다. 선택값은 `ThemeSettingsStore`에 저장되고, Glass 재질과 해당 테마의 반투명 flat 표면에 적용된다. Flat처럼 Glass 역할이 없는 테마에는 이 설정을 숨기며 기존 렌더링 경로를 유지한다.

`components.json`의 `mediaFooter`는 하단 미디어 바 전용이다. `buttonBackground`는 개별 버튼의 배경을, `containerVisible`은 바 전체의 공통 배경을 각각 켜거나 끈다. 버튼의 레이블·재생 기호, 곡 정보, 앨범아트, 터치 영역과 바 표시 정책은 유지한다. Liquid Glass는 `{"buttonBackground":false,"containerVisible":true}`를 지정하고 Flat과 Optics는 이 설정을 상속한다. 기존 `controls: "text"`와 `"buttons"`는 각각 `buttonBackground: false`와 `true`에 해당하며, 두 속성이 함께 있으면 `buttonBackground`가 우선한다.

와이드 플레이어는 `mediaControls`의 `both`(기본값), `container`(공통 배경 위에 개별 배경 없는 아이콘), `buttons`(개별 버튼 배경만 표시)를 사용한다. Liquid Glass 계열은 `container`를 사용한다. 하단 바에서 상속 후에도 값이 빠져 있으면 해당 항목에 기존 `mediaControls` 동작을 적용한다.

`surfaceBlur`는 `dataCard`, `gearCard`, `settingsGroup`, `mediaContainer`, `navigationDock`을 키로 받아 배경 블러를 0–36 논리 단위로 지정한다. 생략 또는 0이면 블러를 그리지 않는다. `flat`·`glass` 표면 안에만, 화면 바탕과 배경 이미지를 포함한 흐린 사본을 표시하고 그 위에 기존 채움과 테두리를 그린다. 숫자·아이콘·다른 UI는 블러의 입력이 아니다. 화면 크기나 배경이 바뀔 때 캐시를 갱신한다. 정적인 배경 블러이며 동적 반사나 굴절은 제공하지 않는다.

`shell.json`의 `shell.navigationAppearance`는 `buttons`(기본값) 또는 왼쪽 메뉴용 `dock`이다. 독을 사용하면 활성 테마의 재질로 공통 배경을 그리고 개별 버튼의 배경과 테두리를 숨긴다. 현재 선택 항목의 강조와 버튼의 터치 영역은 유지한다. 왼쪽 시계 글자는 영역에 맞춘 크기에서 10% 줄인다.

`components.json`의 `decorations`는 표면 재질과 별도로 직선 장식을 제어한다.

```json
{
  "decorations": {
    "overviewCardTopLine": false,
    "navigationUnderline": "off"
  }
}
```

`overviewCardTopLine`은 생략 시 `true`다. `navigationUnderline`은 선택한 메뉴 버튼에만 적용한다. `auto`(기본값)는 `glass` 이외의 메뉴 재질에 밑줄을 그리고, `on`·`off`는 재질과 관계없이 표시·숨김을 지정한다. F1-2는 두 장식을 끈다. 메뉴를 둘러싼 둥근 테두리와 선택 색상에는 영향을 주지 않는다.

`components.json`의 `fills`는 `navigation`(일반 메뉴), `navigationSelected`(선택 메뉴), `gaugeToggle`(게이지 보기 전환)의 채움색을 `#AARRGGBB`로 지정한다. 미지정 시 `navigation`은 `tokens.panel`, 나머지는 `tokens.panelRaised`를 쓴다. 상속받은 색상을 지우고 토큰을 쓰려면 `"token"`을 지정한다. `colors.json`의 `palette`는 해당 색상 토큰을 쓰는 `theme`(기본값) 또는 렌더러가 원래 제공하던 색상을 쓰는 `componentDefaults`다. Original은 기존 색을 보존하는 데 이 모드를 사용한다.

전체 화면 배경은 `colors.json`의 `backdrop`을 따르며 표면 재질과 독립적이다. `style: solid`(기본값)는 `tokens.background` 단색이다. `gradient`는 `startColor`에서 필수 `endColor`로 이어진다. 시작색을 생략하거나 `"background"`로 지정하면 `tokens.background`를 쓴다. `endXPercent`는 화면 하단의 그라데이션 끝점 가로 위치다(0–100, 기본 65). `scanlineColor`는 가로 무늬 색상이다. 모든 스타일에 지정할 수 있고, 알파가 0이면 무늬가 없다. `legacy`는 기존 가로 무늬 색을 기본값으로 제공하는 단색 배경이다. 가로 무늬는 이미지가 있다면 이미지 위에 그린다.

화면 바탕색인 `tokens.background`와 그라데이션의 `startColor`·`endColor`는 불투명한 `#FFRRGGBB`만 허용한다. v2의 `material.backdropEnd`에도 같은 규칙을 적용한다. `tokens.panel`·`tokens.panelRaised`와 버튼 채움색은 알파를 허용한다. Flat은 채움색의 알파를 그대로 쓰고, Glass는 채움의 RGB에 `material.topAlpha`·`material.bottomAlpha`를 적용한다. 가로 무늬는 배경 위 효과이므로 `scanlineColor`에도 알파를 허용한다.

이 설정들은 속성별로 상속한다. 번들 Original도 배경·팔레트·채움색을 JSON에 명시한다. 로더는 이전 v2/v3 Original 파일을 같은 외형으로 변환하며, 번들 검증·선택·복구에는 기존 ID를 사용한다. 예전 v3의 `material.backdropEnd`는 읽을 수 있지만 배경에는 적용하지 않는다.

Liquid Glass는 `material.style: "glass"`와 자체 그라데이션 배경을 정의한다. Liquid Glass (Flat)은 `extends: "f1_2"`인 자식 테마이며, 자체 `components.json`은 `{"material":{"style":"flat"}}`만 지정한다. 색상, 배경, 글꼴, 레이아웃, 장식과 나머지 재질 값은 부모에서 상속한다. 기존 `f1_2` 선택은 Glass를 가리킨다.

Liquid Glass (Optics)도 `f1_2`를 상속한다. 정보 카드·기어·설정 그룹은 낮은 블러의 flat 표면이고 미디어 컨테이너와 왼쪽 독은 더 강한 블러를 쓴다. 와이드 플레이어는 `mediaControls: "container"`로 공통 배경과 아이콘을 표시한다. `shell.navigationAppearance: "dock"`은 왼쪽 메뉴 뒤에 표면을 추가한다. 바탕색·배경 이미지·글꼴과 배치 치수는 부모 값을 사용한다.

## 배치와 입력

`layouts/shell.json`의 `shell.screenCorner`는 디스플레이 외곽과 모든 둥근 UI 표면에 쓸 곡선을 선택한다. `circle`(기본값)은 일반 원호이고, `squircle`은 직선에서 부드럽게 이어지는 4차 초타원 곡선이다. `sqircle`도 같은 스쿼클로 읽는다. 곡선 모양만 공통으로 바꾸며 카드·버튼·이미지·타일의 기존 개별 반경은 유지한다. `shell.screenCornerRadius`는 디스플레이 외곽 반경이며 800×480 기준 정수 0~240을 받는다. 상속 후 생략되었거나 값이 0이면 외곽은 직각이다. Liquid Glass가 `squircle`과 32를 선언하고 Flat과 Optics가 상속한다. 외곽 반경은 화면 배율을 적용하고 짧은 변의 절반으로 제한한다. 메뉴·본문·미디어를 그린 뒤 외곽선 바깥의 네 귀퉁이를 검은색으로 덮는다. 위젯 배치와 터치 좌표는 유지한다.

기본 좌표는 800×480이며 화면을 상단 메뉴·컨텐츠·미디어 세 영역으로 나눈다. 상단 메뉴 모드에서는 메뉴 영역을 화면 전체 폭의 독립된 상단 띠로 배치하고, 그 아래 컨텐츠와 미디어를 나눈다. 폭 1800px 이상·높이 700px 이상인 와이드 패널(예: CN7 1920×720)에서는 아래 영역 폭의 5/7을 컨텐츠, 오른쪽 2/7을 상시 표시하는 미디어 영역으로 배정한다. 컨텐츠는 할당받은 가용 폭·높이로 레이아웃을 계산하며, 선언형 F1 그리드와 전체 조망 카드는 컨텐츠 영역 안에서 재배치한다. 이때 [오른쪽 플레이어](f1_media_bar.md#와이드-화면의-오른쪽-플레이어)를 단일 미디어 UI로 사용하고 본문 음악 패널, 하단 바, 곡 변경 알림은 표시하지 않는다. 800×480 계열에서는 모든 페이지에서 공통 하단 미디어 바를 사용할 수 있고, 바의 앨범아트를 눌러 현재 페이지에서 음악 패널을 열 수 있다. 하단 바와 알림 설정은 일반 화면에서만 사용할 수 있다. 왼쪽 메뉴 모드는 상단 메뉴를 숨기며 왼쪽 메뉴가 컨텐츠 폭을 차지한다. 전체 테마 배경은 세 영역 아래 디스플레이 전체에 먼저 그린다.

쉘의 `position`이 `left`이면 상단 메뉴 영역을 숨긴다. 세로 메뉴는 컨텐츠 영역 안에서 폭을 차지하고 컨텐츠 폭은 그만큼 줄어든다. `position: top`이면 메뉴를 컨텐츠와 미디어 위쪽의 화면 전체 폭 상단 메뉴 영역에 배치한다. 파일 생략도 기존 상단 배치다. 메뉴 버튼 위치는 전체 상단 메뉴 폭에 맞춰 계산한다.

좌측 시계는 위 패딩, 돌아가기는 아래 패딩에 고정한다. 페이지·설정·돌아가기 버튼은 같은 크기의 정사각형이다. 한 변은 좌우 패딩을 뺀 폭과 모든 버튼이 세로로 들어갈 수 있는 높이 중 작은 값으로 정한다. 남는 폭이 있으면 버튼을 가운데 정렬한다. Original에서 사용자 설정으로 왼쪽 메뉴를 고르면 셸 폭은 96px이며 좌우 추가 패딩은 없다. `shell.buttonHeight`는 좌측 시계 영역의 높이이며 테마 파일의 기존 검증 범위는 유지한다. `alignment`는 그 사이에서 현재 보이는 페이지와 설정 버튼 묶음을 정렬한다. 좌측 전체를 별도 패널색으로 덮지 않고 본문과 같은 전체 화면 배경을 사용한다. 그라데이션은 위치에 따라 색이 변하지만 좌측 경계에서 레이어가 끊기지 않는다. F1-2의 좌측 메뉴 폭은 80px, 버튼은 48 × 48이며 좌측 버튼의 모서리 반경은 한 변의 1/4 이하로 제한해 정사각형 윤곽을 유지한다. flat 메뉴 버튼은 별도 선택 밑줄을 표시하지 않는다. 전체 조망 카드도 상단 직선 장식을 표시하지 않는다.

`shell.padding`은 좌측 메뉴의 네 방향 여백이다. 선택 속성 `shell.topPadding`은 상단 메뉴 버튼의 네 방향 여백이며, 메뉴 높이는 버튼 높이 38에 위·아래 여백을 더한 값이다. 선택 속성 `shell.mediaPadding`은 와이드 미디어 영역에서 플레이어 카드까지의 네 방향 공통 여백이며 플레이어 배율을 따른다. 두 속성을 생략하면 기존 배치를 유지한다. F1-2는 상단 메뉴 위·아래 8, 좌·우 16을 사용하고, 좌측 메뉴·F1 콘텐츠 외곽·와이드 미디어 카드의 기준 여백은 네 방향 16이다. F1 콘텐츠의 인접 가로 여백에는 아래 공통 규칙을 적용한다.

설정 → UI 설정에서 테마, 메뉴 위치, 페이지 메뉴, 메뉴 표시 방식을 선택한다. **아이콘** 모드에서는 설정을 제외한 상단 버튼을 같은 폭으로 표시한다. 상단 시계는 별도 영역에 맞추고 상단 돌아가기 높이는 일반 메뉴 버튼보다 낮다. 그리기와 클릭은 같은 경계를 사용한다. 메뉴 위치는 테마 기본값·상단·왼쪽이며 `dashboard_theme/menu_position`에 테마 선택과 별도로 저장된다. 현재 테마의 본문 배치를 수용할 수 없는 위치는 선택할 수 없다. 테마를 바꾼 뒤 저장된 위치가 해당 테마에 맞지 않으면 저장값은 유지하고 그 테마의 기본 위치로 표시한다.

**왼쪽 메뉴 독**은 메뉴가 실제로 왼쪽에 있을 때만 **메뉴 위치** 바로 아래에 표시한다. **테마 기본값**은 현재 테마의 `navigationAppearance`를 따르며, **사용함**·**사용 안 함**은 테마를 바꿔도 유지된다. 설정은 `dashboard_theme/menu_dock`에 저장하고 테마 미리보기에도 반영한다. 상단 메뉴에서는 저장값을 유지하면서 일반 메뉴를 표시한다.

UI 설정 → 배경화면은 카탈로그에 등록된 모든 테마의 배경을 모아 보여준다. 이미지가 없는 테마는 기본 배경색과 그라데이션·스캔라인을 하나의 선택 항목으로 표시하며, 다른 테마에서도 그대로 선택할 수 있다. `components.json`에서 `assets.background`만 지정해도 기본 이미지와 축소 썸네일이 목록에 추가된다. 추가 배경은 `wallpapers` 배열에 전역 고유 `id`, 표시 이름, 이미지 파일, 썸네일, 기본값 여부로 선언한다. 기본값으로 표시할 항목은 최대 하나이며 해당 테마의 `assets.background`와 같은 파일이어야 한다. 상속한 이미지는 한 번만 표시한다. 선택은 `dashboard_theme/wallpaper_id`에 저장되고, **테마 기본값**을 누르면 활성 테마의 이미지나 기본 색 배경으로 돌아간다. F1-2 기본은 1920×1152 Blue이며 Red·Green·Mono도 선택할 수 있다. 이미지는 비율을 유지해 화면을 채우고 넘치는 부분을 가운데서 자른다.

페이지 메뉴의 체크박스로 퍼포먼스·전체 조망·F1·게이지·그래프의 표시를 선택한다. JS 진단은 설정 첫 화면에서 열며 페이지 메뉴의 표시 여부와 무관하다. 적용 시 `pit_vehicle_ui/page_menu_visible_mask`에 기존 페이지 ID 비트(0–4)를 저장하며 기본값은 31(전체 표시), 0(전체 숨김)도 유효하다. 잘못된 값·타입은 전체 표시로 복원한다. 취소하면 기존 값을 유지한다. 선택은 테마에 관계없이 상단·좌측 메뉴에 적용되며, 숨김으로 생긴 빈자리는 압축하고 그리기와 터치가 같은 목록을 따른다. 설정·시계·돌아가기는 항상 유지한다. 현재 화면을 숨겨도 그 화면을 즉시 닫거나 데이터 수신을 중단하지 않는다.

테마 엔진 Java와 번들 자산은 각각 `apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/`, `apps/dashboard/src/0x1101L/assets/themes/`에 둔다. `ThemeLayout`도 `0x1101L` 소스에 둔다. 앱 내부 자산 경로는 기존 `themes/`를 유지한다.

`ThemeSurface`는 메뉴와 페이지를 배치하기 전에 실제 캔버스 전체에 테마 배경을 그린다. 그라데이션과 레거시 가로 무늬는 화면 가장자리까지 이어진다. 배경 이미지는 종횡비를 유지한 채 화면을 덮도록 크기를 맞추고 화면 밖을 가운데 기준으로 자른다(center crop).

`DashboardPage.Content`가 메뉴·미디어 영역과 화면별 `viewport.padding`을 제외한 본문 사각형을 계산한다. 모든 페이지가 이 사각형의 폭과 높이로 위젯을 배치하며, 별도의 고정 비율 캔버스에 맞추지 않는다. 시각 요소와 터치 판정은 같은 영역을 사용한다.

기존 테마 파일 호환을 위해 `viewport.alignment`는 읽되 페이지 배치에는 사용하지 않는다. 공통 외부 여백은 테마의 `cardPadding`, F1은 `f1SidePadding` 또는 F1 그리드의 `inset`을 따른다. 선언형 전체 조망은 자체 `padding`을 더할 수 있다. 설정 행은 `settingsPadding`에서 4 단위를 빼고 최소 8로 제한한 안쪽 여백을 사용한다. 설정 화면은 공통 본문 여백 안쪽에 4 단위를 더 띄운다. 목록과 배경화면 그리드가 본문 높이를 넘으면 세로로 이어서 스크롤하며 화면 상단의 돌아가기와 적용은 고정한다.

가로 외곽 여백은 렌더러의 공통 배치 규칙을 따른다. 좌측 메뉴에 인접한 왼쪽과 활성 와이드 미디어 영역에 인접한 오른쪽은 각 레이아웃의 기본 여백 중 50%를 남긴다. F1 그리드·하단 바·일반 페이지에 동일하게 적용하며, 상단 메뉴만 있거나 우측 미디어 영역이 꺼진 쪽은 원래 여백을 유지한다. 화면별 viewport.padding, 상하 여백, 카드 사이 간격, 메뉴·플레이어 자체 패딩은 유지한다. 이 동작은 테마 설정이 아니며 모든 테마에 공통이다. 선택 근거와 렌더링 비교는 [F1-3 검수 기록](../@memory/theme_support/designs/f1_3/review.md)에 둔다.

F1의 `f1` 객체는 `shift`, `gear`, `speed`, `boost`, `rpm`, `temperatures`, `pedal`, `brake`의 사각형 `x/y/width/height`를 지정한다. x 범위 0–800과 y=52부터 최하단 노드까지의 설계 영역을 실제 본문에 대응시켜 **컨테이너 경계만** 계산한다. 컴포넌트 내부는 새 사각형에서 라벨·수치·바를 직접 배치한다. 글자는 폰트 크기를 균일하게 줄여 폭에 맞추며 LED는 원형을 유지한다. 미디어 바가 보이면 하단 54px를 예약하고, 숨기면 본문이 그 공간을 채운다. 미디어 결합 화면은 왼쪽 64%에 F1 노드를 다시 배치하고 오른쪽에 비율을 유지한 앨범아트·곡 정보를 배치한다. 그림·터치 모두 `Content.f1/album/footer`를 공유한다.

이름은 기존 의미별 컴포넌트를 가리키며 JS의 `boost` 자리는 비 JS에서는 오일 온도다. `temperatures`는 JS에서 냉각수·흡기, 비 JS에서는 기존 냉각수를 표시한다. 선택적인 `padding` 네 방향, `alignment`(`stretch/start/center/end`), `fontRole`(`body/numeric/display`), `colorRole`(`primary/secondary/accent/warning/critical`)을 지원한다. padding은 실제 노드 내부 여백이고, alignment는 각 텍스트 슬롯 안의 가로 정렬이다. `stretch`는 컴포넌트 기본 정렬이며 글자 변형을 뜻하지 않는다. 범위 밖·겹치는 노드와 실제 본문에서 내부 여백을 확보하지 못하는 설정은 거부한다. 목표 RPM은 별도 승인된 설정 페이지 편집기를 사용한다. 미디어 바 정책·수신 계약은 [공통 미디어 바·알림](f1_media_bar.md)를 참고한다.

별도의 F1 좌표나 그리드를 지정하지 않은 테마는 공통 F1 배치를 쓴다. 테마의 `spacing.gridGapX/Y`로 카드 사이를 띄우고, 외곽과 두 열 간격을 제외한 폭을 1:1:1로 나눈다. 번들 Original은 가로 16, 세로 12 논리 단위를 사용한다. F1-2는 자체 `f1Grid.gap`과 동일한 열 span으로 같은 규칙을 만족한다. 속도·기어·RPM 순서와 미디어 결합 여부가 바뀌어도 이 비율은 유지된다.

F1-2의 `f1Grid`는 기존 `f1` 좌표 대신 사용하는 선택 규격이다. 두 객체를 함께 정의할 수 없다. `columns`, `rowWeights`, `inset`, `gap`, `padding`, `radius`, `cells`로 구성하고, 각 요소에 `column/row/columnSpan/rowSpan`을 넣는다. 보이는 음악 바와 공통 외곽·간격을 먼저 확보하고 남은 본문을 열과 행 가중치에 따라 나눈다. 결합 화면의 미디어 카드와 앨범 터치 좌표도 같은 프레임에서 구한다. 잘못된 span, 겹치는 셀, 결합 화면 안에 들어오기 어려운 셀은 로딩 시 거부한다. 기존 좌표 방식은 그대로 쓸 수 있다. [설계 근거와 검수](f1_2_carplay_reference_review.md).

전체 조망(Overview)은 **항상 5열×4행의 단일 화면**이다. 테마는 그리드 크기를 변경하거나 이전/다음 Overview 페이지를 추가할 수 없다. 모든 Overview 정보는 20개 슬롯 안에 한 화면으로 배치하며 페이지 이동 UI·상태·저장을 만들지 않는다. `overview.columns/rows`는 각각 5와 4만 허용한다. 카드에는 고유 `id`, 기존 telemetry 인덱스 `source`, `column`, `row`, `columnSpan`, `rowSpan`을 지정한다. 카드 스키마에는 페이지 번호가 없다. 배열 순서가 그리기 순서다. 다른 ID는 같은 source를 참조할 수 있고, 스타일 설정은 기존대로 source ID별로 공유된다. 카드 중복 ID, 범위 밖 span, 겹침, 너무 작은 셀은 거부한다. 크기 검증은 shell·viewport를 제외한 실제 공간에서 수행한다. 패딩·gap은 실제 픽셀 간격으로 유지하고 남는 폭·높이를 그리드에 분배한다. 빈 목록은 허용한다. v3의 source는 실제 신호 ID이며 프로필에 따라 임의의 다른 신호로 바꾸지 않는다. JS의 source 1은 기존 DCT 온도 카드, source 34는 기존 연비 묶음 카드다. 다른 프로필에서 지원하지 않는 값은 기존 미수신 표현을 사용한다. v2 및 기본 레거시 배치는 기존 프로필별 슬롯 매핑을 유지한다.

### 전체 조망 카드 내부

`layouts/overview.json`의 `cardDefaults`는 일반 카드에만 적용한다. 그리드를 직접 선언하지 않는 기존 레이아웃에도 쓸 수 있다. 아래 예시는 기본 크기를 유지하고 제목과 값 사이 여백을 8, 값과 단위 사이를 6으로 바꾼다.

```json
{
  "cardDefaults": {
    "titleSize": 12,
    "valueSize": 24,
    "unitSize": 10,
    "unitPosition": "bottomLeft",
    "titleValueGap": 8,
    "titleUnitGap": 4,
    "valueUnitGap": 6
  },
  "cardTypes": {
    "tirePressure": {"rowGap": 4, "columnGap": 10},
    "dctTemperature": {"gaugeWidth": 30},
    "fuelEconomy": {"labelValueGap": 6},
    "stability": {"labelSize": 11}
  }
}
```

모든 수치는 논리 단위다. 일반 카드의 내부 패딩은 위·아래 6, 좌·우 `spacing.cardPadding`이며 `padding`에서 방향별로 덮어쓸 수 있다. `unitPosition`은 `topRight`(제목과 같은 행), `bottomLeft`, `bottomRight`, `valueRight`(값 오른쪽)를 지원하고 기본은 `bottomLeft`다. `titleValueGap`, `titleUnitGap`, `valueUnitGap`, `valueGaugeGap`의 기본값은 각각 4다. 제목과 단위는 글자 높이만 차지하고, 값은 제목과 하단 단위 사이의 남은 영역을 채워 세로 가운데에 놓인다. `valueRight`는 값과 단위를 같은 행에 배치하고 `valueUnitGap`만큼 띄우며 두 텍스트의 기준선을 맞춘다. 단위가 상단 오른쪽이면 제목과 한 행으로 묶이며 두 텍스트 사이에 `titleUnitGap`을 둔다. 막대 모드에서 막대는 값과 함께 중앙 배치되고, 원형 모드에서 게이지는 값 왼쪽에 온다. 막대 높이 `barHeight`와 원형 지름 `dialSize`의 기본값은 7과 36이다. 단위가 없는 신호는 단위 공간을 예약하지 않는다.

Liquid Glass와 Flat·Optics 파생 테마는 일반 및 특수 카드 모두 네 방향 패딩 12, 일반 카드 단위 위치 `valueRight`, 값과 단위 사이 간격 4를 사용한다. 이 계열의 전체 조망 그리드 간격은 가로·세로 각각 8이다.

| `cardTypes` 키 | 독립 배치와 설정 |
|---|---|
| `tirePressure` | 네 바퀴를 2×2로 표시하고 공통 단위를 한 줄에 둔다. 행·열 및 라벨과 값 사이 간격, 단위 크기와 간격을 지정할 수 있다. |
| `dctTemperature` | 온도 한두 줄과 오른쪽 게이지. 제목·행·라벨 간격과 게이지 폭 및 이격을 지정할 수 있다. 온도 단위는 값에 붙는다. |
| `fuelEconomy` | 실주행·계기판·주행가능거리 세 줄. 제목·행·라벨 간격을 지정할 수 있다. 각 단위는 값에 붙는다. |
| `stability` | TCS·ESC·ABS 세 줄. 제목·행·라벨 간격을 지정할 수 있다. |

특수 카드에는 종류별 기본 글자 크기·간격이 있으며 일반 카드의 `cardDefaults`를 상속하지 않는다. 각 블록의 생략한 필드는 부모 테마의 같은 블록에서 상속하고, 부모에도 없으면 내장 기본값을 쓴다. 기존 v2/v3 테마는 새 속성 없이 읽을 수 있고 내장 기본값으로 그린다. 내장 Original은 사용자 JSON에서 덮어쓸 수 없다. 제목·단위에 본문 글꼴 배율, 일반 값에 숫자 글꼴 배율을 적용한 뒤, 너비 또는 높이가 부족하면 글자와 내부 배치를 맞춘다. 수신 값·단위 변환·표시 방식 전환·저장 키는 그대로다.

## 제작과 검증

`cd theme && npm run preview -- --theme f1-2`는 기존 로컬 Astryx 환경으로 분리 파일을 만들고, 부모를 포함한 임시 catalog에서 실제 Java 엔진과 DashboardView를 실행한다. `npm run preview:live -- --theme f1-2`는 같은 엔진을 쓰는 클릭 가능한 Swing 호스트다. 정식 앱 소스나 catalog를 시안으로 치환하지 않는다. 결과물은 `theme/artifacts/f1-2/`의 F1·전체 조망·미수신 PNG다. 예시 값은 합성 데이터다.

검사 명령: `python3 scripts/test.py`, `python3 tests/test_theme_v2_renderer.py`, `python3 tests/test_theme_v3_shell.py`, `python3 tests/test_theme_v3_migration.py`, `python3 tests/test_dashboard_runtime.py`, `python3 tests/test_performance_renderer.py`, `python3 tests/test_js_diagnostics_renderer.py`. 마이그레이션 비교는 기존 v2 자산과 v3 자산을 같은 현재 렌더러로 비교한 84개 고정 표본이며 시계 영역은 제외한다. Java2D의 폰트·터치·대화상자는 Android와 다를 수 있다. APK 빌드·서명·설치, Android 기기 및 실차 실행은 이 작업에서 수행하지 않았다.

## 왜곡 수정 검수 (2026-11-18 01:41 KST)

사용자가 지적한 좌측 본문 전체의 비균등 축소를 제거했다. 실제 Java2D 텍스트 명령의 X/Y 배율을 비교하여 JS·JSN·PD, 800×480·800×425·400×240, 전체 화면 전환에서 일치함을 검사한다. F1은 실제 크기 재배치, 기존 화면들은 균일 배율 호환 배치다. 한·영, 큰 폰트(본문 120%, 숫자 120%, display 125%, 기어 200), 긴 설정 이름·실제 클릭·길게 누르기·미디어 버튼을 검사했다. F1-2의 수신/미수신·전체 조망 PNG는 실제 Java 엔진으로 재생성했다. 정식 디자인 승인과 Android·실차 검증은 별개다.

## 공통 표시 옵션과 설정 페이지

사용자 공통 `pedal_bar_style`·`brake_bar_style`·`f1_order`·`menu_icon_mode`는 테마 manifest의 일부가 아니다. 모든 테마에 같은 선택을 적용하며 테마를 바꿔도 유지한다. 설정의 UI·F1·언어 하위 편집은 동일한 페이지 형식으로 제공한다. [세부 계약과 CarPlay 설정 배경 조사](common_display_settings.md)를 따른다.
