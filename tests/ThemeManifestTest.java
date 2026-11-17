import com.veloster.dashboard.theme.ThemeCatalog;
import com.veloster.dashboard.theme.ThemeJsonParser;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.view.overview.OverviewLayout;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ThemeManifestTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String read(String path)throws Exception{return new String(Files.readAllBytes(Paths.get(path)),Charset.forName("UTF-8"));}
    private static Map<String,String> docs(String root)throws Exception{
        Map<String,String> result=new LinkedHashMap<String,String>();
        result.put("original",read(root+"/original/manifest.json"));
        result.put("aurora_focus",read(root+"/aurora_focus/manifest.json"));
        result.put("carplay_glass_light",read(root+"/carplay_glass_light/manifest.json"));
        result.put("carplay_glass_dark",read(root+"/carplay_glass_dark/manifest.json"));return result;
    }
    private static ThemeCatalog load(String catalog,Map<String,String> manifests){return ThemeCatalog.fromJsonDocuments(catalog,manifests);}
    public static void main(String[] args)throws Exception{
        String catalogJson=read(args[0]);Map<String,String> manifests=docs(args[1]);
        ThemeCatalog catalog=load(catalogJson,manifests);
        check(catalog.size()==4,"bundled catalog parses all manifests");
        check(catalog.find("original").background==0xff040506,"Original manifest tokens parse");
        ThemeSpec focus=catalog.find("aurora_focus");
        check(focus!=null&&focus.columns==5&&focus.rows==4&&"aurora_focus".equals(focus.id),"Focus ID and existing grid parse");
        ThemeSpec glassLight=catalog.find("carplay_glass_light"),glassDark=catalog.find("carplay_glass_dark");
        check(glassLight!=null&&glassDark!=null&&glassLight.columns==5&&glassLight.rows==4&&glassDark.columns==5&&glassDark.rows==4,"CarPlay Glass themes preserve the existing 5x4 layout");
        check(glassLight.background==0xffe6ecf2&&glassLight.panel==0x99ffffff&&glassLight.accent==0xff007aff,"light theme color tokens parse");
        check(glassDark.background==0xff0b0e14&&glassDark.panel==0x661c222d&&glassDark.accent==0xff3898ff,"dark theme color tokens parse");
        check("fonts/Pretendard-Regular.otf".equals(glassLight.bodyFont)&&glassLight.backgroundAsset.length()==0,"light theme declares bundled Pretendard with a color background");
        check(Files.size(Paths.get(args[1]+"/carplay_glass_light/fonts/Pretendard-Regular.otf"))>100000&&Files.size(Paths.get(args[1]+"/carplay_glass_dark/fonts/Pretendard-Regular.otf"))>100000,"both theme bundles include the Pretendard font asset");
        Map<String,String> paged=new LinkedHashMap<String,String>(manifests);
        paged.put("aurora_focus",paged.get("aurora_focus").replace("\"columns\": 5, \"rows\": 4","\"columns\": 4, \"rows\": 3"));
        ThemeSpec pagedTheme=load(catalogJson,paged).find("aurora_focus");
        OverviewLayout pagedLayout=new OverviewLayout();pagedLayout.theme(pagedTheme);
        check(pagedTheme.columns==4&&pagedTheme.rows==3&&pagedLayout.pageCount()==2&&pagedLayout.page(1),"Supported 4x3 themes retain overview pagination");
        check("original".equals(load("{\"schemaVersion\":1,\"themeIds\":[\"original\",\"aurora_focus\",\"aurora_focus\"]}",manifests).resolve("aurora_focus").id),"duplicate catalog ID falls back to Original only");
        check("original".equals(load(catalogJson,new LinkedHashMap<String,String>()).resolve("aurora_focus").id),"missing manifests fall back safely");
        check("original".equals(load(catalogJson.replace("\"schemaVersion\": 1","\"schemaVersion\": 2"),manifests).resolve("aurora_focus").id),"unsupported catalog version falls back");
        Map<String,String> bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus",bad.get("aurora_focus").replace("#FF65E0D0","cyan"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id),"invalid token value rejects catalog and falls back");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus",bad.get("aurora_focus").replace("\"rows\": 4","\"rows\": 8"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id),"out-of-range layout rejects catalog");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus",bad.get("aurora_focus").replace("\"columns\": 5","\"columns\": 4"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id),"unsupported renderer grid rejects catalog");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("original",bad.get("original").replace("#FF040506","#FF000000"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id)&&load(catalogJson,bad).size()==1,"altered Original manifest falls back to canonical Original");
        boolean duplicateKey=false;
        try{ThemeJsonParser.parse("{\"id\":\"a\",\"id\":\"b\"}");}catch(IllegalArgumentException expected){duplicateKey=true;}
        check(duplicateKey,"JSON parser rejects duplicate object keys");
        boolean malformed=false;
        try{ThemeJsonParser.parse("{\"value\":01}");}catch(IllegalArgumentException expected){malformed=true;}
        check(malformed,"JSON parser rejects malformed numbers");
        boolean excessiveDepth=false;
        String nested="{";for(int i=0;i<70;i++)nested+="\"x\":{";nested+="\"v\":1";for(int i=0;i<71;i++)nested+="}";
        try{ThemeJsonParser.parse(nested);}catch(IllegalArgumentException expected){excessiveDepth=true;}
        check(excessiveDepth,"JSON nesting is bounded");
        System.out.println("PASS ThemeManifestTest");
    }
}
