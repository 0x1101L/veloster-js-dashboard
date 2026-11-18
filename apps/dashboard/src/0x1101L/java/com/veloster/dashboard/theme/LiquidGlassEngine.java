package com.veloster.dashboard.theme;

import android.graphics.Canvas;
import com.veloster.dashboard.theme.ThemeSpec.Material;
import com.veloster.dashboard.theme.ThemeSpec.SurfaceRole;

/** Liquid Glass material compositor, kept separate from the theme engine's flat/legacy surfaces. */
public final class LiquidGlassEngine {
    public void drawGlass(ThemeSurface surfaces,Canvas canvas,ThemeSpec theme,SurfaceRole role,Material material,
                     float x,float y,float width,float height,float radius,int color){
        surfaces.fill(canvas,theme,x,y+4,width,height,radius,material.shadow);

        boolean reduceTransparency=theme.liquidGlassTransparencyReduced();
        int blur=reduceTransparency?0:scaledBlur(theme.blurFor(role),theme.liquidGlassBlurLevel());
        surfaces.blur(canvas,theme,blur,x,y,width,height,radius);

        int top=reduceTransparency?Math.max(248,material.topAlpha):material.topAlpha;
        int bottom=reduceTransparency?Math.max(240,material.bottomAlpha):material.bottomAlpha;
        top=adjustAlpha(top,theme.liquidGlassTransparencyLevel());
        bottom=adjustAlpha(bottom,theme.liquidGlassTransparencyLevel());
        surfaces.gradient(canvas,theme,x,y,width,height,radius,
                (top<<24)|(color&0x00ffffff),(bottom<<24)|(color&0x00ffffff));

        float highlightScale=reduceTransparency?.72f:highlightScale(theme.liquidGlassHighlightLevel());
        surfaces.edge(canvas,theme,x,y,width,height,radius,withScaledAlpha(material.highlight,highlightScale));
    }

    public void drawFlat(ThemeSurface surfaces,Canvas canvas,ThemeSpec theme,SurfaceRole role,
                         float x,float y,float width,float height,float radius,int color,int stroke){
        boolean reduceTransparency=theme.liquidGlassTransparencyReduced();
        int blur=reduceTransparency?0:scaledBlur(theme.blurFor(role),theme.liquidGlassBlurLevel());
        surfaces.blur(canvas,theme,blur,x,y,width,height,radius);
        int fill=reduceTransparency?(0xff000000|(color&0x00ffffff)):
                ((adjustAlpha((color>>>24)&255,theme.liquidGlassTransparencyLevel())<<24)|(color&0x00ffffff));
        surfaces.fill(canvas,theme,x,y,width,height,radius,fill);
        float highlightScale=reduceTransparency?.72f:highlightScale(theme.liquidGlassHighlightLevel());
        surfaces.edge(canvas,theme,x,y,width,height,radius,withScaledAlpha(stroke,highlightScale));
    }

    private static int scaledBlur(int strength,int level){
        float scale=level==3?0f:level==0?.5f:level==2?1.7f:level==4?3f:1f;
        return Math.max(0,Math.min(36,Math.round(strength*scale)));
    }

    private static int adjustAlpha(int alpha,int level){
        float scale=level==2?.35f:level==3?.7f:level==4?1.35f:1f;
        return Math.max(0,Math.min(255,Math.round(alpha*scale)));
    }

    private static float highlightScale(int level){return level==0?.6f:level==2?1.25f:1f;}

    private static int withScaledAlpha(int color,float scale){
        int alpha=(color>>>24)&255;
        return (Math.max(0,Math.min(255,Math.round(alpha*scale)))<<24)|(color&0x00ffffff);
    }
}
