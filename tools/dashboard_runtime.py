"""Desktop host for the unmodified DashboardView touch and draw methods.

Only Android platform boundaries are adapted. No dashboard hit regions live here.
Preview preferences persist per checkout; OEM/media/system operations are unavailable.
"""
import hashlib
import json
import base64
import queue
import shutil
import threading
import subprocess
import time
from pathlib import Path

from js_ui_preview import ROOT, VEHICLES, Renderer, display_label


def compile_runtime(folder):
    engine = Renderer(folder)
    engine.compile()
    classes = engine.classes
    sources = []
    for name, body in STUBS.items():
        path = classes / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding='utf-8')
        sources.append(path)
    profiles = classes / 'PreviewProfile.java'
    rows = []
    for vehicle, spec in VEHICLES.items():
        width, height = spec['resolution']
        labels = (vehicle, spec['name'], display_label(spec), spec.get('fixture', vehicle))
        rows.append(' new PreviewProfile(' + ', '.join(map(json.dumps, labels)) +
                    f', {width}, {height})')
    profiles.write_text("""final class PreviewProfile {
 final String id,name,display,fixture;
 final int width,height;
 PreviewProfile(String id,String name,String display,String fixture,int width,int height){
  this.id=id;this.name=name;this.display=display;this.fixture=fixture;this.width=width;this.height=height;
 }
 static final PreviewProfile[] ALL={
""" + ',\n'.join(rows) + '\n };\n}\n', encoding='utf-8')
    sources.append(profiles)
    canvas = classes / 'android/graphics/Canvas.java'
    body = canvas.read_text()
    canvas.write_text(body.rsplit('}', 1)[0] +
                      ' public java.awt.image.BufferedImage image(){g.dispose();return image;}\n'
                      ' public void blit(java.awt.Graphics target){target.drawImage(image,0,0,null);g.dispose();}\n}')
    sources.append(canvas)
    sources.append(ROOT / 'tools/DashboardPreviewUi.java')
    sources.append(ROOT / 'tools/LayoutEditorUi.java')
    base = ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard'
    for name in ('DashboardView', 'gauge/GaugeEditor', 'graph/GraphEditor'):
        sources.append(base / 'view' / (name + '.java'))
    for name in ('ThemeCatalog', 'ThemeJsonParser', 'ThemeRepository', 'ThemeSettingsStore',
                 'ThemeSelectionDraft', 'ThemePreviewController', 'ThemeResources'):
        sources.append(ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme' / (name + '.java'))
    sources.extend((base / 'vehicle/js/JsTransmissionSetting.java',
                    ROOT / 'shared/src/com/veloster/i18n/LanguageSettings.java'))
    from test_quick_entry_runtime import java_tools
    java, javac = java_tools()
    result = subprocess.run([javac, '-encoding', 'UTF-8', '-cp', str(classes),
                             '-d', str(classes), *map(str, sources)], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return java, classes


def preference_directory(editor, checkout_root=ROOT, home=None):
    home = Path(home) if home is not None else Path.home()
    scope_key = str(Path(checkout_root).resolve()) + (':layout-editor' if editor else ':preview')
    preference_scope = hashlib.sha256(scope_key.encode('utf-8')).hexdigest()[:20]
    support = home / 'Library/Application Support'
    if not editor:
        return support / 'JS Dashboard Preview/preferences' / preference_scope

    legacy = support / 'JS Dashboard Preview/preferences' / preference_scope
    current = support / 'JS Dashboard Layout Studio/preferences' / preference_scope
    if not current.exists() and legacy.is_dir():
        current.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(legacy, current)
    return current


def run(asset_root=None, editor=False, editor_theme='f1_2', editor_themes=None, editor_handler=None):
    import tempfile
    asset_root = Path(asset_root).resolve() if asset_root else ROOT / 'apps/dashboard/src/0x1101L/assets'
    with tempfile.TemporaryDirectory(prefix='dashboard-runtime-') as folder:
        preference_dir = preference_directory(editor)
        generation = 0
        reload_attempt = 0
        generation_dir = Path(folder) / ('generation-' + str(generation))
        generation_dir.mkdir()
        java, classes = compile_runtime(generation_dir)
        while True:
            command = [java, '-Ddashboard.preferences.dir=' + str(preference_dir),
                       '-Ddashboard.assets=' + str(asset_root),
                       '-Ddashboard.editor=' + str(bool(editor)).lower(),
                       '-Ddashboard.editor.theme=' + editor_theme,
                       '-Ddashboard.editor.themes=' + ','.join(editor_themes or ()),
                       '-cp', str(classes), 'DashboardDesktop']
            process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                       text=True, bufsize=1)
            spotify_stop = threading.Event()
            spotify_lock = threading.Lock()
            spotify_thread = None

            def send_to_runtime(message):
                with spotify_lock:
                    if process.poll() is None:
                        process.stdin.write(message + '\n')
                        process.stdin.flush()

            def spotify_loop():
                script = '''
if not (running of application "Spotify") then return "STOPPED"
tell application "Spotify"
 set t to current track
 set stateText to player state as text
 set trackName to name of t
 set artistName to artist of t
 set trackDuration to duration of t
 set trackPosition to player position
 set coverUrl to artwork url of t
 return stateText & (character id 31) & trackName & (character id 31) & artistName & (character id 31) & trackDuration & (character id 31) & trackPosition & (character id 31) & coverUrl
end tell
'''
                previous = None
                while not spotify_stop.is_set():
                    try:
                        result = subprocess.run(['osascript', '-e', script], capture_output=True,
                                                text=True, timeout=3)
                        value = result.stdout.rstrip('\r\n') if result.returncode == 0 else 'STOPPED'
                        if value == 'STOPPED':
                            message = 'SPOTIFY_STOP'
                        else:
                            fields = value.split('\x1f')
                            if len(fields) != 6:
                                message = 'SPOTIFY_STOP'
                            else:
                                state, title, artist, duration, position, artwork = fields
                                state_code = '1' if state == 'playing' else '0' if state == 'paused' else '-1'
                                try:
                                    position_ms = max(0, int(float(position) * 1000))
                                    duration_ms = max(0, int(duration))
                                except ValueError:
                                    position_ms, duration_ms = -1, -1
                                encode = lambda text: base64.b64encode(text.encode('utf-8')).decode('ascii')
                                message = '\t'.join(('SPOTIFY', state_code, str(position_ms), str(duration_ms),
                                                     encode(title), encode(artist), encode(artwork)))
                        if message != previous:
                            with spotify_lock:
                                if process.poll() is None:
                                    process.stdin.write(message + '\n')
                                    process.stdin.flush()
                            previous = message
                    except (OSError, subprocess.SubprocessError, BrokenPipeError):
                        pass
                    spotify_stop.wait(1)

            def inspect_theme(theme_id):
                nonlocal inspection_id
                inspection_id += 1
                prefix = f'LAYOUT_EDITOR_INSPECT_RESULT\t{inspection_id}\t'
                send_to_runtime(f'LAYOUT_EDITOR_INSPECT\t{inspection_id}\t{theme_id}')
                deadline = time.monotonic() + 8
                while True:
                    try:
                        line = editor_output.get(timeout=max(0, deadline - time.monotonic()))
                    except queue.Empty:
                        raise ValueError("렌더러 검증 응답 시간이 초과되었습니다.") from None
                    if line is None:
                        editor_output.put(None)
                        raise ValueError("렌더러가 검증 중 종료되었습니다.")
                    if line.startswith(prefix):
                        result = line[len(prefix):].rstrip('\r\n')
                        if result == 'error':
                            raise ValueError("렌더러가 이 값을 적용할 수 없습니다. 테마 규격과 화면 배치를 확인하세요.")
                        return json.loads(result)
                    if not line.startswith('LAYOUT_EDITOR_INSPECT_RESULT\t'):
                        print(line, end='', flush=True)

            inspection_id = 0
            editor_output = queue.Queue()

            # Inspection replies share stdout with UI commands. One reader keeps
            # a bounded wait possible without racing the normal command loop.
            def read_editor_output(stream, output):
                try:
                    for line in stream:
                        output.put(line)
                finally:
                    output.put(None)

            if editor:
                threading.Thread(target=read_editor_output, args=(process.stdout, editor_output),
                                 name='dashboard-editor-output', daemon=True).start()
            lines = iter(editor_output.get, None) if editor else process.stdout
            reload_requested = False
            for line in lines:
                message = line.rstrip('\r\n')
                if message.startswith('LAYOUT_EDITOR_INSPECT_RESULT\t'):
                    continue
                if editor and message.startswith('LAYOUT_EDITOR\t') and editor_handler is not None:
                    try:
                        response = editor_handler(message, inspect_theme)
                        if response:
                            send_to_runtime(response)
                    except Exception as error:
                        detail = str(error).replace('\n', ' ')[:1800]
                        send_to_runtime('LAYOUT_EDITOR_STATUS\t0\t' + __import__('base64').b64encode(detail.encode('utf-8')).decode('ascii'))
                    continue
                if message.startswith('READY Dashboard') and spotify_thread is None:
                    spotify_thread = threading.Thread(target=spotify_loop, name='spotify-preview-bridge', daemon=True)
                    spotify_thread.start()
                if message != 'DASHBOARD_PREVIEW_RELOAD':
                    print(line, end='', flush=True)
                    continue
                reload_attempt += 1
                next_generation_dir = Path(folder) / ('reload-' + str(reload_attempt))
                next_generation_dir.mkdir()
                try:
                    next_java, next_classes = compile_runtime(next_generation_dir)
                except Exception as error:
                    message = str(error).replace('\n', ' ')[:1800]
                    print('Dashboard preview reload failed: ' + message, flush=True)
                    try:
                        with spotify_lock:
                            process.stdin.write('RELOAD_FAILED\t' + message + '\n')
                            process.stdin.flush()
                    except (BrokenPipeError, OSError):
                        pass
                    continue
                if process.poll() is not None:
                    break
                spotify_stop.set()
                if spotify_thread is not None:
                    spotify_thread.join(timeout=1)
                process.terminate()
                process.wait(timeout=5)
                process.stdin.close()
                java, classes = next_java, next_classes
                generation += 1
                reload_requested = True
                break
            spotify_stop.set()
            if spotify_thread is not None:
                spotify_thread.join(timeout=1)
            if process.stdout:
                process.stdout.close()
            if reload_requested:
                continue
            return_code = process.wait()
            if return_code:
                raise subprocess.CalledProcessError(return_code, command)
            return


STUBS = {
    'android/graphics/Typeface.java': '''package android.graphics;
import android.content.res.AssetManager;
import java.awt.Font;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Typeface {
 public static final int BOLD=1;
 public static final Typeface DEFAULT=new Typeface(new Font("Dialog",Font.PLAIN,12));
 public static final Typeface DEFAULT_BOLD=create(DEFAULT,BOLD);
 private static final Map<String,Typeface> editorFonts=new LinkedHashMap<String,Typeface>(16,.75f,true){
  @Override protected boolean removeEldestEntry(Map.Entry<String,Typeface> entry){return size()>24;}
 };
 final Font face;
 public final boolean bold;
 private Typeface(Font face){this.face=face;this.bold=face.isBold();}
 public static Typeface create(Typeface source,int style){return new Typeface(source.face.deriveFont(style));}
 public static synchronized Typeface createFromAsset(AssetManager assets,String path){
  try(InputStream input=assets.open(path)){
   String asset=AssetManager.editorAssetKey(input);
   Typeface cached=asset==null?null:editorFonts.get(asset);
   if(cached!=null)return cached;
   Typeface loaded=new Typeface(Font.createFont(Font.TRUETYPE_FONT,input));
   if(asset!=null)editorFonts.put(asset,loaded);
   return loaded;
  }catch(Exception error){throw new IllegalArgumentException(error);}
 }
}''',
    'android/graphics/BitmapFactory.java': '''package android.graphics;
import android.content.res.AssetManager;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BitmapFactory {
 private static final Map<String,Bitmap> editorImages=new LinkedHashMap<String,Bitmap>(8,.75f,true){
  @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> entry){return size()>4;}
 };
 public static class Options {public boolean inJustDecodeBounds;public int outWidth,outHeight,inSampleSize=1;}
 public static Bitmap decodeStream(java.io.InputStream input){return decodeStream(input,null,null);}
 public static Bitmap decodeStream(java.io.InputStream input,Object padding,Options options){try{
  String asset=options==null?AssetManager.editorAssetKey(input):null;
  if(asset!=null)synchronized(editorImages){
   Bitmap cached=editorImages.get(asset);
   if(cached!=null)return cached;
  }
  java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(input);if(image==null)return null;
  if(options!=null){
   options.outWidth=image.getWidth();options.outHeight=image.getHeight();
   if(options.inJustDecodeBounds)return null;
   if(options.inSampleSize>1){
    Bitmap sampled=new Bitmap(Math.max(1,image.getWidth()/options.inSampleSize),Math.max(1,image.getHeight()/options.inSampleSize));
    java.awt.Graphics2D g=sampled.image.createGraphics();
    g.drawImage(image,0,0,sampled.getWidth(),sampled.getHeight(),null);g.dispose();return sampled;
   }
  }
  Bitmap bitmap=new Bitmap(image.getWidth(),image.getHeight());bitmap.image=image;
  if(asset!=null&&(long)image.getWidth()*image.getHeight()<=4000000)synchronized(editorImages){
   editorImages.put(asset,bitmap);
  }
  return bitmap;
 }catch(Exception error){return null;}}
}''',

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
    javax.swing.AbstractButton b=spec.multi?new javax.swing.JCheckBox(spec.items[i],spec.checked[i]):spec.single?new javax.swing.JRadioButton(spec.items[i],i==spec.selected):new javax.swing.JButton(spec.items[i]);
    if(spec.single)group.add(b);b.addActionListener(e->choose(index));list.add(b);
   }javax.swing.JScrollPane scroll=new javax.swing.JScrollPane(list);scroll.setPreferredSize(new java.awt.Dimension(450,Math.min(320,spec.items.length*35+12)));root.add(scroll);
  }
  javax.swing.JPanel actions=new javax.swing.JPanel();
  if(spec.negative!=null){javax.swing.JButton b=new javax.swing.JButton(spec.negative);b.addActionListener(e->negative());actions.add(b);}
  if(spec.positive!=null){javax.swing.JButton b=new javax.swing.JButton(spec.positive);b.addActionListener(e->positive());actions.add(b);}
  root.add(actions);window.setContentPane(root);window.pack();window.setLocationRelativeTo(spec.activity.window);window.setVisible(true);
 }
 public void choose(int i){if(i<0||i>=spec.items.length)throw new IllegalArgumentException();if(spec.multi){spec.checked[i]=!spec.checked[i];if(spec.multiListener!=null)spec.multiListener.onClick(this,i,spec.checked[i]);return;}if(!spec.single)dismiss();if(spec.itemListener!=null)spec.itemListener.onClick(this,i);}
 public void positive(){if(spec.positiveListener!=null)spec.positiveListener.onClick(this,-1);dismiss();}
 public void negative(){if(spec.negativeListener!=null)spec.negativeListener.onClick(this,-2);dismiss();}
 public void cancel(){if(cancel!=null)cancel.onCancel(this);dismiss();}
 public void dismiss(){if(dismissed)return;dismissed=true;if(last==this)last=null;if(window!=null)window.dispose();if(onDismiss!=null)onDismiss.onDismiss(this);}
 public static class Builder {
  public final Activity activity;public String title="",message,positive,negative;public String[] items;public boolean single,multi;public boolean[] checked;public DialogInterface.OnMultiChoiceClickListener multiListener;public int selected;
  public android.view.View content;public DialogInterface.OnClickListener itemListener,positiveListener,negativeListener;
  public Builder(android.content.Context c){activity=(Activity)c;}
  public Builder setTitle(CharSequence s){title=s.toString();return this;}public Builder setMessage(CharSequence s){message=s.toString();return this;}
  public Builder setItems(String[] values,DialogInterface.OnClickListener l){items=values;itemListener=l;return this;}
  public Builder setSingleChoiceItems(String[] values,int n,DialogInterface.OnClickListener l){items=values;selected=n;itemListener=l;single=true;return this;}
  public Builder setMultiChoiceItems(String[] values,boolean[] valuesChecked,DialogInterface.OnMultiChoiceClickListener l){items=values;checked=valuesChecked.clone();multiListener=l;multi=true;return this;}
  public Builder setPositiveButton(CharSequence s,DialogInterface.OnClickListener l){positive=s.toString();positiveListener=l;return this;}
  public Builder setNegativeButton(CharSequence s,DialogInterface.OnClickListener l){negative=s.toString();negativeListener=l;return this;}
  public Builder setView(android.view.View v){content=v;return this;}
  public AlertDialog create(){return new AlertDialog(this);}public AlertDialog show(){AlertDialog d=create();d.show();return d;}
 }
}''',
    'DashboardDesktop.java': '''import java.lang.reflect.*;
