"""Desktop host for the unmodified DashboardView touch and draw methods.

Only Android platform boundaries are adapted. No dashboard hit regions live here.
Preferences are session-local; OEM/media/system operations are unavailable.
"""
from pathlib import Path
import subprocess

from js_ui_preview import ROOT, Renderer, source_stamp


def compile_runtime(folder):
    engine = Renderer(folder)
    engine.compile(source_stamp())
    engine.close()
    classes = engine.classes
    sources = []
    for name, body in STUBS.items():
        path = classes / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding='utf-8')
        sources.append(path)
    canvas = classes / 'android/graphics/Canvas.java'
    body = canvas.read_text()
    canvas.write_text(body.rsplit('}', 1)[0] +
                      ' public java.awt.image.BufferedImage image(){g.dispose();return image;}\n'
                      ' public void blit(java.awt.Graphics target){target.drawImage(image,0,0,null);g.dispose();}\n}')
    sources.append(canvas)
    sources.append(ROOT / 'tools/DashboardPreviewUi.java')
    base = ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard'
    for name in ('DashboardView', 'gauge/GaugeEditor', 'graph/GraphEditor'):
        sources.append(base / 'view' / (name + '.java'))
    for name in ('ThemeCatalog', 'ThemeJsonParser', 'ThemeRepository', 'ThemeSettingsStore',
                 'ThemeSelectionDraft', 'ThemePreviewController'):
        sources.append(base / 'theme' / (name + '.java'))
    sources.extend((base / 'vehicle/js/JsTransmissionSetting.java',
                    ROOT / 'shared/src/com/veloster/i18n/LanguageSettings.java'))
    from test_quick_entry_runtime import java_tools
    java, javac = java_tools()
    result = subprocess.run([javac, '-encoding', 'UTF-8', '-cp', str(classes),
                             '-d', str(classes), *map(str, sources)], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return java, classes


def run():
    import tempfile
    with tempfile.TemporaryDirectory(prefix='dashboard-runtime-') as folder:
        java, classes = compile_runtime(folder)
        subprocess.run([java, '-Ddashboard.assets=' + str(ROOT / 'apps/dashboard/src/main/assets'),
                        '-cp', str(classes), 'DashboardDesktop'], check=True)


STUBS = {
    'android/app/AlertDialog.java': '''package android.app;
import android.content.DialogInterface;
public class AlertDialog implements DialogInterface {
 public static AlertDialog last;public final Builder spec;private javax.swing.JDialog window;private boolean dismissed;
 private DialogInterface.OnCancelListener cancel;private DialogInterface.OnDismissListener onDismiss;
 AlertDialog(Builder b){spec=b;}public void setOnCancelListener(DialogInterface.OnCancelListener l){cancel=l;}
 public void setOnDismissListener(DialogInterface.OnDismissListener l){onDismiss=l;}
 public void show(){
  last=this;if(java.awt.GraphicsEnvironment.isHeadless())return;
  window=new javax.swing.JDialog(spec.activity.window,spec.title,java.awt.Dialog.ModalityType.DOCUMENT_MODAL);
  window.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
  window.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){cancel();}});
  javax.swing.JPanel root=new javax.swing.JPanel();root.setLayout(new javax.swing.BoxLayout(root,javax.swing.BoxLayout.Y_AXIS));
  if(spec.message!=null)root.add(new javax.swing.JLabel(spec.message));
  if(spec.content!=null)root.add(spec.content);
  if(spec.items!=null){javax.swing.JPanel list=new javax.swing.JPanel();list.setLayout(new javax.swing.BoxLayout(list,javax.swing.BoxLayout.Y_AXIS));javax.swing.ButtonGroup group=new javax.swing.ButtonGroup();
   for(int i=0;i<spec.items.length;i++){final int index=i;
    javax.swing.AbstractButton b=spec.single?new javax.swing.JRadioButton(spec.items[i],i==spec.selected):new javax.swing.JButton(spec.items[i]);
    if(spec.single)group.add(b);b.addActionListener(e->choose(index));list.add(b);
   }javax.swing.JScrollPane scroll=new javax.swing.JScrollPane(list);scroll.setPreferredSize(new java.awt.Dimension(450,Math.min(320,spec.items.length*35+12)));root.add(scroll);
  }
  javax.swing.JPanel actions=new javax.swing.JPanel();
  if(spec.negative!=null){javax.swing.JButton b=new javax.swing.JButton(spec.negative);b.addActionListener(e->negative());actions.add(b);}
  if(spec.positive!=null){javax.swing.JButton b=new javax.swing.JButton(spec.positive);b.addActionListener(e->positive());actions.add(b);}
  root.add(actions);window.setContentPane(root);window.pack();window.setLocationRelativeTo(spec.activity.window);window.setVisible(true);
 }
 public void choose(int i){if(i<0||i>=spec.items.length)throw new IllegalArgumentException();if(!spec.single)dismiss();if(spec.itemListener!=null)spec.itemListener.onClick(this,i);}
 public void positive(){if(spec.positiveListener!=null)spec.positiveListener.onClick(this,-1);dismiss();}
 public void negative(){if(spec.negativeListener!=null)spec.negativeListener.onClick(this,-2);dismiss();}
 public void cancel(){if(cancel!=null)cancel.onCancel(this);dismiss();}
 public void dismiss(){if(dismissed)return;dismissed=true;if(last==this)last=null;if(window!=null)window.dispose();if(onDismiss!=null)onDismiss.onDismiss(this);}
 public static class Builder {
  public final Activity activity;public String title="",message,positive,negative;public String[] items;public boolean single;public int selected;
  public android.view.View content;public DialogInterface.OnClickListener itemListener,positiveListener,negativeListener;
  public Builder(android.content.Context c){activity=(Activity)c;}
  public Builder setTitle(CharSequence s){title=s.toString();return this;}public Builder setMessage(CharSequence s){message=s.toString();return this;}
  public Builder setItems(String[] values,DialogInterface.OnClickListener l){items=values;itemListener=l;return this;}
  public Builder setSingleChoiceItems(String[] values,int n,DialogInterface.OnClickListener l){items=values;selected=n;itemListener=l;single=true;return this;}
  public Builder setPositiveButton(CharSequence s,DialogInterface.OnClickListener l){positive=s.toString();positiveListener=l;return this;}
  public Builder setNegativeButton(CharSequence s,DialogInterface.OnClickListener l){negative=s.toString();negativeListener=l;return this;}
  public Builder setView(android.view.View v){content=v;return this;}
  public AlertDialog create(){return new AlertDialog(this);}public AlertDialog show(){AlertDialog d=create();d.show();return d;}
 }
}''',
    'DashboardDesktop.java': '''import java.lang.reflect.*;
import javax.swing.*;
import android.app.*;
import android.view.*;
import com.veloster.dashboard.view.DashboardView;
import com.veloster.dashboard.vehicle.Telemetry;
import com.veloster.dashboard.debugger.JsDiagState;
import com.veloster.i18n.LanguageSettings;
import com.veloster.i18n.UiText;

public class DashboardDesktop {
 static Object field(Object target,String name)throws Exception{Field f=(target instanceof Class?(Class<?>)target:target.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);}
 static void copy(Object from,Object to)throws Exception{for(Field f:from.getClass().getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);f.set(to,f.get(from));}}
 static void fixture(String state,String vehicle)throws Exception{
  android.os.SystemClock.now=state.equals("expired")?62000:2000;
  copy(JsDiagnosticsPreview.fixture(state,vehicle),field(DashboardView.class,"DATA"));
  copy(JsDiagnosticsPreview.observations(state),field(DashboardView.class,"DIAG"));
 }
 static DashboardView create(Activity a)throws Exception{
  a.content.removeAll();DashboardView.attachStandalone(a);
  DashboardView view=(DashboardView)a.content.getComponent(0);view.setSize(800,480);return view;
 }
 static void waitForTimers(){java.awt.SecondaryLoop loop=java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();javax.swing.Timer timer=new javax.swing.Timer(700,e->loop.exit());timer.setRepeats(false);timer.start();loop.enter();}
 static void touch(DashboardView view,int action,float x,float y){int id=action==0?java.awt.event.MouseEvent.MOUSE_PRESSED:action==1?java.awt.event.MouseEvent.MOUSE_RELEASED:java.awt.event.MouseEvent.MOUSE_DRAGGED;view.dispatchEvent(new java.awt.event.MouseEvent(view,id,System.currentTimeMillis(),0,(int)x,(int)y,1,false));}
 static void tap(DashboardView view,float x,float y){touch(view,0,x,y);touch(view,1,x,y);}
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static android.graphics.Canvas draw(DashboardView view){android.graphics.Canvas c=new android.graphics.Canvas(view.getWidth(),view.getHeight());view.draw(c);return c;}
 static void invoke(DashboardView view,String name){try{Method method=DashboardView.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(view);}catch(Exception e){throw new RuntimeException(e);}}
 static void capture(JFrame owner,DashboardView view){DashboardPreviewUi.showCapture(owner,draw(view).image());}
 static void button(java.awt.Container root,String label){for(java.awt.Component child:root.getComponents()){
  if(child instanceof android.widget.Button&&((android.widget.Button)child).button.getText().equals(label)){((android.widget.Button)child).performClick();return;}
  if(child instanceof java.awt.Container){try{button((java.awt.Container)child,label);return;}catch(IllegalArgumentException ignored){}}
 }throw new IllegalArgumentException(label);}
 static void selfCheck()throws Exception{
  fixture("received","js");Activity a=new Activity();LanguageSettings.save(a,"ko");DashboardView view=create(a);
  java.util.List<String> overview=draw(view).texts;
  tap(view,560,20);tap(view,180,20);check(overview.equals(draw(view).texts),"overview from settings must not toggle into diagnostics");
  touch(view,0,60,20);touch(view,2,280,20);touch(view,1,280,20);
  check(overview.equals(draw(view).texts),"drag must not switch pages");
  tap(view,60,20);check(draw(view).texts.contains("PERFORMANCE"),"real Java performance navigation");
  tap(view,560,20);check(draw(view).texts.contains(UiText.t("언어")),"real Java settings navigation");
  tap(view,140,164);check(AlertDialog.last!=null&&AlertDialog.last.spec.title.equals("Language / 언어"),"real LanguageSettings.show");
  AlertDialog.last.choose(2);AlertDialog.last.positive();check(LanguageSettings.selection(a).equals("en"),"real Java save language callback");
  tap(view,140,284);AlertDialog theme=AlertDialog.last;check(theme!=null&&theme.spec.title.equals("Theme / 테마"),"real theme picker");
  button(theme.spec.content,"Aurora Focus");button(theme.spec.content,"Cancel / 취소");
  check(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","original").equals("original"),"cancel leaves saved theme intact");
  tap(view,140,284);theme=AlertDialog.last;button(theme.spec.content,"Aurora Focus");button(theme.spec.content,"Apply / 적용");
  check(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","").equals("aurora_focus"),"real Java theme apply callback");
  check(draw(view).texts.contains("Aurora Focus"),"applied theme visible in settings");
  tap(view,180,20);check(!draw(view).texts.contains(UiText.t("언어")),"leave settings through actual touch code");
  tap(view,360,20);Object layout=field(view,"gaugeLayout");
  boolean before=((com.veloster.dashboard.view.gauge.GaugeLayout)layout).dense();tap(view,700,65);
  check(((com.veloster.dashboard.view.gauge.GaugeLayout)layout).dense()!=before,"real gauge grid toggle");
  touch(view,0,100,160);waitForTimers();check(AlertDialog.last!=null,"actual 600ms hold opens gauge editor");
  AlertDialog.last.choose(1);check(AlertDialog.last!=null,"actual gauge style menu callback");AlertDialog.last.choose(2);
  check(((com.veloster.dashboard.view.gauge.GaugeLayout)layout).style(0)==2,"actual gauge style save callback");touch(view,1,100,160);
  touch(view,0,100,160);touch(view,2,140,160);waitForTimers();check(AlertDialog.last==null,"drag cancels the hold timer");touch(view,1,140,160);
  tap(view,560,20);before=((com.veloster.dashboard.view.gauge.GaugeLayout)layout).dense();tap(view,700,65);
  check(((com.veloster.dashboard.view.gauge.GaugeLayout)layout).dense()==before,"settings does not leak touches into gauges");
  view.setSize(400,240);tap(view,30,10);check(draw(view).texts.contains("PERFORMANCE"),"actual view coordinate scaling");
  for(String vehicle:new String[]{"js","js_n","pd"}){fixture("received",vehicle);DashboardView other=create(new Activity());tap(other,560,20);check(draw(other).texts.contains(UiText.t("언어")),"profile settings "+vehicle);}
  final java.awt.image.BufferedImage[] captured={null};final boolean[] refreshed={false};
  JPanel tools=DashboardPreviewUi.actionBar(new JComboBox<String>(new String[]{"received"}),()->refreshed[0]=true,()->captured[0]=draw(view).image());
  tools.setSize(640,tools.getPreferredSize().height);tools.doLayout();
  JButton capture=(JButton)((java.awt.BorderLayout)tools.getLayout()).getLayoutComponent(java.awt.BorderLayout.EAST);
  java.awt.Component controls=((java.awt.BorderLayout)tools.getLayout()).getLayoutComponent(java.awt.BorderLayout.CENTER);
  check(capture.getX()>=controls.getX()+controls.getWidth()&&capture.getX()+capture.getWidth()<=640,"capture remains visible in lower action bar");
  for(java.awt.Component item:((java.awt.Container)controls).getComponents())if(item instanceof JButton)((JButton)item).doClick();
  check(refreshed[0],"refresh action is connected");
  capture.doClick();check(captured[0]!=null&&captured[0].getWidth()==400&&captured[0].getHeight()==240,"capture action uses current canvas size");
  boolean[] actions={false,false,false};
  JPanel toast=DashboardPreviewUi.capturePanel(captured[0],()->actions[0]=true,()->actions[1]=true,()->actions[2]=true);
  for(java.awt.Component group:toast.getComponents())if(group instanceof JPanel)for(java.awt.Component item:((JPanel)group).getComponents())if(item instanceof JButton)((JButton)item).doClick();
  check(actions[0]&&actions[1]&&actions[2],"capture copy/save/close callbacks connected without touching clipboard or files");
  invoke(view,"showThemePicker");button(AlertDialog.last.spec.content,"CarPlay Glass Light");button(AlertDialog.last.spec.content,"Apply / 적용");
  check(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","").equals("carplay_glass_light"),"desktop theme tool uses actual picker apply callback");
  System.out.println("PASS actual DashboardView: navigation, drag rejection, settings, language save, theme cancel/apply, gauge toggle, long press, editor callback, input isolation, scaled input and 3 vehicle profiles");
 }
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("--check")){SwingUtilities.invokeAndWait(()->{try{selfCheck();}catch(Exception e){throw new RuntimeException(e);}});return;}
  DashboardPreviewUi.installLookAndFeel();
  SwingUtilities.invokeLater(()->{try{
   Activity activity=new Activity();LanguageSettings.save(activity,"ko");fixture("received","js");
   JFrame frame=new JFrame("JS Dashboard Preview");activity.window=frame;frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
   String[] vehicles={"js","js_n","pd"};JComboBox<String> car=new JComboBox<String>(new String[]{"Veloster","Veloster N","i30 (N Line)"});
   JComboBox<String> state=new JComboBox<String>(new String[]{"received","waiting","invalid","held","expired"});
   JPanel screen=new JPanel(new java.awt.BorderLayout());final DashboardView[] current={create(activity)};screen.add(current[0]);
   JButton language=DashboardPreviewUi.button("한국어 ▾",false,()->{}),theme=DashboardPreviewUi.button("Original ▾",false,()->{});
   language.setPreferredSize(new java.awt.Dimension(140,34));theme.setPreferredSize(new java.awt.Dimension(220,34));
   language.setToolTipText("대시보드 언어 변경");theme.setToolTipText("대시보드 테마 선택 및 미리보기");
   Runnable sync=()->{try{
    String selected=LanguageSettings.selection(activity);
    language.setText(("auto".equals(selected)?UiText.t("시스템 설정 따름"):"ko".equals(selected)?"한국어":"English")+" ▾");
    theme.setText(((com.veloster.dashboard.theme.ThemeSpec)field(current[0],"activeTheme")).displayName+" ▾");
   }catch(Exception e){throw new RuntimeException(e);}};
   language.addActionListener(e->LanguageSettings.show(activity,()->{sync.run();current[0].repaint();}));
   theme.addActionListener(e->{invoke(current[0],"showThemePicker");sync.run();});
   Runnable data=()->{try{fixture((String)state.getSelectedItem(),vehicles[car.getSelectedIndex()]);current[0].repaint();}catch(Exception e){throw new RuntimeException(e);}};
   car.addActionListener(e->{try{current[0].removeCallbacks((Runnable)field(current[0],"editHold"));screen.removeAll();data.run();current[0]=create(activity);screen.add(current[0]);screen.revalidate();screen.repaint();sync.run();}catch(Exception error){throw new RuntimeException(error);}});
   state.addActionListener(e->data.run());
   JPanel root=DashboardPreviewUi.shell(DashboardPreviewUi.contextBar(car,language,theme),screen,DashboardPreviewUi.actionBar(state,data,()->capture(frame,current[0])));
   root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("R"),"refresh");
   root.getActionMap().put("refresh",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent event){data.run();}});
   frame.setContentPane(root);frame.pack();frame.setMinimumSize(new java.awt.Dimension(720,480));frame.setLocationByPlatform(true);frame.setVisible(true);
   System.out.println("READY Dashboard Java touch runtime: showing="+frame.isShowing()+", canvas="+current[0].getWidth()+"x"+current[0].getHeight());
   javax.swing.Timer timer=new javax.swing.Timer(100,e->{current[0].repaint();sync.run();});timer.start();
   frame.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){timer.stop();}});
  }catch(Exception error){error.printStackTrace();JOptionPane.showMessageDialog(null,error.toString());}});
 }
}''',
    'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 int getInt(String k,int d); String getString(String k,String d);
 Editor edit(); interface Editor {Editor putInt(String k,int v);Editor putString(String k,String v);boolean commit();void apply();}
}''',
    'android/content/Context.java': '''package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 private final java.util.Map<String,Prefs> preferences=new java.util.HashMap<String,Prefs>();
 public SharedPreferences getSharedPreferences(String n,int mode){if(!preferences.containsKey(n))preferences.put(n,new Prefs());return preferences.get(n);}
 public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}
 static class Prefs implements SharedPreferences,SharedPreferences.Editor {
  java.util.Map<String,Object> values=new java.util.HashMap<String,Object>();
  public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}
  public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
  public Editor edit(){return this;}public Editor putInt(String k,int v){values.put(k,v);return this;}
  public Editor putString(String k,String v){values.put(k,v);return this;}public boolean commit(){return true;}public void apply(){}
 }
}''',
    'android/content/res/AssetManager.java': '''package android.content.res;
