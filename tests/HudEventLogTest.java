import com.veloster.dashboard.debugger.HudEventLog;
import java.io.File;
import java.nio.file.Files;

public class HudEventLogTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        File dir=Files.createTempDirectory("hud-event-test").toFile();
        HudEventLog log=new HudEventLog(dir);
        check(log.record(1,"navigation.route_state","1"));
        check(log.record(2,"navigation.route_state","1"));
        check(log.record(3,"navigation.route_state","-1"));
        check(log.record(4,"private.destination","secret address"));
        check(log.record(5,"navigation.route_state","secret"));
        String data=new String(Files.readAllBytes(new File(dir,"hud-events.log").toPath()),"UTF-8");
        check(data.split("\n").length==2 && !data.contains("secret"));
        check(log.record(6,"navigation.route.observer","registered"));
        check(log.record(7,"navigation.route.stopped","stopped"));
        check(log.record(8,"navigation.route.observer","registered"));
        data=new String(Files.readAllBytes(new File(dir,"hud-events.log").toPath()),"UTF-8");
        check(data.split("\n").length==5);
        check(log.record(9,"binding.media.3","registered"));
        for(int i=0;i<5;i++)check(log.record(10+i,"binding.media.3","received"));
        check(log.record(15,"binding.media.3","processed"));
        check(log.record(16,"binding.media.3","private track title"));
        check(log.record(17,"binding.media.3","stopped"));
        check(log.record(18,"carplay.media.mode","active"));
        check(log.record(19,"carplay.media.playback","playing"));
        check(log.record(20,"carplay.media.playback","private track title"));
        data=new String(Files.readAllBytes(new File(dir,"hud-events.log").toPath()),"UTF-8");
        check(data.contains("value=received_1")&&data.contains("value=received_2")&&data.contains("value=received_4")&&!data.contains("value=received_3")&&!data.contains("value=received_5"));
        check(data.contains("value=processed_1")&&data.contains("value=stopped")&&data.contains("value=playing")&&data.contains("value=active")&&!data.contains("private track title"));
        for(int i=0;i<2500;i++)check(log.record(10+i,"navigation.route_state",String.valueOf(i)));
        check(new File(dir,"hud-events.log").length()<=HudEventLog.LIMIT);
        check(new File(dir,"hud-events.previous.log").length()<=HudEventLog.LIMIT);
        HudEventLog failed=new HudEventLog(new File(dir,"hud-events.log/invalid"));
        check(!failed.record(1,"navigation.route_state","1"));
        System.out.println("PASS bounded HUD event log");
    }
}
