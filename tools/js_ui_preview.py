#!/usr/bin/env python3
"""Live Java2D preview of the JS dashboard renderers on a desktop."""
import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tests'))
import test_js_diagnostics_renderer as renderer

PAGES = ('전체 조망', 'HUD·내비', 'CarPlay', '차량', '연결', 'Bindings', '퍼포먼스', 'F1', '게이지', '그래프', '설정')
STATES = ('waiting', 'received', 'invalid', 'held', 'expired')
LANGUAGES = ('ko', 'en')
THEMES = ('original', 'aurora_focus')
VEHICLES = {
    'js': {'name': 'Veloster', 'resolution': (800, 480)},
    'js_n': {'name': 'Veloster N', 'resolution': (800, 480)},
    'pd': {'name': 'i30 (N Line)', 'resolution': (800, 480)},
    'cn7': {'name': 'Avante (Elantra) CN7', 'inches': 10.25,
            'resolution': (1920, 720), 'fixture': 'js_n'},
}
JS_ONLY_PAGES = frozenset((1, 2, 3, 4, 5))


def display_label(spec):
    width, height = spec['resolution']
    size = f"{spec['inches']:g}″ · " if 'inches' in spec else ''
    return f'{size}{width} × {height}'


def vehicle_pages(vehicle_id):
    if vehicle_id not in VEHICLES:
        raise ValueError('unknown vehicle: ' + vehicle_id)
    return tuple(i for i in range(len(PAGES)) if vehicle_id == 'js' or i not in JS_ONLY_PAGES)


