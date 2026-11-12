package com.veloster.dashboard.vehicle.jsn;
import static com.veloster.dashboard.vehicle.Telemetry.*;
public final class JsnTelemetryDecoder {
    public interface Sink { void put(int i,float v,boolean valid,long now); }
    private final Sink sink;
    public JsnTelemetryDecoder(Sink sink){this.sink=sink;}
    private void put(int i,float v,boolean valid,long now){sink.put(i,v,valid,now);}
    public void receive(int funcLow,byte[] b,long now){
        if(funcLow==0x07&&b.length==8){
            int soc=b[2]&255,voltage=((b[4]&63)<<8)|(b[3]&255);
            boolean sensorOk=(b[6]&0x80)==0&&(b[7]&0x80)==0;
            put(BAT_SOC,soc,sensorOk&&soc<=100,now);
            put(BAT_VOLTAGE,voltage*0.001f+6,sensorOk&&voltage<=12000,now);
        }else if(funcLow==0x53&&b.length==5){
            int raw=b[2]&255;put(STEERING_SPEED,raw*4,raw<255,now);
        }else if(funcLow==0x23&&b.length==8){
            // JSN/Manual DBC call CAN 0x547 EMS15; AiM names its byte2 ECU_IAT.
            // 255 is conservatively withheld until validity is checked on this car.
            int raw=b[2]&255;put(INTAKE,raw*0.75f-48,raw<255,now);
        }else if(funcLow==0x6e&&b.length==8){
            // WHL_SPD11: LE14 per wheel; upper bits carry counters/checksum, not speed.
            for(int i=0;i<4;i++){
                int raw=(b[2*i]&255)|((b[2*i+1]&63)<<8);
                put(3+i,raw*0.03125f,raw<16352,now);
            }
        }else if(funcLow==0x5a&&b.length==8){
            boolean allFF=true;for(byte v:b)if(v!=(byte)255)allFF=false;
            put(TCS,(b[1]>>1)&1,!allFF,now);put(ESC,(b[1]>>6)&1,!allFF,now);put(ABS,(b[1]>>2)&1,!allFF,now);
        }
    }
}
