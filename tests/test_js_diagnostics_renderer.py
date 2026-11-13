"""Render the actual JS diagnostics with synthetic observations, without Android IPC."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from graphics_test_support import GRAPHICS
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
UI_SOURCES = ('ArtworkLoader', 'ClusterRenderer', 'DashboardPage', 'GaugeEditor', 'GaugeLayout', 'GaugeRenderer', 'GraphEditor', 'GraphLayout', 'GraphRenderer', 'JsDiagnosticsRenderer', 'JsGaugeRenderer', 'JsOverviewRenderer', 'JsPerformanceRenderer', 'JsPitRenderer', 'OverviewLayout', 'OverviewRenderer', 'PerformanceRenderer', 'PitRenderer', 'DashboardView')
HARNESS = '''import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.view.*;
import com.veloster.dashboard.view.performance.*;
import com.veloster.dashboard.debugger.*;
import com.veloster.dashboard.debugger.view.*;
import com.veloster.dashboard.view.f1.*;
import com.veloster.dashboard.view.graph.*;
import com.veloster.dashboard.view.gauge.*;
import com.veloster.dashboard.view.overview.*;import com.veloster.i18n.UiText;import android.graphics.Canvas;
public class JsDiagnosticsPreview {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Telemetry fixture(String state){
  Telemetry t=new Telemetry();t.profile(false,true,false);
  if(state.equals("waiting"))return t;
  if(!state.equals("invalid")){
   t.hscanJs(0xc7,0,new byte[]{0,0,70,0,0,0,0,0},1000);
   t.hscanJs(0xc7,0x0a,new byte[]{0,0,0,(byte)142,0,0,0,0},1000);
   t.hscanJs(0xc7,0x20,new byte[]{0,1,32,33,34,35},1000);
   t.hscanJs(0xc7,0x0d,new byte[]{0,0,0,0,(byte)120,0,0,0},1000);
  }
  if(state.equals("invalid")||state.equals("held")){
   t.hscanJs(0xc7,0,new byte[]{0,0,70,0,0,0,(byte)255,(byte)255},2000);
   t.hscanJs(0xc7,0x0a,new byte[]{0,0,0,(byte)255,0,0,0,0},2000);
   t.hscanJs(0xc7,0x20,new byte[]{0,0,(byte)255,(byte)255,(byte)255,(byte)255},2000);
  }
  return t;
 }
 static JsDiagState observations(String state){
  JsDiagState d=new JsDiagState();
  if(state.equals("waiting"))return d;
  d.event("navigation.route.starting","start");d.event("navigation.route.source","sticky");d.event("navigation.route_state","3",1000);
  d.event("navigation.route.observer","registered");d.event("navigation.observer","registered");
  d.event("navigation.state_destination","-1",1300);d.event("navigation.notification","notice",1500);
  d.event("cluster.service","present; matching descriptor");d.event("class","com.hkmc.cluster.NavigationCluster");
  d.event("method","updateTBTInfo");d.event("micom.service","present");d.wifi(1,1,1,1);d.hscan(1);d.event("binding.bcm","registered",1000);d.event("binding.bcm","received",1500);
  if(state.equals("invalid")){d.event("navigation.route.invalid","bad payload");d.event("navigation.state.error","SecurityException");}
  return d;
 }
 public static void main(String[] args)throws Exception{
  int rendered=0;
  for(String language:new String[]{"ko","en"})for(int height:new int[]{480,425})
   for(String state:new String[]{"waiting","received","invalid","held","expired"})for(int tab=0;tab<5;tab++){
    UiText.configure(language,language);Telemetry t=fixture(state);JsDiagState d=observations(state);
    long now=state.equals("expired")?62000:2000;
    JsPitRenderer r=new JsPitRenderer();r.setDiagnosticPage(true,d.snapshot(now));
    check(r.selectDiagnosticTab(40+tab*154,80,40+tab*154,80),"tab selected");
    check(!r.selectDiagnosticTab(40,80,200,80)&&!r.selectDiagnosticTab(40,50,40,80),"cross-tab drag and header press rejected");
    check(r.isScreenProbeTap(500,350,500,350)==(tab==1),"screen probe only on CarPlay tab");
    MediaState media=new MediaState();
    if(!state.equals("waiting")){media.mode(0x73);media.update(3,true,"Example Track",true,"Example Artist",true,12000,true,180000,false,1000);}
    Canvas c=new Canvas(800,height);r.draw(c,800,height,false,t,now,ShiftGuide.DEFAULT,false,media.snapshot(now));
    for(String text:c.texts){
     check(!text.contains("NaN")&&!text.contains("Infinity"),"invalid numbers hidden");
     if(language.equals("en"))check(!text.matches(".*[가-힣].*"),"English catalog: "+text);
    }
    if(tab==0){
     check(c.texts.contains(UiText.t("내비 수신"))&&c.texts.contains(UiText.t("서비스 · 확인 범위")),"navigation and service evidence separated");
     if(state.equals("received"))check(c.texts.contains(UiText.t("초기 sticky"))&&c.texts.contains("-1")&&c.texts.contains("1.0s"),"sticky source, negative raw provider and app reception age");
     if(state.equals("invalid"))check(c.texts.contains(UiText.t("형식 오류"))&&c.texts.contains(UiText.t("권한 오류")),"failure reason retained");
    }else if(tab==1){
     check(c.texts.contains(UiText.t("CarPlay 미디어"))&&c.texts.contains(UiText.t("CarPlay 내비게이션"))&&c.texts.contains(UiText.t("미연결")),"CarPlay media and unavailable navigation separated");
     if(state.equals("waiting"))check(!c.texts.contains("Example Track"),"no invented media");
     else check(c.texts.contains("Example Track")&&c.texts.contains("Example Artist"),"actual CarPlay media snapshot rendered");
    }else if(tab==2){
     check(c.texts.contains(UiText.t("신호별 수신 · 유효성")),"signal page");
     String expected=state.equals("waiting")?"미수신":state.equals("invalid")?"무효 수신":state.equals("held")?"보관값":state.equals("expired")?"만료":"유효";
     check(c.texts.contains(UiText.t(expected)),"distinct signal state: "+state);
     if(state.equals("invalid"))check(!c.texts.contains("14.20 V")&&t.sample(Telemetry.BAT_VOLTAGE,now).receivedCount==1,"invalid-only reception is counted without a fabricated value");
     if(state.equals("held"))check(c.texts.contains("14.20 V")&&c.texts.contains("0.0s / 1.0s")&&t.sample(Telemetry.BAT_VOLTAGE,now).receivedCount==2,"held value keeps separate reception and valid ages");
     if(state.equals("expired"))check(!c.texts.contains("14.20 V"),"expired battery value hidden");
    }else if(tab==4){
     check(c.texts.contains("bcm")&&c.texts.contains("media.4"),"binding sources rendered");
     if(!state.equals("waiting"))check(c.texts.contains("registered / 1 / 0.5s")||state.equals("expired"),"binding status and reception age rendered");
    }else{
     check(c.texts.contains(UiText.t("미연결"))&&c.texts.contains(UiText.t("응답 해석만 구현 · 자동 진단 요청 없음")),"DCT transport boundary explicit");
     if(!state.equals("waiting"))check(c.texts.contains(UiText.t("꺼짐")),"Wi-Fi state 1 means disabled");
    }
    c.write(args[0]+"/diag-"+language+"-"+height+"-"+state+"-"+tab+".png");rendered++;
   }
  Telemetry t=fixture("held");long before=t.sample(Telemetry.BAT_VOLTAGE,2000).receivedCount;
  t.snapshot(9000);check(t.sample(Telemetry.BAT_VOLTAGE,9000).receivedCount==before,"redraw cannot simulate new reception");
  t.clear();check(t.sample(Telemetry.BAT_VOLTAGE,10000).receivedCount==0&&!t.sample(Telemetry.BAT_VOLTAGE,10000).seen,"ignition clears reception evidence");
  System.out.println("PASS "+rendered+" JS diagnostic renders");
 }
}'''


def render(folder):
    java, javac = java_tools()
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="js-diag-render-") as tmp:
        root = Path(tmp)
        sources = []
        for name, body in GRAPHICS.items():
            if name == "Canvas":
                body = body.replace("texts.add(s);", '''
 if(y>55&&(x<0||x+p.measureText(s)>800.1f||y>480))
  throw new AssertionError("Diagnostic text outside logical bounds: "+s);
 texts.add(s);''')
            p = root / "android/graphics" / (name + ".java")
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("package android.graphics;\n" + body)
            sources.append(p)
        harness = root / "JsDiagnosticsPreview.java"
        harness.write_text(HARNESS)
        sources.append(harness)
        names = ("Telemetry", "PdPowertrain", "PdGearState", "JsPowertrain", "JsGearState",
                 "JsTelemetryDecoder", "JsDiagState", "JsPitRenderer", "ClusterRenderer", "JsOverviewRenderer", "JsDiagnosticsRenderer", "JsGaugeRenderer",
                 "JsPerformanceRenderer", "DashboardPage", "ShiftGuide", "MediaState",
                 "GaugeLayout", "GaugeRenderer", "OverviewLayout", "GraphLayout", "GraphRenderer")
        groups = {'OverviewLayout': 'overview', 'OverviewRenderer': 'overview', 'JsOverviewRenderer': 'overview', 'GaugeEditor': 'gauge', 'GaugeLayout': 'gauge', 'GaugeRenderer': 'gauge', 'JsGaugeRenderer': 'gauge', 'GraphEditor': 'graph', 'GraphLayout': 'graph', 'GraphRenderer': 'graph', 'ClusterRenderer': 'cluster', 'JsDiagnosticsRenderer': 'diagnostics', 'PerformanceRenderer': 'performance', 'JsPerformanceRenderer': 'performance'}
        def source_path(name):
            if name in ("JsDiagState", "JsLogger", "JsDiagnosticsRenderer"): return ROOT / ("apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/view" if name == "JsDiagnosticsRenderer" else "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger") / (name + ".java")
            if name in groups: return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/view" / ("f1" if groups[name] == "cluster" else groups[name]) / (name + ".java")
            if name in UI_SOURCES: return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/view" / (name + ".java")
            if name in ("JsPowertrain", "JsGearState", "JsTelemetryDecoder"): return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/js" / (name + ".java")
            if name in ("PdPowertrain", "PdGearState"): return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/pd" / (name + ".java")
            return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle" / (name + ".java")
        sources += [source_path(name) for name in names]
        sources += [ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/pd/PdTelemetryDecoder.java", ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/jsn/JsnTelemetryDecoder.java"]
        sources.append(ROOT / "shared/src/com/veloster/i18n/UiText.java")
        result = subprocess.run([javac, "-encoding", "UTF-8", "-d", str(root), *map(str, sources)],
                                capture_output=True, text=True)
        if result.returncode:
            raise AssertionError(result.stderr)
        result = subprocess.run([java, "-Djava.awt.headless=true", "-cp", str(root),
                                 "JsDiagnosticsPreview", str(folder.resolve())],
                                capture_output=True, text=True, timeout=50)
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)
        return result.stdout.strip()


class JsDiagnosticsRendererTests(unittest.TestCase):
    def test_observed_states(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertIn("PASS 100", render(folder))


if __name__ == "__main__":
    import sys
    if len(sys.argv) == 3 and sys.argv[1] == "--out":
        print(render(sys.argv[2]))
    else:
        unittest.main()
