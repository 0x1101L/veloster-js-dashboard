package com.veloster.dashboard.theme;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import com.veloster.dashboard.theme.ThemeSpec.ScreenCorner;
import com.veloster.dashboard.theme.ThemeSpec.SurfaceRole;

/** API17 Canvas materials, including cached blur of the theme backdrop. */
public final class ThemeSurface {
    private ThemeSpec.Backdrop cachedBackdrop;
    private int cachedBackdropStart;
    private Shader backdropGradient;
    private final Paint paint=new Paint(3);
    private final Path legacyShape=new Path();
    private final Path componentShape=new Path();
    private final Paint cornerPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cornerMask=new Path();
    private int cornerWidth,cornerHeight;
    private float cornerRadius;
    private ScreenCorner cornerShape;

    /** Draw rounded UI geometry with the active theme's global corner curve. */
    public void roundRect(Canvas c,ThemeSpec theme,RectF rect,float rx,float ry,Paint target){
        if(theme==null||theme.shell().screenCorner!=ScreenCorner.SQUIRCLE){
            c.drawRoundRect(rect,rx,ry,target);return;
        }
        float radius=Math.min(Math.min(rx,ry),Math.min(rect.width(),rect.height())*.5f);
        if(radius<=0){c.drawRect(rect.left,rect.top,rect.right,rect.bottom,target);return;}
        buildSquircleRect(componentShape,rect,radius);
        c.drawPath(componentShape,target);
    }

    /** Clip artwork to the active theme's rounded or squircle component outline. */
    public void clipRoundRect(Canvas c,ThemeSpec theme,RectF rect,float radius){
        if(theme==null||theme.shell().screenCorner!=ScreenCorner.SQUIRCLE){
            c.clipPath(roundRectPath(rect,radius));return;
        }
        buildSquircleRect(componentShape,rect,radius);
        c.clipPath(componentShape);
    }

    private Path roundRectPath(RectF rect,float radius){
        Path result=new Path();
        result.addRoundRect(rect,radius,radius,Path.Direction.CW);
        return result;
    }

    private static void buildSquircleRect(Path path,RectF rect,float radius){
        float l=rect.left,t=rect.top,r=rect.right,b=rect.bottom;
        float a=.159103585f,k=.5624f;
        path.reset();
        path.moveTo(l+radius,t);
        path.lineTo(r-radius,t);
        path.cubicTo(r-k*radius,t,r-2*a*radius,t,r-a*radius,t+a*radius);
        path.cubicTo(r,t+2*a*radius,r,t+k*radius,r,t+radius);
        path.lineTo(r,b-radius);
        path.cubicTo(r,b-k*radius,r,b-2*a*radius,r-a*radius,b-a*radius);
        path.cubicTo(r-2*a*radius,b,r-k*radius,b,r-radius,b);
        path.lineTo(l+radius,b);
        path.cubicTo(l+k*radius,b,l+2*a*radius,b,l+a*radius,b-a*radius);
        path.cubicTo(l,b-2*a*radius,l,b-k*radius,l,b-radius);
        path.lineTo(l,t+radius);
        path.cubicTo(l,t+k*radius,l,t+2*a*radius,l+a*radius,t+a*radius);
        path.cubicTo(l+2*a*radius,t,l+k*radius,t,l+radius,t);
        path.close();
    }
    private final java.util.Map<Integer,BitmapShader> blurShaders=new java.util.HashMap<Integer,BitmapShader>();
    private final Matrix blurMatrix=new Matrix();
    private ThemeSpec.Backdrop blurBackdrop;
    private Bitmap blurImage;
    private int blurBase,frameWidth,frameHeight,sampleWidth,sampleHeight;
    private float originX,originY,frameScale=1;

    /** Map local component coordinates to the viewport used to paint the backdrop. */
    public void coordinates(float x,float y,float scale){originX=x;originY=y;frameScale=scale;}

