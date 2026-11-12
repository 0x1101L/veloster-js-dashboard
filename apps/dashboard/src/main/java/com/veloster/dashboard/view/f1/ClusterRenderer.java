package com.veloster.dashboard.view.f1;

import com.veloster.dashboard.vehicle.*;

import com.veloster.dashboard.vehicle.js.JsGearState;

import com.veloster.dashboard.vehicle.js.JsGearState;

import android.graphics.*;
import com.veloster.i18n.UiText;
import java.util.Locale;

/** F1 and media pages for the three Dashboard profiles. */
public final class ClusterRenderer {
    private final boolean jsPresentation;
    private boolean mediaPage,pdProfile,jsProfile,jsnProfile,profileSupported;
    private Telemetry.Sample[] samples;
    private ShiftGuide.Cue cue;
    private MediaState.Snapshot music;
    private Bitmap artwork;
    private int shiftTarget;
    private long frameTime;

    public ClusterRenderer(boolean jsPresentation){this.jsPresentation=jsPresentation;}

    public void prepare(Telemetry model,Telemetry.Sample[] samples,long now,int target,boolean media,
                 MediaState.Snapshot track,Bitmap artwork){
        pdProfile=model.pdProfile();jsProfile=model.jsProfile();jsnProfile=model.jsnProfile();
        profileSupported=model.supportedProfile();this.samples=samples;frameTime=now;
        shiftTarget=target;cue=ShiftGuide.evaluate(samples,target,model.pdLayoutProfile());mediaPage=media;
        music=track;this.artwork=artwork;
    }

