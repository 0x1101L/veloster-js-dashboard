import com.veloster.dashboard.vehicle.Telemetry;
import com.veloster.dashboard.view.overview.OverviewLayout;

public class JsFuelEconomyTest {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        Telemetry t=new Telemetry();t.profile(false,true,false);
        byte[] frame={(byte)0x80,0x20,0,0x71,0x01,0x10,0,0};
        t.hscanJs(0xc7,0x04,frame,1000);
        check(t.sample(Telemetry.CLUSTER_FUEL,1000).valid&&Math.abs(t.sample(Telemetry.CLUSTER_FUEL,1000).value-13.0f)<0.01,"average decode");
        check(t.sample(Telemetry.CLUSTER_RANGE,1000).valid&&t.sample(Telemetry.CLUSTER_RANGE,1000).value==369,"range decode");
        frame[0]=0;frame[1]=0x21;t.hscanJs(0xc7,0x04,frame,1100);
        check(Math.abs(t.sample(Telemetry.CLUSTER_FUEL,1100).value-13.2f)<0.01,"confirmed 13.2");
        frame[0]=(byte)0xc0;frame[1]=(byte)0xff;frame[3]=(byte)0xff;frame[4]=3;t.hscanJs(0xc7,0x04,frame,1200);
        check(!t.sample(Telemetry.CLUSTER_FUEL,1200).valid&&!t.sample(Telemetry.CLUSTER_RANGE,1200).valid,"invalid sentinel");
        t.clear();frame[0]=8;frame[1]=0x21;frame[3]=0x71;frame[4]=1;t.hscanJs(0xc7,0x04,frame,2000);
        check(!t.sample(Telemetry.CLUSTER_FUEL,2000).valid&&t.sample(Telemetry.CLUSTER_RANGE,2000).valid,"unknown unit");
        t.clear();t.hscanJs(0xc7,0x04,new byte[7],3000);check(!t.sample(Telemetry.CLUSTER_FUEL,3000).seen,"short frame");
        t.clear();t.units(new byte[]{0,0,0},4000);t.speed(60,4000);
        byte[] trial={ (byte)130,0,0,0,0,0 };
        t.hscanJs(0xc7,0x22,trial,4000);t.speed(60,5000);t.hscanJs(0xc7,0x22,trial,5000);
        check(Math.abs(t.jsFuelTrial(5000)-60f/(130*0.04608f))<0.01,"trial integration");
        t.clear();t.units(new byte[]{0,0,0},6000);t.speed(60,6000);
        byte[] low={50,0,0,0,0,0},high={100,0,0,0,0,0};
        t.hscanJs(0xc7,0x22,low,6000);t.hscanJs(0xc7,0x22,high,6000);t.speed(60,7000);t.hscanJs(0xc7,0x22,low,7000);
        check(Math.abs(t.jsFuelTrial(7000)-60f/(75*0.04608f))<0.01,"same batch average");
        check(Float.isNaN(t.jsFuelTrial(9000)),"trial expires");
        t.speed(60,10000);t.hscanJs(0xc7,0x22,trial,10000);check(Float.isNaN(t.jsFuelTrial(10000)),"gap resets");
        t.clear();check(Float.isNaN(t.jsFuelTrial(10100)),"ignition reset");
        OverviewLayout layout=new OverviewLayout();layout.pdProfile(true);layout.jsProfile(true);check(layout.signalAt(18)==Telemetry.CLUSTER_FUEL,"SOC position");
        layout.jsProfile(false);check(layout.signalAt(18)==Telemetry.BAT_SOC,"other profiles unchanged");
        check(Telemetry.CLUSTER_RANGE==Telemetry.LABELS.length-1,"range last in selectable list");
        System.out.println("PASS JS cluster fuel and range");
    }
}
