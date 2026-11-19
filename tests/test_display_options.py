"""Common F1 display preferences through page input and all renderer paths."""
import json, shutil, subprocess, sys, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import dashboard_runtime as runtime
from test_quick_entry_runtime import java_tools
CHECK=r'''
 static void openPage(DashboardView view,int id)throws Exception{
  Field media=DashboardView.class.getDeclaredField("media");media.setAccessible(true);media.setBoolean(view,false);
  Method m=DashboardView.class.getDeclaredMethod("openSettingsPage",int.class);m.setAccessible(true);m.invoke(view,id);
  Field f=DashboardView.class.getDeclaredField("settingsVisible");f.setAccessible(true);f.setBoolean(view,true);
 }
 static void settingTap(DashboardView view,float x,float y)throws Exception{tapSettings(view,x,y);}
 static void choose(DashboardView v,int page,int index,boolean apply)throws Exception{
  openPage(v,page);tapEditorIndex(v,index);settingTap(v,apply?700:70,88);
 }
 static DashboardPage.Bounds menuBounds(DashboardView v,int page)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(v,"activeTheme");
  DashboardPage.Menu menu=(DashboardPage.Menu)field(v,"pageMenu");
  return DashboardPage.navigationBounds(theme,menu,page);
 }
 static void menuTap(DashboardView v,int page)throws Exception{
  DashboardPage.Bounds b=menuBounds(v,page);
  tap(v,b.x+b.width/2,b.y+b.height/2);
 }
 static void menuGeometry(DashboardView v,android.graphics.Canvas frame,boolean left)throws Exception{
  DashboardPage.Bounds f1=menuBounds(v,DashboardPage.F1),back=menuBounds(v,DashboardPage.BACK);
  DashboardPage.Bounds settings=menuBounds(v,DashboardPage.SETTINGS),clock=menuBounds(v,DashboardPage.CLOCK);
  for(int id:new int[]{DashboardPage.PERFORMANCE,DashboardPage.OVERVIEW,DashboardPage.GAUGES,DashboardPage.GRAPHS,DashboardPage.BACK})
   check(menuBounds(v,id).width==f1.width,"icon buttons have equal widths");
  if(left){
   check(f1.width==f1.height&&settings.width==settings.height&&back.height==back.width,"sidebar page, settings and back buttons are square");
  }else check(back.height<f1.height,"top back is shorter than page buttons");
  boolean renderedClock=false;
  for(android.graphics.Canvas.TextDraw command:frame.commands)if(command.text.matches("[0-9]{2}:[0-9]{2}")){
   renderedClock=true;
   check(command.x>=clock.x-.01&&command.x+command.width<=clock.x+clock.width+.01,"actual clock fits its slot");
   if(!left)check(command.x-(settings.x+settings.width)>=11.99&&back.x-command.x-command.width>=11.99,"clock text has 12px of clearance on both sides");
  }
  check(renderedClock,"clock still renders as text");
  if(left)check(menuBounds(v,DashboardPage.PERFORMANCE).y-clock.y-clock.height>=12,"sidebar separates clock from the first button");
 }
 static void paddingCheck()throws Exception{
  for(String id:new String[]{"original","aurora_focus","f1_2","f1_2_flat","f1_2_optics"}){
   com.veloster.dashboard.theme.ThemeSpec base=new com.veloster.dashboard.theme.ThemeRepository(com.veloster.dashboard.theme.ThemeCatalog.load(new Activity())).load(id,com.veloster.dashboard.theme.ThemeRepository.SUPPORTED_SCHEMA_VERSION);
   for(boolean left:new boolean[]{false,true})for(boolean wide:new boolean[]{false,true}){
    com.veloster.dashboard.theme.ThemeSpec theme=com.veloster.dashboard.theme.ThemeCatalog.withMenuPosition(base,left?2:1);
    if(left)check(theme.shell().left(),"theme accepts left menu for shared padding: "+id);
    DashboardPage.Regions regions=DashboardPage.regions(wide?1920:800,wide?720:480,theme);
    check(regions.mediaEnabled==wide,"wide region state");
    DashboardPage.Content common=new DashboardPage.Content(theme,"gauges",regions,false);
    DashboardPage.Bounds page=common.padded(theme);
    float outer=theme.spacing.cardPadding;
    check(Math.abs(page.x-common.x-outer*(left?.5f:1))<.02f,"common left edge "+id+left+wide);
    check(Math.abs(common.x+common.width-page.right()-outer*(wide?.5f:1))<.02f,"common right edge "+id+left+wide);
    DashboardPage.Content f1=new DashboardPage.Content(theme,"f1",regions,!wide);
    DashboardPage.Bounds shift=f1.f1(theme,"shift");
    float inset=f1.f1Grid==null?theme.spacing.f1SidePadding:f1.f1Grid.inset;
    check(Math.abs(shift.x-f1.x-inset*(left?.5f:1))<.02f,"F1 left edge "+id+left+wide);
    check(Math.abs(f1.x+f1.width-shift.right()-inset*(wide?.5f:1))<.02f,"F1 right edge "+id+left+wide);
    if(!wide){
     DashboardPage.Bounds footer=f1.footer(-10);
     DashboardPage.Content other=new DashboardPage.Content(theme,"gauges",regions,true);
     DashboardPage.Bounds shared=other.footer(-10);
     check(Math.abs(footer.x-shared.x)<.02f&&Math.abs(footer.y-shared.y)<.02f&&
           Math.abs(footer.width-shared.width)<.02f&&Math.abs(footer.height-shared.height)<.02f,
           "F1 footer uses the same geometry as other pages "+id+left);
     for(int action:new int[]{2,-1,0,1}){
      DashboardPage.Bounds f1Action=f1.footer(action),otherAction=other.footer(action);
      check(Math.abs(f1Action.x-otherAction.x)<.02f&&Math.abs(f1Action.y-otherAction.y)<.02f&&
            Math.abs(f1Action.width-otherAction.width)<.02f&&Math.abs(f1Action.height-otherAction.height)<.02f,
            "F1 footer control uses shared geometry "+id+left+action);
     }
    }
   }
  }
 }
 static DashboardView menuCheck(DashboardView v,Activity a,boolean left)throws Exception{
  choose(v,5,left?2:1,true);
  menuTap(v,1);
  android.graphics.Canvas labels=draw(v);
  check(labels.texts.contains("F1"),"menu names are the default");
  menuTap(v,6);tapEditorLabel(v,UiText.t("UI 설정"));tapEditorLabel(v,UiText.t("메뉴 표시 방식"));
  check((Integer)field(field(v,"settingsEditor"),"screen")==12,"UI group opens menu display editor");
  check(draw(v).texts.contains(UiText.t("아이콘"))&&draw(v).texts.contains(UiText.t("이름")),"localized menu display choices");
  settingTap(v,200,208);settingTap(v,70,88);menuTap(v,1);
  check(draw(v).texts.contains("F1"),"cancel leaves menu names");
  menuTap(v,6);tapEditorLabel(v,UiText.t("UI 설정"));tapEditorLabel(v,UiText.t("메뉴 표시 방식"));
  settingTap(v,200,208);settingTap(v,700,88);menuTap(v,1);
  android.graphics.Canvas icons=draw(v);
  check(!icons.texts.contains("F1")&&icons.texts.contains("ENGINE"),"real menu tap works in icon mode");
  com.veloster.dashboard.theme.ThemeSpec t=(com.veloster.dashboard.theme.ThemeSpec)field(v,"activeTheme");
  menuGeometry(v,icons,left);
  DashboardPage.Bounds f1=menuBounds(v,DashboardPage.F1);
  int cx=(int)(f1.x+f1.width/2),cy=(int)(f1.y+f1.height/2);
  boolean selectedFlag=false;for(int dx=-7;dx<=7;dx++)for(int dy=-8;dy<=7;dy++)if(icons.pixel(cx+dx,cy+dy)==t.accent)selectedFlag=true;
  check(selectedFlag&&labels.pixel(cx-8,cy)!=t.accent,"scaled vector flag is rendered in the selected tab");
  v=create(a);
  check(!draw(v).texts.contains("F1"),"view recreation restores icons");
  Activity exit=new Activity();
  exit.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2").putInt("menu_position",left?2:1).apply();
  exit.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("menu_icon_mode",1).apply();
  DashboardView exitView=create(exit);
  DashboardPage.Bounds back=menuBounds(exitView,DashboardPage.BACK);
  tap(exitView,left?back.x+back.width/2:back.x-6,left?back.y-2:back.y+back.height/2);
  check(!exit.isFinishing(),"space beside the back button is not clickable");
  menuTap(exitView,DashboardPage.BACK);
  check(exit.isFinishing(),"back button remains clickable");
  menuTap(v,6);tapEditorLabel(v,UiText.t("UI 설정"));tapEditorLabel(v,UiText.t("메뉴 표시 방식"));
  settingTap(v,200,158);settingTap(v,700,88);menuTap(v,1);
  check(draw(v).texts.contains("F1"),"switching to names restores labels");
  return v;
 }
 static android.graphics.Bitmap activeBackground(DashboardView view)throws Exception{
  return ((com.veloster.dashboard.theme.ThemeResources)field(view,"activeThemeResources")).background();
 }
 static int wallpaperPixel(android.graphics.Bitmap bitmap)throws Exception{
  return ((java.awt.image.BufferedImage)field(bitmap,"image")).getRGB(1500,900);
 }
 static int wallpaperPixel(String path)throws Exception{
  return javax.imageio.ImageIO.read(new java.io.File(System.getProperty("dashboard.assets"),"themes/"+path)).getRGB(1500,900);
 }
 static void wallpaperCheck()throws Exception{
  fixture("received","js");
  Activity activity=new Activity();LanguageSettings.save(activity,"ko");
  DashboardView view=create(activity);draw(view);
  check(activeBackground(view)==null,"Original uses its color backdrop with no wallpaper override");
  menuTap(view,6);tapEditorLabel(view,"UI 설정");tapEditorLabel(view,"배경화면");
  check(settingsScreen(view)==14,"settings opens the wallpaper grid");
  Object editor=field(view,"settingsEditor");
  String[] labels=(String[])field(editor,"labels");
  check(labels.length==7&&java.util.Arrays.asList(labels).containsAll(java.util.Arrays.asList("Original","Aurora Focus","Material Auto","Blue","Red","Green","Mono")),
    "the gallery includes color and image wallpapers from the remaining themes");
  int auroraIndex=java.util.Arrays.asList(labels).indexOf("Aurora Focus");
  android.graphics.Bitmap thumb=((android.graphics.Bitmap[])field(editor,"wallpaperThumbnails"))[auroraIndex];
  check(thumb!=null&&thumb.getWidth()==480&&thumb.getHeight()==288,"a default image without a thumbnail gets a sampled preview");
  tapEditorLabel(view,"Aurora Focus");
  check(activeBackground(view).getWidth()==1920&&activeBackground(view).getHeight()==1152
        &&wallpaperPixel(activeBackground(view))==wallpaperPixel("aurora_focus/backgrounds/test.png"),
        "another theme's image loads at full size from its owner");
  check(activeBackground(create(activity))!=null,"a selection survives view recreation");
  settingTap(view,700,88);
  check(activeBackground(view)==null,"reset restores the active theme's color backdrop");
  com.veloster.dashboard.theme.ThemeCatalog catalog=com.veloster.dashboard.theme.ThemeCatalog.load(activity);
  com.veloster.dashboard.theme.ThemeSpec active=catalog.find("f1_2");
  activity.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2").apply();
  view=create(activity);
  openPage(view,14);tapEditorLabel(view,"Original");
  com.veloster.dashboard.theme.ThemeResources selected=(com.veloster.dashboard.theme.ThemeResources)field(view,"activeThemeResources");
  com.veloster.dashboard.theme.ThemeSpec original=catalog.find("original");
  check(activeBackground(view)==null&&selected.backdropColor()==original.background
        &&selected.backdrop().mode==original.backdrop.mode
        &&selected.backdrop().endColor==original.backdrop.endColor,
        "selecting a color theme replaces F1-2's image and uses its full backdrop");
  com.veloster.dashboard.theme.ThemeSurface surface=new com.veloster.dashboard.theme.ThemeSurface();
  android.graphics.Canvas actual=new android.graphics.Canvas(320,192),expected=new android.graphics.Canvas(320,192);
  surface.backdrop(actual,active,selected,320,192);
  surface.backdrop(expected,original,com.veloster.dashboard.theme.ThemeResources.load(activity,original),320,192);
  check(actual.pixel(12,20)==expected.pixel(12,20)&&actual.pixel(250,150)==expected.pixel(250,150),
        "selected color backdrop renders like its source theme, including the gradient");
  check(((com.veloster.dashboard.theme.ThemeResources)field(create(activity),"activeThemeResources")).background()==null
        &&"original:color".equals(activity.getSharedPreferences("dashboard_theme",0).getString("wallpaper_id","")),
        "color selection survives recreation");
  settingTap(view,700,88);
  check(activeBackground(view)!=null&&wallpaperPixel(activeBackground(view))==wallpaperPixel("f1_2/backgrounds/blue.png"),
        "reset after a color selection restores F1-2 Blue");
 }
 static void liquidGlassSettingsCheck()throws Exception{
  Activity a=new Activity();a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2_optics").apply();
  a.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("dashboard_page",1).apply();
  DashboardView view=create(a);
  android.graphics.Canvas standardFrame=draw(view);
  check(((com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme")).usesLiquidGlass(),"Optics activates the dedicated material engine");
  openPage(view,9);
  check(java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels")).contains("Liquid Glass"),
        "Liquid Glass settings appear while its engine is active");
  tapEditorLabel(view,"Liquid Glass");
  check(java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels")).containsAll(java.util.Arrays.asList("투명도","블러 강도","하이라이트")),
        "engine settings expose transparency, blur and highlight controls");
  tapEditorLabel(view,"투명도");tapEditorIndex(view,0);settingTap(view,700,88);
  com.veloster.dashboard.theme.ThemeSpec active=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  check(active.liquidGlassTransparencyReduced(),"reduce-transparency preference reaches the active material renderer");
  tapEditorLabel(view,"블러 강도");tapEditorIndex(view,3);settingTap(view,700,88);
  active=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  check(active.liquidGlassBlurLevel()==2,"high blur preference reaches the active material renderer");
  tapEditorLabel(view,"하이라이트");tapEditorIndex(view,0);settingTap(view,700,88);
  active=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  check(active.liquidGlassHighlightLevel()==0,"subtle highlight preference reaches the active material renderer");
  DashboardView restored=create(a);active=(com.veloster.dashboard.theme.ThemeSpec)field(restored,"activeTheme");
  check(active.liquidGlassTransparencyReduced()&&active.liquidGlassBlurLevel()==2&&active.liquidGlassHighlightLevel()==0,
        "Liquid Glass preferences survive view recreation");
  tapEditorLabel(view,"투명도");tapEditorIndex(view,4);settingTap(view,700,88);
  tapEditorLabel(view,"블러 강도");tapEditorIndex(view,4);settingTap(view,700,88);
  active=(com.veloster.dashboard.theme.ThemeSpec)field(create(a),"activeTheme");
  check(active.liquidGlassTransparencyLevel()==2&&active.liquidGlassBlurLevel()==4,
        "expanded transparency and blur extremes survive view recreation");
  settingTap(view,70,88);settingTap(view,70,88);settingTap(view,70,88);
  android.graphics.Canvas adjustedFrame=draw(view);int changed=0;
  for(int y=0;y<480;y++)for(int x=0;x<800;x++)if(standardFrame.pixel(x,y)!=adjustedFrame.pixel(x,y))changed++;
  check(changed>100,"Liquid Glass controls change rendered surface pixels");
  a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2_flat").apply();
  DashboardView flat=create(a);openPage(flat,9);
  check(!((com.veloster.dashboard.theme.ThemeSpec)field(flat,"activeTheme")).usesLiquidGlass()
        &&!java.util.Arrays.asList((String[])field(field(flat,"settingsEditor"),"labels")).contains("Liquid Glass"),
        "Liquid Glass settings are hidden when the engine is inactive");
 }
 static void displayCheck()throws Exception{
  paddingCheck();
  wallpaperCheck();
  liquidGlassSettingsCheck();
  for(String vehicle:new String[]{"js","js_n","pd"})for(String themeId:new String[]{"original","aurora_focus","f1_2","f1_2_flat","f1_2_optics"}){
   fixture("received",vehicle);
   Telemetry telemetry=(Telemetry)field(DashboardView.class,"DATA");Method put=Telemetry.class.getDeclaredMethod("put",int.class,float.class,boolean.class,long.class);put.setAccessible(true);
   for(int[] pair:new int[][]{{0,4560},{7,124},{8,4},{Telemetry.ACCEL_PEDAL,38},{10,24}})put.invoke(telemetry,pair[0],(float)pair[1],true,1000L);
   Activity a=new Activity();LanguageSettings.save(a,"ko");
   a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id",themeId).apply();
   DashboardView v=create(a);
   choose(v,6,1,false);check((Integer)field(v,"pedalBarStyle")==0,"back discards draft");
   choose(v,6,1,true);choose(v,7,0,true);
   for(int order=0;order<6;order++){
    choose(v,8,order,true);
    com.veloster.dashboard.theme.ThemeSpec t=(com.veloster.dashboard.theme.ThemeSpec)field(v,"activeTheme");
    check(t.id.equals(themeId),"fixture loaded: "+themeId+" actual "+t.id);
    check(t.f1Order()==order&&t.pedalBarStyle()==1&&t.brakeBarStyle()==0,"common settings on "+themeId);
    String[][] orders={{"speed","gear","rpm"},{"rpm","gear","speed"},{"gear","speed","rpm"},{"gear","rpm","speed"},{"speed","rpm","gear"},{"rpm","speed","gear"}};
    com.veloster.dashboard.view.DashboardPage.Content c=new com.veloster.dashboard.view.DashboardPage.Content(t,"f1");
    for(boolean footer:new boolean[]{false,true}){
     float gapX=c.f1Grid==null?t.spacing.gridGapX:c.f1Grid.gap,gapY=c.f1Grid==null?t.spacing.gridGapY:c.f1Grid.gap;
     com.veloster.dashboard.view.DashboardPage.Content layout=footer?new com.veloster.dashboard.view.DashboardPage.Content(t,"f1",800,480,true):c;
     com.veloster.dashboard.view.DashboardPage.Bounds shift=layout.f1(t,"shift");
     float columnWidth=(shift.width-2*gapX)/3,edge=shift.x;
     for(String id:orders[order]){
      com.veloster.dashboard.view.DashboardPage.Bounds b=layout.f1(t,id);
      check(Math.abs(b.width-columnWidth)<.01&&Math.abs(b.x-edge)<.01,"exact thirds and horizontal gaps for "+themeId);
      edge=b.x+b.width+gapX;
      check(b.width>0&&b.height>0&&b.x>=c.x&&b.y>=c.y&&b.x+b.width<=c.x+c.width+.01&&b.y+b.height<=c.y+c.height+.01,"content bounds");
      for(String detail:new String[]{"boost","temperatures"}){
       com.veloster.dashboard.view.DashboardPage.Bounds d=layout.f1(t,detail);
       if(Math.abs(d.x-b.x)<.01)check(Math.abs(d.y-b.y-b.height-gapY)<.01,"card below readout is separated by the theme gap");
      }
     }
     String[] all={"shift","speed","gear","rpm","boost","temperatures","pedal","brake"};
     for(int i=0;i<all.length;i++)for(int j=i+1;j<all.length;j++){
      com.veloster.dashboard.view.DashboardPage.Bounds p=layout.f1(t,all[i]),q=layout.f1(t,all[j]);
      check(p.x+p.width<=q.x+.01||q.x+q.width<=p.x+.01||p.y+p.height<=q.y+.01||q.y+q.height<=p.y+.01,"no overlap: "+themeId+" "+all[i]+" "+all[j]);
     }
    }
    Field visible=DashboardView.class.getDeclaredField("settingsVisible");visible.setAccessible(true);visible.setBoolean(v,false);
    ((com.veloster.dashboard.view.DashboardPage.State)field(v,"pageState")).restore(1);
    for(boolean expandedMedia:new boolean[]{false,true}){Field media=DashboardView.class.getDeclaredField("media");media.setAccessible(true);media.setBoolean(v,expandedMedia);
     for(int[] size:new int[][]{{800,480},{800,425},{400,240}}){v.setSize(size[0],size[1]);android.graphics.Canvas frame=draw(v);check(frame.texts.contains("124")&&frame.texts.contains("4560")&&(frame.texts.contains("D4")||frame.texts.contains("4")),"same telemetry in every layout "+vehicle+" "+themeId+" "+order+" "+frame.texts);for(android.graphics.Canvas.TextDraw text:frame.commands)check(Math.abs(text.scaleX-text.scaleY)<.001,"uniform glyphs");
      String[] readings={"124","D4","4560"};
      String leftId=DashboardPage.f1OrderNode(order,0),rightId=DashboardPage.f1OrderNode(order,2);
      String leftReading=leftId.equals("speed")?readings[0]:leftId.equals("rpm")?readings[2]:(frame.texts.contains("D4")?"D4":"4");
      String rightReading=rightId.equals("speed")?readings[0]:rightId.equals("rpm")?readings[2]:(frame.texts.contains("D4")?"D4":"4");
      android.graphics.Canvas.TextDraw leftText=null,rightText=null;
      for(android.graphics.Canvas.TextDraw text:frame.commands){
       if(text.text.equals(leftReading)&&text.size>25)leftText=text;
       if(text.text.equals(rightReading)&&text.size>25)rightText=text;
      }
      check(leftText!=null&&rightText!=null&&Math.abs(leftText.size-rightText.size)<.01,"side slots share value font size for "+vehicle+" "+themeId+" expanded media "+expandedMedia);
     }
    }
   }
   v=create(a);check((Integer)field(v,"f1Order")==5&&(Integer)field(v,"pedalBarStyle")==1,"recreated preferences");
   if(themeId.equals("f1_2")){v=menuCheck(v,a,true);LanguageSettings.save(a,"en");v=menuCheck(v,a,false);}
   if(themeId.equals("f1_2")){
    openPage(v,14);
    check(java.util.Arrays.asList((String[])field(field(v,"settingsEditor"),"labels")).containsAll(java.util.Arrays.asList("Blue","Red","Green","Mono")),
          "wallpaper gallery lists options from every theme bundle");
    tapEditorLabel(v,"Red");
    check("f1_2_red".equals(a.getSharedPreferences("dashboard_theme",0).getString("wallpaper_id","")),
          "wallpaper selection saves a global wallpaper ID");
    check(wallpaperPixel(activeBackground(v))==wallpaperPixel("f1_2/backgrounds/red.png"),
          "selected wallpaper loads from its owning theme bundle");
    openPage(v,1);tapEditorIndex(v,3);settingTap(v,700,88);
    check("f1_2_flat".equals(((com.veloster.dashboard.theme.ThemeSpec)field(v,"activeTheme")).id)
          &&"f1_2_red".equals(a.getSharedPreferences("dashboard_theme",0).getString("wallpaper_id","")),
          "custom wallpaper persists across theme changes");
    openPage(v,14);settingTap(v,700,88);
    check(a.getSharedPreferences("dashboard_theme",0).getString("wallpaper_id","none").equals(""),
          "Theme default clears the global wallpaper override");
    check(wallpaperPixel(activeBackground(v))==wallpaperPixel("f1_2/backgrounds/blue.png"),
          "theme default reloads the bundled Blue background");
   }
   choose(v,12,1,true);
   openPage(v,1);settingTap(v,180,208);
   String saved=a.getSharedPreferences("dashboard_theme",0).getString("theme_id","");
   settingTap(v,520,88);check((Integer)field(field(v,"settingsEditor"),"screen")==11,"theme preview is a page");
   check(draw(v).texts.contains(UiText.t("예시 데이터")),"preview indicates synthetic data");
   check(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","").equals(saved),"preview leaves saved theme intact");
   settingTap(v,70,88);check((Integer)field(field(v,"settingsEditor"),"selected")==1,"preview back retains draft selection");
   settingTap(v,700,88);
   check((Integer)field(v,"f1Order")==5&&(Integer)field(v,"pedalBarStyle")==1&&
         ((com.veloster.dashboard.view.DashboardPage.Menu)field(v,"pageMenu")).icons(),"theme switch retains common preferences");
   if(themeId.equals("original")){
    openPage(v,1);
    android.graphics.Canvas picker=draw(v);
    check(picker.texts.contains("Liquid Glass")&&picker.texts.contains("Liquid Glass (Flat)")
          &&picker.texts.contains("Liquid Glass (Optics)"),"all three Glass themes appear in the actual theme picker");
    tapEditorIndex(v,2);settingTap(v,700,88);
    check("f1_2".equals(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","")),"Glass retains the stable ID");
    openPage(v,1);tapEditorIndex(v,3);settingTap(v,700,88);
    check("f1_2_flat".equals(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","")),"Flat persists as a separate selection");
    check("f1_2_flat".equals(((com.veloster.dashboard.theme.ThemeSpec)field(create(a),"activeTheme")).id),"Flat restores on recreation");
    openPage(v,1);tapEditorLabel(v,"Liquid Glass (Optics)");settingTap(v,700,88);
    check("f1_2_optics".equals(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","")),"Optics persists as a separate selection");
    DashboardView optics=create(a);
    check("f1_2_optics".equals(((com.veloster.dashboard.theme.ThemeSpec)field(optics,"activeTheme")).id)
          &&wallpaperPixel(activeBackground(optics))==wallpaperPixel("f1_2/backgrounds/blue.png"),
          "Optics restores on recreation with the inherited Blue wallpaper");
   }
  }
  com.veloster.dashboard.theme.ThemeSurface surface=new com.veloster.dashboard.theme.ThemeSurface();
  android.graphics.Paint paint=new android.graphics.Paint(3);android.graphics.Canvas continuous=new android.graphics.Canvas(220,20),segmented=new android.graphics.Canvas(220,20);
  surface.levelBar(continuous,paint,0,0,200,10,.5f,0xffffffff,0xff222222,0);
  surface.levelBar(segmented,paint,0,0,200,10,.5f,0xffffffff,0xff222222,1);
  check(continuous.image().getRGB(8,4)==0xffffffff&&segmented.image().getRGB(8,4)!=0xffffffff,"segmented gaps differ from continuous fill");
  Activity corrupt=new Activity();corrupt.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("f1_order",99).putString("pedal_bar_style","bad").putInt("menu_icon_mode",99).apply();
  com.veloster.dashboard.theme.ThemeSettingsStore store=new com.veloster.dashboard.theme.ThemeSettingsStore(corrupt);check(store.f1Order()==0&&store.pedalBarStyle()==0&&!store.menuIcons(),"invalid saved values reset");
  System.out.println("PASS five bundled themes: common display options, menu input, and cross-theme wallpaper selection, restoration and fallback");
 }
'''
class DisplayTests(unittest.TestCase):
 def test_common_options(self):
  with tempfile.TemporaryDirectory(prefix='display-options-') as tmp:
   tmp=Path(tmp);java,classes=runtime.compile_runtime(tmp);_,javac=java_tools()
   source='import com.veloster.dashboard.view.DashboardPage;\n'+runtime.STUBS['DashboardDesktop.java'];start=source.index(' static void selfCheck()throws Exception{');end=source.index(' public static void main(',start)
   probe=tmp/'DashboardDesktop.java';probe.write_text(source[:start]+CHECK+''' static void accentPaletteCheck()throws Exception{
  fixture("received","js");Activity a=new Activity();LanguageSettings.save(a,"ko");
  DashboardView v=create(a);openPage(v,9);
  check(!java.util.Arrays.asList((String[])field(field(v,"settingsEditor"),"labels")).contains("포인트 컬러"),"Original does not expose accent palettes");
  a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","material_auto_cowork").apply();v=create(a);
  openPage(v,9);Object editor=field(v,"settingsEditor");String[] labels=(String[])field(editor,"labels");int[] actions=(int[])field(editor,"actions");
  int paletteRow=java.util.Arrays.asList(labels).indexOf("포인트 컬러");
  check(paletteRow>=0&&actions[paletteRow]==28,"Material Auto exposes the accent palette in UI settings");
  openPage(v,28);editor=field(v,"settingsEditor");
  check(((int[])field(editor,"swatches")).length==7,"Material palette renders seven named color swatches");
  tapEditorIndex(v,2);settingTap(v,700,88);
  com.veloster.dashboard.theme.ThemeSpec active=(com.veloster.dashboard.theme.ThemeSpec)field(v,"activeTheme");
  check(active.accent==0xff48c9b0,"Apply updates the live accent color");
  v=create(a);active=(com.veloster.dashboard.theme.ThemeSpec)field(v,"activeTheme");
  check(active.accent==0xff48c9b0,"selected accent restores after recreation");
  openPage(v,28);tapEditorIndex(v,4);settingTap(v,70,88);
  active=(com.veloster.dashboard.theme.ThemeSpec)field(v,"activeTheme");
  check(active.accent==0xff48c9b0,"back discards the unsaved palette choice");
  Activity glassActivity=new Activity();glassActivity.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2").apply();
  DashboardView glass=create(glassActivity);openPage(glass,9);
  check(java.util.Arrays.asList((String[])field(field(glass,"settingsEditor"),"labels")).contains("포인트 컬러"),"Liquid Glass exposes its own accent setting");
  openPage(glass,28);editor=field(glass,"settingsEditor");String[] glassLabels=(String[])field(editor,"labels");int[] glassSwatches=(int[])field(editor,"swatches");
  check("인디고".equals(glassLabels[2])&&"보라".equals(glassLabels[3])&&"핑크".equals(glassLabels[4])&&glassSwatches[2]==0xff6155f5,
        "Liquid Glass displays the iOS system-color palette independently of Material");
  tapEditorIndex(glass,2);settingTap(glass,700,88);
  com.veloster.dashboard.theme.ThemeSpec glassActive=(com.veloster.dashboard.theme.ThemeSpec)field(glass,"activeTheme");
  check(glassActive.accent==0xff6155f5,"Liquid Glass applies its selected accent");
  glass=create(glassActivity);glassActive=(com.veloster.dashboard.theme.ThemeSpec)field(glass,"activeTheme");
  check(glassActive.accent==0xff6155f5,"Liquid Glass restores its per-theme accent selection");
 }
 static void selfCheck()throws Exception{displayCheck();accentPaletteCheck();}
'''+source[end:])
   subprocess.run([javac,'-encoding','UTF-8','-cp',str(classes),'-d',str(classes),str(probe)],check=True)
   assets=tmp/'assets'
   shutil.copytree(ROOT/'apps/dashboard/src/0x1101L/assets', assets)
   fixtures={'aurora_focus':'test.png'}
   for name,image in fixtures.items():
    components=assets/'themes'/name/'components.json'
    data=json.loads(components.read_text())
    data['assets']['background']='backgrounds/'+image
    components.write_text(json.dumps(data),encoding='utf-8')
   art=assets/'themes/aurora_focus/backgrounds/test.png'
   art.parent.mkdir()
   shutil.copyfile(assets/'themes/f1_2/backgrounds/green.png',art)
   result=subprocess.run([java,'-Djava.awt.headless=true','-Ddashboard.assets='+str(assets),'-cp',str(classes),'DashboardDesktop','--check'],capture_output=True,text=True,timeout=90)
   self.assertEqual(result.returncode,0,result.stdout+result.stderr);print(result.stdout)
if __name__=='__main__':unittest.main()
