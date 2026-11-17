#!/usr/bin/env python3
"""Live Java2D preview of the JS dashboard renderers on a desktop."""
import argparse
import importlib
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tests'))
import test_js_diagnostics_renderer as renderer

PAGES = ('전체 조망', 'HUD·내비', 'CarPlay', '차량', '연결', 'Bindings', '퍼포먼스', 'F1', '게이지', '그래프')
STATES = ('waiting', 'received', 'invalid', 'held', 'expired')
LANGUAGES = ('ko', 'en')
THEMES = ('Original', 'Aurora Focus')
THEME_IDS = {'Original': 'original', 'Aurora Focus': 'aurora_focus'}
THEME_GRIDS = {'original': (5, 4), 'aurora_focus': (5, 4)}
OVERVIEW_ITEM_COUNT = 20
VEHICLES = {
    'js': {'name': 'Veloster', 'resolution': (800, 480)},
    'js_n': {'name': 'Veloster N', 'resolution': (800, 480)},
    'pd': {'name': 'i30 (N LIne)', 'resolution': (800, 480)},
}
JS_ONLY_PAGES = frozenset((1, 2, 3, 4, 5))


def vehicle_pages(vehicle_id):
    if vehicle_id not in VEHICLES:
        raise ValueError('unknown vehicle: ' + vehicle_id)
    return tuple(i for i in range(len(PAGES)) if vehicle_id == 'js' or i not in JS_ONLY_PAGES)


def overview_page_count(theme_id):
    columns, rows = THEME_GRIDS[theme_id]
    return (OVERVIEW_ITEM_COUNT + columns * rows - 1) // (columns * rows)


