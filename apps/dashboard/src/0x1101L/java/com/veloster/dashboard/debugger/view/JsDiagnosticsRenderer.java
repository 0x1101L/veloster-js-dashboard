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
    public boolean select(float downX,float downY,float x,float y){int tab=diagnosticTabAt(x,y);if(tab<0||tab!=diagnosticTabAt(downX,downY))return false;diagnosticTab=tab;return true;}
    public void draw(Canvas c,Telemetry.Sample[] samples,JsDiagState.Snapshot diagnostic,Telemetry.DctTemperatures dct,MediaState.Snapshot media){this.samples=samples;this.diagnostic=diagnostic;this.media=media;drawDiagnostics(c,dct);}
    public boolean isScreenProbeTap(float dx,float dy,float x,float y){return diagnosticTab==1&&dx>=423&&dx<=767&&x>=423&&x<=767&&dy>=338&&dy<=378&&y>=338&&y<=378;}
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
        panel(c,18,112,373,316,PANEL,LINE);panel(c,409,112,373,316,PANEL,LINE);
        if(diagnosticTab==3){drawDiagnosticWifi(c,dct);return;}
        if(diagnosticTab==4){drawBindings(c);return;}
        text(c,UiText.t("내비 수신"),32,138,16,BLUE,true);
        text(c,UiText.t("서비스 · 확인 범위"),423,138,16,BLUE,true);
        String source=UiText.t(diagnostic.routeSource==1?"초기 sticky":diagnostic.routeSource==0?"방송 수신":"미확인");
        String[] labels={"경로 방송 관찰","경로 방송값","방송 종류","정수 수신 횟수","마지막 방송 경과","잘못된 방송 횟수","방송 오류","내비 관찰","내비 상태값","정수 조회 횟수","변경 알림 횟수","조회 / 알림 경과","내비 오류"};
        String[] values={observerState(diagnostic.routeWatch),diagnostic.routeNotice==1?String.valueOf(diagnostic.routeValue):UiText.t(diagnostic.routeNotice==0?"대기 중":"미확인"),
            source,String.valueOf(diagnostic.routeCount),diagAge(diagnostic.routeAgeMs),String.valueOf(diagnostic.routeInvalidCount),diagnostic.routeDetachFailed?UiText.t("해제 실패"):diagError(diagnostic.routeError),
            observerState(diagnostic.nav),diagnostic.navValueKnown?String.valueOf(diagnostic.navValue):UiText.t("미확인"),String.valueOf(diagnostic.navQueryCount),
            String.valueOf(diagnostic.navNoticeCount),diagAge(diagnostic.navAgeMs)+" / "+diagAge(diagnostic.navNoticeAgeMs),diagnostic.navDetachFailed?UiText.t("해제 실패"):diagError(diagnostic.navError)};
        for(int i=0;i<labels.length;i++)diagRow(c,18,160+i*20,labels[i],values[i]);
        String[] checks={"클러스터 API","클러스터 서비스","TBT 메서드","MICOM 서비스","HSCAN 소켓","HUD 장착","방향 · 거리 수신","HUD 출력"};
        String[] states={observed(diagnostic.cluster,"클래스 확인","조회 실패"),serviceState(diagnostic.clusterService),observed(diagnostic.tbt,"메서드 확인","조회 실패"),
            serviceState(diagnostic.micom),diagnostic.hscan==2?UiText.t("연결 실패"):observed(diagnostic.hscan,"연결됨","연결 안 됨"),UiText.t("사용자 확인"),UiText.t("미검증"),UiText.t("미검증")};
        for(int i=0;i<checks.length;i++)diagRow(c,409,162+i*28,checks[i],states[i]);
        fitted(c,UiText.t("서비스 표시는 앱 시작 시 조회 결과"),423,399,13,344,MUTED);
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
    private String screenProbeState(String state){
        if("armed".equals(state))return UiText.t("대기 중");
        if("captured".equals(state))return UiText.t("캡처됨 · 내용 미확인");
        if("wrong_foreground".equals(state))return UiText.t("CarPlay 화면 아님");
        if("permission_denied".equals(state))return UiText.t("권한 오류");
        if("unavailable".equals(state))return UiText.t("사용 불가");
        if("error".equals(state))return UiText.t("조회 실패");
        return UiText.t("미실시");
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
        diagRow(c,409,318,"화면 시험",screenProbeState(diagnostic.screenProbe));
        button(c,UiText.t("20초 뒤 CarPlay 화면 시험"),423,338,344,40,false,BLUE);
        diagNote(c,"곡 정보는 CarPlay가 현재 소스일 때 표시합니다.",448);
        diagNote(c,"내비 입력 경로는 아직 확인되지 않았습니다.",468);
    }
    private void drawDiagnosticSignals(Canvas c){
        panel(c,18,112,764,316,PANEL,LINE);
        text(c,UiText.t("신호별 수신 · 유효성"),32,138,16,BLUE,true);
        text(c,"HSCAN: "+(diagnostic.hscan==2?UiText.t("연결 실패"):observed(diagnostic.hscan,"연결됨","연결 안 됨")),575,138,14,MUTED,false);
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
    private void drawBindings(Canvas c){
        String[] names={"bcm","nmode","speed","pgs","hscan","media","media.mode","media.0","media.1","media.2","media.3","media.4"};
        for(int i=0;i<names.length;i++){
            JsDiagState.SourceSnapshot item=diagnostic.source(names[i]);
            float x=i<6?18:409,y=155+(i%6)*43;
            diagRow(c,x,y,names[i],item.status+" / "+item.count+" / "+diagAge(item.ageMs)+(names[i].startsWith("media.")?" / "+item.processed+" / "+item.invalid:""));
        }
        fitted(c,"status / callbacks / age / processed / errors",32,414,13,736,MUTED);
    }
    private void drawDiagnosticWifi(Canvas c,Telemetry.DctTemperatures dct){
        text(c,UiText.t("Wi-Fi 관찰"),32,138,16,BLUE,true);
        text(c,UiText.t("DCT · 수신 경로"),423,138,16,BLUE,true);
        String[] labels={"Wi-Fi 기능","Wi-Fi 서비스","Wi-Fi 상태","Wi-Fi 알림","Wi-Fi 모듈"};
        String[] states={observed(diagnostic.wifiFeature,"선언됨","선언 안 됨"),observed(diagnostic.wifiService,"서비스 확인","없음/접근 불가"),
            wifiRadio(diagnostic.wifiState),observed(diagnostic.wifiWatch,"등록됨","미등록"),UiText.t("미검증")};
        for(int i=0;i<labels.length;i++)diagRow(c,18,170+i*36,labels[i],states[i]);
        diagRow(c,409,170,"DCT 온도",dctValue(dct));
        diagRow(c,409,206,"실차 수신 경로",UiText.t("미연결"));
        diagRow(c,409,242,"클러치 수명",UiText.t("미검증"));
        diagRow(c,409,278,"차량 속도",diagnosticSample(7," km/h",false));
        fitted(c,UiText.t("응답 해석만 구현 · 자동 진단 요청 없음"),423,329,13,344,MUTED);
        fitted(c,UiText.t("Wi-Fi 켜짐은 통신 모듈 검증과 별개"),32,384,13,344,MUTED);
        diagNote(c,"Wi-Fi 상태 변경을 대조하세요. DCT는 실제 수신 경로 확인이 남아 있습니다.",448);
        diagNote(c,"진단 화면의 탭 전환은 차량 요청이나 송신을 실행하지 않습니다.",468);
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
