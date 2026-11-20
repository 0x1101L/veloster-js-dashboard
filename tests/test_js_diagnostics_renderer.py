"""Render the actual JS diagnostics with synthetic observations, without Android IPC."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from graphics_test_support import GRAPHICS
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
UI_SOURCES = ('ArtworkLoader', 'ClusterRenderer', 'JsClusterRenderer', 'DashboardPage', 'GaugeEditor', 'GaugeLayout', 'GaugeRenderer', 'GraphEditor', 'GraphLayout', 'GraphRenderer', 'JsDiagnosticsRenderer', 'JsGaugeRenderer', 'JsOverviewRenderer', 'JsPerformanceRenderer', 'JsPitRenderer', 'OverviewLayout', 'OverviewRenderer', 'PerformanceRenderer', 'PitRenderer', 'SettingsPageRenderer', 'DashboardView')
HARNESS = '''import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.view.*;
import com.veloster.dashboard.view.performance.*;
import com.veloster.dashboard.debugger.*;
import com.veloster.dashboard.debugger.view.*;
import com.veloster.dashboard.view.f1.*;
import com.veloster.dashboard.view.graph.*;
import com.veloster.dashboard.view.gauge.*;
import com.veloster.dashboard.view.overview.*;import com.veloster.dashboard.theme.ThemeSpec;import com.veloster.i18n.UiText;import android.graphics.Canvas;
public class JsDiagnosticsPreview {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static int pixelDifference(Canvas a,Canvas b){int different=0;for(int y=60;y<480;y+=2)for(int x=0;x<800;x+=2)if(a.pixel(x,y)!=b.pixel(x,y))different++;return different;}
 static Telemetry fixture(String state){return fixture(state,"js");}
 static Telemetry fixture(String state,String vehicle){
  boolean pd=vehicle.equals("pd"),jsn=vehicle.equals("js_n"),js=vehicle.equals("js");
  Telemetry t=new Telemetry();t.profile(pd,js,jsn);
  if(state.equals("waiting"))return t;
  if(!state.equals("invalid")){
   if(js){
    t.hscanJs(0xc7,0,new byte[]{0,0,70,0,0,0,0,0},1000);
    t.hscanJs(0xc7,0x0a,new byte[]{0,0,0,(byte)142,0,0,0,0},1000);
    t.hscanJs(0xc7,0x20,new byte[]{0,1,32,33,34,35},1000);
    t.hscanJs(0xc7,0x0d,new byte[]{0,0,0,0,(byte)120,0,0,0},1000);
   }else if(pd){
    t.hscanPd(0xc7,8,new byte[]{0,0,70,0,0,0,0,0},1000);
    t.hscanPd(0xc7,2,new byte[]{0,70,0,0},1000);
   }else{
    t.hscanJsn(0xc7,7,new byte[]{0,0,74,8,32,0,0,0},1000);
    t.hscanJsn(0xc7,0x23,new byte[]{0,0,100,0,0,0,0,0},1000);
   }
   byte[] additional=new byte[40];additional[1]=94;additional[4]=4;additional[6]=1;additional[7]=0;
   additional[20]=1;additional[21]=44;additional[22]=1;
   for(int i=0;i<4;i++)additional[28+i*2]= (byte)(32+i);
   t.additional(additional,1000);
   t.gforce(new byte[]{(byte)0x89,0x31,0,20,0x7d,0x1f,0,0},1000);
  }
  if(state.equals("invalid")||state.equals("held")){
   if(js){
    t.hscanJs(0xc7,0,new byte[]{0,0,70,0,0,0,(byte)255,(byte)255},2000);
    t.hscanJs(0xc7,0x0a,new byte[]{0,0,0,(byte)255,0,0,0,0},2000);
    t.hscanJs(0xc7,0x20,new byte[]{0,0,(byte)255,(byte)255,(byte)255,(byte)255},2000);
   }else if(pd){
    t.hscanPd(0xc7,8,new byte[]{0,0,70,0,0,0,(byte)255,(byte)255},2000);
   }else{
    t.hscanJsn(0xc7,7,new byte[]{0,0,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255},2000);
   }
  }
  return t;
 }
 static JsDiagState observations(String state){
  JsDiagState d=new JsDiagState();
  if(state.equals("waiting"))return d;
  d.event("dct.service",state.equals("received")?"connected":state.equals("invalid")?"error":"stopped",1000);
  d.event("navigation.route.starting","start");d.event("navigation.route.source","sticky");d.event("navigation.route_state","3",1000);
  d.event("navigation.route.observer","registered");d.event("navigation.observer","registered");
  d.event("navigation.state_destination","-1",1300);d.event("navigation.notification","notice",1500);
  d.event("cluster.service","present; matching descriptor");d.event("class","com.hkmc.cluster.NavigationCluster");
  d.event("method","updateTBTInfo");d.event("micom.service","present");d.wifi(1,1,1,1,1);d.event("navigation.dipo.service","present");d.event("navigation.dipo.status","stat=1 turnByTurn=1",1400);d.hscan(1);d.event("binding.bcm","registered",1000);d.event("binding.bcm","received",1500);
  if(state.equals("received")||state.equals("held")) { d.lighting(0x80,0xe0,1700);d.lighting(0x00,0x00,1900); }
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
    if(!state.equals("waiting"))r.setBluetoothStatus(BluetoothRuntimeProbe.parse("11-17 01:00:00.000 I/bt_vendor( 123): kosti!! read local name for chipID\\n11-17 01:00:00.010 I/bt_vendor( 123): kosti!! read local name result is BCM4335\\n11-17 01:00:00.020 I/bt_vendor( 123): Found patchfile: /vendor/firmware/bcm4335.hcd\\n11-17 01:00:00.030 I/bt_vendor( 123): vendor lib fwcfg completed"));
    float categoryX=40+tab*154;
    check(r.selectDiagnosticTab(categoryX,80,categoryX,80),"category selected");
    check(!r.selectDiagnosticTab(30,80,200,80)&&!r.selectDiagnosticTab(30,50,30,80),"cross-tab drag and header press rejected");
    MediaState media=new MediaState();
    if(!state.equals("waiting")){media.mode(0x73);media.update(3,true,"Example Track",true,"Example Artist",true,12000,true,180000,false,1000);}
    Canvas c=new Canvas(800,height);r.draw(c,800,height,false,t,now,ShiftGuide.DEFAULT,false,media.snapshot(now));
    for(String text:c.texts){
     check(!text.contains("NaN")&&!text.contains("Infinity"),"invalid numbers hidden");
     if(language.equals("en"))check(!text.matches(".*[가-힣].*"),"English catalog: "+text);
    }
    if(tab==0){
     check(c.texts.contains(UiText.t("내비 수신"))&&c.texts.contains(UiText.t("HUD 정보")),"navigation and service evidence separated");
     if(state.equals("received"))check(c.texts.contains(UiText.t("초기 sticky"))&&c.texts.contains("-1")&&c.texts.contains("1.0s"),"sticky source, negative raw provider and app reception age");
     if(state.equals("invalid"))check(c.texts.contains(UiText.t("형식 오류"))&&c.texts.contains(UiText.t("권한 오류")),"failure reason retained");
    }else if(tab==1){
     check(c.texts.contains(UiText.t("CarPlay 미디어"))&&c.texts.contains(UiText.t("iAP2 연결 진단"))&&c.texts.contains(UiText.t("프로세스 로드"))&&c.texts.contains(UiText.t("Frida 후킹"))&&c.texts.contains(UiText.t("미확인")),"CarPlay media and native iAP2 evidence separated");
     if(state.equals("waiting"))check(!c.texts.contains("Example Track"),"no invented media");
     else check(c.texts.contains("Example Track")&&c.texts.contains("Example Artist"),"actual CarPlay media snapshot rendered");
    }else if(tab==2){
     check(c.texts.contains(UiText.t("신호별 수신 · 유효성")),"signal page");
     check(c.pixel(300,height-15)!=c.pixel(300,height-2),"vehicle diagnostics panel uses the available content height");
     check(c.texts.contains(UiText.t("DCT 관측"))&&c.texts.contains(UiText.t("변속기 종류")),"diagnostics reports transmission data");
     check(!c.texts.contains(UiText.t("변속기 설정")),"transmission setting moved out of diagnostics");
     check(c.texts.contains(UiText.t("차체 · 조명 (실험)"))&&c.texts.contains(UiText.t("원시 b3/b4"))&&c.texts.contains(UiText.t("비트 후보")),"lighting experiment sits below DCT on vehicle page");
     check(c.texts.contains(UiText.t("AUTO · Day/Night 의미 미확인")),"unverified lighting semantics remain explicit");
     if(state.equals("received"))check(c.texts.contains("00/00 · 2x · 0.1s")&&c.texts.contains("lamp7=0 sw=0"),"lighting shows latest bytes, candidate bits, count, and age");
     if(state.equals("received")){
      for(int signal:new int[]{10,11,12})check(t.sample(signal,now).hasDisplayValue(),"received preview seeds signal "+Telemetry.LABELS[signal]);
      check(c.texts.contains(String.format(java.util.Locale.US,"%+.2f G",t.sample(11,now).value))
       &&c.texts.contains(String.format(java.util.Locale.US,"%+.2f G",t.sample(12,now).value))
       &&c.texts.contains(String.format(java.util.Locale.US,"%.1f bar",t.sample(10,now).value)),"received preview renders brake pressure and both acceleration samples");
     }
     if(state.equals("waiting"))check(c.texts.contains(UiText.t("미수신")),"lighting stays unknown before first frame");
     String expected=state.equals("waiting")?"미수신":state.equals("invalid")?"무효 수신":state.equals("held")?"보관값":state.equals("expired")?"만료":"유효";
     check(c.texts.contains(UiText.t(expected)),"distinct signal state: "+state);
     if(state.equals("invalid"))check(!c.texts.contains("14.20 V")&&t.sample(Telemetry.BAT_VOLTAGE,now).receivedCount==1,"invalid-only reception is counted without a fabricated value");
     if(state.equals("held"))check(c.texts.contains("14.20 V")&&!c.texts.contains("2 · 0.0s / 1.0s")&&t.sample(Telemetry.BAT_VOLTAGE,now).receivedCount==2,"held value remains visible without the removed count and age column");
     if(state.equals("expired"))check(c.texts.contains("14.20 V"),"expired battery still shows its last valid voltage");
    }else if(tab==3){
     check(c.texts.contains(UiText.t("Wi-Fi 상태"))&&c.texts.contains(UiText.t("Wi-Fi 서비스")),"Wi-Fi status rendered");
     check(c.texts.contains(UiText.t("Bluetooth HAL 관찰"))&&c.texts.contains(UiText.t("컨트롤러"))&&c.texts.contains(UiText.t("로그 기록만으로는 미확정")),"Wi-Fi and Bluetooth share one page");
     if(state.equals("waiting"))check(c.texts.contains(UiText.t("Bluetooth HAL 기록 없음")),"missing log remains unknown");
     else check(c.texts.contains("BCM4335")&&c.texts.contains("bcm4335.hcd")&&c.texts.contains(UiText.t("HAL 펌웨어 초기화 완료")),"successful controller and patch evidence displayed");
    }else if(tab==4){
     check(c.texts.contains("BCM")&&c.texts.contains(UiText.t("DCT 서비스")),"connection sources rendered");
     if(!state.equals("waiting"))check(c.texts.contains(UiText.t("연결 상태 미확인")),"service presence does not claim connection");
     check(c.texts.contains(state.equals("received")?UiText.t("연결됨"):state.equals("waiting")?"—":UiText.t("연결 안 됨")),"connection state rendered");
    }else{
     throw new AssertionError("uncovered diagnostics page");
    }
    c.write(args[0]+"/diag-"+language+"-"+height+"-"+state+"-"+tab+".png");rendered++;
   }
  Telemetry chosen=fixture("waiting");chosen.jsTransmissionSetting(1);chosen.clear();
  check(chosen.dctTemperatures(1).transmissionSetting==1,"DCT choice survives ignition clear");
  chosen.jsTransmissionSetting(2);check(chosen.dctTemperatures(1).transmissionSetting==2,"MT choice replaces DCT");
  chosen.jsTransmissionSetting(99);check(chosen.dctTemperatures(1).transmissionSetting==0,"invalid choice becomes unknown");
  for(int kind=1;kind<=2;kind++){
   Telemetry selected=fixture("waiting");selected.jsTransmissionSetting(kind);selected.clear();
   JsPitRenderer sr=new JsPitRenderer();sr.setDiagnosticPage(true,observations("waiting").snapshot(1000));sr.selectDiagnosticTab(350,80,350,80);
   Canvas sc=new Canvas(800,480);sr.draw(sc,800,480,false,selected,1000,ShiftGuide.DEFAULT,false,new MediaState().snapshot(1000));
   check(sc.texts.contains(kind==1?"DCT":"MT"),"saved transmission displayed");
   check(!sc.texts.contains("MT (test)")&&!sc.texts.contains("DCT (test)"),"stored selection overrides trial");
  }
  JsPitRenderer hit=new JsPitRenderer();check(!hit.wifiSettingsHit(300,137),"Wi-Fi settings requires its diagnostics category");
  hit.selectDiagnosticTab(500,80,500,80);check(hit.wifiSettingsHit(300,137)&&!hit.wifiSettingsHit(300,170),"Wi-Fi settings hit restricted to button");
  Telemetry t=fixture("held");long before=t.sample(Telemetry.BAT_VOLTAGE,2000).receivedCount;
  t.snapshot(9000);check(t.sample(Telemetry.BAT_VOLTAGE,9000).receivedCount==before,"redraw cannot simulate new reception");
  t.clear();check(t.sample(Telemetry.BAT_VOLTAGE,10000).receivedCount==0&&!t.sample(Telemetry.BAT_VOLTAGE,10000).seen,"ignition clears reception evidence");
  for(int tab=0;tab<5;tab++){
   JsPitRenderer a=new JsPitRenderer(),b=new JsPitRenderer();a.setDiagnosticPage(true,observations("received").snapshot(2000));b.setDiagnosticPage(true,observations("received").snapshot(2000));b.setTheme(ThemeSpec.auroraFocus());a.selectDiagnosticTab(40+tab*154,80,40+tab*154,80);b.selectDiagnosticTab(40+tab*154,80,40+tab*154,80);
   Canvas ca=new Canvas(800,480),cb=new Canvas(800,480);a.draw(ca,800,480,false,fixture("received"),2000);b.draw(cb,800,480,false,fixture("received"),2000);
   check(pixelDifference(ca,cb)>0&&ca.texts.equals(cb.texts),"diagnostics theme changes styling and preserves content on tab "+tab);
  }
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
 if(y>55&&(x<0||(x+p.measureText(s))*g.getTransform().getScaleX()>image.getWidth()+.1f||y*g.getTransform().getScaleY()>image.getHeight()+.1f))
  throw new AssertionError("Diagnostic text outside logical bounds: "+s);
 texts.add(s);''')
            p = root / "android/graphics" / (name + ".java")
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("package android.graphics;\n" + body)
            sources.append(p)
        harness = root / "JsDiagnosticsPreview.java"
        harness.write_text(HARNESS)
        sources.append(harness)
        names = ("Telemetry", "ThemeSpec", "ThemePalette", "ThemeResources", "ThemeLayout", "PdPowertrain", "PdGearState", "JsPowertrain", "JsGearState", "SettingsPageRenderer",
                 "JsTelemetryDecoder", "JsDiagState", "BluetoothRuntimeProbe", "JsPitRenderer", "ClusterRenderer", "JsClusterRenderer", "JsOverviewRenderer", "JsDiagnosticsRenderer", "JsGaugeRenderer",
                 "JsPerformanceRenderer", "DashboardPage", "ShiftGuide", "MediaState",
                 "GaugeLayout", "GaugeRenderer", "OverviewLayout", "OverviewCardRenderer", "GraphLayout", "GraphRenderer")
        groups = {'OverviewLayout': 'overview', 'OverviewRenderer': 'overview', 'OverviewCardRenderer': 'overview', 'JsOverviewRenderer': 'overview', 'GaugeEditor': 'gauge', 'GaugeLayout': 'gauge', 'GaugeRenderer': 'gauge', 'JsGaugeRenderer': 'gauge', 'GraphEditor': 'graph', 'GraphLayout': 'graph', 'GraphRenderer': 'graph', 'ClusterRenderer': 'cluster', 'JsClusterRenderer': 'cluster', 'JsDiagnosticsRenderer': 'diagnostics', 'PerformanceRenderer': 'performance', 'JsPerformanceRenderer': 'performance'}
        def source_path(name):
            if name == "ThemeSpec": return ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/ThemeSpec.java"
            if name == "ThemePalette": return ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/ThemePalette.java"
            if name == "ThemeResources": return ROOT / "tests/java_stubs/com/veloster/dashboard/theme/ThemeResources.java"
            if name in ("JsDiagState", "JsLogger", "BluetoothRuntimeProbe", "JsDiagnosticsRenderer"): return ROOT / ("apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/view" if name == "JsDiagnosticsRenderer" else "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger") / (name + ".java")
            if name == "ThemeLayout": return ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/view/overview/ThemeLayout.java"
            if name in groups: return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/view" / ("f1" if groups[name] == "cluster" else groups[name]) / (name + ".java")
            if name in UI_SOURCES: return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/view" / (name + ".java")
            if name in ("JsPowertrain", "JsGearState", "JsTelemetryDecoder"): return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/js" / (name + ".java")
            if name in ("PdPowertrain", "PdGearState"): return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/pd" / (name + ".java")
            return ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle" / (name + ".java")
        sources += [source_path(name) for name in names]
        sources += [ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard/view' / (name + '.java') for name in ('MediaBar', 'MenuIcons')]
        sources.append(ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/ThemeSurface.java")
        sources.append(ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/LiquidGlassEngine.java")
        sources += [ROOT / "tests/java_stubs/android/content/Context.java", ROOT / "tests/java_stubs/android/content/res/AssetManager.java"]
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