class Renderer:
    def __init__(self, directory):
        self.classes = Path(directory) / 'classes'
        self.classes.mkdir()
        self.process = None
        self.stamp = None

    def close(self):
        if self.process:
            self.process.terminate()
            self.process.wait(timeout=5)
            self.process = None

    def copy_png_to_clipboard(self, image_path):
        from test_quick_entry_runtime import java_tools
        java, _javac = java_tools()
        result = subprocess.run([java, '-cp', str(self.classes), 'PngClipboard', str(image_path)],
                                capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(result.stderr.strip() or '클립보드 복사에 실패했습니다.')

    def compile(self, stamp):
        import graphics_test_support
        importlib.reload(graphics_test_support)
        importlib.reload(renderer)
        from test_quick_entry_runtime import java_tools
        java, javac = java_tools()
        sources = []
        for name, body in graphics_test_support.GRAPHICS.items():
            path = self.classes / 'android/graphics' / (name + '.java')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('package android.graphics;\n' + body)
            sources.append(path)
        prefix = renderer.HARNESS.split(' public static void main(String[] args)', 1)[0]
        harness = self.classes / 'JsDiagnosticsPreview.java'
        harness.write_text(prefix + """
 public static void main(String[] args)throws Exception{
  java.io.BufferedReader input=new java.io.BufferedReader(new java.io.InputStreamReader(System.in,"UTF-8"));
  for(String line;(line=input.readLine())!=null;){try{
   String[] v=line.split("\\t");int page=Integer.parseInt(v[0]);String state=v[1];String themeId=v[3];int overviewPage=Integer.parseInt(v[4]);String vehicle=v[5];int outWidth=Integer.parseInt(v[6]);int outHeight=Integer.parseInt(v[7]);
   ThemeSpec theme="aurora_focus".equals(themeId)?ThemeSpec.auroraFocus():ThemeSpec.original();
   UiText.configure(v[2],v[2]);Telemetry t=fixture(state,vehicle);JsDiagState d=observations(state);
   long now=state.equals("expired")?62000:2000;
   OverviewLayout overview=new OverviewLayout();overview.theme(theme);overview.page(overviewPage);
   MediaState media=new MediaState();
   if(!state.equals("waiting")){media.mode(0x73);media.update(3,true,"Example Track",true,"Example Artist",true,12000,true,180000,false,1000);}
   float fit=Math.min(outWidth/800f,outHeight/480f);Canvas c=new Canvas(outWidth,outHeight);c.save();c.translate((outWidth-800*fit)/2,(outHeight-480*fit)/2);c.scale(fit,fit);
   if("js".equals(vehicle)){JsPitRenderer r=new JsPitRenderer();r.setTheme(theme);r.setOverviewLayout(overview);
    if(page>=1&&page<=5){r.setDiagnosticPage(true,d.snapshot(now));float x=40+(page-1)*154;r.selectDiagnosticTab(x,80,x,80);}
    if(page==6)r.setPerformancePage(true);if(page==8)r.setGaugeLayout(new GaugeLayout());if(page==9)r.setGraphLayout(new GraphLayout());
    r.draw(c,800,480,page==7,t,now,ShiftGuide.DEFAULT,false,media.snapshot(now));
   }else{PitRenderer r=new PitRenderer();r.setTheme(theme);r.setOverviewLayout(overview);
    if(page==6)r.setPerformancePage(true);if(page==8)r.setGaugeLayout(new GaugeLayout());if(page==9)r.setGraphLayout(new GraphLayout());
    r.draw(c,800,480,page==7,t,now,ShiftGuide.DEFAULT,false,media.snapshot(now));
   }
   c.restore();
   c.write(v[8]);System.out.println("OK");
  }catch(Throwable e){e.printStackTrace(System.err);System.out.println("ERROR: "+e.toString());}}
 }
}""")
        sources.append(harness)
        clipboard_helper = self.classes / 'PngClipboard.java'
        clipboard_helper.write_text("""
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
public final class PngClipboard {
 public static void main(String[] args) throws Exception {
  BufferedImage image = ImageIO.read(new File(args[0]));
  if (image == null) throw new IllegalArgumentException("Not a supported image");
  Transferable contents = new Transferable() {
   public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
   public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
   public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
    if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
    return image;
   }
  };
  Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);
 }
}
""")
        sources.append(clipboard_helper)
        base = ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard'
        groups = {'ClusterRenderer':'view/f1', 'JsClusterRenderer':'view/f1', 'JsOverviewRenderer':'view/overview',
                  'OverviewRenderer':'view/overview', 'OverviewLayout':'view/overview', 'JsGaugeRenderer':'view/gauge',
                  'GaugeLayout':'view/gauge', 'GaugeRenderer':'view/gauge',
                  'GraphLayout':'view/graph', 'GraphRenderer':'view/graph',
                  'PerformanceRenderer':'view/performance', 'JsPerformanceRenderer':'view/performance', 'JsPowertrain':'vehicle/js',
                  'JsGearState':'vehicle/js', 'JsTelemetryDecoder':'vehicle/js',
                  'PdPowertrain':'vehicle/pd', 'PdGearState':'vehicle/pd'}
        names = ('Telemetry','PdPowertrain','PdGearState','JsPowertrain','JsGearState','BluetoothRuntimeProbe',
                 'JsTelemetryDecoder','JsPitRenderer','PitRenderer','ClusterRenderer','JsClusterRenderer','JsOverviewRenderer',
                 'OverviewRenderer','PerformanceRenderer','JsGaugeRenderer','JsPerformanceRenderer','DashboardPage','ShiftGuide','MediaState',
                 'GaugeLayout','GaugeRenderer','OverviewLayout','GraphLayout','GraphRenderer')
        for name in names:
            if name == 'BluetoothRuntimeProbe':
                sources.append(ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/BluetoothRuntimeProbe.java')
                continue
            group = groups.get(name, 'view' if name in ('JsPitRenderer','PitRenderer','DashboardPage') else 'vehicle')
            sources.append(base / group / (name + '.java'))
        sources.extend([base / 'vehicle/pd/PdTelemetryDecoder.java', base / 'vehicle/jsn/JsnTelemetryDecoder.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/JsDiagState.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/view/JsDiagnosticsRenderer.java',
                        ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard/theme/ThemeSpec.java',
                        ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard/theme/ThemePalette.java',
                        ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard/view/overview/ThemeLayout.java',
                        ROOT / 'tests/java_stubs/com/veloster/dashboard/theme/ThemeResources.java',
                        ROOT / 'tests/java_stubs/android/content/Context.java',
                        ROOT / 'tests/java_stubs/android/content/res/AssetManager.java',
                        ROOT / 'shared/src/com/veloster/i18n/UiText.java'])
        result = subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(self.classes), *map(str,sources)], capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(result.stderr)
        self.close()
        self.process = subprocess.Popen([java, '-Djava.awt.headless=true', '-cp', str(self.classes),
                                         'JsDiagnosticsPreview'], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        text=True, bufsize=1)
        self.stamp = stamp

    def render(self, folder, pages, states, languages, themes=THEMES, vehicle='js'):
        if vehicle not in VEHICLES:
            raise ValueError('unknown vehicle: ' + vehicle)
        stamp = source_stamp()
        if self.process is None or self.process.poll() is not None or self.stamp != stamp:
            self.compile(stamp)
        for page in pages:
            for state in states:
                for lang in languages:
                    for theme in themes:
                        theme_id = THEME_IDS.get(theme, theme)
                        overview_page = min(getattr(self, 'overview_page', 0), overview_page_count(theme_id) - 1)
                        width, height = VEHICLES[vehicle]['resolution']
                        output = Path(folder).resolve() / f'{vehicle}-{page}-{state}-{lang}-{theme_id}.png'
                        self.process.stdin.write(f'{page}\t{state}\t{lang}\t{theme_id}\t{overview_page}\t{vehicle}\t{width}\t{height}\t{output}\n')
                        self.process.stdin.flush()
                        reply = self.process.stdout.readline().strip()
                        if reply != 'OK':
                            raise RuntimeError('Renderer failed: ' + reply)
        return 'Preview rendered'


def source_stamp():
    dirs = (ROOT / 'apps/dashboard/src', ROOT / 'shared/src', ROOT / 'tests')
    return tuple((str(p), p.stat().st_mtime_ns, p.stat().st_size)
                 for d in dirs for p in sorted(d.rglob('*'))
                 if p.is_file() and (p.suffix == '.java' or p.name in
                                     ('graphics_test_support.py', 'test_js_diagnostics_renderer.py')))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='render all screens without a window')
    parser.add_argument('--browser', action='store_true', help='open the legacy browser preview')
    args = parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory(prefix='js-ui-preview-check-') as folder:
            engine = Renderer(folder)
            try:
                for vehicle in VEHICLES:
                    print(engine.render(folder, vehicle_pages(vehicle), STATES, LANGUAGES, vehicle=vehicle))
                image_count = len(list(Path(folder).glob('*.png')))
                expected = sum(len(vehicle_pages(vehicle)) for vehicle in VEHICLES) * len(STATES) * len(LANGUAGES) * len(THEMES)
                if image_count != expected:
                    raise RuntimeError('Preview theme matrix is incomplete: %s images' % image_count)
                import struct
                vehicle_samples = set()
                for vehicle, spec in VEHICLES.items():
                    sample = (Path(folder) / f'{vehicle}-0-received-ko-original.png').read_bytes()
                    vehicle_samples.add(sample)
                    dimensions = struct.unpack('>II', sample[16:24])
                    if dimensions != spec['resolution']:
                        raise RuntimeError('%s preview size %r != %r' % (vehicle, dimensions, spec['resolution']))
                if len(vehicle_samples) != len(VEHICLES):
                    raise RuntimeError('Vehicle profile selection did not change the rendered preview')
                original = (Path(folder) / 'js-0-received-ko-original.png').read_bytes()
                focus_path = Path(folder) / 'js-0-received-ko-aurora_focus.png'
                focus_first = focus_path.read_bytes()
                if original == focus_first:
                    raise RuntimeError('Aurora Focus preview matches Original unexpectedly')
                print('PASS vehicle profiles, 800x480 render sizes, theme selection, and fixed 5x4 overview')
                print('Images:', image_count, '(JS, JSN, PD)')
            finally:
                engine.close()
        return

    if not args.browser:
        from js_ui_window import run
        run()
        return

    import json
    import webbrowser
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    from urllib.parse import parse_qs, urlsplit

    class Preview:
        def __init__(self, folder):
            self.folder = Path(folder)
            self.engine = Renderer(folder)
            self.lock = threading.Lock()
            self.generation = 0
            self.error = None
            self.busy = False
            self.pending = None
            self.stamp = source_stamp()

        def request(self, all_screens, selected):
            with self.lock:
                if self.busy:
                    old = self.pending
                    self.pending = (all_screens or bool(old and old[0]), selected)
                    return
                self.busy = True
            threading.Thread(target=self.run, args=(all_screens, selected), daemon=True).start()

        def run(self, all_screens, selected):
            while True:
                try:
                    p, state, lang, theme, overview_page, vehicle = selected
                    self.engine.overview_page = overview_page
                    # Render separately, then publish complete files atomically.
                    with tempfile.TemporaryDirectory(prefix='js-ui-render-') as work:
                        self.engine.render(work, vehicle_pages(vehicle) if all_screens else (p,),
                               STATES if all_screens else (state,),
                               LANGUAGES if all_screens else (lang,),
                               THEMES if all_screens else (theme,), vehicle=vehicle)
                        for image in Path(work).glob('*.png'):
                            image.replace(self.folder / image.name)
                    error = None
                except Exception as exc:
                    error = str(exc)
                    print(error, file=sys.stderr)
                with self.lock:
                    self.error = error
                    self.generation += 1
                    pending = self.pending
                    self.pending = None
                    if not pending:
                        self.busy = False
                        return
                    all_screens, selected = pending

    html = r"""<!doctype html><html lang="ko"><meta charset="utf-8">
<title>Dashboard · Java2D 프리뷰</title>
<style>body{background:#171a20;color:#eee;font:15px -apple-system,sans-serif;margin:20px}.settings{display:grid;grid-template-columns:1fr;gap:8px;margin-bottom:14px}.row,.actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}select,button{font:inherit;padding:6px;margin-right:8px}img{display:block;width:800px;max-width:100%;height:auto;background:#080b10}.actions{margin-top:10px}#status{white-space:pre-wrap;color:#bfc9d7;margin-top:8px}</style>
<div class="settings"><div class="row"><label>화면 <select id="page"></select></label><label>언어 <select id="lang"></select></label><label>테마 <select id="theme"></select></label></div><div class="row"><label>차량 <select id="vehicle"></select></label><label>조망 페이지 <select id="overview"></select></label></div></div>
<img id="screen" alt="JS Dashboard 프리뷰"><div class="actions"><label>데이터 <select id="state"></select></label><button id="refresh">현재 갱신 (r)</button><button id="all">전체 갱신 (Shift+R)</button><button id="capture" disabled>프리뷰 캡처 (PNG)</button></div><div id="status">준비 중…</div>
<script>
const pages=__PAGES__, states=__STATES__, langs=__LANGS__, themes=__THEMES__, grids=__GRIDS__, vehicles=__VEHICLES__, allowed=__VEHICLE_PAGES__;
const page=document.querySelector('#page'), state=document.querySelector('#state'), lang=document.querySelector('#lang'), theme=document.querySelector('#theme'), overview=document.querySelector('#overview'), vehicle=document.querySelector('#vehicle'), status=document.querySelector('#status'), screen=document.querySelector('#screen'), capture=document.querySelector('#capture');
function options(el, values, direct=false){values.forEach((x,i)=>{let o=document.createElement('option');o.value=direct?x:i;o.textContent=el===theme?(x==='original'?'Original':'Aurora Focus'):x;el.appendChild(o)});}
function setPages(){page.replaceChildren(...allowed[vehicle.value].map(i=>{let o=document.createElement('option');o.value=String(i);o.textContent=pages[i];return o}));if(!allowed[vehicle.value].includes(Number(page.value)))page.value='0'}
options(state,states);options(lang,langs);options(theme,themes,true);Object.entries(vehicles).forEach(([id,v])=>{let o=document.createElement('option');o.value=id;o.textContent=v.name;vehicle.appendChild(o)});state.value='1';theme.value='original';vehicle.value='js';setPages();
function updateOverview(){const [columns,rows]=grids[theme.value],count=Math.ceil(20/(columns*rows));overview.replaceChildren(...Array.from({length:count},(_,i)=>{let o=document.createElement('option');o.value=String(i);o.textContent=String(i+1);return o}));overview.disabled=count<2;if(Number(overview.value)>=count)overview.value='0'}
updateOverview();
let generation=-1, lastSelection='', readyKey='', requestedKey='', requestedUrl='';
function selection(){return [page.value,states[state.value],langs[lang.value],theme.value,overview.value,vehicle.value].join(',')}
function updateCapture(){capture.disabled=!readyKey||readyKey!==selection()||readyKey!==requestedKey}
async function refresh(all=false){capture.disabled=true;readyKey='';if(all){page.value='0';state.value='1';lang.value='0';theme.value='original';overview.value='0'}status.textContent=all?'전체 렌더링 중…':'선택한 화면 갱신 중…';await fetch('/render?all='+(all?1:0)+'&selection='+encodeURIComponent(selection()),{method:'POST'});}
for(const el of [page,state,lang,overview]) el.onchange=()=>refresh();theme.onchange=()=>{updateOverview();refresh()};vehicle.onchange=()=>{setPages();refresh()};
document.querySelector('#refresh').onclick=()=>refresh();document.querySelector('#all').onclick=()=>refresh(true);
screen.addEventListener('load',()=>{if(requestedKey===selection()&&screen.currentSrc===requestedUrl&&screen.naturalWidth>0){readyKey=requestedKey;updateCapture()}});
capture.onclick=()=>{if(capture.disabled)return;const canvas=document.createElement('canvas');canvas.width=screen.naturalWidth;canvas.height=screen.naturalHeight;const context=canvas.getContext('2d');context.drawImage(screen,0,0);canvas.toBlob(blob=>{if(!blob)return;const link=document.createElement('a');link.href=URL.createObjectURL(blob);link.download='dashboard-'+selection().replaceAll(',','-')+'.png';link.click();setTimeout(()=>URL.revokeObjectURL(link.href),1000)},'image/png')};
screen.addEventListener('click',e=>{const rect=screen.getBoundingClientRect(),x=(e.clientX-rect.left)*800/rect.width,y=(e.clientY-rect.top)*480/rect.height;let p=Number(page.value), next=-1;
 if(p===0&&y>=448&&theme.value!=='original'&&!overview.disabled){overview.value=overview.value==='0'?'1':'0';refresh();return}
 if(y>=8&&y<=46){if(x>=18&&x<138)next=6;else if(x>=144&&x<=244)next=(p===0?1:0);else if(x>=250&&x<=322)next=7;else if(x>=328&&x<=412)next=8;else if(x>=418&&x<=528)next=9;else if(x>=534&&x<=582){lang.value=lang.value==='0'?'1':'0';refresh();return}}
 else if(p>=1&&p<=5&&y>=62&&y<=100){for(let i=0;i<5;i++)if(x>=18+i*154&&x<=166+i*154)next=i+1}
 if(next>=0){page.value=String(next);refresh()}
});
document.addEventListener('keydown',e=>{if(e.metaKey||e.ctrlKey||e.altKey)return;if(e.key==='r'||e.key==='R'){e.preventDefault();refresh(e.key==='R')}});
async function poll(){try{let res=await fetch('/status?selection='+encodeURIComponent(selection()),{cache:'no-store'});let data=await res.json();if(data.error){capture.disabled=true;readyKey='';status.textContent='렌더링 오류: '+data.error}else if(data.busy){capture.disabled=true;readyKey='';status.textContent='렌더링 중…'}else {status.textContent='PC Java2D · 예시 데이터 | r: 현재 갱신 · Shift+R: 전체 갱신 후 처음';if(generation!==data.generation||lastSelection!==selection()){generation=data.generation;lastSelection=selection();readyKey='';requestedKey=selection();requestedUrl=new URL('/image?selection='+encodeURIComponent(requestedKey)+'&v='+generation,location.href).href;screen.src=requestedUrl}updateCapture()}}catch(e){capture.disabled=true;status.textContent='프리뷰 서버 연결 끊김'}setTimeout(poll,120)}
refresh();poll();</script></html>""".replace('__PAGES__', json.dumps(PAGES, ensure_ascii=False)).replace('__STATES__', json.dumps(STATES)).replace('__LANGS__', json.dumps(LANGUAGES)).replace('__THEMES__', json.dumps(list(THEME_IDS.values()))).replace('__GRIDS__', json.dumps(THEME_GRIDS)).replace('__VEHICLES__', json.dumps(VEHICLES)).replace('__VEHICLE_PAGES__', json.dumps({v:vehicle_pages(v) for v in VEHICLES}))

    with tempfile.TemporaryDirectory(prefix='js-ui-preview-') as folder:
        preview = Preview(folder)
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def send(self, code, content_type, body):
                self.send_response(code)
                self.send_header('Content-Type', content_type)
                self.send_header('Cache-Control', 'no-store')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            def selected(self, query):
                try:
                    p, state, lang, theme, overview, vehicle = parse_qs(query)['selection'][0].split(',')
                    p = int(p)
                    overview = int(overview)
                    if vehicle not in VEHICLES or p not in vehicle_pages(vehicle) or state not in STATES or lang not in LANGUAGES or theme not in THEME_IDS.values() or overview >= overview_page_count(theme) or overview < 0:
                        raise ValueError()
                    return p, state, lang, theme, overview, vehicle
                except (KeyError, ValueError):
                    return 0, 'received', 'ko', 'original', 0, 'js'
            def do_POST(self):
                parsed = urlsplit(self.path)
                if parsed.path != '/render':
                    self.send(404, 'text/plain', b'Not found'); return
                preview.request(parse_qs(parsed.query).get('all') == ['1'], self.selected(parsed.query))
                self.send(200, 'text/plain', b'OK')
            def do_GET(self):
                parsed = urlsplit(self.path)
                if parsed.path == '/':
                    self.send(200, 'text/html; charset=utf-8', html.encode()); return
                selected = self.selected(parsed.query)
                if parsed.path == '/status':
                    stamp = source_stamp()
                    if stamp != preview.stamp:
                        preview.stamp = stamp
                        preview.request(False, selected)
                    with preview.lock:
                        payload = json.dumps({'busy': preview.busy, 'generation': preview.generation, 'error': preview.error}).encode()
                    self.send(200, 'application/json', payload); return
                if parsed.path == '/image':
                    p, state, lang, theme, overview, vehicle = selected
                    image = preview.folder / ('%s-%s-%s-%s-%s.png' % (vehicle, p, state, lang, theme))
                    if image.exists():
                        self.send(200, 'image/png', image.read_bytes()); return
                self.send(404, 'text/plain', b'Not found')
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        url = 'http://127.0.0.1:%s/' % server.server_port
        print('JS Dashboard 프리뷰:', url, flush=True)
        print('종료: Ctrl+C', flush=True)
        webbrowser.open(url)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            server.server_close()
            preview.engine.close()


if __name__ == '__main__':
    main()
