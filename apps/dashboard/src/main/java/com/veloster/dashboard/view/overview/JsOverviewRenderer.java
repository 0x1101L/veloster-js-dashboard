package com.veloster.dashboard.view.overview;

import android.graphics.Canvas;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.vehicle.Telemetry;
import com.veloster.dashboard.vehicle.js.JsGearState;
import com.veloster.dashboard.view.gauge.JsGaugeRenderer;
import com.veloster.i18n.UiText;
import java.util.Locale;

/** JS overview page; receives snapshots and owns no subscriptions. */
public final class JsOverviewRenderer {
    private ThemeSpec theme=ThemeSpec.original();
    private final OverviewCardRenderer cards;

    public JsOverviewRenderer(){this(new ThemeSurface());}
    public JsOverviewRenderer(ThemeSurface surface){cards=new OverviewCardRenderer(surface);}
    public void setTheme(ThemeSpec value){setTheme(value,null);}
    public void setTheme(ThemeSpec value,ThemeResources assets){
        theme=value==null?ThemeSpec.original():value;
        cards.setTheme(theme,assets);
    }

    public void draw(Canvas c,Telemetry.Sample[] samples,OverviewLayout layout,boolean pd,boolean js,
            Telemetry.DctTemperatures dct,Telemetry.JsDriveMode mode,float trial){
        String[] names={"SPEED","ENGINE",pd||js?"GEAR":"EMS GEAR",pd||js?"ACCEL":"THROTTLE","BRAKE"};
        for(int slot=0;slot<layout.count();slot++){
            int i=layout.signalAt(slot);
            if(OverviewLayout.dctCard(i,js)){
                cards.dctTemperature(c,layout,slot,dct,dctValue(dct.oddC),dctValue(dct.evenC));
            }else if(js&&i==Telemetry.CLUSTER_FUEL){
                cards.fuelEconomy(c,layout,slot,Float.isNaN(trial)?"—":String.format(Locale.US,"%.1f km/L",trial),
                    fuelValue(samples[Telemetry.CLUSTER_FUEL],"%.1f km/L"),fuelValue(samples[Telemetry.CLUSTER_RANGE],"%.0f km"));
            }else if(i==Telemetry.TPMS_FL){
                cards.tirePressure(c,layout,slot,new String[]{value(samples,i),value(samples,i+1),value(samples,i+2),value(samples,i+3)},false);
            }else if(i==Telemetry.TCS||i==Telemetry.PD_TCS_ENABLED){
                cards.stability(c,layout,slot,i,samples);
            }else{
                boolean headline=layout.headline(i);
                String unit=Telemetry.UNITS[i];
                Telemetry.Sample sample=samples[i];
                boolean valid=sample.valid&&!Float.isNaN(sample.value)&&!Float.isInfinite(sample.value);
                boolean position=valid&&(i!=8||(sample.value>=1&&sample.value<=6));
                int color=sample.changed?(theme.overview()==null?0xffe9f7ff:theme.textPrimary):theme.accent;
                float lo=JsGaugeRenderer.min(i,unit),hi=JsGaugeRenderer.max(i,unit);
                float fraction=position?Math.max(0,Math.min(1,(Telemetry.displayValue(i,sample.value)-lo)/(hi-lo))):0;
                cards.standard(c,layout,slot,headline?names[slot]:UiText.t(Telemetry.LABELS[i]),
                    value(samples,i),unit,color,headline&&i==0?rpmAccent(js,mode):theme.accent,layout.style(i),position,
                    fraction,lo<0?-lo/(hi-lo):0);
            }
        }
    }
    private static String value(Telemetry.Sample[] samples,int i){
        Telemetry.Sample sample=samples[i];
        if(!(sample.valid||(i!=8&&sample.hasDisplayValue())))return "—";
        if(i==8)return JsGearState.valueLabel((int)sample.value);
        if(Telemetry.jsTenthSignal(i))return Telemetry.jsTenth(sample.value,i==11||i==12);
        return String.format(Locale.US,i==0||i==7||(i>=Telemetry.TPMS_FL&&i<=Telemetry.BAT_SOC)||i==Telemetry.STEERING_SPEED?"%.0f":
            i==11||i==12?"%+.2f":i==13?"%+.1f":i==17?"%.2f":"%.1f",Telemetry.displayValue(i,sample.value));
    }

    private int rpmAccent(boolean js,Telemetry.JsDriveMode mode){
        if(!js||mode==Telemetry.JsDriveMode.SPORT)return theme.critical;
        return mode==Telemetry.JsDriveMode.ECO?0xff70d991:theme.accent;
    }
    private static String fuelValue(Telemetry.Sample sample,String format){
        return sample.hasDisplayValue()?String.format(Locale.US,format,sample.value):"—";
    }
    private static String dctValue(float temperature){
        return Float.isNaN(temperature)?"—":String.format(Locale.US,"%.0f °C",temperature);
    }
}
