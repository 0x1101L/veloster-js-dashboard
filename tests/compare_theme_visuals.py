"""Compare current bundled-theme pixels with a git reference using identical host shims."""
import sys,tempfile,subprocess,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1];sys.path[:0]=[str(root/'tests'),str(root/'tools')]
from test_theme_v2_renderer import BASELINE
import dashboard_runtime as runtime
from test_quick_entry_runtime import java_tools
reference=sys.argv[1] if len(sys.argv)>1 else "HEAD"
with tempfile.TemporaryDirectory(prefix='theme-baseline-') as folder:
 folder=Path(folder);java,classes=runtime.compile_runtime(folder);_,javac=java_tools()
 probe=folder/'ThemeVisualProbe.java';probe.write_text(BASELINE)
 subprocess.run([javac,'-encoding','UTF-8','-cp',str(classes),'-d',str(classes),str(probe)],check=True)
 def render(assets):
  return subprocess.check_output([java,'-Djava.awt.headless=true','-Ddashboard.assets='+str(assets),'-cp',str(classes),'ThemeVisualProbe'],text=True).splitlines()
 current=render(root/'apps/dashboard/src/0x1101L/assets')
 # Recompile tracked pre-change Java sources in the isolated host only.
 paths=subprocess.check_output(['git','diff','--name-only','--diff-filter=DM',reference,'--','apps/dashboard'],cwd=root,text=True).splitlines()
 sources=[]
 for rel in paths:
  if rel.endswith('.java'):
   target=folder/'baseline-sources'/rel;target.parent.mkdir(parents=True,exist_ok=True)
   target.write_bytes(subprocess.check_output(['git','show',reference+':'+rel],cwd=root));sources.append(str(target))
 subprocess.run([javac,'-encoding','UTF-8','-cp',str(classes),'-d',str(classes),*sources],check=True)
 assets=folder/'baseline-assets';shutil.copytree(root/'apps/dashboard/src/0x1101L/assets',assets)
 for rel in paths:
  if rel.startswith(('apps/dashboard/src/main/assets/','apps/dashboard/src/0x1101L/assets/')):
   (assets/rel.split('assets/',1)[1]).write_bytes(subprocess.check_output(['git','show',reference+':'+rel],cwd=root))
 baseline=render(assets)
 mismatches=[(a,b) for a,b in zip(current,baseline) if a!=b]
 print('Compared',len(current),'renders; differences',len(mismatches))
 for a,b in mismatches[:10]:print('current',a,'baseline',b)
 assert len(current)==84 and current==baseline
 print('PASS all 4 themes x 3 profiles x 7 pages preserve baseline pixels (clock excluded)')
