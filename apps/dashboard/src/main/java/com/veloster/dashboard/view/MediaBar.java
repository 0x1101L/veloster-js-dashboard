package com.veloster.dashboard.view;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSpec.SurfaceRole;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.vehicle.MediaState;
import com.veloster.i18n.UiText;

/** Shared dashboard footer and side media panel. */
public final class MediaBar {
    public static final int ALWAYS=0,HIDDEN=1,PLAYING=2,DEFAULT=ALWAYS;
    public static final int NONE=-10,PREVIOUS=-1,PLAY=0,NEXT=1,TOGGLE=2;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path transportIcon=new Path();
    private final ThemeSurface surface;
    public MediaBar(){this(new ThemeSurface());}
    public MediaBar(ThemeSurface surface){this.surface=surface;}

    public static int normalize(int value){return value>=ALWAYS&&value<=PLAYING?value:DEFAULT;}
    public static String label(int value){return UiText.t(normalize(value)==HIDDEN?"항상 숨기기":normalize(value)==PLAYING?"미디어 재생 중에만":"항상 보기");}
    public static boolean visible(int policy,MediaState.Snapshot media){return normalize(policy)==ALWAYS||(normalize(policy)==PLAYING&&media!=null&&media.playState==1);}
    public static boolean footerVisible(DashboardPage.Regions regions,int policy,MediaState.Snapshot media){
        return !regions.mediaEnabled&&visible(policy,media);
    }
    public static boolean noticeVisible(DashboardPage.Regions regions,int policy,boolean enabled,MediaState.Snapshot media){
        return !regions.mediaEnabled&&policy==HIDDEN&&enabled&&media!=null&&media.notice;
    }
    public static DashboardPage.Bounds noticeBounds(DashboardPage.Bounds b,long age){
        float phase=Math.max(0,Math.min(1f,Math.min(age/250f,(5000-age)/250f)));
        return new DashboardPage.Bounds(b.x,b.y+b.height*(1-phase)*(1-phase),b.width,b.height);
    }
    public static int hit(DashboardPage.Content content,float x,float y){
        if(content.footer(TOGGLE).contains(x,y))return TOGGLE;
        if(content.footer(PREVIOUS).contains(x,y))return PREVIOUS;
        if(content.footer(PLAY).contains(x,y))return PLAY;
        if(content.footer(NEXT).contains(x,y))return NEXT;
        return NONE;
    }
    public static int regionHit(DashboardPage.Bounds region,float x,float y){
        return regionHit(null,region,x,y);
    }
    public static int regionHit(ThemeSpec theme,DashboardPage.Bounds region,float x,float y){
        return regionHit(theme,region,x,y,region!=null&&region.width>=400);
    }
    public static int regionHit(ThemeSpec theme,DashboardPage.Bounds region,float x,float y,boolean wide){
        if(region==null||!region.contains(x,y))return NONE;
        RegionLayout layout=new RegionLayout(theme,region,true,wide);
        if(layout.previous.contains(x,y))return PREVIOUS;
        if(layout.play.contains(x,y))return PLAY;
        if(layout.next.contains(x,y))return NEXT;
        return NONE;
    }
    private static final class Values {
        final float padding,cardRadius,coverGap,coverMax,coverInset,sourceHeight,titleHeight,titleSize,detailsGap,detailsHeight;
        final float artistSize,sourceSize,sourceGap,progressGap,progressHeight,timeGap,timeHeight,timeSize,controlsGap;
        final float playWidth,playHeight,sideWidth,sideHeight,buttonGap,buttonRadius,iconHalfWidth,iconHalfHeight,iconTip,emptyIconSize;
        Values(float padding,float cardRadius,float coverGap,float coverMax,float coverInset,float sourceHeight,float titleHeight,float titleSize,
               float detailsGap,float detailsHeight,float artistSize,float sourceSize,float sourceGap,float progressGap,float progressHeight,
               float timeGap,float timeHeight,float timeSize,float controlsGap,float playWidth,float playHeight,
               float sideWidth,float sideHeight,float buttonGap,float buttonRadius,float iconHalfWidth,float iconHalfHeight,
               float iconTip,float emptyIconSize){
            this.padding=padding;this.cardRadius=cardRadius;this.coverGap=coverGap;this.coverMax=coverMax;this.coverInset=coverInset;this.sourceHeight=sourceHeight;
            this.titleHeight=titleHeight;this.titleSize=titleSize;this.detailsGap=detailsGap;this.detailsHeight=detailsHeight;
            this.artistSize=artistSize;this.sourceSize=sourceSize;this.sourceGap=sourceGap;this.progressGap=progressGap;this.progressHeight=progressHeight;
            this.timeGap=timeGap;this.timeHeight=timeHeight;this.timeSize=timeSize;this.controlsGap=controlsGap;
            this.playWidth=playWidth;this.playHeight=playHeight;this.sideWidth=sideWidth;this.sideHeight=sideHeight;
            this.buttonGap=buttonGap;this.buttonRadius=buttonRadius;this.iconHalfWidth=iconHalfWidth;this.iconHalfHeight=iconHalfHeight;
            this.iconTip=iconTip;this.emptyIconSize=emptyIconSize;
        }
    }
    // Normal-screen values are the baseline; wide-screen values are listed explicitly at 1.5x.
    private static final Values NORMAL=new Values(9.33f,14.67f,10.67f,245.33f,12.27f,16f,26f,20f,
            2.67f,16.67f,13f,11f,4f,14.67f,3.33f,1.33f,16.67f,10f,14f,
            60f,48f,48f,48f,4f,10.67f,6.67f,8.67f,8.67f,45.33f);
    private static final Values WIDE=new Values(14f,22f,16f,368f,18.4f,24f,39f,30f,
            4f,25f,19.5f,16.5f,6f,22f,5f,2f,25f,15f,21f,
            90f,72f,72f,72f,6f,16f,10f,13f,13f,68f);
    private static final class RegionLayout {
        final Values values;
        final float progressY;
        final DashboardPage.Bounds card,cover,source,title,details,times,previous,play,next;
        RegionLayout(ThemeSpec theme,DashboardPage.Bounds region,boolean controls,boolean wide){
            values=wide?WIDE:NORMAL;
            int padding=theme==null?-1:theme.shell().mediaPadding;
            float inset=wide&&padding>=0?padding:values.padding;
            card=region.inset(inset);
            float controlsHeight=Math.max(values.playHeight,values.sideHeight);
            float stackBelowCover=values.coverGap+values.sourceHeight+values.sourceGap+values.titleHeight+values.detailsGap+values.detailsHeight+values.progressGap
                    +values.progressHeight+values.timeGap+values.timeHeight+(controls?values.controlsGap+controlsHeight:0);
            float slot=Math.max(0,Math.min(values.coverMax,Math.min(card.width,card.height-stackBelowCover)));
            float center=card.atX(.5f),top=card.atY(.5f)-(slot+stackBelowCover)/2;
            float size=Math.max(0,slot-values.coverInset*2),left=center-size/2;
            cover=new DashboardPage.Bounds(left,top+(slot-size)/2,size,size);
            source=new DashboardPage.Bounds(left,top+slot+values.coverGap,size,values.sourceHeight);
            title=new DashboardPage.Bounds(left,source.bottom()+values.sourceGap,size,values.titleHeight);
            details=new DashboardPage.Bounds(left,title.bottom()+values.detailsGap,size,values.detailsHeight);
            progressY=details.bottom()+values.progressGap;
            times=new DashboardPage.Bounds(left,progressY+values.progressHeight+values.timeGap,size,values.timeHeight);
            float buttonGap=theme!=null&&theme.hasMediaButtonGap()?theme.mediaButtonGap((int)values.buttonGap)
                    :hasButtons(theme)?(wide?18f:12f):values.buttonGap;
            float groupWidth=values.sideWidth*2+values.playWidth+buttonGap*2;
            float groupLeft=center-groupWidth/2,sideY=times.bottom()+values.controlsGap+(controlsHeight-values.sideHeight)/2;
            previous=new DashboardPage.Bounds(groupLeft,sideY,values.sideWidth,values.sideHeight);
            play=new DashboardPage.Bounds(groupLeft+values.sideWidth+buttonGap,sideY+(values.sideHeight-values.playHeight)/2,values.playWidth,values.playHeight);
            next=new DashboardPage.Bounds(play.right()+buttonGap,sideY,values.sideWidth,values.sideHeight);
        }
    }
    /** Right-side media widget; bounds are in display pixels. */
    public void drawRegion(Canvas c,ThemeSpec theme,ThemeResources resources,MediaState.Snapshot track,
                           Bitmap artwork,DashboardPage.Bounds region){
        drawRegion(c,theme,resources,track,artwork,region,region.width>=400);
    }
    public void drawRegion(Canvas c,ThemeSpec theme,ThemeResources resources,MediaState.Snapshot track,
                           Bitmap artwork,DashboardPage.Bounds region,boolean wide){
        drawPlayer(c,theme,resources,track,artwork,new RegionLayout(theme,region,true,wide),true);
    }
    private void drawPlayer(Canvas c,ThemeSpec theme,ThemeResources resources,MediaState.Snapshot track,
                            Bitmap artwork,RegionLayout l,boolean controls){
        Values v=l.values;
        if(!controls||hasContainer(theme))surface.card(c,theme,SurfaceRole.MEDIA_CONTAINER,l.card.x,l.card.y,l.card.width,l.card.height,v.cardRadius,theme.panel);
        DashboardPage.Bounds art=l.cover;
        float artRadius=theme.material.radius;
        paint.setColor(theme.panelRaised);
        surface.roundRect(c,theme,new RectF(art.x,art.y,art.right(),art.bottom()),artRadius,artRadius,paint);
        if(artwork!=null&&art.width>0){
            drawArtwork(c,artwork,art.x,art.y,art.width,artRadius,theme,surface);
        }else if(art.width>0){
            centeredRegionGlyph(c,theme,resources,"♪",art,v.emptyIconSize,theme.textSecondary);
        }

        String source=track==null?"":track.source;
        String artist=track==null?"":track.artist;
        String title=track==null||track.title.length()==0?UiText.t("미디어 데이터 없음"):track.title;
        if(!source.isEmpty())regionText(c,theme,resources,source,l.source,v.sourceSize,theme.textSecondary,false,false);
        marqueeText(c,theme,resources,title,l.title,v.titleSize,theme.textPrimary,true,0);
        if(!artist.isEmpty()){
            marqueeText(c,theme,resources,artist,l.details,v.artistSize,theme.textSecondary,false,1800);
        }

        boolean hasProgress=track!=null&&track.hasProgress();
        float barHeight=hasProgress?v.progressHeight:Math.min(1,v.progressHeight);
        paint.setColor(theme.stroke);
        surface.roundRect(c,theme,new RectF(art.x,l.progressY,art.right(),l.progressY+barHeight),barHeight/2,barHeight/2,paint);
        if(hasProgress){
            float elapsed=art.width*track.progress();
            if(elapsed>0){
                paint.setColor(theme.accent);
                surface.roundRect(c,theme,new RectF(art.x,l.progressY,art.x+elapsed,l.progressY+barHeight),barHeight/2,barHeight/2,paint);
            }
            regionText(c,theme,resources,MediaState.time(Math.min(track.positionMs,track.durationMs)),l.times,v.timeSize,theme.textSecondary,false,false);
            regionText(c,theme,resources,MediaState.time(track.durationMs),l.times,v.timeSize,theme.textSecondary,false,true);
        }
        if(controls){
            drawPanelTransport(c,theme,l.previous,PREVIOUS,false,v);
            drawPanelTransport(c,theme,l.play,PLAY,track!=null&&track.playState==1,v);
            drawPanelTransport(c,theme,l.next,NEXT,false,v);
        }
    }
    /** Fit the full image inside a themed square; round only its visible outer corners. */
    public static void drawArtwork(Canvas c,Bitmap artwork,float x,float y,float size,float radius){
        drawArtwork(c,artwork,x,y,size,radius,null,null);
    }
    public static void drawArtwork(Canvas c,Bitmap artwork,float x,float y,float size,float radius,
                                   ThemeSpec theme,ThemeSurface surface){
        float fitRatio=size/Math.max(artwork.getWidth(),artwork.getHeight());
        float w=artwork.getWidth()*fitRatio,h=artwork.getHeight()*fitRatio;
        float left=x+(size-w)/2,top=y+(size-h)/2;
        Paint image=new Paint(3);
        BitmapShader shader=new BitmapShader(artwork,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        Matrix matrix=new Matrix();matrix.setScale(fitRatio,fitRatio);matrix.postTranslate(left,top);
        shader.setLocalMatrix(matrix);image.setShader(shader);
        // A non-square bitmap is centered without rounding its internal letterbox edges.
        float rx=Math.abs(w-size)<.01f&&Math.abs(h-size)<.01f?Math.min(radius,size/2):0;
        if(surface==null) c.drawRoundRect(new RectF(left,top,left+w,top+h),rx,rx,image);
        else surface.roundRect(c,theme,new RectF(left,top,left+w,top+h),rx,rx,image);
    }
    private void drawTransport(Canvas c,ThemeSpec theme,DashboardPage.Bounds b,int action,boolean playing,float unit){
        if(!hasButtons(theme)){transportIcon(c,b,action,playing,unit,theme.textPrimary);return;}
        boolean primary=action==PLAY;
        float radius=16*unit;
        boolean styled=surface.draw(c,theme,SurfaceRole.MEDIA_BUTTON,b.x,b.y,b.width,b.height,
                theme.panelRaised,primary?theme.accent:theme.stroke);
        if(!styled){
            if(primary){
                paint.setColor(theme.accent);
                surface.roundRect(c,theme,new RectF(b.x,b.y,b.right(),b.bottom()),radius,radius,paint);
            }else surface.legacyCard(c,theme,b.x,b.y,b.width,b.height,radius,theme.panelRaised);
        }
        transportIcon(c,b,action,playing,unit,primary?(styled?theme.accent:onAccent(theme.accent)):theme.textPrimary);
    }
    private void drawPanelTransport(Canvas c,ThemeSpec theme,DashboardPage.Bounds b,int action,boolean playing,Values v){
        if(!hasButtons(theme)){panelTransportIcon(c,b,action,playing,v,theme.textPrimary);return;}
        boolean primary=action==PLAY;
        boolean styled=surface.draw(c,theme,SurfaceRole.MEDIA_BUTTON,b.x,b.y,b.width,b.height,
                theme.panelRaised,primary?theme.accent:theme.stroke);
        if(!styled){
            if(primary){paint.setColor(theme.accent);surface.roundRect(c,theme,new RectF(b.x,b.y,b.right(),b.bottom()),v.buttonRadius,v.buttonRadius,paint);}
            else surface.legacyCard(c,theme,b.x,b.y,b.width,b.height,v.buttonRadius,theme.panelRaised);
        }
        panelTransportIcon(c,b,action,playing,v,primary?(styled?theme.accent:onAccent(theme.accent)):theme.textPrimary);
    }
    private void panelTransportIcon(Canvas c,DashboardPage.Bounds b,int action,boolean playing,Values v,int ink){
        paint.setColor(ink);
        float x=b.atX(.5f),y=b.atY(.5f),bar=v.iconHalfWidth*.3f;
        if(playing){
            c.drawRect(x-v.iconHalfWidth,y-v.iconHalfHeight,x-bar,y+v.iconHalfHeight,paint);
            c.drawRect(x+bar,y-v.iconHalfHeight,x+v.iconHalfWidth,y+v.iconHalfHeight,paint);
        }else if(action==PLAY)triangle(c,x-v.iconHalfWidth,x+v.iconTip,y,v.iconHalfHeight);
        else{
            float direction=action==NEXT?1:-1;
            triangle(c,x-v.iconHalfWidth*direction,x+v.iconHalfWidth*.5f*direction,y,v.iconHalfWidth);
            float left=direction>0?x+v.iconHalfWidth*.7f:x-v.iconHalfWidth*1.1f;
            c.drawRect(left,y-v.iconHalfWidth,left+bar,y+v.iconHalfWidth,paint);
        }
    }
    private void transportIcon(Canvas c,DashboardPage.Bounds b,int action,boolean playing,float unit,int ink){
        paint.setColor(ink);
        float x=b.atX(.5f),y=b.atY(.5f);
        if(playing){
            c.drawRect(x-10*unit,y-13*unit,x-3*unit,y+13*unit,paint);
            c.drawRect(x+3*unit,y-13*unit,x+10*unit,y+13*unit,paint);
        }else if(action==PLAY)triangle(c,x-8*unit,x+13*unit,y,13*unit);
        else {
            float direction=action==NEXT?1:-1;
            triangle(c,x-10*unit*direction,x+5*unit*direction,y,10*unit);
            c.drawRect(x+(direction>0?7:-11)*unit,y-10*unit,x+(direction>0?11:-7)*unit,y+10*unit,paint);
        }
    }
    private static boolean hasContainer(ThemeSpec theme){return theme==null||theme.mediaControls()!=ThemeSpec.MediaControls.BUTTONS;}
    private static boolean hasButtons(ThemeSpec theme){return theme==null||theme.mediaControls()!=ThemeSpec.MediaControls.CONTAINER;}
    private void triangle(Canvas c,float base,float tip,float y,float halfHeight){
        transportIcon.reset();
        transportIcon.moveTo(base,y-halfHeight);transportIcon.lineTo(tip,y);
        transportIcon.lineTo(base,y+halfHeight);transportIcon.close();
        c.drawPath(transportIcon,paint);
    }
    private static int onAccent(int color){
        double luminance=.2126*linearChannel((color>>16)&255)+.7152*linearChannel((color>>8)&255)+.0722*linearChannel(color&255);
        return luminance>.179?0xff000000:0xffffffff;
    }
    private static double linearChannel(int channel){
        double value=channel/255.0;
        return value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);
    }
    private float regionText(Canvas c,ThemeSpec theme,ThemeResources resources,String value,DashboardPage.Bounds b,
                             float size,int color,boolean bold,boolean right){
        paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.font("body",bold));
        paint.setTextSize(theme.textSize("body",value,size,bold));
        Paint.FontMetrics metrics=paint.getFontMetrics();
        value=fitText(value,b.width);
        float width=paint.measureText(value);
        paint.setColor(color);
        c.drawText(value,right?b.right()-width:b.x,b.atY(.5f)-(metrics.ascent+metrics.descent)/2,paint);
        return width;
    }
    private void centeredRegionGlyph(Canvas c,ThemeSpec theme,ThemeResources resources,String value,DashboardPage.Bounds b,float size,int color){
        paint.setTypeface(resources==null?Typeface.DEFAULT:resources.font("body",false));
        paint.setTextSize(theme.textSize("body",value,size,false));
        Paint.FontMetrics metrics=paint.getFontMetrics();
        paint.setColor(color);
        float width=paint.measureText(value);
        c.drawText(value,b.atX(.5f)-width/2,b.atY(.5f)-(metrics.ascent+metrics.descent)/2,paint);
    }
    private void marqueeText(Canvas c,ThemeSpec theme,ThemeResources resources,String value,DashboardPage.Bounds b,
                             float size,int color,boolean bold,long phaseOffset){
        paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.font("body",bold));
        paint.setTextSize(theme.textSize("body",value,size,bold));
        Paint.FontMetrics metrics=paint.getFontMetrics();
        float width=paint.measureText(value),y=b.atY(.5f)-(metrics.ascent+metrics.descent)/2;
        paint.setColor(color);
        if(width<=b.width){c.drawText(value,b.x,y,paint);return;}
        float overflow=width-b.width,gap=Math.max(size*1.5f,24f),travel=overflow+gap;
        long pause=900,moveMs=(long)(travel/36f*1000f),period=pause+moveMs+pause;
        long elapsed=(System.currentTimeMillis()+phaseOffset)%Math.max(1,period);
        float offset=elapsed<pause?0:elapsed<pause+moveMs?-travel*(elapsed-pause)/(float)moveMs:-travel;
        int save=c.save();c.clipRect(b.x,b.y,b.right(),b.bottom());
        c.drawText(value,b.x+offset,y,paint);
        if(offset<0)c.drawText(value,b.x+offset+width+gap,y,paint);
        c.restoreToCount(save);
    }
    public void drawAdaptive(Canvas c,ThemeSpec theme,ThemeResources resources,MediaState.Snapshot track,Bitmap artwork,boolean expanded,DashboardPage.Content content){
        DashboardPage.Bounds strip=content.footer(NONE);
        int stripColor=theme.panel;
        if(theme.footerContainerVisible()&&!surface.draw(c,theme,SurfaceRole.MEDIA_CONTAINER,SurfaceRole.PANEL,strip.x,strip.y,strip.width,strip.height,stripColor,theme.stroke)){
            rect(c,strip,theme.panel);
        }
        for(int action:new int[]{TOGGLE,PREVIOUS,PLAY,NEXT}){
            DashboardPage.Bounds b=content.footer(action);
            boolean selected=action==TOGGLE&&expanded;
            DashboardPage.Bounds area=b;
            if(theme.footerButtonBackground()&&!surface.draw(c,theme,SurfaceRole.MEDIA_BUTTON,area.x,area.y,area.width,area.height,
                    selected?theme.panelRaised:theme.panel,selected?theme.accent:theme.stroke)){
                rect(c,b,selected?theme.panelRaised:theme.panel);
            }
            if(action!=TOGGLE&&theme.footerUsesLegacyIcons())transportIcon(c,b,action,action==PLAY&&track!=null&&track.playState==1,.63f,theme.textPrimary);
            else if(action!=TOGGLE){String value=action==PREVIOUS?UiText.t("이전"):action==NEXT?UiText.t("다음"):track!=null&&track.playState==1?"Ⅱ":"▶";
                centeredText(c,theme,resources,value,b.x,b.y,b.width,b.height,14,theme.textPrimary);}
        }
        DashboardPage.Bounds art=content.footer(TOGGLE);
        if(artwork==null)centeredText(c,theme,resources,"♪",art.x,art.y,art.width,art.height,24,theme.accent);
        else drawArtwork(c,artwork,art.atX(.5f)-18,art.atY(.5f)-18,36,theme.material.radius,theme,surface);
        float x=art.x+art.width+10,width=content.footer(PREVIOUS).x-x-10;
        String source=track==null||track.source.length()==0?UiText.t("음악"):track.source;
        String artist=track==null?"":track.artist;
        text(c,theme,resources,source+(artist.length()==0?"":" · "+artist),x,strip.y+15,11,width,theme.textSecondary);
        text(c,theme,resources,track==null||track.title.length()==0?UiText.t("미디어 데이터 없음"):track.title,x,strip.y+36,14,width,theme.textPrimary);
    }
    private void rect(Canvas c,DashboardPage.Bounds b,int color){
        paint.setColor(color);c.drawRect(b.x,b.y,b.right(),b.bottom(),paint);
    }
    private void centeredText(Canvas canvas,ThemeSpec theme,ThemeResources resources,String value,float x,float y,float width,float height,float size,int color){
        paint.setTextSize(theme.textSize(value,size,false));
        paint.setTypeface(resources==null?Typeface.DEFAULT:resources.fontForText(value,false));
        float measured=paint.measureText(value),available=Math.max(1,width-16);
        if(measured>available)paint.setTextSize(paint.getTextSize()*available/measured);
        Paint.FontMetrics metrics=paint.getFontMetrics();
        paint.setColor(color);paint.setStyle(Paint.Style.FILL);
        canvas.drawText(value,x+(width-paint.measureText(value))/2,y+height/2-(metrics.ascent+metrics.descent)/2,paint);
    }
    private void text(Canvas canvas,ThemeSpec theme,ThemeResources resources,String value,float x,float y,float size,float width,int color){
        paint.setColor(color);paint.setTextSize(theme.textSize(value,size,false));
        paint.setTypeface(resources==null?Typeface.DEFAULT:resources.fontForText(value,false));
        canvas.drawText(fitText(value,width),x,y,paint);
    }
    private String fitText(String value,float width){
        if(paint.measureText(value)>width){
            if(paint.measureText("…")>width)return "";
            int end=value.length();while(end>0&&paint.measureText(value.substring(0,end)+"…")>width)end--;
            if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;
            value=value.substring(0,end)+"…";
        }
        return value;
    }
}
