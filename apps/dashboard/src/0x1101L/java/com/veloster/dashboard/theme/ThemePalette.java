package com.veloster.dashboard.theme;

/** Engine-specific accent choices mapped to each theme's semantic primary color. */
public final class ThemePalette {
    private static final String[] DEFAULT_NAMES={"테마 기본값","파랑","청록","보라","초록","호박색","장미색"};
    // Material choices are source/key-color inspired; each engine color is tuned for its scheme.
    private static final String[] MATERIAL_NAMES=DEFAULT_NAMES;
    // Apple HIG reference values are design swatches, not UIKit dynamic-color implementations.
    private static final String[] GLASS_NAMES={"테마 기본값","파랑","인디고","보라","핑크","주황","초록"};
    private static final int[] MATERIAL_DARK={0,0xff78b7ff,0xff48c9b0,0xffb69bff,0xff82cf91,0xffffbd66,0xffff8ea1};
    private static final int[] MATERIAL_LIGHT={0,0xff175fbd,0xff087e73,0xff6842bd,0xff287a3d,0xff945600,0xffb52e51};
    private static final int[] GLASS_DARK={0,0xff0091ff,0xff6d7cff,0xffdb34f2,0xffff375f,0xffff9230,0xff30d158};
    private static final int[] GLASS_LIGHT={0,0xff0088ff,0xff6155f5,0xffcb30e0,0xffff2d55,0xffff8d28,0xff34c759};

    private ThemePalette() {}

    public static boolean supportsAccentSelection(ThemeSpec theme){
        return theme!=null&&("material_auto_cowork".equals(theme.id)||"f1_2".equals(theme.id)
                ||"f1_2_flat".equals(theme.id)||"f1_2_optics".equals(theme.id));
    }
    public static int accentCount(String themeId){return names(themeId).length;}
    public static String accentName(String themeId,int index){
        String[] names=names(themeId);return names[index>=0&&index<names.length?index:0];
    }
    public static int accentColor(ThemeSpec theme,int index){
        if(theme==null)return 0xffa4d9ef;
        String[] names=names(theme.id);
        if(index<=0||index>=names.length)return theme.accent;
        int r=(theme.background>>>16)&255,g=(theme.background>>>8)&255,b=theme.background&255;
        double luminance=(0.2126*r+0.7152*g+0.0722*b)/255.0;
        boolean lightScheme=luminance>=0.55;
        int[] colors=theme.usesLiquidGlass()?(lightScheme?GLASS_LIGHT:GLASS_DARK)
                :(lightScheme?MATERIAL_LIGHT:MATERIAL_DARK);
        return colors[index];
    }
    private static String[] names(String themeId){return "material_auto_cowork".equals(themeId)?MATERIAL_NAMES:GLASS_NAMES;}
    public static int accent(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.accent;}
    public static int primary(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.textPrimary;}
    public static int secondary(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.textSecondary;}
    public static int critical(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.critical;}
    public static int warning(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.warning;}
    public static int stroke(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.stroke;}
    public static int panel(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.panel;}
    public static int background(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.background;}
}
