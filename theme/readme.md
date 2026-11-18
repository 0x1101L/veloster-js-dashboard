# Dashboard theme workbench

Dashboard 테마의 디자인 작업 환경이다. Astryx와 의존성은 이 폴더에 설치한다. 디자인 초안은 앱의 정식 테마 카탈로그에 등록하지 않는다.

## 설치와 실행

```bash
cd theme
npm ci --offline --cache .npm-cache --ignore-scripts --no-audit --no-fund
npm run astryx -- experimental theme editor show f1-blue --png

# 실제 Java 렌더러로 F1 이미지 생성 및 클릭 동작 검사
npm run preview

# 클릭 가능한 실제 DashboardView 호스트 실행
npm run preview:live

# Liquid Glass를 참고한 두 번째 버전
npm run astryx -- experimental theme editor show f1-2 --png
npm run preview -- --theme f1-2
npm run preview:live -- --theme f1-2
```

의존성은 `node_modules/`, Astryx 편집기 캐시는 `node_modules/.cache/`에 생긴다. 해당 경로와 `artifacts/` 등 출력 폴더는 저장소의 `.gitignore`에서 제외한다. `package.json`, `package-lock.json`, 테마 소스와 제작 도구는 버전 관리한다. 위 오프라인 명령은 프로젝트 `.npm-cache`를 사용한다. 오프라인 설치가 캐시 부족으로 실패하면 필요한 패키지와 네트워크 다운로드 여부를 확인한다.

## F1 Blue 샘플

흰색 `#FFFFFF` 배경, primary `#004CFF`, 보조 표면 `#F2F6FF`를 사용한다. 현재 F1의 속도·기어·RPM, LED, 부스트·온도, 페달, 목표 RPM 배치는 유지한다.

`export_draft.mjs`는 Astryx의 빌드된 light 토큰을 `artifacts/manifest.json`으로 내보낸다. `preview_f1.py`는 실제 `DashboardView`와 Java 렌더러를 Java2D 대역에서 실행한다. 앱 자산을 임시 디렉토리에 복사한 후 그 사본에만 드래프트를 등록하며, 환경설정도 프로세스 안에서만 유지한다. F1·설정·퍼포먼스 이동은 실제 앱의 터치 처리 코드를 사용한다. 목표 RPM은 별도 설정 편집기로 이동했고 하단은 미디어 바로 분리했다.

테마 규격 v3 엔진을 실제 앱 소스 그대로 사용한다. 글꼴·크기·간격·재질은 각 시안의 `dashboard.json`, F1-2 배치는 `themes/f1-2/layouts/`의 파일을 수정한다. 임시 Java 렌더러 치환은 제거했다. 검수에는 실차 데이터가 아닌 고정 합성 값(124 km/h, D4, 4560 rpm 등)을 사용한다. 지원 범위와 제한은 [테마 규격 v3](../docs/theme_engine_v3.md)를 따른다.

`dashboard.json`의 `decorations`·`fills`·`mediaControls`·`mediaFooter`는 `components.json`으로, `palette`·`backdrop`은 `colors.json`으로 내보낸다. F1-2의 카드 상단선과 선택 메뉴 밑줄은 여기서 끈다. 시안과 정식 앱에서 ID가 달라도 같은 속성이 적용된다.

