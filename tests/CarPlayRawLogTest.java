import com.veloster.dashboard.debugger.CarPlayRawLog;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

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
        ByteArrayOutputStream capture=new ByteArrayOutputStream();DataOutputStream raw=new DataOutputStream(capture);
        raw.write("CPRAW1\r\n".getBytes("US-ASCII"));raw.write("I2RX".getBytes("US-ASCII"));raw.writeInt(1);raw.writeLong(123);raw.writeInt(3);raw.write(new byte[]{0,1,(byte)255});raw.write("DROP".getBytes("US-ASCII"));raw.writeInt(0);raw.writeInt(0);raw.close();
        byte[] original=capture.toByteArray();File imported=log.importCapture(new ByteArrayInputStream(original),123456);
        check(imported.getParentFile().getName().equals("carplay-raw"));
        check(java.util.Arrays.equals(original,Files.readAllBytes(imported.toPath())));
        check(log.latestCaptureSummary().contains(imported.getName()));
        boolean rejected=false;try{log.importCapture(new ByteArrayInputStream(new byte[]{1,2,3}),123457);}catch(java.io.IOException expected){rejected=true;}
        check(rejected);
        ByteArrayOutputStream dual=new ByteArrayOutputStream();DataOutputStream dualRaw=new DataOutputStream(dual);
        dualRaw.write("CPRAW1\r\n".getBytes("US-ASCII"));dualRaw.write("RDRX".getBytes("US-ASCII"));dualRaw.writeInt(1);dualRaw.writeLong(124);dualRaw.writeInt(1);dualRaw.writeByte(9);dualRaw.write("DROP".getBytes("US-ASCII"));dualRaw.writeInt(0);dualRaw.writeInt(0);dualRaw.close();
        check(log.importCapture(new ByteArrayInputStream(dual.toByteArray()),123458).isFile());
        byte[] oversized=new byte[CarPlayRawLog.PAYLOAD_LIMIT+1];
        boolean sizeRejected=false;try{log.importCapture(new ByteArrayInputStream(oversized),123459);}catch(java.io.IOException expected){sizeRejected=true;}
        check(sizeRejected);
        System.out.println("PASS CarPlay raw records, limits and rotation");
    }
}
