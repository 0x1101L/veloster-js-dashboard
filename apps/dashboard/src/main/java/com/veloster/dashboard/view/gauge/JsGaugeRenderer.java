package com.veloster.dashboard.view.gauge;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.view.DashboardPage.Bounds;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSpec.SurfaceRole;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemePalette;

import com.veloster.dashboard.vehicle.js.JsGearState;

import com.veloster.dashboard.vehicle.js.JsGearState;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.Locale;

/** Draws six or twelve cards below the shared navigation. */
public final class JsGaugeRenderer {
    private final ThemeSurface surface=new ThemeSurface();
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeResources resources;
    public void setTheme(ThemeSpec value,ThemeResources assets){theme=value==null?ThemeSpec.original():value;resources=assets;}
    private final Paint p=new Paint(3);
    private void text(Canvas c,String s,float x,float y,float size,int color){
        rawText(c,s,x,y,theme.textSize(s,size,false),color);
    }
    private void rawText(Canvas c,String s,float x,float y,float size,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(resources==null?Typeface.DEFAULT:resources.fontForText(s,false));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size);c.drawText(s,x,y,p);}
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setColor(color);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
    public static float min(int i,String u){return "G".equals(u)?-2:"°C".equals(u)?-40:i==Telemetry.STEERING?-540:0;}
    public static float max(int i,String u){
        if(i>=Telemetry.TCS&&i<=Telemetry.ABS||i>=Telemetry.PD_TCS_ENABLED&&i<=Telemetry.PD_ABS_WARNING)return 1;
        if(i==Telemetry.ELSD)return 6553.5f;
        if(i==Telemetry.BAT_VOLTAGE)return 18;if(i==Telemetry.STEERING_SPEED)return 1020;
        if("psi".equals(u))return 60;
        if("rpm".equals(u))return 8000;if("km/h".equals(u))return 300;if("%".equals(u))return 100;
        if("°C".equals(u))return 160;if("G".equals(u))return 2;if("PS".equals(u)||"hp".equals(u))return 350;
        if("Nm".equals(u)||"N·m".equals(u))return 500;if("kPa".equals(u))return 250;
        if("bar".equals(u))return i==10?100:2.5f;if(i==Telemetry.STEERING)return 540;
        return i==8?6:100;
    }
    private static String axis(float value){return String.format(Locale.US,value==(int)value?"%.0f":"%.1f",value);}
    public void draw(Canvas c,Telemetry data,long now,GaugeLayout layout){
        Bounds toggle=layout.toggle();
        int toggleColor=theme.gaugeToggleFill();
        if(!surface.draw(c,theme,SurfaceRole.CONTROL_BUTTON,toggle.x,toggle.y,toggle.width,toggle.height,toggleColor,theme.stroke)){
            p.setColor(toggleColor);
            surface.roundRect(c,theme,new RectF(toggle.x,toggle.y,toggle.right(),toggle.bottom()),5,5,p);
        }
        text(c,UiText.t("게이지를 길게 눌러 항목 · 모양 · 위치 편집"),(layout.left(0)+toggle.x)/2,toggle.atY(.68f),13,theme.textSecondary);
        text(c,layout.dense()?UiText.t("3 × 2 보기"):UiText.t("4 × 3 보기"),toggle.atX(.5f),toggle.atY(.68f),14,theme.accent);
        Telemetry.Sample[] samples=data.snapshot(now);
        for(int slot=0;slot<layout.count();slot++){
            int i=layout.signal(slot),style=layout.style(slot);Telemetry.Sample s=samples[i];
            String u=Telemetry.UNITS[i];
            Bounds card=layout.card(slot);
            surface.panelWithLegacy(c,theme,p,card.x,card.y,card.width,card.height,
                ThemePalette.panel(theme,0xff101920),ThemePalette.stroke(theme,0xff293943));
            Bounds inner=card.inset(0);
            int cardSave=c.save();c.clipRect(card.x,card.y,card.right(),card.bottom());
            float scale=Math.min(inner.width/232,inner.height/170);
            if(scale<=0){c.restoreToCount(cardSave);continue;}
            c.translate(inner.atX(.5f)-126*scale,inner.atY(.5f)-95*scale);c.scale(scale,scale);
            float l=0,t=0,cx=126;
            boolean ok=s.valid&&!Float.isNaN(s.value)&&!Float.isInfinite(s.value);
            String title=UiText.t(Telemetry.LABELS[i]);
            text(c,title,cx,t+25,17,ThemePalette.accent(theme,0xffa4d9ef));
            float displayed=Telemetry.displayValue(i,s.value);
            String value=(ok||(i!=8&&s.hasDisplayValue()))?(i==8?JsGearState.valueLabel((int)s.value):(Telemetry.jsTenthSignal(i)?Telemetry.jsTenth(displayed,i==11||i==12):String.format(Locale.US,"rpm".equals(u)||"km/h".equals(u)?"%.0f":"G".equals(u)?"%+.2f":i==Telemetry.STEERING?"%+.1f":"%.1f",displayed))):"—";
            float lo=min(i,u),hi=max(i,u),f=ok?Math.max(0,Math.min(1,(displayed-lo)/(hi-lo))):0;
            if(style==0){
                float cy=t+107,r=63;
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(ThemePalette.stroke(theme,0xff293943));RectF arc=new RectF(cx-r,cy-r,cx+r,cy+r);c.drawArc(arc,150,240,false,p);
                if(ok){p.setColor(ThemePalette.accent(theme,0xffa4d9ef));c.drawArc(arc,150,240*f,false,p);}
                for(int tick=0;tick<=8;tick++){double a=Math.toRadians(150+30*tick);line(c,cx+(float)Math.cos(a)*54,cy+(float)Math.sin(a)*54,cx+(float)Math.cos(a)*60,cy+(float)Math.sin(a)*60,ThemePalette.secondary(theme,0xff8299a4),1);}
                if(ok){double a=Math.toRadians(150+240*f);line(c,cx,cy,cx+(float)Math.cos(a)*48,cy+(float)Math.sin(a)*48,ThemePalette.primary(theme,0xffe1edf2),2);p.setStyle(Paint.Style.FILL);p.setColor(ThemePalette.accent(theme,0xffa4d9ef));c.drawCircle(cx,cy,4,p);}
                text(c,axis(lo),cx-69,t+155,10,ThemePalette.secondary(theme,0xff8299a4));text(c,axis(hi),cx+69,t+155,10,ThemePalette.secondary(theme,0xff8299a4));
                text(c,value,cx,t+163,29,ThemePalette.primary(theme,0xffe1edf2));text(c,u,cx,t+181,12,ThemePalette.secondary(theme,0xff8299a4));
            }else{
                text(c,value,cx,t+103,48,ThemePalette.primary(theme,0xffe1edf2));text(c,u,cx,t+129,17,ThemePalette.secondary(theme,0xff8299a4));
                if(style==1){p.setColor(ThemePalette.stroke(theme,0xff293943));c.drawRect(l+20,t+147,l+232,t+159,p);if(ok){p.setColor(ThemePalette.accent(theme,0xffa4d9ef));c.drawRect(l+20,t+147,l+20+212*f,t+159,p);}text(c,axis(lo),l+25,t+178,11,ThemePalette.secondary(theme,0xff8299a4));text(c,axis(hi),l+226,t+178,11,ThemePalette.secondary(theme,0xff8299a4));}
            }
            c.restoreToCount(cardSave);
        }
    }
}
