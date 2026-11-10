package com.veloster.vehicle;

/** PD DCT7 selector and current-gear receipts; no Android, polling or inferred ratios. */
public final class PdGearState {
    public static final int P=-1,R=-2,N=-3,D=-4,M=-5;
    public static final long PAIR_MAX_AGE_MS=1000;
    public static final class Result {
        public final boolean updated,valid;
        public final float value;
        public final long time;
        private Result(boolean updated,boolean valid,float value,long time){
            this.updated=updated;this.valid=valid;this.value=value;this.time=time;
        }
    }
    private boolean selectorSeen,currentSeen;
    private int selector=-1,current=-1;
    private long selectorTime,currentTime,selectorEpoch,lastReceived=-1;

    public void clear(){
        selectorSeen=false;currentSeen=false;selector=-1;current=-1;
        selectorTime=0;currentTime=0;selectorEpoch=0;lastReceived=-1;
    }

    public Result receive(int function,byte[] data,long now){
        if((function!=0x1d&&function!=0x1e)||data==null||data.length!=8
                ||now<0||now<lastReceived)return new Result(false,false,0,now);
        lastReceived=now;
        int raw=data[1]&15;
        if(function==0x1d){
            if(!selectorSeen||selector!=raw){
                // A current gear from before P/R/N/D/M changed cannot cross that boundary.
                currentSeen=false;current=-1;selectorEpoch=now;
            }
            selector=raw;selectorTime=now;selectorSeen=true;
        }else{
            current=raw;currentTime=now;currentSeen=true;
        }
        if(!selectorSeen||selectorTime>now)return new Result(true,false,0,now);
        if(selector==0)return new Result(true,true,P,selectorTime);
        if(selector==7)return new Result(true,true,R,selectorTime);
        if(selector==6)return new Result(true,true,N,selectorTime);
        boolean manual=selector==4||selector==8||selector==12;
        if(selector!=5&&!manual)return new Result(true,false,0,selectorTime);
        if(currentSeen&&currentTime>=selectorEpoch&&currentTime<=now
                &&now-selectorTime<=PAIR_MAX_AGE_MS&&now-currentTime<=PAIR_MAX_AGE_MS
                &&current>=1&&current<=7){
            return new Result(true,true,100+current,Math.min(selectorTime,currentTime));
        }
        // D/M is known from the selector alone; unrelated gear receipts cannot refresh it.
        return new Result(true,true,manual?M:D,selectorTime);
    }
}
