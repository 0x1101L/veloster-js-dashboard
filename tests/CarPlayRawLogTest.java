import com.veloster.dashboard.debugger.CarPlayRawLog;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public class CarPlayRawLogTest {
    private static void check(boolean b) { if (!b) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        File dir=Files.createTempDirectory("carplay-raw-test").toFile();
        CarPlayRawLog log=new CarPlayRawLog(dir);
        check(log.record(42,"iap2","0x5202",new byte[]{0, 1, (byte)255}).equals("stored"));
        File current=new File(dir,"carplay-raw.log");
        String line=new String(Files.readAllBytes(current.toPath()), StandardCharsets.UTF_8);
        check(line.equals("CARPLAY_RAW v=1 elapsed_ms=42 source=iap2 type=0x5202 original_length=3 stored_length=3 state=complete hex=0001ff\n"));
        long size=current.length();
        check(log.record(43,"invalid source","x",new byte[0]).equals("invalid"));
        check(log.record(43,"iap2","x",new byte[CarPlayRawLog.PAYLOAD_LIMIT+1]).equals("too_large"));
        check(current.length()==size);
        byte[] data=new byte[CarPlayRawLog.PAYLOAD_LIMIT];
        for(int i=0;i<33;i++)check(log.record(i,"iap2","data",data).equals("stored"));
        check(new File(dir,"carplay-raw.previous.log").isFile());
        check(current.length()<=CarPlayRawLog.FILE_LIMIT);
        System.out.println("PASS CarPlay raw records, limits and rotation");
    }
}
