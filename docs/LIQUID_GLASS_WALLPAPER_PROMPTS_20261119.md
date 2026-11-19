# Liquid Glass 팔레트별 배경 이미지 프롬프트

- 목적: 기존 Liquid Glass 테마 배경과 같은 곡선·광도 스타일의 palette별 기본 wallpaper 생성
- 대상: Liquid Glass / CarPlay 스타일 테마의 콘텐츠 배경 레이어
- 기준 자료: [Apple HIG · Materials](https://developer.apple.com/design/human-interface-guidelines/materials), [Apple HIG · Color](https://developer.apple.com/design/human-interface-guidelines/color)
- 참조 이미지: `apps/dashboard/src/0x1101L/assets/themes/f1_2/backgrounds/blue.png`
- 기준 크기: 1920×1152, PNG, sRGB
- 사용할 팔레트 키: `blue`, `indigo`, `purple`, `pink`, `orange`, `green`
- 여섯 palette key 모두 새 `palette-*` 이미지로 생성한다. 현행 Blue/Green은 composition/style 참조로만 사용하고 원본을 덮어쓰지 않는다. 기존 Blue/Red/Green/Mono wallpaper는 수동 선택지로 보존한다.

## 기준 프롬프트

각 palette variant는 같은 참조 이미지로 별도 생성 요청을 보낸다. 아래 `[COLOR_VARIANT]`만 해당 variant 문구로 바꾼다.

```text
Use case: stylized-concept
Asset type: full-bleed wallpaper background for an 800x480 automotive dashboard theme
Primary request: create one calm abstract background image in the same visual family as Image 1
Input images: Image 1 is a style and composition reference only; do not copy its exact pixels
Scene/backdrop: a soft, pale, luminous gradient field with a few broad glass-like light ribbons sweeping in the same curved paths as Image 1; keep the main content area smooth and quiet
Style/medium: refined abstract raster artwork, soft gradients, subtle depth, clean premium automotive display background
Composition/framing: preserve Image 1's wide 5:3 composition, open central field, gentle arc near the upper portion, and layered diagonal curves flowing toward the lower-right; keep the same visual balance and scale
Lighting/mood: diffuse pearl-like illumination, restrained glow, low visual noise, calm and legible under dashboard text and gauges
Color palette: [COLOR_VARIANT]; use the selected hue as a muted tint in the field and lower-right depth while keeping the highlights neutral soft white; match Image 1's overall brightness and contrast
Materials/textures: smooth translucent-looking gradients and fine soft light, no visible grain or hard reflections
Text (verbatim): none
Constraints: output one background only, full bleed, 1920x1152 landscape, sRGB appearance; maintain the same composition, curve thickness, curve paths, gradient softness, negative space, brightness range, and focal balance across all palette variants; image is a content backdrop with Liquid Glass UI rendered separately above it
Avoid: interface mockup, cards, buttons, icons, gauges, text, logos, watermark, objects, scenery, sharp edges, high-contrast streaks, busy center, dark vignette, oversaturated color, large hue shifts outside [COLOR_VARIANT], changing the curve layout, adding extra curves
```

## Variant 문구

| palette key | `[COLOR_VARIANT]` 값 | 출력 파일명 |
|---|---|---|
| `blue` | muted cool blue and blue-silver undertones, pale periwinkle shadows | `backgrounds/palette-blue.png` |
| `indigo` | muted indigo and blue-violet undertones, pale lavender-blue shadows | `backgrounds/palette-indigo.png` |
| `purple` | muted violet and soft lilac undertones, pale lavender shadows | `backgrounds/palette-purple.png` |
| `pink` | muted rose-pink undertones, pale blush shadows, neutral white highlights | `backgrounds/palette-pink.png` |
| `orange` | muted warm amber-orange undertones, pale peach shadows, neutral white highlights | `backgrounds/palette-orange.png` |
| `green` | muted sage and soft mint undertones, pale celadon shadows | `backgrounds/palette-green.png` |

## 생성 후 검수

- 여섯 이미지를 나란히 놓고 곡선 위치·굵기·밝기·여백이 한 제품군처럼 일치하는지 확인한다.
- 800×480, 1920×1152에서 자르기와 중심부 텍스트·게이지 가독성을 확인한다.
- Glass 표면을 합성해 생성하지 않았는지 확인한다. 실제 Glass 효과는 `LiquidGlassEngine`이 배경 위에서 그린다.
- Apple HIG에 따라 밝은 배경에서 `clear` Glass를 사용할 때는 surface 뒤 dimming 필요성을 검사하고, 텍스트 중심의 불투명/정규 표면은 legibility를 유지하는지 확인한다.
- 이미지에서 잘림/초점이 달라지면 개별 구도를 고치는 대신 기준 프롬프트의 구도 보존 문구를 조정하고 전체 세트를 다시 비교한다.
