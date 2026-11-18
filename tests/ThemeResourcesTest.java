package com.veloster.dashboard.theme;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public final class ThemeResourcesTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ThemeResources original=ThemeResources.load(new Context(),ThemeSpec.original());
        check(original.background()==null&&original.body()==null,"Original retains platform defaults");
        check(original.font("body",false)==Typeface.DEFAULT,"body role falls back to Android default");
        ThemeSpec missing=ThemeSpec.fromManifest("missing_asset","Missing",0xff000000,0xff111111,
                0xff222222,0xff333333,0xffffffff,0xffaaaaaa,0xff00ffff,0xffffff00,
                0xffff0000,0xff888888,4,3,false,"fonts/missing-body.ttf",
                "fonts/missing-number.ttf","fonts/missing-display.ttf","backgrounds/missing.png",ThemeSpec.defaultTypography(),ThemeSpec.defaultSpacing(4),ThemeSpec.defaultMaterial(),ThemeSpec.Backdrop.SOLID);
        ThemeResources fallback=ThemeResources.load(new Context(),missing);
        check(fallback.background()==null,"missing bitmap asset falls back without throwing");
        check(fallback.body()==null&&fallback.numeric()==null&&fallback.display()==null,"missing font files fall back");
        check(fallback.font("body",false)==Typeface.DEFAULT&&fallback.font("display",true)==Typeface.DEFAULT_BOLD,
                "missing font roles use default regular and bold faces");
        check(fallback.font("numeric",false)==fallback.font("numeric",false),"fallback face is reused");
        final String[] opened={""};
        AssetManager assets=new AssetManager(){public InputStream open(String path){opened[0]=path;return new ByteArrayInputStream(new byte[]{1});}};
        Context context=new Context(){public AssetManager getAssets(){return assets;}};
        ThemeResources loaded=ThemeResources.load(context,missing);
        check("themes/missing_asset/fonts/missing-display.ttf".equals(Typeface.lastAssetPath),"theme assets resolve beneath their own bundle");
        check(TypeFaceLoaded(loaded)&&opened[0].startsWith("themes/missing_asset/"),"font role is loaded and asset requests stay in the theme directory");
        check(loaded.font("numeric",false)==loaded.numeric()&&loaded.font("body",false)==loaded.body(),"font roles reuse eagerly cached faces");
        System.out.println("PASS ThemeResourcesTest");
    }
    private static boolean TypeFaceLoaded(ThemeResources resources){return resources.body()!=null&&resources.numeric()!=null&&resources.display()!=null;}
}
