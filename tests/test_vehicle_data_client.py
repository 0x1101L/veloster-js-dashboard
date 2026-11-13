"""Exercise the actual passive subscription client with deterministic OEM/Handler doubles."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'com/veloster/dashboard/vehicle/HscanProfile.java': 'package com.veloster.dashboard.vehicle;public class HscanProfile {public enum Kind {JS, PD, UNSUPPORTED}}',
    'com/veloster/dashboard/vehicle/js/JsDctClient.java': '''package com.veloster.dashboard.vehicle.js;
public class JsDctClient {public interface Gate {boolean open();}public static Gate gate;public static int starts,stops;
 public JsDctClient(Gate g){gate=g;}public void start(){starts++;}public void stop(){stops++;}}''',
    "com/veloster/diagnostics/ErrorLog.java": "package com.veloster.diagnostics;public class ErrorLog {public static void record(String s,Throwable e){}}",
    'android/content/Context.java': 'package android.content; public class Context {}',
    'android/util/Log.java': 'package android.util; public class Log {public static int e(String t,String m,Throwable e){return 0;}}',
    'android/os/Handler.java': '''package android.os; import java.util.*;
public class Handler {
 public static List<Runnable> jobs=new ArrayList<Runnable>();public static long delay;
 public void removeCallbacks(Runnable r){while(jobs.remove(r)){} }
 public boolean postDelayed(Runnable r,long ms){jobs.add(r);delay=ms;return true;}
 public static void next(){if(jobs.size()!=1)throw new AssertionError("one scheduled retry required");jobs.remove(0).run();}
}''',
    'com/veloster/dashboard/view/DashboardView.java': '''package com.veloster.dashboard.view;
public class DashboardView {public static int writes,clears; public static java.util.Map<String,String> statuses=new java.util.HashMap<String,String>(); public static java.util.Set<String> received=new java.util.HashSet<String>(); public static volatile java.util.concurrent.CountDownLatch entered,release;
 public static void diagEvent(String n,String d){if(d.equals("received"))received.add(n);else statuses.put(n,d);if(n.equals("binding.nmode")&&d.equals("received")&&entered!=null){entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}}}
 public static void ignition(boolean b){clears++;}public static void calculateObserved(){}
 public static void rpm(byte[] b){writes++;}public static void gforce(byte[] b){writes++;}
 public static void temperatures(byte[] b){writes++;}public static void units(byte[] b){writes++;}
 public static void additional(byte[] b){writes++;}public static void wheels(byte[] b){writes++;}
 public static void speed(int s){writes++;}public static void rearView(int s){writes++;}
}''',
    'com/veloster/dashboard/HscanDataClient.java': '''package com.veloster.dashboard.vehicle;
final class HscanDataClient {interface Gate {boolean accepts();}static int fail,creates,starts,stops;static Gate gate;static boolean supported=true;static HscanProfile.Kind kind=HscanProfile.Kind.PD;static HscanProfile.Kind profile(){return kind;}static boolean pdProfile(){return false;}static boolean supportedProfile(){return supported;}
 boolean live;HscanDataClient(Gate g){creates++;if(fail-->0)throw new IllegalStateException("HSCAN unavailable");gate=g;}
 void start(){if(!live){live=true;starts++;}}void stop(){if(live){live=false;stops++;}}
}''',
    'com/hkmc/VehicleInfoManager.java': '''package com.hkmc;import android.content.Context;import com.hkmc.bcm.BCMManager;
public class VehicleInfoManager {public static final String BCM_SERVICE="bcm";public static VehicleInfoManager getInstance(Context c){return new VehicleInfoManager();}
 public Object getService(String name){return new BCMManager(null);}}
''',
    'com/hkmc/micom/NModeListener.java': '''package com.hkmc.micom;public class NModeListener {
 public void onPowerTorqueTurboBoostReceived(byte[] b){} public void onGForceDataReceived(byte[] b){}
 public void onOilCoolantTemperature(byte[] b){}public void onSpeedTempUnitTempReceived(byte[] b){}
 public void onNAddInfoReceived(byte[] b){}public void onNModeZeroSpeedChanged(byte[] b){}}
''',
    'com/hkmc/micom/VehicleInfoListener.java': 'package com.hkmc.micom;public class VehicleInfoListener {public void onVehicleSpeedChanged(int n){}}',
    'com/hkmc/bcm/BCMPowerListener.java': 'package com.hkmc.bcm;public class BCMPowerListener {public void onIgnitionChanged(boolean b){}}',
    'com/hkmc/pgs/PgsStatusListener.java': 'package com.hkmc.pgs;public class PgsStatusListener {public void onRVMViewStatusChanged(int n){}}',
}
for pkg, cls, listener, add, remove in (
    ('micom', 'NModeManager', 'NModeListener', 'registerNModeListener', 'unregisterNModeListener'),
    ('micom', 'VehicleInfoManager', 'VehicleInfoListener', 'addListener', 'removeListener'),
    ('bcm', 'BCMManager', 'BCMPowerListener', 'addPowerListener', 'removePowerListener'),
    ('pgs', 'PgsManager', 'PgsStatusListener', 'registerPgsStatusListener', 'unregisterPgsStatusListener'),
):
    STUBS[f'com/hkmc/{pkg}/{cls}.java'] = f'''package com.hkmc.{pkg};import android.content.Context;
public class {cls} {{
 public static int creates,adds,removes,failCreate,failAdd;public static boolean failRemove,deny,missing,ignition=true;
 public static {listener} current,previous;
 public {cls}(Context c){{creates++;if(missing)throw new NoClassDefFoundError("OEM unavailable");if(failCreate-->0)throw new IllegalStateException("service not ready");}}
 public void {add}({listener} l){{adds++;previous=current;current=l;if(deny)throw new SecurityException();if(failAdd-->0)throw new IllegalStateException("partial registration");}}
 public void {remove}({listener} l){{removes++;if(failRemove)throw new IllegalStateException("unregister failed");}}
 public boolean getIGNStatus(){{return ignition;}}
 public static void reset(){{creates=adds=removes=failCreate=failAdd=0;failRemove=deny=missing=false;ignition=true;current=previous=null;}}
}}'''

HARNESS = '''package com.veloster.dashboard.vehicle;
import android.content.Context;import android.os.Handler;import com.hkmc.micom.*;import com.hkmc.bcm.*;import com.hkmc.pgs.*;import com.veloster.dashboard.view.DashboardView;
public class VehicleDataClientHarness {
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 static VehicleDataClient fresh(){Handler.jobs.clear();NModeManager.reset();VehicleInfoManager.reset();BCMManager.reset();PgsManager.reset();HscanDataClient.fail=HscanDataClient.creates=HscanDataClient.starts=HscanDataClient.stops=0;DashboardView.writes=DashboardView.clears=0;DashboardView.statuses.clear();DashboardView.received.clear();return new VehicleDataClient(new Context());}
 static void ready(){VehicleDataClient c=fresh();c.start();c.start();check(Handler.jobs.isEmpty(),"all ready stops retrying");check(NModeManager.adds==1&&PgsManager.adds==1&&HscanDataClient.starts==1,"start idempotent");c.stop();check(NModeManager.removes==1&&PgsManager.removes==1&&HscanDataClient.stops==1,"all subscriptions released");}
 static void allCallbacks(){VehicleDataClient c=fresh();c.start();BCMManager.current.onIgnitionChanged(true);NModeManager.current.onOilCoolantTemperature(new byte[0]);VehicleInfoManager.current.onVehicleSpeedChanged(42);PgsManager.current.onRVMViewStatusChanged(1);for(String name:new String[]{"bcm","nmode","speed","pgs"})check(DashboardView.received.contains("binding."+name),name+" callback observed");check(DashboardView.writes==3,"each data callback delivered");c.stop();}
 static void delayedRear(){VehicleDataClient c=fresh();PgsManager.failCreate=2;c.start();check(Handler.jobs.size()==1&&Handler.delay==2000,"PGS-only startup failure retried");Handler.next();check(Handler.delay==4000,"retry backoff");Handler.next();check(Handler.jobs.isEmpty()&&PgsManager.adds==1,"late PGS connects");check(NModeManager.creates==1&&VehicleInfoManager.creates==1&&BCMManager.creates==1,"ready peers not duplicated");c.stop();}
 static void delayedHscan(){VehicleDataClient c=fresh();HscanDataClient.fail=1;c.start();check(Handler.jobs.size()==1,"HSCAN-only failure retried");Handler.next();check(HscanDataClient.starts==1&&Handler.jobs.isEmpty(),"HSCAN recovered");c.stop();}
 static void failedRegistration(){VehicleDataClient c=fresh();NModeManager.failAdd=1;c.start();NModeListener stale=NModeManager.current;check(NModeManager.removes==1,"partial registration cleaned up");stale.onPowerTorqueTurboBoostReceived(new byte[0]);check(DashboardView.writes==0,"failed registration callback ignored");Handler.next();stale.onPowerTorqueTurboBoostReceived(new byte[0]);check(DashboardView.writes==0,"same-session old registration ignored");NModeManager.current.onPowerTorqueTurboBoostReceived(new byte[0]);check(DashboardView.writes==1,"replacement listener delivers");c.stop();}
 static void failedCleanup(){VehicleDataClient c=fresh();NModeManager.failAdd=1;NModeManager.failRemove=true;c.start();NModeListener stale=NModeManager.current;Handler.next();stale.onOilCoolantTemperature(new byte[0]);check(DashboardView.writes==0,"failed unregister cannot revive old listener");NModeManager.failRemove=false;c.stop();}
 static void powerFailure(){VehicleDataClient c=fresh();BCMManager.failAdd=1;c.start();BCMPowerListener stale=BCMManager.current;check(!HscanDataClient.gate.accepts()&&HscanDataClient.starts==0,"failed BCM must not enable telemetry");stale.onIgnitionChanged(true);check(!HscanDataClient.gate.accepts(),"failed power callback ignored");Handler.next();check(HscanDataClient.starts==1&&HscanDataClient.gate.accepts(),"power registration recovered");c.stop();}
 static void stopInvalidates(){VehicleDataClient c=fresh();c.start();NModeListener n=NModeManager.current;VehicleInfoListener v=VehicleInfoManager.current;PgsStatusListener r=PgsManager.current;BCMPowerListener p=BCMManager.current;c.stop();c.start();n.onOilCoolantTemperature(new byte[0]);v.onVehicleSpeedChanged(42);r.onRVMViewStatusChanged(1);p.onIgnitionChanged(false);check(DashboardView.writes==0&&HscanDataClient.gate.accepts(),"old generation cannot mutate resumed session");c.stop();}
 static void permissions(){
  String[] names={"bcm","nmode","speed","pgs"};
  for(int i=0;i<names.length;i++){
   VehicleDataClient c=fresh();if(i==0)BCMManager.deny=true;if(i==1)NModeManager.deny=true;if(i==2)VehicleInfoManager.deny=true;if(i==3)PgsManager.deny=true;
   c.start();check("permission denied".equals(DashboardView.statuses.get("binding."+names[i])),names[i]+" permission status");check(Handler.jobs.size()==1,names[i]+" retry scheduled");
   BCMManager.deny=NModeManager.deny=VehicleInfoManager.deny=PgsManager.deny=false;Handler.next();check("registered".equals(DashboardView.statuses.get("binding."+names[i])),names[i]+" recovered");c.stop();check("stopped".equals(DashboardView.statuses.get("binding."+names[i])),names[i]+" stopped");
  }
 }
 static void missingAndCleanup(){
  String[] names={"bcm","nmode","speed","pgs"};
  for(int i=0;i<names.length;i++){
   VehicleDataClient c=fresh();if(i==0)BCMManager.missing=true;if(i==1)NModeManager.missing=true;if(i==2)VehicleInfoManager.missing=true;if(i==3)PgsManager.missing=true;
   c.start();check("unsupported".equals(DashboardView.statuses.get("binding."+names[i])),names[i]+" missing class");
   BCMManager.missing=NModeManager.missing=VehicleInfoManager.missing=PgsManager.missing=false;Handler.next();check("registered".equals(DashboardView.statuses.get("binding."+names[i])),names[i]+" recovery");c.stop();
  }
  VehicleDataClient c=fresh();c.start();NModeListener n=NModeManager.current;VehicleInfoListener v=VehicleInfoManager.current;PgsStatusListener p=PgsManager.current;BCMPowerListener b=BCMManager.current;
  BCMManager.failRemove=NModeManager.failRemove=VehicleInfoManager.failRemove=PgsManager.failRemove=true;c.stop();
  for(String name:names)check("error".equals(DashboardView.statuses.get("binding."+name)),name+" detach failure");
  n.onOilCoolantTemperature(new byte[0]);v.onVehicleSpeedChanged(1);p.onRVMViewStatusChanged(1);b.onIgnitionChanged(true);check(DashboardView.writes==0,"failed detaches reject callbacks");
 }
 static void concurrentStop() throws Exception {
  final VehicleDataClient c=fresh();c.start();final NModeListener old=NModeManager.current;
  DashboardView.entered=new java.util.concurrent.CountDownLatch(1);DashboardView.release=new java.util.concurrent.CountDownLatch(1);
  Thread callback=new Thread(new Runnable(){public void run(){old.onOilCoolantTemperature(new byte[0]);}});callback.start();
  check(DashboardView.entered.await(2,java.util.concurrent.TimeUnit.SECONDS),"callback entered");
  Thread stopper=new Thread(new Runnable(){public void run(){c.stop();}});stopper.start();
  Thread.sleep(50);check(stopper.isAlive(),"stop waits for in-flight callback");
  DashboardView.release.countDown();callback.join(2000);stopper.join(2000);
  check(!callback.isAlive()&&!stopper.isAlive(),"callback and stop complete");
  int writes=DashboardView.writes;old.onOilCoolantTemperature(new byte[0]);check(DashboardView.writes==writes,"no writes after stop");
  DashboardView.entered=null;DashboardView.release=null;
 }
 static void boundedRetry(){VehicleDataClient c=fresh();PgsManager.failCreate=100;c.start();for(int i=0;i<12;i++){c.run();check(Handler.jobs.size()==1&&Handler.delay<=30000,"bounded single retry");}check(Handler.delay==30000,"delay caps at 30 sec");c.stop();check(Handler.jobs.isEmpty(),"stop cancels retries");c.start();check(Handler.delay==2000,"fresh session resets backoff");c.stop();}
 static void unsupportedProfile(){VehicleDataClient c=fresh();HscanDataClient.supported=false;c.start();check(!HscanDataClient.gate.accepts(),"unknown product/build blocks all telemetry callbacks");NModeManager.current.onOilCoolantTemperature(new byte[0]);check(DashboardView.writes==0,"unknown profile cannot publish OEM data");c.stop();HscanDataClient.supported=true;}
 static void ignitionGate(){VehicleDataClient c=fresh();BCMManager.ignition=false;c.start();NModeManager.current.onOilCoolantTemperature(new byte[0]);check(DashboardView.writes==0&&HscanDataClient.starts==0,"off gates receive");BCMManager.current.onIgnitionChanged(true);check(HscanDataClient.starts==1,"ignition starts receive");NModeManager.current.onOilCoolantTemperature(new byte[0]);check(DashboardView.writes==1,"on delivers");BCMManager.current.onIgnitionChanged(false);check(HscanDataClient.stops==1&&!HscanDataClient.gate.accepts(),"off stops receive");c.stop();}
 static void dctLifecycle(){HscanDataClient.kind=HscanProfile.Kind.JS;VehicleDataClient c=fresh();BCMManager.ignition=false;c.start();check(!com.veloster.dashboard.vehicle.js.JsDctClient.gate.open(),"DCT gated while ignition off");BCMManager.current.onIgnitionChanged(true);check(com.veloster.dashboard.vehicle.js.JsDctClient.gate.open(),"DCT enabled when ignition on");c.stop();check(!com.veloster.dashboard.vehicle.js.JsDctClient.gate.open()&&com.veloster.dashboard.vehicle.js.JsDctClient.stops>0,"DCT gated and stopped");HscanDataClient.kind=HscanProfile.Kind.PD;}
 public static void main(String[] a) throws Exception {missingAndCleanup();permissions();concurrentStop();ready();allCallbacks();delayedRear();delayedHscan();failedRegistration();failedCleanup();powerFailure();stopInvalidates();boundedRetry();unsupportedProfile();ignitionGate();dctLifecycle();System.out.println("PASS 14 vehicle subscription lifecycle scenarios");}
}'''

class VehicleDataClientTests(unittest.TestCase):
    def test_lifecycle(self):
        java, javac = java_tools()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            sources = []
            for name, text in STUBS.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
                sources.append(p)
            harness = root / 'com/veloster/dashboard/vehicle/VehicleDataClientHarness.java'
            harness.parent.mkdir(parents=True, exist_ok=True)
            harness.write_text(HARNESS)
            source = Path(os.environ.get('VN_VEHICLE_CLIENT_SOURCE', ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/VehicleDataClient.java'))
            result = subprocess.run([javac, '-source', '8', '-target', '8', '-d', str(root), *map(str, sources), str(harness), str(source)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([java, '-cp', str(root), 'com.veloster.dashboard.vehicle.VehicleDataClientHarness'], capture_output=True, text=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('PASS 14', result.stdout)

if __name__ == '__main__':
    unittest.main()
