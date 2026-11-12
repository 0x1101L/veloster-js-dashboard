package com.veloster.vehicle;

import static com.veloster.vehicle.Telemetry.*;

/** JS copy of the PD baseline; independent state and signal changes belong here. */
final class JsTelemetryDecoder {
    interface Sink { void put(int signal,float value,boolean valid,long at); }
    private final Sink sink;
    private final JsPowertrain jsPowertrain=new JsPowertrain();
    private final JsGearState jsGear=new JsGearState();
    private int speedUnit;
    private JsDriveMode driveMode=JsDriveMode.UNKNOWN;
    private long driveModeAt=-1;
    JsTelemetryDecoder(Sink sink){this.sink=sink;}
    private void put(int i,float v,boolean valid,long at){sink.put(i,v,valid,at);}
    void clear(){jsPowertrain.clear();jsGear.clear();speedUnit=0;driveMode=JsDriveMode.UNKNOWN;driveModeAt=-1;}
    JsDriveMode driveMode(long now){
        return driveModeAt>=0&&now-driveModeAt<JS_FAST_STALE_MS?driveMode:JsDriveMode.UNKNOWN;
    }
    void receive(int cid,int function,byte[] b,long now){
        if(cid!=0xc7||b==null||function==2||function==8||function==0x14)return;
        if(function==0x04){
            if(b.length!=8||now<0||now<driveModeAt)return;
            // JS 1.6T DCT captures distinguish NORMAL from SMART at b5, not at b2.
            switch(b[5]&255){
                case 0x10: driveMode=JsDriveMode.NORMAL;break;
                case 0x30: driveMode=JsDriveMode.SPORT;break;
                case 0x20: driveMode=JsDriveMode.ECO;break;
                case 0x50: driveMode=JsDriveMode.SMART;break;
                default: driveMode=JsDriveMode.UNKNOWN;
            }
            driveModeAt=now;
            return;
        }
        if(function==8&&b.length==8){
            int raw=(b[2]&255)|((b[3]&255)<<8);put(0,raw*0.25f,raw!=65535,now);
        }else if(function==2&&b.length==4){
            int raw=(b[1]&255)|((b[2]&1)<<8);speedUnit=(b[2]>>1)&1;
            put(7,raw*0.5f*(speedUnit==1?1.609344f:1f),raw!=511,now);
        }else if(function==9&&b.length==8){
            int coolant=b[1]&255,pedal=b[6]&255,tps=b[5]&255;
            put(2,coolant*0.75f-48,coolant<255,now);put(ACCEL_PEDAL,pedal*0.3906f,pedal<255,now);
            float throttle=tps*0.4694836f-15.0234742f;put(9,Math.max(0,Math.min(100,throttle)),tps>=32&&tps<=245,now);
        }else if(function==0x0b&&b.length==8){int raw=b[2]&255;put(INTAKE,raw*0.75f-48,raw<255,now);
        }else if(function==0x0d&&b.length==8){int raw=b[4]&255;put(1,raw*0.75f-40,raw>0&&raw<255,now);
        }else if(function==0x14&&b.length==8){int raw=b[3]&255;put(AMBIENT,raw*0.5f-40,raw<255,now);
        }else if(function==0&&b.length==8){
            int soc=b[2]&255;boolean sensorOk=(b[6]&0x80)==0&&(b[7]&0x80)==0;
            put(BAT_SOC,soc,sensorOk&&soc<=100,now);
        }else if(function==0x0a&&b.length==8){
            // PD-2 da14ad53632f216d04042645958c9cfaafed1731, EMS14: candidate pending JS comparison.
            int raw=b[3]&255;put(BAT_VOLTAGE,raw*0.1f,raw>=60&&raw<=200,now);
        }else if(function==0x19&&b.length==5){
            int angle=(short)((b[0]&255)|((b[1]&255)<<8)),rate=b[2]&255;boolean stateOk=(b[3]&255)==7;
            put(STEERING,angle*0.1f,stateOk&&Math.abs(angle)<=10800,now);put(STEERING_SPEED,rate*4,stateOk&&rate<255,now);
        }else if(function==0x13&&b.length==8){
            int pressure=((b[3]&255)>>2)|((b[4]&63)<<6);put(10,pressure*0.1f,(b[4]&0xc0)==0,now);
            int lat=(b[0]&255)|((b[1]&7)<<8),longitudinal=((b[1]&255)>>5)|((b[2]&255)<<3);
            put(11,(lat*0.01f-10.23f)/9.80665f,(b[1]&0x18)==0&&lat<2047,now);
            put(12,(longitudinal*0.01f-10.23f)/9.80665f,(b[3]&3)==0&&longitudinal<2047,now);
        }else if(function==0x1c&&b.length==4){
            int tcsOff=(b[0]>>1)&3,escOff=b[1]&3;boolean allFF=true;for(byte v:b)if(v!=(byte)255)allFF=false;
            put(PD_TCS_ENABLED,tcsOff==0?1:0,!allFF&&tcsOff<=1,now);put(PD_ESC_ENABLED,escOff<2?1:0,!allFF&&escOff<=2,now);put(PD_ABS_WARNING,b[0]&1,!allFF,now);
        }else if(function==0x20&&b.length==6){
            int status=b[1]&7,unit=(b[1]>>3)&3;float factor=unit==1?0.7251887f:unit==2?1.4503774f:1;
            for(int i=0;i<4;i++){int raw=b[2+i]&255;float psi=raw*factor;put(TPMS_FL+i,psi,status==1&&unit<=2&&raw<255&&psi<=100,now);}
        }else if((function==0x1d||function==0x1e)&&b.length==8){
            JsGearState.Result gear=jsGear.receive(function,b,now);if(gear.updated)put(8,gear.value,gear.valid,gear.time);
        }else if(function!=0x0a||b.length!=8)return;
        JsPowertrain.Result calculated=jsPowertrain.receive(function,b,now);
        if(calculated.torqueUpdated){put(TORQUE,calculated.torqueNm,calculated.torqueValid,calculated.torqueAt);put(POWER,calculated.powerPs,calculated.torqueValid,calculated.torqueAt);}
        if(calculated.boostUpdated)put(BOOST,calculated.boostBar,calculated.boostValid,calculated.boostAt);

    }
}
