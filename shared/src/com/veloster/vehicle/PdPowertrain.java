package com.veloster.vehicle;

/** PD-only received-source calculations. No Android, polling, commands or vehicle constants. */
public final class PdPowertrain {
    // The observed PD standard-torque multiplex interval reaches 1868ms while
    // its value remains350Nm. Dynamic ACOR/friction/RPM still share one receipt.
    private static final long STANDARD_MAX_AGE_MS=3000,PRESSURE_MAX_AGE_MS=1000;
    private static final double MMHG_TO_HPA=1.33322387415;
    private boolean standardSeen,ambientSeen;
    private float standardNm;
    private double ambientMmHg;
    private long standardAt,ambientAt,lastAt=-1;

    public static final class Result {
        public final boolean torqueUpdated,torqueValid,boostUpdated,boostValid;
        public final float torqueNm,powerPs,boostBar;
        public final long torqueAt,boostAt;
        private Result(boolean tu,boolean tv,float torque,float power,long tt,
                       boolean bu,boolean bv,float boost,long bt){
            torqueUpdated=tu;torqueValid=tv;torqueNm=torque;powerPs=power;torqueAt=tt;
            boostUpdated=bu;boostValid=bv;boostBar=boost;boostAt=bt;
        }
    }
    private static final Result NONE=new Result(false,false,0,0,0,false,false,0,0);
    private static Result torque(boolean valid,float value,float power,long at){
        return new Result(true,valid,value,power,at,false,false,0,0);
    }
    private static Result boost(boolean valid,float value,long at){
        return new Result(false,false,0,0,0,true,valid,value,at);
    }
    public synchronized void clear(){
        standardSeen=false;ambientSeen=false;standardNm=0;ambientMmHg=0;
        standardAt=0;ambientAt=0;lastAt=-1;
    }
    private static boolean pair(long received,long source,long maximumAge){
        return source<=received&&received-source<=maximumAge;
    }
    /** Results are published only on new RPM/pressure receipts or source invalidation.
     * Source flags, raw31/63/255/4095 suppression and the three-second standard
     * torque / one-second pressure windows are
     * conservative dashboard policies, not a complete OEM validity dictionary. */
    public synchronized Result receive(int function,byte[] b,long now){
        if(b==null||b.length!=8||(function!=8&&function!=9&&function!=0x0a&&function!=0x0d))return NONE;
        // Do not let a delayed/future-relative input resurrect or backdate a pair.
        if(now<0||now<lastAt){
            if(function==9){standardSeen=false;return torque(false,0,0,now);}
            if(function==0x0a){ambientSeen=false;return boost(false,0,now);}
            return function==8?torque(false,0,0,now):boost(false,0,now);
        }
        lastAt=now;
        if(function==9){
            if(((b[0]&255)>>6)!=3)return NONE;
            int raw=b[0]&63;
            if(raw==0||raw==63){standardSeen=false;return torque(false,0,0,now);}
            standardNm=raw*10;standardAt=now;standardSeen=true;
            return NONE; // Other mux traffic never refreshes this timestamp.
        }
        if(function==0x0a){
            int raw=((b[0]&255)>>3)&31;
            if(raw==31){ambientSeen=false;return boost(false,0,now);}
            ambientMmHg=raw*10.731613+458.98;ambientAt=now;ambientSeen=true;
            return NONE;
        }
        if(function==8){
            int rpmRaw=(b[2]&255)|((b[3]&255)<<8),acor=b[1]&255,friction=b[5]&255;
            if(!standardSeen||!pair(now,standardAt,STANDARD_MAX_AGE_MS)||(b[0]&0xb2)!=0
                    ||rpmRaw==65535||acor==255||friction==255)return torque(false,0,0,now);
            // ACOR and friction are percentages of the same received standard torque.
            float net=(float)((acor-friction)*0.390625*standardNm/100.0);
            float power=(float)(net*(rpmRaw*0.25)*2*Math.PI/60/735.49875);
            return torque(true,net,power,Math.min(now,standardAt));
        }
        int raw=((b[2]&255)>>4)|((b[3]&255)<<4);
        if(!ambientSeen||!pair(now,ambientAt,PRESSURE_MAX_AGE_MS)||raw==4095)return boost(false,0,now);
        float gauge=(float)((raw*1.322-ambientMmHg*MMHG_TO_HPA)/1000);
        return boost(true,gauge,Math.min(now,ambientAt));
    }
}
