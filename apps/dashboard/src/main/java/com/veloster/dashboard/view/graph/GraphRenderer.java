package com.veloster.dashboard.view.graph;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemePalette;
import com.veloster.dashboard.view.gauge.GaugeRenderer;
import com.veloster.dashboard.vehicle.js.JsGearState;

import com.veloster.dashboard.vehicle.js.JsGearState;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.Locale;

/** Actual receipt history; missing and invalid intervals are never filled with held values. */
public final class GraphRenderer {
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeResources resources;
    public void setTheme(ThemeSpec value,ThemeResources assets){theme=value==null?ThemeSpec.original():value;resources=assets;}
    private final Paint p=new Paint(3);
    private final Path path=new Path();
    private void text(Canvas c,String s,float x,float y,float size,int color){p.setStyle(Paint.Style.FILL);p.setTypeface(resources==null?Typeface.DEFAULT:resources.fontForText(s,false));p.setTextAlign(Paint.Align.LEFT);p.setTextSize(size);p.setColor(color);c.drawText(s,x,y,p);}
    private void line(Canvas c,float x,float y,float xx,float yy,int color){p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(1);c.drawLine(x,y,xx,yy,p);p.setStyle(Paint.Style.FILL);}
    private static String number(int i,float value){return String.format(Locale.US,i==0||i==7||i>=3&&i<=6||i==8?"%.0f":i==11||i==12||i==17?"%+.2f":i==13?"%+.1f":"%.1f",value);}
    private static float span(int signal){return signal==0?100:signal==11||signal==12?0.1f:signal==17?0.1f:signal==13?10:2;}
    private static float displayValue(int signal,float value,boolean jsGear){
        return jsGear?JsGearState.displayNumber((int)value):Telemetry.displayValue(signal,value);
    }
    public void draw(Canvas c,Telemetry model,long now,GraphLayout layout){
        text(c,UiText.t("그래프를 길게 눌러 항목 변경"),22,72,13,ThemePalette.secondary(theme,0xff8299a4));
        for(int i=0;i<2;i++){
            float x=628+80*i;boolean selected=layout.window()==(i==0?60000:300000);
            p.setStyle(Paint.Style.FILL);p.setColor(selected?(theme.isOriginal()?0xff294653:theme.panelRaised):(theme.isOriginal()?0xff142129:theme.panel));if(theme.cutPanels)c.drawRoundRect(new RectF(x,54,x+74,79),5,5,p);else c.drawRect(x,54,x+74,79,p);
            text(c,i==0?UiText.t("1분"):UiText.t("5분"),x+24,72,14,selected?ThemePalette.accent(theme,0xffa4d9ef):ThemePalette.secondary(theme,0xff8299a4));
        }
        for(int slot=0;slot<GraphLayout.SLOTS;slot++)drawSeries(c,model,now,layout,slot);
    }
    private void drawSeries(Canvas c,Telemetry model,long now,GraphLayout layout,int slot){
        int signal=layout.signal(slot);float top=GraphLayout.top(slot);
        p.setStyle(Paint.Style.FILL);p.setColor(theme.isOriginal()?0xff101920:theme.panel);if(theme.cutPanels)c.drawRoundRect(new RectF(18,top,782,top+124),7,7,p);else c.drawRect(18,top,782,top+124,p);
        Telemetry.Sample current=model.sample(signal,now);
        boolean jsGear=signal==8&&model.jsProfile();
        String title=UiText.t(Telemetry.LABELS[signal]);
        text(c,title,30,top+20,15,ThemePalette.accent(theme,0xffa4d9ef));
        String value=(current.valid||(model.jsProfile()&&signal!=8&&current.hasDisplayValue()))?(jsGear?Telemetry.gearLabel((int)current.value):(model.jsProfile()&&Telemetry.jsTenthSignal(signal)?Telemetry.jsTenth(current.value,true):number(signal,displayValue(signal,current.value,jsGear)))):"—";
        text(c,value+" "+Telemetry.UNITS[signal],622,top+20,16,ThemePalette.primary(theme,0xffe1edf2));
        Telemetry.Series series=model.history(signal,now,layout.window());
        float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
        for(int i=0;i<series.size();i++)if(series.valid[i]){float v=displayValue(signal,series.values[i],jsGear);lo=Math.min(lo,v);hi=Math.max(hi,v);}
        if(lo==Float.POSITIVE_INFINITY){lo=GaugeRenderer.min(signal,Telemetry.UNITS[signal]);hi=GaugeRenderer.max(signal,Telemetry.UNITS[signal]);}
        else{if(signal==Telemetry.STEERING||signal==11||signal==12){lo=Math.min(lo,0);hi=Math.max(hi,0);}float pad=Math.max(span(signal)/2,(hi-lo)*0.12f);lo-=pad;hi+=pad;}
        final float left=82,right=766,plotTop=top+30,bottom=top+103;
        for(int row=0;row<=2;row++){float y=plotTop+(bottom-plotTop)*row/2;line(c,left,y,right,y,ThemePalette.stroke(theme,0xff293943));text(c,model.jsProfile()&&Telemetry.jsTenthSignal(signal)?Telemetry.jsTenth(hi-(hi-lo)*row/2,true):number(signal,hi-(hi-lo)*row/2),27,y+4,10,ThemePalette.secondary(theme,0xff8299a4));}
        text(c,layout.window()==60000?UiText.t("−1분"):UiText.t("−5분"),left,top+117,10,ThemePalette.secondary(theme,0xff8299a4));text(c,layout.window()==60000?UiText.t("−30초"):UiText.t("−2분30초"),392,top+117,10,ThemePalette.secondary(theme,0xff8299a4));text(c,UiText.t("지금"),740,top+117,10,ThemePalette.secondary(theme,0xff8299a4));
        if(lo<0&&hi>0)line(c,left,bottom-(0-lo)/(hi-lo)*(bottom-plotTop),right,bottom-(0-lo)/(hi-lo)*(bottom-plotTop),0xff49616c);
        path.reset();boolean connected=false;long previous=0;
        int saved=c.save();c.clipRect(left,plotTop,right,bottom);
        for(int i=0;i<series.size();i++){
            if(!series.valid[i]){connected=false;continue;}
            float v=displayValue(signal,series.values[i],jsGear);
            float x=right-(now-series.times[i])*(right-left)/layout.window(),y=bottom-(v-lo)/(hi-lo)*(bottom-plotTop);
            if(!connected||series.breakBefore[i]||series.times[i]-previous>2000)path.moveTo(x,y);else path.lineTo(x,y);
            connected=true;previous=series.times[i];p.setStyle(Paint.Style.FILL);p.setColor(ThemePalette.accent(theme,0xffa4d9ef));c.drawCircle(x,y,1.4f,p);
        }
        p.setStyle(Paint.Style.STROKE);p.setColor(ThemePalette.accent(theme,0xffa4d9ef));p.setStrokeWidth(2);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);c.restoreToCount(saved);
    }
}
