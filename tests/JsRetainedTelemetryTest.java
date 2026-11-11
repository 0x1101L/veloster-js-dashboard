import com.veloster.vehicle.Telemetry;

public class JsRetainedTelemetryTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        Telemetry js=new Telemetry();js.profile(false,true,false);
        js.temperatures(new byte[]{(byte)255,(byte)255},1000);
        check(!js.sample(2,1000).held&&!js.sample(2,1000).valid,"no invented initial value");
        js.temperatures(new byte[]{(byte)100,(byte)120},2000);
        float value=js.sample(2,2000).value;
        js.temperatures(new byte[]{(byte)255,(byte)255},5000);
        Telemetry.Sample s=js.sample(2,6000);
        check(!s.valid&&s.held&&s.value==value,"last valid retained and labeled");
        check(s.ageMs==1000&&s.lastValidAgeMs==4000&&s.lastChangedAgeMs==4000,"receive, valid and change clocks separated");
        js.temperatures(new byte[]{(byte)255,(byte)255},61000);
        check(!js.sample(2,62000).held&&js.sample(2,62000).stale,"invalid does not refresh expiry");
        js.temperatures(new byte[]{(byte)100,(byte)120},63000);
        check(js.sample(2,63000).valid&&!js.sample(2,63000).changed,"recovery of same value");
        js.temperatures(new byte[]{(byte)100,(byte)121},64000);
        check(js.sample(2,64000).changed&&js.sample(2,64500).lastChangedAgeMs==500,"change clock");
        js.profile(false,true,false);js.units(new byte[]{2,70,0},65000);js.speed(25,65000);
        check(js.sample(7,66999).valid&&!js.sample(7,67000).valid,"short fast expiry");
        js.speed(511,67001);js.units(new byte[]{2,70,0},67002);
        check(!js.sample(7,67002).valid,"unit packet cannot resurrect invalid speed");
        js.units(new byte[]{2,70,0},70000);
        byte[] tires=new byte[40];tires[22]=1;tires[29]=35;tires[31]=36;tires[33]=37;tires[35]=38;
        js.additional(tires,70000);
        check(js.sample(Telemetry.AMBIENT,3700000).valid,"ambient has no time expiry");
        for(int i=Telemetry.TPMS_FL;i<=Telemetry.TPMS_RR;i++)check(js.sample(i,3700000).valid,"TPMS has no time expiry");
        js.units(null,3700000);js.additional(null,3700000);
        check(js.sample(Telemetry.AMBIENT,7200000).held,"invalid ambient retains last value");
        for(int i=Telemetry.TPMS_FL;i<=Telemetry.TPMS_RR;i++)check(js.sample(i,7200000).held,"invalid TPMS retains each last value");
        js.clear();check(!js.sample(Telemetry.AMBIENT,7200001).held&&!js.sample(Telemetry.TPMS_FL,7200001).seen,"session clears no-expiry values");
        js.clear();check(!js.sample(2,7200001).held&&!js.sample(2,7200001).seen,"session clear");
        Telemetry pd=new Telemetry();pd.profile(true,false,false);pd.temperatures(new byte[]{100,120},1000);pd.temperatures(new byte[]{(byte)255,(byte)255},2000);
        check(!pd.sample(2,2000).held&&!pd.sample(2,2000).valid&&pd.sample(2,2000).value!=value,"PD unchanged");
        Telemetry jsn=new Telemetry();jsn.profile(false,false,true);jsn.temperatures(new byte[]{100,120},1000);jsn.temperatures(new byte[]{(byte)255,(byte)255},2000);
        check(!jsn.sample(2,2000).held&&!jsn.sample(2,2000).valid,"JSN unchanged");
        System.out.println("PASS JS retained telemetry");
    }
}