전체 조망 카드의 내부 크기와 간격은 `layouts/overview.json`의 `cardDefaults`에서 지정한다. 공기압·DCT 온도·연비·안전장치 카드는 `cardTypes`의 각 규격을 사용한다. [지원 필드와 예제](../docs/theme_engine_v3.md#전체-조망-카드-내부).

출력은 `artifacts/f1_blue.png`, `f1_blue_waiting.png`, `f1_original.png`다. Astryx의 `themes/f1-blue/preview.html`과 `sheet.html`은 별도의 웹 컴포넌트 색상 검수 자료다. `--png`에는 기존 Chrome을 사용할 수 있으며, 이 방식의 웹 PNG는 페이지 상단 2400px만 포함할 수 있다. 웹 프리뷰의 선택적 페이지에 필요한 recharts·아이콘 패키지는 설치하지 않았다.

## Liquid Glass

`themes/f1-2/layouts/shell.json`의 `"screenCorner": "squircle"`은 디스플레이 외곽과 Liquid Glass UI의 둥근 카드·버튼·이미지·타일에 스쿼클 곡선을 적용한다. 컴포넌트마다 기존 반경을 유지한다. `"screenCornerRadius": 32`는 디스플레이 외곽 반경이다. Flat과 Optics도 부모 설정을 상속한다. 원호 모양을 원하면 `"screenCorner": "circle"`로 바꾼다. Astryx 컴포넌트의 `corners`와는 별개다.

`themes/f1-2/`는 정식 테마 Liquid Glass의 디자인 원본이며 좌측 메뉴, 재배치된 F1과 3열 전체 조망을 사용한다. Astryx 시안은 `surfaces=glass`, `corners=round`, `buttons=soft`를 사용한다. 앱과 앱 렌더러용 시안의 `dashboard.json`은 `glass` 표면과 그라데이션 배경을 따로 지정한다. 앱의 Liquid Glass (Flat)은 정식 Glass를 부모로 두고 `material.style`만 덮어쓰므로 별도의 Astryx 시안 폴더가 없다. 내보내기는 배경을 `colors.json`에 기록한다. 앱은 `apps/dashboard/src/0x1101L/assets/themes/f1_2/`의 승인된 사본을 읽는다. 색상과 컴포넌트는 Astryx 내보내기 결과를, 배치는 `layouts/`를 반영한다. Pretendard 글꼴은 F1-2에 번들되며 Flat과 Optics가 상속한다. F1 Blue 등 미등록 시안은 임시 카탈로그를 사용한다.

기본 테마 엔진은 역할별 표면을 선택하고, Glass 역할은 독립 `LiquidGlassEngine`에 위임한다. 이 협력 엔진은 테마 렌더러의 캔버스·색상·재질 값을 사용하며 별도의 화면 렌더링 경로를 만들지 않는다. Glass 테마가 활성화되면 설정 → UI 설정 → Liquid Glass에서 투명도와 블러를 각각 다섯 단계로, 하이라이트를 세 단계로 조절할 수 있다. 투명도 감소에서는 블러를 끄며, 그 밖의 단계에서 블러는 테마의 역할별 블러 값을 기준으로 한다. 설정은 저장·복원되며 Glass가 없는 Flat 테마에서는 항목을 숨긴다. 렌더링은 Apple의 Liquid Glass를 참고한 Canvas 근사 표현이며 iOS 네이티브 재질 API나 광학 굴절, 움직임에 따른 반사를 제공하지 않는다.

F1-2 왼쪽 메뉴 폭은 80이고 네 방향 여백은 16 논리 단위다. 상단 메뉴 여백은 위·아래 8, 좌·우 16이며, 와이드 미디어 카드와 F1 그리드의 외곽 여백 기준값은 네 방향 16이다. F1 본문의 메뉴·미디어 인접 가로 여백에는 공통 렌더러 규칙을 적용한다. 시계는 최상단, 돌아가기는 최하단에 고정하며 전체 화면 배경을 본문과 공유한다. 설정 → UI 설정에서 메뉴 위치와 각 페이지의 표시·숨김을 선택한다. 메뉴 위치는 테마 기본값·상단·왼쪽을 지원하며, 본문이 들어가지 않는 위치는 선택할 수 없다. 설정·돌아가기는 유지한다. 음악 아이콘·이전·재생·다음은 각 컨테이너 중앙에 정렬한다.


F1-2의 `layouts/f1.json`은 `f1Grid`로 행 가중치와 열·행 span을 지정한다. 콘텐츠와 음악 바가 외곽선을 공유하며, 일반·음악 결합·하단 바 숨김에서 배치를 검증한다. [CarPlay 참고 이미지와 검수](../docs/f1_2_carplay_reference_review.md)를 참고한다.

출력은 `artifacts/f1-2/f1_2_settings.png`, `f1_2_menu_hidden.png`, `f1_2.png`, `f1_2_waiting.png`, `f1_original.png`, `f1_2_overview.png`, 분리 JSON과 `token_mapping.json`이다. 검수·승인 기록은 `@memory/theme_support/designs/f1_2/`에 둔다. Astryx는 soft 버튼의 일부 variant/state 대비 검사를 완료하지 않았다고 보고하므로 자동 검사 통과를 전체 접근성 검증으로 취급하지 않는다.

렌더러 공통 규칙으로 좌측 메뉴 옆 본문 왼쪽과 활성 우측 미디어 영역 옆 본문 오른쪽의 테마 기본 가로 여백을 절반 남긴다. 이 배치는 테마별 설정이나 토큰이 아니다. 비교 캡처와 판정은 @memory/theme_support/designs/f1_3/review.md를 참고한다.

## 검수와 적용

**Liquid Glass (Optics)**는 [검수 시안](themes/f1-2-optics/manifest.json)을 기반으로 한 탐색 테마다. F1-2의 배경·글꼴·본문 배치를 상속한다. 정보 카드·기어·설정 그룹은 평면 반투명 표면과 낮은 블러를 사용하고, 미디어 컨테이너와 왼쪽 독은 조금 더 강한 블러를 쓴다. 유리 조작부는 단일 하이라이트 선으로 렌더링하며 이중 외곽선을 그리지 않는다. 하단 바는 공통 배경을 표시하고 개별 버튼 배경을 숨긴다.

`python3 theme/preview_glass_optics.py`는 앱에 등록된 Liquid Glass·Flat·Optics에 하단 바와 와이드 플레이어의 공통 배경 및 배경 없는 개별 버튼을 실제 Java 렌더러로 확인한다. 임시 자산에서는 공통 배경을 끈 경우와 버튼 배경을 켠 경우, 와이드 버튼과 블러 미적용을 비교한다. 표준·낮은 높이·와이드 화면의 입력을 호스트에서 확인하며, 결과는 `theme/artifacts/f1-2-optics/`에 저장된다.
`python3 theme/preview_glass_optics.py --glass-settings`는 Liquid Glass의 투명도·블러 극값과 하이라이트를 실제 Java 렌더러로 캡처한다. `--glass-settings-glass`는 임시 자산에서 Optics의 flat 표면을 glass로 바꿔 같은 비교를 생성하며 앱 테마는 변경하지 않는다.

- 디자인 원본은 `themes/`, Astryx가 생성하는 웹용 사본은 `src/themes/`에 둔다. 이 사본은 Android 앱 테마가 아니다.
- F1 시안의 목적과 검수 결과는 `@memory/theme_support/designs/f1_blue/`에 기록한다.
- 생성한 시안과 실제 Java 렌더러 결과를 구분한다. 사용자가 승인한 뒤에만 앱의 테마·렌더링에 반영한다.
- 자세한 작업 규칙은 [Astryx 테마 작업 규칙](../@memory/theme_support/readme.md)을 따른다.

## 규격과 테스트

[규격 v3·상속·레이아웃·검증](../docs/theme_engine_v3.md), [매니페스트 JSON Schema](manifest.schema.json)와 [역할별 Schema](schema/)를 참고한다. `npm run preview`는 변경 후 다시 컴파일하여 렌더한다. 실행 중인 Swing 창에는 파일 변경 자동 반영 기능이 없으므로 변경 후 명령을 다시 실행한다.

좌측 메뉴 본문은 전체 그림을 찌그러뜨리지 않는다. F1은 노드별 실제 패널·글자를 다시 배치하고, 전체 조망은 가용 영역의 그리드를 계산한다. 그 외 기존 화면은 균일 배율로 맞춘다. 800×425처럼 종횡비가 다른 창에는 바깥 여백이 생긴다.