public class AssetManager {public java.io.InputStream open(String name)throws java.io.IOException{
 return java.nio.file.Files.newInputStream(java.nio.file.Paths.get(System.getProperty("dashboard.assets"),name));
}}''',
    'android/content/Intent.java': '''package android.content;
public class Intent {public static final String ACTION_GET_CONTENT="get-content";public Intent(){}public Intent(String a){}
 public boolean getBooleanExtra(String n,boolean d){return d;}public void removeExtra(String n){}
 public Intent setType(String t){return this;}public static Intent createChooser(Intent i,String s){return i;}
}''',
    'android/content/DialogInterface.java': '''package android.content;
public interface DialogInterface {void dismiss();interface OnClickListener {void onClick(DialogInterface d,int n);}
 interface OnCancelListener {void onCancel(DialogInterface d);}interface OnDismissListener {void onDismiss(DialogInterface d);}}
''',
    'android/app/Activity.java': '''package android.app;
public class Activity extends android.content.Context {
 public final android.widget.FrameLayout content=new android.widget.FrameLayout(this);
 public javax.swing.JFrame window;public boolean finished;
 public android.view.View findViewById(int id){return content;}
 public android.content.Intent getIntent(){return new android.content.Intent();}
 public void finish(){finished=true;if(window!=null)window.dispose();}
 public boolean isFinishing(){return finished;}public void runOnUiThread(Runnable r){javax.swing.SwingUtilities.invokeLater(r);}
 public void startActivity(android.content.Intent i){throw new UnsupportedOperationException("Android system screen unavailable in desktop host");}
 public void startActivityForResult(android.content.Intent i,int code){startActivity(i);}
}''',
    'android/R.java': 'package android;public class R {public static class id {public static final int content=1;}}',
    'android/os/SystemClock.java': '''package android.os;public class SystemClock {
 public static long now=2000;public static long elapsedRealtime(){return now;}}
