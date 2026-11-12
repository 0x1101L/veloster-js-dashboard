"""Exercise passive media receiver registration against deterministic Android doubles."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
'android/content/BroadcastReceiver.java': '''package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}''',
'android/content/IntentFilter.java': '''package android.content; public class IntentFilter {public void addAction(String a){}}''',
'android/content/Intent.java': '''package android.content; public class Intent {String action; public boolean malformed; public Intent(String a){action=a;} public String getAction(){return action;} public boolean hasExtra(String n){return "filename".equals(n);} public String getStringExtra(String n){if(malformed)throw new IllegalArgumentException();return "track";} public long getLongExtra(String n,long d){return d;} public boolean getBooleanExtra(String n,boolean d){return d;} public int getIntExtra(String n,int d){return d;} public byte[] getByteArrayExtra(String n){return null;}}''',
'android/content/Context.java': '''package android.content; public class Context {public BroadcastReceiver current;public int adds,removes;public boolean deny,failRemove;public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){adds++;if(deny)throw new SecurityException();current=r;return null;}public void unregisterReceiver(BroadcastReceiver r){removes++;if(failRemove)throw new IllegalStateException();}}''',
'android/os/SystemClock.java': '''package android.os;public class SystemClock {public static long elapsedRealtime(){return 1000;}}''',
'android/util/Log.java': '''package android.util;public class Log {public static int i(String a,String b){return 0;}public static int e(String a,String b,Throwable e){return 0;}}''',
'com/veloster/dashboard/view/DashboardView.java': '''package com.veloster.dashboard.view;public class DashboardView {public static String status="";public static int received,processed,invalid;public static void diagEvent(String n,String d){if(n.equals("binding.media"))status=d;if(n.startsWith("binding.media.")&&d.equals("received"))received++;if(n.startsWith("binding.media.")&&d.equals("processed"))processed++;if(n.startsWith("binding.media.")&&d.equals("invalid"))invalid++;}}''',
}
HARNESS = '''package com.veloster.dashboard.vehicle;
import com.veloster.dashboard.view.DashboardView;
import android.content.*;
public class MediaReceiverHarness {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a){
  Context c=new Context();MediaState state=new MediaState();MediaReceiver r=new MediaReceiver(state);
  c.deny=true;r.start(c);check(DashboardView.status.equals("permission denied"),"permission failure");
  c.deny=false;r.start(c);r.start(c);check(c.adds==2&&DashboardView.status.equals("registered"),"retry and duplicate registration");
  BroadcastReceiver old=c.current;old.onReceive(c,new Intent(MediaState.ACTIONS[0]));check(DashboardView.received==1&&DashboardView.processed==1,"current callback processed");Intent bad=new Intent(MediaState.ACTIONS[0]);bad.malformed=true;old.onReceive(c,bad);check(DashboardView.received==2&&DashboardView.processed==1&&DashboardView.invalid==1,"malformed arrival is not processing success");
  r.stop();r.stop();check(c.removes==1&&DashboardView.status.equals("stopped"),"idempotent stop");
  old.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==2,"stopped callback rejected");
  r.start(c);old.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==2,"old session rejected");
  c.current.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==3,"new session delivers");
  BroadcastReceiver failed=c.current;c.failRemove=true;r.stop();check(DashboardView.status.equals("error"),"detach failure reported");
  failed.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==3,"failed detach callback rejected");c.failRemove=false;r.start(c);failed.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==3,"failed detach stays stale after restart");r.stop();
 }
}'''
class MediaReceiverTests(unittest.TestCase):
 def test_lifecycle(self):
  java,javac=java_tools()
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp); sources=[]
   for name,body in STUBS.items():
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);sources.append(p)
   p=root/'com/veloster/vehicle/MediaReceiverHarness.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(HARNESS);sources.append(p)
   sources += [ROOT/'apps/dashboard/src/main/java/com/veloster/dashboard/vehicle'/name for name in ('MediaReceiver.java','MediaState.java')]
   result=subprocess.run([javac,'-d',str(root),*map(str,sources)],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
   result=subprocess.run([java,'-cp',str(root),'com.veloster.dashboard.vehicle.MediaReceiverHarness'],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
