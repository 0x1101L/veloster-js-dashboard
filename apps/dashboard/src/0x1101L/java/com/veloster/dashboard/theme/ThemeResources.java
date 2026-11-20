package com.veloster.dashboard.theme;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
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
    private final Typeface defaultFont;
    private final Map<String,Typeface> weightedFaces;

    private ThemeResources(ThemeSpec spec,Bitmap background,int backdropColor,ThemeSpec.Backdrop backdrop,Typeface body,Typeface numeric,Typeface display,Typeface defaultFont,Map<String,Typeface> weightedFaces){
        this.spec=spec;this.background=background;this.body=body;this.numeric=numeric;this.display=display;
        this.backdropColor=backdropColor;this.backdrop=backdrop;
        this.defaultFont=defaultFont;
        bodyBold=bold(body);numericBold=bold(numeric);displayBold=bold(display);
        this.weightedFaces=weightedFaces;
    }

    private static Typeface bold(Typeface face){return face==null?null:face==Typeface.DEFAULT?Typeface.DEFAULT_BOLD:Typeface.create(face,Typeface.BOLD);}

    public static ThemeResources load(Context context,ThemeSpec spec){
        return load(context,spec,null);
    }

    public static ThemeResources load(Context context,ThemeSpec spec,ThemeSpec.Wallpaper selectedWallpaper){
        if(context==null||spec==null)throw new IllegalArgumentException("context and theme are required");
        AssetManager assets=context.getAssets();
        boolean color=selectedWallpaper!=null&&selectedWallpaper.color!=null;
        Bitmap background=selectedWallpaper==null||color?null:bitmap(assets,selectedWallpaper.owner,selectedWallpaper.asset);
        if(background==null&&!color)background=bitmap(assets,spec.backgroundAssetOwner,spec.backgroundAsset);
        Typeface defaultFont=defaultFont(assets);
        Map<String,Typeface> loadedWeights=new HashMap<String,Typeface>();
        Map<String,Typeface> fontCache=new HashMap<String,Typeface>();
        String[] roles={"body","numeric","display"};
        String[] owners={spec.bodyFontOwner,spec.numericFontOwner,spec.displayFontOwner};
        String[] paths={spec.bodyFont,spec.numericFont,spec.displayFont};
        for(int i=0;i<roles.length;i++){
            ThemeSpec.FontStyle style=spec.fontStyle(roles[i]);
            if(style==null)continue;
            int[] weights={style.normal,style.emphasis};
            for(int weight:weights){
                String key=roles[i]+":"+weight;
                if(loadedWeights.containsKey(key))continue;
                String sharedKey=owners[i]+":"+paths[i]+":"+weight;
                Typeface face=fontCache.get(sharedKey);
                if(face==null){
                    face=variableFont(assets,assetPath(owners[i],paths[i]),weight);
                    if(face==null){
                        String fallback=spec.fallbackWeightFont(weight);
                        String fallbackOwner=spec.fallbackWeightFontOwner==null?spec.id:spec.fallbackWeightFontOwner;
                        face=font(assets,fallbackOwner,fallback);
                    }
                    if(face!=null)fontCache.put(sharedKey,face);
                }
                loadedWeights.put(key,face);
            }
        }
        Typeface body=baseFace(spec,"body",assets,loadedWeights,defaultFont);
        Typeface numeric=baseFace(spec,"numeric",assets,loadedWeights,defaultFont);
        Typeface display=baseFace(spec,"display",assets,loadedWeights,defaultFont);
        return new ThemeResources(spec,background,color?selectedWallpaper.color.intValue():spec.background,
                color?selectedWallpaper.backdrop:spec.backdrop,body,numeric,display,defaultFont,loadedWeights);
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
        try{
            String asset=assetPath(themeId,path);
            Typeface primary=Typeface.createFromAsset(assets,asset);
            return withCjkFallback(assets,asset,primary,0);
        }catch(Exception ignored){return null;}
    }

    private static Typeface defaultFont(AssetManager assets){
        try{return Typeface.createFromAsset(assets,"fonts/SourceHanSansK-Regular.otf");}
        catch(Exception ignored){return Typeface.DEFAULT;}
    }

    /** Android 10+ can keep a theme face first and append the bundled Korean CJK face. */
    private static Typeface withCjkFallback(AssetManager assets,String primaryPath,Typeface primary,int weight){
        try{
            Class<?> fontType=Class.forName("android.graphics.fonts.Font");
            Class<?> fontBuilderType=Class.forName("android.graphics.fonts.Font$Builder");
            Object primaryBuilder=fontBuilderType.getConstructor(AssetManager.class,String.class).newInstance(assets,primaryPath);
            if(weight>0)fontBuilderType.getMethod("setWeight",Integer.TYPE).invoke(primaryBuilder,Integer.valueOf(weight));
            Object primaryFont=primaryBuilder.getClass().getMethod("build").invoke(primaryBuilder);
            Class<?> familyType=Class.forName("android.graphics.fonts.FontFamily");
            Class<?> familyBuilderType=Class.forName("android.graphics.fonts.FontFamily$Builder");
            Object primaryFamilyBuilder=familyBuilderType.getConstructor(fontType).newInstance(primaryFont);
            Object primaryFamily=primaryFamilyBuilder.getClass().getMethod("build").invoke(primaryFamilyBuilder);
            Object fallbackBuilder=fontBuilderType.getConstructor(AssetManager.class,String.class)
                    .newInstance(assets,"fonts/SourceHanSansK-Regular.otf");
            Object fallbackFont=fallbackBuilder.getClass().getMethod("build").invoke(fallbackBuilder);
            Object fallbackFamilyBuilder=familyBuilderType.getConstructor(fontType).newInstance(fallbackFont);
            Object fallbackFamily=fallbackFamilyBuilder.getClass().getMethod("build").invoke(fallbackFamilyBuilder);
            Class<?> customBuilderType=Class.forName("android.graphics.Typeface$CustomFallbackBuilder");
            Object customBuilder=customBuilderType.getConstructor(familyType).newInstance(primaryFamily);
            customBuilder=customBuilderType.getMethod("addCustomFallback",familyType).invoke(customBuilder,fallbackFamily);
            return (Typeface)customBuilderType.getMethod("build").invoke(customBuilder);
        }catch(Exception unsupported){return primary;}
    }

    private static Typeface variableFont(AssetManager assets,String path,int weight){
        if(path==null||path.length()==0)return null;
        try{
            Class<?> builderType=Class.forName("android.graphics.Typeface$Builder");
            Constructor<?> constructor=builderType.getConstructor(AssetManager.class,String.class);
            Object builder=constructor.newInstance(assets,path);
            Method variation=builderType.getMethod("setFontVariationSettings",String.class);
            variation.invoke(builder,"'wght' "+weight);
            try{builderType.getMethod("setWeight",Integer.TYPE).invoke(builder,Integer.valueOf(weight));}catch(Exception ignored){}
            Typeface face=(Typeface)builderType.getMethod("build").invoke(builder);
            return face==null?null:withCjkFallback(assets,path,face,weight);
        }catch(Exception ignored){return null;}
    }

    private static Typeface baseFace(ThemeSpec spec,String role,AssetManager assets,Map<String,Typeface> weighted,Typeface defaultFont){
        ThemeSpec.FontStyle style=spec.fontStyle(role);
        if(style!=null){
            Typeface face=weighted.get(role+":"+style.normal);
            return face==null?defaultFont:face;
        }
        Typeface face;
        if("body".equals(role))face=font(assets,spec.bodyFontOwner,spec.bodyFont);
        else if("numeric".equals(role))face=font(assets,spec.numericFontOwner,spec.numericFont);
        else face=font(assets,spec.displayFontOwner,spec.displayFont);
        return face==null?defaultFont:face;
    }

    private static String assetPath(String themeId,String path){return ThemeCatalog.ASSET_ROOT+themeId+"/"+path;}

    public ThemeSpec spec(){return spec;}
    public Bitmap background(){return background;}
    public int backdropColor(){return backdropColor;}
    public ThemeSpec.Backdrop backdrop(){return backdrop;}
    public Typeface body(){return body;}
    public Typeface numeric(){return numeric;}
    public Typeface display(){return display;}
    public Typeface defaultFont(){return defaultFont;}
    public Typeface font(String role,boolean bold){
        ThemeSpec.FontStyle style=spec.fontStyle(role);
        if(style!=null){
            Typeface weighted=weightedFaces.get(role+":"+style.weight(bold));
            if(weighted!=null)return weighted;
        }
        bold=spec.fontBold(role,bold);
        Typeface selected="display".equals(role)?display:"numeric".equals(role)?numeric:body;
        Typeface selectedBold="display".equals(role)?displayBold:"numeric".equals(role)?numericBold:bodyBold;
        if(bold&&selectedBold!=null)return selectedBold;
        if(selected==null)selected=defaultFont;
        if(selectedBold==null&&selected==defaultFont)
            selectedBold=defaultFont==Typeface.DEFAULT?Typeface.DEFAULT_BOLD:Typeface.create(defaultFont,Typeface.BOLD);
        if(selected==null)return bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT;
        return selected;
    }
    public Typeface fontForText(String text,boolean bold){
        return font(ThemeSpec.textRole(text,bold),bold);
    }
}
