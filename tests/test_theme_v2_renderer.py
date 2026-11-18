#!/usr/bin/env python3
"""Exercise schema v2 with production renderers/resources on the desktop shim."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import dashboard_runtime as runtime
from test_quick_entry_runtime import java_tools

PROBE = r'''
import com.veloster.dashboard.theme.*;
import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.view.*;
import com.veloster.dashboard.view.f1.*;
import com.veloster.dashboard.view.overview.*;
import android.graphics.*;
import android.app.Activity;
import java.nio.file.*;
import java.util.*;
public final class ThemeV2Probe {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static ThemeSpec changed(String key,int value)throws Exception{
  String root=System.getProperty("dashboard.v2fixtures")+"/";
  String catalog=new String(Files.readAllBytes(Paths.get(root+"catalog.json")),"UTF-8");
  Map<String,String> docs=new LinkedHashMap<String,String>();
  for(String id:new String[]{"original","aurora_focus","carplay_glass_light","carplay_glass_dark"}){
   String text=new String(Files.readAllBytes(Paths.get(root+id+"/manifest.json")),"UTF-8");
   if(id.equals("aurora_focus"))text=text.replaceAll("(\""+key+"\"\\s*:\\s*)[0-9]+","$1"+value);
   docs.put(id,text);
  }
  ThemeCatalog c=ThemeCatalog.fromJsonDocuments(catalog,docs);check(c.size()==4,"valid custom property: "+key);return c.find("aurora_focus");
 }
 static Canvas cluster(ThemeSpec theme){
  Telemetry model=new Telemetry();model.profile(false,true,false);
  model.hscanJs(0xc7,0x1d,new byte[]{0,5,0,0,0,0,0,0},1000);
  model.hscanJs(0xc7,0x1e,new byte[]{0,4,0,0,0,0,0,0},1000);
  JsClusterRenderer r=new JsClusterRenderer();r.setTheme(theme,ThemeResources.load(new Activity(),theme));
  r.prepare(model,model.snapshot(1000),1000,6000,false,null,null);
  Canvas c=new Canvas(800,480);r.drawCluster(c);return c;
 }
 static Canvas.TextDraw text(Canvas c,String s){for(Canvas.TextDraw d:c.commands)if(d.text.equals(s))return d;throw new AssertionError("missing: "+s);}
 static Canvas frame(ThemeSpec theme,ThemeResources resources,boolean js,int width,int height){
  Canvas c=new Canvas(width,height);Telemetry t=new Telemetry();t.profile(false,js,!js);
  if(js){JsPitRenderer r=new JsPitRenderer();r.setTheme(theme,resources);r.draw(c,width,height,false,t,1000);}
  else{PitRenderer r=new PitRenderer();r.setTheme(theme,resources);r.draw(c,width,height,false,t,1000,6000,false,null);}
  return c;
 }
 static void checkSharedSurfaces(ThemeSpec theme,ThemeSurface surface){
  Canvas[] canvases=new Canvas[6];for(int i=0;i<canvases.length;i++){canvases[i]=new Canvas(100,100);canvases[i].drawColor(0xffffffff);}
  int fill=theme.panel,edge=theme.stroke;
  surface.panel(canvases[0],theme,5,5,90,90,fill,edge);
  surface.navigationButton(canvases[1],theme,null,new Paint(3),null,5,5,90,90,false,theme.accent);
  surface.card(canvases[2],theme,5,5,90,90,3,fill); // Grid geometry cannot change the shared radius.
  surface.settingsGroup(canvases[3],theme,5,5,90,90);
  surface.draw(canvases[4],theme,ThemeSpec.SurfaceRole.CONTROL_BUTTON,5,5,90,90,fill,edge);
  surface.draw(canvases[5],theme,ThemeSpec.SurfaceRole.MEDIA_BUTTON,5,5,90,90,fill,edge);
  for(int i=1;i<canvases.length;i++){
   // Left navigation caps its corner radius to preserve a square outline.
   if(i==1&&theme.shell().left())continue;
   for(int y=0;y<100;y++)for(int x=0;x<100;x++)
    check(canvases[0].pixel(x,y)==canvases[i].pixel(x,y),"identical "+theme.material.style+" pixels for surface "+i+" at "+x+","+y);
  }
  check((canvases[0].pixel(50,96)!=0xffffffff)==theme.material.glass(),"only glass casts a shadow outside the bounds");
 }
 static ThemeSpec f1Variant(final String file,final String before,final String after){
  return f1Variant("f1_2",file,before,after);
 }
 static ThemeSpec f1Variant(final String id,final String file,final String before,final String after){
  ThemeSpec theme=ThemeCatalog.load(new Activity(){
   @Override public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager(){
    @Override public java.io.InputStream open(String path)throws java.io.IOException{
     String prefix="themes/"+id+"/";
     String source=path.startsWith(prefix)?"themes/f1_2/"+path.substring(prefix.length()):path;
     boolean rename=!id.equals("f1_2")&&(path.equals("themes/catalog.json")||path.equals(prefix+"manifest.json"));
     boolean renameWallpapers=!id.equals("f1_2")&&path.equals(prefix+"components.json");
     boolean patch=path.equals(prefix+file);
     if(!rename&&!renameWallpapers&&!patch)return super.open(source);
     try(java.io.InputStream input=super.open(source)){
      java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
      byte[] buffer=new byte[4096];int len;while((len=input.read(buffer))!=-1)bytes.write(buffer,0,len);
      String doc=bytes.toString("UTF-8");
      if(rename)doc=doc.replace("\"f1_2\"",path.endsWith("catalog.json")?"\"f1_2\", \""+id+"\"":"\""+id+"\"");
      if(renameWallpapers)doc=doc.replace("\"id\": \"f1_2_","\"id\": \""+id+"_");
      if(patch){
       check(doc.contains(before),"expected F1-2 asset value");
       doc=doc.replace(before,after);
      }
      return new java.io.ByteArrayInputStream(doc.getBytes("UTF-8"));
     }
    }
   };}
  }).find(id);
  check(theme!=null,"F1-2 variation loads through v3 asset catalog");
  return theme;
 }
 static void checkJsonDecorations(ThemeSpec official,ThemeSurface surface){
  ThemeSpec renamed=f1Variant("renamed_theme","","","");
  ThemeSpec enabled=f1Variant("renamed_theme","components.json",
      "\"overviewCardTopLine\": false,\n    \"navigationUnderline\": \"off\"",
      "\"overviewCardTopLine\": true,\n    \"navigationUnderline\": \"on\"");
  int accent=0xff005bff;
  for(ThemeSpec theme:new ThemeSpec[]{official,renamed,enabled}){
   Canvas c=new Canvas(140,150);Paint p=new Paint(3);
   surface.navigationButton(c,theme,null,p,null,10,10,110,40,true,accent);
   surface.overviewCard(c,theme,p,10,70,110,65,accent);
   check((c.pixel(60,47)==accent)==(theme==enabled),"selected underline follows JSON, including a renamed theme");
   check((c.pixel(45,71)==accent)==(theme==enabled),"overview top line follows JSON, including a renamed theme");
   if(!theme.material.glass())check(c.pixel(60,10)==accent,"flat selection outline remains visible");
  }
 }
 static void checkIndependentBackdrop(ThemeSpec flat,ThemeSpec glass,ThemeSurface surface){
  ThemeSpec solid=f1Variant("colors.json","\"style\": \"gradient\"","\"style\": \"solid\"");
  check(!flat.material.glass()&&glass.material.glass()&&solid.backdrop.mode==ThemeSpec.Backdrop.Mode.SOLID,"the overrides change separate settings");
  Canvas a=new Canvas(192,72),b=new Canvas(192,72),c=new Canvas(192,72);
  surface.backdrop(a,flat.withDisplayOptions(1,1,2),null,192,72);
  surface.backdrop(b,glass,null,192,72);
  surface.backdrop(c,solid,null,192,72);
  check(a.pixel(20,0)!=a.pixel(20,71),"flat surfaces retain the F1-2 gradient backdrop");
  for(int y=0;y<72;y++)for(int x=0;x<192;x++)
   check(a.pixel(x,y)==b.pixel(x,y)&&c.pixel(x,y)==flat.background,"only backdrop settings can change backdrop pixels");
 }
 static void checkWideBackdrops(ThemeSpec glass,Map<String,String> docs,String catalogJson)throws Exception{
  for(boolean js:new boolean[]{true,false})for(ThemeSpec theme:new ThemeSpec[]{ThemeSpec.original(),glass})
   for(int width:new int[]{1280,1920}){
    Canvas wide=frame(theme,null,js,width,720);
    for(int x:new int[]{1,width-2}){
     check(wide.pixel(x,1)!=wide.pixel(x,718),"gradient reaches edge: "+theme.id+", "+width+", "+x);
    }
   }
  docs.put("aurora_focus",docs.get("aurora_focus").replace("\"assets\": {\"background\": \"\"}",
      "\"assets\": {\"background\": \"backgrounds/test.png\"}"));
  ThemeSpec custom=ThemeCatalog.fromJsonDocuments(catalogJson,docs).find("aurora_focus");
  check(custom!=null,"test background manifest loads");
  final int center=0xff3050a0;
  java.awt.image.BufferedImage source=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<8;y++)for(int x=0;x<8;x++)source.setRGB(x,y,y<2?0xffde271a:y>5?0xff22a051:center);
  java.io.ByteArrayOutputStream png=new java.io.ByteArrayOutputStream();
  javax.imageio.ImageIO.write(source,"png",png);
  ThemeResources bitmap=ThemeResources.load(new Activity(){
   @Override public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager(){
    @Override public java.io.InputStream open(String path)throws java.io.IOException{
     if(!path.equals("themes/aurora_focus/backgrounds/test.png"))return super.open(path);
     return new java.io.ByteArrayInputStream(png.toByteArray());
    }
   };}
  },custom);
  check(bitmap.background()!=null,"test background bitmap loads");
  for(boolean js:new boolean[]{true,false}){
   Canvas wide=frame(custom,bitmap,js,1920,720);
   float sampleY=DashboardPage.regions(1920,720,custom).headerHeight+2;
   for(int x:new int[]{1,1918})for(int y:new int[]{(int)sampleY,718})
    check(wide.pixel(x,y)==center,"bitmap center crop remains visible at exposed display edges: "+x+","+y);
  }
 }
 static Canvas.TextDraw cardText(Canvas c,OverviewLayout layout,int slot,String content){
  for(Canvas.TextDraw d:c.commands)
   if(d.text.equals(content)&&d.x>=layout.leftAt(slot)&&d.x<layout.leftAt(slot)+layout.widthAt(slot)
       &&d.y>=layout.topAt(slot)&&d.y<layout.topAt(slot)+layout.heightAt(slot))return d;
  throw new AssertionError("missing card text: "+content);
 }
 static Paint.FontMetrics metrics(Canvas.TextDraw text){
  Paint p=new Paint(1);p.setTypeface(text.type);p.setTextSize(text.size);return p.getFontMetrics();
 }
 static void near(float actual,float expected,String name){
  check(Math.abs(actual-expected)<.04f,name+": "+actual+" vs "+expected);
 }
 static void checkOverviewCardStyles()throws Exception{
  String fixture=System.getProperty("dashboard.v2fixtures")+"/original/manifest.json";
  String catalog="{\"schemaVersion\":3,\"themeIds\":[\"original\",\"base\",\"child\"]}";
  Map<String,String> docs=new LinkedHashMap<String,String>();
  docs.put("original",new String(Files.readAllBytes(Paths.get(fixture)),"UTF-8"));
  docs.put("base","{\"schemaVersion\":3,\"id\":\"base\",\"displayName\":\"Base\",\"extends\":\"original\",\"files\":{\"overview\":\"overview.json\"}}");
  docs.put("child","{\"schemaVersion\":3,\"id\":\"child\",\"displayName\":\"Child\",\"extends\":\"base\",\"files\":{\"overview\":\"overview.json\"}}");
  docs.put("base/overview.json","{\"cardDefaults\":{\"titleValueGap\":12,\"padding\":{\"left\":7}}}");
  docs.put("child/overview.json","{\"cardDefaults\":{\"valueUnitGap\":9,\"padding\":{\"top\":4}},"
      +"\"cardTypes\":{\"tirePressure\":{\"titleSize\":18},\"dctTemperature\":{\"gaugeWidth\":27},"
      +"\"fuelEconomy\":{\"rowGap\":5},\"stability\":{\"labelSize\":11}}}");
  ThemeCatalog valid=ThemeCatalog.fromJsonDocuments(catalog,docs);
  check(valid.size()==3,"overview optional properties load with v2 parent");
  ThemeSpec child=valid.find("child");
  for(ThemeSpec theme:new ThemeSpec[]{ThemeSpec.original(),ThemeCatalog.withMenuPosition(child,ThemeSpec.MENU_LEFT)}){
   for(boolean js:new boolean[]{true,false}){
    OverviewLayout layout=new OverviewLayout();layout.theme(theme);layout.pdProfile(js);layout.jsProfile(js);
    Telemetry t=new Telemetry();t.profile(false,js,!js);
    boolean changed=theme.id.equals("child");
    layout.viewport(1000,640);
    near(text(overview(theme,t,layout,js),com.veloster.i18n.UiText.t("타이어 공기압")).size,changed?18:13,
        "tire pressure has independent title");
    for(int mode:new int[]{2,1,0}){
     layout.viewport(800,480);layout.style(layout.signalAt(0),mode);
     Canvas c=overview(theme,t,layout,js);
     Canvas.TextDraw title=cardText(c,layout,0,"SPEED"),number=cardText(c,layout,0,"—"),unit=cardText(c,layout,0,"km/h");
     near(title.size,12,"default title");near(number.size,24,"default value");near(unit.size,10,"default unit");
     near(title.x-layout.leftAt(0),changed?7:10,"inherited left padding");
     near(theme.overviewCardStyle(ThemeSpec.OverviewCardKind.STANDARD).titleValueGap,changed?12:4,"theme title/value gap");
     near(theme.overviewCardStyle(ThemeSpec.OverviewCardKind.STANDARD).valueUnitGap,changed?9:4,"theme value/unit gap");
     layout.viewport(800,300);
     c=overview(theme,t,layout,js);
     for(String content:new String[]{"SPEED","—","km/h"}){
      Canvas.TextDraw d=cardText(c,layout,0,content);
      check(d.y+metrics(d).ascent>=layout.topAt(0)&&d.y+metrics(d).descent<=layout.topAt(0)+layout.heightAt(0),
          "short card contains "+content+" in mode "+mode);
     }
    }
   }
  }
  docs.put("child/overview.json","{\"cardTypes\":{\"tirePressure\":{\"gaugeWidth\":24}}}");
  check(ThemeCatalog.fromJsonDocuments(catalog,docs).size()==1,"type-specific fields are validated");
  docs.put("child/overview.json","{\"cardDefaults\":{\"titleValueGap\":-1}}");
  check(ThemeCatalog.fromJsonDocuments(catalog,docs).size()==1,"out-of-range gap rejected");
  docs.put("child/overview.json","{\"cardDefaults\":{\"unitPosition\":\"topRight\",\"titleUnitGap\":6}}");
  ThemeSpec topRight=ThemeCatalog.fromJsonDocuments(catalog,docs).find("child");
  OverviewLayout layout=new OverviewLayout();layout.theme(topRight);
  Telemetry t=new Telemetry();Canvas c=overview(topRight,t,layout,false);
  Canvas.TextDraw title=cardText(c,layout,0,"SPEED"),unit=cardText(c,layout,0,"km/h");
  check(title.x+title.width+6<=unit.x+.05f,"top-right unit shares the heading row with a configurable gap");
  docs.put("child/overview.json","{\"cardDefaults\":{\"unitPosition\":\"bottomRight\"}}");
  ThemeSpec bottomRight=ThemeCatalog.fromJsonDocuments(catalog,docs).find("child");
  layout.theme(bottomRight);c=overview(bottomRight,t,layout,false);
  title=cardText(c,layout,0,"SPEED");unit=cardText(c,layout,0,"km/h");
  near(unit.x+unit.width,layout.leftAt(0)+layout.widthAt(0)-10,"bottom-right unit alignment");
  docs.put("child/overview.json","{\"cardDefaults\":{\"unitPosition\":\"valueRight\",\"valueUnitGap\":4}}");
  ThemeSpec valueRight=ThemeCatalog.fromJsonDocuments(catalog,docs).find("child");
  layout.theme(valueRight);c=overview(valueRight,t,layout,false);
  Canvas.TextDraw value=cardText(c,layout,0,"—");unit=cardText(c,layout,0,"km/h");
  near(unit.x,value.x+value.width+4,"value-right unit uses the configured 4-unit gap");
  near(unit.y,value.y,"value-right unit shares the value baseline");
 }
 static Canvas overview(ThemeSpec theme,Telemetry t,OverviewLayout layout,boolean js){
  Canvas c=new Canvas(800,480);
  if(js){
   JsOverviewRenderer r=new JsOverviewRenderer();r.setTheme(theme);
   r.draw(c,t.snapshot(1000),layout,false,true,t.dctTemperatures(1000),t.jsDriveMode(1000),Float.NaN);
  }else{
   OverviewRenderer r=new OverviewRenderer();r.setTheme(theme);
   r.draw(c,t.snapshot(1000),layout,false,false);
  }
  return c;
 }
 public static void main(String[] args)throws Exception{
  checkOverviewCardStyles();
  ThemeSpec numeric=changed("numericScale",120);
  check(Math.abs(text(cluster(numeric),"D4").size-184*1.2f)<.001,"centered text scales once");
  ThemeSpec display=changed("displayScale",120);
  check(Math.abs(text(cluster(display),"SPEED").size-14*1.2f)<.001,"display scale reaches renderer");
  ThemeSpec side=changed("f1SidePadding",20);
  check(text(cluster(side),"SPEED").x==38,"F1 side padding changes coordinates");
  ThemeSpec pedal=changed("pedalPadding",24);
  check(text(cluster(pedal),"PEDAL").x==42,"pedal padding changes coordinates");
  ThemeSpec inset=changed("gearPadding",20);Canvas lens=cluster(inset);Canvas.TextDraw gear=text(lens,"D4");
  check(gear.width<=172.01&&gear.x>=314,"gear fit respects padding and measured size");
  ThemeSpec buttons=changed("buttonPadding",24);Canvas narrow=new Canvas(800,480);
  JsPitRenderer buttonRenderer=new JsPitRenderer();buttonRenderer.setTheme(buttons);buttonRenderer.draw(narrow,800,480,false,new Telemetry(),1000);
  check(text(narrow,"F1").size>0,"large padding cannot invert narrow navigation button text");
  ThemeSpec settings=changed("settingsPadding",32);Canvas page=new Canvas(800,480);
  new SettingsPageRenderer().draw(page,settings,ThemeResources.load(new Activity(),settings),"English",false,new com.veloster.dashboard.view.DashboardPage.Content(settings,"settings"));
  check(text(page,com.veloster.i18n.UiText.t("언어")).x==settings.spacing.cardPadding+settings.spacing.settingsPadding,"settings padding preserves click bounds");
  ThemeSpec cards=changed("cardPadding",18);OverviewLayout layout=new OverviewLayout();layout.theme(cards);layout.jsProfile(true);layout.pdProfile(true);
  Telemetry t=new Telemetry();t.profile(false,true,false);JsOverviewRenderer overview=new JsOverviewRenderer();overview.setTheme(cards,null);Canvas grid=new Canvas(800,480);
  overview.draw(grid,t.snapshot(1000),layout,false,true,t.dctTemperatures(1000),t.jsDriveMode(1000),Float.NaN);
  check(text(grid,"SPEED").x==36,"overview card padding reaches label placement");
  ThemeCatalog catalog=ThemeCatalog.load(new Activity());ThemeSpec liquidGlass=catalog.find("f1_2");ThemeResources fonts=ThemeResources.load(new Activity(),liquidGlass);
  for(String id:new String[]{"f1_2","f1_2_flat","f1_2_optics"}){
   ThemeSpec theme=catalog.find(id);
   for(ThemeSpec.OverviewCardKind kind:ThemeSpec.OverviewCardKind.values()){
    ThemeSpec.OverviewCardStyle style=theme.overviewCardStyle(kind);
    check(style.padding.top==12&&style.padding.right==12&&style.padding.bottom==12&&style.padding.left==12,
        id+" "+kind+" inherits 12-unit card padding");
   }
   check("valueRight".equals(theme.overviewCardStyle(ThemeSpec.OverviewCardKind.STANDARD).unitPosition),
       id+" places standard card units beside values");
   check(theme.overviewCardStyle(ThemeSpec.OverviewCardKind.STANDARD).valueUnitGap==4,
       id+" uses a 4-unit value-to-unit gap");
   check(theme.spacing.gridGapX==8&&theme.spacing.gridGapY==8,
       id+" uses 8-unit overview grid gaps");
  }
  check(fonts.body()!=null&&fonts.numeric()!=null&&fonts.display()!=null,"desktop loads actual bundled OTF font files");
  ThemeSurface surface=new ThemeSurface();Canvas a=new Canvas(100,100),b=new Canvas(100,100);a.drawColor(0xffffffff);b.drawColor(0xffffffff);
  // Exercise glass via its real manifest rather than a special renderer branch.
  String asset=System.getProperty("dashboard.v2fixtures")+"/aurora_focus/manifest.json";
  String json=new String(Files.readAllBytes(Paths.get(asset)),"UTF-8").replace("\"style\": \"legacy\"","\"style\": \"glass\"").replace("\"radius\": 0","\"radius\": 24").replace("\"bottomAlpha\": 255","\"bottomAlpha\": 100");
  Map<String,String> only=new LinkedHashMap<String,String>();only.put("aurora_focus",json);only.put("original",new String(Files.readAllBytes(Paths.get(System.getProperty("dashboard.v2fixtures")+"/original/manifest.json")),"UTF-8"));
  String catalogJson="{\"schemaVersion\":2,\"themeIds\":[\"original\",\"aurora_focus\"]}";
  ThemeSpec glass=ThemeCatalog.fromJsonDocuments(catalogJson,only).find("aurora_focus");
  check(glass!=null&&surface.panel(a,glass,5,5,90,90,0xff004cff,0xff004cff),"glass material supported");
  check(a.pixel(5,5)==0xffffffff&&a.pixel(50,20)!=a.pixel(50,80),"glass radius and alpha gradient affect pixels");
  checkSharedSurfaces(glass,surface);
  ThemeSpec glassParent=liquidGlass,flatChild=catalog.find("f1_2_flat");
  checkSharedSurfaces(glassParent,surface);
  checkSharedSurfaces(flatChild,surface);
  checkJsonDecorations(flatChild,surface);
  checkIndependentBackdrop(flatChild,glassParent,surface);
  checkWideBackdrops(glass,only,catalogJson);
  System.out.println("PASS v2 rendering, real font assets, and full viewport gradient and bitmap pixels");
 }
}'''

BASELINE = r'''
import com.veloster.dashboard.theme.*;
import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.view.*;
import com.veloster.dashboard.view.overview.*;
import com.veloster.dashboard.view.gauge.*;
import com.veloster.dashboard.view.graph.*;
import android.graphics.*;
import android.app.Activity;
public final class ThemeVisualProbe {
 public static void main(String[] args)throws Exception{
  Activity activity=new Activity();ThemeCatalog catalog=ThemeCatalog.load(activity);
  for(String id:new String[]{"original","aurora_focus","carplay_glass_light","carplay_glass_dark"})for(int profile=0;profile<3;profile++)for(int page=0;page<7;page++){
   ThemeSpec theme=catalog.find(id);if(theme==null)throw new AssertionError("missing migration fixture "+id);
   Telemetry t=new Telemetry();t.profile(profile==1,profile==0,profile==2);
   ThemeResources resources=ThemeResources.load(activity,theme);OverviewLayout layout=new OverviewLayout();layout.theme(theme);
   Canvas c=new Canvas(800,480);
   if(profile==0){JsPitRenderer r=new JsPitRenderer();r.setTheme(theme,resources);r.setOverviewLayout(layout);
    if(page==3)r.setPerformancePage(true);if(page==4)r.setGaugeLayout(new GaugeLayout());if(page==5)r.setGraphLayout(new GraphLayout());if(page==6)r.setSettingsPage(true,"한국어",theme.displayName,com.veloster.dashboard.view.SettingsPageRenderer.menuPositionLabel(ThemeSpec.MENU_THEME));
    r.draw(c,800,480,page==1||page==2,t,1000,6000,page==2,null);
   }else{PitRenderer r=new PitRenderer();r.setTheme(theme,resources);r.setOverviewLayout(layout);
    if(page==3)r.setPerformancePage(true);if(page==4)r.setGaugeLayout(new GaugeLayout());if(page==5)r.setGraphLayout(new GraphLayout());if(page==6)r.setSettingsPage(true,"한국어",theme.displayName,com.veloster.dashboard.view.SettingsPageRenderer.menuPositionLabel(ThemeSpec.MENU_THEME));
    r.draw(c,800,480,page==1||page==2,t,1000,6000,page==2,null);
   }
   java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
   DashboardPage.Bounds clock=DashboardPage.navigationBounds(theme,new DashboardPage.Menu(),DashboardPage.CLOCK);
   for(int y=0;y<480;y++)for(int x=0;x<800;x++){if(clock.contains(x,y))continue;int v=c.pixel(x,y);digest.update((byte)(v>>24));digest.update((byte)(v>>16));digest.update((byte)(v>>8));digest.update((byte)v);}
   String hash="";for(byte b:digest.digest())hash+=String.format("%02x",b&255);
   System.out.println(theme.id+"/"+profile+"/"+page+" "+hash);
  }
 }
}'''

class ThemeV2RendererTests(unittest.TestCase):
    def test_theme_values_reach_production_renderers(self):
        with tempfile.TemporaryDirectory(prefix='theme-v2-') as folder:
            java, classes = runtime.compile_runtime(folder)
            _, javac = java_tools()
            probe = Path(folder) / 'ThemeV2Probe.java'
            probe.write_text(PROBE)
            subprocess.run([javac, '-encoding', 'UTF-8', '-cp', str(classes), '-d', str(classes), str(probe)], check=True)
            result = subprocess.run([java, '-Djava.awt.headless=true', '-Ddashboard.assets=' + str(ROOT / 'apps/dashboard/src/0x1101L/assets'), '-Ddashboard.v2fixtures=' + str(ROOT / 'tests/fixtures/theme_v2'), '-cp', str(classes), 'ThemeV2Probe'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            print(result.stdout.strip())

if __name__ == '__main__':
    unittest.main()
