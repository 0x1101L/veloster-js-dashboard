"""Project Java2D doubles: real Canvas geometry, host font/raster approximations."""
GRAPHICS = {
'Bitmap': '''public class Bitmap {
 java.awt.image.BufferedImage image;
 public enum Config {ARGB_8888}
 private Bitmap(java.awt.image.BufferedImage image){this.image=image;}
 public static Bitmap createBitmap(int w,int h,Config config){return new Bitmap(new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB));}
 public Bitmap(int w,int h){image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=image.createGraphics();g.setPaint(new java.awt.GradientPaint(0,0,new java.awt.Color(0x285c74),w,h,new java.awt.Color(0xc86757)));g.fillRect(0,0,w,h);g.setColor(java.awt.Color.WHITE);g.setFont(new java.awt.Font("Dialog",1,18));g.drawString("ARTWORK PREVIEW",16,h/2);g.dispose();}
 public int getWidth(){return image.getWidth();}public int getHeight(){return image.getHeight();}
 public void getPixels(int[] pixels,int offset,int stride,int x,int y,int w,int h){image.getRGB(x,y,w,h,pixels,offset,stride);}
 public void setPixels(int[] pixels,int offset,int stride,int x,int y,int w,int h){image.setRGB(x,y,w,h,pixels,offset,stride);}
}''',
'RectF': '''public class RectF {
 public float left,top,right,bottom;float l,t,r,b;
 public RectF(float a,float y,float c,float d){left=l=a;top=t=y;right=r=c;bottom=b=d;}
 public float width(){return right-left;}public float height(){return bottom-top;}
}''',
'Typeface': '''public class Typeface {
 public static final int BOLD=1;public boolean bold;java.awt.Font face;
 private Typeface(boolean b){bold=b;face=new java.awt.Font("Dialog",b?1:0,12);}
 public static Typeface DEFAULT=new Typeface(false),DEFAULT_BOLD=new Typeface(true);
 public static Typeface create(Typeface source,int style){Typeface t=new Typeface(style==BOLD);t.face=source.face.deriveFont(style);return t;}
 public static Typeface createFromAsset(android.content.res.AssetManager assets,String path){
  try(java.io.InputStream input=assets.open(path)){Typeface t=new Typeface(false);t.face=java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,input);return t;}
  catch(Exception error){throw new IllegalArgumentException(error);}
 }
}''',
'Shader': '''public class Shader {public enum TileMode {CLAMP}}''',
'Matrix': '''public class Matrix {float sx=1,sy=1,tx,ty;public void setScale(float x,float y){sx=x;sy=y;}public boolean postTranslate(float x,float y){tx+=x;ty+=y;return true;}}''',
'BitmapShader': '''public class BitmapShader extends Shader {
 final Bitmap bitmap;Matrix matrix=new Matrix();
 public BitmapShader(Bitmap b,TileMode x,TileMode y){bitmap=b;}
 public void setLocalMatrix(Matrix m){matrix=m;}
 java.awt.TexturePaint paint(){return new java.awt.TexturePaint(bitmap.image,new java.awt.geom.Rectangle2D.Float(matrix.tx,matrix.ty,bitmap.getWidth()*matrix.sx,bitmap.getHeight()*matrix.sy));}
}''',
'LinearGradient': '''public class LinearGradient extends Shader {java.awt.GradientPaint paint;public LinearGradient(float a,float b,float c,float d,int x,int y,TileMode t){paint=new java.awt.GradientPaint(a,b,new java.awt.Color(x,true),c,d,new java.awt.Color(y,true));}}''',
'Paint': '''public class Paint {
 public static int ANTI_ALIAS_FLAG=1;int color;float size=12,width=1;Typeface type=Typeface.DEFAULT;Shader shader;Style style=Style.FILL;Align align=Align.LEFT;public enum Align{LEFT,CENTER,RIGHT}public void setTextAlign(Align a){align=a;}
 public enum Style{FILL,STROKE}public Paint(int f){} public void setColor(int c){color=c;}public void setTextSize(float s){size=s;}public void setTypeface(Typeface t){type=t;}
 public void setShader(Shader s){shader=s;}public void setStyle(Style s){style=s;}public void setStrokeWidth(float w){width=w;}public float getTextSize(){return size;}
 java.awt.Font font(){return type.face.deriveFont(size);}
 public static class FontMetrics {public float ascent,descent;}
 public FontMetrics getFontMetrics(){java.awt.font.LineMetrics lm=font().getLineMetrics("Hg",new java.awt.font.FontRenderContext(null,true,true));FontMetrics m=new FontMetrics();m.ascent=-lm.getAscent();m.descent=lm.getDescent();return m;}
 public float measureText(String s){return (float)font().getStringBounds(s,new java.awt.font.FontRenderContext(null,true,true)).getWidth();}
 void apply(java.awt.Graphics2D g){g.setPaint(shader instanceof LinearGradient?((LinearGradient)shader).paint:shader instanceof BitmapShader?((BitmapShader)shader).paint():new java.awt.Color(color,true));g.setFont(font());g.setStroke(new java.awt.BasicStroke(width));}
}''',
'Path': '''public class Path {
 java.awt.geom.Path2D.Float path=new java.awt.geom.Path2D.Float();
 public enum FillType {WINDING,EVEN_ODD}
 public enum Direction {CW,CCW}
 public void reset(){path.reset();}
 public void setFillType(FillType type){path.setWindingRule(type==FillType.EVEN_ODD?java.awt.geom.Path2D.WIND_EVEN_ODD:java.awt.geom.Path2D.WIND_NON_ZERO);}
 public void addRect(float l,float t,float r,float b,Direction dir){path.append(new java.awt.geom.Rectangle2D.Float(l,t,r-l,b-t),false);}
 public void addRoundRect(RectF r,float rx,float ry,Direction dir){path.append(new java.awt.geom.RoundRectangle2D.Float(r.l,r.t,r.r-r.l,r.b-r.t,2*rx,2*ry),false);}
 public void moveTo(float x,float y){path.moveTo(x,y);}public void lineTo(float x,float y){path.lineTo(x,y);}public void close(){path.closePath();}
 public void cubicTo(float x1,float y1,float x2,float y2,float x3,float y3){path.curveTo(x1,y1,x2,y2,x3,y3);}
}''',
'Canvas': '''public class Canvas {
 java.awt.image.BufferedImage image;java.awt.Graphics2D g;java.util.Stack<java.awt.geom.AffineTransform> stack=new java.util.Stack<java.awt.geom.AffineTransform>();
 java.util.Stack<java.awt.Shape> clips=new java.util.Stack<java.awt.Shape>();public java.util.List<String> texts=new java.util.ArrayList<String>();
 public static final class TextDraw {public final String text;public final float x,y,size,width,deviceX,deviceY,scaleX,scaleY;public final Typeface type;
 TextDraw(String s,float x,float y,Paint p,java.awt.geom.AffineTransform transform){text=s;this.x=x;this.y=y;size=p.size;width=p.measureText(s);type=p.type;java.awt.geom.Point2D pt=transform.transform(new java.awt.geom.Point2D.Float(x,y),null);deviceX=(float)pt.getX();deviceY=(float)pt.getY();scaleX=(float)transform.getScaleX();scaleY=(float)transform.getScaleY();}}
 public java.util.List<TextDraw> commands=new java.util.ArrayList<TextDraw>();
 public Canvas(int w,int h){this(new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB));}
 public Canvas(Bitmap bitmap){this(bitmap.image);}
 private Canvas(java.awt.image.BufferedImage image){this.image=image;g=image.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS,java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);}
 public void drawBitmap(Bitmap b,Object source,RectF d,Paint p){g.drawImage(b.image,(int)d.l,(int)d.t,(int)d.r,(int)d.b,0,0,b.getWidth(),b.getHeight(),null);}public int save(){int n=stack.size()+1;stack.push(g.getTransform());clips.push(g.getClip());return n;}public void restore(){g.setTransform(stack.pop());g.setClip(clips.pop());}public void restoreToCount(int n){while(stack.size()>=n)restore();}public boolean clipRect(float a,float b,float c,float d){g.clip(new java.awt.geom.Rectangle2D.Float(a,b,c-a,d-b));return true;}public void translate(float x,float y){g.translate(x,y);}public void scale(float x,float y){g.scale(x,y);}
 public void drawColor(int c){g.setColor(new java.awt.Color(c,true));g.fillRect(0,0,image.getWidth(),image.getHeight());}
 public int pixel(int x,int y){return image.getRGB(x,y);}
 public void drawText(String s,float x,float y,Paint p){texts.add(s);commands.add(new TextDraw(s,x,y,p,g.getTransform()));p.apply(g);float at=x;if(p.align==Paint.Align.CENTER)at-=p.measureText(s)/2;else if(p.align==Paint.Align.RIGHT)at-=p.measureText(s);g.drawString(s,at,y);}
 private void shape(java.awt.Shape s,Paint p){p.apply(g);if(p.style==Paint.Style.FILL)g.fill(s);else g.draw(s);}
 public void drawArc(RectF r,float start,float sweep,boolean center,Paint p){shape(new java.awt.geom.Arc2D.Float(r.l,r.t,r.r-r.l,r.b-r.t,-start,-sweep,center?java.awt.geom.Arc2D.PIE:java.awt.geom.Arc2D.OPEN),p);}public void drawRoundRect(RectF r,float rx,float ry,Paint p){shape(new java.awt.geom.RoundRectangle2D.Float(r.l,r.t,r.r-r.l,r.b-r.t,rx*2,ry*2),p);}public void drawRect(float a,float b,float c,float d,Paint p){shape(new java.awt.geom.Rectangle2D.Float(a,b,c-a,d-b),p);}
 public void drawPath(Path path,Paint p){shape(path.path,p);}public boolean clipPath(Path path){g.clip(path.path);return true;}public void drawLine(float a,float b,float c,float d,Paint p){p.apply(g);g.draw(new java.awt.geom.Line2D.Float(a,b,c,d));}
 public void drawCircle(float a,float b,float c,Paint p){shape(new java.awt.geom.Ellipse2D.Float(a-c,b-c,2*c,2*c),p);}
 public void write(String file)throws Exception{javax.imageio.ImageIO.write(image,"png",new java.io.File(file));g.dispose();}
}''',
}
