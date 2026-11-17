package com.veloster.dashboard.view;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeResources;

import com.veloster.dashboard.view.overview.OverviewLayout;
import com.veloster.dashboard.view.overview.OverviewRenderer;
import com.veloster.dashboard.view.gauge.GaugeLayout;
import com.veloster.dashboard.view.gauge.GaugeRenderer;
import com.veloster.dashboard.view.graph.GraphLayout;
import com.veloster.dashboard.view.graph.GraphRenderer;
import com.veloster.dashboard.view.f1.ClusterRenderer;
import com.veloster.dashboard.view.performance.PerformanceRenderer;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Bitmap;
import java.util.Locale;

/** Original N/PIT drawing geometry, with only received vehicle samples as values. */
public final class PitRenderer {
    private final Paint paint=new Paint(3);
    private final Path shape=new Path();
    private final Shader background=new LinearGradient(0,0,800,480,0xff111315,0xff030405,Shader.TileMode.CLAMP);
    private static final int BG=0xff040506,PANEL=0xff101315,LINE=0xff303638,BLUE=0xffa4d9ef,WHITE=0xffe1edf2,MUTED=0xff91a5ae,RED=0xffff4059;
    private final ClusterRenderer clusterRenderer=new ClusterRenderer(false);
    private Bitmap artwork;
    private final OverviewRenderer overviewRenderer=new OverviewRenderer();
    private final GaugeRenderer gaugeRenderer=new GaugeRenderer();
    private final GraphRenderer graphRenderer=new GraphRenderer();
    private GraphLayout graphLayout;
    private GaugeLayout gaugeLayout;
    private OverviewLayout overviewLayout=new OverviewLayout();
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeResources resources;
    private boolean performancePage;
    private final PerformanceRenderer performanceRenderer=new PerformanceRenderer();
    private final java.text.SimpleDateFormat clockFormat=new java.text.SimpleDateFormat("HH:mm",Locale.US);
    private long clockMinute=Long.MIN_VALUE;
    private String clockText="";
    private String clock(){
        long now=System.currentTimeMillis(),minute=now/60000;
        if(minute!=clockMinute){clockMinute=minute;clockFormat.setTimeZone(java.util.TimeZone.getDefault());clockText=clockFormat.format(new java.util.Date(now));}
        return clockText;
    }
    public void setPerformancePage(boolean value){performancePage=value;}
    public void setArtwork(Bitmap value){artwork=value;}
    public void setGaugeLayout(GaugeLayout value){gaugeLayout=value;}
    public void setGraphLayout(GraphLayout value){graphLayout=value;}
    public void setOverviewLayout(OverviewLayout value){if(value!=null)overviewLayout=value;}
    public void setTheme(ThemeSpec value){setTheme(value,null);}
    public void setTheme(ThemeSpec value,ThemeResources assets){theme=value==null?ThemeSpec.original():value;resources=assets;overviewRenderer.setTheme(theme,assets);clusterRenderer.setTheme(theme,assets);gaugeRenderer.setTheme(theme,assets);graphRenderer.setTheme(theme,assets);performanceRenderer.setTheme(theme,assets);}
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now){
        draw(c,width,height,cluster,model,now,ShiftGuide.DEFAULT);
    }
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now,int target){
        draw(c,width,height,cluster,model,now,target,false,null);
    }
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now,int target,boolean media,MediaState.Snapshot track){
        boolean mediaPage=cluster&&media,pdProfile=model.pdProfile(),jsProfile=model.jsProfile();
        overviewLayout.pdProfile(pdProfile||jsProfile);performanceRenderer.profile(pdProfile,jsProfile,model.jsnProfile(),model.supportedProfile());
        Telemetry.Sample[] samples=model.snapshot(now);
        clusterRenderer.prepare(model,samples,now,ShiftGuide.clamp(target,model.pdLayoutProfile()),mediaPage,track,artwork);
        c.save();c.scale(width/800f,height/480f);c.drawColor(theme.background);
        if(theme.isOriginal()){paint.setShader(background);c.drawRect(0,0,800,480,paint);paint.setShader(null);}
        else if(resources!=null&&resources.background()!=null)c.drawBitmap(resources.background(),null,new android.graphics.RectF(0,0,800,480),paint);
        for(int y=56;y<480;y+=8)stroke(c,0,y,800,y,theme.isOriginal()?0x063f4948:0x18314a50,1);
        box(c,0,0,800,52,theme.isOriginal()?0xff090b0c:0xff0b1b22);box(c,18,51,782,52,theme.stroke);
        button(c,UiText.t("퍼포먼스"),DashboardPage.left(DashboardPage.PERFORMANCE),8,DashboardPage.width(DashboardPage.PERFORMANCE),38,performancePage,theme.accent);
        button(c,UiText.t("전체 조망"),144,8,100,38,!performancePage&&!cluster&&gaugeLayout==null&&graphLayout==null,theme.accent);
        button(c,"F1",250,8,72,38,cluster&&gaugeLayout==null&&graphLayout==null,theme.accent);
        button(c,UiText.t("게이지"),328,8,84,38,gaugeLayout!=null,theme.accent);
        button(c,UiText.t("그래프"),418,8,110,38,graphLayout!=null,theme.accent);
        button(c,UiText.language().toUpperCase(Locale.US),534,8,48,38,false,theme.accent);
        centered(c,clock(),620,33,22,theme.textPrimary,true);
        button(c,UiText.t("돌아가기"),666,8,116,38,false,theme.accent);
        if(performancePage)performanceRenderer.draw(c,model,now);
        else if(graphLayout!=null)graphRenderer.draw(c,model,now,graphLayout);
        else if(gaugeLayout!=null)gaugeRenderer.draw(c,model,now,gaugeLayout);
        else if(mediaPage)clusterRenderer.drawCombined(c);else if(cluster)clusterRenderer.drawCluster(c);else {overviewRenderer.draw(c,samples,overviewLayout,pdProfile,jsProfile);drawOverviewPager(c);}
        if(!performancePage&&graphLayout==null&&!mediaPage && track!=null && track.notice)clusterRenderer.drawMedia(c);
        if(cluster&&gaugeLayout==null&&graphLayout==null)clusterRenderer.drawArtworkToggle(c);
        c.restore();
    }
    private void box(Canvas c,float l,float t,float r,float b,int color){paint.setColor(color);c.drawRect(l,t,r,b,paint);}
    private void drawOverviewPager(Canvas c){if(theme.isOriginal()||overviewLayout.pageCount()<2)return;centered(c,"‹",358,470,20,theme.accent,true);centered(c,(overviewLayout.page()+1)+" / "+overviewLayout.pageCount(),400,470,14,theme.textPrimary,true);centered(c,"›",442,470,20,theme.accent,true);}
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int edge){
        if(!theme.cutPanels){paint.setColor(fill);c.drawRect(x,y,x+w,y+h,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(edge);c.drawRect(x,y,x+w,y+h,paint);paint.setStyle(Paint.Style.FILL);return;}
        shape.reset();shape.moveTo(x,y);shape.lineTo(x+w-12,y);shape.lineTo(x+w,y+12);
        shape.lineTo(x+w,y+h);shape.lineTo(x+12,y+h);shape.lineTo(x,y+h-12);shape.close();
        paint.setColor(fill);c.drawPath(shape,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(edge);c.drawPath(shape,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void stroke(Canvas c,float x,float y,float x2,float y2,int color,float width){
        paint.setColor(color);paint.setStrokeWidth(width);c.drawLine(x,y,x2,y2,paint);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(s,bold));c.drawText(s,x,y,paint);
    }
    private void button(Canvas c,String s,float x,float y,float w,float h,boolean on,int accent){
        panel(c,x,y,w,h,on?(theme.isOriginal()?0xff233b46:theme.panelRaised):(theme.isOriginal()?0xff111415:theme.panel),on?accent:theme.stroke);
        if(on)box(c,x+9,y+h-3,x+w-9,y+h-1,accent);
        paint.setTypeface(resources==null?Typeface.DEFAULT_BOLD:resources.fontForText(s,true));paint.setTextSize(14);float measured=paint.measureText(s);
        text(c,s,x+12,y+h/2+5,measured>w-24?14*(w-24)/measured:14,on?accent:theme.textSecondary,true);
    }
    private void centered(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setTextSize(size);paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(s,bold));
        text(c,s,x-paint.measureText(s)/2,y,size,color,bold);
    }
}
