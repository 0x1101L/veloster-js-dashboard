package com.veloster.dashboard.theme;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads only the reviewed static catalog and manifests packaged in the Dashboard APK. */
public final class ThemeCatalog {
    public static final String ASSET_ROOT="themes/";
    private static final int MAX_CATALOG_BYTES=16384;
    private static final int MAX_MANIFEST_BYTES=32768;
    private final Map<String,ThemeSpec> themes;

    public ThemeCatalog(Context context) {
        if(context==null)throw new IllegalArgumentException("context required");
        LinkedHashMap<String,String> documents=new LinkedHashMap<String,String>();
        String catalogJson;
        try {
            AssetManager assets=context.getAssets();
            catalogJson=read(assets,ASSET_ROOT+"catalog.json",MAX_CATALOG_BYTES);
            Map<String,Object> parsed=object(ThemeJsonParser.parse(catalogJson),"catalog");
            List<Object> ids=array(parsed.get("themeIds"),"themeIds");
            for(Object item:ids)if(item instanceof String){String id=(String)item;if(id.matches("[a-z][a-z0-9_]{0,47}"))documents.put(id,read(assets,ASSET_ROOT+id+"/manifest.json",MAX_MANIFEST_BYTES));}
        } catch(Exception e) { catalogJson=""; }
        ThemeCatalog loaded=fromJsonDocuments(catalogJson,documents);
        themes=loaded.themes;
    }

    private ThemeCatalog(Map<String,ThemeSpec> values){themes=Collections.unmodifiableMap(new LinkedHashMap<String,ThemeSpec>(values));}

    public static ThemeCatalog fromJsonDocuments(String catalogJson,Map<String,String> manifests){
        LinkedHashMap<String,ThemeSpec> values=new LinkedHashMap<String,ThemeSpec>();
        try {
            if(catalogJson==null||catalogJson.getBytes("UTF-8").length>MAX_CATALOG_BYTES)throw new IllegalArgumentException("catalog exceeds size limit");
            Object parsed=ThemeJsonParser.parse(catalogJson);
            Map<String,Object> catalog=object(parsed,"catalog");
            exactKeys(catalog,new String[]{"schemaVersion","themeIds"});
            if(integer(catalog,"schemaVersion")!=ThemeSpec.VERSION)throw new IllegalArgumentException("unsupported catalog version");
            List<Object> ids=array(catalog.get("themeIds"),"themeIds");
            if(ids.isEmpty()||ids.size()>32)throw new IllegalArgumentException("catalog theme count out of range");
            for(Object item:ids){
                if(!(item instanceof String))throw new IllegalArgumentException("theme ID must be a string");
                String id=(String)item;
                if(!id.matches("[a-z][a-z0-9_]{0,47}"))throw new IllegalArgumentException("invalid theme ID");
                String manifest=manifests==null?null:manifests.get(id);
                if(manifest==null||manifest.getBytes("UTF-8").length>MAX_MANIFEST_BYTES)throw new IllegalArgumentException("manifest missing or oversized");
                ThemeSpec spec=parseManifest(id,manifest);
                if(values.put(id,spec)!=null)throw new IllegalArgumentException("duplicate theme ID");
            }
            ThemeSpec original=values.get("original");
            if(original==null||!original.isOriginal())throw new IllegalArgumentException("catalog must contain Original");
        } catch(Exception e) {
            values.clear();
            ThemeSpec original=ThemeSpec.original();
            values.put(original.id,original);
        }
        return new ThemeCatalog(values);
    }

    public static ThemeCatalog load(Context context){return new ThemeCatalog(context);}

    /** Test/compatibility constructor. Production loads the bundled JSON catalog. */
    public ThemeCatalog() {
        LinkedHashMap<String,ThemeSpec> values=new LinkedHashMap<String,ThemeSpec>();
        values.put("original",ThemeSpec.original());values.put("aurora_focus",ThemeSpec.auroraFocus());
        themes=Collections.unmodifiableMap(values);
    }

