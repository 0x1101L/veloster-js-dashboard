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

PAGES = ('전체 조망', 'HUD·내비', 'CarPlay', '차량 신호', 'Wi-Fi·DCT', 'Bindings', '퍼포먼스', 'F1', '게이지', '그래프')
STATES = ('waiting', 'received', 'invalid', 'held', 'expired')
LANGUAGES = ('ko', 'en')


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
   String[] v=line.split("\\t");int page=Integer.parseInt(v[0]);String state=v[1];
   UiText.configure(v[2],v[2]);Telemetry t=fixture(state);JsDiagState d=observations(state);
   long now=state.equals("expired")?62000:2000;
   JsPitRenderer r=new JsPitRenderer();
   if(page>=1&&page<=5){r.setDiagnosticPage(true,d.snapshot(now));r.selectDiagnosticTab(40+(page-1)*154,80,40+(page-1)*154,80);}
   if(page==6)r.setPerformancePage(true);
   if(page==8)r.setGaugeLayout(new GaugeLayout());
   if(page==9)r.setGraphLayout(new GraphLayout());
   MediaState media=new MediaState();
   if(!state.equals("waiting")){media.mode(0x73);media.update(3,true,"Example Track",true,"Example Artist",true,12000,true,180000,false,1000);}
   Canvas c=new Canvas(800,480);r.draw(c,800,480,page==7,t,now,ShiftGuide.DEFAULT,false,media.snapshot(now));
   c.write(v[3]);System.out.println("OK");
  }catch(Throwable e){e.printStackTrace(System.err);System.out.println("ERROR: "+e.toString());}}
 }
}""")
        sources.append(harness)
        base = ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard'
        groups = {'ClusterRenderer':'view/f1', 'JsClusterRenderer':'view/f1', 'JsOverviewRenderer':'view/overview',
                  'OverviewLayout':'view/overview', 'JsGaugeRenderer':'view/gauge',
                  'GaugeLayout':'view/gauge', 'GaugeRenderer':'view/gauge',
                  'GraphLayout':'view/graph', 'GraphRenderer':'view/graph',
                  'JsPerformanceRenderer':'view/performance', 'JsPowertrain':'vehicle/js',
                  'JsGearState':'vehicle/js', 'JsTelemetryDecoder':'vehicle/js',
                  'PdPowertrain':'vehicle/pd', 'PdGearState':'vehicle/pd'}
        names = ('Telemetry','PdPowertrain','PdGearState','JsPowertrain','JsGearState',
                 'JsTelemetryDecoder','JsPitRenderer','ClusterRenderer','JsClusterRenderer','JsOverviewRenderer',
                 'JsGaugeRenderer','JsPerformanceRenderer','DashboardPage','ShiftGuide','MediaState',
                 'GaugeLayout','GaugeRenderer','OverviewLayout','GraphLayout','GraphRenderer')
        for name in names:
            group = groups.get(name, 'view' if name in ('JsPitRenderer','DashboardPage') else 'vehicle')
            sources.append(base / group / (name + '.java'))
        sources.extend([base / 'vehicle/pd/PdTelemetryDecoder.java', base / 'vehicle/jsn/JsnTelemetryDecoder.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/JsDiagState.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/view/JsDiagnosticsRenderer.java',
                        ROOT / 'shared/src/com/veloster/i18n/UiText.java'])
        result = subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(self.classes), *map(str,sources)], capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(result.stderr)
        self.close()
        self.process = subprocess.Popen([java, '-Djava.awt.headless=true', '-cp', str(self.classes),
                                         'JsDiagnosticsPreview'], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        text=True, bufsize=1)
        self.stamp = stamp

    def render(self, folder, pages, states, languages):
        stamp = source_stamp()
        if self.process is None or self.process.poll() is not None or self.stamp != stamp:
            self.compile(stamp)
        for page in pages:
            for state in states:
                for lang in languages:
                    output = Path(folder).resolve() / f'{page}-{state}-{lang}.png'
                    self.process.stdin.write(f'{page}\t{state}\t{lang}\t{output}\n')
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
    args = parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory(prefix='js-ui-preview-check-') as folder:
            engine = Renderer(folder)
            try:
                print(engine.render(folder, range(len(PAGES)), STATES, LANGUAGES))
                print('Images:', len(list(Path(folder).glob('*.png'))))
            finally:
                engine.close()
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
                    p, state, lang = selected
                    # Render separately, then publish complete files atomically.
                    with tempfile.TemporaryDirectory(prefix='js-ui-render-') as work:
                        self.engine.render(work, range(len(PAGES)) if all_screens else (p,),
                               STATES if all_screens else (state,),
                               LANGUAGES if all_screens else (lang,))
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
<title>JS Dashboard · Java2D 프리뷰</title>
<style>body{background:#171a20;color:#eee;font:15px -apple-system,sans-serif;margin:20px}select,button{font:inherit;padding:6px;margin:0 12px 10px 4px}img{display:block;width:800px;max-width:100%;background:#080b10}#status{white-space:pre-wrap;color:#bfc9d7;margin-top:12px}</style>
<label>화면 <select id="page"></select></label><label>데이터 <select id="state"></select></label><label>언어 <select id="lang"></select></label>
<button id="refresh">현재 갱신 (r)</button><button id="all">전체 갱신 (Shift+R)</button>
<img id="screen" alt="JS Dashboard 프리뷰"><div id="status">준비 중…</div>
<script>
const pages=__PAGES__, states=__STATES__, langs=__LANGS__;
const page=document.querySelector('#page'), state=document.querySelector('#state'), lang=document.querySelector('#lang'), status=document.querySelector('#status'), screen=document.querySelector('#screen');
function options(el, values){values.forEach((x,i)=>{let o=document.createElement('option');o.value=i;o.textContent=x;el.appendChild(o)});}
options(page,pages);options(state,states);options(lang,langs);state.value='1';
let generation=-1, lastSelection='';
function selection(){return [page.value,states[state.value],langs[lang.value]].join(',')}
async function refresh(all=false){if(all){page.value='0';state.value='1';lang.value='0'}status.textContent=all?'전체 렌더링 중…':'현재 화면 렌더링 중…';await fetch('/render?all='+(all?1:0)+'&selection='+encodeURIComponent(selection()),{method:'POST'});}
for(const el of [page,state,lang]) el.onchange=()=>refresh();
document.querySelector('#refresh').onclick=()=>refresh();document.querySelector('#all').onclick=()=>refresh(true);
screen.addEventListener('click',e=>{const rect=screen.getBoundingClientRect(),x=(e.clientX-rect.left)*800/rect.width,y=(e.clientY-rect.top)*480/rect.height;let p=Number(page.value), next=-1;
 if(y>=8&&y<=46){if(x>=18&&x<138)next=6;else if(x>=144&&x<=244)next=(p===0?1:0);else if(x>=250&&x<=322)next=7;else if(x>=328&&x<=412)next=8;else if(x>=418&&x<=528)next=9;else if(x>=534&&x<=582){lang.value=lang.value==='0'?'1':'0';refresh();return}}
 else if(p>=1&&p<=5&&y>=62&&y<=100){for(let i=0;i<5;i++)if(x>=18+i*154&&x<=166+i*154)next=i+1}
 if(next>=0){page.value=String(next);refresh()}
});
document.addEventListener('keydown',e=>{if(e.metaKey||e.ctrlKey||e.altKey)return;if(e.key==='r'||e.key==='R'){e.preventDefault();refresh(e.key==='R')}});
async function poll(){try{let res=await fetch('/status?selection='+encodeURIComponent(selection()),{cache:'no-store'});let data=await res.json();if(data.error)status.textContent='렌더링 오류: '+data.error;else if(data.busy)status.textContent='렌더링 중…';else {status.textContent='PC Java2D · 예시 데이터 | r: 현재 갱신 · Shift+R: 전체 갱신 후 처음';if(generation!==data.generation||lastSelection!==selection()){generation=data.generation;lastSelection=selection();screen.src='/image?selection='+encodeURIComponent(selection())+'&v='+generation}}}catch(e){status.textContent='프리뷰 서버 연결 끊김'}setTimeout(poll,120)}
refresh();poll();</script></html>""".replace('__PAGES__', json.dumps(PAGES, ensure_ascii=False)).replace('__STATES__', json.dumps(STATES)).replace('__LANGS__', json.dumps(LANGUAGES))

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
                    p, state, lang = parse_qs(query)['selection'][0].split(',')
                    p = int(p)
                    if not 0 <= p < len(PAGES) or state not in STATES or lang not in LANGUAGES:
                        raise ValueError()
                    return p, state, lang
                except (KeyError, ValueError):
                    return 0, 'received', 'ko'
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
                    p, state, lang = selected
                    image = preview.folder / ('%s-%s-%s.png' % (p, state, lang))
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
