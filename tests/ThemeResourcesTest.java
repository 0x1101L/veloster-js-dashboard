package com.veloster.dashboard.theme;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ThemeResourcesTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ThemeResources original=ThemeResources.load(new Context(),ThemeSpec.original());
        check(original.background()==null&&original.body()==Typeface.DEFAULT,"Original falls back to platform when bundled assets are unavailable");
        check(original.font("body",false)==Typeface.DEFAULT,"body role falls back to Android default");
        ThemeSpec missing=ThemeSpec.fromManifest("missing_asset","Missing",0xff000000,0xff111111,
                0xff222222,0xff333333,0xffffffff,0xffaaaaaa,0xff00ffff,0xffffff00,
                0xffff0000,0xff888888,5,4,false,"fonts/missing-body.ttf",
                "fonts/missing-number.ttf","fonts/missing-display.ttf","backgrounds/missing.png",ThemeSpec.defaultTypography(),ThemeSpec.defaultSpacing(),ThemeSpec.defaultMaterial(),ThemeSpec.Backdrop.SOLID);
        ThemeResources fallback=ThemeResources.load(new Context(),missing);
        check(fallback.background()==null,"missing bitmap asset falls back without throwing");
        check(fallback.body()==Typeface.DEFAULT&&fallback.numeric()==Typeface.DEFAULT&&fallback.display()==Typeface.DEFAULT,
                "missing theme fonts fall back to the platform face when the bundled default is unavailable");
        check(fallback.font("body",false)==Typeface.DEFAULT&&fallback.font("display",true)==Typeface.DEFAULT_BOLD,
                "missing font roles use default regular and bold faces");
        check(fallback.font("numeric",false)==fallback.font("numeric",false),"fallback face is reused");
        final String[] opened={""};
        AssetManager assets=new AssetManager(){public InputStream open(String path){opened[0]=path;return new ByteArrayInputStream(new byte[]{1});}};
        Context context=new Context(){public AssetManager getAssets(){return assets;}};
        ThemeResources defaultFont=ThemeResources.load(context,ThemeSpec.original());
        check("fonts/SourceHanSansK-Regular.otf".equals(defaultFont.defaultFont().sourceAssetPath)
                &&defaultFont.body()==defaultFont.defaultFont()&&defaultFont.font("display",false)==defaultFont.defaultFont(),
                "the bundled Source Han Sans Korean face supplies all unthemed roles");
        ThemeResources loaded=ThemeResources.load(context,missing);
        check("themes/missing_asset/fonts/missing-display.ttf".equals(Typeface.lastAssetPath),"theme assets resolve beneath their own bundle");
        check(TypeFaceLoaded(loaded)&&opened[0].startsWith("themes/missing_asset/"),"font role is loaded and asset requests stay in the theme directory");
        check("themes/missing_asset/fonts/missing-body.ttf".equals(loaded.body().sourceAssetPath),
                "an available theme font remains ahead of the bundled default face");
        check(loaded.font("numeric",false)==loaded.numeric()&&loaded.font("body",false)==loaded.body(),"font roles reuse eagerly cached faces");

        ThemeSpec weighted=ThemeSpec.fromManifest("weighted","Weighted",0xff000000,0xff111111,0xff222222,
                0xff333333,0xffffffff,0xffaaaaaa,0xff00ffff,0xffffff00,0xffff0000,0xff888888,
                5,4,false,"fonts/PretendardVariable.ttf","fonts/PretendardVariable.ttf",
                "fonts/PretendardVariable.ttf","",ThemeSpec.defaultTypography(),ThemeSpec.defaultSpacing(),
                ThemeSpec.defaultMaterial(),ThemeSpec.Backdrop.SOLID);
        Map<String,ThemeSpec.FontStyle> styles=new LinkedHashMap<String,ThemeSpec.FontStyle>();
        styles.put("body",new ThemeSpec.FontStyle(400,600));
        styles.put("numeric",new ThemeSpec.FontStyle(500,700));
        styles.put("display",new ThemeSpec.FontStyle(600,700));
        weighted.fontStyles(styles);
        Map<Integer,String> fallbackFonts=new LinkedHashMap<Integer,String>();
        fallbackFonts.put(Integer.valueOf(400),"fonts/Pretendard-Regular.otf");
        fallbackFonts.put(Integer.valueOf(500),"fonts/Pretendard-Medium.otf");
        fallbackFonts.put(Integer.valueOf(600),"fonts/Pretendard-SemiBold.otf");
        fallbackFonts.put(Integer.valueOf(700),"fonts/Pretendard-Bold.otf");
        weighted.fallbackWeightFonts(fallbackFonts);weighted.fallbackWeightFontOwner=weighted.id;

        Typeface.failVariableBuild=false;
        ThemeResources variable=ThemeResources.load(context,weighted);
        check(variable.font("body",false).sourceFontWeight==400&&variable.font("body",true).sourceFontWeight==600
                &&variable.font("numeric",false).sourceFontWeight==500&&variable.font("numeric",true).sourceFontWeight==700
                &&"'wght' 700".equals(Typeface.lastVariationSettings),
                "API 26+ uses the variable font axis for role-specific Regular, Medium, SemiBold and Bold faces");

        Typeface.failVariableBuild=true;
        ThemeResources legacy=ThemeResources.load(context,weighted);
        check("themes/weighted/fonts/Pretendard-Regular.otf".equals(legacy.font("body",false).sourceAssetPath)
                &&"themes/weighted/fonts/Pretendard-SemiBold.otf".equals(legacy.font("body",true).sourceAssetPath)
                &&"themes/weighted/fonts/Pretendard-Medium.otf".equals(legacy.font("numeric",false).sourceAssetPath)
                &&"themes/weighted/fonts/Pretendard-Bold.otf".equals(legacy.font("numeric",true).sourceAssetPath),
                "API 17 selects bundled static faces for the same role weights");
        System.out.println("PASS ThemeResourcesTest");
    }
    private static boolean TypeFaceLoaded(ThemeResources resources){return resources.body()!=null&&resources.numeric()!=null&&resources.display()!=null;}
}
