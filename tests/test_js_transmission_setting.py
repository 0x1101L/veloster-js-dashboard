"""Verify JS transmission selection persists and can be changed."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {int getInt(String k,int d); Editor edit(); interface Editor {Editor putInt(String k,int v);void apply();}}''',
'android/app/Activity.java': '''package android.app; import android.content.*; import java.util.*; public class Activity {static final Map<String,Integer> data=new HashMap<String,Integer>();public SharedPreferences getSharedPreferences(String name,int mode){if(!name.equals("js_vehicle_settings"))throw new AssertionError("wrong scope");return new Prefs();} static class Prefs implements SharedPreferences,SharedPreferences.Editor {public int getInt(String k,int d){return data.containsKey(k)?data.get(k):d;}public SharedPreferences.Editor edit(){return this;}public SharedPreferences.Editor putInt(String k,int v){data.put(k,v);return this;}public void apply(){}}}''',
'Harness.java': '''import android.app.Activity;import com.veloster.dashboard.vehicle.js.JsTransmissionSetting;
public class Harness {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  JsTransmissionSetting setting=new JsTransmissionSetting(new Activity());check(setting.get()==0);
  setting.save(1);JsTransmissionSetting recreated=new JsTransmissionSetting(new Activity());check(recreated.get()==1);
  recreated.save(2);check(new JsTransmissionSetting(new Activity()).get()==2);
  try{recreated.save(8);throw new AssertionError("invalid option accepted");}catch(IllegalArgumentException expected){}
  check(recreated.get()==2);
 }
}'''
}
class JsTransmissionSettingTest(unittest.TestCase):
 def test_choice_survives_recreation_and_can_change(self):
  java, javac = java_tools()
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder)
   for name, source in STUBS.items():
    path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source)
   target=ROOT/'apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/js/JsTransmissionSetting.java'
   compiled=subprocess.run([javac,'-d',str(root),*map(str,root.rglob('*.java')),str(target)],capture_output=True,text=True)
   self.assertEqual(compiled.returncode,0,compiled.stderr)
   run=subprocess.run([java,'-cp',str(root),'Harness'],capture_output=True,text=True)
   self.assertEqual(run.returncode,0,run.stderr)
if __name__=='__main__': unittest.main()
