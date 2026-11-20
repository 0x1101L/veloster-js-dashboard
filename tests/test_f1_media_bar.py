"""F1 settings, geometry and policies through production DashboardView and Java Canvas."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import dashboard_runtime as runtime

CHECK = r'''
 static com.veloster.dashboard.theme.ThemeSpec currentTheme(DashboardView view)throws Exception{return (com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");}
 static void screenTap(DashboardView view,float x,float y){
  try{tapLogical(view,x,y);}catch(Exception e){throw new RuntimeException(e);}
 }
 static void navigation(DashboardView view,int page)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec t=currentTheme(view);
  com.veloster.dashboard.view.DashboardPage.Regions r=viewRegions(view);
  com.veloster.dashboard.view.DashboardPage.Menu menu=(com.veloster.dashboard.view.DashboardPage.Menu)field(view,"pageMenu");
  float x,y;
  com.veloster.dashboard.view.DashboardPage.Bounds b=com.veloster.dashboard.view.DashboardPage.navigationBounds(t,menu,page,r.logicalDisplayWidth);
  x=b.x+b.width/2;y=b.y+b.height/2;screenTap(view,x,y);
 }
 static void contentTap(DashboardView view,String page,float x,float y)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec t=currentTheme(view);
  com.veloster.dashboard.view.DashboardPage.Regions r=com.veloster.dashboard.view.DashboardPage.regions(view.getWidth(),view.getHeight(),t);
  if(page.equals("settings")){tapSettings(view,x,y);return;}
  screenTap(view,x,y);
 }
 static void footerTap(DashboardView view,int action)throws Exception{
  com.veloster.dashboard.view.DashboardPage.Regions r=viewRegions(view);
  com.veloster.dashboard.view.DashboardPage.Content area=new com.veloster.dashboard.view.DashboardPage.Content(currentTheme(view),"f1",r,footer(view));
  com.veloster.dashboard.view.DashboardPage.Bounds b=area.footer(action);screenTap(view,b.atX(.5f),b.atY(.5f));
 }
 static void rpmTouch(DashboardView view,int action,float x,float y)throws Exception{
  com.veloster.dashboard.view.DashboardPage.Regions r=com.veloster.dashboard.view.DashboardPage.regions(view.getWidth(),view.getHeight(),currentTheme(view));
  view.onTouchEvent(new android.view.MotionEvent(action,r.left+x*r.scale,r.top+y*r.scale));
 }
 static com.veloster.dashboard.view.DashboardPage.Bounds settingsBounds(DashboardView view,String part)throws Exception{
  return (com.veloster.dashboard.view.DashboardPage.Bounds)field(settingsLayout(view),part);
 }
 static void rpmDrag(DashboardView view,float start,float end)throws Exception{
  com.veloster.dashboard.view.DashboardPage.Bounds list=settingsBounds(view,"viewport");
  float x=list.atX(.5f),y=list.atY(start),to=list.atY(end);
  rpmTouch(view,android.view.MotionEvent.ACTION_DOWN,x,y);
  rpmTouch(view,android.view.MotionEvent.ACTION_MOVE,x,(y+to)*.5f);
  rpmTouch(view,android.view.MotionEvent.ACTION_UP,x,to);
 }
 static android.graphics.Canvas.TextDraw rpmRow(DashboardView view,String label)throws Exception{
  com.veloster.dashboard.view.DashboardPage.Bounds list=settingsBounds(view,"viewport");
  return draw(view).commands.stream().filter(t->t.text.equals(label)&&t.y>=list.y&&t.y<list.bottom()).findFirst().orElse(null);
 }
 static void policy(DashboardView view,int value,boolean apply)throws Exception{
  navigation(view,6);tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("하단 미디어 바"));
  check(settingsScreen(view)==3,"common media bar setting opens from UI group");
  tapEditorIndex(view,value);
  if(apply)contentTap(view,"settings",700,88);else contentTap(view,"settings",70,88);
  navigation(view,1);
 }
 static void drainMedia(DashboardView view)throws Exception{
  long limit=System.currentTimeMillis()+2000;
  while(((java.util.concurrent.atomic.AtomicBoolean)field(view,"mediaPending")).get()&&System.currentTimeMillis()<limit){
   java.awt.SecondaryLoop loop=java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
   javax.swing.Timer timer=new javax.swing.Timer(5,e->loop.exit());timer.setRepeats(false);timer.start();loop.enter();
  }
  check(!((java.util.concurrent.atomic.AtomicBoolean)field(view,"mediaPending")).get(),"media boundary completed");
 }
 static boolean footer(DashboardView view){
  try{com.veloster.dashboard.vehicle.MediaState state=(com.veloster.dashboard.vehicle.MediaState)field(view,"mediaState");
   return com.veloster.dashboard.view.MediaBar.footerVisible(viewRegions(view),(Integer)field(view,"mediaBarPolicy"),state.snapshot(android.os.SystemClock.elapsedRealtime()));
  }catch(Exception e){throw new RuntimeException(e);}
 }
 static void screenshot(DashboardView view,String name)throws Exception{
  java.nio.file.Path folder=java.nio.file.Paths.get(System.getProperty("f1.output"));java.nio.file.Files.createDirectories(folder);
  javax.imageio.ImageIO.write(draw(view).image(),"png",folder.resolve(name+".png").toFile());
 }
 static float[] regionPoint(DashboardView view,com.veloster.dashboard.view.DashboardPage.Regions r,int action)throws Exception{
  for(float y=r.media.y;y<r.media.bottom();y+=1)
   for(float x=r.media.x;x<r.media.right();x+=1)
    if(com.veloster.dashboard.view.MediaBar.regionHit(currentTheme(view),r.media,x,y,r.mediaEnabled)==action)return new float[]{x+1,y+1};
  throw new AssertionError("side media action has no hit area: "+action);
 }
 static void playerGeometryCheck()throws Exception{
  Class<?> layout=Class.forName("com.veloster.dashboard.view.MediaBar$RegionLayout");
  java.lang.reflect.Constructor<?> constructor=layout.getDeclaredConstructors()[0];constructor.setAccessible(true);
  Object wide=constructor.newInstance(null,new com.veloster.dashboard.view.DashboardPage.Bounds(0,0,548,642),true,true);
  Object normal=constructor.newInstance(null,new com.veloster.dashboard.view.DashboardPage.Bounds(0,0,228,428),true,false);
  Object wideValues=field(wide,"values"),normalValues=field(normal,"values");
  for(String value:new String[]{"padding","cardRadius","coverGap","coverMax","coverInset","sourceHeight","titleHeight","titleSize",
       "detailsGap","detailsHeight","artistSize","sourceSize","sourceGap","progressGap","progressHeight","timeGap","timeHeight",
       "timeSize","controlsGap","playWidth","playHeight","sideWidth","sideHeight","buttonGap","buttonRadius",
       "iconHalfWidth","iconHalfHeight","iconTip","emptyIconSize"}){
   float large=((Number)field(wideValues,value)).floatValue(),base=((Number)field(normalValues,value)).floatValue();
   check(Math.abs(large-base*1.5f)<.02f,"wide "+value+" is explicitly 1.5 times the normal baseline");
  }
  com.veloster.dashboard.view.DashboardPage.Bounds cover=(com.veloster.dashboard.view.DashboardPage.Bounds)field(normal,"cover");
  for(String part:new String[]{"source","title","details","times"}){
   com.veloster.dashboard.view.DashboardPage.Bounds text=(com.veloster.dashboard.view.DashboardPage.Bounds)field(normal,part);
   check(Math.abs(cover.x-text.x)<.01&&Math.abs(cover.width-text.width)<.01,"normal player "+part+" aligns to the album-art column");
  }
  for(String part:new String[]{"previous","play","next"}){
   com.veloster.dashboard.view.DashboardPage.Bounds a=(com.veloster.dashboard.view.DashboardPage.Bounds)field(wide,part);
   com.veloster.dashboard.view.DashboardPage.Bounds b=(com.veloster.dashboard.view.DashboardPage.Bounds)field(normal,part);
   check(Math.abs(a.width-b.width*1.5f)<.02&&Math.abs(a.height-b.height*1.5f)<.02,"wide "+part+" uses explicit 1.5x button values");
  }
  com.veloster.dashboard.view.DashboardPage.Bounds card=(com.veloster.dashboard.view.DashboardPage.Bounds)field(normal,"card");
  com.veloster.dashboard.view.DashboardPage.Bounds previous=(com.veloster.dashboard.view.DashboardPage.Bounds)field(normal,"previous");
  com.veloster.dashboard.view.DashboardPage.Bounds next=(com.veloster.dashboard.view.DashboardPage.Bounds)field(normal,"next");
  check(previous.x>=card.x&&next.right()<=card.right(),"normal transport row fits inside the media card");
 }
 static void alignmentCheck(DashboardView view)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec theme=currentTheme(view);
  com.veloster.dashboard.view.DashboardPage.Content area=new com.veloster.dashboard.view.DashboardPage.Content(theme,"f1");
  java.lang.reflect.Field style=DashboardView.class.getDeclaredField("mediaButtonStyle");style.setAccessible(true);int originalStyle=style.getInt(view);style.setInt(view,1);
  int found=0;
  for(android.graphics.Canvas.TextDraw text:draw(view).commands){
   int action=text.text.equals("♪")?2:text.text.equals(UiText.t("이전"))||text.text.equals("‹")?-1:text.text.equals(UiText.t("다음"))||text.text.equals("›")?1:text.text.equals(UiText.t("재생"))||text.text.equals(UiText.t("일시정지"))||text.text.startsWith("▶")||text.text.equals("Ⅱ")?0:-10;
   if(action==-10)continue;
   com.veloster.dashboard.view.DashboardPage.Bounds b=area.footer(action);float cx=b.atX(.5f),cy=b.atY(.5f);
   android.graphics.Paint p=new android.graphics.Paint(1);p.setTypeface(text.type);p.setTextSize(text.size);android.graphics.Paint.FontMetrics metrics=p.getFontMetrics();
   check(Math.abs(text.x+text.width/2-cx)<0.01,"media label horizontally centered: "+text.text);
   check(Math.abs(text.y+(metrics.ascent+metrics.descent)/2-cy)<0.01,"media label vertically centered: "+text.text);found++;
  }
  style.setInt(view,originalStyle);
  check(found==4,"all footer controls checked for alignment");
 }
 static void widePlayerCheck()throws Exception{
  for(String vehicle:new String[]{"js","js_n","pd"}){
   fixture("received",vehicle);Activity a=new Activity();LanguageSettings.save(a,vehicle.equals("js")?"ko":"en");
   String themeId=vehicle.equals("js")?"original":vehicle.equals("js_n")?"f1_2":"aurora_focus";
   a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id",themeId).apply();
   PreviewProfile profile=PreviewProfile.ALL[3];DashboardView view=create(a,profile);
   com.veloster.dashboard.view.DashboardPage.Regions r=com.veloster.dashboard.view.DashboardPage.regions(view.getWidth(),view.getHeight(),currentTheme(view));
   check(r.mediaEnabled&&currentTheme(view).id.equals(themeId),"wide check uses actual bundled theme");
   android.graphics.Canvas corners=new android.graphics.Canvas(160,160);corners.drawColor(0xffff00ff);
   com.veloster.dashboard.view.MediaBar.drawArtwork(corners,new android.graphics.Bitmap(100,100),30,30,100,currentTheme(view).material.radius);
   if(currentTheme(view).material.radius==0)check(corners.pixel(30,30)!=0xffff00ff,"zero theme radius keeps square artwork");
   else check(corners.pixel(30,30)==0xffff00ff&&corners.pixel(80,80)!=0xffff00ff,"theme radius clips artwork corners");
   String prefix="wide-"+themeId;
   navigation(view,1);screenshot(view,prefix+"-empty");
   mediaFixture(view,"received",profile);
   android.graphics.Canvas frame=draw(view);
   android.graphics.Canvas.TextDraw title=frame.commands.stream().filter(t->t.text.equals("Midnight Drive")).findFirst().orElseThrow(AssertionError::new);
   android.graphics.Canvas.TextDraw artist=frame.commands.stream().filter(t->t.text.equals("Neon Avenue")).findFirst().orElseThrow(AssertionError::new);
   android.graphics.Canvas.TextDraw source=frame.commands.stream().filter(t->t.text.equals("CarPlay")&&t.x>r.media.x).findFirst().orElseThrow(AssertionError::new);
   check(title.x>r.media.x&&source.y<title.y&&title.y<artist.y&&title.x==artist.x&&source.x==title.x,
         "source appears above title, with artist below in the shared text column");
   check(frame.texts.contains("1:19")&&frame.texts.contains("3:42")&&!frame.texts.contains("♪"),"real sample time and artwork replace the empty display");
   screenshot(view,prefix+"-playing");
   for(int page:new int[]{0,1,2,3,4,6}){
    navigation(view,page);
    check(draw(view).commands.stream().anyMatch(t->t.text.equals("Midnight Drive")&&t.x>r.media.x),
          "wide media is common across pages and vehicle profiles");
   }
   navigation(view,1);

   float[] playPoint=regionPoint(view,r,0);
   for(int command=-1;command<=1;command++){
    int count=com.veloster.dashboard.vehicle.MediaControl.calls.get();
    float[] point=regionPoint(view,r,command);tap(view,point[0],point[1]);drainMedia(view);
    check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==count+1&&
          com.veloster.dashboard.vehicle.MediaControl.command==command&&com.veloster.dashboard.vehicle.MediaControl.mode==0x73,
          "wide rendered transport uses the existing command boundary");
   }
   int count=com.veloster.dashboard.vehicle.MediaControl.calls.get();
   float[] nextPoint=regionPoint(view,r,1);
   tap(view,r.media.x+1,r.media.y+1);touch(view,0,playPoint[0],playPoint[1]);touch(view,1,nextPoint[0],nextPoint[1]);drainMedia(view);
   check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==count,"wide gaps, metadata and cross-button release do not send commands");

   touch(view,0,1642,250);touch(view,2,1700,250);touch(view,1,1642,250);
   tap(view,1642,250);drainMedia(view);
   check(viewRegions(view).mediaEnabled&&!footer(view)
         &&com.veloster.dashboard.vehicle.MediaControl.calls.get()==count,
         "the wide cover stays display only and the player remains open");
   navigation(view,0);
   check(draw(view).commands.stream().noneMatch(t->t.text.equals("Midnight Drive")&&t.x<r.media.x),
         "the wide overview does not repeat now-playing in the content panel");
   com.veloster.dashboard.vehicle.MediaState media=(com.veloster.dashboard.vehicle.MediaState)field(view,"mediaState");
   media.update(3,true,"아주 긴 곡 제목이 들어와도 플레이어 너비에 맞춰 끝을 잘라서 표시해야 합니다 — A title with several more words",
       true,"길이가 긴 아티스트 이름도 재생 소스와 겹치지 않아야 합니다",false,0,false,0,false,1000);
   media.playback(3,0);
   frame=draw(view);
  check(frame.texts.contains("아주 긴 곡 제목이 들어와도 플레이어 너비에 맞춰 끝을 잘라서 표시해야 합니다 — A title with several more words")&&
        frame.texts.contains("길이가 긴 아티스트 이름도 재생 소스와 겹치지 않아야 합니다")&&
         !frame.texts.contains("1:19")&&!frame.texts.contains("3:42"),
         "long metadata is retained for marquee scrolling and missing progress has no elapsed or duration labels");
   screenshot(view,prefix+"-long");
   // The same view can open compact media on either renderer, including with a left menu.
   view.setSize(400,240);navigation(view,2);
   media.update(3,true,"Short title",true,"Fixture artist",false,0,false,0,false,1000);
   com.veloster.dashboard.view.DashboardPage.Content compact=new com.veloster.dashboard.view.DashboardPage.Content(currentTheme(view),"gauges",viewRegions(view),true);
   com.veloster.dashboard.view.DashboardPage.Bounds art=compact.footer(2);
   screenTap(view,art.atX(.5f),art.atY(.5f));
   check((Boolean)field(view,"media")&&viewRegions(view).mediaPanelVisible,"gauges open shared media on "+vehicle);
   view.setSize(1920,720);
   check(java.util.Collections.frequency(draw(view).texts,"Short title")==1&&!(Boolean)field(view,"media"),"wide resize replaces the compact panel with the right player");
  }
 }
 static void chooseNotices(DashboardView view,boolean enabled,boolean apply)throws Exception{
  navigation(view,6);tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("미디어 알림"));
  tapEditorIndex(view,enabled?0:1);tapSettings(view,apply?700:70,88);
  navigation(view,1);
 }
 static void chooseMediaButtons(DashboardView view,boolean enabled)throws Exception{
  navigation(view,6);tapEditorLabel(view,UiText.t("UI 설정"));
  check(java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels")).contains(UiText.t("미디어 버튼")),"UI settings expose media controls");
  tapEditorLabel(view,UiText.t("미디어 버튼"));tapEditorIndex(view,enabled?0:1);tapSettings(view,700,88);navigation(view,1);
 }
 static void chooseMediaButtonStyle(DashboardView view,int style)throws Exception{
  navigation(view,6);tapEditorLabel(view,UiText.t("UI 설정"));
  check(java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels")).contains(UiText.t("미디어 버튼 표시 방식")),"UI settings expose media control style");
  tapEditorLabel(view,UiText.t("미디어 버튼 표시 방식"));tapEditorIndex(view,style);tapSettings(view,700,88);navigation(view,1);
 }
 static void selectPage(DashboardView view,int page,String name)throws Exception{
  if(page==5){navigation(view,6);tapEditorLabel(view,UiText.t("진단"));}
  else navigation(view,page);
  Class<?> klass=DashboardView.class;
  Method method=klass.getDeclaredMethod("viewportPage");method.setAccessible(true);
  check(name.equals(method.invoke(view)),"actual page selected: "+name);
 }
 static void commonMediaCheck()throws Exception{
  fixture("received","js");Activity a=new Activity();LanguageSettings.save(a,"ko");
  a.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("f1_media_bar_policy",com.veloster.dashboard.view.MediaBar.ALWAYS).putInt("media_button_style",1).apply();
  DashboardView view=create(a);
  String[] names={"overview","f1","gauges","graphs","performance","diagnostics","settings"};
  com.veloster.dashboard.vehicle.MediaState state=(com.veloster.dashboard.vehicle.MediaState)field(view,"mediaState");
  state.mode(0x73);state.update(3,true,"Common media track",true,"Fixture artist",false,0,false,0,false,1000);
  state.playback(3,1);
  for(int page=0;page<names.length;page++){
   selectPage(view,page,names[page]);
   android.graphics.Canvas frame=draw(view);
   int trackLabels=java.util.Collections.frequency(frame.texts,"Common media track");
   check(footer(view)&&trackLabels==1,"one footer and no duplicate notice on "+names[page]+" (footer="+footer(view)+", trackLabels="+trackLabels+")");
   com.veloster.dashboard.view.DashboardPage.Content area=new com.veloster.dashboard.view.DashboardPage.Content(currentTheme(view),names[page],800,480,true);
   com.veloster.dashboard.view.DashboardPage.Bounds play=area.footer(0);
   if(page!=1)check(area.y+area.height<play.y,"body ends before footer on "+names[page]);
   int count=com.veloster.dashboard.vehicle.MediaControl.calls.get();
   screenTap(view,play.atX(.5f),play.atY(.5f));drainMedia(view);
   check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==count+1,"transport reaches control on "+names[page]);
   screenTap(view,play.atX(.5f)-200,play.atY(.5f));drainMedia(view);
   check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==count+1,"metadata is not a button on "+names[page]);
   com.veloster.dashboard.view.DashboardPage.Bounds art=area.footer(2);
   screenTap(view,art.atX(.5f),art.atY(.5f));
   check((Boolean)field(view,"media")&&viewRegions(view).mediaPanelVisible,"footer opens the player on "+names[page]);
   com.veloster.dashboard.view.DashboardPage.Regions split=viewRegions(view);
   com.veloster.dashboard.view.DashboardPage.Regions full=com.veloster.dashboard.view.DashboardPage.regions(split.displayWidth,split.displayHeight,currentTheme(view));
   float fixedNav=currentTheme(view).shell().left()?currentTheme(view).shell().width*split.scale:0;
   float framePadding=currentTheme(view).layoutSpacing().horizontalPadding*split.scale;
   float expectedContent=framePadding+fixedNav+(split.displayWidth-2*framePadding-fixedNav-(split.media.x-split.contentWidth))*5f/7f;
   check(split.mediaPanelVisible&&!split.mediaEnabled&&split.contentWidth<split.displayWidth&&split.scale==full.scale
         &&split.logicalContentWidth<full.logicalContentWidth&&Math.abs(split.contentWidth-expectedContent)<.01f&&footer(view),
         "compact player keeps fixed navigation then splits remaining width 5:2 on "+names[page]);
   int playerCalls=com.veloster.dashboard.vehicle.MediaControl.calls.get();
   float playerX=-1,playerY=-1;
   for(float py=split.media.y;py<split.media.bottom()&&playerX<0;py+=2*split.scale)
    for(float px=split.media.x;px<split.media.right();px+=2*split.scale)
     if(com.veloster.dashboard.view.MediaBar.regionHit(currentTheme(view),split.media,px,py,split.mediaEnabled)==com.veloster.dashboard.view.MediaBar.PLAY){playerX=px;playerY=py;break;}
   check(playerX>=0,"shared player exposes its standard play hit region on "+names[page]);
   tapLogical(view,playerX/split.scale,playerY/split.scale);drainMedia(view);
   check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==playerCalls+1
         &&com.veloster.dashboard.vehicle.MediaControl.command==0&&(Boolean)field(view,"media"),
         "shared player transport remains tappable on "+names[page]);
   if(page==3||page==6)screenshot(view,"compact-"+names[page]+"-media");
   com.veloster.dashboard.view.DashboardPage.Content expandedArea=new com.veloster.dashboard.view.DashboardPage.Content(currentTheme(view),names[page],split,true);
   com.veloster.dashboard.view.DashboardPage.Bounds expandedPlay=expandedArea.footer(0);
   screenTap(view,expandedPlay.atX(.5f),expandedPlay.atY(.5f));drainMedia(view);
   check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==count+3,"footer transport still works while music is open on "+names[page]);
   if(page==6){
    tapEditorLabel(view,UiText.t("UI 설정"));
    check(settingsScreen(view)==9,"settings remains interactive beside the shared player");
    screenTap(view,expandedArea.footer(2).atX(.5f),expandedArea.footer(2).atY(.5f));
   }else screenTap(view,expandedArea.footer(2).atX(.5f),expandedArea.footer(2).atY(.5f));
   check(java.util.Collections.frequency(draw(view).texts,"Common media track")==1,"player closes on "+names[page]);
   Method pageMethod=DashboardView.class.getDeclaredMethod("viewportPage");pageMethod.setAccessible(true);
   check(names[page].equals(pageMethod.invoke(view)),"opening and closing keeps the current page");
  }
  navigation(view,1);
  policy(view,2,true);state.playback(3,0);
  state.update(3,true,"Notice candidate",false,"",false,0,false,0,false,1000);
  selectPage(view,4,"performance");
  check(!footer(view)&&!draw(view).texts.contains("Notice candidate"),"temporarily hidden bar does not enable notices");
  policy(view,1,true);
  for(int page=0;page<names.length;page++){
   selectPage(view,page,names[page]);
   check(!footer(view)&&draw(view).texts.contains("Notice candidate"),"explicitly hidden bar permits notice on "+names[page]);
  }
  navigation(view,1);
  // Let each notice clear before using the bottom settings rows, then announce another track update.
  state.dismissNotice();chooseNotices(view,false,false);
  state.update(3,true,"Notice candidate",true,"Cancel fixture",false,0,false,0,false,1000);
  check(draw(view).texts.contains("Notice candidate"),"cancel keeps notices on");
  state.dismissNotice();chooseNotices(view,false,true);
  state.update(3,true,"Notice candidate",true,"Apply fixture",false,0,false,0,false,1000);
  check(!draw(view).texts.contains("Notice candidate"),"off suppresses notice with bar explicitly hidden");
  DashboardView restored=create(a);
  check(!(Boolean)field(restored,"mediaNotices")&&(Integer)field(restored,"mediaBarPolicy")==1,"common preferences survive recreation");
  chooseNotices(view,true,true);state.dismissNotice();
  navigation(view,6);tapEditorLabel(view,UiText.t("UI 설정"));tapEditorLabel(view,UiText.t("미디어 알림"));
  view.setSize(1920,720);android.graphics.Canvas wide=draw(view);
  check(settingsScreen(view)==9,"wide resize exits inapplicable editor");
  check(!java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels")).contains(UiText.t("하단 미디어 바"))&&
        !java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels")).contains(UiText.t("미디어 알림")),"wide UI settings omit both media rows");
  check(java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels")).contains(UiText.t("미디어 버튼"))&&
        java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels")).contains(UiText.t("미디어 버튼 표시 방식")),"wide UI settings retain media control options");
  float mediaLeft=viewRegions(view).media.x;
  double noticeX=wide.commands.stream().filter(t->t.text.equals("Notice candidate")).mapToDouble(t->t.deviceX).min().orElse(-1);
  boolean wideFooter=com.veloster.dashboard.view.MediaBar.footerVisible(viewRegions(view),(Integer)field(view,"mediaBarPolicy"),state.snapshot(android.os.SystemClock.elapsedRealtime()));
  check(!wideFooter&&wide.commands.stream().filter(t->t.text.equals("Notice candidate")).allMatch(t->t.deviceX>=mediaLeft)
        &&wide.texts.contains("Notice candidate"),"wide resize keeps only right player (footer="+footer(view)+", mediaLeft="+mediaLeft+", noticeX="+noticeX+")");
  view.setSize(800,480);draw(view);
  java.util.List<String> compactLabels=java.util.Arrays.asList((String[])field(field(view,"settingsEditor"),"labels"));
  check(compactLabels.contains(UiText.t("하단 미디어 바"))&&compactLabels.contains(UiText.t("미디어 알림"))&&(Integer)field(view,"mediaBarPolicy")==1,
        "compact resize restores both settings and saved policy");
 }
 static void f1Check()throws Exception{
  playerGeometryCheck();widePlayerCheck();commonMediaCheck();
  int cases=0;
  for(String vehicle:new String[]{"js","js_n","pd"})for(String id:new String[]{"original","aurora_focus","f1_2","left_media_test"})for(String language:new String[]{"ko","en"}){
   fixture("received",vehicle);Activity a=new Activity();LanguageSettings.save(a,language);
   a.getSharedPreferences("dashboard_theme",0).edit().putString("theme_id",id).apply();
   a.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("shift_target",6300).apply();
   DashboardView view=create(a);navigation(view,6);
   Object settings=field(view,"settingsEditor");
   check((Integer)field(settings,"screen")==-1,"settings opens on root");
   String[] rootLabels=(String[])field(settings,"labels");int[] rootActions=(int[])field(settings,"actions");
   check(rootLabels.length==(vehicle.equals("js")?5:3)&&rootLabels[0].equals(UiText.t("UI 설정"))&&rootLabels[1].equals(UiText.t("페이지 설정"))&&rootLabels[vehicle.equals("js")?3:2].equals(UiText.t("언어")),"root contains UI, page settings, vehicle settings, language and diagnostics where supported");
   check(rootActions[0]==9&&rootActions[1]==31&&rootActions[vehicle.equals("js")?2:2]==(vehicle.equals("js")?40:0)&&rootActions[vehicle.equals("js")?3:2]==(vehicle.equals("js")?0:0),"root routes to UI, pages, vehicle settings and language selectors");
   openSettingsPage(view,31);settings=field(view,"settingsEditor");
   check(((String[])field(settings,"labels")).length==2&&((String[])field(settings,"labels"))[0].equals("F1")&&((String[])field(settings,"labels"))[1].equals(UiText.t("전체 조망")),"page settings contains the F1 and overview pages");
   openSettingsPage(view,10);settings=field(view,"settingsEditor");
   check(((String[])field(settings,"labels")).length==4,"F1 group contains only F1 settings");
   if(vehicle.equals("js")&&id.equals("original")&&language.equals("ko")){
    openSettingsPage(view,36);settings=field(view,"settingsEditor");
    check(((String[])field(settings,"labels")).length==3,"overview settings expose signal selection, order and reset");
    com.veloster.dashboard.view.overview.OverviewLayout overview=(com.veloster.dashboard.view.overview.OverviewLayout)field(view,"overviewLayout");
    int defaultOverviewCount=overview.count();openSettingsPage(view,37);settings=field(view,"settingsEditor");
    boolean[] overviewChecks=(boolean[])field(settings,"checked");check(overviewChecks.length>defaultOverviewCount&&overviewChecks[0],"overview picker checks supported defaults and offers vehicle signals");
    overviewChecks[0]=false;java.lang.reflect.Method applyOverview=DashboardView.class.getDeclaredMethod("applySettingsPage");applyOverview.setAccessible(true);applyOverview.invoke(view);
    check(overview.count()==defaultOverviewCount-1,"overview selection removes a card from the live grid");
    java.lang.reflect.Method preferenceKey=DashboardView.class.getDeclaredMethod("overviewPreferenceKey");preferenceKey.setAccessible(true);
    String storedOverview=a.getSharedPreferences("pit_vehicle_ui",0).getString((String)preferenceKey.invoke(view),"");
    check(storedOverview.split(",").length==overview.count(),"overview selection persists the vehicle-specific card list");
    int secondSignal=overview.signalAt(1);openSettingsPage(view,38);tapEditorIndex(view,1);
    check(overview.signalAt(0)==secondSignal,"overview order moves the chosen card one slot up");
    openSettingsPage(view,39);check(overview.count()==defaultOverviewCount&&overview.signalAt(0)==overview.defaultSignals()[0],"overview reset restores vehicle defaults");
   }
   navigation(view,1);
   check((Integer)field(view,"shiftTarget")==6300,"existing RPM preserved");
   check(currentTheme(view).id.equals(id),"test theme actually loaded: "+id);
   check((Integer)field(view,"mediaBarPolicy")==0,"backward compatible default");
   check((Integer)field(view,"mediaButtonStyle")==0,"media button style defaults to icons");
   check(footer(view)&&draw(view).texts.contains(UiText.t("미디어 데이터 없음")),"default always shows empty footer");
   if(vehicle.equals("js")&&id.equals("original")&&language.equals("ko")){
    chooseMediaButtons(view,false);check(!(Boolean)field(view,"mediaButtons")&&a.getSharedPreferences("pit_vehicle_ui",0).getInt("media_buttons",1)==0,"media controls setting persists hidden state");
    int calls=com.veloster.dashboard.vehicle.MediaControl.calls.get();footerTap(view,com.veloster.dashboard.view.MediaBar.PLAY);drainMedia(view);
    check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==calls,"hidden media control has no touch action");
    chooseMediaButtons(view,true);check((Boolean)field(view,"mediaButtons"),"media controls can be restored");
    chooseMediaButtonStyle(view,1);
    com.veloster.dashboard.vehicle.MediaState styleState=(com.veloster.dashboard.vehicle.MediaState)field(view,"mediaState");
    styleState.mode(0x73);styleState.update(3,true,"Style fixture",true,"Artist",false,0,false,0,false,1000);styleState.playback(3,1);
    check(draw(view).texts.contains("이전")&&draw(view).texts.contains("일시정지")&&draw(view).texts.contains("다음"),"text style labels footer controls");
    footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);
    check(draw(view).texts.contains("이전")&&draw(view).texts.contains("일시정지")&&draw(view).texts.contains("다음"),"text style labels expanded player controls");
    footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);
    check(a.getSharedPreferences("pit_vehicle_ui",0).getInt("media_button_style",-1)==1,"media button style persists");
    chooseMediaButtonStyle(view,0);check((Integer)field(view,"mediaButtonStyle")==0,"icon style can be restored");
   }
   check(!draw(view).texts.contains("+")&&!draw(view).texts.contains("−"),"RPM controls moved out of F1");
   alignmentCheck(view);
   if(id.equals("original")&&language.equals("en"))view.setSize(400,240);
   navigation(view,6);tapEditorLabel(view,UiText.t("페이지 설정"));tapEditorLabel(view,"F1");tapEditorLabel(view,UiText.t("F1 목표 RPM"));
   Object rpm=field(view,"settingsEditor");check((Integer)field(rpm,"screen")==2,"real RPM editor opens from F1 settings");
   String[] rpmOptions=(String[])field(rpm,"labels");
   int min=vehicle.equals("js_n")?6000:3000;
   check(rpmOptions[0].equals(min+" rpm")&&rpmOptions[rpmOptions.length-1].equals("6700 rpm"),"profile limits and step");
   android.graphics.Canvas.TextDraw saved=rpmRow(view,"6300 rpm");
   check(saved!=null,"RPM opens with saved value visible");
   check(!draw(view).texts.contains("‹ "+UiText.t("이전"))&&!draw(view).texts.contains(UiText.t("다음")+" ›"),"RPM has no pager");
   com.veloster.dashboard.view.DashboardPage.Bounds list=settingsBounds(view,"viewport"),apply=settingsBounds(view,"apply");
   float x=list.atX(.5f),y=list.atY(.5f);
   rpmTouch(view,android.view.MotionEvent.ACTION_DOWN,x,y);
   rpmTouch(view,android.view.MotionEvent.ACTION_MOVE,x,y+25);
   android.graphics.Canvas.TextDraw moved=rpmRow(view,"6300 rpm");
   if(!vehicle.equals("js_n"))check(moved!=null&&Math.abs(moved.y-saved.y-25)<.05,"RPM rows track the finger between row boundaries");
   rpmTouch(view,android.view.MotionEvent.ACTION_UP,apply.atX(.5f),apply.atY(.5f));
   check(settingsScreen(view)==2&&(Integer)field(rpm,"selected")==((6300-min)/100),"drag release on Apply neither selects nor saves");
   for(int drag=0;drag<8;drag++)rpmDrag(view,.85f,.15f);
   android.graphics.Canvas.TextDraw last=rpmRow(view,"6700 rpm");
   check(last!=null&&last.y<list.bottom(),"upward drags reach the maximum RPM");
   screenTap(view,list.atX(.25f),last.y);
   check((Integer)field(rpm,"selected")==rpmOptions.length-1,"tap selects the row at its scrolled position");
   contentTap(view,"settings",70,88);check((Integer)field(view,"shiftTarget")==6300,"RPM cancel preserves value");
   tapEditorLabel(view,UiText.t("F1 목표 RPM"));
   check(rpmRow(view,"6300 rpm")!=null,"reopening repositions at the saved RPM");
   for(int drag=0;drag<10;drag++)rpmDrag(view,.15f,.85f);
   android.graphics.Canvas.TextDraw first=rpmRow(view,min+" rpm"),next=rpmRow(view,(min+100)+" rpm");
   check(first!=null&&next!=null,"downward drags reach the minimum RPM");
   screenTap(view,list.atX(.25f),next.y);contentTap(view,"settings",700,88);
   check(a.getSharedPreferences("pit_vehicle_ui",0).getInt("shift_target",0)==min+100,"RPM applies to original preference");
   view.setSize(800,480);
   navigation(view,1);
   policy(view,1,false);check((Integer)field(view,"mediaBarPolicy")==0,"policy cancel preserves value");
   com.veloster.dashboard.vehicle.MediaState state=(com.veloster.dashboard.vehicle.MediaState)field(view,"mediaState");
   state.mode(0x73);state.update(3,true,"Fixture track",true,"Fixture artist",true,12000,true,180000,false,1000);
   check(state.snapshot(2000).notice,"fixture track notice is active");
   for(int selected=0;selected<3;selected++){
    policy(view,selected,true);
    for(int status:new int[]{1,0,-1}){
     state.playback(3,status);boolean expected=selected==0||(selected==2&&status==1);
     check(footer(view)==expected,"policy renders only explicit playing: "+vehicle+id+language+selected+status);
     int calls=com.veloster.dashboard.vehicle.MediaControl.calls.get();
     footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);check((Boolean)field(view,"media")==expected,"footer toggle obeys visibility on "+vehicle+id+language);
     if(expected)footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);
     footerTap(view,com.veloster.dashboard.view.MediaBar.PLAY);drainMedia(view);
     check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==calls+(expected?1:0),"hidden footer has no touch and visible toggle requests media");
     check(footer(view)==expected,"a track notice cannot expose the hidden bar");cases++;
    }
    check(a.getSharedPreferences("pit_vehicle_ui",0).getInt("f1_media_bar_policy",-1)==selected,"policy persisted");
   }
   // Reconstruct the production view; the host preference adapter retains the same context store.
   view=create(a);navigation(view,1);check((Integer)field(view,"mediaBarPolicy")==2&&(Integer)field(view,"shiftTarget")==min+100,"constructor restores preferences");
   state=(com.veloster.dashboard.vehicle.MediaState)field(view,"mediaState");state.mode(0x73);state.update(3,true,"Fixture track",true,"Fixture artist",false,0,false,0,false,1000);state.playback(3,1);
   for(int command:new int[]{-1,0,1}){
    footerTap(view,command);drainMedia(view);
    check(com.veloster.dashboard.vehicle.MediaControl.command==command&&com.veloster.dashboard.vehicle.MediaControl.mode==0x73,"actual media control command and source");
   }
   footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);check((Boolean)field(view,"media"),"F1 artwork opens the same shared media panel");
   float sideLeft=viewRegions(view).media.x;
   check(footer(view)&&draw(view).commands.stream().anyMatch(t->t.x>=sideLeft&&t.text.startsWith("Fixture")),"F1 has the common footer and expanded media panel");
   state.playback(3,0);int hiddenCalls=com.veloster.dashboard.vehicle.MediaControl.calls.get();footerTap(view,com.veloster.dashboard.view.MediaBar.PLAY);drainMedia(view);
   check(com.veloster.dashboard.vehicle.MediaControl.calls.get()==hiddenCalls&&!footer(view),"combined hidden bar has no controls");
   check((Boolean)field(view,"media"),"expanded panel remains open when the footer is hidden");
   screenTap(view,300,160);check((Boolean)field(view,"media"),"page content tap does not dismiss the side player");
   state.playback(3,1);footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);
   check(!(Boolean)field(view,"media"),"common footer artwork closes the side player");
   policy(view,1,true);state.update(3,true,"New track",false,"",false,0,false,0,false,1000);
   navigation(view,0);check(draw(view).texts.contains("New track"),"other page notices remain enabled");navigation(view,1);
   if(vehicle.equals("js")){
    String prefix=id+"-"+language;
    screenshot(view,prefix+"-hidden");state.dismissNotice();policy(view,0,true);screenshot(view,prefix+"-visible");
    footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);screenshot(view,prefix+"-media");footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);
    navigation(view,6);screenshot(view,prefix+"-settings");navigation(view,1);
   }
   state.dismissNotice();view.setSize(800,425);policy(view,2,true);state.playback(3,0);check(!footer(view),"425px paused footer hidden");state.playback(3,1);check(footer(view),"425px playing footer visible");
   view.setSize(400,240);footerTap(view,com.veloster.dashboard.view.MediaBar.TOGGLE);check((Boolean)field(view,"media"),"scaled F1 artwork opens the shared media panel");
  }
  fixture("received","js_n");Activity broken=new Activity();
  broken.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("shift_target",3050).putInt("f1_media_bar_policy",99).apply();
  DashboardView restored=create(broken);check((Integer)field(restored,"shiftTarget")==6000&&(Integer)field(restored,"mediaBarPolicy")==0,"profile clamp and invalid policy fallback");
  broken.getSharedPreferences("pit_vehicle_ui",0).edit().putString("shift_target","wrong type").putString("f1_media_bar_policy","wrong type").apply();
  restored=create(broken);check((Integer)field(restored,"shiftTarget")==6000&&(Integer)field(restored,"mediaBarPolicy")==0,"incompatible saved preference types recover");
  System.out.println("PASS F1 media policy/input cases="+cases+"; five selectable/fixture themes x 3 profiles x 2 languages; settings apply/cancel, restore, media commands and transformed touch");
 }
'''


class MediaBarTests(unittest.TestCase):
    def test_production_settings_render_and_input(self):
        with tempfile.TemporaryDirectory(prefix='f1-media-test-') as folder:
            root = Path(folder)
            assets = root / 'assets'
            shutil.copytree(ROOT / 'apps/dashboard/src/0x1101L/assets', assets)
            catalog = assets / 'themes/catalog.json'
            data = json.loads(catalog.read_text())
            data['themeIds'].append('left_media_test')
            catalog.write_text(json.dumps(data))
            draft = assets / 'themes/left_media_test'
            (draft / 'layouts').mkdir(parents=True)
            (draft / 'manifest.json').write_text(json.dumps({'schemaVersion': 3, 'id': 'left_media_test', 'displayName': 'Left media test', 'extends': 'original', 'files': {'shell': 'layouts/shell.json', 'settings': 'layouts/settings.json', 'f1': 'layouts/f1.json'}}))
            (draft / 'layouts/shell.json').write_text(json.dumps({'shell': {'navigationPosition': 'left', 'sideMenuBar': {'alignment': 'center', 'width': 160, 'gap': 5, 'itemGap': 5, 'buttonHeight': 40}}}))
            for page in ('settings', 'f1'):
                document = {'viewport': {'alignment': 'end'}}
                if page == 'f1':
                    document.update(json.loads((ROOT / 'theme/themes/f1-2/layouts/f1.json').read_text()))
                (draft / ('layouts/'+page+'.json')).write_text(json.dumps(document))
            originals = dict(runtime.STUBS)
            try:
                runtime.STUBS['DashboardDesktop.java'] = originals['DashboardDesktop.java'].replace(' public static void main(String[] args)throws Exception{', CHECK + '''
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("--f1-check")){SwingUtilities.invokeAndWait(()->{try{f1Check();}catch(Exception e){throw new RuntimeException(e);}});return;}
''', 1)
                runtime.STUBS['com/veloster/dashboard/vehicle/MediaControl.java'] = '''package com.veloster.dashboard.vehicle;
public class MediaControl {
 public static final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();public static volatile int command,mode;
 public static boolean toggle(android.content.Context c,int m,int state){mode=m;command=0;calls.incrementAndGet();return true;}
 public static boolean step(android.content.Context c,boolean next,int m){mode=m;command=next?1:-1;calls.incrementAndGet();return true;}
}'''
                java, classes = runtime.compile_runtime(folder)
            finally:
                runtime.STUBS.clear()
                runtime.STUBS.update(originals)
            output = Path(os.environ.get('F1_MEDIA_PREVIEW_OUT', ROOT / 'build/f1-media-validation-20261118'))
            result = subprocess.run([java, '-Djava.awt.headless=true', '-Ddashboard.assets=' + str(assets), '-Df1.output='+str(output), '-cp', str(classes), 'DashboardDesktop', '--f1-check'], capture_output=True, text=True, timeout=90)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('PASS F1 media policy/input cases=216', result.stdout)
            print(result.stdout.strip())


if __name__ == '__main__':
    unittest.main()