class Renderer:
    def __init__(self, directory):
        self.classes = Path(directory) / 'classes'
        self.classes.mkdir()
        self.process = None

    def close(self):
        if self.process:
            self.process.terminate()
            self.process.wait(timeout=5)
            self.process.stdin.close()
            self.process.stdout.close()
            self.process = None

    def compile(self):
        import graphics_test_support
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
   String[] v=line.split("\\t");int page=Integer.parseInt(v[0]);String state=v[1];String themeId=v[3];String vehicle=v[4];int outWidth=Integer.parseInt(v[5]);int outHeight=Integer.parseInt(v[6]);
   ThemeSpec theme="aurora_focus".equals(themeId)?ThemeSpec.auroraFocus():ThemeSpec.original();
   UiText.configure(v[2],v[2]);Telemetry t=fixture(state,vehicle);JsDiagState d=observations(state);
   long now=state.equals("expired")?62000:2000;
   OverviewLayout overview=new OverviewLayout();overview.theme(theme);
   MediaState media=new MediaState();
   if(!state.equals("waiting")){media.mode(0x73);media.update(3,true,"Example Track",true,"Example Artist",true,12000,true,180000,false,1000);}
   Canvas c=new Canvas(outWidth,outHeight);
   if("js".equals(vehicle)){JsPitRenderer r=new JsPitRenderer();r.setTheme(theme);r.setOverviewLayout(overview);
    if(page==10)r.setSettingsPage(true,"ko".equals(v[2])?"한국어":"English",theme.displayName,com.veloster.dashboard.view.SettingsPageRenderer.menuPositionLabel(ThemeSpec.MENU_THEME));
    if(page>=1&&page<=5){r.setDiagnosticPage(true,d.snapshot(now));float x=40+(page-1)*154;r.selectDiagnosticTab(x,80,x,80);}
    if(page==6)r.setPerformancePage(true);if(page==8)r.setGaugeLayout(new GaugeLayout());if(page==9)r.setGraphLayout(new GraphLayout());
    r.draw(c,outWidth,outHeight,page==7,t,now,ShiftGuide.DEFAULT,false,media.snapshot(now));
   }else{PitRenderer r=new PitRenderer();r.setTheme(theme);r.setOverviewLayout(overview);
    if(page==10)r.setSettingsPage(true,"ko".equals(v[2])?"한국어":"English",theme.displayName,com.veloster.dashboard.view.SettingsPageRenderer.menuPositionLabel(ThemeSpec.MENU_THEME));
    if(page==6)r.setPerformancePage(true);if(page==8)r.setGaugeLayout(new GaugeLayout());if(page==9)r.setGraphLayout(new GraphLayout());
    r.draw(c,outWidth,outHeight,page==7,t,now,ShiftGuide.DEFAULT,false,media.snapshot(now));
   }
   c.write(v[7]);System.out.println("OK");
  }catch(Throwable e){e.printStackTrace(System.err);System.out.println("ERROR: "+e.toString());}}
 }
}""")
        sources.append(harness)
        base = ROOT / 'apps/dashboard/src/main/java/com/veloster/dashboard'
        groups = {'ClusterRenderer':'view/f1', 'JsClusterRenderer':'view/f1', 'JsOverviewRenderer':'view/overview',
                  'OverviewRenderer':'view/overview', 'OverviewCardRenderer':'view/overview', 'OverviewLayout':'view/overview', 'JsGaugeRenderer':'view/gauge',
                  'GaugeLayout':'view/gauge', 'GaugeRenderer':'view/gauge',
                  'GraphLayout':'view/graph', 'GraphRenderer':'view/graph',
                  'PerformanceRenderer':'view/performance', 'JsPerformanceRenderer':'view/performance', 'MediaBar':'view', 'MenuIcons':'view', 'SettingsPageRenderer':'view', 'JsPowertrain':'vehicle/js',
                  'JsGearState':'vehicle/js', 'JsTelemetryDecoder':'vehicle/js',
                  'PdPowertrain':'vehicle/pd', 'PdGearState':'vehicle/pd'}
        names = ('Telemetry','PdPowertrain','PdGearState','JsPowertrain','JsGearState','BluetoothRuntimeProbe',
                 'JsTelemetryDecoder','JsPitRenderer','PitRenderer','ClusterRenderer','JsClusterRenderer','JsOverviewRenderer',
                 'OverviewRenderer','OverviewCardRenderer','PerformanceRenderer','JsGaugeRenderer','JsPerformanceRenderer','SettingsPageRenderer','MediaBar','MenuIcons','DashboardPage','ShiftGuide','MediaState',
                 'GaugeLayout','GaugeRenderer','OverviewLayout','GraphLayout','GraphRenderer')
        for name in names:
            if name == 'BluetoothRuntimeProbe':
                sources.append(ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/BluetoothRuntimeProbe.java')
                continue
            group = groups.get(name, 'view' if name in ('JsPitRenderer','PitRenderer','DashboardPage','SettingsPageRenderer') else 'vehicle')
            sources.append(base / group / (name + '.java'))
        sources.extend([base / 'vehicle/pd/PdTelemetryDecoder.java', base / 'vehicle/jsn/JsnTelemetryDecoder.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/JsDiagState.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/view/JsDiagnosticsRenderer.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/ThemeSpec.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/ThemePalette.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/LiquidGlassEngine.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/theme/ThemeSurface.java',
                        ROOT / 'apps/dashboard/src/0x1101L/java/com/veloster/dashboard/view/overview/ThemeLayout.java',
                        ROOT / 'tests/java_stubs/com/veloster/dashboard/theme/ThemeResources.java',
                        ROOT / 'tests/java_stubs/android/content/Context.java',
                        ROOT / 'tests/java_stubs/android/content/res/AssetManager.java',
                        ROOT / 'shared/src/com/veloster/i18n/UiText.java'])
        result = subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(self.classes), *map(str,sources)], capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(result.stderr)
        return java

    def render(self, folder, pages, states, languages, themes=THEMES, vehicle='js'):
        if vehicle not in VEHICLES:
            raise ValueError('unknown vehicle: ' + vehicle)
        if self.process is None:
            java = self.compile()
            self.process = subprocess.Popen([java, '-Djava.awt.headless=true', '-cp', str(self.classes),
                                             'JsDiagnosticsPreview'], stdin=subprocess.PIPE,
                                            stdout=subprocess.PIPE, text=True, bufsize=1)
        spec = VEHICLES[vehicle]
        width, height = spec['resolution']
        fixture = spec.get('fixture', vehicle)
        for page in pages:
            for state in states:
                for lang in languages:
                    for theme_id in themes:
                        output = Path(folder).resolve() / f'{vehicle}-{page}-{state}-{lang}-{theme_id}.png'
                        self.process.stdin.write(f'{page}\t{state}\t{lang}\t{theme_id}\t{fixture}\t{width}\t{height}\t{output}\n')
                        self.process.stdin.flush()
                        reply = self.process.stdout.readline().strip()
                        if reply != 'OK':
                            raise RuntimeError('Renderer failed: ' + reply)
        return 'Preview rendered'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='render all screens without a window')
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
                print('PASS vehicle profiles, native render sizes and theme selection')
                print('Images:', image_count, '(' + ', '.join(VEHICLES) + ')')
            finally:
                engine.close()
        return


    from dashboard_runtime import run
    run()


if __name__ == '__main__':
    main()