''',
    'android/util/Log.java': '''package android.util;public class Log {
 public static int i(String t,String s){return 0;}public static int e(String t,String s,Throwable e){e.printStackTrace();return 0;}}
''',
    'android/view/MotionEvent.java': '''package android.view;
public class MotionEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;
 private final int action;private final float x,y;
 public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
 public int getAction(){return action;}public float getX(){return x;}public float getY(){return y;}
}''',
    'android/view/Gravity.java': '''package android.view;public class Gravity {
 public static final int BOTTOM=80,CENTER_HORIZONTAL=1,CENTER_VERTICAL=16;}
''',
    'android/view/View.java': '''package android.view;
public class View extends javax.swing.JPanel {
 public static final int VISIBLE=0,GONE=8;private Object tag;private int visibility=VISIBLE;
 private final java.util.Map<Runnable,javax.swing.Timer> callbacks=new java.util.HashMap<Runnable,javax.swing.Timer>();
 public interface OnClickListener {void onClick(View v);}protected OnClickListener listener;
 public View(android.content.Context context){
  setOpaque(false);
  java.awt.event.MouseAdapter input=new java.awt.event.MouseAdapter(){
   private void send(java.awt.event.MouseEvent e,int action){onTouchEvent(new MotionEvent(action,e.getX(),e.getY()));repaint();}
   public void mousePressed(java.awt.event.MouseEvent e){send(e,MotionEvent.ACTION_DOWN);}
   public void mouseDragged(java.awt.event.MouseEvent e){send(e,MotionEvent.ACTION_MOVE);}
   public void mouseReleased(java.awt.event.MouseEvent e){send(e,MotionEvent.ACTION_UP);}
  };addMouseListener(input);addMouseMotionListener(input);
 }
 public java.awt.Dimension getPreferredSize(){return new java.awt.Dimension(800,480);}
 public void setVisibility(int v){visibility=v;setVisible(v==VISIBLE);}public int getVisibility(){return visibility;}
 public void setOnClickListener(OnClickListener l){listener=l;}public void performClick(){if(listener!=null)listener.onClick(this);}
 public void bringToFront(){}public void setTag(Object value){tag=value;}public Object getTag(){return tag;}
 public void invalidate(){super.invalidate();repaint();}
 public boolean post(Runnable r){javax.swing.SwingUtilities.invokeLater(r);return true;}
 public boolean postDelayed(final Runnable r,long delay){removeCallbacks(r);javax.swing.Timer timer=new javax.swing.Timer((int)delay,e->{callbacks.remove(r);r.run();repaint();});timer.setRepeats(false);callbacks.put(r,timer);timer.start();return true;}
 public boolean removeCallbacks(Runnable r){javax.swing.Timer old=callbacks.remove(r);if(old!=null)old.stop();return old!=null;}
 protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}protected void onMeasure(int w,int h){}
 protected void setMeasuredDimension(int w,int h){setPreferredSize(new java.awt.Dimension(w,h));}
 protected void onDraw(android.graphics.Canvas c){}public boolean onTouchEvent(MotionEvent e){return false;}
 public void draw(android.graphics.Canvas c){onDraw(c);}
 protected void paintComponent(java.awt.Graphics g){super.paintComponent(g);if(getWidth()>0&&getHeight()>0){android.graphics.Canvas c=new android.graphics.Canvas(getWidth(),getHeight());onDraw(c);c.blit(g);}}
 public static class MeasureSpec {public static final int EXACTLY=1,AT_MOST=2,UNSPECIFIED=0;public static int getMode(int s){return s>>>30;}public static int getSize(int s){return s&0x3fffffff;}}
}''',
    'android/view/ViewGroup.java': '''package android.view;public class ViewGroup extends View {
 public ViewGroup(android.content.Context c){super(c);}public void addView(View v,LayoutParams p){add(v);}
 public static class LayoutParams {public final int width,height;public LayoutParams(int w,int h){width=w;height=h;}}
}''',
    'android/widget/FrameLayout.java': '''package android.widget;public class FrameLayout extends android.view.ViewGroup {
 public FrameLayout(android.content.Context c){super(c);setLayout(new java.awt.BorderLayout());}
 public void addView(android.view.View v,android.view.ViewGroup.LayoutParams p){add(v,java.awt.BorderLayout.CENTER);}
 public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,int gravity){super(w,h);}}
}''',
    'android/widget/LinearLayout.java': '''package android.widget;public class LinearLayout extends android.view.ViewGroup {
 public static final int VERTICAL=1,HORIZONTAL=0;private int orientation;
 public LinearLayout(android.content.Context c){super(c);setOrientation(VERTICAL);}
 public void setOrientation(int v){orientation=v;setLayout(new javax.swing.BoxLayout(this,v==VERTICAL?javax.swing.BoxLayout.Y_AXIS:javax.swing.BoxLayout.X_AXIS));}
 public java.awt.Dimension getPreferredSize(){return getLayout().preferredLayoutSize(this);}
 public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,float weight){super(w,h);}}
}''',
    'android/widget/TextView.java': '''package android.widget;public class TextView extends android.view.View {
 protected final javax.swing.JLabel label=new javax.swing.JLabel();public TextView(android.content.Context c){super(c);setLayout(new java.awt.BorderLayout());add(label);}
 public void setText(String s){label.setText(s);}public void setGravity(int g){}public void setPadding(int l,int t,int r,int b){setBorder(javax.swing.BorderFactory.createEmptyBorder(t,l,b,r));}
 public java.awt.Dimension getPreferredSize(){return getLayout().preferredLayoutSize(this);}
}''',
    'android/widget/Button.java': '''package android.widget;public class Button extends android.view.View {
 public final javax.swing.JButton button=new javax.swing.JButton();public Button(android.content.Context c){super(c);setLayout(new java.awt.BorderLayout());add(button);button.addActionListener(e->performClick());}
 public void setText(String s){button.setText(s);}public java.awt.Dimension getPreferredSize(){return button.getPreferredSize();}
}''',
    'android/widget/Toast.java': '''package android.widget;public class Toast {public static final int LENGTH_SHORT=0;private String message;
 public static Toast makeText(android.content.Context c,String s,int time){Toast t=new Toast();t.message=s;return t;}
 public void show(){System.err.println(message);if(!java.awt.GraphicsEnvironment.isHeadless())javax.swing.JOptionPane.showMessageDialog(null,message);}}
