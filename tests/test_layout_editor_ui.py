"""Navigation, live settings, and panel sizing in the Dashboard editor."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
import sys
sys.path.insert(0, str(ROOT / "tools"))
from dashboard_runtime import compile_runtime


HARNESS = r"""
import android.app.Activity;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import com.veloster.dashboard.theme.ThemeCatalog;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.view.DashboardView;
import com.veloster.i18n.LanguageSettings;
import com.veloster.i18n.UiText;

public class LayoutEditorResizeCheck {
 static void layout(Container c){c.doLayout();for(Component x:c.getComponents())if(x instanceof Container)layout((Container)x);}
 static Object read(LayoutEditorUi ui,String field)throws Exception{
  Field f=LayoutEditorUi.class.getDeclaredField(field);f.setAccessible(true);return f.get(ui);
 }
 static JButton button(Container root,String name){
  for(Component x:root.getComponents()){
   if(x instanceof JButton&&name.equals(x.getAccessibleContext().getAccessibleName()))return (JButton)x;
   if(x instanceof Container){JButton found=button((Container)x,name);if(found!=null)return found;}
  }return null;
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 interface Task {void run()throws Exception;}
 static void edt(Task task)throws Exception{
  SwingUtilities.invokeAndWait(()->{try{task.run();}catch(Exception e){throw new RuntimeException(e);}});
 }
 static void size(LayoutEditorUi ui,int width)throws Exception{
  edt(()->{ui.panel().setSize(width,640);layout(ui.panel());});
  edt(()->layout(ui.panel()));
 }
 static LayoutEditorUi editor;
 static DashboardView view;
 static int refreshRequests;
 static boolean deferredCloseInvoked;
 static String encode(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
 static void document(String role)throws Exception{
  editor.handle("LAYOUT_EDITOR_DOC\t"+encode("f1_2")+"\t"+encode(role)+"\t"+encode("theme.json")+"\t"
      +encode("{\"path\":[],\"kind\":\"object\",\"children\":[]}")+"\t0");
  edt(()->{});
 }
 static void expectLoad(String role, Runnable action)throws Exception{
  ByteArrayOutputStream output=new ByteArrayOutputStream();PrintStream previous=System.out;
  try{System.setOut(new PrintStream(output));edt(action::run);}
  finally{System.setOut(previous);}
  check(output.toString().trim().equals("LAYOUT_EDITOR\tload\t"+encode("f1_2")+"\t"+encode(role)),
    "expected one load request for "+role+": "+output);
  document(role);
 }
 static void checkItems(JComboBox<?> box,String... expected){
  String[] actual=new String[box.getItemCount()];
  for(int i=0;i<actual.length;i++)actual[i]=box.getItemAt(i).toString();
  check(Arrays.equals(expected,actual),"unexpected menu: "+Arrays.toString(actual));
 }
 static void navigation()throws Exception{
  JComboBox<?> areas=(JComboBox<?>)read(editor,"areas"),docs=(JComboBox<?>)read(editor,"documents");
  edt(()->{
   checkItems(areas,"페이지","테마 설정");
   checkItems(docs,"전체 조망","F1","게이지","그래프","퍼포먼스","설정 페이지","진단");
  });
  expectLoad("settings",()->docs.setSelectedIndex(5));
  edt(()->check(DashboardDesktop.settingsScreen(view)==-1
      &&DashboardDesktop.draw(view).texts.contains(UiText.t("UI 설정")),"settings opens real root page"));
  expectLoad("shell",()->areas.setSelectedIndex(1));
  edt(()->checkItems(docs,"화면 틀","색상","글꼴","컴포넌트"));
  expectLoad("fonts",()->docs.setSelectedIndex(2));
  expectLoad("settings",()->areas.setSelectedIndex(0));
  expectLoad("fonts",()->areas.setSelectedIndex(1));
  edt(()->{
   button(editor.panel(),"앱 테마 설정").doClick();
   check(DashboardDesktop.draw(view).texts.contains(UiText.t("배경화면")),"app theme settings did not render");
   DashboardDesktop.tapEditorLabel(view,UiText.t("테마"));
   check(DashboardDesktop.settingsScreen(view)==1,"theme settings row did not receive real canvas touch");
   check(docs.getSelectedItem().toString().equals("글꼴"),"app settings changed the selected theme document");
  });
 }
 static void liveThemeRefresh(Activity activity)throws Exception{
  edt(()->{
   DashboardDesktop.openThemeSettings(view);
   Object settings=DashboardDesktop.field(view,"settingsEditor");
   int screen=DashboardDesktop.settingsScreen(view);
   Field scroll=settings.getClass().getDeclaredField("pageScrollY");
   scroll.setAccessible(true);scroll.setFloat(settings,20f);
   ThemeCatalog catalog=ThemeCatalog.load(activity);
   DashboardDesktop.refreshEditorTheme(activity,view,catalog,"f1_2_optics",true,"");
   ThemeSpec active=(ThemeSpec)DashboardDesktop.field(view,"activeTheme");
   check(active.id.equals("f1_2_optics")&&active.shell().width==64,"live view did not receive theme geometry");
   check(DashboardDesktop.settingsScreen(view)==screen&&scroll.getFloat(settings)==20f,
       "theme change reset the settings screen or its scroll position");
  });
 }
 static void verifyWidths(LayoutEditorUi ui,JSplitPane outer,JSplitPane inner)throws Exception{
    int width=ui.panel().getWidth();
    check(outer.getLeftComponent().getWidth()>=184,"structure panel clipped at "+width);
    check(inner.getLeftComponent().getWidth()>=480,"preview canvas clipped at "+width);
    check(inner.getRightComponent().getWidth()>=330,"property panel clipped at "+width);
    JButton theme=button(ui.panel(),"앱 테마 설정"),refresh=button(ui.panel(),"소스 새로고침"),capture=button(ui.panel(),"캔버스 캡처");
    check(theme.getWidth()==theme.getPreferredSize().width&&refresh.getWidth()==refresh.getPreferredSize().width
        &&capture.getWidth()==capture.getPreferredSize().width,
        "canvas actions clipped at "+width);
    JButton options=button(ui.panel(),"보기 옵션 ▾");
    int optionsEnd=SwingUtilities.convertPoint(options,options.getWidth(),0,ui.panel()).x;
    int themeStart=SwingUtilities.convertPoint(theme,0,0,ui.panel()).x;
    check(optionsEnd<themeStart,"canvas options overlap at "+width);
 }
 static void dragDivider(JSplitPane split,int delta){
  Component divider=((BasicSplitPaneUI)split.getUI()).getDivider();
  int x=divider.getWidth()/2,y=divider.getHeight()/2;
  long time=System.currentTimeMillis();
  divider.dispatchEvent(new MouseEvent(divider,MouseEvent.MOUSE_PRESSED,time,MouseEvent.BUTTON1_DOWN_MASK,
      x,y,1,false,MouseEvent.BUTTON1));
  divider.dispatchEvent(new MouseEvent(divider,MouseEvent.MOUSE_DRAGGED,time,MouseEvent.BUTTON1_DOWN_MASK,
      x+delta,y,0,false,MouseEvent.NOBUTTON));
  divider.dispatchEvent(new MouseEvent(divider,MouseEvent.MOUSE_RELEASED,time,0,x+delta,y,1,false,MouseEvent.BUTTON1));
 }
 static void resize()throws Exception{
  JSplitPane outer=(JSplitPane)read(editor,"workspaceSplit"),inner=(JSplitPane)read(editor,"canvasSplit");
  for(int width:new int[]{1040,1180,1360,1760}){
   size(editor,width);
   edt(()->verifyWidths(editor,outer,inner));
  }
  edt(()->dragDivider(outer,40));
  edt(()->{
   check(outer.getLeftComponent().getWidth()==260,"outline divider position did not apply");
   dragDivider(inner,-40);
  });
  edt(()->{
   check(inner.getRightComponent().getWidth()==420,"inspector divider position did not apply");
   verifyWidths(editor,outer,inner);
  });
  size(editor,1040);
  edt(()->verifyWidths(editor,outer,inner));
  size(editor,1360);
  edt(()->check(outer.getLeftComponent().getWidth()==260&&inner.getRightComponent().getWidth()==420,
    "preferred widths were lost when the window narrowed"));
 }
 static LayoutEditorUi makeEditor(Activity activity){
  return new LayoutEditorUi(new DashboardDesktop.Screen(view),new JComboBox<>(new String[]{"JS"}),
     new JComboBox<>(new String[]{"received"}),()->{},()->{try{DashboardDesktop.openThemeSettings(view);}
     catch(Exception e){throw new RuntimeException(e);}},()->{},()->refreshRequests++,activity.getSharedPreferences("test-layout-panels",0),
     activity.getSharedPreferences("test-layout-panels",0).getString("active_theme","f1_2"),
     activity.getSharedPreferences("test-layout-panels",0).getString("active_role","overview"),
     (theme,follow,role)->{try{DashboardDesktop.editorPage(view,role);return "800 × 480";}
     catch(Exception e){throw new RuntimeException(e);}});
 }
 public static void main(String[] args)throws Exception{
  Activity activity=new Activity();
  edt(()->{
   DashboardPreviewUi.installLookAndFeel();
   LanguageSettings.init(activity);
   LanguageSettings.save(activity,"ko");
   view=DashboardDesktop.create(activity);
   editor=makeEditor(activity);
   editor.setThemes(Arrays.asList("original","f1_2","f1_2_optics"));
  });
  edt(()->{
   editor.requestCurrent();
   editor.requestClose(()->deferredCloseInvoked=true);
   check(!deferredCloseInvoked,"close must wait while the initial document request is pending");
  });
  editor.handle("LAYOUT_EDITOR_DOC\t"+encode("f1_2")+"\t"+encode("overview")+"\t"+encode("theme.json")+"\t"
      +encode("{\"path\":[],\"kind\":\"object\",\"children\":[]}")+"\t0");
  edt(()->{});
  check(deferredCloseInvoked,"a close request during initial loading must finish after the document arrives");
  document("overview");
  JTree tree=(JTree)read(editor,"tree");
  javax.swing.tree.TreeModel existingTree=tree.getModel();
  document("overview");
  check(tree.getModel()==existingTree,"same document replaced the tree");
  navigation();
  edt(()->{
   JButton refresh=(JButton)read(editor,"refreshButton");refresh.doClick();
   check(refreshRequests==1,"source refresh button was not connected");
  });
  liveThemeRefresh(activity);
  resize();
  edt(()->editor.handle("LAYOUT_EDITOR_DOC\t"+encode("original")+"\t"+encode("colors")+"\t"+encode("theme.json")+"\t"
      +encode("{\"path\":[],\"kind\":\"object\",\"children\":[]}")+"\t0"));
  edt(()->{});
  LayoutEditorUi[] reopened=new LayoutEditorUi[1];
  edt(()->reopened[0]=makeEditor(activity));
  LayoutEditorUi restored=reopened[0];
  check(read(restored,"themeId").equals("original")&&read(restored,"role").equals("colors"),
      "last active theme and document were not restored");
  size(restored,1440);
  edt(()->check(((JSplitPane)read(restored,"workspaceSplit")).getLeftComponent().getWidth()==260
      &&((JSplitPane)read(restored,"canvasSplit")).getRightComponent().getWidth()==420,"restart did not restore widths"));
  System.out.println("PASS grouped navigation, real theme settings, and persistent panel widths");
 }
}
"""


class LayoutEditorUiTests(unittest.TestCase):
    def test_grouped_navigation_live_settings_and_resizable_panels(self):
        with tempfile.TemporaryDirectory(prefix="layout-editor-resize-") as temp:
            java, classes = compile_runtime(temp)
            source = Path(temp) / "LayoutEditorResizeCheck.java"
            source.write_text(HARNESS, encoding="utf-8")
            compiled = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-cp", str(classes), "-d", str(classes), str(source)],
                capture_output=True, text=True, timeout=30)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            result = subprocess.run(
                [java, "-Djava.awt.headless=true",
                 "-Djava.util.prefs.userRoot=" + str(Path(temp) / "user-prefs"),
                 "-Djava.util.prefs.systemRoot=" + str(Path(temp) / "system-prefs"),
                 "-Ddashboard.assets="
                 + str(ROOT / "apps/dashboard/src/0x1101L/assets"),
                 "-cp", str(classes), "LayoutEditorResizeCheck"],
                capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS grouped navigation, real theme settings, and persistent panel widths", result.stdout)


if __name__ == "__main__":
    unittest.main()
