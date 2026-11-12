package com.veloster.dashboard.view.overview;

import com.veloster.dashboard.vehicle.*;

import com.veloster.dashboard.vehicle.js.JsGearState;

/** Fixed overview geometry and per-signal display choices, separate from gauges. */
public final class OverviewLayout {
    private static final int[] ORDER={7,0,8,9,10,1,2,29,14,13,15,16,17,11,12,18,22,26,25,27};
    private final int[] styles=new int[Telemetry.LABELS.length];
    private boolean pd;
    private static void check(int i){if(i<0||i>=Telemetry.LABELS.length)throw new IllegalArgumentException("overview signal");}
    public OverviewLayout(){for(int i=0;i<styles.length;i++)styles[i]=2;}
    public void pdProfile(boolean value){pd=value;}
    public int count(){return ORDER.length;}
    public int signalAt(int slot){if(slot<0||slot>=ORDER.length)throw new IllegalArgumentException("overview slot");return pd&&slot==3?Telemetry.ACCEL_PEDAL:pd&&slot==10?9:pd&&slot==16?Telemetry.PD_TCS_ENABLED:ORDER[slot];}
    private int slot(int signal){check(signal);for(int i=0;i<ORDER.length;i++)if(signalAt(i)==signal)return i;throw new IllegalArgumentException("overview signal");}
    public int style(int signal){check(signal);return styles[signal];}
    public void style(int signal,int value){check(signal);if(value<0||value>2)throw new IllegalArgumentException("overview style");styles[signal]=value;}
    /** JS reuses the oil card's geometry, while oil telemetry and its preferences keep their own ID. */
    public static boolean dctCard(int signal,boolean js){return js&&signal==1;}
    /** Number -> bar -> arc -> number; style IDs match the gauge renderer. */
    public boolean grouped(int signal){return signal==Telemetry.TPMS_FL||signal==Telemetry.TCS||signal==Telemetry.PD_TCS_ENABLED;}
    public void cycle(int signal){if(grouped(signal))return;style(signal,style(signal)==2?1:style(signal)==1?0:2);}
    public boolean headline(int signal){return slot(signal)<5;}
    public float left(int signal){int n=slot(signal);return 18+(n%5)*155;}
    public float top(int signal){int n=slot(signal);return 58+(n/5)*104;}
    public float width(int signal){int n=slot(signal);return 147;}
    public float height(int signal){return 98;}
    /** Inter-card gaps and the navigation/header are not touch targets. */
    public int hit(float x,float y){
        for(int slot=0;slot<ORDER.length;slot++){int signal=signalAt(slot);float l=left(signal),t=top(signal);if(x>=l&&x<l+width(signal)&&y>=t&&y<t+height(signal))return signal;}
        return -1;
    }
}
