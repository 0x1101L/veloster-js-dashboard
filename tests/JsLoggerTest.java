import android.util.Log;
import com.veloster.dashboard.debugger.JsDiagState;
import com.veloster.dashboard.debugger.JsLogger;
import com.veloster.dashboard.vehicle.Telemetry;

public class JsLoggerTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Telemetry telemetry=new Telemetry();telemetry.profile(false,true,false);
        telemetry.temperatures(new byte[]{100,120},990);
        telemetry.temperatures(new byte[]{(byte)255,(byte)255},995);
        telemetry.speed(511,996);
        JsDiagState diagnostic=new JsDiagState();
        diagnostic.event("binding.nmode","registered",980);
        diagnostic.event("binding.nmode","received",990);
        diagnostic.event("binding.media.0","received",991);
        diagnostic.event("binding.media.0","processed",992);
        diagnostic.event("binding.media.0","invalid",993);
        diagnostic.event("navigation.route.source","sticky",985);
        diagnostic.event("navigation.route_state","1",990);
        diagnostic.event("navigation.observer","registered",980);
        diagnostic.event("navigation.notification","received",990);
        JsLogger.emit(1000,telemetry,diagnostic.snapshot(1000));
        String oil=null,speed=null,route=null,missing=null,notice=null;
        for(String line:Log.lines){
            if(line.contains("name=oil_temp "))oil=line;
            if(line.contains("name=speed "))speed=line;
            if(line.contains("name=route_status "))route=line;
            if(line.contains("name=brake_pressure "))missing=line;
            if(line.contains("name=navigation_notice "))notice=line;
        }
        check(oil!=null&&oil.contains("v=1 ")&&oil.contains("source=nmode ")&&oil.contains("status=held "),"held telemetry records known source and status");
        check(oil.contains("received_ms=995 ")&&oil.contains("valid_ms=990 ")&&oil.contains("count=2"),"separate receive and valid clocks");
        check(speed!=null&&speed.contains("source=speed ")&&speed.contains("status=invalid ")&&speed.contains("value=na "),"invalid input does not export previous value");
        check(missing!=null&&missing.contains("status=missing ")&&missing.contains("source=unknown ")&&missing.contains("count=0"),"missing source is not inferred");
        check(route!=null&&route.contains("source=sticky ")&&route.contains("status=valid ")&&route.contains("value=1 "),"diagnostic source and received value");
        check(notice!=null&&notice.contains("source=navigation_provider "),"content observer notice identifies provider");
        boolean missingBinding=false;for(String line:Log.lines)if(line.contains("name=binding_bcm ")&&line.contains("status=unknown ")&&line.contains("count=0"))missingBinding=true;
        check(missingBinding,"known but unobserved binding remains unknown");
        boolean nmodeBinding=false,mediaBinding=false;
        for(String line:Log.lines){
            if(line.contains("name=binding_nmode ")&&line.contains("status=registered ")&&line.contains("received_ms=990 ")&&line.contains("count=1"))
                nmodeBinding=true;
            if(line.contains("name=binding_media_0 ")&&line.contains("source=media.0 ")&&line.contains("received_ms=991 ")&&line.contains("count=1"))
                mediaBinding=true;
        }
        check(nmodeBinding&&mediaBinding,"binding records retain each source's registration and reception");
        boolean mediaCounters=false;for(String line:Log.lines)if(line.contains("name=binding_media_0_processed ")&&line.contains("status=observed ")&&line.contains("value=1 "))mediaCounters=true;
        check(mediaCounters,"retained receiver processing counters exported without inventing a receive time");
        check(!Log.lines.toString().contains("100,120")&&!Log.lines.toString().contains("destination="),"no raw payload or destination content");
        telemetry.units(new byte[]{0,100,0},1000);telemetry.speed(22,1001);JsLogger.emit(1002,telemetry,diagnostic.snapshot(1002));
        String validSpeed=null;for(String line:Log.lines)if(line.contains("name=speed ")&&line.contains("t_ms=1002 "))validSpeed=line;
        check(validSpeed!=null&&validSpeed.contains("status=valid ")&&validSpeed.contains("value=22.0 "),"valid current value is exported");
        Log.lines.clear();JsLogger.emit(70000,telemetry,diagnostic.snapshot(70000));
        String expired=null;for(String line:Log.lines)if(line.contains("name=oil_temp "))expired=line;
        check(expired!=null&&expired.contains("status=expired ")&&expired.contains("value=na "),"expired values are withheld");
        Telemetry mixed=new Telemetry();mixed.profile(false,true,false);
        mixed.temperatures(new byte[]{100,120},100);
        Telemetry.Sample before=mixed.snapshot(101)[1];
        mixed.hscanJs(0xc7,0x0d,new byte[]{0,0,0,0,(byte)255,0,0,0},102);
        Telemetry.Sample held=mixed.snapshot(103)[1];
        check(before.valid&&before.source.equals("nmode")&&before.value==35f,"snapshot retains value and source after later writes");
        check(held.held&&held.source.equals("nmode")&&held.value==35f,"held value retains last valid source after invalid other source");
        Log.lines.clear();JsLogger.emit(103,mixed,diagnostic.snapshot(103));
        boolean heldSource=false;for(String line:Log.lines)if(line.contains("name=oil_temp ")&&line.contains("source=nmode ")&&line.contains("status=held "))heldSource=true;
        check(heldSource,"logger emits the held value source from the same snapshot");
        System.out.println("PASS JS_LOGGER v"+JsLogger.CONTRACT_VERSION+" export contract");
    }
}