import com.veloster.dashboard.theme.ThemeCatalog;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSettingsStore;
import javax.swing.*;
import android.app.*;
import android.view.*;
import com.veloster.dashboard.view.DashboardView;
import com.veloster.dashboard.vehicle.Telemetry;
import com.veloster.dashboard.debugger.JsDiagState;
import com.veloster.i18n.LanguageSettings;
import com.veloster.i18n.UiText;

public class DashboardDesktop {
 static LayoutEditorUi layoutEditorUi;
 static Object field(Object target,String name)throws Exception{Field f=(target instanceof Class?(Class<?>)target:target.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);}
 static void copy(Object from,Object to)throws Exception{for(Field f:from.getClass().getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);f.set(to,f.get(from));}}
 static void fixture(String state,String vehicle)throws Exception{
  android.os.SystemClock.now=state.equals("expired")?62000:2000;
  copy(JsDiagnosticsPreview.fixture(state,vehicle),field(DashboardView.class,"DATA"));
  copy(JsDiagnosticsPreview.observations(state),field(DashboardView.class,"DIAG"));
 }
 static void mediaFixture(DashboardView view,String state,PreviewProfile profile)throws Exception{
  if(!profile.id.equals("cn7"))return;
  com.veloster.dashboard.vehicle.MediaState media=(com.veloster.dashboard.vehicle.MediaState)field(view,"mediaState");
  media.mode(-1);
  if(!state.equals("received"))return;
  media.mode(0x73);
  media.update(3,true,"Midnight Drive",true,"Neon Avenue",true,79000,true,222000,false,1000);
  media.playback(3,1);
  media.artwork(3,true,"preview:cover",null,false);
 }
 static DashboardView create(Activity a)throws Exception{return create(a,PreviewProfile.ALL[0]);}
 static DashboardView create(Activity a,PreviewProfile profile)throws Exception{
  a.content.removeAll();DashboardView.attachStandalone(a);
  DashboardView view=(DashboardView)a.content.getComponent(0);view.setSize(profile.width,profile.height);return view;
 }
 static void previewMenuTheme(DashboardView view)throws Exception{
  for(String name:new String[]{"menuPosition","menuDock"}){
   Field setting=DashboardView.class.getDeclaredField(name);setting.setAccessible(true);
   setting.setInt(view,name.equals("menuPosition")?com.veloster.dashboard.theme.ThemeSpec.MENU_THEME:com.veloster.dashboard.theme.ThemeSpec.DOCK_THEME);
  }
  Method apply=DashboardView.class.getDeclaredMethod("applyTheme",com.veloster.dashboard.theme.ThemeSpec.class);apply.setAccessible(true);
  apply.invoke(view,field(view,"selectedTheme"));
 }
 static String refreshEditorTheme(Activity activity, DashboardView view, ThemeCatalog catalog,
                                  String themeId, boolean followTheme, String role)throws Exception{
  ThemeSettingsStore store=new ThemeSettingsStore(activity);
  ThemeCatalog currentCatalog=(ThemeCatalog)field(view,"themeCatalog");
  if(catalog==null)catalog=currentCatalog;
  int position=followTheme?ThemeSpec.MENU_THEME:store.menuPosition();
  int dock=followTheme?ThemeSpec.DOCK_THEME:store.menuDock();
  ThemeSpec selected=(ThemeSpec)field(view,"selectedTheme");
  if(catalog!=currentCatalog||!themeId.equals(selected.id)
          ||position!=(Integer)field(view,"menuPosition")||dock!=(Integer)field(view,"menuDock")){
   Method apply=DashboardView.class.getDeclaredMethod("reloadTheme",ThemeCatalog.class,String.class,int.class,int.class);
   apply.setAccessible(true);
   apply.invoke(view,catalog,themeId,position,dock);
  }
  editorPage(view,role);
  return editorCanvasStatus(view);
 }
 static void editorPage(DashboardView view,String role)throws Exception{
  int index=java.util.Arrays.asList("overview","f1","gauges","graphs","performance").indexOf(role);
  if(index>=0){
   Method select=DashboardView.class.getDeclaredMethod("selectMenuTarget",int.class);select.setAccessible(true);select.invoke(view,index);
  }else if(role.equals("settings"))openSettingsRoot(view);
  else if(role.equals("diagnostics"))openSettingsPage(view,(Integer)field(com.veloster.dashboard.view.SettingsPageRenderer.class,"DIAGNOSTICS"));
 }
 static void openThemeSettings(DashboardView view)throws Exception{
  openSettingsPage(view,(Integer)field(com.veloster.dashboard.view.SettingsPageRenderer.class,"THEME_SETTINGS"));
 }
 static void openSettingsRoot(DashboardView view)throws Exception{
  Field visible=DashboardView.class.getDeclaredField("settingsVisible");visible.setAccessible(true);visible.setBoolean(view,true);
  Method root=DashboardView.class.getDeclaredMethod("settingsRoot");root.setAccessible(true);root.invoke(view);view.invalidate();
 }
 static String editorCanvasStatus(DashboardView view)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec displayed=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  com.veloster.dashboard.theme.ThemeSpec.Shell active=displayed.shell();
  com.veloster.dashboard.theme.ThemeSpec.Shell base=((com.veloster.dashboard.theme.ThemeSpec)field(view,"selectedTheme")).shell();
  return displayed.displayName+"  ·  "+view.getWidth()+" × "+view.getHeight()+"  ·  "+(active.left()?"왼쪽 메뉴 "+active.width+" px":"상단 메뉴")
         +((active.position.equals(base.position)&&active.dock()==base.dock())?"":"  ·  앱 메뉴 설정 적용 중");
 }
 static void waitForTimers(){java.awt.SecondaryLoop loop=java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();javax.swing.Timer timer=new javax.swing.Timer(700,e->loop.exit());timer.setRepeats(false);timer.start();loop.enter();}
 static void touch(DashboardView view,int action,float x,float y){int id=action==0?java.awt.event.MouseEvent.MOUSE_PRESSED:action==1?java.awt.event.MouseEvent.MOUSE_RELEASED:java.awt.event.MouseEvent.MOUSE_DRAGGED;view.dispatchEvent(new java.awt.event.MouseEvent(view,id,System.currentTimeMillis(),0,(int)x,(int)y,1,false));}
 static void tap(DashboardView view,float x,float y){touch(view,0,x,y);touch(view,1,x,y);}
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static android.graphics.Canvas draw(DashboardView view){android.graphics.Canvas c=new android.graphics.Canvas(view.getWidth(),view.getHeight());view.draw(c);return c;}
 static final class Screen extends JPanel {
  private DashboardView view;
  private boolean pressed;
  Screen(DashboardView view){
   setView(view);setPreferredSize(new java.awt.Dimension(800,480));
   setBackground(new java.awt.Color(0xe9edf1));
   java.awt.event.MouseAdapter input=new java.awt.event.MouseAdapter(){
    public void mousePressed(java.awt.event.MouseEvent e){send(e,MotionEvent.ACTION_DOWN);}
    public void mouseDragged(java.awt.event.MouseEvent e){send(e,MotionEvent.ACTION_MOVE);}
    public void mouseReleased(java.awt.event.MouseEvent e){send(e,MotionEvent.ACTION_UP);}
    public void mouseWheelMoved(java.awt.event.MouseWheelEvent e){
     java.awt.Rectangle b=displayBounds();if(!b.contains(e.getPoint()))return;
     view.onGenericMotionEvent(new MotionEvent(MotionEvent.ACTION_SCROLL,
       (e.getX()-b.x)*(float)view.getWidth()/b.width,(e.getY()-b.y)*(float)view.getHeight()/b.height,-(float)e.getPreciseWheelRotation()));
     repaint();
    }
   };addMouseListener(input);addMouseMotionListener(input);addMouseWheelListener(input);
  }
  void setView(DashboardView view){this.view=view;pressed=false;repaint();}
  java.awt.Rectangle displayBounds(){
   double scale=Math.min((double)getWidth()/view.getWidth(),(double)getHeight()/view.getHeight());
   int w=(int)Math.round(view.getWidth()*scale),h=(int)Math.round(view.getHeight()*scale);
   return new java.awt.Rectangle((getWidth()-w)/2,(getHeight()-h)/2,w,h);
  }
  private void send(java.awt.event.MouseEvent e,int action){
   java.awt.Rectangle bounds=displayBounds();
   if(action==MotionEvent.ACTION_DOWN)pressed=bounds.contains(e.getPoint());
   if(!pressed||bounds.isEmpty())return;
   if(action==MotionEvent.ACTION_UP)pressed=false;
   view.onTouchEvent(new MotionEvent(action,(e.getX()-bounds.x)*(float)view.getWidth()/bounds.width,
            (e.getY()-bounds.y)*(float)view.getHeight()/bounds.height));
   repaint();
  }
  @Override protected void paintComponent(java.awt.Graphics graphics){
   super.paintComponent(graphics);
   java.awt.Rectangle bounds=displayBounds();
   if(bounds.isEmpty())return;
   java.awt.Graphics2D g=(java.awt.Graphics2D)graphics.create();
   g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
   g.drawImage(draw(view).image(),bounds.x,bounds.y,bounds.width,bounds.height,null);g.dispose();
  }
 }
 static void invoke(DashboardView view,String name){try{Method method=DashboardView.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(view);}catch(Exception e){throw new RuntimeException(e);}}
 static void openSettingsPage(DashboardView view,int screen){try{Field visible=DashboardView.class.getDeclaredField("settingsVisible");visible.setAccessible(true);visible.setBoolean(view,true);Method method=DashboardView.class.getDeclaredMethod("openSettingsPage",int.class);method.setAccessible(true);method.invoke(view,screen);}catch(Exception e){throw new RuntimeException(e);}}
 static com.veloster.dashboard.view.DashboardPage.Regions viewRegions(DashboardView view)throws Exception{
  Method method=DashboardView.class.getDeclaredMethod("displayRegions");method.setAccessible(true);
  return (com.veloster.dashboard.view.DashboardPage.Regions)method.invoke(view);
 }
 static com.veloster.dashboard.view.SettingsPageRenderer.Layout settingsLayout(DashboardView view)throws Exception{
  com.veloster.dashboard.theme.ThemeSpec t=(com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme");
  com.veloster.dashboard.view.DashboardPage.Regions r=viewRegions(view);
  com.veloster.dashboard.vehicle.MediaState media=(com.veloster.dashboard.vehicle.MediaState)field(view,"mediaState");
  boolean footer=com.veloster.dashboard.view.MediaBar.footerVisible(r,(Integer)field(view,"mediaBarPolicy"),media.snapshot(android.os.SystemClock.elapsedRealtime()));
  com.veloster.dashboard.view.DashboardPage.Content c=new com.veloster.dashboard.view.DashboardPage.Content(t,"settings",r,footer);
  return new com.veloster.dashboard.view.SettingsPageRenderer.Layout(t,c,(com.veloster.dashboard.view.SettingsPageRenderer.Editor)field(view,"settingsEditor"));
 }
 static void tapLogical(DashboardView view,float x,float y)throws Exception{
  com.veloster.dashboard.view.DashboardPage.Regions r=viewRegions(view);
  tap(view,r.left+x*r.scale,r.top+y*r.scale);
 }
 static void tapSettings(DashboardView view,float x,float y)throws Exception{
  Object l=settingsLayout(view);
  String part=y<109?(x<170?"back":x<640?"previewButton":"apply"):y>=133?"viewport":"preview";
  com.veloster.dashboard.view.DashboardPage.Bounds b=(com.veloster.dashboard.view.DashboardPage.Bounds)field(l,part);
  if(part.equals("viewport")&&settingsScreen(view)!=14){
   int index=Math.max(0,(int)((y-133)/50));
   if(index<((String[])field(field(view,"settingsEditor"),"labels")).length){
    java.lang.reflect.Method row=l.getClass().getDeclaredMethod("rowBounds",int.class);row.setAccessible(true);
    com.veloster.dashboard.view.DashboardPage.Bounds item=(com.veloster.dashboard.view.DashboardPage.Bounds)row.invoke(l,index);
    tapLogical(view,b.atX(.25f),item.atY(.5f));
   }else tapLogical(view,b.atX(.25f),b.atY(.5f));
  }
  else tapLogical(view,b.atX(.5f),b.atY(.5f));
 }
 static int settingsScreen(DashboardView view)throws Exception{return (Integer)field(field(view,"settingsEditor"),"screen");}
 static com.veloster.dashboard.view.DashboardPage.Bounds settingsItemBounds(DashboardView view,Object layout,int index)throws Exception{
  int local=index-(Integer)field(layout,"offset");
  if(settingsScreen(view)==14){
   java.lang.reflect.Method tile=layout.getClass().getDeclaredMethod("wallpaperTile",int.class);tile.setAccessible(true);
   return (com.veloster.dashboard.view.DashboardPage.Bounds)tile.invoke(layout,index);
  }
  java.lang.reflect.Method row=layout.getClass().getDeclaredMethod("rowBounds",int.class);row.setAccessible(true);
  return (com.veloster.dashboard.view.DashboardPage.Bounds)row.invoke(layout,index);
 }
 static void dragSettingsPage(DashboardView view,float start,float end)throws Exception{
  com.veloster.dashboard.view.DashboardPage.Bounds b=(com.veloster.dashboard.view.DashboardPage.Bounds)field(settingsLayout(view),"viewport");
  com.veloster.dashboard.view.DashboardPage.Regions r=viewRegions(view);
  float x=r.left+b.atX(.25f)*r.scale,y=r.top+b.atY(start)*r.scale,to=r.top+b.atY(end)*r.scale;
  touch(view,0,x,y);touch(view,2,x,to);touch(view,1,x,to);
 }
 static void tapEditorIndex(DashboardView view,int index)throws Exception{
  for(int attempt=0;attempt<30;attempt++){
   Object layout=settingsLayout(view);
   int offset=(Integer)field(layout,"offset"),count=(Integer)field(layout,"count");
   if(index>=offset&&index<offset+count){
    com.veloster.dashboard.view.DashboardPage.Bounds list=(com.veloster.dashboard.view.DashboardPage.Bounds)field(layout,"viewport");
    com.veloster.dashboard.view.DashboardPage.Bounds item=settingsItemBounds(view,layout,index);
    if(item.atY(.5f)>=list.y&&item.atY(.5f)<list.bottom()){
     tapLogical(view,item.atX(settingsScreen(view)==14?.5f:.25f),item.atY(.5f));return;
    }
   }
   boolean up=index<offset||(index<offset+count&&settingsItemBounds(view,layout,index).atY(.5f)<((com.veloster.dashboard.view.DashboardPage.Bounds)field(layout,"viewport")).y);
   float before=(Float)field(layout,"pageScrollY");
   dragSettingsPage(view,up?.2f:.8f,up?.8f:.2f);
   check((Float)field(settingsLayout(view),"pageScrollY")!=before,"settings index reachable by scrolling: "+index);
  }
  throw new AssertionError("too many scroll gestures to reach settings index "+index);
 }
 static void tapEditorLabel(DashboardView view,String label)throws Exception{
  String[] labels=(String[])field(field(view,"settingsEditor"),"labels");int index=-1;
  for(int i=0;i<labels.length;i++)if(labels[i].equals(label)){index=i;break;}
  check(index>=0,"settings option exists: "+label);tapEditorIndex(view,index);
 }
 static void tapDrawnLabel(DashboardView view,String label){
  for(android.graphics.Canvas.TextDraw t:draw(view).commands)if(t.text.equals(label)){
   tap(view,(float)(t.deviceX+t.width*t.scaleX/2),(float)(t.deviceY-t.size*t.scaleY/3));return;
  }
  throw new AssertionError("missing label: "+label);
 }
 static void capture(JFrame owner,DashboardView view){DashboardPreviewUi.showCapture(owner,draw(view).image());}
 static void button(java.awt.Container root,String label){for(java.awt.Component child:root.getComponents()){
  if(child instanceof android.widget.Button&&((android.widget.Button)child).button.getText().equals(label)){((android.widget.Button)child).performClick();return;}
  if(child instanceof java.awt.Container){try{button((java.awt.Container)child,label);return;}catch(IllegalArgumentException ignored){}}
 }throw new IllegalArgumentException(label);}
 static ThemeCatalog validateEditorCatalog(Activity activity,String themeId,String themeIds){
  ThemeCatalog catalog=ThemeCatalog.load(activity);
  if(!catalog.all().keySet().equals(new java.util.HashSet<String>(java.util.Arrays.asList(themeIds.split(","))))
      ||catalog.find(themeId)==null)
   throw new IllegalArgumentException("Renderer rejected the theme catalog");
  return catalog;
 }
 static void selfCheck()throws Exception{
  com.veloster.dashboard.view.DashboardPage.Regions standard=com.veloster.dashboard.view.DashboardPage.regions(800,480);
  com.veloster.dashboard.view.DashboardPage.Regions wide=com.veloster.dashboard.view.DashboardPage.regions(1920,720);
  check(!standard.mediaEnabled&&standard.content.width==800,"standard display has no separate media region");
  check(wide.mediaEnabled&&Math.abs(wide.content.width-1920*5f/7f)<.01f&&Math.abs(wide.media.width-1920*2f/7f)<.01f,
        "wide display splits content and media 5:2");
  check(wide.media.x+wide.media.width==1920&&wide.topMenu.width==1920&&wide.content.y==wide.topMenu.height&&wide.media.y==wide.topMenu.height,
        "wide top menu spans display and sits above content/media regions");
  com.veloster.dashboard.view.DashboardPage.Content wideF1=new com.veloster.dashboard.view.DashboardPage.Content(com.veloster.dashboard.theme.ThemeSpec.original(),"f1",wide.logicalContentWidth,wide.logicalHeight);
  check(wideF1.width>800&&Math.abs(wideF1.width*wide.scale-wide.content.width)<.1f,
        "responsive F1 content uses the full allocated content width");
  com.veloster.dashboard.theme.ThemeSpec leftMenu=com.veloster.dashboard.theme.ThemeCatalog.withMenuPosition(com.veloster.dashboard.theme.ThemeSpec.original(),com.veloster.dashboard.theme.ThemeSpec.MENU_LEFT);
  com.veloster.dashboard.view.DashboardPage.Regions leftWide=com.veloster.dashboard.view.DashboardPage.regions(1920,720,leftMenu);
  check(leftWide.topMenu.width==0&&leftWide.content.y==0&&leftWide.media.y==0,
        "left menu hides the top-menu region and lets content/media start at the screen top");
  check(com.veloster.dashboard.view.MediaBar.regionHit(wide.media,1534,654)==com.veloster.dashboard.view.MediaBar.PREVIOUS&&
        com.veloster.dashboard.view.MediaBar.regionHit(wide.media,1642,654)==com.veloster.dashboard.view.MediaBar.PLAY&&
        com.veloster.dashboard.view.MediaBar.regionHit(wide.media,1750,654)==com.veloster.dashboard.view.MediaBar.NEXT&&
        com.veloster.dashboard.view.MediaBar.regionHit(wide.media,wide.media.atX(.5f)-54,654)==com.veloster.dashboard.view.MediaBar.NONE&&
        com.veloster.dashboard.view.MediaBar.regionHit(wide.media,wide.media.atX(.5f)+54,654)==com.veloster.dashboard.view.MediaBar.NONE&&
        com.veloster.dashboard.view.MediaBar.regionHit(wide.media,1642,530)==com.veloster.dashboard.view.MediaBar.NONE,
        "wide media transport hit regions exclude gaps and metadata");
  fixture("received","js");Activity wideActivity=new Activity();LanguageSettings.save(wideActivity,"ko");
  DashboardView wideView=create(wideActivity);wideView.setSize(1920,720);
  ((com.veloster.dashboard.view.DashboardPage.State)field(wideView,"pageState")).restore(com.veloster.dashboard.view.DashboardPage.F1);
  android.graphics.Canvas wideFrame=draw(wideView);boolean mediaText=false,contentText=false;
  for(android.graphics.Canvas.TextDraw command:wideFrame.commands){
   if(command.deviceX>=wide.media.x&&command.text.equals(UiText.t("미디어 데이터 없음")))mediaText=true;
   if(command.deviceX<wide.media.x&&command.text.equals("F1"))contentText=true;
  }
  check(mediaText&&contentText,"wide renderer restores display coordinates before drawing the separate right media area");
  check(wideFrame.commands.stream().noneMatch(command->command.deviceX<wide.media.x&&command.text.equals("♪")),
        "wide F1 page does not duplicate its bottom media bar");
  fixture("received","js");Activity a=new Activity();LanguageSettings.save(a,"ko");DashboardView view=create(a,PreviewProfile.ALL[0]);
  java.util.List<String> overview=draw(view).texts;
  tap(view,180,20);tap(view,180,20);check(overview.equals(draw(view).texts),"repeated overview taps stay on overview");
  tap(view,560,20);tap(view,180,20);check(overview.equals(draw(view).texts),"overview opens directly from settings");
  touch(view,0,60,20);touch(view,2,280,20);touch(view,1,280,20);
  check(overview.equals(draw(view).texts),"drag must not switch pages");
  tap(view,60,20);check(draw(view).texts.contains("PERFORMANCE"),"real Java performance navigation");
  tap(view,560,20);check(settingsScreen(view)==-1&&draw(view).texts.contains(UiText.t("언어")),"real Java settings root page");
  tapEditorLabel(view,UiText.t("진단"));
  android.graphics.Canvas diag=draw(view);
  check(diag.texts.contains(UiText.t("HUD · 내비"))&&diag.texts.contains(UiText.t("전체 조망")),"settings opens diagnostics and overview keeps its label");
  tapDrawnLabel(view,UiText.t("차량"));
  check(!draw(view).texts.contains(UiText.t("변속기 설정")),"diagnostics no longer owns transmission settings");
  tap(view,560,20);check(settingsScreen(view)==-1&&draw(view).texts.contains(UiText.t("차량 설정")),"settings button returns from diagnostics to settings root");
  tapEditorLabel(view,UiText.t("차량 설정"));
  check(settingsScreen(view)==40&&draw(view).texts.contains(UiText.t("차량 프로필"))&&draw(view).texts.contains("JS"),"vehicle settings shows profile details as menu rows");
  tapEditorLabel(view,UiText.t("변속기 설정"));
  check(settingsScreen(view)==26,"vehicle settings opens transmission editor");
  tapSettings(view,70,88);
  check(settingsScreen(view)==40&&draw(view).texts.contains(UiText.t("차량 프로필")),"transmission editor returns to vehicle settings");
  tapSettings(view,70,88);check(settingsScreen(view)==-1&&draw(view).texts.contains(UiText.t("진단")),"vehicle settings back returns to settings root");
  tap(view,560,20);check(draw(view).texts.contains("PERFORMANCE"),"closing settings returns to the original dashboard page");
  DashboardView reopened=create(a);check(draw(reopened).texts.contains("PERFORMANCE"),"diagnostics does not replace the last saved dashboard page");
  tap(view,560,20);
  tapEditorLabel(view,UiText.t("페이지 설정"));check(settingsScreen(view)==31,"root page row opens page settings");tapSettings(view,70,88);
  tapEditorLabel(view,UiText.t("UI 설정"));check(settingsScreen(view)==9,"root UI row opens UI settings");
  tapEditorLabel(view,UiText.t("테마 설정"));check(settingsScreen(view)==32,"UI theme row opens theme settings");
  tapEditorLabel(view,UiText.t("테마"));check(settingsScreen(view)==1,"theme settings opens theme editor");
  tapEditorLabel(view,"Aurora Focus");tapSettings(view,70,88);
  check(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","original").equals("original"),"theme back cancels draft selection");
  tapEditorLabel(view,UiText.t("테마"));tapEditorLabel(view,"Aurora Focus");tapSettings(view,700,88);
  check(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","").equals("aurora_focus"),"theme apply saves selected theme");
  check(settingsScreen(view)==32&&draw(view).texts.contains("Aurora Focus"),"applied theme returns to updated theme settings");
  tapEditorLabel(view,UiText.t("테마"));tapEditorLabel(view,"Original");tapSettings(view,700,88);
  check(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","").equals("original"),"theme editor saves restored default");
  tapSettings(view,70,88);tapSettings(view,70,88);
  tapEditorLabel(view,UiText.t("언어"));check(settingsScreen(view)==0,"root Language row opens language editor");
  tapEditorLabel(view,"English");tapSettings(view,70,88);
  check(LanguageSettings.selection(a).equals("ko"),"language back cancels draft selection");
  tapEditorLabel(view,UiText.t("언어"));tapEditorLabel(view,"English");tapSettings(view,700,88);
  check(LanguageSettings.selection(a).equals("en"),"language apply saves selected language");
  tapEditorLabel(view,UiText.t("언어"));tapEditorLabel(view,"한국어");tapSettings(view,700,88);
  check(LanguageSettings.selection(a).equals("ko"),"language editor saves restored language");
  tapSettings(view,70,88);check(!(Boolean)field(view,"settingsVisible"),"settings root back returns to dashboard");
  tap(view,360,20);Object layout=field(view,"gaugeLayout");
  boolean before=((com.veloster.dashboard.view.gauge.GaugeLayout)layout).dense();tap(view,700,70);
  check(((com.veloster.dashboard.view.gauge.GaugeLayout)layout).dense()!=before,"real gauge grid toggle");
  touch(view,0,100,160);waitForTimers();check(settingsScreen(view)==20,"actual 600ms hold opens gauge settings page");int editedSlot=(Integer)field(view,"editingGaugeSlot");
  tapEditorLabel(view,UiText.t("게이지 모양"));check(settingsScreen(view)==22,"gauge style row opens shared canvas page");tapEditorLabel(view,"숫자");tapSettings(view,700,88);
  check(((com.veloster.dashboard.view.gauge.GaugeLayout)layout).style(editedSlot)==2,"gauge style applies from canvas page: slot="+editedSlot+" actual="+((com.veloster.dashboard.view.gauge.GaugeLayout)layout).style(editedSlot));touch(view,1,100,160);
  touch(view,0,100,160);touch(view,2,140,160);waitForTimers();check(AlertDialog.last==null,"drag cancels the hold timer");touch(view,1,140,160);
  tap(view,560,20);before=((com.veloster.dashboard.view.gauge.GaugeLayout)layout).dense();tap(view,700,70);
  check(((com.veloster.dashboard.view.gauge.GaugeLayout)layout).dense()==before,"settings does not leak touches into gauges");
  view.setSize(400,240);tap(view,30,10);check(draw(view).texts.contains("PERFORMANCE"),"actual view coordinate scaling");
  for(PreviewProfile profile:PreviewProfile.ALL){
   fixture("received",profile.fixture);DashboardView other=create(new Activity(),profile);
   java.awt.image.BufferedImage nativeFrame=draw(other).image();
   check(nativeFrame.getWidth()==profile.width&&nativeFrame.getHeight()==profile.height,"native frame "+profile.id);
   Screen screen=new Screen(other);screen.setSize(800,480);
   java.awt.Rectangle bounds=screen.displayBounds();
   float scale=com.veloster.dashboard.view.DashboardPage.screenScale(profile.width,profile.height);
   float left=com.veloster.dashboard.view.DashboardPage.screenLeft(profile.width,profile.height);
   float top=com.veloster.dashboard.view.DashboardPage.screenTop(profile.width,profile.height);
   com.veloster.dashboard.theme.ThemeSpec screenTheme=(com.veloster.dashboard.theme.ThemeSpec)field(other,"activeTheme");
   com.veloster.dashboard.view.DashboardPage.Menu screenMenu=(com.veloster.dashboard.view.DashboardPage.Menu)field(other,"pageMenu");
   com.veloster.dashboard.view.DashboardPage.Regions screenRegions=com.veloster.dashboard.view.DashboardPage.regions(profile.width,profile.height,screenTheme);
   com.veloster.dashboard.view.DashboardPage.Bounds settingsBounds=com.veloster.dashboard.view.DashboardPage.navigationBounds(screenTheme,screenMenu,com.veloster.dashboard.view.DashboardPage.SETTINGS,screenRegions.logicalDisplayWidth);
   int x=bounds.x+Math.round((screenRegions.left+(settingsBounds.x+settingsBounds.width/2)*screenRegions.scale)*bounds.width/profile.width);
   int y=bounds.y+Math.round((screenRegions.top+(settingsBounds.y+settingsBounds.height/2)*screenRegions.scale)*bounds.height/profile.height);
   for(int event:new int[]{java.awt.event.MouseEvent.MOUSE_PRESSED,java.awt.event.MouseEvent.MOUSE_RELEASED})
    screen.dispatchEvent(new java.awt.event.MouseEvent(screen,event,0,0,x,y,1,false));
   check(draw(other).texts.contains(UiText.t("언어")),"profile settings through fitted screen "+profile.id);
  }
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
  openSettingsPage(view,0);check(settingsScreen(view)==0&&AlertDialog.last==null,"desktop language tool opens canvas editor");tapSettings(view,70,88);
  openSettingsPage(view,1);check(settingsScreen(view)==1&&AlertDialog.last==null,"desktop theme tool opens canvas editor");
  tapEditorLabel(view,"Aurora Focus");tapSettings(view,700,88);
  check(a.getSharedPreferences("dashboard_theme",0).getString("theme_id","").equals("aurora_focus"),"desktop theme tool uses canvas apply callback");
  System.out.println("PASS actual DashboardView: navigation, drag rejection, settings, language save, theme cancel/apply, gauge toggle, long press, canvas editor, input isolation, scaled input and "+PreviewProfile.ALL.length+" vehicle presets at native resolutions");
 }
 public static void main(String[] args)throws Exception{
  if(args.length==3&&args[0].equals("--inspect-theme")){
   System.out.println(LayoutEditorUi.inspect(validateEditorCatalog(new Activity(),args[1],args[2]).find(args[1])));
   return;
  }
  if(args.length>0&&(args[0].equals("--prefs-write")||args[0].equals("--prefs-read"))){
   SwingUtilities.invokeAndWait(()->{try{persistenceCheck(args[0]);}catch(Exception e){throw new RuntimeException(e);}});return;
  }
  if(args.length>0&&args[0].equals("--check")){SwingUtilities.invokeAndWait(()->{try{selfCheck();}catch(Exception e){throw new RuntimeException(e);}});return;}
  DashboardPreviewUi.installLookAndFeel();
  SwingUtilities.invokeLater(()->{try{
   Activity activity=new Activity();LanguageSettings.init(activity);
   boolean editorMode=Boolean.parseBoolean(System.getProperty("dashboard.editor","false"));
   JFrame frame=new JFrame(editorMode?"레이아웃 스튜디오":"JS Dashboard Preview");activity.window=frame;frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
   PreviewProfile[] vehicles=PreviewProfile.ALL;
   String[] sampleStates={"received","waiting","invalid","held","expired"};
   android.content.SharedPreferences desktopPrefs=activity.getSharedPreferences("desktop_preview",0);
   String savedVehicle=desktopPrefs.getString("vehicle",vehicles[0].id);int selectedVehicle=0;
   for(int i=0;i<vehicles.length;i++)if(vehicles[i].id.equals(savedVehicle))selectedVehicle=i;
   String savedState=desktopPrefs.getString("sample_state","received");int selectedState=0;
   for(int i=0;i<sampleStates.length;i++)if(sampleStates[i].equals(savedState))selectedState=i;
   JComboBox<String> car=new JComboBox<String>(java.util.Arrays.stream(vehicles).map(p->p.name).toArray(String[]::new));car.setSelectedIndex(selectedVehicle);
   JComboBox<String> state=new JComboBox<String>(sampleStates);state.setSelectedIndex(selectedState);
   fixture(sampleStates[selectedState],vehicles[selectedVehicle].fixture);
   final DashboardView[] current={create(activity,vehicles[selectedVehicle])};
   mediaFixture(current[0],sampleStates[selectedState],vehicles[selectedVehicle]);Screen screen=new Screen(current[0]);
   JButton language=DashboardPreviewUi.button("한국어 ▾",false,()->{}),theme=DashboardPreviewUi.button("Original ▾",false,()->{});
   language.setPreferredSize(new java.awt.Dimension(140,34));theme.setPreferredSize(new java.awt.Dimension(220,34));
   language.setToolTipText("대시보드 언어 변경");theme.setToolTipText("대시보드 테마 선택 및 미리보기");
   Runnable sync=()->{try{
    String selected=LanguageSettings.selection(activity);
    language.setText(("auto".equals(selected)?UiText.t("시스템 설정 따름"):"ko".equals(selected)?"한국어":"English")+" ▾");
    theme.setText(((com.veloster.dashboard.theme.ThemeSpec)field(current[0],"activeTheme")).displayName+" ▾");
   }catch(Exception e){throw new RuntimeException(e);}};
   language.addActionListener(e->{openSettingsPage(current[0],0);sync.run();screen.repaint();});
   theme.addActionListener(e->{openSettingsPage(current[0],1);sync.run();screen.repaint();});
   Runnable data=()->{try{
    PreviewProfile profile=vehicles[car.getSelectedIndex()];String sample=(String)state.getSelectedItem();
    fixture(sample,profile.fixture);mediaFixture(current[0],sample,profile);screen.repaint();
   }catch(Exception e){throw new RuntimeException(e);}};
   car.setToolTipText(vehicles[selectedVehicle].display);
   car.addActionListener(e->{try{
    PreviewProfile profile=vehicles[car.getSelectedIndex()];current[0].removeCallbacks((Runnable)field(current[0],"editHold"));
    desktopPrefs.edit().putString("vehicle",profile.id).apply();
    String sample=(String)state.getSelectedItem();fixture(sample,profile.fixture);current[0]=create(activity,profile);
    if(editorMode&&layoutEditorUi!=null&&layoutEditorUi.followsTheme())previewMenuTheme(current[0]);
    mediaFixture(current[0],sample,profile);screen.setView(current[0]);car.setToolTipText(profile.display);sync.run();
   }catch(Exception error){throw new RuntimeException(error);}});
   state.addActionListener(e->{desktopPrefs.edit().putString("sample_state",(String)state.getSelectedItem()).apply();data.run();});
   JPanel editorRoot=null;
   final ThemeCatalog[] acceptedEditorCatalog={null};
   String themeIds=System.getProperty("dashboard.editor.themes","original,f1_2,aurora_focus,material_auto_cowork");
   if(editorMode){
    android.content.SharedPreferences editorPreferences=activity.getSharedPreferences("layout_editor_panels",0);
    String defaultEditorTheme=new com.veloster.dashboard.theme.ThemeSettingsStore(activity).selectedId();
    String initialEditorTheme=editorPreferences.getString("active_theme",defaultEditorTheme);
    String initialEditorRole=editorPreferences.getString("active_role","overview");
    java.util.List<String> availableThemes=java.util.Arrays.asList(themeIds.split(","));
    if(!availableThemes.contains(initialEditorTheme))initialEditorTheme=availableThemes.contains("f1_2")?"f1_2":availableThemes.get(0);
    if(!java.util.Arrays.asList("overview","f1","gauges","graphs","performance","settings","diagnostics","shell","colors","fonts","components").contains(initialEditorRole))initialEditorRole="overview";
    layoutEditorUi=new LayoutEditorUi(screen,car,state,()->language.doClick(),()->{
     try{openThemeSettings(current[0]);screen.repaint();}catch(Exception e){throw new RuntimeException(e);}
    },()->capture(frame,current[0]),DashboardDesktop::requestReload,
     editorPreferences,initialEditorTheme,initialEditorRole,(themeId,followTheme,previewRole)->{try{
     String detail=refreshEditorTheme(activity,current[0],acceptedEditorCatalog[0],themeId,followTheme,previewRole);
     screen.repaint();sync.run();
     return detail;
    }catch(Exception error){throw new RuntimeException(error);}});
    layoutEditorUi.setThemes(java.util.Arrays.asList(themeIds.split(",")),
        com.veloster.dashboard.theme.ThemeCatalog.load(activity).all());
    editorRoot=layoutEditorUi.panel();
    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new java.awt.event.WindowAdapter(){
     public void windowClosing(java.awt.event.WindowEvent event){layoutEditorUi.requestClose(frame::dispose);}
    });
   }else editorRoot=DashboardPreviewUi.shell(DashboardPreviewUi.contextBar(car,language,theme),screen,DashboardPreviewUi.actionBar(state,DashboardDesktop::requestReload,()->capture(frame,current[0])));
   JPanel root=editorRoot;
   root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("R"),"reload");
   root.getActionMap().put("reload",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent event){requestReload();}});
   frame.setContentPane(root);frame.pack();frame.setMinimumSize(editorMode?new java.awt.Dimension(1040,640):new java.awt.Dimension(720,480));frame.setLocationByPlatform(true);frame.setVisible(true);
   listenForReloadMessages(frame,current,activity,themeIds,acceptedEditorCatalog);
   if(editorMode)layoutEditorUi.requestCurrent();
   System.out.println("READY Dashboard Java touch runtime: showing="+frame.isShowing()+", canvas="+current[0].getWidth()+"x"+current[0].getHeight());
   javax.swing.Timer timer=new javax.swing.Timer(100,e->{screen.repaint();sync.run();
    if(editorMode)try{layoutEditorUi.setCanvasStatus(editorCanvasStatus(current[0]));}catch(Exception error){throw new RuntimeException(error);}
   });timer.start();
   frame.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){timer.stop();}});
  }catch(Exception error){error.printStackTrace();JOptionPane.showMessageDialog(null,error.toString());}});
 }
 static void requestReload(){System.out.println("DASHBOARD_PREVIEW_RELOAD");System.out.flush();}
 static String spotifyArtworkUrl="";
 static void spotifySourceLabel()throws Exception{
  java.lang.reflect.Field sources=com.veloster.dashboard.vehicle.MediaState.class.getDeclaredField("SOURCES");sources.setAccessible(true);
  ((String[])sources.get(null))[3]="Spotify";
 }
 static void listenForReloadMessages(JFrame frame,DashboardView[] current,Activity activity,
                                    String themeIds,ThemeCatalog[] acceptedCatalog){Thread listener=new Thread(()->{try{
  java.io.BufferedReader input=new java.io.BufferedReader(new java.io.InputStreamReader(System.in,"UTF-8"));
  ThemeCatalog candidate=null;
  for(String message;(message=input.readLine())!=null;){
   if(message.startsWith("LAYOUT_EDITOR_INSPECT\\t")&&layoutEditorUi!=null){
    String[] request=message.split("\\\\t",3);
    if(request.length!=3)continue;
    String result="error";
    candidate=null;
    try{
     ThemeCatalog catalog=validateEditorCatalog(activity,request[2],themeIds);
     result=LayoutEditorUi.inspect(catalog.find(request[2]));
     candidate=catalog;
    }catch(Exception error){error.printStackTrace();}
    System.out.println("LAYOUT_EDITOR_INSPECT_RESULT\\t"+request[1]+"\\t"+result);
    System.out.flush();
   }else if((message.startsWith("LAYOUT_EDITOR_DOC\\t")||message.startsWith("LAYOUT_EDITOR_STATUS\\t"))&&layoutEditorUi!=null){
    // Python can still reject and roll back a draft after inspection.
    if(candidate!=null&&message.startsWith("LAYOUT_EDITOR_DOC\\t")){
     ThemeCatalog validated=candidate;
     SwingUtilities.invokeLater(()->acceptedCatalog[0]=validated);
    }
    candidate=null;
    layoutEditorUi.handle(message);
   }else if(message.startsWith("RELOAD_FAILED\\t")){
    String detail=message.substring("RELOAD_FAILED\\t".length());SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,"Java 또는 테마 파일을 다시 컴파일하지 못했습니다. 현재 프리뷰는 이전 버전으로 유지합니다.\\n\\n"+detail,"새로고침 실패",JOptionPane.ERROR_MESSAGE));
   }else if(message.equals("SPOTIFY_STOP"))SwingUtilities.invokeLater(()->{try{
    com.veloster.dashboard.vehicle.MediaState media=(com.veloster.dashboard.vehicle.MediaState)field(current[0],"mediaState");
    media.mode(-1);spotifyArtworkUrl="";current[0].invalidate();
   }catch(Exception ignored){}
   });
   else if(message.startsWith("SPOTIFY\\t")){
    String[] v=message.split("\\\\t",-1);if(v.length!=7)continue;
    SwingUtilities.invokeLater(()->{try{
     int state=Integer.parseInt(v[1]);long position=Long.parseLong(v[2]),duration=Long.parseLong(v[3]);
     java.util.Base64.Decoder decoder=java.util.Base64.getDecoder();
     String title=new String(decoder.decode(v[4]),java.nio.charset.StandardCharsets.UTF_8);
     String artist=new String(decoder.decode(v[5]),java.nio.charset.StandardCharsets.UTF_8);
     String artwork=new String(decoder.decode(v[6]),java.nio.charset.StandardCharsets.UTF_8);
     com.veloster.dashboard.vehicle.MediaState media=(com.veloster.dashboard.vehicle.MediaState)field(current[0],"mediaState");
     spotifySourceLabel();media.mode(0x73);media.update(3,true,title,true,artist,true,position,true,duration,false,android.os.SystemClock.elapsedRealtime());
     media.playback(3,state);
     if(!artwork.equals(spotifyArtworkUrl)){spotifyArtworkUrl=artwork;media.artwork(3,true,artwork,null,false);}
     current[0].invalidate();
    }catch(Exception error){error.printStackTrace();}});
   }
  }
 }catch(Exception ignored){}},"dashboard-preview-control");listener.setDaemon(true);listener.start();}
 static void persistenceCheck(String mode)throws Exception{
  Activity activity=new Activity();
  if("--prefs-write".equals(mode)){
   LanguageSettings.init(activity);fixture("received","js");DashboardView view=create(activity,PreviewProfile.ALL[0]);
   openSettingsPage(view,1);tapEditorLabel(view,"Aurora Focus");tapSettings(view,700,88);
   openSettingsPage(view,0);tapEditorLabel(view,"English");tapSettings(view,700,88);
   ((com.veloster.dashboard.view.gauge.GaugeEditor)field(view,"gaugeEditor")).toggleGrid();
   System.out.println("PASS preferences written through dashboard editors");return;
  }
  LanguageSettings.init(activity);fixture("received","js");DashboardView view=create(activity,PreviewProfile.ALL[0]);
  check(LanguageSettings.selection(activity).equals("en"),"language restores after process restart");
  check(((com.veloster.dashboard.theme.ThemeSpec)field(view,"activeTheme")).id.equals("aurora_focus"),"theme restores after process restart");
  check(((com.veloster.dashboard.view.gauge.GaugeLayout)field(view,"gaugeLayout")).dense(),"gauge layout setting restores after process restart");
  System.out.println("PASS preferences restored in a fresh process: language, theme and gauge layout");
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
 private static final java.util.Map<String,Prefs> diskPreferences=new java.util.HashMap<String,Prefs>();
 private final java.util.Map<String,Prefs> memoryPreferences=new java.util.HashMap<String,Prefs>();
 public synchronized SharedPreferences getSharedPreferences(String n,int mode){
  String directory=System.getProperty("dashboard.preferences.dir");
  if(directory==null||directory.isEmpty()){
   if(!memoryPreferences.containsKey(n))memoryPreferences.put(n,new Prefs(null));return memoryPreferences.get(n);
  }
  java.nio.file.Path file=java.nio.file.Paths.get(directory).resolve(hash(n)+".properties");String key=file.toAbsolutePath().normalize().toString();
  synchronized(diskPreferences){if(!diskPreferences.containsKey(key))diskPreferences.put(key,new Prefs(file));return diskPreferences.get(key);}
 }
 private static String hash(String value){try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}
 static class Prefs implements SharedPreferences {
  private final java.nio.file.Path file;private final java.util.Map<String,Object> values=new java.util.HashMap<String,Object>();
  Prefs(java.nio.file.Path file){this.file=file;if(file!=null&&java.nio.file.Files.exists(file)){java.util.Properties p=new java.util.Properties();try(java.io.InputStream in=java.nio.file.Files.newInputStream(file)){p.load(in);for(String k:p.stringPropertyNames()){String v=p.getProperty(k);if(v.startsWith("i:"))values.put(k,Integer.valueOf(v.substring(2)));else if(v.startsWith("s:"))values.put(k,v.substring(2));else throw new java.io.IOException("Unknown preference type for "+k);}}catch(Exception e){throw new IllegalStateException("Cannot read dashboard preferences: "+file,e);}}}
  public synchronized int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}
  public synchronized String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
  public Editor edit(){return new EditorImpl();}
  private synchronized boolean write(java.util.Map<String,Object> updates){
   java.util.Map<String,Object> next=new java.util.HashMap<String,Object>(values);next.putAll(updates);
   if(file!=null){java.nio.file.Path temp=null;try{java.nio.file.Files.createDirectories(file.getParent());temp=java.nio.file.Files.createTempFile(file.getParent(),file.getFileName().toString(),".tmp");java.util.Properties p=new java.util.Properties();for(java.util.Map.Entry<String,Object> entry:next.entrySet()){Object v=entry.getValue();p.setProperty(entry.getKey(),v instanceof Integer?"i:"+v:"s:"+v);}try(java.io.OutputStream out=java.nio.file.Files.newOutputStream(temp)){p.store(out,"JS Dashboard Preview");}try{java.nio.file.Files.move(temp,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException e){java.nio.file.Files.move(temp,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}values.clear();values.putAll(next);return true;}catch(Exception e){if(temp!=null)try{java.nio.file.Files.deleteIfExists(temp);}catch(Exception ignored){}System.err.println("Cannot save dashboard preferences: "+e);return false;}}
   values.clear();values.putAll(next);return true;
  }
  private final class EditorImpl implements SharedPreferences.Editor {
   private final java.util.Map<String,Object> updates=new java.util.HashMap<String,Object>();
   public Editor putInt(String k,int v){updates.put(k,Integer.valueOf(v));return this;}
   public Editor putString(String k,String v){updates.put(k,v);return this;}
   public boolean commit(){return write(updates);}public void apply(){if(!commit())throw new IllegalStateException("Cannot persist dashboard preferences");}
  }
 }
}''',
    'android/content/res/AssetManager.java': '''package android.content.res;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

public class AssetManager {
 public InputStream open(String name)throws IOException{
  Path path=Paths.get(System.getProperty("dashboard.assets"),name);
  InputStream input=Files.newInputStream(path);
  return Boolean.getBoolean("dashboard.editor")?new EditorAsset(input,path):input;
 }
 public static String editorAssetKey(InputStream input){
  if(!(input instanceof EditorAsset))return null;
  Path path=((EditorAsset)input).path;
  try{
   BasicFileAttributes version=Files.readAttributes(path,BasicFileAttributes.class);
   return path+"|"+version.fileKey()+"|"+version.lastModifiedTime()+"|"+version.size();
  }catch(IOException unavailable){return null;}
 }
 private static final class EditorAsset extends FilterInputStream {
  final Path path;
  EditorAsset(InputStream stream,Path path){super(stream);this.path=path;}
 }
}''',
    'android/content/Intent.java': '''package android.content;
public class Intent {public static final String ACTION_GET_CONTENT="get-content";public Intent(){}public Intent(String a){}
 public boolean getBooleanExtra(String n,boolean d){return d;}public void removeExtra(String n){}
 public Intent setType(String t){return this;}public static Intent createChooser(Intent i,String s){return i;}
}''',
    'android/content/DialogInterface.java': '''package android.content;
public interface DialogInterface {void dismiss();interface OnMultiChoiceClickListener {void onClick(DialogInterface d,int n,boolean checked);}interface OnClickListener {void onClick(DialogInterface d,int n);}
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
 public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_SCROLL=8,AXIS_VSCROLL=9;
 private final int action;private final float x,y,vertical;
 public MotionEvent(int a,float x,float y){this(a,x,y,0);}
 public MotionEvent(int a,float x,float y,float vertical){action=a;this.x=x;this.y=y;this.vertical=vertical;}
 public int getAction(){return action;}public float getX(){return x;}public float getY(){return y;}
 public float getAxisValue(int axis){return axis==AXIS_VSCROLL?vertical:0;}
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
   public void mouseWheelMoved(java.awt.event.MouseWheelEvent e){onGenericMotionEvent(new MotionEvent(MotionEvent.ACTION_SCROLL,e.getX(),e.getY(),-(float)e.getPreciseWheelRotation()));}
  };addMouseListener(input);addMouseMotionListener(input);addMouseWheelListener(input);
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
 protected void onDraw(android.graphics.Canvas c){}public boolean onTouchEvent(MotionEvent e){return false;}public boolean onGenericMotionEvent(MotionEvent e){return false;}
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
 private android.graphics.Bitmap bitmap;private long revision=-1;
 void request(com.veloster.dashboard.vehicle.MediaState.Snapshot s){
  if(revision==s.artworkRevision)return;revision=s.artworkRevision;
  if("preview:cover".equals(s.artworkUri)){if(bitmap==null)bitmap=new android.graphics.Bitmap(480,480);}
  else if(s.artworkUri.startsWith("https://")){bitmap=null;final String url=s.artworkUri;final long requested=revision;new Thread(()->{try{
   java.net.URLConnection connection=new java.net.URL(url).openConnection();connection.setConnectTimeout(2500);connection.setReadTimeout(2500);
   android.graphics.Bitmap loaded=android.graphics.BitmapFactory.decodeStream(connection.getInputStream());if(loaded!=null&&requested==revision)bitmap=loaded;
  }catch(Exception ignored){}},"spotify-preview-artwork").start();}
  else bitmap=null;
 }
 android.graphics.Bitmap bitmap(){return bitmap;}}
