"""Exercise the real HSCAN client using a local socket double, never a vehicle."""
from pathlib import Path
import subprocess, tempfile, unittest
from test_quick_entry_runtime import java_tools
ROOT=Path(__file__).resolve().parents[1]
STUBS={
'android/os/Build.java':'''package android.os; public class Build {public static String PRODUCT="js_kr";public static class VERSION {public static String INCREMENTAL="eng.vmadmin.20260114.090656";}}''',
'android/os/SystemClock.java':'''package android.os;public class SystemClock {public static long elapsedRealtime(){return 100;}}''',
'android/net/LocalSocketAddress.java':'''package android.net;public class LocalSocketAddress {public enum Namespace {ABSTRACT} public LocalSocketAddress(String s,Namespace n){}}''',
'android/net/LocalSocket.java':'''package android.net;import java.io.*;public class LocalSocket {
 public static volatile int connects;public static volatile boolean fail,deny;private volatile boolean closed;
 public void connect(LocalSocketAddress a)throws IOException{connects++;if(deny)throw new SecurityException();if(fail)throw new IOException();}public void setSoTimeout(int n){}public void close() throws IOException {closed=true;}
 public InputStream getInputStream(){return new InputStream(){boolean sent;public int read(){return -1;}public int read(byte[] b)throws IOException{if(!sent){sent=true;byte[] f=new byte[264];int[] h={255,11,142,1,139,199,1,0};for(int i=0;i<h.length;i++)f[i]=(byte)h[i];f[8]=(byte)0xc7;f[9]=1;f[11]=0;f[12]=8;int x=0;for(int i=0;i<263;i++)x^=f[i]&255;f[263]=(byte)x;System.arraycopy(f,0,b,0,264);return 264;}while(!closed){try{Thread.sleep(10);}catch(InterruptedException e){throw new IOException();}}return -1;}};}}
''',
'com/veloster/dashboard/view/DashboardView.java':'''package com.veloster.dashboard.view;public class DashboardView {public static volatile int received;public static volatile String status="";public static void diagEvent(String n,String d){if(n.equals("binding.hscan")){if(d.equals("received"))received++;else status=d;}} public static void diagHscan(int n){}public static void hscanJs(int a,int b,byte[] c,long d){}public static void hscanPd(int a,int b,byte[] c,long d){}public static void hscanJsn(int a,int b,byte[] c,long d){}}''',
}
HARNESS='''package com.veloster.dashboard.vehicle;import android.net.LocalSocket;import android.os.Build;import com.veloster.dashboard.view.DashboardView;
public class HscanHarness {static void check(boolean b,String m){if(!b)throw new AssertionError(m);}static void waitFor(String s)throws Exception{long end=System.currentTimeMillis()+2000;while(!DashboardView.status.equals(s)&&System.currentTimeMillis()<end)Thread.sleep(5);check(DashboardView.status.equals(s),"status "+s+" got "+DashboardView.status);}
 public static void main(String[] a)throws Exception{
  HscanDataClient c=new HscanDataClient(new HscanDataClient.Gate(){public boolean accepts(){return true;}});LocalSocket.fail=true;c.start();waitFor("error");c.stop();waitFor("stopped");
  LocalSocket.fail=false;LocalSocket.deny=true;c.start();waitFor("permission denied");c.stop();waitFor("stopped");LocalSocket.deny=false;int before=LocalSocket.connects;c.start();c.start();waitFor("connected");long end=System.currentTimeMillis()+2000;while(DashboardView.received==0&&System.currentTimeMillis()<end)Thread.sleep(5);check(DashboardView.received==1,"frame received");check(LocalSocket.connects==before+1,"duplicate start");c.stop();waitFor("stopped");int count=DashboardView.received;Thread.sleep(50);check(DashboardView.received==count,"no delivery after stop");
  Build.PRODUCT="unsupported";new HscanDataClient(new HscanDataClient.Gate(){public boolean accepts(){return true;}}).start();check(DashboardView.status.equals("unsupported"),"unsupported profile");
 }}'''
class HscanClientTests(unittest.TestCase):
 def test_lifecycle(self):
  java,javac=java_tools()
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp);sources=[]
   for name,body in STUBS.items():
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);sources.append(p)
   p=root/'com/veloster/dashboard/HscanHarness.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(HARNESS);sources.append(p)
   sources += [ROOT/'apps/dashboard/src/main/java/com/veloster/dashboard/vehicle'/n for n in ('HscanDataClient.java','HscanFrameDecoder.java','HscanProfile.java')]
   result=subprocess.run([javac,'-d',str(root),*map(str,sources)],capture_output=True,text=True);self.assertEqual(result.returncode,0,result.stderr)
   result=subprocess.run([java,'-cp',str(root),'com.veloster.dashboard.vehicle.HscanHarness'],capture_output=True,text=True,timeout=10);self.assertEqual(result.returncode,0,result.stderr)
