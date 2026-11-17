package com.veloster.dashboard.debugger.view;

import com.veloster.dashboard.debugger.JsDiagState;
import com.veloster.dashboard.debugger.BluetoothRuntimeProbe;

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
    private String carPlayRawStatus="내부 저장 캡처 없음";
    private String carPlayLibraryStatus="미확인";
    private String carPlayProcessStatus="미확인";
    private BluetoothRuntimeProbe.Snapshot bluetoothStatus=BluetoothRuntimeProbe.parse("");
    public boolean wifiSettingsHit(float x,float y){return diagnosticTab==3&&x>=244&&x<=379&&y>=118&&y<=154;}
    public boolean transmissionHit(float x,float y){return diagnosticTab==2&&x>=642&&x<=770&&y>=154&&y<=190;}
    public boolean select(float downX,float downY,float x,float y){
        if(downY>=62&&downY<=100&&y>=62&&y<=100){int tab=diagnosticTabAt(x);if(tab<0||tab!=diagnosticTabAt(downX))return false;diagnosticTab=tab;return true;}
        return false;
    }
    public void draw(Canvas c,Telemetry.Sample[] samples,JsDiagState.Snapshot diagnostic,Telemetry.DctTemperatures dct,MediaState.Snapshot media,String rawStatus,String libraryStatus,String processStatus,BluetoothRuntimeProbe.Snapshot bluetoothStatus){this.samples=samples;this.diagnostic=diagnostic;this.media=media;this.carPlayRawStatus=rawStatus;this.carPlayLibraryStatus=libraryStatus;this.carPlayProcessStatus=processStatus;if(bluetoothStatus!=null)this.bluetoothStatus=bluetoothStatus;drawDiagnostics(c,dct);}
    public boolean carPlayImportHit(float x,float y){return diagnosticTab==1&&x>=425&&x<=770&&y>=310&&y<=354;}
    private static int diagnosticTabAt(float x){
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
        button(c,UiText.t("차량"),326,62,148,38,diagnosticTab==2,BLUE);
        button(c,UiText.t("연결"),480,62,148,38,diagnosticTab==3,BLUE);
        button(c,"Bindings",634,62,148,38,diagnosticTab==4,BLUE);
        if(diagnosticTab==1){drawCarPlay(c);return;}
        if(diagnosticTab==2){drawVehicleDiagnostics(c,dct);return;}
        if(diagnosticTab==3){drawConnectivityDiagnostics(c);return;}
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
        text(c,UiText.t("iAP2 연결 진단"),423,138,16,BLUE,true);
        JsDiagState.SourceSnapshot source=diagnostic.source("media.3");
        boolean active=media!=null&&"CarPlay".equals(media.source);
        diagRow(c,18,170,"미디어 관찰",mediaObserverState(source.status));
        diagRow(c,18,202,"방송 수신 횟수",String.valueOf(source.count));
        diagRow(c,18,234,"마지막 방송 경과",diagAge(source.ageMs));
        diagRow(c,18,266,"곡명",active&&media.title.length()>0?media.title:"—");
        diagRow(c,18,298,"가수",active&&media.artist.length()>0?media.artist:"—");
        diagRow(c,18,330,"재생 시간",active&&media.positionMs>=0?diagAge(media.positionMs):"—");
        diagRow(c,18,362,"전체 시간",active&&media.durationMs>=0?diagAge(media.durationMs):"—");
        diagRow(c,409,170,"libiap2.so 파일",UiText.t(carPlayLibraryStatus));
        diagRow(c,409,202,"프로세스 로드",UiText.t(carPlayProcessStatus));
        diagRow(c,409,234,"Frida 후킹",UiText.t("앱 연동 없음 · 미확인"));
        diagRow(c,409,266,"가져온 원본",UiText.t(carPlayRawStatus));
        button(c,UiText.t("캡처 파일 가져오기"),423,310,344,42,true,BLUE);
        diagNote(c,"미디어 방송은 CarPlay 링크나 TBT 원본의 증거가 아닙니다.",448);
        diagNote(c,"라이브러리 로드 확인과 Frida 후킹·원본 수신은 별개입니다.",468);
    }
    private void drawVehicleDiagnostics(Canvas c,Telemetry.DctTemperatures dct){
        panel(c,18,112,500,316,PANEL,LINE);panel(c,530,112,252,316,PANEL,LINE);
        text(c,UiText.t("신호별 수신 · 유효성"),32,138,16,BLUE,true);
        text(c,UiText.t("신호"),32,163,12,MUTED,false);
        text(c,UiText.t("상태"),150,163,12,MUTED,false);
        text(c,UiText.t("값"),235,163,12,MUTED,false);
        text(c,UiText.t("횟수 · 경과"),352,163,12,MUTED,false);
        int[] indices={Telemetry.BAT_VOLTAGE,Telemetry.BAT_SOC,Telemetry.TPMS_FL,Telemetry.TPMS_FR,Telemetry.TPMS_RL,Telemetry.TPMS_RR,1,10,11,12};
        for(int row=0;row<indices.length;row++){
            int i=indices[row];Telemetry.Sample s=samples[i];float y=184+row*21;
            fitted(c,UiText.t(Telemetry.LABELS[i]),32,y,13,112,WHITE);
            fitted(c,sampleState(s),150,y,13,78,!s.seen?MUTED:s.valid?ECO_GREEN:s.held?BLUE:RED);
            String reading="—";
            if(s.valid||s.held)reading=String.format(Locale.US,i==Telemetry.BAT_VOLTAGE?"%.2f":i==11||i==12?"%+.2f":"%.1f",s.value)+" "+Telemetry.UNITS[i];
            fitted(c,reading,235,y,13,112,WHITE);
            fitted(c,s.receivedCount+" · "+diagAge(s.seen?s.ageMs:-1)+" / "+diagAge(s.lastValidAgeMs),352,y,12,154,MUTED);
        }
        text(c,UiText.t("DCT 관측"),544,138,16,BLUE,true);
        button(c,UiText.t("변속기 설정"),642,154,128,36,false,BLUE);
        compactDiagRow(c,544,211,"변속기 종류",dct.transmissionSetting==1?"DCT":dct.transmissionSetting==2?"MT":dct.dctEstimate?"DCT (test)":"MT (test)");
        compactDiagRow(c,544,243,"DCT 온도",dctValue(dct));
        compactDiagRow(c,544,275,"DCT 서비스",connectionState(diagnostic.dctService));
        compactDiagRow(c,544,307,"차량 속도",diagnosticSample(7," km/h",false));
        compactDiagRow(c,544,339,"클러치 수명",UiText.t("미검증"));
        fitted(c,UiText.t("온도는 기존 진단 응답만 표시합니다."),544,382,11,220,MUTED);
        fitted(c,UiText.t("시험 추정은 실물 확인이 아닙니다."),544,401,11,220,MUTED);
        fitted(c,UiText.t("값·상태·수신 횟수와 경과를 표시합니다. 보관값은 현재 실측값이 아닙니다."),32,417,11,476,MUTED);
    }
    private void compactDiagRow(Canvas c,float x,float y,String label,String value){
        fitted(c,UiText.t(label),x,y,12,96,MUTED);
        fitted(c,value,x+99,y,12,130,WHITE);
    }
    private void wideDiagRow(Canvas c,float x,float y,String label,String value){
        fitted(c,UiText.t(label),x,y,12,96,MUTED);
        fitted(c,value,x+99,y,12,244,WHITE);
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
    private void drawConnectivityDiagnostics(Canvas c){
        panel(c,18,112,373,316,PANEL,LINE);panel(c,409,112,373,316,PANEL,LINE);
        text(c,UiText.t("Wi-Fi 상태"),32,138,16,BLUE,true);
        button(c,UiText.t("Wi-Fi 설정"),244,118,135,36,false,BLUE);
        diagRow(c,18,174,"Wi-Fi 기능",observed(diagnostic.wifiFeature,"선언됨","선언 안 됨"));
        diagRow(c,18,207,"Wi-Fi 상태",wifiRadio(diagnostic.wifiState));
        diagRow(c,18,240,"Wi-Fi 알림",observed(diagnostic.wifiWatch,"등록됨","미등록"));
        diagRow(c,18,273,"Wi-Fi 서비스",serviceAvailability(diagnostic.wifiService));
        diagRow(c,18,306,"Wi-Fi 모듈",UiText.t("미검증"));
        diagRow(c,18,339,"Android 상태",wifiRadio(diagnostic.wifiState));
        fitted(c,UiText.t("Wi-Fi 켜짐은 통신 모듈 검증과 별개"),32,386,12,340,MUTED);
        text(c,UiText.t("Bluetooth HAL 관찰"),423,138,16,BLUE,true);
        wideDiagRow(c,423,174,"컨트롤러",UiText.t(bluetoothStatus.controller));
        wideDiagRow(c,423,207,"선택된 HCD",UiText.t(bluetoothStatus.patch));
        wideDiagRow(c,423,240,"초기화 상태",UiText.t(bluetoothStatus.status));
        wideDiagRow(c,423,273,"로그 시각",UiText.t(bluetoothStatus.time));
        wideDiagRow(c,423,306,"현재 활성 상태",UiText.t("로그 기록만으로는 미확정"));
        fitted(c,UiText.t("최근 bt_vendor 초기화 기록입니다. 현재 활성 칩을 보증하지 않습니다."),423,350,12,340,MUTED);
        fitted(c,UiText.t("권한 거부·기록 없음은 미확인으로 표시됩니다."),423,373,12,340,MUTED);
        fitted(c,UiText.t("컨트롤러·HCD·fwcfg 결과만 추리며 원문 로그는 표시하지 않습니다."),423,396,11,340,MUTED);
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
