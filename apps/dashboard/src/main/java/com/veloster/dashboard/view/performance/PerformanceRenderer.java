package com.veloster.dashboard.view.performance;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.view.DashboardPage;
import com.veloster.dashboard.view.DashboardPage.Bounds;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemePalette;

import com.veloster.dashboard.vehicle.js.JsGearState;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import com.veloster.i18n.UiText;
import java.util.Locale;

/** Reference-inspired layout. Draws only existing received Telemetry samples; no new decoding. */
public final class PerformanceRenderer {
    private final ThemeSurface surface=new ThemeSurface();
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeResources resources;
    public void setTheme(ThemeSpec value,ThemeResources assets){theme=value==null?ThemeSpec.original():value;resources=assets;}
    private Bounds area;
    // Anchors retain the relative position of each instrument in the original design.
    private float x(float anchor){return area.atX((anchor-14)/772f);}
    private float y(float anchor){return area.atY((anchor-60)/406f);}
    private float w(float span){return area.width*span/772f;}
    private float h(float span){return area.height*span/406f;}
    private float uniform(float span){return Math.min(w(span),h(span));}
    private RectF rect(float l,float t,float r,float b){return new RectF(x(l),y(t),x(r),y(b));}
    private void circle(Canvas c,float cx,float cy,float radius){c.drawCircle(x(cx),y(cy),uniform(radius),p);}
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Telemetry.Sample[] samples;
    private boolean pd,js,jsn,supported;
    public void profile(boolean isPd,boolean isJs,boolean isJsn,boolean isSupported){pd=isPd;js=isJs;jsn=isJsn;supported=isSupported;}
    private String vehicleName(){return pd?"i30 N Line":js?"Veloster (WIP)":jsn?"VELOSTER N":supported?"Dashboard":"UNSUPPORTED";}
    private boolean valid(int i){return samples[i].valid&&!Float.isNaN(samples[i].value)&&!Float.isInfinite(samples[i].value);}
    private float number(int i){return Telemetry.displayValue(i,samples[i].value);}
    private String value(int i,String format){return valid(i)?String.format(Locale.US,format,number(i)):"—";}
    private float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align,boolean bold){
        rawText(c,s,x,y,theme.textSize(s,size,bold),color,align,bold);
    }
    private void rawText(Canvas c,String s,float x,float y,float size,int color,Paint.Align align,boolean bold){
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextAlign(align);p.setTextSize(size);p.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(s,bold));c.drawText(s,x(x),y(y),p);
    }
    private void fit(Canvas c,String s,float x,float y,float size,float width,int color,Paint.Align align){
        size=theme.textSize(s,size,false);
        p.setTypeface(resources==null?Typeface.DEFAULT:resources.fontForText(s,false));p.setTextSize(size);float measured=p.measureText(s);width=w(width);
        rawText(c,s,x,y,measured>width?size*width/measured:size,color,align,false);
    }
    private void line(Canvas c,float x,float y,float x2,float y2,int color,float width){p.setColor(color);p.setStrokeWidth(width);c.drawLine(x(x),y(y),x(x2),y(y2),p);}
    private void box(Canvas c,float l,float t,float r,float b){
        l=x(l);t=y(t);r=x(r);b=y(b);
        if(surface.panel(c,theme,l,t,r-l,b-t,ThemePalette.panel(theme,0xff10212b),ThemePalette.stroke(theme,0xff29424e)))return;
        p.setStyle(Paint.Style.FILL);p.setColor(ThemePalette.panel(theme,0xff10212b));if(theme.cutPanels)surface.roundRect(c,theme,new RectF(l,t,r,b),7,7,p);else c.drawRect(l,t,r,b,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(ThemePalette.stroke(theme,0xff29424e));if(theme.cutPanels)surface.roundRect(c,theme,new RectF(l,t,r,b),7,7,p);else c.drawRect(l,t,r,b,p);p.setStyle(Paint.Style.FILL);
    }
    private void arc(Canvas c,RectF bounds,float start,float sweep,int color,float width){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);c.drawArc(bounds,start,sweep,false,p);p.setStyle(Paint.Style.FILL);
    }
    /** Called below the common header, on the already scaled canvas. */
    public void draw(Canvas c,Telemetry model,long now,DashboardPage.Content content){
        area=content.padded(theme);
        samples=model.snapshot(now);
        text(c,pd?"PD":js?"JS":jsn?"N":"—",16,82,25,ThemePalette.accent(theme,0xffa4d9ec),Paint.Align.LEFT,true);
        text(c,"PERFORMANCE",42,80,19,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.LEFT,true);
        text(c,vehicleName(),233,79,13,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.LEFT,false);
        box(c,426,60,580,87);
        text(c,UiText.t("배터리"),438,78,13,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.LEFT,false);
        text(c,value(Telemetry.BAT_VOLTAGE,"%.2f")+" V",569,80,17,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.RIGHT,false);
        engine(c);headline(c);temperatures(c);pedals(c);forces(c);tires(c);
    }
    private void engine(Canvas c){
        text(c,"ENGINE",20,114,14,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.LEFT,false);
        text(c,"rpm",275,114,12,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.RIGHT,false);
        float radius=uniform(118),cx=x(156),cy=y(246);RectF gauge=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);arc(c,gauge,180,180,ThemePalette.stroke(theme,0xff29424e),6);
        if(valid(0))arc(c,gauge,180,180*clamp(number(0)/8000f,0,1),ThemePalette.accent(theme,0xffa4d9ec),6);
        fit(c,value(0,"%,.0f"),156,231,51,202,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.CENTER);
        text(c,"0",38,269,12,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.LEFT,false);
        text(c,"8,000",274,269,12,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.RIGHT,false);
        for(int i=0;i<20;i++){
            p.setColor(valid(0)&&number(0)/8000f*20>i?ThemePalette.accent(theme,0xffa4d9ec):ThemePalette.stroke(theme,0xff29424e));
            surface.roundRect(c,theme,rect(50+i*10.7f,280,56+i*10.7f,283),1,1,p);
        }
    }
    private void headline(Canvas c){
        line(c,293,106,293,281,ThemePalette.stroke(theme,0xff29424e),1);line(c,389,106,389,281,ThemePalette.stroke(theme,0xff29424e),1);
        text(c,"GEAR",341,115,14,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);
        text(c,valid(8)?Telemetry.gearLabel((int)samples[8].value):"—",341,227,67,ThemePalette.accent(theme,0xffa4d9ec),Paint.Align.CENTER,false);
        text(c,UiText.t("기어"),341,259,14,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);
        text(c,"SPEED",406,115,14,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.LEFT,false);
        fit(c,value(7,"%.0f"),488,227,79,171,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.CENTER);
        text(c,"km/h",488,259,15,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);
    }
    private void temperatures(Canvas c){
        int[] ids={Telemetry.AMBIENT,Telemetry.INTAKE,2,1};
        String[] labels={UiText.t("외기 온도"),UiText.t("흡기 온도"),UiText.t("냉각수 온도"),UiText.t("오일 온도")};
        for(int n=0;n<4;n++){
            float x=14+n*144;box(c,x,296,x+134,355);
            fit(c,labels[n],x+10,314,13,114,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.LEFT);
            text(c,value(ids[n],"%.1f"),x+92,343,26,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.RIGHT,false);
            text(c,"°C",x+123,343,13,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.RIGHT,false);
        }
    }
    private void pedals(Canvas c){
        int[] ids={9,10};String[] labels={UiText.t("스로틀"),UiText.t("브레이크")};
        for(int n=0;n<2;n++){
            float x=14+n*144;box(c,x,365,x+134,466);
            text(c,labels[n],x+10,386,14,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.LEFT,false);
            text(c,value(ids[n],"%.1f"),x+92,423,29,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.RIGHT,false);
            text(c,n==0?"%":"bar",x+124,423,13,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.RIGHT,false);
            p.setColor(ThemePalette.stroke(theme,0xff29424e));surface.roundRect(c,theme,rect(x+10,446,x+124,452),3,3,p);
            if(valid(ids[n])){p.setColor(ThemePalette.accent(theme,0xffa4d9ec));float f=clamp(number(ids[n])/100f,0,1);if(f>0)surface.roundRect(c,theme,rect(x+10,446,x+10+114*f,452),3,3,p);}
        }
    }
    private void forces(Canvas c){
        box(c,302,365,580,466);text(c,"G FORCE",314,385,14,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.LEFT,false);
        float cx=345,cy=424;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0xff557e91);
        circle(c,cx,cy,31);circle(c,cx,cy,19);
        p.setColor(ThemePalette.stroke(theme,0xff29424e));
        c.drawLine(x(cx)-uniform(31),y(cy),x(cx)+uniform(31),y(cy),p);
        c.drawLine(x(cx),y(cy)-uniform(31),x(cx),y(cy)+uniform(31),p);
        if(valid(11)&&valid(12)){
            p.setStyle(Paint.Style.FILL);p.setColor(ThemePalette.accent(theme,0xffa4d9ec));
            c.drawCircle(x(cx)+clamp(number(11),-1,1)*uniform(27),y(cy)-clamp(number(12),-1,1)*uniform(27),uniform(4),p);
        }
        text(c,UiText.t("종가속"),423,402,13,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);
        text(c,UiText.t("횡가속"),526,402,13,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);
        fit(c,value(12,"%+.2f"),423,432,25,84,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.CENTER);
        fit(c,value(11,"%+.2f"),526,432,25,84,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.CENTER);
        text(c,"G",423,451,12,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);text(c,"G",526,451,12,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);
    }
    private void tires(Canvas c){
        box(c,592,94,786,466);
        fit(c,UiText.t("타이어 공기압")+" · psi",689,118,14,170,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER);
        text(c,UiText.t("조향각"),689,140,12,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);
        fit(c,value(Telemetry.STEERING,"%+.1f")+" °",689,165,23,170,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.CENTER);
        float cx=689,cy=191;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(valid(Telemetry.STEERING)?ThemePalette.accent(theme,0xffa4d9ec):ThemePalette.stroke(theme,0xff29424e));circle(c,cx,cy,12);
        if(valid(Telemetry.STEERING)){
            double angle=Math.toRadians(number(Telemetry.STEERING));float dx=(float)Math.cos(angle)*uniform(10),dy=(float)Math.sin(angle)*uniform(10);
            p.setColor(ThemePalette.accent(theme,0xffa4d9ec));
            c.drawLine(x(cx)-dx,y(cy)-dy,x(cx)+dx,y(cy)+dy,p);c.drawLine(x(cx),y(cy),x(cx)-dy,y(cy)+dx,p);
        }
        line(c,606,210,772,210,ThemePalette.stroke(theme,0xff29424e),1);
        // Simple top-view silhouette, not a path prediction or an image asset.
        p.setStyle(Paint.Style.FILL);p.setColor(0xff688592);surface.roundRect(c,theme,rect(666,235,712,368),16,16,p);
        p.setColor(0xffc6dce5);surface.roundRect(c,theme,rect(671,239,707,362),13,13,p);
        p.setColor(0xff24414e);surface.roundRect(c,theme,rect(675,264,703,331),8,8,p);
        p.setColor(0xff98b6c4);c.drawRect(x(677),y(281),x(701),y(316),p);
        p.setColor(ThemePalette.background(theme,0xff0c1820));surface.roundRect(c,theme,rect(660,253,666,275),2,2,p);surface.roundRect(c,theme,rect(712,253,718,275),2,2,p);
        surface.roundRect(c,theme,rect(660,334,666,356),2,2,p);surface.roundRect(c,theme,rect(712,334,718,356),2,2,p);
        String[] labels={UiText.t("앞 왼쪽"),UiText.t("앞 오른쪽"),UiText.t("뒤 왼쪽"),UiText.t("뒤 오른쪽")};
        for(int n=0;n<4;n++){
            float x=n%2==0?624:754,y=n<2?246:342;
            fit(c,labels[n],x,y,12,60,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER);
            fit(c,value(Telemetry.TPMS_FL+n,"%.0f"),x,y+28,29,60,ThemePalette.primary(theme,0xffe4f1f6),Paint.Align.CENTER);
        }
        text(c,vehicleName(),689,427,14,ThemePalette.secondary(theme,0xff91aebb),Paint.Align.CENTER,false);
        text(c,"TPMS",689,449,12,ThemePalette.accent(theme,0xffa4d9ec),Paint.Align.CENTER,false);
    }
}
