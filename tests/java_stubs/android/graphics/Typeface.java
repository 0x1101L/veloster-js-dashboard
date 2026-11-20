package android.graphics;
import android.content.res.AssetManager;
public class Typeface {
    public static final int BOLD=1;
    public static final Typeface DEFAULT=new Typeface();
    public static final Typeface DEFAULT_BOLD=new Typeface();
    public static String lastAssetPath;
    public static int assetLoads;
    public static String lastVariationSettings;
    public static int lastBuilderWeight;
    public static boolean failVariableBuild;
    public String sourceAssetPath;
    public int sourceFontWeight;
    public static Typeface createFromAsset(AssetManager assets,String path){lastAssetPath=path;assetLoads++;java.io.InputStream input=null;try{input=assets.open(path);Typeface face=new Typeface();face.sourceAssetPath=path;return face;}catch(java.io.IOException missing){throw new RuntimeException(missing);}finally{if(input!=null)try{input.close();}catch(java.io.IOException ignored){}}}
    public static Typeface create(Typeface source,int style){return source;}
    public static final class Builder {
        private final AssetManager assets;
        private final String path;
        private int weight;
        public Builder(AssetManager assets,String path){this.assets=assets;this.path=path;}
        public Builder setFontVariationSettings(String value){lastVariationSettings=value;return this;}
        public Builder setWeight(int value){weight=value;lastBuilderWeight=value;return this;}
        public Typeface build(){if(failVariableBuild)return null;Typeface face=new Typeface();face.sourceAssetPath=path;face.sourceFontWeight=weight;return face;}
    }
}
