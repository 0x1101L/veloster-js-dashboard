package com.veloster.dashboard.view.overview;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.view.gauge.GaugeRenderer;
import com.veloster.dashboard.vehicle.js.JsGearState;

import android.graphics.*;
import com.veloster.i18n.UiText;
import java.util.Locale;

/** Overview page for PD and JSN Dashboard profiles. */
public final class OverviewRenderer {
    private Telemetry.Sample[] samples;
    private OverviewLayout overviewLayout;
    private boolean pdProfile,jsProfile;
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
    private void stroke(Canvas c,float x,float y,float x2,float y2,int color,float width){
        paint.setColor(color);paint.setStrokeWidth(width);c.drawLine(x,y,x2,y2,paint);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(s,x,y,paint);
    }
    private void fitted(Canvas c,String s,float x,float y,float size,float width,int color){
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);
        float measured=paint.measureText(s);
        text(c,s,x,y,measured>width?size*width/measured:size,color,false);
    }
    private static final String[] PREFIX={"엔진 RPM","오일 온도","냉각수 온도","앞 왼쪽 휠","앞 오른쪽 휠","뒤 왼쪽 휠","뒤 오른쪽 휠","속도 km/h","현재 기어","스로틀 %","브레이크 압력 bar","횡가속","종가속","조향각","외기 온도","출력 (계산)","토크 (계산)","부스트 (계산)","앞 왼쪽 공기압","앞 오른쪽 공기압","뒤 왼쪽 공기압","뒤 오른쪽 공기압","TCS","ESC","ABS","배터리 SOC","배터리 전압","조향 속도","e-LSD 수치","흡기 온도","가속 페달 %","TCS 사용 상태","ESC 사용 상태","ABS 경고등"};
    private int index(String prefix){for(int i=0;i<PREFIX.length;i++)if(PREFIX[i].equals(prefix))return i;return -1;}
    private String value(String prefix){int i=index(prefix);if(i<0||i>=samples.length||!samples[i].valid)return "—";
        if(i==8)return Telemetry.gearLabel((int)samples[i].value);
        return String.format(Locale.US,i==0||i==7||(i>=Telemetry.TPMS_FL&&i<=Telemetry.BAT_SOC)||i==Telemetry.STEERING_SPEED?"%.0f":i==11||i==12?"%+.2f":i==13?"%+.1f":i==17||i==Telemetry.BAT_VOLTAGE?"%.2f":"%.1f",Telemetry.displayValue(i,samples[i].value));}
    public void draw(Canvas c,Telemetry.Sample[] samples,OverviewLayout layout,boolean pd,boolean js){
        this.samples=samples;overviewLayout=layout;pdProfile=pd;jsProfile=js;
        String[] names={"SPEED / km/h","ENGINE / rpm",pdProfile||jsProfile?"GEAR":"EMS GEAR",pdProfile||jsProfile?"ACCEL / %":"THROTTLE / %","BRAKE / bar"};
        for(int slot=0;slot<overviewLayout.count();slot++){
            int i=overviewLayout.signalAt(slot);boolean head=overviewLayout.headline(i);
            if(overviewLayout.grouped(i))groupCard(c,i);else overviewCard(c,i,head?names[slot]:UiText.t(Telemetry.LABELS[i]));
        }
    }
    private void groupCard(Canvas c,int i){
        float x=overviewLayout.left(i),y=overviewLayout.top(i),w=overviewLayout.width(i);
        panel(c,x,y,w,overviewLayout.height(i),PANEL,LINE);box(c,x+10,y,x+53,y+2,BLUE);
        if(i==Telemetry.TPMS_FL){
            fitted(c,UiText.t("타이어 공기압"),x+10,y+20,13,w-20,MUTED);
            String[] corners={"FL","FR","RL","RR"};
            for(int n=0;n<4;n++){
                float l=x+10+(n%2)*69,t=y+44+(n/2)*25;
                text(c,corners[n],l,t,10,MUTED,false);
                text(c,value(PREFIX[i+n]),l+20,t,20,BLUE,true);
            }
            text(c,"psi",x+10,y+88,11,MUTED,false);
        }else if(i==Telemetry.PD_TCS_ENABLED){
            fitted(c,UiText.t("TCS · ESC · ABS 상태"),x+10,y+20,13,w-20,MUTED);
            for(int n=0;n<3;n++){float t=y+43+n*22;Telemetry.Sample s=samples[i+n];boolean ok=s.valid&&(s.value==0||s.value==1);
                String label=n==0?"TCS":n==1?"ESC":UiText.t("ABS 경고등");fitted(c,label,x+10,t,12,w-66,MUTED);
                String state=!ok?"—":s.value==1?"ON":"OFF";boolean warning=ok&&(n<2?s.value==0:s.value==1);
                fitted(c,state,x+w-43,t,16,33,warning?RED:BLUE);}
        }else{
            fitted(c,"TCS · ESC · ABS",x+10,y+20,13,w-20,MUTED);
            for(int n=0;n<3;n++){
                float t=y+43+n*22;Telemetry.Sample s=samples[i+n];
                text(c,UiText.t(Telemetry.LABELS[i+n]),x+10,t,12,MUTED,false);
                text(c,s.valid?(s.value==1?UiText.t("작동"):UiText.t("비작동")):"—",x+66,t,16,s.valid&&s.value==1?RED:BLUE,true);
            }
        }
    }
    private void overviewCard(Canvas c,int i,String title){
        float x=overviewLayout.left(i),y=overviewLayout.top(i),w=overviewLayout.width(i),h=overviewLayout.height(i);
        boolean head=overviewLayout.headline(i),ok=samples[i].valid&&!Float.isNaN(samples[i].value)&&!Float.isInfinite(samples[i].value);
        int style=overviewLayout.style(i),color=samples[i].changed?0xffe9f7ff:BLUE;
        panel(c,x,y,w,h,PANEL,LINE);box(c,x+10,y,x+53,y+2,head&&i==0?RED:BLUE);
        fitted(c,title,x+10,y+(head?18:22),head?11:13,w-20,MUTED);
        String number=ok?value(PREFIX[i]):"—";
        float lo=GaugeRenderer.min(i,Telemetry.UNITS[i]),hi=GaugeRenderer.max(i,Telemetry.UNITS[i]);
        // Unsupported gears keep the existing dash and have no gauge position.
        boolean position=ok&&(i!=8||(samples[i].value>=1&&samples[i].value<=6));
        float f=position?Math.max(0,Math.min(1,(Telemetry.displayValue(i,samples[i].value)-lo)/(hi-lo))):0;
        if(style==2){
            fitted(c,number,x+10,y+(head?61:63),head?(i==0?35:38):32,w-20,color);
        }else if(style==1){
            fitted(c,number,x+10,y+(head?48:54),head?28:29,w-20,color);
            float t=y+(head?59:64),b=t+7;
            box(c,x+10,t,x+w-10,b,LINE);if(position)box(c,x+10,t,x+10+(w-20)*f,b,BLUE);
            if(lo<0){float zero=x+10+(w-20)*(-lo)/(hi-lo);stroke(c,zero,t-2,zero,b+2,MUTED,1);}
        }else{
            float cx=x+32,cy=y+(head?46:56),radius=head?17:23;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(head?3:4);paint.setColor(LINE);
            RectF arc=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);c.drawArc(arc,150,240,false,paint);
            if(position){paint.setColor(BLUE);c.drawArc(arc,150,240*f,false,paint);}
            paint.setStyle(Paint.Style.FILL);
            if(position){double angle=Math.toRadians(150+240*f);stroke(c,cx,cy,cx+(float)Math.cos(angle)*(radius-6),cy+(float)Math.sin(angle)*(radius-6),WHITE,1.5f);paint.setColor(BLUE);c.drawCircle(cx,cy,2,paint);}
            fitted(c,number,x+58,y+(head?54:66),head?26:28,w-68,color);
        }
        if(!head)text(c,Telemetry.UNITS[i],x+10,y+85,11,MUTED,false);
    }
}
