"""Render the actual JS diagnostics with synthetic observations, without Android IPC."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from graphics_test_support import GRAPHICS
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
HARNESS = '''import com.veloster.vehicle.*;import com.veloster.i18n.UiText;import android.graphics.Canvas;
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
  d.event("method","updateTBTInfo");d.event("micom.service","present");d.wifi(1,1,1,1);d.hscan(1);
  if(state.equals("invalid")){d.event("navigation.route.invalid","bad payload");d.event("navigation.state.error","SecurityException");}
  return d;
 }
 public static void main(String[] args)throws Exception{
  int rendered=0;
  for(String language:new String[]{"ko","en"})for(int height:new int[]{480,425})
   for(String state:new String[]{"waiting","received","invalid","held","expired"})for(int tab=0;tab<3;tab++){
    UiText.configure(language,language);Telemetry t=fixture(state);JsDiagState d=observations(state);
    long now=state.equals("expired")?62000:2000;
    JsPitRenderer r=new JsPitRenderer();r.setDiagnosticPage(true,d.snapshot(now));
    check(r.selectDiagnosticTab(40+tab*252,80,40+tab*252,80),"tab selected");
    check(!r.selectDiagnosticTab(40,80,292,80)&&!r.selectDiagnosticTab(40,50,40,80),"cross-tab drag and header press rejected");
    Canvas c=new Canvas(800,height);r.draw(c,800,height,false,t,now);
    for(String text:c.texts){
     check(!text.contains("NaN")&&!text.contains("Infinity"),"invalid numbers hidden");
     if(language.equals("en"))check(!text.matches(".*[가-힣].*"),"English catalog: "+text);
    }
    if(tab==0){
     check(c.texts.contains(UiText.t("내비 수신"))&&c.texts.contains(UiText.t("서비스 · 확인 범위")),"navigation and service evidence separated");
     if(state.equals("received"))check(c.texts.contains(UiText.t("초기 sticky"))&&c.texts.contains("-1")&&c.texts.contains("1.0s"),"sticky source, negative raw provider and app reception age");
     if(state.equals("invalid"))check(c.texts.contains(UiText.t("형식 오류"))&&c.texts.contains(UiText.t("권한 오류")),"failure reason retained");
    }else if(tab==1){
     check(c.texts.contains(UiText.t("신호별 수신 · 유효성")),"signal page");
     String expected=state.equals("waiting")?"미수신":state.equals("invalid")?"무효 수신":state.equals("held")?"보관값":state.equals("expired")?"만료":"유효";
     check(c.texts.contains(UiText.t(expected)),"distinct signal state: "+state);
     if(state.equals("invalid"))check(!c.texts.contains("14.20 V")&&t.sample(Telemetry.BAT_VOLTAGE,now).receivedCount==1,"invalid-only reception is counted without a fabricated value");
     if(state.equals("held"))check(c.texts.contains("14.20 V")&&c.texts.contains("0.0s / 1.0s")&&t.sample(Telemetry.BAT_VOLTAGE,now).receivedCount==2,"held value keeps separate reception and valid ages");
     if(state.equals("expired"))check(!c.texts.contains("14.20 V"),"expired battery value hidden");
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
                 "JsTelemetryDecoder", "JsDiagState", "JsPitRenderer", "JsGaugeRenderer",
                 "JsPerformanceRenderer", "DashboardPage", "ShiftGuide", "MediaState",
                 "GaugeLayout", "GaugeRenderer", "OverviewLayout", "GraphLayout", "GraphRenderer")
        sources += [ROOT / "shared/src/com/veloster/vehicle" / (name + ".java") for name in names]
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
            self.assertIn("PASS 60", render(folder))


if __name__ == "__main__":
    import sys
    if len(sys.argv) == 3 and sys.argv[1] == "--out":
        print(render(sys.argv[2]))
    else:
        unittest.main()
