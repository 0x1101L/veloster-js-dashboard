#!/usr/bin/env python3
"""Compare bundled Liquid Glass and Optics, plus temporary Optics controls."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT / "tools"))
import dashboard_runtime as runtime
from test_quick_entry_runtime import java_tools

PROBE = """
import android.app.Activity;
import android.graphics.Canvas;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.view.*;
import com.veloster.dashboard.vehicle.*;
import com.veloster.i18n.LanguageSettings;
import java.lang.reflect.Method;
import java.nio.file.*;

public final class GlassOpticsProbe extends DashboardDesktop {
 static Path output;
 static final String TRIAL="f1_2_optics";
 static void save(Canvas c,String name)throws Exception{c.write(output.resolve(name+".png").toString());}
 static int energy(java.awt.image.BufferedImage image,int x,int y,int w,int h){
  int sum=0;
  for(int row=y;row<y+h;row++)for(int col=x;col<x+w;col++){
   int p=(image.getRGB(col,row)>>16)&255;
   sum+=Math.abs(p-((image.getRGB(col+1,row)>>16)&255));
   sum+=Math.abs(p-((image.getRGB(col,row+1)>>16)&255));
  }
  return sum;
 }
 static ThemeSpec theme(DashboardView view)throws Exception{return (ThemeSpec)field(view,"activeTheme");}
 static DashboardView view(String id,PreviewProfile profile)throws Exception{
  fixture("received",profile.fixture);
  if("js".equals(profile.fixture)){
   Telemetry t=(Telemetry)field(DashboardView.class,"DATA");
   Method put=t.getClass().getDeclaredMethod("put",int.class,float.class,boolean.class,long.class);put.setAccessible(true);
   int[] keys={0,7,8,2,Telemetry.INTAKE,Telemetry.BOOST,Telemetry.ACCEL_PEDAL,10};
   float[] values={4560,124,4,91,34,.8f,38,0};
   for(int i=0;i<keys.length;i++)put.invoke(t,keys[i],values[i],true,1000L);
   t.hscanJs(0xc7,0x1d,new byte[]{0,5,0,0,0,0,0,0},1000);
   t.hscanJs(0xc7,0x1e,new byte[]{0,4,0,0,0,0,0,0},1000);
   t.hscanJs(0xc7,0x04,new byte[]{0,0,0,0,0,0x10,0,0},1000);
  }
  Activity a=new Activity();
  LanguageSettings.save(a,System.getProperty("preview.language","en"));
  android.content.SharedPreferences.Editor themePrefs=a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id",id);
  themePrefs.putInt("liquid_glass_transparency",Integer.getInteger("glass.transparency",0));
  themePrefs.putInt("liquid_glass_blur",Integer.getInteger("glass.blur",1));
  themePrefs.putInt("liquid_glass_highlight",Integer.getInteger("glass.highlight",1)).apply();
  a.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("dashboard_page",1).putInt("menu_icon_mode",1).apply();
  DashboardView v=create(a,profile);
  check(id.equals(theme(v).id),"test theme loaded: "+id);
  MediaState media=(MediaState)field(v,"mediaState");
  media.mode(0x73);
  media.update(3,true,"Midnight Drive",true,"Neon Avenue",true,79000,true,222000,false,1000);
  media.playback(3,1);
  return v;
 }
 static void logicalTap(DashboardView v,DashboardPage.Bounds b)throws Exception{
  DashboardPage.Regions r=viewRegions(v);
  tap(v,r.left+b.atX(.5f)*r.scale,r.top+b.atY(.5f)*r.scale);
 }
 static void menu(DashboardView v,int id)throws Exception{
  DashboardPage.Menu m=(DashboardPage.Menu)field(v,"pageMenu");
  logicalTap(v,DashboardPage.navigationBounds(theme(v),m,id));
 }
 static void drain(DashboardView v)throws Exception{
  long end=System.currentTimeMillis()+2000;
  while(((java.util.concurrent.atomic.AtomicBoolean)field(v,"mediaPending")).get()&&System.currentTimeMillis()<end){
   java.awt.SecondaryLoop loop=java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
   javax.swing.Timer timer=new javax.swing.Timer(5,e->loop.exit());timer.setRepeats(false);timer.start();loop.enter();
  }
  check(!((java.util.concurrent.atomic.AtomicBoolean)field(v,"mediaPending")).get(),"media request finished");
 }
 static void transport(DashboardView v,boolean wide)throws Exception{
  ThemeSpec t=theme(v);DashboardPage.Regions r=viewRegions(v);
  DashboardPage.Content c=new DashboardPage.Content(t,"f1",r,true);
  for(int cmd:new int[]{MediaBar.PREVIOUS,MediaBar.PLAY,MediaBar.NEXT}){
   int calls=MediaControl.calls.get();
   if(wide){
    boolean found=false;
    for(int y=(int)r.media.atY(.55f);y<r.media.bottom()&&!found;y+=4){
     for(int x=(int)r.media.x;x<r.media.right();x+=4)if(MediaBar.regionHit(t,r.media,x,y)==cmd){tap(v,x,y);found=true;break;}
    }
    check(found,"wide transport hit area");
   }else logicalTap(v,c.footer(cmd));
   drain(v);
   check(MediaControl.calls.get()==calls+1&&MediaControl.command==cmd,"transport still dispatches "+cmd);
  }
 }
 static void checkFooter(DashboardView v,boolean buttons,boolean container)throws Exception{
  ThemeSpec t=theme(v);
  DashboardPage.Content c=new DashboardPage.Content(t,"f1",viewRegions(v),true);
  Canvas footer=new Canvas(800,480);
  footer.drawColor(0xff406080);
  new MediaBar().drawAdaptive(footer,t,null,null,null,true,c);
  DashboardPage.Bounds strip=c.footer(MediaBar.NONE);
  Canvas background=new Canvas(800,480);
  background.drawColor(0xff406080);
  if(container)new ThemeSurface().draw(background,t,ThemeSpec.SurfaceRole.MEDIA_CONTAINER,ThemeSpec.SurfaceRole.PANEL,
      strip.x,strip.y,strip.width,strip.height,t.panel,t.stroke);
  int y=(int)strip.atY(.25f);
  check((footer.pixel((int)strip.atX(.55f),y)!=0xff406080)==container,"only container visibility controls the shared background");
  check(!container||footer.pixel((int)strip.atX(.55f),y)==background.pixel((int)strip.atX(.55f),y),
        "shared footer container uses the same panel fill as dashboard containers");
  for(int action:new int[]{MediaBar.PREVIOUS,MediaBar.PLAY,MediaBar.NEXT}){
   int x=(int)c.footer(action).atX(.5f);
   check((footer.pixel(x,y)!=background.pixel(x,y))==buttons,"individual button background matches configuration: "+action);
  }
  check(footer.texts.contains("Previous")&&footer.texts.contains("Next"),"footer buttons keep their labels");
 }
 static void checkWidePlayer(DashboardView v)throws Exception{
  ThemeSpec t=theme(v);
  DashboardPage.Bounds region=viewRegions(v).media;
  float u=Math.min(region.width/548f,region.height/642f);
  DashboardPage.Bounds card=region.inset(t.shell().mediaPadding*u);
  Canvas player=new Canvas(v.getWidth(),v.getHeight()),background=new Canvas(v.getWidth(),v.getHeight());
  player.drawColor(0xff406080);background.drawColor(0xff406080);
  new MediaBar().drawRegion(player,t,null,null,null,region);
  new ThemeSurface().card(background,t,ThemeSpec.SurfaceRole.MEDIA_CONTAINER,card.x,card.y,card.width,card.height,22*u,t.panel);
  for(int action:new int[]{MediaBar.PREVIOUS,MediaBar.PLAY,MediaBar.NEXT}){
   int left=v.getWidth(),right=0,top=v.getHeight(),bottom=0;
   for(int y=(int)region.atY(.55f);y<region.bottom();y+=2){
    for(int x=(int)region.x;x<region.right();x+=2){
     if(MediaBar.regionHit(t,region,x,y)!=action)continue;
     left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
    }
   }
   check(right>left&&bottom>top,"wide button has a touch area");
   int x=(left+right)/2,y=top+(bottom-top)/4;
   int expected=background.pixel(x,y);
   check(expected!=0xff406080&&player.pixel(x,y)==expected,"wide button area shows only the shared card background");
   boolean icon=false;
   for(int col=left;col<=right;col++)if(player.pixel(col,(top+bottom)/2)==t.textPrimary)icon=true;
   check(icon,"wide button icon remains visible");
  }
 }
 static Canvas f1(String label,String id,PreviewProfile p,boolean test)throws Exception{
  DashboardView v=view(id,p);
  Canvas frame=draw(v);save(frame,label);
  if(test){
   check(theme(v).shell().dock(),"dock is active");
   boolean wide=p.width>=1800;
   check(frame.texts.contains("Previous")!=wide&&frame.texts.contains("Next")!=wide,
         "Optics uses bottom buttons and keeps wide transport icons");
   transport(v,p.width>=1800);
   menu(v,DashboardPage.SETTINGS);
   check((Boolean)field(v,"settingsVisible"),"dock settings opens");
   save(draw(v),label+"-settings");
   menu(v,DashboardPage.F1);
   check(!(Boolean)field(v,"settingsVisible"),"dock returns to F1");
  }
  return frame;
 }
 public static void main(String[] args)throws Exception{
  output=Paths.get(args[0]);
  javax.swing.SwingUtilities.invokeAndWait(()->{try{
   PreviewProfile normal=PreviewProfile.ALL[0],wide=PreviewProfile.ALL[3];
   if(args.length>1&&"settings-ui".equals(args[1])){
    DashboardView v=view("f1_2_optics",normal);
    openSettingsPage(v,9);save(draw(v),"settings-ui-group");
    tapEditorLabel(v,"Liquid Glass");save(draw(v),"settings-liquid-glass");
    openSettingsPage(v,18);save(draw(v),"settings-transparency");
    openSettingsPage(v,19);save(draw(v),"settings-blur");
    return;
   }
   if(args.length>1&&"glass-settings".equals(args[1])){
    String[] names={"glass-default","glass-transparency-reduced","glass-more-transparent","glass-very-transparent","glass-blur-off","glass-blur-very-high","glass-highlight-subtle","glass-highlight-strong"};
    int[][] options={{0,1,1},{1,1,1},{3,1,1},{2,1,1},{0,3,1},{0,4,1},{0,1,0},{0,1,2}};
    java.awt.image.BufferedImage baseline=null;
    for(int i=0;i<names.length;i++){
     System.setProperty("glass.transparency",Integer.toString(options[i][0]));
     System.setProperty("glass.blur",Integer.toString(options[i][1]));
     System.setProperty("glass.highlight",Integer.toString(options[i][2]));
     DashboardView v=view("f1_2_optics",normal);Canvas frame=draw(v);save(frame,names[i]);
     ThemeSpec actual=theme(v);
     check(actual.liquidGlassTransparencyLevel()==options[i][0]&&actual.liquidGlassBlurLevel()==options[i][1]
       &&actual.liquidGlassHighlightLevel()==options[i][2],names[i]+" settings reach the renderer");
     java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(output.resolve(names[i]+".png").toFile());
     if(i==0)baseline=image;
     else{int changed=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)!=baseline.getRGB(x,y))changed++;
      System.out.println(names[i]+": "+changed+" pixels differ from default");}
    }
    java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(1640,2090,java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g=sheet.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
    for(int i=0;i<names.length;i++){
     java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(output.resolve(names[i]+".png").toFile());
     int x=20+(i%2)*810,y=20+(i/2)*515;g.setColor(java.awt.Color.BLACK);g.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,18));g.drawString(names[i],x,y+20);
     g.drawImage(image,x,y+30,800,480,null);
    }
    g.dispose();javax.imageio.ImageIO.write(sheet,"png",output.resolve("glass-settings-comparison.png").toFile());
    return;
   }
   if(args.length>1&&"no-blur".equals(args[1])){
    DashboardView v=view(TRIAL,normal);
    check(theme(v).blurFor(ThemeSpec.SurfaceRole.DATA_CARD)==0,"blur control uses no blur");
    Canvas frame=draw(v);
    save(frame,"optics-no-blur");
    java.awt.image.BufferedImage sharp=javax.imageio.ImageIO.read(output.resolve("optics-no-blur.png").toFile());
    java.awt.image.BufferedImage soft=javax.imageio.ImageIO.read(output.resolve("optics.png").toFile());
    check(energy(soft,612,289,148,30)<energy(sharp,612,289,148,30),"blur smooths the wallpaper under the boost card");
    check(sharp.getRGB(80,414)==soft.getRGB(80,414),"blur leaves exposed wallpaper alone");
    return;
   }
   if(args.length>1&&("footer-no-container".equals(args[1])||"footer-button-backgrounds".equals(args[1]))){
    DashboardView v=view(TRIAL,normal);
    boolean buttonBackgrounds="footer-button-backgrounds".equals(args[1]);
    checkFooter(v,buttonBackgrounds,buttonBackgrounds);
    save(draw(v),args[1]);
    transport(v,false);
    return;
   }
   if(args.length>1){
    DashboardView v=view(TRIAL,normal);
    check(theme(v).mediaControls()==ThemeSpec.MediaControls.BUTTONS,"buttons-only mode loads");
    Canvas buttons=draw(v);
    check(buttons.texts.contains("Previous"),"individual media buttons rendered");
    save(buttons,"buttons-only");transport(v,false);
    DashboardView w=view(TRIAL,wide);save(draw(w),"buttons-only-wide");transport(w,true);
    return;
   }
   Canvas original=f1("original", "f1_2",normal,false);
   Canvas trial=f1("optics",TRIAL,normal,true);
   for(String id:new String[]{"f1_2","f1_2_flat",TRIAL})checkFooter(view(id,normal),false,true);
   save(draw(view("f1_2_flat",normal)),"flat");
   check(original.pixel(80,414)==trial.pixel(80,414),"outside paint stays unchanged");
   check(original.pixel(20,61)!=trial.pixel(20,61),"dock paints behind clock and buttons");
   for(String id:new String[]{"f1_2","f1_2_flat",TRIAL}){
    DashboardView player=view(id,wide);
    checkWidePlayer(player);
    if(TRIAL.equals(id))f1("optics-wide",id,wide,true);
    else{
     save(draw(player),"f1_2".equals(id)?"original-wide":"flat-wide");
     transport(player,true);
    }
   }
   DashboardView resized=view(TRIAL,normal);resized.setSize(800,425);
   save(draw(resized),"optics-800x425");
   transport(resized,false);menu(resized,DashboardPage.SETTINGS);
   check((Boolean)field(resized,"settingsVisible"),"resized dock settings opens");
  }catch(Exception e){throw new RuntimeException(e);}});
 }
}
"""

MEDIA_SPY = """package com.veloster.dashboard.vehicle;
public class MediaControl {
 public static final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
 public static volatile int command;
 public static boolean toggle(android.content.Context c,int mode,int state){command=0;calls.incrementAndGet();return true;}
 public static boolean step(android.content.Context c,boolean next,int mode){command=next?1:-1;calls.incrementAndGet();return true;}
}
"""

def main():
    output = HERE / "artifacts" / "f1-2-optics"
    glass_material = len(sys.argv)>1 and sys.argv[1]=="--glass-settings-glass"
    if glass_material:
        output = output / "glass-material"
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="glass-optics-") as temp:
        folder = Path(temp)
        spy_path = "com/veloster/dashboard/vehicle/MediaControl.java"
        old_spy = runtime.STUBS[spy_path]
        try:
            runtime.STUBS[spy_path] = MEDIA_SPY
            java, classes = runtime.compile_runtime(folder)
        finally:
            runtime.STUBS[spy_path] = old_spy
        _, javac = java_tools()
        source = folder / "GlassOpticsProbe.java"
        source.write_text(PROBE)
        subprocess.run([javac, "-encoding", "UTF-8", "-cp", str(classes),
                        "-d", str(classes), str(source)], check=True)
        assets = folder / "assets"
        shutil.copytree(ROOT / "apps/dashboard/src/0x1101L/assets", assets)
        theme = assets / "themes/f1_2_optics"
        if glass_material:
            components=theme / "components.json"
            config=json.loads(components.read_text())
            config["surfaceStyles"]={role:"glass" for role in config["surfaceStyles"]}
            components.write_text(json.dumps(config))
        command = [java, "-Djava.awt.headless=true", "-Ddashboard.assets=" + str(assets),
                   "-cp", str(classes), "GlassOpticsProbe", str(output)]
        if len(sys.argv)>1 and sys.argv[1]=="--glass-settings-ui":
            subprocess.run([command[0],"-Djava.awt.headless=true","-Ddashboard.assets="+str(assets),
                            "-Dpreview.language=ko","-cp",str(classes),"GlassOpticsProbe",str(output),"settings-ui"],check=True)
            print("PASS Liquid Glass settings UI rendered by the Java dashboard renderer. Images:",output)
            return
        if len(sys.argv)>1 and sys.argv[1] in ("--glass-settings","--glass-settings-glass"):
            subprocess.run([*command,"glass-settings"],check=True)
            print("PASS Liquid Glass setting variants rendered by the Java dashboard renderer. Images:",output)
            return
        subprocess.run(command, check=True)
        components = theme / "components.json"
        config = json.loads(components.read_text())
        for variant, overrides in {
            "no-blur": {"surfaceBlur": {}},
            "buttons": {"mediaControls": "buttons"},
            "footer-no-container": {"mediaFooter": {"containerVisible": False}},
            "footer-button-backgrounds": {"mediaFooter": {"buttonBackground": True}},
        }.items():
            components.write_text(json.dumps({**config, **overrides}))
            subprocess.run([*command, variant], check=True)
    print("PASS footer styles, backdrop blur, dock and media commands on standard, short and wide displays. Images:", output)

if __name__ == "__main__":
    main()
