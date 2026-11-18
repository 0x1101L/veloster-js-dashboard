package android.graphics;
import java.io.InputStream;
public final class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize,outWidth,outHeight;
    }
    public static Bitmap decodeStream(InputStream input,Object padding,Options options){return null;}
}
