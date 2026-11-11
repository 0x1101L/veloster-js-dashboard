"""Render the actual new dashboard page; fixtures never enter the APK."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from graphics_test_support import GRAPHICS
from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
HARNESS = '''import com.veloster.vehicle.*;import com.veloster.i18n.UiText;import android.graphics.Canvas;
public class PerformancePreview {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 static Telemetry fixture(){
  Telemetry t=new Telemetry();t.profile(false,false,true);t.speed(75,1000);t.units(new byte[]{2,60,108},1000);
  t.rpm(new byte[]{0,0x22,0x38,0,0,0,0,0},1000);t.temperatures(new byte[]{(byte)176,(byte)185},1000);
  byte[] a=new byte[40];a[4]=3;a[7]=55;a[1]=10;a[20]=(byte)0xff;a[21]=(byte)0x79;a[22]=1;a[29]=35;a[31]=37;a[33]=36;a[35]=35;t.additional(a,1000);
  t.gforce(new byte[]{(byte)0x89,0x31,0,0,0x7d,0x1f,0,0},1000);
  t.hscanJsn(0xc7,7,new byte[]{0,0,74,8,32,0,0,0},1000);t.hscanJsn(0xc7,0x23,new byte[]{0,0,100,0,0,0,0,0},1000);return t;
 }
 static void gear(Telemetry t,int function,int value,long time){
  t.hscanJs(0xc7,function,new byte[]{0,(byte)value,0,0,0,0,0,0},time);
 }
 static void jsGearPages(Telemetry t,long now,String mode,String number){
  for(int page=0;page<6;page++){
   Canvas c=new Canvas(800,480);JsPitRenderer r=new JsPitRenderer();
   if(page==1)r.setPerformancePage(true);
   if(page==2){GaugeLayout layout=new GaugeLayout();layout.set(0,8,2);r.setGaugeLayout(layout);}
   if(page==3){GraphLayout layout=new GraphLayout();layout.signal(0,8);r.setGraphLayout(layout);}
   r.draw(c,800,480,page>=4,t,now,6000,page==5,null);
   boolean found=false;for(String text:c.texts)if(text.endsWith(" · "+mode))found=true;
   check(found&&c.texts.contains(page==3?number+" ":number)&&!c.texts.contains(mode+number),"separate JS mode and received gear on page "+page+": "+c.texts);
  }
 }
 public static void main(String[] args)throws Exception{
  int rendered=0;
  for(String language:new String[]{"ko","en"})for(int height:new int[]{480,425})for(String state:new String[]{"received","waiting","stale","invalid"}){
   UiText.configure(language,language);Telemetry t=state.equals("waiting")?new Telemetry():fixture();if(state.equals("waiting"))t.profile(false,false,true);
   if(state.equals("invalid")){t.additional(null,1000);t.rpm(null,1000);t.temperatures(null,1000);t.gforce(null,1000);}
   Canvas c=new Canvas(800,height);PitRenderer renderer=new PitRenderer();renderer.setPerformancePage(true);renderer.draw(c,800,height,false,t,state.equals("stale")?61000:1000);
   check(c.texts.contains("PERFORMANCE")&&c.texts.contains("TPMS"),"new page rendered");
   for(String text:c.texts){check(!text.contains("NaN")&&!text.contains("Infinity"),"no invalid numeric text");if(language.equals("en"))check(!text.matches(".*[가-힣].*"),"English translation: "+text);}
   check(!c.texts.contains("D")&&!c.texts.contains("SPORT"),"no unsupported transmission/mode");
   if(state.equals("received")){check(c.texts.contains("2,190")&&c.texts.contains("75")&&c.texts.contains("3"),"received headline");check(c.texts.contains("14.20 V")&&c.texts.contains("+13.5 °")&&c.texts.contains("37"),"battery steering tires");}
   if(state.equals("waiting")||state.equals("stale")){check(!c.texts.contains("75")&&!c.texts.contains("2,190")&&!c.texts.contains("37"),"no absent/expired readings");int unknown=0;for(String text:c.texts)if(text.startsWith("—"))unknown++;check(unknown==17,"all 17 values unknown: "+unknown);}
   if(state.equals("invalid"))check(!c.texts.contains("2,190")&&!c.texts.contains("37"),"invalid packet clears readings");
   c.write(args[0]+"/performance-"+language+"-"+height+"-"+state+".png");rendered++;
  }
  UiText.configure("en","en");
  for(int page=0;page<4;page++){
   Canvas c=new Canvas(800,480);PitRenderer r=new PitRenderer();if(page==2)r.setGaugeLayout(new GaugeLayout());if(page==3)r.setGraphLayout(new GraphLayout());r.draw(c,800,480,page==1,fixture(),1000);
   check(!c.texts.contains("PERFORMANCE"),"existing page remains selectable");c.write(args[0]+"/existing-page-"+page+".png");
  }
  Telemetry js=new Telemetry();js.profile(false,true,false);js.temperatures(new byte[]{100,120},1000);js.temperatures(new byte[]{(byte)255,(byte)255},2000);
  for(int page=0;page<4;page++){
   Canvas c=new Canvas(800,480);JsPitRenderer r=new JsPitRenderer();if(page==1)r.setPerformancePage(true);if(page==2){GaugeLayout layout=new GaugeLayout();r.setGaugeLayout(layout);}if(page==3)r.setGraphLayout(new GraphLayout());r.draw(c,800,480,false,js,3000);
   if(page<2){boolean found=false;for(String text:c.texts)if(text.contains("LAST 2s"))found=true;check(found,"JS held value labeled on page "+page);}
  }
  Telemetry gearbox=new Telemetry();gearbox.profile(false,true,false);
  gear(gearbox,0x1d,5,4000);gear(gearbox,0x1e,3,4050);
  jsGearPages(gearbox,4050,"D","3");
  gear(gearbox,0x1d,4,4100);
  Canvas fallback=new Canvas(800,480);JsPitRenderer r=new JsPitRenderer();r.setPerformancePage(true);r.draw(fallback,800,480,false,gearbox,4100);
  check(fallback.texts.contains("M")&&!fallback.texts.contains("GEAR · M")&&!fallback.texts.contains("3"),"selector-only fallback keeps M without recycling the D gear");
  gear(gearbox,0x1e,3,4150);jsGearPages(gearbox,4150,"M","3");
  gear(gearbox,0x1e,4,4200);jsGearPages(gearbox,4200,"M","4");
  gear(gearbox,0x1e,3,4250);jsGearPages(gearbox,4250,"M","3");
  gear(gearbox,0x1e,7,4300);jsGearPages(gearbox,4300,"M","7");
  gear(gearbox,0x1d,5,4350);gear(gearbox,0x1e,6,4400);jsGearPages(gearbox,4400,"D","6");
  System.out.println("PASS "+rendered+" performance renders and 4 existing page renders");
 }
}'''

def render(folder):
    java, javac = java_tools()
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='vn-render-') as tmp:
        root = Path(tmp)
        sources = []
        for name, body in GRAPHICS.items():
            p = root / 'android/graphics' / (name + '.java')
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('package android.graphics;\n' + body)
            sources.append(p)
        p = root / 'PerformancePreview.java'
        p.write_text(HARNESS)
        sources.append(p)
        sources += [ROOT / 'shared/src/com/veloster/vehicle' / (name + '.java') for name in ('Telemetry','PdPowertrain','PdGearState','JsPowertrain','JsGearState','JsTelemetryDecoder','JsPitRenderer','JsGaugeRenderer','JsPerformanceRenderer','PitRenderer','PerformanceRenderer','DashboardPage','ShiftGuide','MediaState','GaugeLayout','GaugeRenderer','OverviewLayout','GraphLayout','GraphRenderer')]
        sources.append(ROOT / 'shared/src/com/veloster/i18n/UiText.java')
        result = subprocess.run([javac,'-encoding','UTF-8','-d',str(root),*map(str,sources)],capture_output=True,text=True)
        if result.returncode: raise AssertionError(result.stderr)
        result = subprocess.run([java,'-Djava.awt.headless=true','-cp',str(root),'PerformancePreview',str(folder.resolve())],capture_output=True,text=True,timeout=40)
        if result.returncode: raise AssertionError(result.stdout + result.stderr)
        return result.stdout.strip()

class PerformanceRendererTests(unittest.TestCase):
    def test_rendered_states(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIn('PASS 16',render(tmp))

if __name__ == '__main__':
    import sys
    if len(sys.argv)==3 and sys.argv[1]=='--out': print(render(sys.argv[2]))
    else: unittest.main()
