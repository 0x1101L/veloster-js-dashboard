#!/usr/bin/env python3
"""Render an isolated F1-2 material study with the current DashboardView."""
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT / "tools"))
import dashboard_runtime as runtime

PROBE = """
import android.app.Activity;
import com.veloster.dashboard.view.DashboardView;
import com.veloster.i18n.LanguageSettings;
public final class GlassStudyProbe {
 public static void main(String[] args)throws Exception {
  DashboardDesktop.fixture("received","js");
  com.veloster.dashboard.vehicle.Telemetry t=(com.veloster.dashboard.vehicle.Telemetry)
   DashboardDesktop.field(DashboardView.class,"DATA");
  java.lang.reflect.Method put=t.getClass().getDeclaredMethod("put",int.class,float.class,boolean.class,long.class);
  put.setAccessible(true);
  int[] signals={0,7,8,2,com.veloster.dashboard.vehicle.Telemetry.INTAKE,
   com.veloster.dashboard.vehicle.Telemetry.BOOST,com.veloster.dashboard.vehicle.Telemetry.ACCEL_PEDAL,10};
  float[] values={4560,124,4,91,34,0.8f,38,0};
  for(int i=0;i<signals.length;i++)put.invoke(t,signals[i],values[i],true,1000L);
  t.hscanJs(0xc7,0x1d,new byte[]{0,5,0,0,0,0,0,0},1000);
  t.hscanJs(0xc7,0x1e,new byte[]{0,4,0,0,0,0,0,0},1000);
  t.hscanJs(0xc7,0x04,new byte[]{0,0,0,0,0,0x10,0,0},1000);
  Activity a=new Activity();
  LanguageSettings.save(a,"en");
  a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2").apply();
  a.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("dashboard_page",1).apply();
  DashboardView view=DashboardDesktop.create(a);
  com.veloster.dashboard.theme.ThemeSpec theme=
   (com.veloster.dashboard.theme.ThemeSpec)DashboardDesktop.field(view,"activeTheme");
  if(!"f1_2".equals(theme.id))throw new AssertionError("F1-2 failed to load");
  DashboardDesktop.draw(view).write(args[0]);
  DashboardDesktop.tap(view,40,com.veloster.dashboard.view.DashboardPage.shellTop(theme,6)+theme.shell().buttonHeight/2);
  DashboardDesktop.draw(view).write(args[1]);
 }
}
"""

def merge(target, patch):
    for key, value in patch.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            merge(target[key], value)
        else:
            target[key] = value

def main():
    output = HERE / "artifacts" / "f1-2-glass-study"
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="glass-study-") as tmp:
        folder = Path(tmp)
        java, classes = runtime.compile_runtime(folder)
        source = folder / "GlassStudyProbe.java"
        source.write_text(PROBE)
        javac = str(Path(java).with_name("javac"))
        subprocess.run([javac, "-encoding", "UTF-8", "-cp", str(classes),
                        "-d", str(classes), str(source)], check=True)
        assets = folder / "assets"
        shutil.copytree(ROOT / "apps/dashboard/src/0x1101L/assets", assets)
        # This visual study needs only the default image, not the wallpaper picker.
        # Keep the in-progress wallpaper collection out of the temporary overlay.
        components = assets / "themes/f1_2/components.json"
        source_config = json.loads((HERE / "themes/f1-2/dashboard.json").read_text())
        temporary_components = {key: source_config[key] for key in
                                ("spacing", "material", "decorations") if key in source_config}
        temporary_components["assets"] = {"background": "backgrounds/blue.png"}
        components.write_text(json.dumps(temporary_components, ensure_ascii=False, indent=2) + "\n")
        def render(label):
            subprocess.run([java, "-Djava.awt.headless=true",
                            "-Ddashboard.assets=" + str(assets), "-cp", str(classes),
                            "GlassStudyProbe", str(output / (label + ".png")),
                            str(output / (label + "_settings.png"))], check=True)
        render("current")
        for name in ("colors.json", "components.json"):
            path = assets / "themes/f1_2" / name
            value = json.loads(path.read_text())
            merge(value, json.loads((HERE / "themes/f1-2-glass-study" / name).read_text()))
            path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
        render("study")
    print("Rendered current and study F1/settings images:", output)

if __name__ == "__main__":
    main()