    /** In display coordinates, cover only the pixels outside the rounded screen outline. */
    public void maskScreenCorners(Canvas c,ThemeSpec theme,int width,int height,float scale){
        ThemeSpec.Shell shell=theme.shell();
        float radius=Math.min(shell.screenCornerRadius*scale,Math.min(width,height)*.5f);
        if(radius<=0)return;
        if(width!=cornerWidth||height!=cornerHeight||radius!=cornerRadius||shell.screenCorner!=cornerShape){
            cornerMask.reset();
            cornerMask.setFillType(Path.FillType.EVEN_ODD);
            if(shell.screenCorner==ScreenCorner.SQUIRCLE){
                addSquircleCorner(cornerMask,0,0,radius,radius);
                addSquircleCorner(cornerMask,width,0,-radius,radius);
                addSquircleCorner(cornerMask,0,height,radius,-radius);
                addSquircleCorner(cornerMask,width,height,-radius,-radius);
            }else{
                cornerMask.addRect(0,0,width,height,Path.Direction.CW);
                cornerMask.addRoundRect(new RectF(0,0,width,height),radius,radius,Path.Direction.CW);
            }
            cornerPaint.setColor(0xff000000);
            cornerWidth=width;cornerHeight=height;cornerRadius=radius;cornerShape=shell.screenCorner;
        }
        c.drawPath(cornerMask,cornerPaint);
    }

    /** Two symmetric cubics approximate the quartic superellipse, with zero curvature at the straight edges. */
    private static void addSquircleCorner(Path mask,float x,float y,float dx,float dy){
        final float diagonal=0.159103585f; // 1 - 2^(-1/4)
        final float control=0.5624f; // Fitted to the quartic; 2*diagonal gives the shared tangent.
        mask.moveTo(x,y);
        mask.lineTo(x+dx,y);
        mask.cubicTo(x+dx*control,y,x+2*dx*diagonal,y,x+dx*diagonal,y+dy*diagonal);
        mask.cubicTo(x,y+2*dy*diagonal,x,y+dy*control,x,y+dy);
        mask.close();
    }

    private void blur(Canvas c,ThemeSpec theme,int strength,float x,float y,float w,float h,float radius){
        if(strength==0||frameWidth<=0||frameHeight<=0)return;
        BitmapShader shader=blurShaders.get(strength);
        if(shader==null){
            Bitmap image=Bitmap.createBitmap(sampleWidth,sampleHeight,Bitmap.Config.ARGB_8888);
            Canvas sample=new Canvas(image);
            sample.scale(sampleWidth/(float)frameWidth,sampleHeight/(float)frameHeight);
            paintBackdrop(sample,blurBase,blurBackdrop,blurImage,frameWidth,frameHeight);
            int[] pixels=new int[sampleWidth*sampleHeight],work=new int[pixels.length];
            image.getPixels(pixels,0,sampleWidth,0,0,sampleWidth,sampleHeight);
            int r=Math.max(1,Math.round(strength*Math.min(sampleWidth/800f,sampleHeight/480f)));
            for(int pass=0;pass<3;pass++){
                blurPass(pixels,work,sampleWidth,sampleHeight,1,sampleWidth,r);
                blurPass(work,pixels,sampleHeight,sampleWidth,sampleWidth,1,r);
            }
            image.setPixels(pixels,0,sampleWidth,0,0,sampleWidth,sampleHeight);
            shader=new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            blurShaders.put(strength,shader);
        }
        blurMatrix.setScale(frameWidth/(sampleWidth*frameScale),frameHeight/(sampleHeight*frameScale));
        blurMatrix.postTranslate(-originX/frameScale,-originY/frameScale);
        shader.setLocalMatrix(blurMatrix);
        paint.setColor(0xffffffff);paint.setStyle(Paint.Style.FILL);paint.setShader(shader);
        roundRect(c,theme,new RectF(x,y,x+w,y+h),radius,radius,paint);
        paint.setShader(null);
    }

