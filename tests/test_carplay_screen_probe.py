"""The probe must check permission and foreground before touching the screen."""
from pathlib import Path
import subprocess, tempfile, unittest
from test_quick_entry_runtime import java_tools

ROOT=Path(__file__).resolve().parents[1]
STUBS={
'android/content/Context.java':'''package android.content; public class Context {public static final String ACTIVITY_SERVICE="activity"; public boolean denied; public android.app.ActivityManager manager=new android.app.ActivityManager(); public int checkCallingOrSelfPermission(String p){return denied?-1:0;} public Object getSystemService(String s){return manager;}}''',
'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager {public static final int PERMISSION_GRANTED=0;}',
'android/content/ComponentName.java':'package android.content;public class ComponentName {public String name;public ComponentName(String s){name=s;}public String getPackageName(){return name;}}',
'android/app/ActivityManager.java':'''package android.app;import java.util.*;import android.content.ComponentName;public class ActivityManager {public String top="other";public static class RunningTaskInfo {public ComponentName topActivity;} public List<RunningTaskInfo> getRunningTasks(int n){RunningTaskInfo t=new RunningTaskInfo();t.topActivity=new ComponentName(top);return Arrays.asList(t);}}''',
'android/graphics/Bitmap.java':'package android.graphics;public class Bitmap {public static boolean recycled;public int getWidth(){return 800;}public int getHeight(){return 480;}public void recycle(){recycled=true;}}',
'android/view/Surface.java':'package android.view;public class Surface {public static int calls;public static boolean fail;public static android.graphics.Bitmap screenshot(int w,int h){calls++;if(fail)throw new SecurityException();return new android.graphics.Bitmap();}}',
}
HARNESS='''import android.content.Context;import android.view.Surface;import android.graphics.Bitmap;import com.veloster.dashboard.debugger.CarPlayScreenProbe;
public class ProbeTest {static void check(boolean b){if(!b)throw new AssertionError();}public static void main(String[] a){Context c=new Context();c.denied=true;check(CarPlayScreenProbe.run(c).equals("permission_denied")&&Surface.calls==0);c.denied=false;check(CarPlayScreenProbe.run(c).equals("wrong_foreground")&&Surface.calls==0);c.manager.top="com.daudio.app.dipo";check(CarPlayScreenProbe.run(c).equals("captured")&&Surface.calls==1&&Bitmap.recycled);Surface.fail=true;check(CarPlayScreenProbe.run(c).equals("permission_denied"));}}'''
class ScreenProbeTests(unittest.TestCase):
 def test_permissions_foreground_and_disposal(self):
  java,javac=java_tools()
  with tempfile.TemporaryDirectory() as d:
   root=Path(d); files=[]
   for name,body in STUBS.items():
    path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
   harness=root/'ProbeTest.java';harness.write_text(HARNESS);files.append(harness)
   files.append(ROOT/'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/CarPlayScreenProbe.java')
   c=subprocess.run([javac,'-d',str(root),*map(str,files)],capture_output=True,text=True);self.assertEqual(c.returncode,0,c.stderr)
   r=subprocess.run([java,'-cp',str(root),'ProbeTest'],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stderr)
