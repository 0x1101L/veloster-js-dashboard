package com.veloster.dashboard.vehicle;

import com.veloster.dashboard.vehicle.js.JsTelemetryDecoder;
import com.veloster.dashboard.vehicle.pd.PdTelemetryDecoder;
import com.veloster.dashboard.vehicle.jsn.JsnTelemetryDecoder;

/** Packet observations only. No CAN writes, preferences, simulation or Android dependency. */
public final class Telemetry {
    public static final int STEERING=13,AMBIENT=14,POWER=15,TORQUE=16,BOOST=17;
    public static final int TPMS_FL=18,TPMS_FR=19,TPMS_RL=20,TPMS_RR=21;
    public static final int TCS=22,ESC=23,ABS=24,BAT_SOC=25,BAT_VOLTAGE=26,STEERING_SPEED=27,ELSD=28;
    public static final int INTAKE=29,ACCEL_PEDAL=30,PD_TCS_ENABLED=31,PD_ESC_ENABLED=32,PD_ABS_WARNING=33,CLUSTER_FUEL=34,CLUSTER_RANGE=35;
    public static final String[] LABELS = {"엔진 회전수", "오일 온도", "냉각수 온도", "앞 왼쪽 휠", "앞 오른쪽 휠", "뒤 왼쪽 휠", "뒤 오른쪽 휠", "차량 속도", "EMS 기어", "스로틀", "브레이크 압력", "횡가속", "종가속", "조향각", "외기 온도", "출력 (계산)", "토크 (계산)", "부스트 (계산)", "앞 왼쪽 공기압", "앞 오른쪽 공기압", "뒤 왼쪽 공기압", "뒤 오른쪽 공기압", "TCS", "ESC", "ABS", "배터리 SOC", "배터리 전압", "조향 속도", "e-LSD 수치", "흡기 온도", "가속 페달", "TCS 사용 상태", "ESC 사용 상태", "ABS 경고등", "계기판 연비", "주행가능거리"};
    public static final String[] UNITS = {"rpm", "°C", "°C", "km/h", "km/h", "km/h", "km/h", "km/h", "", "%", "bar", "G", "G", "°", "°C", "PS", "N·m", "bar", "psi", "psi", "psi", "psi", "", "", "", "%", "V", "°/s", "", "°C", "%", "", "", "", "km/L", "km"};
    public static final long STALE_MS = 60000; // UI freshness threshold, not an OEM timeout.
    public static final long HISTORY_MS=300000,HISTORY_STEP_MS=250;
    private final History[] history=new History[LABELS.length];
    /** Display orientation follows the driver's reported left/right convention. Raw SAS stays unchanged. */
    public static float displayValue(int signal,float raw){return signal==STEERING?(raw==0?0:-raw):raw;}
    public static boolean jsTenthSignal(int signal){return signal==11||signal==12||signal==BOOST;}
    public static String jsTenth(float value,boolean signed){
        String result=new java.math.BigDecimal(Float.toString(value)).setScale(1,java.math.RoundingMode.DOWN).toPlainString();
        return signed&&!result.startsWith("-")?"+"+result:result;
    }
    public static final class Series {
        public final long[] times;
        public final float[] values;
        public final boolean[] valid,breakBefore;
        Series(int n){times=new long[n];values=new float[n];valid=new boolean[n];breakBefore=new boolean[n];}
        public int size(){return times.length;}
    }
    private static final class History {
        private static final int CAPACITY=(int)(HISTORY_MS/HISTORY_STEP_MS)+1;
        final long[] times=new long[CAPACITY];
        final float[] values=new float[CAPACITY];
        final boolean[] valid=new boolean[CAPACITY],breakBefore=new boolean[CAPACITY];
        int start,count;
        void add(float value,boolean ok,long now){
            ok=ok&&!Float.isNaN(value)&&!Float.isInfinite(value);
            int last=(start+count-1+CAPACITY)%CAPACITY;
            if(count>0&&now<times[last])return;
            if(count>0&&now/HISTORY_STEP_MS==times[last]/HISTORY_STEP_MS){
                breakBefore[last]|=!ok||!valid[last];times[last]=now;values[last]=value;valid[last]=ok;return;
            }
            boolean gap=count>0&&!valid[last];
            int at=(start+count)%CAPACITY;
            if(count==CAPACITY){start=(start+1)%CAPACITY;}else count++;
            times[at]=now;values[at]=value;valid[at]=ok;breakBefore[at]=gap||!ok;
            while(count>0&&times[start]<now-HISTORY_MS){start=(start+1)%CAPACITY;count--;}
        }
        Series snapshot(long now,long window){
            int n=0;long left=now-window;
            for(int i=0;i<count;i++){int at=(start+i)%CAPACITY;if(times[at]>=left&&times[at]<=now)n++;}
            Series result=new Series(n);n=0;
            for(int i=0;i<count;i++){int at=(start+i)%CAPACITY;if(times[at]<left||times[at]>now)continue;
                result.times[n]=times[at];result.values[n]=values[at];result.valid[n]=valid[at];result.breakBefore[n]=breakBefore[at];n++;
            }
            return result;
        }
    }
    public synchronized Series history(int signal,long now,long window){
        if(signal<0||signal>=LABELS.length||window<=0||window>HISTORY_MS)throw new IllegalArgumentException("history range");
        return history[signal]==null?new Series(0):history[signal].snapshot(now,window);
    }
    private double trialDistanceKm,trialFuelLitres;
    private long trialAt=-1;
    private float trialSpeed,trialFuelRate;
    private int trialBatchSamples;
    private void clearFuelTrial(){trialDistanceKm=0;trialFuelLitres=0;trialAt=-1;trialBatchSamples=0;}
    /** Experimental PD-derived formula on a different JS record; never a validated fuel measurement. */
    private void fuelTrial(byte[] b,long now){
        if(b.length!=6||now<0)return;
        Sample speed=sample(7,now);int raw=(b[0]&255)|((b[1]&255)<<8);
        if(!speed.valid||raw==65535||speed.value<0||Float.isNaN(speed.value)){clearFuelTrial();return;}
        float rate=raw*0.04608f;
        if(trialAt>=0&&(now<trialAt||now-trialAt>JS_FAST_STALE_MS))clearFuelTrial();
        if(trialAt==now){trialFuelRate+=(rate-trialFuelRate)/++trialBatchSamples;trialSpeed=speed.value;return;}
        if(trialAt>=0){double hours=(now-trialAt)/3600000.0;trialDistanceKm+=trialSpeed*hours;trialFuelLitres+=trialFuelRate*hours;}
        trialAt=now;trialSpeed=speed.value;trialFuelRate=rate;trialBatchSamples=1;
    }
    public synchronized float jsFuelTrial(long now){
        if(!js||trialAt<0||now<trialAt||now-trialAt>=JS_FAST_STALE_MS||!sample(7,now).valid||trialFuelLitres<=0)return Float.NaN;
        return (float)(trialDistanceKm/trialFuelLitres);
    }
    private int speedUnit=-1, rearView, rawSpeed;
    private boolean pd,js,jsn,supported;
    private final PdTelemetryDecoder pdDecoder=new PdTelemetryDecoder(new PdTelemetryDecoder.Sink(){
        public void put(int i,float v,boolean ok,long now){Telemetry.this.put(i,v,ok,now);}
        public int speedUnit(){return speedUnit;}
        public void speedUnit(int value){speedUnit=value;}
    });
    private final JsnTelemetryDecoder jsnDecoder=new JsnTelemetryDecoder(new JsnTelemetryDecoder.Sink(){
        public void put(int i,float v,boolean ok,long now){Telemetry.this.put(i,v,ok,now);}
    });
    private final JsTelemetryDecoder jsDecoder=new JsTelemetryDecoder(new JsTelemetryDecoder.Sink(){
        public void put(int i,float v,boolean ok,long now){Telemetry.this.put(i,v,ok,now);}
    });
    private final long[] jsValidAt=new long[LABELS.length],jsChangedAt=new long[LABELS.length];
    private final boolean[] jsHasValue=new boolean[LABELS.length];
    // Application policy, not an OEM timeout; fast readings disappear promptly.
    public static final long JS_FAST_STALE_MS=2000;
    public enum JsDriveMode { UNKNOWN, NORMAL, SPORT, ECO, SMART }
    public synchronized JsDriveMode jsDriveMode(long now){return js?jsDecoder.driveMode(now):JsDriveMode.UNKNOWN;}
    private static final long JS_DCT_STALE_MS=5000;
    private DctTemperatures dctTemperatures=DctTemperatures.UNKNOWN;
    private boolean jsDualTemperatureObserved;
    private int jsTransmissionSetting;
    public synchronized void jsTransmissionSetting(int value){jsTransmissionSetting=value==1||value==2?value:0;}
    public static final class DctTemperatures {
        public static final float MIN_C=-40,MAX_C=300;
        static final DctTemperatures UNKNOWN=new DctTemperatures(Float.NaN,Float.NaN,-1,true);
        public final float oddC,evenC;
        public final boolean single;
        public boolean dctEstimate;
        public int transmissionSetting;
        private final long at;
        private DctTemperatures(float oddC,float evenC,long at,boolean single){this.oddC=oddC;this.evenC=evenC;this.at=at;this.single=single;}
        private static float temperature(byte[] reply,int offset){
            int raw=((reply[offset]&255)<<8)|(reply[offset+1]&255);
            float c=raw*2+MIN_C;
            return c<=MAX_C?c:Float.NaN;
        }
    }
    /** Complete positive TCU reply, including 62 01 A4. PD-2/OBDb mapping; JS transport not enabled. */
    public synchronized void jsDctTemperatureReply(byte[] reply,long now){
        if(!js||now<0||now<dctTemperatures.at||reply==null||reply.length<36
            ||reply[0]!=0x62||reply[1]!=0x01||(reply[2]&255)!=0xa4)return;
        dctTemperatures=new DctTemperatures(DctTemperatures.temperature(reply,32),DctTemperatures.temperature(reply,34),now,false);
        if(jsTransmissionSetting==0&&!Float.isNaN(dctTemperatures.oddC)&&!Float.isNaN(dctTemperatures.evenC))jsDualTemperatureObserved=true;
    }
    /** Candidate opendbc hyundai_can.dbc TCU11 TEMP_AT; no JS HSCAN slot is assumed. */
    public synchronized void jsTcu11Frame(int canId,byte[] frame,long now){
        if(!js||canId!=0x111||frame==null||frame.length!=8||now<0||now<dctTemperatures.at)return;
        int raw=frame[4]&255;
        dctTemperatures=new DctTemperatures(raw==255?Float.NaN:raw-40,Float.NaN,now,true);
    }
    public synchronized DctTemperatures dctTemperatures(long now){
        if(!js)return DctTemperatures.UNKNOWN;
        DctTemperatures result=dctTemperatures.at>=0&&now>=dctTemperatures.at&&now-dctTemperatures.at<JS_DCT_STALE_MS
            ?new DctTemperatures(dctTemperatures.oddC,dctTemperatures.evenC,dctTemperatures.at,dctTemperatures.single)
            :new DctTemperatures(Float.NaN,Float.NaN,-1,dctTemperatures.single);
        result.dctEstimate=jsTransmissionSetting==0&&(jsDualTemperatureObserved||jsDecoder.manualModeObserved());
        result.transmissionSetting=jsTransmissionSetting;
        return result;
    }
    private static boolean jsNoTimeExpiry(int i){return i==AMBIENT||i>=TPMS_FL&&i<=TPMS_RR;}
    public static boolean jsFast(int i){return !(i==1||i==2||i==AMBIENT||i>=TPMS_FL&&i<=TPMS_RR||i==BAT_SOC||i==BAT_VOLTAGE||i==INTAKE||i==CLUSTER_FUEL||i==CLUSTER_RANGE);}

