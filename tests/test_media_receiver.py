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
'android/os/Bundle.java': '''package android.os; public class Bundle {public java.util.Map<String,Object> values=new java.util.HashMap<String,Object>();public Object get(String key){return values.get(key);}}''',
'android/content/Intent.java': '''package android.content; public class Intent {String action; public boolean malformed, carplayPlayback; public Intent(String a){action=a;} public String getAction(){return action;} public boolean hasExtra(String n){return "filename".equals(n)||(carplayPlayback&&"playstate".equals(n));} public String getStringExtra(String n){if(malformed)throw new IllegalArgumentException();return "track";} public long getLongExtra(String n,long d){return d;} public boolean getBooleanExtra(String n,boolean d){return d;} public int getIntExtra(String n,int d){return carplayPlayback&&"playstate".equals(n)?1:d;} public byte[] getByteArrayExtra(String n){return null;} public android.os.Bundle getExtras(){android.os.Bundle b=new android.os.Bundle();return b;}}''',
'android/content/Context.java': '''package android.content; public class Context {public BroadcastReceiver current;public int adds,removes;public boolean deny,failRemove;public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){adds++;if(deny)throw new SecurityException();current=r;return null;}public void unregisterReceiver(BroadcastReceiver r){removes++;if(failRemove)throw new IllegalStateException();}}''',
'android/os/SystemClock.java': '''package android.os;public class SystemClock {public static long elapsedRealtime(){return 1000;}}''',
'android/util/Log.java': '''package android.util;public class Log {public static int i(String a,String b){return 0;}public static int e(String a,String b,Throwable e){return 0;}}''',
'com/veloster/dashboard/view/DashboardView.java': '''package com.veloster.dashboard.view;public class DashboardView {public static String status="",playback="";public static int received,processed,invalid;public static void diagEvent(String n,String d){if(n.equals("binding.media"))status=d;if(n.equals("carplay.media.playback"))playback=d;if(n.startsWith("binding.media.")&&d.equals("received"))received++;if(n.startsWith("binding.media.")&&d.equals("processed"))processed++;if(n.startsWith("binding.media.")&&d.equals("invalid"))invalid++;}}''',
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
  Intent carplay=new Intent(MediaState.ACTIONS[3]);carplay.carplayPlayback=true;old.onReceive(c,carplay);check(DashboardView.playback.equals("playing"),"CarPlay playback is observed");
  r.stop();r.stop();check(c.removes==1&&DashboardView.status.equals("stopped"),"idempotent stop");
  old.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==3,"stopped callback rejected");
  r.start(c);old.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==3,"old session rejected");
  c.current.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==4,"new session delivers");
  BroadcastReceiver failed=c.current;c.failRemove=true;r.stop();check(DashboardView.status.equals("error"),"detach failure reported");
  failed.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==4,"failed detach callback rejected");c.failRemove=false;r.start(c);failed.onReceive(c,new Intent(MediaState.ACTIONS[1]));check(DashboardView.received==4,"failed detach stays stale after restart");r.stop();
 }
}'''
MAPPING_INTENT = '''package android.content;
public class Intent {
 String action;java.util.Map<String,Object> extras=new java.util.HashMap<String,Object>();
 public Intent(String a){action=a;}public Intent put(String key,Object value){extras.put(key,value);return this;}
 public String getAction(){return action;}public boolean hasExtra(String n){return extras.containsKey(n);}public android.os.Bundle getExtras(){android.os.Bundle b=new android.os.Bundle();b.values.putAll(extras);return b;}
 public String getStringExtra(String n){return (String)extras.get(n);}
 public long getLongExtra(String n,long d){return extras.containsKey(n)?((Number)extras.get(n)).longValue():d;}
 public int getIntExtra(String n,int d){return extras.containsKey(n)?((Number)extras.get(n)).intValue():d;}
 public boolean getBooleanExtra(String n,boolean d){return extras.containsKey(n)?(Boolean)extras.get(n):d;}
 public byte[] getByteArrayExtra(String n){return (byte[])extras.get(n);}
}'''
MAPPING_HARNESS = '''package com.veloster.dashboard.vehicle;
import android.content.*;
public class MediaMappingHarness {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  String[] keys={"playstate","playing_state","playstate","playstate","state"};
  int[] modes={0x31,0x36,0x34,0x73,0x74};
  for(int source=0;source<5;source++){
   Context context=new Context();MediaState state=new MediaState();state.mode(modes[source]);MediaReceiver receiver=new MediaReceiver(state);receiver.start(context);
   context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("filename","Title alone"));
   check(state.snapshot(1000).playState==-1,"title never implies playing: "+source);
   for(int status:new int[]{1,0}){
    Object raw=source==1?Boolean.valueOf(status==1):Integer.valueOf(source==4?status==1?2:1:status);
    context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put(keys[source],raw));
    check(state.snapshot(1000).playState==status,"source playback mapping: "+source+"/"+status);
    context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("artist","Partial"));
    check(state.snapshot(1000).playState==status,"partial update retains explicit state: "+source);
   }
   if(source==1){
    context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("playing_state",Integer.valueOf(1)));
    check(state.snapshot(1000).playState==1,"Bluetooth integer playing_state is accepted");
    context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("playing_state",Integer.valueOf(0)));
    check(state.snapshot(1000).playState==0,"Bluetooth integer pause state is accepted");
   }
   if(source==4){
    context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("state",0));check(state.snapshot(1000).playState==-1,"AA stopped is not playing");
    context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("state",99));check(state.snapshot(1000).playState==-1,"AA unknown is not playing");
   }else if(source!=1){
    context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("playstate",-1));check(state.snapshot(1000).playState==0,"OEM omitted-state sentinel preserves pause");
    context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("playstate",7));check(state.snapshot(1000).playState==-1,"invalid OEM state becomes unknown");
   }
   Object playing=source==1?Boolean.TRUE:Integer.valueOf(source==4?2:1);
   context.current.onReceive(context,new Intent(MediaState.ACTIONS[source]).put("invisible",true).put(keys[source],playing));
   check(state.snapshot(1000).playState==-1&&state.snapshot(1000).title.equals(""),"invisible clears even conflicting playback: "+source);
   receiver.stop();
  }
 }
}'''

class MediaReceiverTests(unittest.TestCase):
 def test_source_playback_mapping(self):
  java,javac=java_tools()
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp);sources=[]
   stubs=dict(STUBS);stubs['android/content/Intent.java']=MAPPING_INTENT
   for name,body in stubs.items():
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);sources.append(p)
   p=root/'MediaMappingHarness.java';p.write_text(MAPPING_HARNESS);sources.append(p)
   sources += [ROOT/'apps/dashboard/src/main/java/com/veloster/dashboard/vehicle'/name for name in ('MediaReceiver.java','MediaState.java')]
   result=subprocess.run([javac,'-d',str(root),*map(str,sources)],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
   result=subprocess.run([java,'-cp',str(root),'com.veloster.dashboard.vehicle.MediaMappingHarness'],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)

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
