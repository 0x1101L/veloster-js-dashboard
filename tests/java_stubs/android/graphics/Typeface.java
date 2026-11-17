package android.graphics;
import android.content.res.AssetManager;
public class Typeface {
    public static final int BOLD=1;
    public static final Typeface DEFAULT=new Typeface();
    public static final Typeface DEFAULT_BOLD=new Typeface();
    public static String lastAssetPath;
    public static int assetLoads;
    public static Typeface createFromAsset(AssetManager assets,String path){lastAssetPath=path;assetLoads++;java.io.InputStream input=null;try{input=assets.open(path);return new Typeface();}catch(java.io.IOException missing){throw new RuntimeException(missing);}finally{if(input!=null)try{input.close();}catch(java.io.IOException ignored){}}}
    public static Typeface create(Typeface source,int style){return source;}
}