    private static void blurPass(int[] src,int[] dst,int length,int lines,int step,int stride,int radius){
        int divisor=radius*2+1;
        for(int line=0;line<lines;line++){
            int base=line*stride,r=0,g=0,b=0;
            for(int i=-radius;i<=radius;i++){
                int pixel=src[base+Math.min(length-1,Math.max(0,i))*step];
                r+=(pixel>>>16)&255;g+=(pixel>>>8)&255;b+=pixel&255;
            }
            for(int i=0;i<length;i++){
                dst[base+i*step]=0xff000000|((r/divisor)<<16)|((g/divisor)<<8)|(b/divisor);
                int drop=src[base+Math.max(0,i-radius)*step],add=src[base+Math.min(length-1,i+radius+1)*step];
                r+=((add>>>16)&255)-((drop>>>16)&255);
                g+=((add>>>8)&255)-((drop>>>8)&255);b+=(add&255)-(drop&255);
            }
        }
    }
    private void fill(Canvas c,ThemeSpec theme,float x,float y,float w,float h,float radius,int color){
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);
        roundRect(c,theme,new RectF(x,y,x+w,y+h),radius,radius,paint);
    }
    private void gradient(Canvas c,ThemeSpec theme,float x,float y,float w,float h,float radius,int top,int bottom){
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffffffff);
        paint.setShader(new LinearGradient(x,y,x+w*.65f,y+h,top,bottom,Shader.TileMode.CLAMP));
        roundRect(c,theme,new RectF(x,y,x+w,y+h),radius,radius,paint);paint.setShader(null);
    }
    private void edge(Canvas c,ThemeSpec theme,float x,float y,float w,float h,float radius,int color){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(color);
        roundRect(c,theme,new RectF(x,y,x+w,y+h),radius,radius,paint);paint.setStyle(Paint.Style.FILL);
    }
    /** Returns false for legacy so the renderer can retain its exact existing geometry. */
    public boolean panel(Canvas c,ThemeSpec theme,float x,float y,float w,float h,int color,int stroke){
        return draw(c,theme,SurfaceRole.PANEL,x,y,w,h,color,stroke);
    }
    /** All non-legacy surfaces use the same material recipe, regardless of role. */
    public boolean draw(Canvas c,ThemeSpec theme,SurfaceRole role,float x,float y,float w,float h,int color,int stroke){
        return draw(c,theme,role,role==SurfaceRole.GEAR_CARD?SurfaceRole.DATA_CARD:role,x,y,w,h,color,stroke);
    }
    public boolean draw(Canvas c,ThemeSpec theme,SurfaceRole role,SurfaceRole fallback,float x,float y,float w,float h,int color,int stroke){
        ThemeSpec.Material m=theme.materialFor(role,fallback);
        if(m.legacy())return false;
        if(w<=0||h<=0)return true;
        boolean sidebarButton=role==SurfaceRole.NAVIGATION_BUTTON&&theme.shell().left();
        float radius=Math.min(m.radius,Math.min(w,h)/(sidebarButton?4:2));
        if(m.glass()){
            fill(c,theme,x,y+4,w,h,radius,m.shadow);
            blur(c,theme,theme.blurFor(role),x,y,w,h,radius);
            gradient(c,theme,x,y,w,h,radius,(m.topAlpha<<24)|(color&0xffffff),(m.bottomAlpha<<24)|(color&0xffffff));
            edge(c,theme,x,y,w,h,radius,m.highlight);
            edge(c,theme,x+1.5f,y+1.5f,w-3,h-3,Math.max(0,radius-1.5f),stroke);
        }else{blur(c,theme,theme.blurFor(role),x,y,w,h,radius);fill(c,theme,x,y,w,h,radius,color);edge(c,theme,x,y,w,h,radius,stroke);}
        return true;
    }
    /** Shared panel including the original cut-corner fallback used by existing pages. */
    public void panelWithLegacy(Canvas c,ThemeSpec theme,Paint caller,float x,float y,float w,float h,int fill,int edge){
        drawWithLegacy(c,theme,SurfaceRole.PANEL,caller,x,y,w,h,fill,edge);
    }
    public void drawWithLegacy(Canvas c,ThemeSpec theme,SurfaceRole role,Paint caller,
                               float x,float y,float w,float h,int fill,int edge){
        if(draw(c,theme,role,x,y,w,h,fill,edge))return;
        if(!theme.cutPanels){
            caller.setColor(fill);c.drawRect(x,y,x+w,y+h,caller);
            caller.setStyle(Paint.Style.STROKE);caller.setStrokeWidth(1);caller.setColor(edge);
            c.drawRect(x,y,x+w,y+h,caller);caller.setStyle(Paint.Style.FILL);return;
        }
        legacyShape.reset();legacyShape.moveTo(x,y);legacyShape.lineTo(x+w-12,y);legacyShape.lineTo(x+w,y+12);
        legacyShape.lineTo(x+w,y+h);legacyShape.lineTo(x+12,y+h);legacyShape.lineTo(x,y+h-12);legacyShape.close();
        caller.setColor(fill);c.drawPath(legacyShape,caller);
        caller.setStyle(Paint.Style.STROKE);caller.setStrokeWidth(1);caller.setColor(edge);
        c.drawPath(legacyShape,caller);caller.setStyle(Paint.Style.FILL);
    }
    public void overviewCard(Canvas c,ThemeSpec theme,Paint caller,float x,float y,float w,float h,int accent){
        drawWithLegacy(c,theme,SurfaceRole.DATA_CARD,caller,x,y,w,h,theme.panel,theme.stroke);
        if(theme.overviewCardTopLine()){
            caller.setColor(accent);c.drawRect(x+theme.spacing.cardPadding,y,x+53,y+2,caller);
        }
    }
    /** Navigation appearance; bounds and hit testing stay with DashboardPage. */
    public void navigationButton(Canvas c,ThemeSpec theme,ThemeResources resources,Paint caller,
                                 String label,float x,float y,float w,float h,boolean selected,int accent){
        if(theme.shell().dock()){
            if(selected)fill(c,theme,x+2,y+2,w-4,h-4,Math.min(w,h)/4,0x22000000|(accent&0xffffff));
        }else drawWithLegacy(c,theme,SurfaceRole.NAVIGATION_BUTTON,caller,x,y,w,h,
                theme.navigationFill(selected),selected?accent:theme.stroke);
        if(selected&&!theme.shell().dock()&&theme.navigationUnderline()){
            caller.setColor(accent);c.drawRect(x+9,y+h-3,x+w-9,y+h-1,caller);
        }
        if(label==null)return;
        boolean sidebar=theme.shell().left();
        float padding=Math.min(theme.spacing.buttonPadding,sidebar?w/8:Math.max(0,w/2-2));
        caller.setTypeface(resources==null?Typeface.DEFAULT_BOLD:resources.fontForText(label,true));
        float size=theme.textSize(label,14,true);caller.setTextSize(size);float measured=caller.measureText(label);
        caller.setColor(selected?accent:theme.textSecondary);
        caller.setTextSize(measured>w-2*padding?size*(w-2*padding)/measured:size);
        float labelX=sidebar?x+(w-caller.measureText(label))/2:x+padding;
        c.drawText(label,labelX,y+h/2+5,caller);
    }
    public void navigationClock(Canvas c,ThemeSpec theme,ThemeResources resources,Paint caller,
                                String value,float x,float y,float width,float height){
        caller.setTypeface(resources==null?Typeface.DEFAULT_BOLD:resources.fontForText(value,true));
        float size=theme.textSize(value,20,true);
        caller.setTextSize(size);
        float measured=caller.measureText(value);
        if(measured>width)size*=Math.max(1,width-.25f)/measured;
        caller.setTextSize(theme.shell().dock()?size*.9f:size);
        Paint.FontMetrics metrics=caller.getFontMetrics();
        caller.setColor(theme.textPrimary);
        c.drawText(value,x+(width-caller.measureText(value))/2,y+(height-metrics.ascent-metrics.descent)/2,caller);
    }
    /** Render the card with the shared recipe and retain the layout radius for legacy themes. */
    public void card(Canvas c,ThemeSpec theme,float x,float y,float w,float h,float radius,int color){
        card(c,theme,SurfaceRole.DATA_CARD,x,y,w,h,radius,color);
    }
    public void card(Canvas c,ThemeSpec theme,SurfaceRole role,float x,float y,float w,float h,float radius,int color){
        if(draw(c,theme,role,SurfaceRole.DATA_CARD,x,y,w,h,color,theme.stroke))return;
        legacyCard(c,theme,x,y,w,h,radius,color);
    }
    public void legacyCard(Canvas c,ThemeSpec theme,float x,float y,float w,float h,float radius,int color){
        radius=Math.max(0,Math.min(radius,Math.min(w,h)/2));
        fill(c,theme,x,y,w,h,radius,color);
        edge(c,theme,x+.5f,y+.5f,w-1,h-1,Math.max(0,radius-.5f),theme.stroke);
    }
    public void settingsGroup(Canvas c,ThemeSpec theme,float x,float y,float w,float h){
        if(draw(c,theme,SurfaceRole.SETTINGS_GROUP,x,y,w,h,theme.panel,theme.stroke))return;
        float radius=Math.min(theme.material.radius,Math.min(w,h)/2);
        fill(c,theme,x,y,w,h,radius,theme.panel);
    }
    /** Paint the full viewport before fitting the menu and page to their logical coordinates. */
    public void backdrop(Canvas c,ThemeSpec theme,ThemeResources resources,int width,int height){
        if(width<=0||height<=0)return;
        Bitmap image=resources==null?null:resources.background();
        int base=resources==null?theme.background:resources.backdropColor();
        ThemeSpec.Backdrop background=resources==null?theme.backdrop:resources.backdrop();
        if(theme.maxBlur()==0){
            blurShaders.clear();blurImage=null;frameWidth=0;
        }else if(width!=frameWidth||height!=frameHeight||background!=blurBackdrop||image!=blurImage||base!=blurBase){
            blurShaders.clear();frameWidth=width;frameHeight=height;
            blurImage=image;blurBackdrop=background;blurBase=base;
            float shrink=Math.min(1,384f/Math.max(width,height));
            sampleWidth=Math.max(1,Math.round(width*shrink));sampleHeight=Math.max(1,Math.round(height*shrink));
        }
        coordinates(0,0,1);
        paintBackdrop(c,base,background,image,width,height);
    }
    public void wallpaperPreview(Canvas c,ThemeSpec.Wallpaper wallpaper,int width,int height){
        if(wallpaper.color!=null&&width>0&&height>0)paintBackdrop(c,wallpaper.color.intValue(),wallpaper.backdrop,null,width,height);
    }
    private void paintBackdrop(Canvas c,int base,ThemeSpec.Backdrop backdrop,Bitmap image,int width,int height){
        c.drawColor(base);
        paint.setStyle(Paint.Style.FILL);
        if(backdrop.mode==ThemeSpec.Backdrop.Mode.GRADIENT){
            int start=backdrop.startColor(base);
            if(backdrop!=cachedBackdrop||start!=cachedBackdropStart){
                backdropGradient=new LinearGradient(0,0,8f*backdrop.endXPercent,480,start,backdrop.endColor,Shader.TileMode.CLAMP);
                cachedBackdrop=backdrop;cachedBackdropStart=start;
            }
            c.save();
            c.scale(width/800f,height/480f);
            paint.setColor(0xffffffff);
            paint.setShader(backdropGradient);
            c.drawRect(0,0,800,480,paint);
            paint.setShader(null);
            c.restore();
        }
        if(image!=null){
            float scale=Math.max(width/(float)image.getWidth(),height/(float)image.getHeight());
            float x=(width-image.getWidth()*scale)/2;
            float y=(height-image.getHeight()*scale)/2;
            c.save();
            c.clipRect(0,0,width,height);
            paint.setColor(0xffffffff);
            c.drawBitmap(image,null,new RectF(x,y,width-x,height-y),paint);
            c.restore();
        }
        if((backdrop.scanlineColor>>>24)!=0){
            c.save();
            c.scale(1,height/480f);
            paint.setColor(backdrop.scanlineColor);
            paint.setStrokeWidth(1);
            for(int y=56;y<480;y+=8)c.drawLine(0,y,width,y,paint);
            c.restore();
        }
    }
    /** Shared continuous/segmented level bar. Invalid readings are passed as zero. */
    public void levelBar(Canvas c,Paint p,float x,float y,float w,float h,float fraction,int fill,int track,int style){
        fraction=Math.min(1,Math.max(0,fraction));
        if(style==1){
            float step=w/20f,gap=Math.min(3,step*.28f);
            for(int i=0;i<20;i++){p.setColor(fraction>i/20f?fill:track);c.drawRect(x+i*step,y,x+(i+1)*step-gap,y+h,p);}
        }else{
            p.setColor(track);c.drawRect(x,y,x+w,y+h,p);
            if(fraction>0){p.setColor(fill);c.drawRect(x,y,x+w*fraction,y+h,p);}
        }
    }
    public boolean header(Canvas c,ThemeSpec theme){return panel(c,theme,5,1,790,51,theme.panel,theme.stroke);}
}
