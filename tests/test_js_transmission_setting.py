"""Verify JS transmission selection persists and can be changed."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {int getInt(String k,int d); Editor edit(); interface Editor {Editor putInt(String k,int v);void apply();}}''',
'android/content/DialogInterface.java': '''package android.content; public interface DialogInterface {void dismiss(); interface OnClickListener {void onClick(DialogInterface d,int index);}}''',
'android/app/Activity.java': '''package android.app; import android.content.*; import java.util.*; public class Activity {static final Map<String,Integer> data=new HashMap<String,Integer>();public SharedPreferences getSharedPreferences(String name,int mode){if(!name.equals("js_vehicle_settings"))throw new AssertionError("wrong scope");return new Prefs();} static class Prefs implements SharedPreferences,SharedPreferences.Editor {public int getInt(String k,int d){return data.containsKey(k)?data.get(k):d;}public SharedPreferences.Editor edit(){return this;}public SharedPreferences.Editor putInt(String k,int v){data.put(k,v);return this;}public void apply(){}}}''',
'android/app/AlertDialog.java': '''package android.app; import android.content.*; public class AlertDialog {public static DialogInterface.OnClickListener listener;public static int selected;public static class Builder {public Builder(Activity a){} public Builder setTitle(String s){return this;} public Builder setSingleChoiceItems(String[] s,int n,DialogInterface.OnClickListener l){selected=n;listener=l;return this;} public Builder setNegativeButton(String s,DialogInterface.OnClickListener l){return this;}public AlertDialog show(){return new AlertDialog();}}}''',
'com/veloster/i18n/UiText.java': '''package com.veloster.i18n;public class UiText {public static String t(String s){return s;}}''',
'Harness.java': '''import android.app.*;import android.content.*;import com.veloster.dashboard.vehicle.js.JsTransmissionSetting;public class Harness {static int changes;static void check(boolean b){if(!b)throw new AssertionError();}public static void main(String[] a){JsTransmissionSetting s=new JsTransmissionSetting(new Activity());check(s.get()==0);s.show(new Runnable(){public void run(){changes++;}});check(AlertDialog.selected==-1);DialogInterface d=new DialogInterface(){public void dismiss(){}};AlertDialog.listener.onClick(d,0);check(changes==1&&s.get()==1);JsTransmissionSetting recreated=new JsTransmissionSetting(new Activity());check(recreated.get()==1);recreated.show(new Runnable(){public void run(){changes++;}});check(AlertDialog.selected==0);AlertDialog.listener.onClick(d,1);check(changes==2&&new JsTransmissionSetting(new Activity()).get()==2);AlertDialog.listener.onClick(d,8);check(changes==2&&recreated.get()==2);System.out.println("PASS transmission selection persistence");}}'''
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
