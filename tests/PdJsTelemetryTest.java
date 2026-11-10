import com.veloster.vehicle.Telemetry;

public final class PdJsTelemetryTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void value(Telemetry t,int signal,float expected,long now,String message){
        Telemetry.Sample s=t.sample(signal,now);check(s.valid&&Math.abs(s.value-expected)<0.001f,message);
    }
    public static void main(String[] args){
        Telemetry pd=new Telemetry();pd.profile(true,false,false);
        pd.hscanPd(0xc7,8,new byte[]{0,0,100,0,0,0,0,0},1000);
        pd.hscanPd(0xc7,2,new byte[]{0,100,0,0},1100);
        value(pd,0,25,1100,"PD HSCAN RPM path");value(pd,7,50,1100,"PD HSCAN speed path");
        pd.hscanPd(0xc7,0x1d,new byte[]{0,5,0,0,0,0,0,0},1200);
        pd.hscanPd(0xc7,0x1e,new byte[]{0,3,0,0,0,0,0,0},1250);
        check(pd.sample(8,1250).valid&&pd.sample(8,1250).value==103,"PD DCT selector/current gear pairing");
        check(pd.pdProfile()&&!pd.jsProfile()&&pd.pdLayoutProfile(),"PD profile selection");
        check(com.veloster.vehicle.ShiftGuide.clamp(3500,true)==3500&&com.veloster.vehicle.ShiftGuide.clamp(3500,false)==6000,
            "PD-derived RPM range is profile-specific and leaves JSN default intact");
        com.veloster.vehicle.OverviewLayout layout=new com.veloster.vehicle.OverviewLayout();
        check(layout.signalAt(3)==9,"JSN retains its original throttle overview mapping");layout.pdProfile(true);
        check(layout.signalAt(3)==Telemetry.ACCEL_PEDAL&&layout.signalAt(10)==9
            &&layout.signalAt(16)==Telemetry.PD_TCS_ENABLED&&layout.grouped(Telemetry.PD_TCS_ENABLED),
            "PD overview branches select pedal/throttle and PD stability semantics");

        Telemetry js=new Telemetry();js.profile(false,true,false);
        js.hscanJs(0xc7,8,new byte[]{0,0,100,0,0,0,0,0},2000);
        js.hscanJs(0xc7,2,new byte[]{0,100,0,0},2000);
        js.hscanJs(0xc7,0x14,new byte[8],2000);
        check(!js.sample(0,2000).seen&&!js.sample(7,2000).seen&&!js.sample(Telemetry.AMBIENT,2000).seen,
            "JS keeps confirmed OEM RPM, speed and ambient sources authoritative");
        js.rpm(new byte[]{0,0,100,0,0,0,0,0},2100);js.units(new byte[]{2,80,0},2110);js.speed(50,2120);
        value(js,0,25,2120,"JS OEM RPM callback retained");value(js,7,50,2120,"JS OEM speed callback retained");
        value(js,Telemetry.AMBIENT,40,2120,"JS OEM ambient callback retained");
        byte[] coolant=new byte[8];coolant[1]=(byte)176;js.hscanJs(0xc7,9,coolant,2200);
        value(js,2,84,2200,"JS uses PD-baseline coolant mapping");
        js.hscanJs(0xc7,0x5a,new byte[8],2300);
        check(!js.sample(Telemetry.TCS,2300).seen,"JS does not inherit JSN stability decoder");
        check(js.jsProfile()&&!js.pdProfile()&&js.pdLayoutProfile(),"JS has a separate profile with PD layout baseline");
        layout.pdProfile(true);
        check(layout.signalAt(3)==Telemetry.ACCEL_PEDAL&&layout.signalAt(16)==Telemetry.PD_TCS_ENABLED,
            "JS shares the PD layout baseline without using JSN-only controls");
        System.out.println("PASS "+checks+" PD/JS telemetry profile checks");
    }
}