    private void temperature(Canvas c,String value,float x,float y,float width){
        if(jsPresentation)fitted(c,value,x,y,22,width,WHITE);
        else text(c,value,x,y,22,WHITE,true);
    }
    private final Paint paint=new Paint(3);
    private final Path shape=new Path();
    private static final int BG=0xff040506,PANEL=0xff101315,LINE=0xff303638,BLUE=0xffa4d9ef,WHITE=0xffe1edf2,MUTED=0xff91a5ae,RED=0xffff4059;
    private void box(Canvas c,float l,float t,float r,float b,int color){paint.setColor(color);c.drawRect(l,t,r,b,paint);}
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int edge){
        shape.reset();shape.moveTo(x,y);shape.lineTo(x+w-12,y);shape.lineTo(x+w,y+12);
        shape.lineTo(x+w,y+h);shape.lineTo(x+12,y+h);shape.lineTo(x,y+h-12);shape.close();
        paint.setColor(fill);c.drawPath(shape,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(edge);c.drawPath(shape,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(s,x,y,paint);
    }
    private void fitted(Canvas c,String s,float x,float y,float size,float width,int color){
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);
        float measured=paint.measureText(s);
        text(c,s,x,y,measured>width?size*width/measured:size,color,false);
    }
    private void ellipsis(Canvas c,String s,float x,float y,float size,float width,int color){
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);
        if(paint.measureText(s)>width){
            int end=s.length();while(end>0 && paint.measureText(s.substring(0,end)+"…")>width)end--;
            if(end>0 && Character.isHighSurrogate(s.charAt(end-1)))end--;
            s=s.substring(0,end)+"…";
        }
        text(c,s,x,y,size,color,false);
    }
    private void button(Canvas c,String s,float x,float y,float w,float h,boolean on,int accent){
        panel(c,x,y,w,h,on?0xff233b46:0xff111415,on?accent:LINE);
        if(on)box(c,x+9,y+h-3,x+w-9,y+h-1,accent);
        paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(14);float measured=paint.measureText(s);
        text(c,s,x+12,y+h/2+5,measured>w-24?14*(w-24)/measured:14,on?accent:MUTED,true);
    }
    private void centered(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setTextSize(size);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        text(c,s,x-paint.measureText(s)/2,y,size,color,bold);
    }
    private static final String[] PREFIX={"엔진 RPM","오일 온도","냉각수 온도","앞 왼쪽 휠","앞 오른쪽 휠","뒤 왼쪽 휠","뒤 오른쪽 휠","속도 km/h","현재 기어","스로틀 %","브레이크 압력 bar","횡가속","종가속","조향각","외기 온도","출력 (계산)","토크 (계산)","부스트 (계산)","앞 왼쪽 공기압","앞 오른쪽 공기압","뒤 왼쪽 공기압","뒤 오른쪽 공기압","TCS","ESC","ABS","배터리 SOC","배터리 전압","조향 속도","e-LSD 수치","흡기 온도","가속 페달 %","TCS 사용 상태","ESC 사용 상태","ABS 경고등"};
    private int index(String prefix){for(int i=0;i<PREFIX.length;i++)if(PREFIX[i].equals(prefix))return i;return -1;}
    private String value(String prefix){int i=index(prefix);if(i<0||i>=samples.length||!(samples[i].valid||(jsPresentation&&samples[i].held)))return "—";
        if(i==8)return jsPresentation?JsGearState.valueLabel((int)samples[i].value):Telemetry.gearLabel((int)samples[i].value);
        return (jsPresentation&&samples[i].held?"LAST "+samples[i].lastValidAgeMs/1000+"s ":"")+String.format(Locale.US,i==0||i==7||(i>=Telemetry.TPMS_FL&&i<=Telemetry.BAT_SOC)||i==Telemetry.STEERING_SPEED?"%.0f":i==11||i==12?"%+.2f":i==13?"%+.1f":i==17||i==Telemetry.BAT_VOLTAGE?"%.2f":"%.1f",Telemetry.displayValue(i,samples[i].value));}
    private float numeric(String prefix){int i=index(prefix);return i>=0&&i<samples.length&&samples[i].valid?Telemetry.displayValue(i,samples[i].value):0;}
    private String gearTitle(String title){return title;}
    public void drawMedia(Canvas c){
        long age=music==null?0:music.noticeAgeMs;
        float phase=Math.min(1f,Math.min(age/250f,(5000-age)/250f));
        phase=Math.max(0,phase);phase=1-(1-phase)*(1-phase);
        float top=347+133*(1-phase);
        panel(c,18,top,764,101,0xff12212a,BLUE);
        cover(c,30,top+12,76);
        String source=music==null||music.source.length()==0?"MEDIA":music.source;
        text(c,source,120,top+18,11,BLUE,true);
        String title=music==null||music.title.length()==0?"—":music.title;
        ellipsis(c,title,120,top+43,21,644,WHITE);
        String artist=music==null||music.artist.length()==0?"—":music.artist;
        ellipsis(c,artist,120,top+64,13,480,MUTED);
        if(music!=null && music.hasProgress()){
            String times=MediaState.time(music.positionMs)+" / "+MediaState.time(music.durationMs);
            text(c,times,622,top+64,12,WHITE,false);
            box(c,120,top+79,764,top+83,LINE);
            box(c,120,top+79,120+644*music.progress(),top+83,BLUE);
        }
    }
    private void cover(Canvas c,float x,float y,float size){
        box(c,x,y,x+size,y+size,0xff1c303b);
        if(artwork!=null){
            float scale=Math.min(size/artwork.getWidth(),size/artwork.getHeight());
            float w=artwork.getWidth()*scale,h=artwork.getHeight()*scale;
            c.drawBitmap(artwork,null,new RectF(x+(size-w)/2,y+(size-h)/2,x+(size+w)/2,y+(size+h)/2),paint);
        }else{
            centered(c,"♪",x+size/2,y+size*.61f,size*.35f,BLUE,true);
            if(size>100)centered(c,"ALBUM ART",x+size/2,y+size*.84f,11,MUTED,false);
        }
    }
    public void drawArtworkToggle(Canvas c){
        panel(c,18,436,52,44,mediaPage?0xff233b46:PANEL,mediaPage?BLUE:LINE);
        cover(c,24,438,40);
        text(c,music==null||music.source.length()==0?UiText.t("음악"):music.source,80,451,11,BLUE,true);
        ellipsis(c,music==null||music.title.length()==0?"♪":music.title,80,472,13,120,MUTED);
    }
    public void drawCombined(Canvas c){
        panel(c,18,60,464,388,PANEL,LINE);
        text(c,cue.shift?"SHIFT ↑":"SHIFT / LED",30,83,11,cue.shift?RED:MUTED,true);
        for(int i=0;i<15;i++){
            boolean lit=cue.shift?(frameTime/250)%2==0:i<cue.leds;
            paint.setColor(lit?(i<5?BLUE:i<10?WHITE:RED):0xff252a2c);
            c.drawCircle(36+i*30.5f,105,7,paint);
        }
        float rpm=Math.max(0,Math.min(8000,numeric("엔진 RPM")));
        box(c,30,124,470,127,LINE);if(rpm>0)box(c,30,124,30+440*rpm/8000,127,BLUE);
        box(c,180,143,316,340,BLUE);
        centered(c,gearTitle(pdProfile||jsProfile?"GEAR":"EMS GEAR"),248,164,11,BG,true);
        centered(c,value("현재 기어"),248,290,130,BG,true);
        centered(c,vehicleName(),248,324,13,BG,true);
        centered(c,"SPEED",98,177,12,MUTED,true);
        centered(c,value("속도 km/h"),98,253,57,WHITE,true);
        centered(c,"km/h",98,280,12,MUTED,false);
        centered(c,"ENGINE",398,177,12,MUTED,true);
        centered(c,value("엔진 RPM"),398,253,46,WHITE,true);
        centered(c,"rpm",398,280,12,MUTED,false);
        text(c,"OIL",32,372,11,MUTED,true);temperature(c,value("오일 온도")+" °C",116,375,135);
        text(c,"COOLANT",258,372,11,MUTED,true);temperature(c,value("냉각수 온도")+" °C",368,375,100);
        compactPedal(c,UiText.t(pdProfile||jsProfile?"가속 페달 / PEDAL":"스로틀"),pdProfile||jsProfile?"가속 페달 %":"스로틀 %","%",32,208,BLUE,100);
        compactPedal(c,UiText.t("브레이크"),"브레이크 압력 bar","bar",258,212,RED,100);
        panel(c,494,60,288,388,0xff12212a,LINE);
        cover(c,518,72,240);
        text(c,music==null||music.source.length()==0?"MEDIA":music.source,510,333,12,BLUE,true);
        ellipsis(c,music==null||music.title.length()==0?"—":music.title,510,362,21,256,WHITE);
        ellipsis(c,music==null||music.artist.length()==0?"—":music.artist,510,386,14,256,MUTED);
        if(music!=null && music.hasProgress()){
            text(c,MediaState.time(music.positionMs)+" / "+MediaState.time(music.durationMs),510,413,13,WHITE,false);
            box(c,510,428,766,432,LINE);box(c,510,428,510+256*music.progress(),432,BLUE);
        }
        button(c,"−",210,452,42,27,false,BLUE);text(c,UiText.t("목표 ")+shiftTarget+" rpm",267,471,12,BLUE,true);
        button(c,"+",440,452,42,27,false,BLUE);
        button(c,UiText.t("이전"),494,452,86,27,false,BLUE);
        button(c,music!=null&&music.playState==1?"Ⅱ":music!=null&&music.playState==0?"▶":"▶ / Ⅱ",588,452,98,27,false,BLUE);
        button(c,UiText.t("다음"),694,452,88,27,false,BLUE);
    }
    private void compactPedal(Canvas c,String label,String prefix,String unit,int x,int width,int color,float maximum){
        text(c,label,x,420,11,MUTED,true);
        fitted(c,value(prefix)+" "+unit,x+66,422,18,width-66,WHITE);
        box(c,x,432,x+width,438,LINE);
        float fraction=Math.max(0,Math.min(1,numeric(prefix)/maximum));
        if(fraction>0)box(c,x,432,x+width*fraction,438,color);
    }
    private void pedal(Canvas c,String label,String prefix,String unit,int x,int color,float maximum){
        panel(c,x,377,376,57,0xff111516,LINE);
        text(c,label,x+14,396,10,MUTED,true);
        String v=value(prefix);
        fitted(c,v,x+14,423,25,85,WHITE);
        text(c,unit,x+105,422,11,MUTED,false);
        float fraction=Math.max(0,Math.min(1,numeric(prefix)/maximum));
        for(int i=0;i<20;i++)box(c,x+148+i*10.6f,403,x+155+i*10.6f,421,fraction>i/20f?color:LINE);
    }
    public void drawCluster(Canvas c){
        float rpm=Math.max(0,Math.min(8000,numeric("엔진 RPM")));
        text(c,cue.shift?"SHIFT ↑":"SHIFT / LED",20,83,11,cue.shift?RED:MUTED,true);
        text(c,shiftTarget+" rpm",690,83,11,MUTED,true);
        for(int i=0;i<15;i++){
            int color=i<5?BLUE:i<10?0xffd0ebf8:RED;
            float x=155+i*35;
            boolean lit=cue.shift?(frameTime/250)%2==0:i<cue.leds;
            paint.setColor(lit?color:0xff252a2c);c.drawCircle(x,79,10,paint);
            if(lit){paint.setColor(0x70ffffff);c.drawCircle(x-2,76,3,paint);}
        }
        box(c,18,105,782,108,LINE);
        if(rpm>0)box(c,18,105,18+764*rpm/8000,108,BLUE);
        // Central gear is the primary cue, like a single-seat race display.
        box(c,294,120,506,367,BLUE);
        centered(c,gearTitle(pdProfile||jsProfile?"G E A R":"E M S  G E A R"),400,148,15,BG,true);
        String gear=value("현재 기어");
        centered(c,gear,400,321,184,BG,true);
        centered(c,vehicleName(),400,350,14,BG,true);
        text(c,"SPEED",22,148,14,MUTED,true);
        text(c,"km/h",229,148,12,MUTED,false);
        centered(c,value("속도 km/h"),150,253,88,WHITE,true);
        box(c,18,276,276,278,LINE);
        text(c,"OIL TEMP",22,307,11,MUTED,true);
        temperature(c,value("오일 온도"),175,310,72);
        text(c,"°C",250,310,11,MUTED,false);
        text(c,"ENGINE",526,148,14,MUTED,true);
        text(c,"rpm",749,148,12,MUTED,false);
        centered(c,value("엔진 RPM"),653,247,69,WHITE,true);
        box(c,524,276,782,278,LINE);
        text(c,"COOLANT",528,307,11,MUTED,true);
        temperature(c,value("냉각수 온도"),685,310,72);
        text(c,"°C",760,310,11,MUTED,false);
        pedal(c,pdProfile||jsProfile?"PEDAL":"THROTTLE",pdProfile||jsProfile?"가속 페달 %":"스로틀 %","%",18,BLUE,100);
        pedal(c,"BRAKE PRESSURE", "브레이크 압력 bar", "bar",406,RED,100);
        button(c,"−",548,452,42,27,false,BLUE);
        text(c,UiText.t("목표 ")+shiftTarget+" rpm",604,471,12,BLUE,true);
        button(c,"+",740,452,42,27,false,BLUE);
    }
    private String vehicleName(){return pdProfile?"i30 N Line":jsProfile?"Veloster (WIP)":jsnProfile?"VELOSTER N":profileSupported?"Dashboard":"UNSUPPORTED";}

}
