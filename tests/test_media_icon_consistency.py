"""Check themed media icon colors and rendering on the desktop graphics adapter."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from dashboard_runtime import compile_runtime

PROBE = r'''
import android.app.Activity;
import android.graphics.Canvas;
import com.veloster.dashboard.theme.ThemeCatalog;
import com.veloster.dashboard.theme.ThemePalette;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.view.DashboardPage;
import com.veloster.dashboard.view.MediaBar;
import java.lang.reflect.*;

public class MediaIconProbe {
 public static void main(String[] args)throws Exception{
  ThemeCatalog catalog=ThemeCatalog.load(new Activity());
  ThemeSpec original=catalog.find("original"),theme=catalog.find("f1_2");
  if(original==null||theme==null)throw new AssertionError("theme missing");
  if(ThemePalette.primary(original,0xffffffff)!=0xffffffff)throw new AssertionError("Original media icons must be white");
  if(ThemePalette.primary(theme,0xffffffff)!=0xff000000)throw new AssertionError("Liquid media icons must be black");
  Class<?> values=Class.forName("com.veloster.dashboard.view.MediaBar$Values");
  Field normal=MediaBar.class.getDeclaredField("NORMAL");normal.setAccessible(true);
  Method panel=MediaBar.class.getDeclaredMethod("drawPanelTransport",Canvas.class,ThemeSpec.class,
    com.veloster.dashboard.theme.ThemeResources.class,DashboardPage.Bounds.class,int.class,boolean.class,values);
  panel.setAccessible(true);MediaBar bar=new MediaBar();
  for(int action:new int[]{MediaBar.PREVIOUS,MediaBar.PLAY,MediaBar.NEXT}){
   Canvas c=new Canvas(100,100);
   panel.invoke(bar,c,theme,null,new DashboardPage.Bounds(0,0,100,100),action,action==MediaBar.PLAY,normal.get(null));
   java.awt.image.BufferedImage image=c.image();int color=theme.textPrimary&0xffffff;int pixels=0;
   for(int y=35;y<65;y++)for(int x=35;x<65;x++){
    int pixel=image.getRGB(x,y);
    if((pixel>>>24)!=0&&(pixel&0xffffff)==color)pixels++;
   }
   if(pixels<10)throw new AssertionError("missing primary-color icon for action "+action+": "+pixels);
  }
  System.out.println("PASS media panel icons share the primary text color");
 }
}
'''


class MediaIconConsistencyTest(unittest.TestCase):
    def test_background_free_controls_share_color(self):
        with tempfile.TemporaryDirectory(prefix='media-icons-') as folder:
            java, classes = compile_runtime(folder)
            source = Path(folder) / 'MediaIconProbe.java'
            source.write_text(PROBE, encoding='utf-8')
            javac = str(Path(java).with_name('javac'))
            subprocess.run([javac, '-encoding', 'UTF-8', '-cp', str(classes),
                            '-d', str(classes), str(source)], check=True, capture_output=True, text=True)
            result = subprocess.run([java, '-Djava.awt.headless=true',
                                     '-Ddashboard.assets=' + str(ROOT / 'apps/dashboard/src/0x1101L/assets'),
                                     '-cp', str(classes), 'MediaIconProbe'],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
