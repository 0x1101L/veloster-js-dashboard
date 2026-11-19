#!/usr/bin/env python3
"""Preview an Astryx draft with actual Java renderers and touch handling.

Only workbench artifacts and temporary classes/assets are written.
Application renderers and the theme engine are compiled without source substitutions.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT / 'tools'))
import dashboard_runtime as runtime

CHECK = '''
 static void draftNavigation(DashboardView view,int id)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  if(theme.shell().left())tap(view,40,com.veloster.dashboard.view.DashboardPage.shellTop(theme,id)+theme.shell().buttonHeight/2);
  else tap(view,id==6?560:com.veloster.dashboard.view.DashboardPage.left(id)+10,20);
 }
 static float baseline(android.graphics.Canvas frame,String label){
  for(android.graphics.Canvas.TextDraw text:frame.commands)if(label.equals(text.text))return text.deviceY;
  throw new AssertionError("missing "+label);
 }
 static void gridCheck(DashboardView view,boolean visible,boolean combined)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec t=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  com.veloster.dashboard.view.DashboardPage.Content c=new com.veloster.dashboard.view.DashboardPage.Content(t,"f1");
  check(c.f1Grid!=null,"F1-2 grid loaded");
  com.veloster.dashboard.view.DashboardPage.Bounds rpm=c.f1(t,"rpm",visible,combined), speed=c.f1(t,"speed",visible,combined);
  check(Math.abs(rpm.y-speed.y)<.01f&&Math.abs(rpm.height-speed.height)<.01f,"shared engine/speed row");
  java.util.List<com.veloster.dashboard.view.DashboardPage.Bounds> used=new java.util.ArrayList<>();
  for(String id:new String[]{"shift","gear","speed","boost","rpm","temperatures","pedal","brake"}){
   com.veloster.dashboard.view.DashboardPage.Bounds b=c.f1(t,id,visible,combined);
   check(b.width>0&&b.height>0&&c.contains(b.x,b.y)&&c.contains(b.x+b.width-.01f,b.y+b.height-.01f),"contained: "+id);
   for(com.veloster.dashboard.view.DashboardPage.Bounds a:used)
    check(b.x>=a.x+a.width-.01f||a.x>=b.x+b.width-.01f||b.y>=a.y+a.height-.01f||a.y>=b.y+b.height-.01f,"no overlap: "+id);
   used.add(b);
  }
  com.veloster.dashboard.view.DashboardPage.Bounds bottom=c.f1(t,"brake",visible,combined);
  float end=visible?c.footer(-10).y-c.f1Grid.gap:c.y+c.height-c.f1Grid.inset;
  check(Math.abs(bottom.y+bottom.height-end)<.01f,"bottom inset and footer gap");
  if(combined){
   com.veloster.dashboard.view.DashboardPage.Bounds media=c.mediaPanel(visible);
   check(Math.abs(bottom.x+bottom.width+c.f1Grid.gap-media.x)<.01f,"media joins at the grid gap");
  }else{
   com.veloster.dashboard.view.DashboardPage.Bounds footer=c.footer(-10);
   float left=Float.MAX_VALUE,right=-Float.MAX_VALUE;
   for(String id:new String[]{"speed","gear","rpm"}){
    com.veloster.dashboard.view.DashboardPage.Bounds cell=c.f1(t,id,visible,combined);
    left=Math.min(left,cell.x);right=Math.max(right,cell.x+cell.width);
   }
   check(Math.abs(left-footer.x)<.01f&&Math.abs(right-footer.x-footer.width)<.01f,"common physical outer edges");
  }
  android.graphics.Canvas frame=draw(view);
  check(Math.abs(baseline(frame,"ENGINE")-baseline(frame,"SPEED"))<.01f,"label baseline");
  check(Math.abs(baseline(frame,"4560")-baseline(frame,"124"))<.01f,"numeric baseline");
 }
 static void draftCheck(String folder)throws Exception{
  fixture("received","js");Activity a=new Activity();LanguageSettings.save(a,"ko");DashboardView view=create(a);
  android.graphics.Canvas before=draw(view);
  boolean glass="f1-2".equals(System.getProperty("draft.slug"));
  check((Integer)field(view,"pedalBarStyle")==0,"continuous pedal-bar default");
  check(((com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme")).f1Order()==0,"common speed-gear-rpm order default");
  if(!glass){
    com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
    check(theme.id.equals("f1_blue_draft"),"F1 Blue draft loads");
    check(before.pixel(2,200)==0xffffffff,"white draft background");
    com.veloster.dashboard.view.DashboardPage.Bounds gear=new com.veloster.dashboard.view.DashboardPage.Content(theme,"f1").f1(theme,"gear",true,false);
    check(before.pixel((int)gear.x+8,(int)gear.y+8)==0xff004cff,"blue central gear panel");}
  else {boolean speedLeft=false,engineRight=false;for(android.graphics.Canvas.TextDraw command:before.commands){
      if(command.text.equals("SPEED")&&command.deviceX<300)speedLeft=true;
      if(command.text.equals("ENGINE")&&command.deviceX>500)engineRight=true;
    }
    check(speedLeft&&engineRight,"default F1 order places speed left and engine right");
    com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
    android.graphics.Canvas backdrop=new android.graphics.Canvas(800,480);new com.veloster.dashboard.theme.ThemeSurface().backdrop(backdrop,theme,null,800,480);
    int sidebar=theme.shell().width;
    for(int x:new int[]{2,sidebar-2,sidebar+1})check(before.pixel(x,400)==backdrop.pixel(x,400),"sidebar and body share the full-screen backdrop at "+x);
  }
  check(before.texts.contains("D4")&&before.texts.contains("124")&&before.texts.contains("4560"),"synthetic F1 data: "+before.texts);
  String image=glass?"f1_2":"f1_blue";
  before.write(folder+"/"+image+".png");
  if(glass){
    com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
    com.veloster.dashboard.view.DashboardPage.Content area=new com.veloster.dashboard.view.DashboardPage.Content(theme,"f1");
    com.veloster.dashboard.view.DashboardPage.Bounds toggle=area.footer(2),album=area.album(true);
    gridCheck(view,true,false);
    tap(view,toggle.x+toggle.width/2,toggle.y+toggle.height/2);
    check((Boolean)field(view,"media"),"resolved media toggle");gridCheck(view,true,true);draw(view).write(folder+"/f1_2_media.png");
    tap(view,album.x+album.width/2,album.y+album.height/2);check(!(Boolean)field(view,"media"),"resolved album hit");
    view.setSize(800,425);android.graphics.Canvas resized=draw(view);
    for(android.graphics.Canvas.TextDraw command:resized.commands)check(Math.abs(command.scaleX-command.scaleY)<0.001,"425px glyph aspect");
    resized.write(folder+"/f1_2_800x425.png");view.setSize(800,480);
    Activity noBar=new Activity();LanguageSettings.save(noBar,"ko");
    noBar.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("f1_media_bar_policy",1).apply();
    DashboardView hidden=create(noBar);
    gridCheck(hidden,false,false);draw(hidden).write(folder+"/f1_2_bar_hidden.png");
  }

  draftNavigation(view,6);check(draw(view).texts.contains(UiText.t("언어")),"settings page touch");
  check(AlertDialog.last==null,"settings opens a page");
  if(glass){
    draw(view).write(folder+"/f1_2_settings.png");
    tapSettings(view,400,258);check(settingsScreen(view)==0,"root Language row opens language editor");draw(view).write(folder+"/f1_2_language_settings.png");tapSettings(view,70,88);
    tapSettings(view,400,208);check(settingsScreen(view)==10,"root F1 row opens F1 settings");draw(view).write(folder+"/f1_2_f1_settings.png");
    tapSettings(view,400,158);check(settingsScreen(view)==6,"F1 pedal row opens bar options");
    check((Integer)field(field(view,"settingsEditor"),"selected")==0,"continuous bar option selected by default");draw(view).write(folder+"/f1_2_bar_options.png");tapSettings(view,70,88);
    tapSettings(view,400,258);check(settingsScreen(view)==8,"F1 order row opens order options");
    check((Integer)field(field(view,"settingsEditor"),"selected")==0,"speed-gear-rpm order selected by default");draw(view).write(folder+"/f1_2_order_options.png");tapSettings(view,70,88);tapSettings(view,70,88);
    tapSettings(view,400,158);check(settingsScreen(view)==9,"root UI row opens UI settings");draw(view).write(folder+"/f1_2_ui_settings.png");
    tapSettings(view,400,158);check(settingsScreen(view)==1,"theme selection opens as a page");
    tapSettings(view,520,88);check(settingsScreen(view)==11,"theme preview opens as a page");draw(view).write(folder+"/f1_2_theme_preview.png");
    tapSettings(view,70,88);check(settingsScreen(view)==1,"preview back restores theme selection");tapSettings(view,70,88);
    tapEditorLabel(view,UiText.t("페이지 메뉴"));check(settingsScreen(view)==4,"UI page-menu row opens visibility editor");
    boolean[] checked=(boolean[])field(field(view,"settingsEditor"),"checked");for(boolean value:checked)check(value,"all page-menu items initially visible");
    int originalMask=((com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu")).mask();
    tapSettings(view,400,308);tapSettings(view,400,358);check(((com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu")).mask()==originalMask,"page-menu selection remains a draft before apply");
    tapSettings(view,70,88);check(((com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu")).mask()==originalMask,"page-menu back cancels visibility draft");
    tapEditorLabel(view,UiText.t("페이지 메뉴"));checked=(boolean[])field(field(view,"settingsEditor"),"checked");for(boolean value:checked)check(value,"reopened page-menu editor discards canceled draft");
    tapSettings(view,400,308);tapSettings(view,400,358);tapSettings(view,700,88);
    com.veloster.dashboard.view.DashboardPage.Menu menu=(com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu");
    check(!menu.visible(com.veloster.dashboard.view.DashboardPage.GAUGES)&&!menu.visible(com.veloster.dashboard.view.DashboardPage.GRAPHS),"page-menu apply hides selected pages");
    check(a.getSharedPreferences("pit_vehicle_ui",0).getInt("page_menu_visible_mask",31)!=31,"page-menu apply persists visibility");draw(view).write(folder+"/f1_2_menu_hidden.png");
    tapEditorLabel(view,UiText.t("페이지 메뉴"));tapSettings(view,400,308);tapSettings(view,400,358);tapSettings(view,700,88);
    check(menu.visible(com.veloster.dashboard.view.DashboardPage.GAUGES)&&menu.visible(com.veloster.dashboard.view.DashboardPage.GRAPHS),"page-menu editor restores visibility");draw(view).write(folder+"/f1_2_menu_visible.png");
    tapSettings(view,70,88);tapSettings(view,70,88);
  }
  draftNavigation(view,1);check(draw(view).texts.contains("D4"),"return to F1");
  // RPM editing now lives in Settings; the footer is dedicated to media.
  if(glass)for(android.graphics.Canvas.TextDraw command:before.commands)
    check(Math.abs(command.scaleX-command.scaleY)<0.001,"glyph aspect ratio: "+command.text);
  if(glass){draftNavigation(view,0);com.veloster.dashboard.view.overview.OverviewLayout layout=(com.veloster.dashboard.view.overview.OverviewLayout)field(view,"overviewLayout");
    check(layout.count()==7&&layout.widthAt(0)>layout.widthAt(1),"custom overview cards and span stay on the fixed 5x4 screen");draw(view).write(folder+"/f1_2_overview.png");}
  draftNavigation(view,4);check(draw(view).texts.contains("PERFORMANCE"),"performance navigation");
  fixture("waiting","js");Activity waiting=new Activity();LanguageSettings.save(waiting,"ko");DashboardView empty=create(waiting);
  android.graphics.Canvas missing=draw(empty);check(!missing.texts.contains("124")&&!missing.texts.contains("D4"),"missing data is not fabricated");
  missing.write(folder+"/"+image+"_waiting.png");
  fixture("received","js");Activity original=new Activity();LanguageSettings.save(original,"ko");
  original.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","original").apply();
  draw(create(original)).write(folder+"/f1_original.png");
  System.out.println("PASS draft white/blue colors, synthetic data, missing data, real F1/settings/performance navigation and glyph aspect ratio");
 }
'''


def compile_draft(folder, slug):
    desktop = runtime.STUBS['DashboardDesktop.java']
    # These preferences exist only in the host stub, never on a device.
    desktop = desktop.replace('a.content.removeAll();DashboardView.attachStandalone(a);', '''
      if(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","").isEmpty())
        a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_blue_draft").apply();
      a.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("dashboard_page",1).apply();
      a.content.removeAll();DashboardView.attachStandalone(a);''', 1)
    desktop = desktop.replace('copy(JsDiagnosticsPreview.observations(state),field(DashboardView.class,"DIAG"));', '''
      copy(JsDiagnosticsPreview.observations(state),field(DashboardView.class,"DIAG"));
      if(state.equals("received")&&vehicle.equals("js")){
        Telemetry t=(Telemetry)field(DashboardView.class,"DATA");
        Method put=Telemetry.class.getDeclaredMethod("put",int.class,float.class,boolean.class,long.class);put.setAccessible(true);
        int[] signals={0,7,8,2,Telemetry.INTAKE,Telemetry.BOOST,Telemetry.ACCEL_PEDAL,10};
        float[] values={4560,124,4,91,34,0.8f,38,0};
        for(int i=0;i<signals.length;i++)put.invoke(t,signals[i],values[i],true,1000L);
        t.hscanJs(0xc7,0x1d,new byte[]{0,5,0,0,0,0,0,0},1000);
        t.hscanJs(0xc7,0x1e,new byte[]{0,4,0,0,0,0,0,0},1000);
        t.hscanJs(0xc7,0x04,new byte[]{0,0,0,0,0,0x10,0,0},1000);
      }''', 1)
    desktop = desktop.replace(' public static void main(String[] args)throws Exception{', CHECK + '''
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("--draft-check")){
    SwingUtilities.invokeAndWait(()->{try{draftCheck(args[1]);}catch(Exception e){throw new RuntimeException(e);}});return;
  }
''', 1)
    desktop = desktop.replace('new JFrame("JS Dashboard Preview")', 'new JFrame("F1 Blue — Astryx draft / synthetic data")')
    if slug == 'f1-2':
        desktop = desktop.replace('f1_blue_draft', 'f1_2_draft').replace('F1 Blue —', 'Liquid Glass —')
    runtime.STUBS['DashboardDesktop.java'] = desktop
    java, classes = runtime.compile_runtime(folder)

    return java, classes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--live', action='store_true', help='Open clickable Java host simulator')
    parser.add_argument('--theme', choices=('f1-blue', 'f1-2'), default='f1-blue')
    args = parser.parse_args()
    artifacts = HERE / 'artifacts' / ('f1-2' if args.theme == 'f1-2' else '')
    artifacts.mkdir(exist_ok=True)
    subprocess.run(['npm', 'run', 'astryx', '--', 'experimental', 'theme', 'compile',
                    f'themes/{args.theme}/{args.theme}.theme.ts'], check=True, cwd=HERE)
    subprocess.run(['node', str(HERE / 'export_draft.mjs'), args.theme], check=True, cwd=HERE)
    with tempfile.TemporaryDirectory(prefix='f1-blue-', dir=artifacts) as folder:
        java, classes = compile_draft(folder, args.theme)
        # Private asset overlay for this host process. The APK catalog is untouched.
        assets = Path(folder) / 'assets'
        shutil.copytree(ROOT / 'apps/dashboard/src/0x1101L/assets', assets)
        catalog = assets / 'themes/catalog.json'
        data = json.loads(catalog.read_text())
        data['schemaVersion'] = 3
        theme_id = args.theme.replace('-', '_') + '_draft'
        data['themeIds'].append(theme_id)
        catalog.write_text(json.dumps(data))
        draft = assets / 'themes' / theme_id
        draft.mkdir()
        shutil.copy2(artifacts / 'manifest.json', draft / 'manifest.json')
        config = json.loads((artifacts / 'manifest.json').read_text())
        for path in config['files'].values():
            target = draft / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(artifacts / path, target)
        fonts = json.loads((artifacts / config['files']['fonts']).read_text())['fonts']
        for asset in set(fonts.values()):
            if not asset:
                continue
            # Reuse the reviewed font bundle for host drafts; custom fonts can live next to dashboard.json.
            source = HERE / 'themes' / args.theme / asset
            if not source.is_file():
                source = ROOT / 'apps/dashboard/src/0x1101L/assets/themes/f1_2' / asset
            target = draft / asset
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        command = [java, '-Ddraft.slug=' + args.theme, '-Ddashboard.assets=' + str(assets), '-cp', str(classes), 'DashboardDesktop']
        if not args.live:
            command.insert(1, '-Djava.awt.headless=true')
            command.extend(['--draft-check', str(artifacts)])
        subprocess.run(command, check=True)


if __name__ == '__main__':
    main()
