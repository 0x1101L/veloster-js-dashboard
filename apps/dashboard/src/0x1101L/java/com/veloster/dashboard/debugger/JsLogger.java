package com.veloster.dashboard.debugger;

import com.veloster.dashboard.vehicle.Telemetry;

import android.util.Log;

/** Versioned export of already retained JS observations. It never subscribes or queries OEM data. */
public final class JsLogger {
    public static final int CONTRACT_VERSION=1;
    private static final String TAG="JSLogger";
    private static final String PROFILE="js_kr",BUILD="eng.vmadmin.20260114.090656";
    private static final String[] BINDING_NAMES={"bcm","nmode","speed","pgs","hscan","media"};
    private static final String[] SIGNAL_NAMES={"rpm","oil_temp","coolant_temp","wheel_fl","wheel_fr","wheel_rl","wheel_rr","speed","gear","throttle","brake_pressure","lateral_g","longitudinal_g","steering_angle","ambient_temp","power","torque","boost","tpms_fl","tpms_fr","tpms_rl","tpms_rr","tcs","esc","abs","battery_soc","battery_voltage","steering_speed","elsd","intake_temp","accelerator_pedal","pd_tcs_enabled","pd_esc_enabled","pd_abs_warning"};
    private JsLogger(){}

    public static void emit(long recordedMs,Telemetry telemetry,JsDiagState.Snapshot diagnostics){
        Telemetry.Sample[] samples=telemetry.snapshot(recordedMs);
        for(int i=0;i<SIGNAL_NAMES.length&&i<samples.length;i++)emitSignal(recordedMs,i,samples[i]);
        java.util.HashSet<String> emitted=new java.util.HashSet<String>();
        for(String key:BINDING_NAMES){emitBinding(recordedMs,diagnostics,key);emitted.add(key);}
        for(String key:diagnostics.sourceNames()){
            if(!emitted.contains(key))emitBinding(recordedMs,diagnostics,key);
        }
        String routeSource=diagnostics.routeSource==1?"sticky":diagnostics.routeSource==0?"broadcast":"unknown";
        String routeStatus=diagnostics.routeNotice==1?"valid":diagnostics.routeError==2?"invalid":diagnostics.routeNotice==0?"missing":"unavailable";
        line(recordedMs,"diagnostic","route_status",routeSource,routeStatus,
            routeStatus.equals("valid")?Integer.toString(diagnostics.routeValue):"na","none",
            at(recordedMs,diagnostics.routeAgeMs),diagnostics.routeAgeMs,-1,-1,diagnostics.routeCount);
        String navStatus=diagnostics.navValueKnown?"valid":diagnostics.navError==2?"invalid":diagnostics.nav==1&&diagnostics.navError==0?"missing":"unavailable";
        line(recordedMs,"diagnostic","navigation_state_destination","navigation_provider",navStatus,
            diagnostics.navValueKnown?Integer.toString(diagnostics.navValue):"na","none",
            at(recordedMs,diagnostics.navAgeMs),diagnostics.navAgeMs,-1,-1,diagnostics.navQueryCount);
        String noticeStatus=diagnostics.navNotice==1?"valid":diagnostics.nav==1?"missing":"unavailable";
        line(recordedMs,"diagnostic","navigation_notice","navigation_provider",noticeStatus,"na","none",
            at(recordedMs,diagnostics.navNoticeAgeMs),diagnostics.navNoticeAgeMs,-1,-1,diagnostics.navNoticeCount);
        state(recordedMs,"cluster_service","cluster",diagnostics.clusterService);
        state(recordedMs,"micom_service","micom",diagnostics.micom);
        state(recordedMs,"tbt_method","cluster",diagnostics.tbt);
        state(recordedMs,"hscan_connection","hscan",diagnostics.hscan);
        state(recordedMs,"wifi_feature","android_wifi",diagnostics.wifiFeature);
        state(recordedMs,"wifi_service","android_wifi",diagnostics.wifiService);
        state(recordedMs,"wifi_receiver","android_wifi",diagnostics.wifiWatch);
        String wifiStatus=diagnostics.wifiState==3?"enabled":diagnostics.wifiState==1?"disabled":diagnostics.wifiState==2?"enabling":diagnostics.wifiState==0?"disabling":diagnostics.wifiState==-2?"permission_denied":"unknown";
        line(recordedMs,"diagnostic","wifi_radio","android_wifi",wifiStatus,
            diagnostics.wifiState<0?"na":Integer.toString(diagnostics.wifiState),"none",-1,-1,-1,-1,0);
    }

