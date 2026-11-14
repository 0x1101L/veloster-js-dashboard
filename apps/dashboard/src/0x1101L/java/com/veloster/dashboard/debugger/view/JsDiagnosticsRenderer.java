package com.veloster.dashboard.debugger.view;

import com.veloster.dashboard.debugger.JsDiagState;

import com.veloster.dashboard.vehicle.*;

import com.veloster.dashboard.vehicle.js.JsGearState;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import com.veloster.i18n.UiText;
import java.util.Locale;

/** Diagnostics page, sharing the telemetry snapshot without opening subscriptions. */
public final class JsDiagnosticsRenderer {
    private final Paint paint=new Paint(3);
    private final Path shape=new Path();
    private static final int PANEL=0xff101315,LINE=0xff303638,BLUE=0xffa4d9ef,WHITE=0xffe1edf2,MUTED=0xff91a5ae,RED=0xffff4059,ECO_GREEN=0xff70d991;
    private int diagnosticTab;
    private JsDiagState.Snapshot diagnostic;
    private Telemetry.Sample[] samples;
    private MediaState.Snapshot media;
    public boolean transmissionHit(float x,float y){return diagnosticTab==3&&x>=640&&x<=770&&y>=118&&y<=154;}
    public boolean select(float downX,float downY,float x,float y){int tab=diagnosticTabAt(x,y);if(tab<0||tab!=diagnosticTabAt(downX,downY))return false;diagnosticTab=tab;return true;}
    public void draw(Canvas c,Telemetry.Sample[] samples,JsDiagState.Snapshot diagnostic,Telemetry.DctTemperatures dct,MediaState.Snapshot media){this.samples=samples;this.diagnostic=diagnostic;this.media=media;drawDiagnostics(c,dct);}
    private static int diagnosticTabAt(float x,float y){
        if(y<62||y>100)return -1;
        for(int i=0;i<5;i++){float left=18+i*154;if(x>=left&&x<=left+148)return i;}
        return -1;
    }
    private void box(Canvas c,float l,float t,float r,float b,int color){paint.setColor(color);c.drawRect(l,t,r,b,paint);}
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int edge){
        shape.reset();shape.moveTo(x,y);shape.lineTo(x+w-12,y);shape.lineTo(x+w,y+12);
        shape.lineTo(x+w,y+h);shape.lineTo(x+12,y+h);shape.lineTo(x,y+h-12);shape.close();
        paint.setColor(fill);c.drawPath(shape,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(edge);c.drawPath(shape,paint);paint.setStyle(Paint.Style.FILL);
    }
    private String observed(int state,String yes,String no){return UiText.t(state==1?yes:state==0?no:"미확인");}
    private String wifiRadio(int state){
        switch(state){case 0:return UiText.t("꺼지는 중");case 1:return UiText.t("꺼짐");case 2:return UiText.t("켜지는 중");
            case 3:return UiText.t("켜짐");case -2:return UiText.t("권한 오류");default:return UiText.t("미확인");}
    }
    private String diagnosticSample(int index,String unit,boolean candidate){
        Telemetry.Sample s=samples[index];
        if(!s.valid&&!s.held)return "—";
        String prefix=(s.held?"LAST "+(s.lastValidAgeMs/1000)+"s ":"")+(candidate?UiText.t("후보")+" ":"");
        return prefix+String.format(Locale.US,index==Telemetry.BAT_VOLTAGE?"%.2f":"%.0f",s.value)+unit;
    }
    private String dctValue(Telemetry.DctTemperatures dct){
        if(Float.isNaN(dct.oddC)&&Float.isNaN(dct.evenC))return UiText.t("미수신");
        String left=Float.isNaN(dct.oddC)?"—":String.format(Locale.US,"%.0f",dct.oddC);
        String right=Float.isNaN(dct.evenC)?"—":String.format(Locale.US,"%.0f",dct.evenC);
        return UiText.t("후보")+" "+left+(dct.single?"":" / "+right)+" °C";
    }
    private void diagRow(Canvas c,float x,float y,String label,String value){
        fitted(c,UiText.t(label),x+14,y,14,150,MUTED);
        fitted(c,value,x+165,y,15,190,WHITE);
    }
    private String diagAge(long age){
        if(age<0||age==Long.MAX_VALUE)return "—";
        return age<10000?String.format(Locale.US,"%.1fs",age/1000f):age/1000+"s";
    }
    private String diagError(int error){return UiText.t(error==1?"권한 오류":error==2?"형식 오류":error==3?"조회 실패":"오류 없음");}
    private String observerState(int state){return state==2?UiText.t("조회 실패"):observed(state,"등록됨","미등록");}
    private String serviceState(int state){return state==2?UiText.t("조회 실패"):observed(state,"Binder 연결됨","없음/접근 불가");}
    private String sampleState(Telemetry.Sample sample){
        return UiText.t(!sample.seen?"미수신":sample.stale?"만료":sample.held?"보관값":sample.valid?"유효":"무효 수신");
    }
    private void diagNote(Canvas c,String key,float y){fitted(c,UiText.t(key),32,y,13,736,MUTED);}
    private void drawDiagnostics(Canvas c,Telemetry.DctTemperatures dct){
        button(c,UiText.t("HUD · 내비"),18,62,148,38,diagnosticTab==0,BLUE);
        button(c,"CarPlay",172,62,148,38,diagnosticTab==1,BLUE);
        button(c,UiText.t("차량 신호"),326,62,148,38,diagnosticTab==2,BLUE);
        button(c,"Wi-Fi · DCT",480,62,148,38,diagnosticTab==3,BLUE);
        button(c,"Bindings",634,62,148,38,diagnosticTab==4,BLUE);
        if(diagnosticTab==1){drawCarPlay(c);return;}
        if(diagnosticTab==2){drawDiagnosticSignals(c);return;}
        if(diagnosticTab==3){drawDiagnosticWifi(c,dct);return;}
        panel(c,18,112,373,316,PANEL,LINE);panel(c,409,112,373,316,PANEL,LINE);
        if(diagnosticTab==4){drawBindings(c);return;}
        text(c,UiText.t("내비 수신"),32,138,16,BLUE,true);
        text(c,UiText.t("HUD 정보"),423,138,16,BLUE,true);
        String source=UiText.t(diagnostic.routeSource==1?"초기 sticky":diagnostic.routeSource==0?"방송 수신":"미확인");
        String[] labels={"경로 방송 관찰","경로 방송값","방송 종류","정수 수신 횟수","마지막 방송 경과","잘못된 방송 횟수","방송 오류","내비 관찰","내비 상태값","정수 조회 횟수","변경 알림 횟수","조회 / 알림 경과","내비 오류"};
        String[] values={observerState(diagnostic.routeWatch),diagnostic.routeNotice==1?String.valueOf(diagnostic.routeValue):UiText.t(diagnostic.routeNotice==0?"대기 중":"미확인"),
            source,String.valueOf(diagnostic.routeCount),diagAge(diagnostic.routeAgeMs),String.valueOf(diagnostic.routeInvalidCount),diagnostic.routeDetachFailed?UiText.t("해제 실패"):diagError(diagnostic.routeError),
            observerState(diagnostic.nav),diagnostic.navValueKnown?String.valueOf(diagnostic.navValue):UiText.t("미확인"),String.valueOf(diagnostic.navQueryCount),
            String.valueOf(diagnostic.navNoticeCount),diagAge(diagnostic.navAgeMs)+" / "+diagAge(diagnostic.navNoticeAgeMs),diagnostic.navDetachFailed?UiText.t("해제 실패"):diagError(diagnostic.navError)};
        for(int i=0;i<labels.length;i++)diagRow(c,18,160+i*20,labels[i],values[i]);
        String[] checks={"HUD 장착","방향 · 거리 수신","HUD 출력"};
        String[] states={UiText.t("사용자 확인"),UiText.t("미검증"),UiText.t("미검증")};
        for(int i=0;i<checks.length;i++)diagRow(c,409,170+i*36,checks[i],states[i]);
        diagNote(c,"경로 변경 전후의 정수값과 수신 횟수를 대조하세요.",448);
        diagNote(c,"경과는 앱 수신 이후 시간입니다. sticky 원 발신 시각은 미확인입니다.",468);
    }
    private String mediaObserverState(String status){
        if("registered".equals(status))return UiText.t("등록됨");
        if("stopped".equals(status))return UiText.t("미등록");
        if("permission denied".equals(status))return UiText.t("권한 오류");
        if("error".equals(status))return UiText.t("조회 실패");
        return UiText.t("미확인");
    }
    private void drawCarPlay(Canvas c){
        panel(c,18,112,373,316,PANEL,LINE);panel(c,409,112,373,316,PANEL,LINE);
        text(c,UiText.t("CarPlay 미디어"),32,138,16,BLUE,true);
        text(c,UiText.t("CarPlay 내비게이션"),423,138,16,BLUE,true);
        JsDiagState.SourceSnapshot source=diagnostic.source("media.3");
        boolean active=media!=null&&"CarPlay".equals(media.source);
        diagRow(c,18,170,"미디어 관찰",mediaObserverState(source.status));
        diagRow(c,18,202,"방송 수신 횟수",String.valueOf(source.count));
        diagRow(c,18,234,"마지막 방송 경과",diagAge(source.ageMs));
        diagRow(c,18,266,"곡명",active&&media.title.length()>0?media.title:"—");
        diagRow(c,18,298,"가수",active&&media.artist.length()>0?media.artist:"—");
        diagRow(c,18,330,"재생 시간",active&&media.positionMs>=0?diagAge(media.positionMs):"—");
        diagRow(c,18,362,"전체 시간",active&&media.durationMs>=0?diagAge(media.durationMs):"—");
        diagRow(c,409,170,"내비 입력",UiText.t("미연결"));
        diagRow(c,409,202,"방향 · 거리 수신",UiText.t("미수신"));
        diagRow(c,409,234,"카메라 정보",UiText.t("미수신"));
        fitted(c,UiText.t("미디어 방송과 내비 입력은 별도입니다."),423,285,13,344,MUTED);
        diagNote(c,"곡 정보는 CarPlay가 현재 소스일 때 표시합니다.",448);
        diagNote(c,"내비 입력 경로는 아직 확인되지 않았습니다.",468);
    }
    private void drawDiagnosticSignals(Canvas c){
        panel(c,18,112,764,316,PANEL,LINE);
        text(c,UiText.t("신호별 수신 · 유효성"),32,138,16,BLUE,true);
        String[] headers={"신호","상태","현재 / 보관값","수신 횟수","수신 / 유효 경과"};
        float[] positions={32,190,302,510,610};
        for(int i=0;i<headers.length;i++)text(c,UiText.t(headers[i]),positions[i],163,13,MUTED,false);
        int[] indices={Telemetry.BAT_VOLTAGE,Telemetry.BAT_SOC,Telemetry.TPMS_FL,Telemetry.TPMS_FR,Telemetry.TPMS_RL,Telemetry.TPMS_RR,1,10,11,12};
        for(int row=0;row<indices.length;row++){
            int i=indices[row];Telemetry.Sample s=samples[i];float y=187+row*25;
            fitted(c,UiText.t(Telemetry.LABELS[i]),32,y,14,150,WHITE);
            fitted(c,sampleState(s),190,y,14,102,!s.seen?MUTED:s.valid?ECO_GREEN:s.held?BLUE:RED);
            String reading="—";
            if(s.valid||s.held)reading=String.format(Locale.US,i==Telemetry.BAT_VOLTAGE?"%.2f":i==11||i==12?"%+.2f":"%.1f",s.value)+" "+Telemetry.UNITS[i];
            fitted(c,reading,302,y,15,198,WHITE);
            fitted(c,String.valueOf(s.receivedCount),510,y,14,90,WHITE);
            fitted(c,diagAge(s.seen?s.ageMs:-1)+" / "+diagAge(s.lastValidAgeMs),610,y,14,156,MUTED);
        }
        diagNote(c,"유효·무효는 디코더 판정입니다. 배터리 전압·가속도는 실차 대조가 필요합니다.",448);
        diagNote(c,"신호 횟수는 시동 경계에서 초기화됩니다. 보관값은 현재 실측값이 아닙니다.",468);
    }
    private String connectionState(String status){
        if("registered".equals(status)||"connected".equals(status))return UiText.t("연결됨");
        if("unknown".equals(status)||"connecting".equals(status))return "—";
        return UiText.t("연결 안 됨");
    }
    private String serviceAvailability(int state){
        return state<0?"—":UiText.t(state==1?"연결 상태 미확인":"연결 안 됨");
    }
    private void drawBindings(Canvas c){
        text(c,UiText.t("차량 연결"),32,138,16,BLUE,true);
        text(c,UiText.t("서비스"),423,138,16,BLUE,true);
        String[] keys={"bcm","nmode","speed","pgs","hscan","media"};
        String[] names={"BCM","N Mode","속도","후방 카메라","HSCAN","미디어"};
        for(int i=0;i<keys.length;i++)diagRow(c,18,170+i*36,names[i],connectionState(diagnostic.source(keys[i]).status));
        diagRow(c,409,170,"DCT 서비스",connectionState(diagnostic.dctService));
        diagRow(c,409,206,"Wi-Fi 서비스",serviceAvailability(diagnostic.wifiService));
        diagRow(c,409,242,"클러스터 서비스",serviceAvailability(diagnostic.clusterService));
        diagRow(c,409,278,"MICOM 서비스",serviceAvailability(diagnostic.micom));
    }
    private void drawDiagnosticWifi(Canvas c,Telemetry.DctTemperatures dct){
        panel(c,18,112,373,316,PANEL,LINE);
        panel(c,409,112,373,150,PANEL,LINE);
        panel(c,409,272,373,156,PANEL,LINE);
        text(c,UiText.t("Wi-Fi 관찰"),32,138,16,BLUE,true);
        text(c,UiText.t("변속기 정보"),423,138,16,BLUE,true);
        button(c,UiText.t("변속기 설정"),640,118,130,36,false,BLUE);
        String[] labels={"Wi-Fi 기능","Wi-Fi 상태","Wi-Fi 알림","Wi-Fi 모듈"};
        String[] states={observed(diagnostic.wifiFeature,"선언됨","선언 안 됨"),wifiRadio(diagnostic.wifiState),observed(diagnostic.wifiWatch,"등록됨","미등록"),UiText.t("미검증")};
        for(int i=0;i<labels.length;i++)diagRow(c,18,170+i*36,labels[i],states[i]);
        diagRow(c,409,170,"변속기 종류",dct.transmissionSetting==1?"DCT":dct.transmissionSetting==2?"MT":dct.dctEstimate?"DCT (test)":"MT (test)");
        diagRow(c,409,201,"DCT 온도",dctValue(dct));
        diagRow(c,409,232,"클러치 수명",UiText.t("미검증"));
        text(c,UiText.t("차량 정보"),423,297,16,BLUE,true);
        diagRow(c,409,357,"차량 속도",diagnosticSample(7," km/h",false));
        fitted(c,UiText.t("Wi-Fi 켜짐은 통신 모듈 검증과 별개"),32,384,13,344,MUTED);

    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(s,x,y,paint);
    }
    private void fitted(Canvas c,String s,float x,float y,float size,float width,int color){
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);
        float measured=paint.measureText(s);
        text(c,s,x,y,measured>width?size*width/measured:size,color,false);
    }
    private void button(Canvas c,String s,float x,float y,float w,float h,boolean on,int accent){
        panel(c,x,y,w,h,on?0xff233b46:0xff111415,on?accent:LINE);
        if(on)box(c,x+9,y+h-3,x+w-9,y+h-1,accent);
        paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(14);float measured=paint.measureText(s);
        text(c,s,x+12,y+h/2+5,measured>w-24?14*(w-24)/measured:14,on?accent:MUTED,true);
    }
}
