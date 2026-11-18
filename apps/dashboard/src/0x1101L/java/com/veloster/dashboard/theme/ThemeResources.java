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
    private final int backdropColor;
    private final ThemeSpec.Backdrop backdrop;
    private final Typeface body;
    private final Typeface numeric;
    private final Typeface display;
    private final Typeface bodyBold;
    private final Typeface numericBold;
    private final Typeface displayBold;

    private ThemeResources(ThemeSpec spec,Bitmap background,int backdropColor,ThemeSpec.Backdrop backdrop,Typeface body,Typeface numeric,Typeface display){
        this.spec=spec;this.background=background;this.body=body;this.numeric=numeric;this.display=display;
        this.backdropColor=backdropColor;this.backdrop=backdrop;
        bodyBold=bold(body);numericBold=bold(numeric);displayBold=bold(display);
    }

    private static Typeface bold(Typeface face){return face==null?null:Typeface.create(face,Typeface.BOLD);}

    public static ThemeResources load(Context context,ThemeSpec spec){
        return load(context,spec,null);
    }

    public static ThemeResources load(Context context,ThemeSpec spec,ThemeSpec.Wallpaper selectedWallpaper){
        if(context==null||spec==null)throw new IllegalArgumentException("context and theme are required");
        AssetManager assets=context.getAssets();
        boolean color=selectedWallpaper!=null&&selectedWallpaper.color!=null;
        Bitmap background=selectedWallpaper==null||color?null:bitmap(assets,selectedWallpaper.owner,selectedWallpaper.asset);
        if(background==null&&!color)background=bitmap(assets,spec.backgroundAssetOwner,spec.backgroundAsset);
        return new ThemeResources(spec,background,color?selectedWallpaper.color.intValue():spec.background,
                color?selectedWallpaper.backdrop:spec.backdrop,font(assets,spec.bodyFontOwner,spec.bodyFont),
                font(assets,spec.numericFontOwner,spec.numericFont),font(assets,spec.displayFontOwner,spec.displayFont));
    }

    public static Bitmap loadThumbnail(Context context,ThemeSpec.Wallpaper wallpaper){
        if(context==null||wallpaper==null)return null;
        AssetManager assets=context.getAssets();
        Bitmap thumbnail=bitmap(assets,wallpaper.owner,wallpaper.thumbnail);
        if(thumbnail!=null)return thumbnail;
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        bitmap(assets,wallpaper.owner,wallpaper.asset,options);
        int longest=Math.max(options.outWidth,options.outHeight);
        if(longest<=0)return null;
        options.inJustDecodeBounds=false;
        options.inSampleSize=1;
        while(longest/options.inSampleSize>=640)options.inSampleSize*=2;
        return bitmap(assets,wallpaper.owner,wallpaper.asset,options);
    }

    private static Bitmap bitmap(AssetManager assets,String themeId,String path){
        return bitmap(assets,themeId,path,null);
    }

    private static Bitmap bitmap(AssetManager assets,String themeId,String path,BitmapFactory.Options options){
        if(path==null||path.length()==0)return null;
        InputStream input=null;try{input=assets.open(assetPath(themeId,path));return BitmapFactory.decodeStream(input,null,options);}
        catch(Exception ignored){return null;}finally{if(input!=null)try{input.close();}catch(Exception ignored){}}
    }

    private static Typeface font(AssetManager assets,String themeId,String path){
        if(path==null||path.length()==0)return null;
        try{return Typeface.createFromAsset(assets,assetPath(themeId,path));}catch(Exception ignored){return null;}
    }

    private static String assetPath(String themeId,String path){return ThemeCatalog.ASSET_ROOT+themeId+"/"+path;}

    public ThemeSpec spec(){return spec;}
    public Bitmap background(){return background;}
    public int backdropColor(){return backdropColor;}
    public ThemeSpec.Backdrop backdrop(){return backdrop;}
    public Typeface body(){return body;}
    public Typeface numeric(){return numeric;}
    public Typeface display(){return display;}
    public Typeface font(String role,boolean bold){
        bold=spec.fontBold(role,bold);
        Typeface selected="display".equals(role)?display:"numeric".equals(role)?numeric:body;
        Typeface selectedBold="display".equals(role)?displayBold:"numeric".equals(role)?numericBold:bodyBold;
        if(bold&&selectedBold!=null)return selectedBold;
        if(selected==null)return bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT;
        return selected;
    }
    public Typeface fontForText(String text,boolean bold){
        return font(ThemeSpec.textRole(text,bold),bold);
    }
}