    private static void emitBinding(long recordedMs,JsDiagState.Snapshot diagnostics,String key){
        JsDiagState.SourceSnapshot source=diagnostics.source(key);
        String name="binding_"+key.replace('.','_');
        line(recordedMs,"diagnostic",name,key,statusToken(source.status),"na","none",
            source.ageMs<0?-1:recordedMs-source.ageMs,source.ageMs,-1,-1,source.count);
        if(source.processed>0||source.invalid>0||key.startsWith("media.")){
            line(recordedMs,"diagnostic",name+"_processed",key,"observed",Long.toString(source.processed),"count",-1,-1,-1,-1,source.processed);
            line(recordedMs,"diagnostic",name+"_invalid",key,"observed",Long.toString(source.invalid),"count",-1,-1,-1,-1,source.invalid);
        }
    }

    private static void emitSignal(long now,int index,Telemetry.Sample sample){
        String status=!sample.seen?"missing":sample.valid?"valid":sample.held?"held":sample.stale?"expired":"invalid";
        String value=(status.equals("valid")||status.equals("held"))?number(sample.value):"na";
        String unit=Telemetry.UNITS[index].length()==0?"none":Telemetry.UNITS[index].replace(' ','_');
        long receivedAge=sample.seen?sample.ageMs:-1;
        long validAge=sample.lastValidAgeMs==Long.MAX_VALUE?-1:sample.lastValidAgeMs;
        long changedAge=sample.lastChangedAgeMs==Long.MAX_VALUE?-1:sample.lastChangedAgeMs;
        line(now,"signal",signalName(index),sample.source,status,value,unit,
            at(now,receivedAge),receivedAge,at(now,validAge),validAge,sample.receivedCount);
    }

    private static void state(long now,String name,String source,int value){
        String status=value<0?"unknown":value==0?"absent":value==1?"present":"error";
        line(now,"diagnostic",name,source,status,value<0?"na":Integer.toString(value),"none",-1,-1,-1,-1,0);
    }

    private static long at(long now,long age){return age<0?-1:now-age;}
    private static String number(float value){return Float.isNaN(value)||Float.isInfinite(value)?"na":Float.toString(value);}
    private static String signalName(int i){return SIGNAL_NAMES[i];}
    private static String statusToken(String value){
        if(value==null)return "unknown";
        if(value.equals("registered")||value.equals("stopped")||value.equals("connecting")||value.equals("connected")||value.equals("disconnected")||value.equals("unsupported")||value.equals("error")||value.equals("permission denied")||value.equals("received"))return value.replace(' ','_');
        return "unknown";
    }
    private static void line(long t,String type,String name,String source,String status,String value,String unit,
            long receivedMs,long receivedAgeMs,long validMs,long validAgeMs,long count){
        StringBuilder s=new StringBuilder(256).append("JS_LOGGER v=").append(CONTRACT_VERSION)
            .append(" profile=").append(PROFILE).append(" build=").append(BUILD).append(" t_ms=").append(t)
            .append(" type=").append(type).append(" name=").append(token(name)).append(" source=").append(token(source))
            .append(" status=").append(token(status)).append(" value=").append(token(value)).append(" unit=").append(token(unit))
            .append(" received_ms=").append(receivedMs).append(" received_age_ms=").append(receivedAgeMs)
            .append(" valid_ms=").append(validMs).append(" valid_age_ms=").append(validAgeMs).append(" count=").append(count);
        Log.i(TAG,s.toString());
    }
    private static String token(String value){return value==null||value.length()==0?"na":value.replace(' ','_');}
}
