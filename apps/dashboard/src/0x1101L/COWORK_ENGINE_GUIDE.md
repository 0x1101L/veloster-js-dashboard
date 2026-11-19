# CoWork 공용 테마 계약

CoWork는 Dashboard의 여러 테마가 공통 renderer와 화면 동작을 공유하는 **공용 테마 계약**을 가리킨다. 별도의 `CoWorkEngine` renderer가 있는 구조는 아니다. 이 안내서는 공용 계약이 제공하는 역할과 확장 기준을 설명한다. 전체 적용 경로는 [테마 엔진 안내](THEME_ENGINE_GUIDE.md)를, 화면 제약은 [테마 규격](THEME_SPEC_GUIDE.md)을 참고한다.

## 공용 계약의 역할

테마 번들은 외관을 선언하고 `ThemeCatalog`는 이를 `ThemeSpec`으로 해석한다. 기존 renderer는 이 공통 모델을 사용하므로, 테마별로 화면과 입력 처리를 복제하지 않고도 시각 차이를 표현할 수 있다.

| 관심사 | 공용 API | 제공하는 것 |
|---|---|---|
| 색상 의미 | `ThemeSpec.ColorRole`, `color(role)` | 배경·표면·primary·외곽선·상태색에 일관된 이름 제공 |
| 표면 역할 | `SurfaceRole`, `materialFor(role)` | 메뉴, 버튼, 미디어, 카드, 설정 영역별 재질 선택 |
| Canvas 표현 | `ThemeSurface` | 공통 표면의 형태, 채움, 선, 그라데이션과 배경 흐림 처리 |
| Glass 효과 | `LiquidGlassEngine` | Glass가 지정된 표면의 흐림, 투명도, 그라데이션과 하이라이트 합성 |
| 사용자 선택 | `ThemeSettingsStore`와 설정 화면 | 테마별 포인트 컬러와 공통 배경·표시 설정 저장 |

`LiquidGlassEngine`은 공용 계약과 나란히 존재하는 두 번째 엔진이 아니라, 그 계약이 호출하는 Glass 전용 효과 구현이다. Flat 표면은 같은 `ThemeSurface` 경로에서 처리한다.

## Material Auto와의 관계

`material_auto_cowork`는 이 계약을 사용하는 독립 테마다. 자체 manifest, 색상, 구성과 화면 레이아웃을 선언하고 Original을 상속하지 않는다. 이 테마는 Material 계열 포인트 컬러와 공통 surface/color 역할이 기존 renderer에 전달되는지 확인하는 구현 사례다. `material_auto_cowork`라는 ID가 별도 Material renderer나 Compose 런타임을 뜻하지 않는다.

Material과 Liquid Glass의 포인트 컬러 목록은 서로 분리한다. 포인트 컬러는 primary 표현에 적용하고 경고·위험·미수신 의미는 그대로 둔다.

## 공용 기능을 추가할 때

한 테마에서만 쓰는 장식은 테마 파일에 둔다. 여러 테마와 renderer가 같은 의미로 사용해야 할 때만 공용 계약을 확장한다.

새 역할이나 속성이 꼭 필요하다면 v3 schema, `ThemeCatalog`의 파싱·검증, `ThemeSpec` accessor와 복사·상속 경로를 함께 갱신한다. 이어 renderer와 hit-test에 적용하고 설정값이면 저장·취소·복귀까지 연결한다. 누락값과 잘못된 입력의 기본 동작도 정의한다.

검증은 parser 거부와 fallback, 신규 역할이 적용되는 화면, Apply/Cancel 및 저장 복귀, Original과 기존 테마 회귀를 포함한다. 한 테마의 화면 캡처만으로 공용 계약의 호환성을 판단하지 않는다. Overview는 5×4 한 화면을 유지한다.

## 현재 지원 범위와 남은 기능

현재 코드에는 v3 catalog/parser, semantic color role, surface role, Flat/Glass Canvas 경로와 테마별 포인트 컬러 선택이 있다. Material Auto는 자체 리소스를 제공해 공용 경로를 사용한다. API 17 실기기와 실차 성능은 별도 검증 범위다.

Liquid Glass 포인트 컬러에 따라 기본 wallpaper를 바꾸는 자동 연결은 아직 구현되지 않았다. 구현 시 직접 고른 배경은 보존하고, 테마 기본값에서만 palette별 배경을 선택해야 한다. 팔레트 변경, 기본값 복귀, 이미지 누락 fallback, 설정 미리보기와 저장 후 복원까지 한 흐름으로 검증한다. 상세 동작은 [테마 규격의 Liquid Glass 배경 표](THEME_SPEC_GUIDE.md#liquid-glass-배경-선택)에 정의되어 있다.

## 정본 자료

- [테마 엔진 v3 규격](../../../../docs/theme_engine_v3.md)
- [Material Auto 조사·구현 규격](../../../../docs/MATERIAL_AUTO_THEME_RESEARCH_SPEC_20261119.md)
- [Material Auto 구현 기록 및 검증 범위](../../../../docs/MATERIAL_AUTO_THEME_IMPLEMENTATION_20261119.md)