    private static ThemeSpec parseManifest(String id,String json){
        Map<String,Object> root=object(ThemeJsonParser.parse(json),"manifest");
        exactKeys(root,new String[]{"schemaVersion","id","displayName","tokens","layout","fonts","assets"});
        if(integer(root,"schemaVersion")!=ThemeSpec.VERSION)throw new IllegalArgumentException("unsupported manifest version");
        if(!id.equals(string(root,"id")))throw new IllegalArgumentException("manifest ID does not match catalog");
        String display=string(root,"displayName");if(display.length()==0||display.length()>64)throw new IllegalArgumentException("invalid display name");
        Map<String,Object> tokens=object(root.get("tokens"),"tokens");
        exactKeys(tokens,new String[]{"background","panel","panelRaised","stroke","textPrimary","textSecondary","accent","warning","critical","unavailable","cutPanels"});
        Map<String,Object> layout=object(root.get("layout"),"layout");exactKeys(layout,new String[]{"columns","rows"});
        int columns=integer(layout,"columns"),rows=integer(layout,"rows");
        if(!((columns==5&&rows==4)||(columns==4&&rows==3)))throw new IllegalArgumentException("unsupported theme grid");
        Map<String,Object> fonts=object(root.get("fonts"),"fonts");exactKeys(fonts,new String[]{"body","numeric","display"});
        String body=fontPath(string(fonts,"body")),numeric=fontPath(string(fonts,"numeric")),displayFont=fontPath(string(fonts,"display"));
        Map<String,Object> assets=object(root.get("assets"),"assets");exactKeys(assets,new String[]{"background"});
        String backgroundAsset=assetPath(string(assets,"background"));
        ThemeSpec spec=ThemeSpec.fromManifest(id,display,color(tokens,"background"),color(tokens,"panel"),color(tokens,"panelRaised"),
                color(tokens,"stroke"),color(tokens,"textPrimary"),color(tokens,"textSecondary"),color(tokens,"accent"),
                color(tokens,"warning"),color(tokens,"critical"),color(tokens,"unavailable"),columns,rows,
                bool(tokens,"cutPanels"),body,numeric,displayFont,backgroundAsset);
        if(spec.isOriginal()&&!same(spec,ThemeSpec.original()))throw new IllegalArgumentException("Original manifest must match the built-in baseline");
        return spec;
    }

    private static boolean same(ThemeSpec a,ThemeSpec b){return a.id.equals(b.id)&&a.displayName.equals(b.displayName)&&a.background==b.background&&a.panel==b.panel&&a.panelRaised==b.panelRaised&&a.stroke==b.stroke&&a.textPrimary==b.textPrimary&&a.textSecondary==b.textSecondary&&a.accent==b.accent&&a.warning==b.warning&&a.critical==b.critical&&a.unavailable==b.unavailable&&a.columns==b.columns&&a.rows==b.rows&&a.cutPanels==b.cutPanels&&a.bodyFont.equals(b.bodyFont)&&a.numericFont.equals(b.numericFont)&&a.displayFont.equals(b.displayFont)&&a.backgroundAsset.equals(b.backgroundAsset);}

    private static String fontPath(String path){if(path.length()==0)return path;if(!path.matches("fonts/[A-Za-z0-9_.-]+\\.(ttf|otf)"))throw new IllegalArgumentException("invalid font path");return path;}
    private static String assetPath(String path){if(path.length()==0)return path;if(!path.matches("backgrounds/[A-Za-z0-9_.-]+\\.(png|jpg|jpeg)"))throw new IllegalArgumentException("invalid background path");return path;}
    private static String read(AssetManager assets,String path,int max)throws IOException{
        InputStream input=assets.open(path);try{ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int total=0,n;while((n=input.read(buffer))!=-1){total+=n;if(total>max)throw new IOException("asset exceeds size limit");output.write(buffer,0,n);}return new String(output.toByteArray(),"UTF-8");}finally{input.close();}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value,String name){if(!(value instanceof Map))throw new IllegalArgumentException(name+" must be an object");return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<Object> array(Object value,String name){if(!(value instanceof List))throw new IllegalArgumentException(name+" must be an array");return (List<Object>)value;}
    private static void exactKeys(Map<String,Object> value,String[] keys){if(value.size()!=keys.length)throw new IllegalArgumentException("unexpected or missing keys");for(String key:keys)if(!value.containsKey(key))throw new IllegalArgumentException("missing key: "+key);}
    private static String string(Map<String,Object> value,String key){Object v=value.get(key);if(!(v instanceof String))throw new IllegalArgumentException(key+" must be a string");return (String)v;}
    private static int integer(Map<String,Object> value,String key){Object v=value.get(key);if(!(v instanceof Long)||((Long)v)<Integer.MIN_VALUE||((Long)v)>Integer.MAX_VALUE)throw new IllegalArgumentException(key+" must be an integer");return ((Long)v).intValue();}
    private static boolean bool(Map<String,Object> value,String key){Object v=value.get(key);if(!(v instanceof Boolean))throw new IllegalArgumentException(key+" must be boolean");return ((Boolean)v).booleanValue();}
    private static int color(Map<String,Object> value,String key){String raw=string(value,key);if(!raw.matches("#[0-9A-Fa-f]{8}"))throw new IllegalArgumentException("invalid color: "+key);return (int)Long.parseLong(raw.substring(1),16);}

    public ThemeSpec find(String id){return themes.get(id);}
    public ThemeSpec resolve(String id){ThemeSpec found=find(id);return found==null?themes.get("original"):found;}
    public int size(){return themes.size();}
    public Map<String,ThemeSpec> all(){return themes;}
}