    public synchronized boolean pdProfile(){return pd;}
    public synchronized boolean jsProfile(){return js;}
    public synchronized boolean jsnProfile(){return jsn;}
    public synchronized boolean supportedProfile(){return supported;}
    public synchronized boolean pdLayoutProfile(){return pd||js;}
    public synchronized void profile(boolean isPd,boolean isJs,boolean isJsn){pd=isPd;js=isJs;jsn=isJsn;supported=pd||js||jsn;}
    private final long[] counts=new long[LABELS.length];
    private final String[] sources=new String[LABELS.length];
    private final String[] validSources=new String[LABELS.length];
    private String writeSource="unknown";
    private final float[] values = new float[LABELS.length];
    private final long[] times = new long[LABELS.length];
    private final boolean[] valid = new boolean[LABELS.length], changed = new boolean[LABELS.length], seen = new boolean[LABELS.length];
    private long epoch,ptVersion,gfVersion,ptTime,gfTime,stdTime,lastComputedPt=-1,lastComputedGf=-1;
    private boolean ptValid,gfValid,stdSeen;
    private float inputRpm,inputTqi,inputAcor,inputStd,inputBoost,inputAmbient,friction;
    private float observedPreviousRpm=-1;
    private boolean observedTorqueValid;
    private final ThreadLocal<Calculation> calculation=new ThreadLocal<Calculation>();
    private static final class Calculation {
        long epoch,pt,gf,time;
        float power,torque,boost;
        boolean valid;
    }
    public static final class Sample {
        public final float value;
        public final long ageMs;
        public final long receivedCount;
        public final String source;
        public final boolean valid, changed, seen, stale, held;
        public final long lastValidAgeMs,lastChangedAgeMs;
        Sample(float v, boolean ok, boolean diff, boolean received, boolean old,long age,boolean heldValue,long validAge,long changedAge,long count,String source) {
            ageMs=age;value=v; valid=ok; changed=diff; seen=received; stale=old;
            held=heldValue;lastValidAgeMs=validAge;lastChangedAgeMs=changedAge;receivedCount=count;
            this.source=source;
        }
    }
    private void put(int i, float v, boolean ok, long now) {
        if(js){
            ok=ok&&!Float.isNaN(v)&&!Float.isInfinite(v);
            changed[i]=ok&&jsHasValue[i]&&values[i]!=v;
            if(ok){if(!jsHasValue[i]||changed[i])jsChangedAt[i]=now;jsValidAt[i]=now;jsHasValue[i]=true;values[i]=v;validSources[i]=writeSource;}
            counts[i]++;valid[i]=ok;seen[i]=true;times[i]=now;
            sources[i]=writeSource;
        }else{
            changed[i] = ok && valid[i] && seen[i] && now-times[i] <= STALE_MS && values[i] != v;
            counts[i]++; values[i]=v; valid[i]=ok; seen[i]=true; times[i]=now;
        }
        if(history[i]==null)history[i]=new History();
        history[i].add(v,ok,now);
    }
    /** Source label comes from the observed API path; unavailable provenance stays unknown. */
    public synchronized String source(int i){return i<0||i>=sources.length||sources[i]==null?"unknown":sources[i];}
    public synchronized void clear() {
        speedUnit=-1; rearView=0; rawSpeed=0;
        dctTemperatures=DctTemperatures.UNKNOWN;
        jsDualTemperatureObserved=false;
        clearFuelTrial();
        pdDecoder.clear();jsDecoder.clear();
        epoch++;ptValid=false;gfValid=false;stdSeen=false;lastComputedPt=-1;lastComputedGf=-1;
        calculation.remove();
        observedPreviousRpm=-1;observedTorqueValid=false;
        writeSource="unknown";
        for(int i=0;i<LABELS.length;i++) { counts[i]=0; valid[i]=false; changed[i]=false; seen[i]=false;jsHasValue[i]=false;jsValidAt[i]=0;jsChangedAt[i]=0;sources[i]="unknown";validSources[i]="unknown"; history[i]=null; }
    }
    public synchronized Sample sample(int i, long now) {
        long age=seen[i]?Math.max(0,now-times[i]):Long.MAX_VALUE;
        long validAge=js&&jsHasValue[i]?Math.max(0,now-jsValidAt[i]):Long.MAX_VALUE;
        long changedAge=js&&jsHasValue[i]?Math.max(0,now-jsChangedAt[i]):Long.MAX_VALUE;
        boolean stale=js?jsHasValue[i]&&!jsNoTimeExpiry(i)&&validAge>=(jsFast(i)?JS_FAST_STALE_MS:STALE_MS):seen[i]&&age>=STALE_MS;
        boolean current=valid[i]&&!stale;
        boolean held=js&&jsHasValue[i]&&!current&&((i==7&&valid[i]&&stale)||(!stale&&!jsFast(i)));
        String source=(current||held)&&js?validSources[i]:sources[i];
        return new Sample(values[i],current,changed[i]&&current,seen[i],stale,age,held,validAge,changedAge,counts[i],source==null?"unknown":source);
    }
    public synchronized Sample[] snapshot(long now) {
        Sample[] result=new Sample[LABELS.length];
        for(int i=0;i<result.length;i++)result[i]=sample(i,now);
        return result;
    }
    public static String gearLabel(int raw) {if(raw>=101&&raw<=107)return "D"+(raw-100);if(raw>=201&&raw<=207)return "M"+(raw-200);if(raw==-1)return "P";if(raw==-2)return "R";if(raw==-3)return "N";if(raw==-4)return "D";if(raw==-5)return "M";return raw>=1&&raw<=7?String.valueOf(raw):"—";}
    public synchronized void speed(int raw,long now) {
        writeSource="speed";
        if(!js||raw>=0&&raw<511)rawSpeed=raw;
        put(7,speedUnit==1?raw*1.609344f:raw,speedUnit>=0 && raw>=0 && raw<511,now);
    }
    public synchronized void units(byte[] b,long now) {
        writeSource="nmode";
        speedUnit=b!=null && b.length>=3?(b[0]&1):-1;
        int unit=b!=null && b.length>=3?(b[0]>>1)&3:0;
        int raw=b!=null && b.length>=3?b[unit==2?2:1]&255:255;
        // JSN OEM ranges and offsets; the dashboard consistently shows Celsius.
        boolean ok=(unit==1&&raw<=100)||(unit==2&&raw<=180);
        put(AMBIENT,unit==2?(raw-40-32)*5f/9:raw-40,ok,now);
        if(seen[7]) {
            // Unit events must not refresh the timestamp or reception count of old speed.
            if(!js||valid[7]){
                values[7]=speedUnit==1?rawSpeed*1.609344f:rawSpeed;
                valid[7]=speedUnit>=0 && rawSpeed>=0 && rawSpeed<511;
            }
            changed[7]=false;
        }
    }
    public synchronized String diagnostics(long now) {
        StringBuilder s=new StringBuilder("unit=").append(speedUnit);
        for(int i=0;i<LABELS.length;i++)s.append(" ").append(i).append(":").append(counts[i])
            .append("/").append(seen[i]?now-times[i]:-1).append("ms/").append(sample(i,now).valid?"valid":"unknown");
        return s.toString();
    }
    public synchronized void rearView(int view) {
        rearView=view;
        if(view==1 || view==3) { valid[8]=false;changed[8]=false; }
        // A clear event alone cannot resurrect a previously received gear.
    }
    public synchronized void additional(byte[] b,long now) {
        writeSource="nmode";
        pressures(b,now);
        if(b==null || (b.length!=19 && b.length!=40)) {
            for(int i=8;i<=10;i++)put(i,0,false,now);
            put(STEERING,0,false,now);
            return;
        }
        int gear=b[4];
        int throttle=((b[6]&255)<<8)|(b[7]&255);
        int brake=b.length==19?b[0]:((b[0]&255)<<8)|(b[1]&255);
        put(8,gear,gear>=1 && gear<=6 && b[5]!=1 && rearView!=1 && rearView!=3,now); // Verified manual forward gears only.
        put(9,Math.min(100,throttle*0.3906f),throttle!=65535,now);
        put(10,b.length==19?brake:brake*0.1f,brake>=0 && brake!=65535,now);
        if(b.length==40){
            int angle=((b[20]&255)<<8)|(b[21]&255);
            put(STEERING,(short)angle*0.1f,true,now);
        }
    }
    /** PD signal-table decoder. */
    public synchronized void hscanPd(int cid,int function,byte[] b,long now){
        writeSource="hscan_pd";
        if(pdDecoder.receive(cid,function,b,now)){pd=true;js=false;}
    }
    /** JS uses the PD table as a baseline while confirmed OEM values stay authoritative. */
    public synchronized void hscanJs(int cid,int function,byte[] b,long now){
        if(cid!=0xc7||b==null)return;
        writeSource="hscan_js";
        pd=false;js=true;
        if(function==0x22)fuelTrial(b,now);
        jsDecoder.receive(cid,function,b,now);
    }
    /** Only new IL frames from the verified JSN HSCAN bridge. */
    public synchronized void hscanJsn(int cid,int funcLow,byte[] b,long now){
        if(cid!=0xc7||(funcLow&0x80)!=0||b==null)return;
        writeSource="hscan_jsn";jsnDecoder.receive(funcLow,b,now);
    }
    /** Explicit CAN-ID input only. No MICOM function has been mapped to e-LSD yet.
     * Retains the candidate scaled number without inventing a percent/current/torque unit. */
    public synchronized void canFrame(int canId,byte[] b,long now){
        if(canId!=0x3f0||b==null||b.length!=8)return;
        writeSource="can_0x3f0";
        int raw=(b[1]&255)|((b[2]&255)<<8);
        put(ELSD,raw*0.1f,raw!=65535,now);
    }
    private void pressures(byte[] b,long now){
        // Captured NAdd40: status22, unit23, FL/FR/RL/RR BE16 at28..35.
        // Only status1/unit0 has been corroborated against this vehicle's cluster (psi).
        boolean known=b!=null&&b.length==40&&b[22]==1&&b[23]==0;
        for(int i=0;i<4;i++){
            int raw=known?((b[28+2*i]&255)<<8)|(b[29+2*i]&255):511;
            // Conservative display bound, not an asserted OEM validity enum.
            put(TPMS_FL+i,raw,known&&raw<=100,now);
        }
    }
    public synchronized void rpm(byte[] b, long now) {
        writeSource="nmode";
        ptVersion++;ptTime=now;
        if(b==null || b.length<8) { ptValid=false;put(0,0,false,now);invalidateComputed(now);return; }
        int raw=((b[1]&255)<<8)|(b[2]&255);
        // OEM onPowerTorqueTurboBoostBitMasking: unsigned big-endian * 0.25.
        // 0xffff conservatively suppressed; protocol validity is not fully documented.
        put(0,raw*0.25f,raw!=65535,now);
        ptValid=raw!=65535;
        inputRpm=raw*0.25f;inputAcor=(b[0]&255)*0.390625f;inputTqi=(b[3]&255)*0.390625f;
        inputAmbient=(float)(((b[4]>>2)&31)*10.731613+458.98);
        inputBoost=(float)((((b[5]&255)<<4)|((b[6]>>4)&15))*1.322);
        if((b[4]&3)==3){
            inputStd=(((b[6]&15)<<2)|((b[7]>>6)&3))*10f;stdTime=now;stdSeen=inputStd>0;
        }
        if(!ptValid)invalidateComputed(now);
    }
    public synchronized void temperatures(byte[] b, long now) {
        writeSource="nmode";
        for(int i=0;i<2;i++) {
            int raw=b!=null && b.length>=2 ? b[i]&255 : 255;
            // OEM onOilCoolantTemperatureBitMasking explicitly rejects 0xff.
            put(i+1,raw*0.75f-(i==0?40:48),raw<255,now);
        }
    }
    public synchronized void gforce(byte[] b,long now) {
        writeSource="nmode";
        gfVersion++;gfTime=now;gfValid=b!=null&&b.length>=8;
        if(gfValid)friction=(b[3]&255)*0.390625f;else invalidateComputed(now);
        for(int axis=0;axis<2;axis++) {
            if(b==null || b.length<8) { put(11+axis,0,false,now);continue; }
            int offset=axis==0?0:4;
            int raw=((b[offset]&255)<<8)|(b[offset+1]&255);
            int status=(b[2]>>(axis*4))&15;
            float value=(float)(raw*0.000127465-4.17677312);
            if(axis==1)value=-value; // OEM longitudinal sign inversion.
            // Status 0 is observed in retained normal data; other states remain unknown.
            put(11+axis,value,status==0 && raw!=65535,now);
        }
    }
    private void invalidateComputed(long now){for(int i=POWER;i<=BOOST;i++)put(i,0,false,now);}
    private static boolean close(float a,float b){return !Float.isNaN(a)&&!Float.isInfinite(a)&&Math.abs(a-b)<=Math.max(0.0001f,Math.abs(b)*0.00001f);}
    /** Called at the start of OEM calculation, capturing the actual input generation. */
    public synchronized void beginComputed(float rpm,float tqi,float acor,float standard,float boost,float ambient){
        Calculation c=new Calculation();c.epoch=epoch;c.pt=ptVersion;c.gf=gfVersion;
        c.time=Math.min(ptTime,Math.min(gfTime,stdTime));
        c.valid=ptValid&&gfValid&&stdSeen&&close(rpm,inputRpm)&&close(tqi,inputTqi)&&close(acor,inputAcor)
            &&close(standard,inputStd)&&close(boost,inputBoost)&&close(ambient,inputAmbient);
        float rawTorque=(acor-friction)*standard/100;
        c.power=Math.max(0,(float)(rawTorque*((double)rpm/716.2/9.81)));
        c.torque=rawTorque<5.5f?0:rawTorque;
        float pressure=(float)(((double)boost-1.333*ambient)/10);
        c.boost=pressure<5.5f?0:pressure;
        calculation.set(c);
    }
    /** Receives instantaneous validated OEM outputs, never the peak/total log fields. */
    public synchronized void computed(float power,float torque,float boostKpa){
        writeSource="derived";
        Calculation c=calculation.get();calculation.remove();
        if(c==null||!c.valid||c.epoch!=epoch||c.pt!=ptVersion||c.gf!=gfVersion
                ||(c.pt==lastComputedPt&&c.gf==lastComputedGf))return;
        // Matching power also checks the friction sample used by the native calculation.
        // OEM startup/low-RPM policy may independently zero torque and boost.
        if(!close(power,c.power)||!(torque==0||close(torque,c.torque))||!(boostKpa==0||close(boostKpa,c.boost)))return;
        lastComputedPt=c.pt;lastComputedGf=c.gf;
        put(POWER,power,true,c.time);put(TORQUE,torque,true,c.time);put(BOOST,boostKpa/100,true,c.time);
    }
    /** Standalone calculation from received source frames, using the captured OEM formula.
     * No cached OEM app fields, peak values or synthetic receipts are involved. */
    public synchronized void calculateObserved(long now){
        if(!ptValid||!gfValid||!stdSeen||now-ptTime>=STALE_MS||now-gfTime>=STALE_MS||now-stdTime>=STALE_MS)return;
        if(ptVersion==lastComputedPt&&gfVersion==lastComputedGf)return;
        beginComputed(inputRpm,inputTqi,inputAcor,inputStd,inputBoost,inputAmbient);
        Calculation c=calculation.get();if(c==null||!c.valid)return;
        float torque=c.torque,boost=c.boost;
        if(inputRpm!=observedPreviousRpm){
            observedTorqueValid=!(inputRpm<=300||(observedPreviousRpm<inputRpm&&inputRpm<400));
        }
        if(!observedTorqueValid){torque=0;boost=0;}
        observedPreviousRpm=inputRpm;
        computed(c.power,torque,boost);
    }
    public synchronized void wheels(byte[] b, long now) {
        writeSource="nmode";
        for(int i=0;i<4;i++) {
            int raw=b!=null && b.length>=8 ? ((b[2*i]&255)<<8)|(b[2*i+1]&255) : 65535;
            float speed=raw*0.03125f;
            // OEM uses this factor, then rejects aggregate speeds >=511. Apply
            // that range conservatively per wheel; this is not the cluster speed.
            put(i+3,speed,speed<511,now);
        }
    }
}
