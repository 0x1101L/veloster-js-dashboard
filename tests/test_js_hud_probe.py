"""Run the passive HUD probe with deterministic Android doubles; no device or IPC."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'android/os/Build.java': '''package android.os;public class Build {
 public static String PRODUCT="js_kr";public static class VERSION {public static String INCREMENTAL="eng.vmadmin.20260114.090656";}}''',
    'android/os/IBinder.java': 'package android.os;public interface IBinder {String getInterfaceDescriptor();}',
    'android/os/ServiceManager.java': '''package android.os;public class ServiceManager {
 public static IBinder binder;public static boolean fail;public static String last;
 public static IBinder getService(String name){last=name;if(fail)throw new SecurityException();return binder;}}''',
    'android/os/Bundle.java': 'package android.os;public class Bundle extends java.util.HashMap<String,Object> {}',
    'android/os/Looper.java': 'package android.os;public class Looper {public static Looper myLooper(){return null;}public static Looper getMainLooper(){return new Looper();}}',
    'android/os/Handler.java': '''package android.os;public class Handler {
 public Handler(Looper l){}public boolean post(Runnable r){return true;}public boolean postDelayed(Runnable r,long ms){return true;}}''',
    'android/os/HandlerThread.java': '''package android.os;public class HandlerThread {
 public HandlerThread(String s){}public void start(){}public Looper getLooper(){return new Looper();}public boolean quit(){return true;}}''',
    'android/net/Uri.java': 'package android.net;public class Uri {public static Uri parse(String s){return new Uri();}}',
    'android/database/ContentObserver.java': '''package android.database;public class ContentObserver {
 public ContentObserver(android.os.Handler h){}public void onChange(boolean b){}}''',
    'android/database/Cursor.java': '''package android.database;public interface Cursor {
 int getColumnIndex(String n);boolean moveToFirst();boolean isNull(int c);String getString(int c);void close();}''',
    'android/content/ContentResolver.java': '''package android.content;import android.net.Uri;import android.database.*;
 public class ContentResolver {public boolean fail;public int adds,removes;
 public void registerContentObserver(Uri u,boolean b,ContentObserver o){adds++;if(fail)throw new SecurityException();}
 public void unregisterContentObserver(ContentObserver o){removes++;}
 public Cursor query(Uri u,String[] p,String s,String[] a,String order){return null;}}''',
    'android/content/Intent.java': '''package android.content;import android.os.Bundle;public class Intent {
 private String action;private Bundle extras=new Bundle();public Intent(String a){action=a;}
 public String getAction(){return action;}public Bundle getExtras(){return extras;}
 public Intent put(String k,Object v){extras.put(k,v);return this;}}''',
    'android/content/IntentFilter.java': 'package android.content;public class IntentFilter {public final String action;public IntentFilter(String a){action=a;}}',
    'android/content/BroadcastReceiver.java': 'package android.content;public abstract class BroadcastReceiver {public boolean initialSticky;public boolean isInitialStickyBroadcast(){return initialSticky;}public abstract void onReceive(Context c,Intent i);}',
    'android/content/Context.java': '''package android.content;public class Context {
 public BroadcastReceiver receiver,lastReceiver;public IntentFilter filter;public int adds,removes;public boolean fail,failRemove;public Intent sticky;
 public ContentResolver resolver=new ContentResolver();public ContentResolver getContentResolver(){return resolver;}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){adds++;receiver=lastReceiver=r;filter=f;if(fail)throw new SecurityException();if(sticky!=null){r.initialSticky=true;r.onReceive(this,sticky);r.initialSticky=false;}return sticky;}
 public void unregisterReceiver(BroadcastReceiver r){removes++;if(failRemove)throw new IllegalStateException();if(receiver==r)receiver=null;}}''',
}

HARNESS = '''package com.veloster.dashboard;
import android.content.*;import android.os.*;import com.veloster.vehicle.JsDiagState;
public class JsHudProbeHarness {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static class Output implements JsHudProbe.Sink {JsDiagState state=new JsDiagState();int values;
 public void event(String n,String d){state.event(n,d);if(n.equals("navigation.route_state"))values++;}}
 static Intent route(Object v){return new Intent(JsRouteStatusObserver.ACTION).put(JsRouteStatusObserver.STATE,v);}
 static void lifecycle(){
  Context c=new Context();Output o=new Output();JsHudProbe p=new JsHudProbe(c,o);
  p.startNavigationObservation();p.startNavigationObservation();
  check(c.adds==1&&c.resolver.adds==1,"one subscription each");
  check(JsRouteStatusObserver.ACTION.equals(c.filter.action)&&o.state.snapshot().routeNotice==0,"exact filter and waiting");
  BroadcastReceiver old=c.receiver;old.onReceive(c,route(0));
  check(o.state.snapshot().routeValue==0&&o.state.snapshot().routeNotice==1,"zero is a received value");
  check(o.state.snapshot().routeCount==1&&o.state.snapshot().routeSource==0,"counted as broadcast, without inferring an OEM sender");
  old.onReceive(c,route(-1));check(o.state.snapshot().routeValue==-1,"raw negative integer preserved");
  old.onReceive(c,new Intent("other").put(JsRouteStatusObserver.STATE,99));old.onReceive(c,null);
  check(o.values==2,"other broadcasts ignored");
  old.onReceive(c,route("1"));check(o.state.snapshot().routeNotice==-1,"string never coerced into a route state");
  old.onReceive(c,new Intent(JsRouteStatusObserver.ACTION));check(o.values==2,"missing extra never defaults to zero");
  p.close();p.close();check(c.removes==1&&o.state.snapshot().routeWatch==0,"idempotent cleanup");
  old.onReceive(c,route(1));check(o.values==2,"late callback ignored");
  p.startNavigationObservation();old.onReceive(c,route(3));check(o.values==2,"old generation ignored after restart");
  c.receiver.onReceive(c,route(3));check(o.values==3&&o.state.snapshot().routeValue==3,"new receiver live");p.close();
 }
 static void independent(){
  Context c=new Context();Output o=new Output();c.resolver.fail=true;JsHudProbe p=new JsHudProbe(c,o);p.startNavigationObservation();
  check(o.state.snapshot().nav==2&&o.state.snapshot().routeWatch==1,"provider denial does not stop broadcast receiver");
  c.receiver.onReceive(c,route(1));check(o.values==1,"broadcast survives provider denial");p.close();
  c=new Context();o=new Output();c.fail=true;p=new JsHudProbe(c,o);p.startNavigationObservation();
  check(o.state.snapshot().routeWatch==2&&o.state.snapshot().nav==1&&c.removes==1,"broadcast failure cleaned without stopping provider");
  BroadcastReceiver failed=c.lastReceiver;p.close();
  failed.onReceive(c,route(1));check(o.values==0,"failed registration never delivers");
 }
 static void failedCleanup(){
  Context c=new Context();Output o=new Output();JsHudProbe p=new JsHudProbe(c,o);p.startNavigationObservation();
  BroadcastReceiver old=c.receiver;c.failRemove=true;p.close();p.startNavigationObservation();
  old.onReceive(c,route(1));check(o.values==0,"unregister exception cannot revive old receiver");c.failRemove=false;p.close();
 }
 static void sticky(){
  Context c=new Context();c.sticky=route(3);Output o=new Output();JsHudProbe p=new JsHudProbe(c,o);p.startNavigationObservation();
  check(o.state.snapshot().routeNotice==1&&o.state.snapshot().routeValue==3&&o.state.snapshot().routeSource==1,"initial sticky value survives registration completion");
  c.receiver.onReceive(c,route(0));check(o.state.snapshot().routeCount==2&&o.state.snapshot().routeSource==0,"later broadcast replaces initial sticky origin");p.close();
 }
 static void service(){
  Context c=new Context();Output o=new Output();JsHudProbe p=new JsHudProbe(c,o);p.inspect();
  check(o.state.snapshot().clusterService==0&&ServiceManager.last.equals("com.hkmc.micom.cluster"),"owning cluster service queried");
  ServiceManager.binder=new IBinder(){public String getInterfaceDescriptor(){return "com.hkmc.cluster.IClusterService";}};p.inspect();
  check(o.state.snapshot().clusterService==1&&o.state.snapshot().tbt==-1,"descriptor is not TBT reception");
  ServiceManager.binder=new IBinder(){public String getInterfaceDescriptor(){return "wrong";}};p.inspect();check(o.state.snapshot().clusterService==2,"wrong interface rejected");
  ServiceManager.fail=true;p.inspect();check(o.state.snapshot().clusterService==2,"denied service query contained");ServiceManager.fail=false;
  ServiceManager.binder=new IBinder(){public String getInterfaceDescriptor(){throw new SecurityException();}};p.inspect();check(o.state.snapshot().clusterService==2,"descriptor failure contained");
 }
 static void profile(){Build.PRODUCT="hyundaiB_kr";try{new JsHudProbe(new Context(),new Output());throw new AssertionError("profile gate");}catch(IllegalStateException expected){}finally{Build.PRODUCT="js_kr";}}
 public static void main(String[] args){lifecycle();independent();failedCleanup();sticky();service();profile();System.out.println("PASS passive HUD observation scenarios");}
}'''


class JsHudProbeTests(unittest.TestCase):
    def test_passive_observation(self):
        java, javac = java_tools()
        with tempfile.TemporaryDirectory(prefix='js-hud-tests-') as folder:
            root = Path(folder)
            sources = []
            for name, text in STUBS.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
                sources.append(p)
            harness = root / 'JsHudProbeHarness.java'
            harness.write_text(HARNESS)
            app = ROOT / 'apps/dashboard/src/com/veloster/dashboard'
            sources += [harness, app / 'JsHudProbe.java', app / 'JsRouteStatusObserver.java',
                        ROOT / 'shared/src/com/veloster/vehicle/JsDiagState.java']
            result = subprocess.run([javac, '-source', '8', '-target', '8', '-d', str(root),
                                     *map(str, sources)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([java, '-cp', str(root), 'com.veloster.dashboard.JsHudProbeHarness'],
                                    capture_output=True, text=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('PASS passive HUD', result.stdout)
