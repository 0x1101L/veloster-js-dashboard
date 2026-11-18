package com.veloster.dashboard.view.overview;

import android.graphics.Canvas;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.vehicle.Telemetry;
import com.veloster.dashboard.view.gauge.GaugeRenderer;
import com.veloster.i18n.UiText;
import java.util.Locale;

/** Overview page for PD and JSN Dashboard profiles. */
public final class OverviewRenderer {
    private ThemeSpec theme=ThemeSpec.original();
    private final OverviewCardRenderer cards;

    public OverviewRenderer(){this(new ThemeSurface());}
    public OverviewRenderer(ThemeSurface surface){cards=new OverviewCardRenderer(surface);}
    public void setTheme(ThemeSpec value){setTheme(value,null);}
    public void setTheme(ThemeSpec value,ThemeResources assets){
        theme=value==null?ThemeSpec.original():value;
        cards.setTheme(theme,assets);
    }

    public void draw(Canvas c,Telemetry.Sample[] samples,OverviewLayout layout,boolean pd,boolean js){
        String[] names={"SPEED","ENGINE",pd||js?"GEAR":"EMS GEAR",pd||js?"ACCEL":"THROTTLE","BRAKE"};
        for(int slot=0;slot<layout.count();slot++){
            int i=layout.signalAt(slot);
            if(i==Telemetry.TPMS_FL){
                cards.tirePressure(c,layout,slot,new String[]{value(samples,i),value(samples,i+1),value(samples,i+2),value(samples,i+3)},true);
            }else if(i==Telemetry.TCS||i==Telemetry.PD_TCS_ENABLED){
                cards.stability(c,layout,slot,i,samples);
            }else{
                boolean headline=layout.headline(i);
                String unit=Telemetry.UNITS[i];
                Telemetry.Sample sample=samples[i];
                boolean valid=sample.valid&&!Float.isNaN(sample.value)&&!Float.isInfinite(sample.value);
                boolean position=valid&&(i!=8||(sample.value>=1&&sample.value<=6));
                int color=sample.changed?(theme.overview()==null?0xffe9f7ff:theme.textPrimary):theme.accent;
                float lo=GaugeRenderer.min(i,unit),hi=GaugeRenderer.max(i,unit);
                float fraction=position?Math.max(0,Math.min(1,(Telemetry.displayValue(i,sample.value)-lo)/(hi-lo))):0;
                cards.standard(c,layout,slot,headline?names[slot]:UiText.t(Telemetry.LABELS[i]),
                    (valid?value(samples,i):"—"),unit,color,headline&&i==0?theme.critical:theme.accent,layout.style(i),position,
                    fraction,lo<0?-lo/(hi-lo):0);
            }
        }
    }
    private static String value(Telemetry.Sample[] samples,int i){
        Telemetry.Sample sample=samples[i];
        if(i>=Telemetry.CLUSTER_FUEL||!sample.valid)return "—";
        if(i==8)return Telemetry.gearLabel((int)sample.value);
        return String.format(Locale.US,i==0||i==7||(i>=Telemetry.TPMS_FL&&i<=Telemetry.BAT_SOC)||i==Telemetry.STEERING_SPEED?"%.0f":
            i==11||i==12?"%+.2f":i==13?"%+.1f":i==17||i==Telemetry.BAT_VOLTAGE?"%.2f":"%.1f",Telemetry.displayValue(i,sample.value));
    }
}
