package com.veloster.dashboard.theme;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import java.io.InputStream;

/** Eagerly loads and caches optional bundle assets before a renderer starts drawing. */
public final class ThemeResources {
    private final ThemeSpec spec;
    private final Bitmap background;
    private final Typeface body;
    private final Typeface numeric;
    private final Typeface display;
    private final Typeface bodyBold;
    private final Typeface numericBold;
    private final Typeface displayBold;

    private ThemeResources(ThemeSpec spec,Bitmap background,Typeface body,Typeface numeric,Typeface display){
        this.spec=spec;this.background=background;this.body=body;this.numeric=numeric;this.display=display;
        bodyBold=bold(body);numericBold=bold(numeric);displayBold=bold(display);
    }

    private static Typeface bold(Typeface face){return face==null?null:Typeface.create(face,Typeface.BOLD);}

    public static ThemeResources load(Context context,ThemeSpec spec){
        if(context==null||spec==null)throw new IllegalArgumentException("context and theme are required");
        if(spec.isOriginal())return new ThemeResources(spec,null,null,null,null);
        AssetManager assets=context.getAssets();
        return new ThemeResources(spec,bitmap(assets,spec.id,spec.backgroundAsset),font(assets,spec.id,spec.bodyFont),
                font(assets,spec.id,spec.numericFont),font(assets,spec.id,spec.displayFont));
    }

    private static Bitmap bitmap(AssetManager assets,String themeId,String path){
        if(path==null||path.length()==0)return null;
        InputStream input=null;try{input=assets.open(assetPath(themeId,path));return BitmapFactory.decodeStream(input);}
        catch(Exception ignored){return null;}finally{if(input!=null)try{input.close();}catch(Exception ignored){}}
    }

    private static Typeface font(AssetManager assets,String themeId,String path){
        if(path==null||path.length()==0)return null;
        try{return Typeface.createFromAsset(assets,assetPath(themeId,path));}catch(Exception ignored){return null;}
    }

    private static String assetPath(String themeId,String path){return ThemeCatalog.ASSET_ROOT+themeId+"/"+path;}

    public ThemeSpec spec(){return spec;}
    public Bitmap background(){return background;}
    public Typeface body(){return body;}
    public Typeface numeric(){return numeric;}
    public Typeface display(){return display;}
    public Typeface font(String role,boolean bold){
        Typeface selected="display".equals(role)?display:"numeric".equals(role)?numeric:body;
        Typeface selectedBold="display".equals(role)?displayBold:"numeric".equals(role)?numericBold:bodyBold;
        if(bold&&selectedBold!=null)return selectedBold;
        if(selected==null)return bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT;
        return selected;
    }
    public Typeface fontForText(String text,boolean bold){
        boolean numericText=false;
        if(text!=null)for(int i=0;i<text.length();i++)if(text.charAt(i)>='0'&&text.charAt(i)<='9'){numericText=true;break;}
        return font(numericText?"numeric":bold?"display":"body",bold);
    }
}
