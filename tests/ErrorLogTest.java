import com.veloster.diagnostics.ErrorLog;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ErrorLogTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static String read(File file)throws Exception{return new String(Files.readAllBytes(file.toPath()),"UTF-8");}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("vn-errors-");
  try{
   File dir=root.resolve("private").toFile();final int[] forwarded={0};final Throwable error=new IllegalStateException("SECRET media title / VIN");
   ErrorLog log=new ErrorLog(dir,new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread t,Throwable e){check(e==error,"original error delegated");forwarded[0]++;}});
   error.initCause(new IOException("SECRET cause message"));log.uncaughtException(Thread.currentThread(),error);
   String first=read(new File(dir,"errors.log"));check(first.startsWith("VN-ERROR 1 ")&&first.contains("IllegalStateException")&&first.contains("IOException"),"exception types persist");check(!first.contains("SECRET")&&!first.contains("VIN"),"messages excluded");check(forwarded[0]==1,"original handler called once");
   for(int i=0;i<1000;i++)log.write("retry",error);
   File current=new File(dir,"errors.log"),old=new File(dir,"errors.previous.log");
   check(current.length()>0&&current.length()<=ErrorLog.LIMIT&&old.length()>0&&old.length()<=ErrorLog.LIMIT,"two bounded files");check(dir.list().length==2,"bounded file count");
   ErrorLog reopened=new ErrorLog(dir,null);reopened.write("after_restart",error);check(read(current).contains("after_restart"),"survives logger recreation");
   File blocked=root.resolve("not-a-directory").toFile();Files.write(blocked.toPath(),new byte[]{1});ErrorLog failed=new ErrorLog(blocked,new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread t,Throwable e){forwarded[0]++;}});failed.uncaughtException(Thread.currentThread(),error);check(forwarded[0]==2,"logging failure still delegates");
   Throwable huge=new Exception("SECRET");StackTraceElement[] frames=new StackTraceElement[1000];Arrays.fill(frames,new StackTraceElement("Example","method","file",42));huge.setStackTrace(frames);long before=current.length();reopened.write("bounded_stack",huge);check(current.length()-before<2000,"stack frame count bounded");
   System.out.println("PASS: error logging, rotation, privacy, restart and failure delegation");
  }finally{Files.walk(root).sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}
 }
}