''',
    'com/veloster/dashboard/view/ArtworkLoader.java': '''package com.veloster.dashboard.view;
final class ArtworkLoader {ArtworkLoader(android.content.Context c,android.view.View v){}void open(){}void close(){}
 void request(com.veloster.dashboard.vehicle.MediaState.Snapshot s){}android.graphics.Bitmap bitmap(){return null;}}
''',
    'com/veloster/dashboard/vehicle/MediaReceiver.java': '''package com.veloster.dashboard.vehicle;
public class MediaReceiver {public MediaReceiver(MediaState s){}public void start(android.content.Context c){}public void stop(){}}
''',
    'com/veloster/dashboard/vehicle/MediaControl.java': '''package com.veloster.dashboard.vehicle;
public class MediaControl {public static boolean toggle(android.content.Context c,int mode,int state){return false;}
 public static boolean step(android.content.Context c,boolean next,int mode){return false;}}
''',
    'com/veloster/dashboard/vehicle/RegulationState.java': '''package com.veloster.dashboard.vehicle;
public class RegulationState {public static int read(android.content.Context c){return 0;}}
''',
    'com/veloster/dashboard/DashboardActivity.java': '''package com.veloster.dashboard;public class DashboardActivity {public static final int REQUEST_CARPLAY_CAPTURE=1;}''',
    'com/veloster/dashboard/debugger/JsLogger.java': '''package com.veloster.dashboard.debugger;public class JsLogger {public static void emit(long now,com.veloster.dashboard.vehicle.Telemetry t,JsDiagState.Snapshot s){}}''',
    'com/veloster/dashboard/debugger/CarPlayRawLog.java': '''package com.veloster.dashboard.debugger;public class CarPlayRawLog {}''',
}