''',
    'com/veloster/dashboard/vehicle/MediaReceiver.java': '''package com.veloster.dashboard.vehicle;
public class MediaReceiver {public MediaReceiver(MediaState s){}public void start(android.content.Context c){}public void stop(){}}
''',
    'com/veloster/dashboard/vehicle/MediaControl.java': '''package com.veloster.dashboard.vehicle;
public class MediaControl {
 private static boolean send(String command){try{
  Process p=new ProcessBuilder("osascript","-e","tell application \\\"Spotify\\\" to "+command).start();
  if(!p.waitFor(2,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();return false;}
  return p.exitValue()==0;
 }catch(Exception ignored){return false;}}
 public static boolean toggle(android.content.Context c,int mode,int state){return mode==0x73&&(state==0||state==1)&&send("playpause");}
 public static boolean step(android.content.Context c,boolean next,int mode){return mode==0x73&&send(next?"next track":"previous track");}}
''',
    'com/veloster/dashboard/vehicle/RegulationState.java': '''package com.veloster.dashboard.vehicle;
public class RegulationState {public static int read(android.content.Context c){return 0;}}
''',
    'com/veloster/dashboard/DashboardActivity.java': '''package com.veloster.dashboard;public class DashboardActivity {public static final int REQUEST_CARPLAY_CAPTURE=1;}''',
    'com/veloster/dashboard/debugger/JsLogger.java': '''package com.veloster.dashboard.debugger;public class JsLogger {public static void emit(long now,com.veloster.dashboard.vehicle.Telemetry t,JsDiagState.Snapshot s){}}''',
    'com/veloster/dashboard/debugger/CarPlayRawLog.java': '''package com.veloster.dashboard.debugger;public class CarPlayRawLog {}''',
}
