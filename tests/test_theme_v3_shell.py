"""Exercise v3 shell against the production DashboardView on a temporary asset overlay."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import dashboard_runtime as runtime

CHECK = '''
 static Object editor(DashboardView view)throws Exception{return field(view,"settingsEditor");}
 static void pageHold(DashboardView view,String page)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec t=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  com.veloster.dashboard.view.DashboardPage.Regions r=com.veloster.dashboard.view.DashboardPage.regions(view.getWidth(),view.getHeight(),t);
  draw(view);
  com.veloster.dashboard.view.DashboardPage.Bounds b=page.equals("gauges")?
   ((com.veloster.dashboard.view.gauge.GaugeLayout)field(view,"gaugeLayout")).card(0):
   ((com.veloster.dashboard.view.graph.GraphLayout)field(view,"graphLayout")).card(0);
  float x=b.atX(.5f)*r.scale,y=b.atY(.5f)*r.scale;
  touch(view,0,x,y);waitForTimers();touch(view,1,x,y);
 }
 static void menuNavigation(DashboardView view,int id)throws Exception{
  if(id==6&&(Boolean)field(view,"settingsVisible"))return;
  pressMenu(view,id);
 }
 static void pressMenu(DashboardView view,int id)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  com.veloster.dashboard.view.DashboardPage.Menu menu=(com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu");
  com.veloster.dashboard.view.DashboardPage.Regions r=com.veloster.dashboard.view.DashboardPage.regions(view.getWidth(),view.getHeight(),theme);
  com.veloster.dashboard.view.DashboardPage.Bounds b=com.veloster.dashboard.view.DashboardPage.navigationBounds(theme,menu,id,r.logicalDisplayWidth);
  float x=b.x+b.width/2,y=b.y+b.height/2;
  tap(view,r.left+x*r.scale,r.top+y*r.scale);
 }
 static void settingsTap(DashboardView view,float x,float y)throws Exception{tapSettings(view,x,y);}
 static void rootSettings(DashboardView view)throws Exception{
  if(!(Boolean)field(view,"settingsVisible"))menuNavigation(view,6);
  while((Integer)field(editor(view),"screen")!=-1)settingsTap(view,70,88);
  check((Integer)field(editor(view),"screen")==-1,"settings opens on root");
 }
 static void selectEditorIndex(DashboardView view,int index)throws Exception{tapEditorIndex(view,index);}
 static void menuCheck()throws Exception{
  for(String vehicle:new String[]{"js","js_n","pd"})for(String themeId:new String[]{"shell_test","original"}){
   fixture("received",vehicle);Activity a=new Activity();LanguageSettings.save(a,"ko");
   a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id",themeId).apply();
   DashboardView view=create(a);draw(view);rootSettings(view);tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("페이지 메뉴"));
   check((Integer)field(editor(view),"screen")==4,"page menu editor opens from UI group");
   String[] labels=(String[])field(editor(view),"labels");check(labels.length==5,"five page menu choices");
   check(labels[1].equals(UiText.t("전체 조망")),"overview menu option covers only overview");
   settingsTap(view,150,258);settingsTap(view,70,88);
   com.veloster.dashboard.view.DashboardPage.Menu menu=(com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu");
   check(menu.mask()==31,"cancel leaves menu unchanged");
   tapEditorLabel(view,UiText.t("페이지 메뉴"));selectEditorIndex(view,0);selectEditorIndex(view,2);settingsTap(view,700,88);
   check(menu.count()==3&&!menu.visible(1)&&!menu.visible(4),"apply hides individual page entries");
   android.graphics.Canvas frame=draw(view);check(!frame.texts.contains("F1")&&!frame.texts.contains(UiText.t("퍼포먼스")),"hidden entries are not drawn");
   com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
   if(theme.shell().left()){
    check(com.veloster.dashboard.view.DashboardPage.shellTop(theme,menu,7)==theme.shell().top,"clock pinned at top");
    com.veloster.dashboard.view.DashboardPage.Bounds back=com.veloster.dashboard.view.DashboardPage.navigationBounds(theme,menu,8);
    check(back.y+back.height==480-theme.shell().bottom,"square back stays pinned at bottom");
    check(com.veloster.dashboard.view.DashboardPage.shellHit(theme,menu,78,com.veloster.dashboard.view.DashboardPage.shellTop(theme,menu,0)+20)==0,"visible menu touch follows compact positions");
    tap(view,78,65);check((Boolean)field(view,"settingsVisible"),"old hidden performance target no longer navigates");
   }else{
    check(com.veloster.dashboard.view.DashboardPage.menuLeft(menu,0)==18,"top menu compacts from left");
    tap(view,500,27);check((Boolean)field(view,"settingsVisible"),"empty old top target no longer navigates");
   }
   menuNavigation(view,0);check(!((Boolean)field(view,"settingsVisible")),"compacted visible page navigates");
   view=create(a);draw(view);menu=(com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu");check(menu.mask()==13,"page visibility restored from preferences");
   rootSettings(view);tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("페이지 메뉴"));
   boolean[] checked=(boolean[])field(editor(view),"checked");for(int i=0;i<5;i++)if(checked[i])selectEditorIndex(view,i);settingsTap(view,700,88);
   check(menu.mask()==0&&draw(view).texts.stream().anyMatch(s->s.contains(UiText.t("돌아가기"))),"all pages can hide while back remains visible");
   rootSettings(view);
   check(draw(view).texts.contains(UiText.t("진단"))==vehicle.equals("js"),"only JS settings exposes diagnostics");
   if(vehicle.equals("js")){
    tapEditorLabel(view,UiText.t("진단"));
    check(draw(view).texts.contains(UiText.t("HUD · 내비")),"diagnostics opens with all page buttons hidden");
    pressMenu(view,6);check(settingsScreen(view)==-1,"settings button returns to root from diagnostics");
   }
   tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("페이지 메뉴"));for(int i=0;i<5;i++)selectEditorIndex(view,i);settingsTap(view,700,88);
   check(menu.mask()==31&&draw(view).texts.contains("F1"),"settings remains reachable and restores pages");
  }
 }
 static void positionCheck()throws Exception{
  for(String vehicle:new String[]{"js","js_n","pd"}){
   fixture("received",vehicle);Activity a=new Activity();LanguageSettings.save(a,"ko");
   DashboardView view=create(a);rootSettings(view);tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("메뉴 위치"));
   check((Integer)field(editor(view),"screen")==5&&((String[])field(editor(view),"labels")).length==3,"menu position editor");
   settingsTap(view,150,258);settingsTap(view,70,88);
   check(!((com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme")).shell().left(),"cancel retains top");
   tapEditorLabel(view,UiText.t("메뉴 위치"));tapEditorLabel(view,UiText.t("왼쪽"));settingsTap(view,700,88);
   check(draw(view).texts.contains(UiText.t("왼쪽")),"left setting is rendered");
   menuNavigation(view,1);check(((com.veloster.dashboard.view.DashboardPage.State)field(view,"pageState")).is(1),"left F1 touch navigates with Original");
   rootSettings(view);settingsTap(view,150,158);settingsTap(view,150,158);
   String[] themes=(String[])field(editor(view),"labels");int focus=-1;for(int i=0;i<themes.length;i++)if(themes[i].equals("Aurora Focus"))focus=i;
   check(focus>=0,"Aurora Focus theme option exists");selectEditorIndex(view,focus);settingsTap(view,700,88);
   check(((com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme")).shell().left(),"theme change preserves left position");
   view=create(a);check(((com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme")).shell().left(),"left position restored");
   rootSettings(view);tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("메뉴 위치"));tapEditorLabel(view,UiText.t("상단"));settingsTap(view,700,88);
   check(draw(view).texts.contains(UiText.t("상단")),"top setting is rendered");
   menuNavigation(view,4);check(draw(view).texts.contains("PERFORMANCE"),"top touch navigates after switching");
   a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","shell_test").apply();
   view=create(a);check(!((com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme")).shell().left(),"top overrides theme sidebar");
   rootSettings(view);tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("메뉴 위치"));tapEditorLabel(view,UiText.t("테마 기본값"));settingsTap(view,700,88);
   check(((com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme")).shell().left(),"theme default restores its sidebar");
  }
 }
 static void uiSettings(DashboardView view)throws Exception{
  rootSettings(view);tapEditorLabel(view,UiText.t("UI 설정"));
 }
 static com.veloster.dashboard.theme.ThemeSpec activeTheme(DashboardView view)throws Exception{
  return (com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
 }
 static void screenCornerCheck()throws Exception{
  for(String id:new String[]{"original","aurora_focus","f1_2","f1_2_flat","f1_2_optics","square_glass","circle_glass","alias_glass"}){
   Activity a=new Activity();
   a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id",id).apply();
   com.veloster.dashboard.theme.ThemeSpec theme=activeTheme(create(a));
   int expected=id.startsWith("f1_2")||id.equals("circle_glass")||id.equals("alias_glass")?32:0;
   check(id.equals(theme.id)&&theme.shell().screenCornerRadius==expected,id+" resolves its screen corner radius");
   boolean squircle=id.startsWith("f1_2")||id.equals("square_glass")||id.equals("alias_glass");
   check(theme.shell().screenCorner.name().equals(squircle?"SQUIRCLE":"CIRCLE"),id+" resolves its screen corner shape");
  }
  for(String vehicle:new String[]{"js","pd"}){
   fixture("received",vehicle);
   DashboardView[] views=new DashboardView[2];
   for(int i=0;i<views.length;i++){
    Activity a=new Activity();
    a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id",i==0?"f1_2":"square_glass")
      .putInt("menu_position",1).apply();
    views[i]=create(a);
    check(!activeTheme(views[i]).shell().left(),"top menu override applies");
   }
   com.veloster.dashboard.theme.ThemeCatalog catalog=(com.veloster.dashboard.theme.ThemeCatalog)field(views[0],"themeCatalog");
  android.graphics.Canvas circleCard=new android.graphics.Canvas(100,100),squircleCard=new android.graphics.Canvas(100,100);
  com.veloster.dashboard.theme.ThemeSurface cardSurface=new com.veloster.dashboard.theme.ThemeSurface();
  cardSurface.draw(circleCard,catalog.find("circle_glass"),com.veloster.dashboard.theme.ThemeSpec.SurfaceRole.DATA_CARD,
    10,10,80,80,0xffffffff,0xff000000);
  cardSurface.draw(squircleCard,catalog.find("f1_2"),com.veloster.dashboard.theme.ThemeSpec.SurfaceRole.DATA_CARD,
    10,10,80,80,0xffffffff,0xff000000);
  check(circleCard.pixel(15,15)==0&&squircleCard.pixel(15,15)!=0,
    "Liquid Glass component surfaces use the theme's squircle curve, while circle uses a circular arc");
   java.lang.reflect.Method apply=DashboardView.class.getDeclaredMethod("applyTheme",com.veloster.dashboard.theme.ThemeSpec.class);
   apply.setAccessible(true);
   for(int[] size:new int[][]{{800,480},{400,240},{1920,720}}){
    for(DashboardView v:views)v.setSize(size[0],size[1]);
    apply.invoke(views[0],catalog.find("f1_2"));
    android.graphics.Canvas squircle=draw(views[0]),square=draw(views[1]);
    apply.invoke(views[0],catalog.find("circle_glass"));
    android.graphics.Canvas circle=draw(views[0]);
    int right=size[0]-1,bottom=size[1]-1;
    int radius=size[1]/15; // 32 at 480 px high.
    for(int startX:new int[]{0,right})for(int startY:new int[]{0,bottom}){
     int dx=startX==0?1:-1,dy=startY==0?1:-1;
     int between=radius*11/48,safe=radius/2;
     int betweenX=startX+dx*between,betweenY=startY+dy*between;
     int safeX=startX+dx*safe,safeY=startY+dy*safe;
     check(squircle.pixel(startX,startY)==0xff000000&&circle.pixel(startX,startY)==0xff000000
       &&square.pixel(startX,startY)!=0xff000000,vehicle+" masks every screen corner at height "+size[1]);
     check(circle.pixel(betweenX,betweenY)==0xff000000&&squircle.pixel(betweenX,betweenY)==square.pixel(betweenX,betweenY),
       vehicle+" switches the cached shape and the squircle cuts less at height "+size[1]);
     check(squircle.pixel(safeX,safeY)==square.pixel(safeX,safeY),
       vehicle+" preserves the inside of every rounded corner at height "+size[1]);
     check(squircle.pixel(startX+dx*radius,startY)==square.pixel(startX+dx*radius,startY)
       &&squircle.pixel(startX,startY+dy*radius)==square.pixel(startX,startY+dy*radius),
       vehicle+" preserves the straight screen edges at height "+size[1]);
    }
   }
  }
 }
 static float clockSize(DashboardView view){
  for(android.graphics.Canvas.TextDraw t:draw(view).commands)if(t.text.matches("[0-9]{2}:[0-9]{2}"))return t.size;
  throw new AssertionError("navigation clock is drawn");
 }
 static void dockCheck()throws Exception{
  for(String vehicle:new String[]{"js","pd"}){
   fixture("received",vehicle);Activity a=new Activity();LanguageSettings.save(a,"ko");
   a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2_flat").apply();
   DashboardView view=create(a);String dock=UiText.t("왼쪽 메뉴 독");
   check(!activeTheme(view).shell().dock(),"Flat defaults to individual buttons");
   float normal=clockSize(view);
   uiSettings(view);tapEditorLabel(view,dock);tapEditorLabel(view,UiText.t("사용함"));settingsTap(view,70,88);
   check(!activeTheme(view).shell().dock(),"cancel leaves dock unchanged");
   tapEditorLabel(view,dock);tapEditorLabel(view,UiText.t("사용함"));settingsTap(view,700,88);
   check(activeTheme(view).shell().dock()&&Math.abs(clockSize(view)-normal*.9f)<.01f,"dock reduces the rendered clock by 10 percent");
   com.veloster.dashboard.theme.ThemeSpec theme=activeTheme(view);
   check(theme.blurFor(com.veloster.dashboard.theme.ThemeSpec.SurfaceRole.NAVIGATION_DOCK)==0,"Flat dock does not inherit Optics blur");
   com.veloster.dashboard.theme.ThemeSurface surface=new com.veloster.dashboard.theme.ThemeSurface();
   android.graphics.Paint paint=new android.graphics.Paint(3);
   android.graphics.Canvas buttons=new android.graphics.Canvas(50,50);
   surface.navigationButton(buttons,theme,null,paint,null,1,1,48,48,false,theme.accent);
   for(int y=0;y<50;y++)for(int x=0;x<50;x++)check(buttons.pixel(x,y)==0,"inactive dock button has no background or border");
   surface.navigationButton(buttons,theme,null,paint,null,1,1,48,48,true,theme.accent);
   check(buttons.pixel(25,25)!=0&&buttons.pixel(1,25)==0,"selected dock button retains only its inset highlight");
   pressMenu(view,1);
   check(((com.veloster.dashboard.view.DashboardPage.State)field(view,"pageState")).is(1),"dock keeps navigation hit targets");
   view=create(a);check(activeTheme(view).shell().dock(),"dock preference survives view recreation");
   uiSettings(view);tapEditorLabel(view,UiText.t("메뉴 위치"));tapEditorLabel(view,UiText.t("상단"));settingsTap(view,700,88);
   check(!activeTheme(view).shell().dock()&&!java.util.Arrays.asList((String[])field(editor(view),"labels")).contains(dock),"top menu hides the dock setting");
   tapEditorLabel(view,UiText.t("메뉴 위치"));tapEditorLabel(view,UiText.t("왼쪽"));settingsTap(view,700,88);
   check(activeTheme(view).shell().dock(),"returning left restores the dock override");
   tapEditorLabel(view,dock);tapEditorLabel(view,UiText.t("사용 안 함"));settingsTap(view,700,88);
   tapEditorLabel(view,UiText.t("테마"));tapEditorLabel(view,"Liquid Glass (Optics)");settingsTap(view,700,88);
   check(!activeTheme(view).shell().dock(),"explicit off wins over the Optics default after a theme change");
   tapEditorLabel(view,dock);tapEditorLabel(view,UiText.t("테마 기본값"));settingsTap(view,700,88);
   check(activeTheme(view).shell().dock(),"theme default restores Optics dock");
  }
 }
 static void settingsScrollCheck()throws Exception{
  fixture("received","js");Activity a=new Activity();LanguageSettings.save(a,"ko");
  a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2_flat").apply();
  DashboardView view=create(a);view.setSize(800,425);uiSettings(view);
  String[] labels=(String[])field(editor(view),"labels");
  check(labels[2].equals("메뉴 위치")&&labels[3].equals("왼쪽 메뉴 독"),"dock is directly below menu position");
  Object layout=settingsLayout(view);
  float first=(Float)field(layout,"firstRowY"),max=(Float)field(layout,"maxScroll");
  check(max>4,"UI settings overflow in the compact view");
  com.veloster.dashboard.view.DashboardPage.Bounds list=(com.veloster.dashboard.view.DashboardPage.Bounds)field(layout,"list");
  com.veloster.dashboard.view.DashboardPage.Regions r=viewRegions(view);
  float distance=Math.min(max/2,8);
  check(view.onGenericMotionEvent(new android.view.MotionEvent(android.view.MotionEvent.ACTION_SCROLL,
   r.left+list.atX(.5f)*r.scale,r.top+list.atY(.5f)*r.scale,-distance/44)),"wheel is handled inside the settings list");
  layout=settingsLayout(view);
  check(Math.abs((Float)field(layout,"firstRowY")-first+distance)<.01,"wheel moves rows continuously");
  dragSettingsList(view,.8f,.2f);
  check(settingsScreen(view)==9&&(Float)field(settingsLayout(view),"scrollY")>distance,"drag scrolls without opening a row");
  android.graphics.Canvas frame=draw(view);
  check(!frame.texts.contains("‹ 이전")&&!frame.texts.contains("다음 ›"),"settings list has no pager buttons");
  tapEditorLabel(view,"미디어 알림");check(settingsScreen(view)==13,"last settings row can be opened after scrolling");
  settingsTap(view,70,88);tapEditorLabel(view,"배경화면");
  int last=((String[])field(editor(view),"labels")).length-1;
  check(last>=6&&(Float)field(settingsLayout(view),"maxScroll")>0,"gallery has an additional row below the viewport");
  dragSettingsList(view,.8f,.2f);
  check(settingsScreen(view)==14&&a.getSharedPreferences("dashboard_theme",0).getString("wallpaper_id","").isEmpty(),
        "dragging gallery does not select a wallpaper");
  tapEditorIndex(view,last);
  check(a.getSharedPreferences("dashboard_theme",0).getString("wallpaper_id","").equals("shell_test:color"),
        "a wallpaper in the scrolled grid can be applied");
 }
 static void shellCheck()throws Exception{
  fixture("received","js");Activity a=new Activity();LanguageSettings.save(a,"ko");
  a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","shell_test").apply();
  a.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("f1_order",1).apply();
  DashboardView view=create(a);
  com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  check(theme.shell().left(),"v3 shell must load from asset files");
  check(draw(view).texts.contains(UiText.t("설정")),"left menu is drawn by real renderer");
  menuNavigation(view,4);check(draw(view).texts.contains("PERFORMANCE"),"left performance navigation");
  rootSettings(view);String[] root=(String[])field(editor(view),"labels");
  check(root.length==4&&root[0].equals(UiText.t("UI 설정"))&&root[1].equals(UiText.t("F1 설정"))&&root[2].equals(UiText.t("언어"))&&root[3].equals(UiText.t("진단")),"JS settings root exposes UI, F1, language and diagnostics");
  settingsTap(view,150,258);check((Integer)field(editor(view),"screen")==0,"language opens from settings root");
  settingsTap(view,70,88);settingsTap(view,150,158);check((Integer)field(editor(view),"screen")==9,"UI group opens from settings root");
  settingsTap(view,150,158);check((Integer)field(editor(view),"screen")==1,"transformed theme row opens editor");
  settingsTap(view,70,88);settingsTap(view,70,88);
  menuNavigation(view,1);check(((com.veloster.dashboard.view.DashboardPage.State)field(view,"pageState")).is(1),"left F1 navigation");
  boolean movedSpeed=false;for(android.graphics.Canvas.TextDraw command:draw(view).commands)if(command.text.equals("SPEED")&&command.deviceX>500&&Math.abs(command.size-16.8)<.01&&!command.type.bold)movedSpeed=true;
  check(movedSpeed,"configured speed component bounds and numeric font role are rendered");
  android.graphics.Canvas f1=draw(view);
  for(android.graphics.Canvas.TextDraw command:f1.commands)check(Math.abs(command.scaleX-command.scaleY)<0.001,"F1 glyphs must keep aspect ratio: "+command.text);
  check(f1.commands.stream().filter(t->t.text.equals("SPEED")).allMatch(t->Math.abs(t.scaleX-1)<0.001),"F1 reflows at native font scale");
  com.veloster.dashboard.view.DashboardPage.Content area=new com.veloster.dashboard.view.DashboardPage.Content(theme,"f1");
  com.veloster.dashboard.view.DashboardPage.Bounds speed=area.f1(theme,"speed");
  com.veloster.dashboard.view.DashboardPage.Content larger=new com.veloster.dashboard.view.DashboardPage.Content(theme,"f1",900,530);
  com.veloster.dashboard.view.DashboardPage.Bounds largerSpeed=larger.f1(theme,"speed");
  check(speed.x>500&&speed.right()<=area.x+area.width&&largerSpeed.width>speed.width&&largerSpeed.height>speed.height,
        "configured F1 components grow on both axes as the available area grows");
  tap(view,350,20);tap(view,720,20);check(!a.isFinishing(),"old top back target must not close left shell");check(((com.veloster.dashboard.view.DashboardPage.State)field(view,"pageState")).is(1),"old top navigation coordinates do not navigate");
  menuNavigation(view,0);draw(view);com.veloster.dashboard.view.overview.OverviewLayout overview=(com.veloster.dashboard.view.overview.OverviewLayout)field(view,"overviewLayout");
  check(overview.count()==2&&overview.signalAt(0)==7&&overview.widthAt(0)>overview.widthAt(1),"configured overview order and span");
  tapLogical(view,overview.pagerX()+42,overview.pagerY());check(overview.page()==1&&overview.signalAt(0)==0,"transformed overview page control");
  menuNavigation(view,2);check(((com.veloster.dashboard.view.DashboardPage.State)field(view,"pageState")).is(2),"left gauge navigation");
  touch(view,0,162,126);waitForTimers();check(settingsScreen(view)==-1&&!(Boolean)field(view,"settingsVisible"),"gauge padding rejects long press");touch(view,1,162,126);
  pageHold(view,"gauges");check(settingsScreen(view)==20,"responsive gauge long press opens canvas editor");tapSettings(view,70,88);
  menuNavigation(view,3);check(((com.veloster.dashboard.view.DashboardPage.State)field(view,"pageState")).is(3),"left graph navigation");
  pageHold(view,"graphs");check(settingsScreen(view)==25,"responsive graph long press opens canvas editor");tapSettings(view,70,88);
  menuNavigation(view,1);
  com.veloster.dashboard.view.DashboardPage.Bounds toggle=area.footer(2);
  tapLogical(view,toggle.atX(.5f),toggle.atY(.5f));check((Boolean)field(view,"media"),"F1 footer artwork opens the common expanded panel");
  com.veloster.dashboard.view.DashboardPage.Regions split=viewRegions(view);
  com.veloster.dashboard.view.DashboardPage.Regions full=com.veloster.dashboard.view.DashboardPage.regions(split.displayWidth,split.displayHeight,(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme"));
  com.veloster.dashboard.theme.ThemeSpec active=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  float fixedNav=active.shell().left()?active.shell().width*split.scale:0;
  float expectedContent=fixedNav+(split.displayWidth-fixedNav)*5f/7f;
  check(split.mediaPanelVisible&&!split.mediaEnabled&&split.contentWidth<split.displayWidth&&split.scale==full.scale
        &&split.logicalContentWidth<full.logicalContentWidth&&Math.abs(split.contentWidth-expectedContent)<.01f&&draw(view).texts.contains(UiText.t("이전")),"compact player keeps the fixed menu then splits remaining width 5:2 and keeps the footer");
  com.veloster.dashboard.view.DashboardPage.Content expandedArea=new com.veloster.dashboard.view.DashboardPage.Content(active,"f1",split,true);
  for(int action=-1;action<=1;action++){
   com.veloster.dashboard.view.DashboardPage.Bounds b=expandedArea.footer(action);
   tapLogical(view,b.atX(.5f),b.atY(.5f));waitForTimers();
  }
  check(com.veloster.dashboard.vehicle.MediaControl.previous==1&&com.veloster.dashboard.vehicle.MediaControl.toggles==1&&com.veloster.dashboard.vehicle.MediaControl.next==1,"transformed media previous, play and next actions");
  tapLogical(view,300,160);check((Boolean)field(view,"media"),"page content remains interactive beside the shared panel");
  com.veloster.dashboard.view.DashboardPage.Bounds expandedToggle=expandedArea.footer(2);
  tapLogical(view,expandedToggle.atX(.5f),expandedToggle.atY(.5f));check(!(Boolean)field(view,"media"),"shared panel closes from the common footer artwork");
  menuNavigation(view,0);menuNavigation(view,0);
  check(((com.veloster.dashboard.view.DashboardPage.State)field(view,"pageState")).is(0)&&!draw(view).texts.contains(UiText.t("HUD · 내비")),"left overview button stays on overview");
  LanguageSettings.save(a,"en");rootSettings(view);android.graphics.Canvas englishRoot=draw(view);
  check(englishRoot.texts.contains(UiText.t("언어")),"English settings remains reachable");
  settingsTap(view,150,158);android.graphics.Canvas english=draw(view);
  android.graphics.Canvas.TextDraw label=null,value=null;for(android.graphics.Canvas.TextDraw command:english.commands){if(command.text.equals(UiText.t("테마"))&&command.y>60)label=command;if(command.text.startsWith("Very long theme"))value=command;}
  check(label!=null&&value!=null&&value.x>=label.x+label.width+15.5f,"long theme name fits beside settings label");
  view.setSize(400,240);menuNavigation(view,4);check(draw(view).texts.contains("PERFORMANCE"),"scaled viewport navigation");
  menuNavigation(view,8);check(a.isFinishing(),"left back action reaches real activity close");
  for(String vehicle:new String[]{"js","js_n","pd"}){
   fixture("received",vehicle);Activity other=new Activity();other.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","shell_test").apply();
   DashboardView screen=create(other);draw(screen);tap(screen,514,468);draw(screen);menuNavigation(screen,1);draw(screen);
   for(int[] size:new int[][]{{800,480},{800,425},{400,240},{1920,720}}){
    screen.setSize(size[0],size[1]);
    float scale=com.veloster.dashboard.view.DashboardPage.screenScale(size[0],size[1]);
    float left=com.veloster.dashboard.view.DashboardPage.screenLeft(size[0],size[1]);
    for(int[] nav:new int[][]{{77,4},{122,0},{167,1},{212,2},{257,3},{302,6}}){
      menuNavigation(screen,nav[1]);android.graphics.Canvas frame=draw(screen);
      for(android.graphics.Canvas.TextDraw command:frame.commands)check(Math.abs(command.scaleX-command.scaleY)<0.001,"all pages keep glyph aspect at "+size[0]+"x"+size[1]+": "+command.text);
    }
    menuNavigation(screen,4);check(draw(screen).texts.contains("PERFORMANCE"),"resized profile navigation "+vehicle);
   }
  }
  menuCheck();positionCheck();dockCheck();settingsScrollCheck();
  System.out.println("PASS production left shell navigation and transformed touch");
 }
 static void f1MenuCheck()throws Exception{
  fixture("received","js");Activity a=new Activity();LanguageSettings.save(a,"ko");
  a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id","f1_2_flat").apply();
  DashboardView view=create(a);com.veloster.dashboard.theme.ThemeSpec theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  check(theme.shell().width==80&&theme.shell().left(),"F1-2 uses the requested 80px left menu");
  com.veloster.dashboard.view.DashboardPage.Bounds bounds=com.veloster.dashboard.view.DashboardPage.navigationBounds(theme,
   (com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu"),0);
  check(bounds.x==16&&bounds.right()==64&&bounds.height==48,"F1-2 left menu uses 48 by 48 buttons with 16 units on both sides");
  android.graphics.Canvas menu=new android.graphics.Canvas(80,62);
  new com.veloster.dashboard.theme.ThemeSurface().navigationButton(menu,theme,null,new android.graphics.Paint(3),"Overview",bounds.x,5,bounds.width,bounds.height,true,theme.accent);
  com.veloster.dashboard.theme.ThemeCatalog catalog=(com.veloster.dashboard.theme.ThemeCatalog)field(view,"themeCatalog");
  android.graphics.Canvas circularMenu=new android.graphics.Canvas(80,62);
  new com.veloster.dashboard.theme.ThemeSurface().navigationButton(circularMenu,catalog.find("circle_glass"),null,new android.graphics.Paint(3),"Overview",bounds.x,5,bounds.width,bounds.height,true,theme.accent);
  check((circularMenu.pixel(17,6)>>>24)==0&&(menu.pixel(17,6)>>>24)!=0,
    "F1-2 squircle button has the flatter corner curve; circle mode keeps the circular cut");
  check((menu.pixel(23,8)>>>24)!=0,"F1-2 squircle menu fills inside its corner curve");
  check(menu.pixel(40,50)!=theme.accent,"F1-2 flat menu omits the separate selected underline");
  check((menu.pixel(40,55)>>>24)==0,"flat menu casts no shadow");

  a.getSharedPreferences("dashboard_theme",0).edit().putInt("menu_position",1).apply();
  view=create(a);
  theme=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  check(!theme.shell().left(),"F1-2 accepts the top menu override");
  com.veloster.dashboard.view.DashboardPage.Regions regions=com.veloster.dashboard.view.DashboardPage.regions(800,480,theme);
  check(regions.topMenu.height==54&&regions.content.y==54,"top menu leaves eight units above and below the 38-unit button row");
  tap(view,15,20);tap(view,17,7);
  check(!draw(view).texts.contains("PERFORMANCE"),"top menu padding does not select a page");
  tap(view,17,9);
  check(draw(view).texts.contains("PERFORMANCE"),"top menu accepts a touch just inside its padded edge");
 }
'''


class ThemeV3ShellTests(unittest.TestCase):
    def test_real_dashboard_view_and_assets(self):
        with tempfile.TemporaryDirectory(prefix='theme-v3-shell-') as folder:
            root = Path(folder)
            assets = root / 'assets'
            shutil.copytree(ROOT / 'apps/dashboard/src/0x1101L/assets', assets)
            catalog = assets / 'themes/catalog.json'
            data = json.loads(catalog.read_text())
            data['schemaVersion'] = 3
            corner_controls = {
                'square_glass': {'screenCornerRadius': 0},
                'circle_glass': {'screenCorner': 'circle'},
                'alias_glass': {'screenCorner': 'sqircle'},
            }
            data['themeIds'].extend(['shell_test', *corner_controls])
            catalog.write_text(json.dumps(data))
            draft = assets / 'themes/shell_test'
            (draft / 'layouts').mkdir(parents=True)
            for theme_id, shell in corner_controls.items():
                control = assets / 'themes' / theme_id
                control.mkdir()
                (control / 'manifest.json').write_text(json.dumps({'schemaVersion': 3, 'id': theme_id,
                    'displayName': theme_id.replace('_', ' ').title(), 'extends': 'f1_2', 'files': {'shell': 'shell.json'}}))
                (control / 'shell.json').write_text(json.dumps({'shell': shell}))
            (draft / 'manifest.json').write_text(json.dumps({'schemaVersion': 3, 'id': 'shell_test', 'displayName': 'Very long theme name for settings row and fitting behavior test', 'extends': 'original', 'files': {'fonts': 'fonts.json', 'shell': 'layouts/shell.json', 'f1': 'layouts/f1.json', 'overview': 'layouts/overview.json', 'gauges': 'layouts/gauges.json'}}))
            (draft / 'fonts.json').write_text(json.dumps({'typography': {'bodyScale': 120, 'numericScale': 120, 'displayScale': 125, 'gearSize': 200}, 'weights': {'numeric': 'regular'}}))
            (draft / 'layouts/shell.json').write_text(json.dumps({'shell': {'position': 'left', 'alignment': 'center', 'width': 160, 'padding': {'top': 10, 'right': 12, 'bottom': 20, 'left': 8}, 'gap': 5, 'buttonHeight': 40}}))
            (draft / 'layouts/gauges.json').write_text(json.dumps({'viewport': {'padding': {'top': 10, 'right': 40, 'bottom': 20, 'left': 20}, 'alignment': 'end'}}))
            bounds = {'shift': (18, 60, 764, 48), 'gear': (294, 120, 212, 247), 'speed': (524, 120, 258, 158), 'boost': (524, 278, 258, 89), 'rpm': (18, 120, 258, 158), 'temperatures': (18, 278, 258, 89), 'pedal': (18, 377, 376, 57), 'brake': (406, 377, 376, 57)}
            f1 = {key: dict(zip(('x', 'y', 'width', 'height'), value)) for key, value in bounds.items()}
            f1['speed'].update({'fontRole': 'numeric', 'colorRole': 'accent', 'padding': {'top': 4, 'right': 10, 'bottom': 6, 'left': 20}, 'alignment': 'center'})
            (draft / 'layouts/f1.json').write_text(json.dumps({'f1': f1}))
            cards = [dict(zip(('id', 'source', 'page', 'column', 'row', 'columnSpan', 'rowSpan'), card)) for card in [('speed', 7, 0, 0, 0, 2, 1), ('temp', 2, 0, 2, 0, 1, 1), ('rpm', 0, 1, 0, 0, 1, 1), ('fuel', 34, 1, 1, 0, 1, 1)]]
            (draft / 'layouts/overview.json').write_text(json.dumps({'overview': {'columns': 3, 'rows': 3, 'padding': {'top': 4, 'right': 8, 'bottom': 6, 'left': 10}, 'gapX': 8, 'gapY': 6, 'cards': cards}}))
            original = runtime.STUBS['DashboardDesktop.java']
            media_control = runtime.STUBS['com/veloster/dashboard/vehicle/MediaControl.java']
            try:
                runtime.STUBS['com/veloster/dashboard/vehicle/MediaControl.java'] = '''package com.veloster.dashboard.vehicle;
public class MediaControl {public static volatile int previous,next,toggles;
 public static boolean toggle(android.content.Context c,int mode,int state){toggles++;return true;}
 public static boolean step(android.content.Context c,boolean forward,int mode){if(forward)next++;else previous++;return true;}}
'''
                runtime.STUBS['DashboardDesktop.java'] = original.replace(' public static void main(String[] args)throws Exception{', CHECK + '''
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("--shell-check")){SwingUtilities.invokeAndWait(()->{try{shellCheck();f1MenuCheck();screenCornerCheck();}catch(Exception e){throw new RuntimeException(e);}});return;}
''', 1)
                java, classes = runtime.compile_runtime(folder)
            finally:
                runtime.STUBS['DashboardDesktop.java'] = original
                runtime.STUBS['com/veloster/dashboard/vehicle/MediaControl.java'] = media_control
            result = subprocess.run([java, '-Djava.awt.headless=true', '-Ddashboard.assets=' + str(assets), '-cp', str(classes), 'DashboardDesktop', '--shell-check'], capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('PASS production left shell', result.stdout)


if __name__ == '__main__':
    unittest.main()
