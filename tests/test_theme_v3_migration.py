"""Compare migrated v3 bundle with preserved v2 manifests under the same renderer."""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
from test_theme_v2_renderer import BASELINE
import dashboard_runtime as runtime
from test_quick_entry_runtime import java_tools

with tempfile.TemporaryDirectory(prefix='theme-v3-migration-') as folder:
    folder = Path(folder)
    java, classes = runtime.compile_runtime(folder)
    _, javac = java_tools()
    probe = folder / 'ThemeVisualProbe.java'
    retained_themes = BASELINE.replace(
        'new String[]{"original","aurora_focus","carplay_glass_light","carplay_glass_dark"}',
        'new String[]{"original","aurora_focus"}')
    probe.write_text(retained_themes)
    subprocess.run([javac, '-encoding', 'UTF-8', '-cp', str(classes), '-d', str(classes), str(probe)], check=True)
    assets = ROOT / 'apps/dashboard/src/0x1101L/assets'
    old = folder / 'assets-v2'
    shutil.copytree(assets, old)
    shutil.copytree(ROOT / 'tests/fixtures/theme_v2', old / 'themes', dirs_exist_ok=True)
    def render(path):
        return subprocess.check_output([java, '-Djava.awt.headless=true', '-Ddashboard.assets=' + str(path), '-cp', str(classes), 'ThemeVisualProbe'], text=True).splitlines()
    current, previous = render(assets), render(old)
    # All pages use grid spacing: performance now reserves a themed gap above the footer too.
    expected_changes = {f'{theme}/{profile}/{page}'
                        for theme in ('original', 'aurora_focus')
                        for profile in range(3) for page in range(7)}
    assert len(current) == len(previous) == 42
    assert [line.split()[0] for line in current] == [line.split()[0] for line in previous]
    changes = {a.split()[0] for a, b in zip(current, previous) if a != b}
    assert changes == expected_changes, f'Unexpected v3/v2 visual differences: {changes ^ expected_changes}'
    print('PASS 42 retained v2/v3 renders: Original layout spacing and Aurora Focus inherited typography changed (clock excluded)')
