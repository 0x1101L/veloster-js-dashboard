package com.veloster.dashboard.theme;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Loads only the reviewed static catalog and manifests packaged in the Dashboard APK. */
public final class ThemeCatalog {
    public static final String ASSET_ROOT="themes/";
    private static final int MAX_CATALOG_BYTES=16384;
    private static final int MAX_MANIFEST_BYTES=32768;
    private static final int MAX_DOCUMENT_BYTES=2097152;
    private final Map<String,ThemeSpec> themes;
    private final Map<String,ThemeSpec.Wallpaper> wallpapers;

    public ThemeCatalog(Context context) {
        if(context==null)throw new IllegalArgumentException("context required");
        LinkedHashMap<String,String> documents=new LinkedHashMap<String,String>();
        String catalogJson;
        try {
            AssetManager assets=context.getAssets();
            catalogJson=read(assets,ASSET_ROOT+"catalog.json",MAX_CATALOG_BYTES);
            Map<String,Object> parsed=object(ThemeJsonParser.parse(catalogJson),"catalog");
            List<Object> ids=array(parsed.get("themeIds"),"themeIds");
            if(ids.isEmpty()||ids.size()>32)throw new IllegalArgumentException("catalog theme count out of range");
            int total=0;
            for(Object item:ids)if(item instanceof String){
                String id=(String)item;
                if(id.matches("[a-z][a-z0-9_]{0,47}")){
                    String manifest=read(assets,ASSET_ROOT+id+"/manifest.json",MAX_MANIFEST_BYTES);
                    total+=manifest.getBytes("UTF-8").length;if(total>MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("theme documents exceed size limit");
                    documents.put(id,manifest);
                    Map<String,Object> root=object(ThemeJsonParser.parse(manifest),"manifest");
                    if(integer(root,"schemaVersion")==3){
                        Map<String,Object> files=object(root.get("files"),"files");
                        allowedKeys(files,new String[]{"colors","fonts","components","overview","shell","f1","gauges","graphs","performance","settings","diagnostics"});
                        for(Object path:files.values()){
                            if(!(path instanceof String)||!validFile((String)path))throw new IllegalArgumentException("invalid theme file path");
                            String key=id+"/"+(String)path;
                            String file=read(assets,ASSET_ROOT+key,MAX_MANIFEST_BYTES);total+=file.getBytes("UTF-8").length;
                            if(total>MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("theme documents exceed size limit");documents.put(key,file);
                        }
                    }
                }
            }
        } catch(Exception e) { catalogJson=""; }
        ThemeCatalog loaded=fromJsonDocuments(catalogJson,documents);
        themes=loaded.themes;
        wallpapers=loaded.wallpapers;
    }

    private ThemeCatalog(Map<String,ThemeSpec> values,Map<String,ThemeSpec.Wallpaper> backgrounds){
        themes=Collections.unmodifiableMap(new LinkedHashMap<String,ThemeSpec>(values));
        wallpapers=Collections.unmodifiableMap(new LinkedHashMap<String,ThemeSpec.Wallpaper>(backgrounds));
    }

    public static ThemeCatalog fromJsonDocuments(String catalogJson,Map<String,String> manifests){
        LinkedHashMap<String,ThemeSpec> values=new LinkedHashMap<String,ThemeSpec>();
        Map<String,ThemeSpec.Wallpaper> backgrounds=new LinkedHashMap<String,ThemeSpec.Wallpaper>();
        try {
            if(catalogJson==null||catalogJson.getBytes("UTF-8").length>MAX_CATALOG_BYTES)throw new IllegalArgumentException("catalog exceeds size limit");
            int total=0;if(manifests!=null)for(String document:manifests.values()){if(document==null)throw new IllegalArgumentException("null document");total+=document.getBytes("UTF-8").length;if(total>MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("theme documents exceed size limit");}
            Object parsed=ThemeJsonParser.parse(catalogJson);
            Map<String,Object> catalog=object(parsed,"catalog");
            exactKeys(catalog,new String[]{"schemaVersion","themeIds"});
            int catalogVersion=integer(catalog,"schemaVersion");
            if(catalogVersion!=2&&catalogVersion!=3)throw new IllegalArgumentException("unsupported catalog version");
            List<Object> ids=array(catalog.get("themeIds"),"themeIds");
            if(ids.isEmpty()||ids.size()>32)throw new IllegalArgumentException("catalog theme count out of range");
            Set<String> catalogIds=new HashSet<String>();
            for(Object item:ids){
                if(!(item instanceof String)||!((String)item).matches("[a-z][a-z0-9_]{0,47}")||!catalogIds.add((String)item))
                    throw new IllegalArgumentException("invalid or duplicate catalog ID");
            }
            for(Object item:ids){
                if(!(item instanceof String))throw new IllegalArgumentException("theme ID must be a string");
                String id=(String)item;
                if(!id.matches("[a-z][a-z0-9_]{0,47}"))throw new IllegalArgumentException("invalid theme ID");
                String manifest=manifests==null?null:manifests.get(id);
                if(manifest==null||manifest.getBytes("UTF-8").length>MAX_MANIFEST_BYTES)throw new IllegalArgumentException("manifest missing or oversized");
                Map<String,Object> document=object(ThemeJsonParser.parse(manifest),"manifest");
                int version=integer(document,"schemaVersion");
                ThemeSpec spec;
                if(version==2)spec=parseManifest(id,manifest);
                else if(version==3&&catalogVersion==3){
                    Resolved resolved=resolveV3(id,manifests,catalogIds,new HashSet<String>(),0);
                    Map<String,Object> resolvedValues=new LinkedHashMap<String,Object>(resolved.root);
                    Object shell=resolvedValues.remove("shell");
                    Object overview=resolvedValues.remove("overview");
                    Object cardDefaults=resolvedValues.remove("cardDefaults"),cardTypes=resolvedValues.remove("cardTypes");
                    Object f1=resolvedValues.remove("f1");
                    Object f1Grid=resolvedValues.remove("f1Grid");
                    Object viewports=resolvedValues.remove("viewports");
                    Object weights=resolvedValues.remove("weights");
                    Object surfaceStyles=resolvedValues.remove("surfaceStyles");
                    Object surfaceBlur=resolvedValues.remove("surfaceBlur");
                    Object mediaControls=resolvedValues.remove("mediaControls");
                    Object mediaFooter=resolvedValues.remove("mediaFooter");
                    Object mediaButtonGap=resolvedValues.remove("mediaButtonGap");
                    Object backdrop=resolvedValues.remove("backdrop");
                    Object palette=resolvedValues.remove("palette");
                    Object decorations=resolvedValues.remove("decorations");
                    Object fills=resolvedValues.remove("fills");
                    Object wallpapers=resolvedValues.remove("wallpapers");
                    spec=parseManifestMap(id,resolvedValues,false,backdrop==null?null:parseBackdrop(id,object(backdrop,"backdrop")));
                    if(palette!=null){
                        if("theme".equals(palette))spec.palette(ThemeSpec.Palette.THEME);
                        else if("componentDefaults".equals(palette))spec.palette(ThemeSpec.Palette.COMPONENT_DEFAULTS);
                        else throw new IllegalArgumentException("unsupported palette");
                    }
                    if(decorations!=null)parseDecorations(spec,object(decorations,"decorations"));
                    if(fills!=null)parseFills(spec,object(fills,"fills"));
                    if(spec.isOriginal()&&!spec.sameComponentAppearance(ThemeSpec.original()))
                        throw new IllegalArgumentException("Original component appearance is immutable");
                    if(surfaceStyles!=null)parseSurfaceStyles(spec,object(surfaceStyles,"surfaceStyles"));
                    if(surfaceBlur!=null)parseSurfaceBlur(spec,object(surfaceBlur,"surfaceBlur"));
                    if(mediaControls!=null){
                        if(spec.isOriginal()||!(mediaControls instanceof String))throw new IllegalArgumentException("invalid mediaControls");
                        if("container".equals(mediaControls))spec.mediaControls(ThemeSpec.MediaControls.CONTAINER);
                        else if("buttons".equals(mediaControls))spec.mediaControls(ThemeSpec.MediaControls.BUTTONS);
                        else if(!"both".equals(mediaControls))throw new IllegalArgumentException("invalid mediaControls");
                    }
                    if(mediaFooter!=null)parseMediaFooter(spec,object(mediaFooter,"mediaFooter"));
                    if(mediaButtonGap!=null){
                        if(spec.isOriginal())throw new IllegalArgumentException("Original media button gap is immutable");
                        if(!(mediaButtonGap instanceof Number))throw new IllegalArgumentException("invalid mediaButtonGap");
                        int gap=((Number)mediaButtonGap).intValue();
                        if(gap<0||gap>64||((Number)mediaButtonGap).doubleValue()!=gap)throw new IllegalArgumentException("invalid mediaButtonGap");
                        spec.mediaButtonGap(Integer.valueOf(gap));
                    }
                    if(shell!=null){
                        spec.shell(parseShell(object(shell,"shell")));
                        if(spec.isOriginal()&&spec.shell().left())throw new IllegalArgumentException("Original shell is immutable");
                    }
                    if(overview!=null){
                        if(spec.isOriginal())throw new IllegalArgumentException("Original overview is immutable");
                        spec.overview(parseOverview(object(overview,"overview")));
                    }
                    if(cardDefaults!=null||cardTypes!=null){
                        if(spec.isOriginal())throw new IllegalArgumentException("Original overview card styles are immutable");
                        parseOverviewCardStyles(spec,cardDefaults,cardTypes);
                    }
                    if(f1!=null){if(spec.isOriginal())throw new IllegalArgumentException("Original F1 is immutable");spec.f1(parseF1(object(f1,"f1")));}
                    if(f1Grid!=null){
                        if(spec.isOriginal()||f1!=null)throw new IllegalArgumentException("F1 grid requires a non-original theme without legacy F1 coordinates");
                        spec.f1Grid(parseF1Grid(object(f1Grid,"f1Grid")));
                    }
                    if(viewports!=null){if(spec.isOriginal())throw new IllegalArgumentException("Original viewports are immutable");spec.viewports(parseViewports(object(viewports,"viewports")));}
                    if(weights!=null){
                        Map<String,Object> rawWeights=object(weights,"weights");allowedKeys(rawWeights,new String[]{"body","numeric","display"});
                        Map<String,String> fontWeights=new LinkedHashMap<String,String>();
                        for(String role:rawWeights.keySet()){String weight=string(rawWeights,role);if(!(weight.equals("auto")||weight.equals("regular")||weight.equals("bold"))||spec.isOriginal()&&!weight.equals("auto"))throw new IllegalArgumentException("invalid font weight");fontWeights.put(role,weight);}
                        spec.weights(fontWeights);
                    }
                    validateContentSize(spec);
                    spec.bodyFontOwner=resolved.body;spec.numericFontOwner=resolved.numeric;
                    spec.displayFontOwner=resolved.display;spec.backgroundAssetOwner=resolved.background;
                    if(wallpapers!=null){
                        List<ThemeSpec.Wallpaper> options=parseWallpapers(array(wallpapers,"wallpapers"),resolved.wallpaper);
                        String defaultId="";
                        for(ThemeSpec.Wallpaper option:options){
                            if(option.owner.equals(spec.backgroundAssetOwner)&&option.asset.equals(spec.backgroundAsset)){
                                if(defaultId.isEmpty()||option.isDefault)defaultId=option.id;
                            }else if(option.isDefault&&id.equals(resolved.wallpaper)){
                                throw new IllegalArgumentException("default wallpaper must match assets.background in its bundle");
                            }
                        }
                        spec.wallpapers(options,defaultId);
                    }
                }else throw new IllegalArgumentException("unsupported manifest version");
                if(values.put(id,spec)!=null)throw new IllegalArgumentException("duplicate theme ID");
            }
            ThemeSpec original=values.get("original");
            if(original==null||!original.isOriginal())throw new IllegalArgumentException("catalog must contain Original");
            for(ThemeSpec spec:values.values()){
                if(spec.backgroundAsset.isEmpty()){
                    ThemeSpec.Wallpaper option=new ThemeSpec.Wallpaper(spec);
                    List<ThemeSpec.Wallpaper> options=new ArrayList<ThemeSpec.Wallpaper>(spec.wallpapers());
                    options.add(option);
                    spec.wallpapers(options,option.id);
                }
                if(!spec.backgroundAsset.isEmpty()&&spec.defaultWallpaperId().isEmpty()){
                    ThemeSpec owner=values.get(spec.backgroundAssetOwner);
                    ThemeSpec.Wallpaper option=null;
                    if(owner!=null)for(ThemeSpec.Wallpaper candidate:owner.wallpapers()){
                        if(candidate.owner.equals(owner.id)&&candidate.asset.equals(spec.backgroundAsset)){
                            option=candidate;if(candidate.isDefault)break;
                        }
                    }
                    // A theme can publish its default image without defining a wallpaper list.
                    if(option==null)option=new ThemeSpec.Wallpaper(spec.backgroundAssetOwner+":"+spec.backgroundAsset,
                        owner==null?spec.displayName:owner.displayName,spec.backgroundAsset,"",spec.backgroundAssetOwner,false);
                    List<ThemeSpec.Wallpaper> options=new ArrayList<ThemeSpec.Wallpaper>(spec.wallpapers());
                    options.add(option);
                    spec.wallpapers(options,option.id);
                }
                for(ThemeSpec.Wallpaper option:spec.wallpapers()){
                    ThemeSpec.Wallpaper previous=backgrounds.get(option.id);
                    if(previous!=null&&(!previous.owner.equals(option.owner)||!previous.asset.equals(option.asset)))
                        throw new IllegalArgumentException("duplicate wallpaper ID: "+option.id);
                    if(previous==null)backgrounds.put(option.id,option);
                }
            }
        } catch(Exception e) {
            values.clear();
            backgrounds.clear();
            ThemeSpec original=ThemeSpec.original();
            values.put(original.id,original);
            ThemeSpec.Wallpaper color=new ThemeSpec.Wallpaper(original);
            List<ThemeSpec.Wallpaper> fallback=new ArrayList<ThemeSpec.Wallpaper>();
            fallback.add(color);original.wallpapers(fallback,color.id);backgrounds.put(color.id,color);
        }
        return new ThemeCatalog(values,backgrounds);
    }

    public static ThemeCatalog load(Context context){return new ThemeCatalog(context);}

    /** Test/compatibility constructor. Production loads the bundled JSON catalog. */
    public ThemeCatalog() {
        LinkedHashMap<String,ThemeSpec> values=new LinkedHashMap<String,ThemeSpec>();
        values.put("original",ThemeSpec.original());values.put("aurora_focus",ThemeSpec.auroraFocus());
        themes=Collections.unmodifiableMap(values);
        wallpapers=Collections.emptyMap();
    }

    private static final class Resolved {
        Map<String,Object> root;
        String body,numeric,display,background,wallpaper;
        Resolved(String owner,Map<String,Object> value){root=value;body=owner;numeric=owner;display=owner;background=owner;wallpaper=owner;}
    }

    /** Resolve once when the catalog is loaded; no JSON parsing occurs while drawing. */
    private static Resolved resolveV3(String id,Map<String,String> docs,Set<String> catalogIds,Set<String> visiting,int depth)throws Exception{
        if(depth>8||!catalogIds.contains(id)||!visiting.add(id))throw new IllegalArgumentException("invalid theme ancestry");
        String source=docs.get(id);
        if(source==null||source.getBytes("UTF-8").length>MAX_MANIFEST_BYTES)throw new IllegalArgumentException("manifest missing or oversized");
        Map<String,Object> manifest=object(ThemeJsonParser.parse(source),"manifest");
        int version=integer(manifest,"schemaVersion");
        if(version==2){
            parseManifestMap(id,manifest,true,null);
            visiting.remove(id);
            return new Resolved(id,manifest);
        }
        if(version!=3)throw new IllegalArgumentException("unsupported parent version");
        allowedKeys(manifest,new String[]{"schemaVersion","id","displayName","extends","files"});
        if(!id.equals(string(manifest,"id")))throw new IllegalArgumentException("manifest ID does not match catalog");
        String name=string(manifest,"displayName");
        if(name.length()==0||name.length()>64)throw new IllegalArgumentException("invalid display name");
        Resolved result;
        if(manifest.containsKey("extends")){
            String parent=string(manifest,"extends");
            result=resolveV3(parent,docs,catalogIds,visiting,depth+1);
        }else{
            result=new Resolved(id,new LinkedHashMap<String,Object>());
        }
        Map<String,Object> files=object(manifest.get("files"),"files");
        allowedKeys(files,new String[]{"colors","fonts","components","overview","shell","f1","gauges","graphs","performance","settings","diagnostics"});
        for(Map.Entry<String,Object> entry:files.entrySet()){
            if(!(entry.getValue() instanceof String)||!validFile((String)entry.getValue()))throw new IllegalArgumentException("invalid theme file path");
            String json=docs.get(id+"/"+entry.getValue());
            if(json==null||json.getBytes("UTF-8").length>MAX_MANIFEST_BYTES)throw new IllegalArgumentException("theme file missing or oversized");
            Map<String,Object> part=object(ThemeJsonParser.parse(json),"theme file");
            String role=entry.getKey();
            if("colors".equals(role))allowedKeys(part,new String[]{"tokens","backdrop","palette"});
            else if("fonts".equals(role))allowedKeys(part,new String[]{"fonts","typography","weights"});
            else if("components".equals(role))allowedKeys(part,new String[]{"spacing","material","surfaceStyles","surfaceBlur","mediaControls","mediaFooter","mediaButtonGap","decorations","fills","assets","wallpapers"});
            else if("overview".equals(role))allowedKeys(part,new String[]{"layout","overview","viewport","cardDefaults","cardTypes"});
            else if("shell".equals(role))allowedKeys(part,new String[]{"shell"});
            else if("f1".equals(role))allowedKeys(part,new String[]{"f1","f1Grid","viewport"});
            else allowedKeys(part,new String[]{"viewport"});
            if(part.containsKey("fonts")){
                Map<String,Object> fonts=object(part.get("fonts"),"fonts");
                if(fonts.containsKey("body"))result.body=id;
                if(fonts.containsKey("numeric"))result.numeric=id;
                if(fonts.containsKey("display"))result.display=id;
            }
            if(part.containsKey("assets")&&object(part.get("assets"),"assets").containsKey("background"))result.background=id;
            if(part.containsKey("wallpapers"))result.wallpaper=id;
            if(part.containsKey("viewport")){
                Map<String,Object> wrapper=new LinkedHashMap<String,Object>(),page=new LinkedHashMap<String,Object>();
                page.put(role,part.remove("viewport"));wrapper.put("viewports",page);merge(result.root,wrapper);
            }
            merge(result.root,part);
        }
        result.root.put("schemaVersion",Long.valueOf(2));
        result.root.put("id",id);result.root.put("displayName",name);
        visiting.remove(id);
        return result;
    }
    private static boolean validFile(String path){return path.matches("(?:layouts/)?[a-z][a-z0-9_]{0,47}\\.json");}

    private static void parseMediaFooter(ThemeSpec spec,Map<String,Object> values){
        allowedKeys(values,new String[]{"buttonBackground","controls","containerVisible"});
        Boolean buttons=null;
        if(values.containsKey("controls")){
            String style=string(values,"controls");
            if("text".equals(style))buttons=Boolean.FALSE;
            else if("buttons".equals(style))buttons=Boolean.TRUE;
            else throw new IllegalArgumentException("invalid mediaFooter controls");
        }
        if(values.containsKey("buttonBackground"))buttons=Boolean.valueOf(bool(values,"buttonBackground"));
        Boolean visible=values.containsKey("containerVisible")?Boolean.valueOf(bool(values,"containerVisible")):null;
        spec.mediaFooter(buttons,visible);
        if(spec.isOriginal()&&(!spec.footerButtonBackground()||!spec.footerContainerVisible()))
            throw new IllegalArgumentException("Original media footer is immutable");
    }
    private static ThemeSpec.Shell parseShell(Map<String,Object> shell){
        allowedKeys(shell,new String[]{"position","alignment","width","padding","gap","buttonHeight","topPadding","mediaPadding","navigationAppearance","screenCornerRadius","screenCorner"});
        String position=string(shell,"position"),alignment=string(shell,"alignment");
        if(!("left".equals(position)||"top".equals(position)))throw new IllegalArgumentException("unsupported shell position");
        if(!("start".equals(alignment)||"center".equals(alignment)||"end".equals(alignment)))throw new IllegalArgumentException("unsupported shell alignment");
        int width=range(shell,"width",80,240),gap=range(shell,"gap",0,16),height=range(shell,"buttonHeight",24,50);
        Map<String,Object> padding=object(shell.get("padding"),"padding");exactKeys(padding,new String[]{"top","right","bottom","left"});
        int top=range(padding,"top",0,60),right=range(padding,"right",0,40),bottom=range(padding,"bottom",0,60),left=range(padding,"left",0,40);
        ThemeSpec.Insets topPadding=null;
        if(shell.containsKey("topPadding")){
            Map<String,Object> p=object(shell.get("topPadding"),"topPadding");exactKeys(p,new String[]{"top","right","bottom","left"});
            topPadding=new ThemeSpec.Insets(range(p,"top",0,24),range(p,"right",0,40),range(p,"bottom",0,24),range(p,"left",0,40));
        }
        int mediaPadding=shell.containsKey("mediaPadding")?range(shell,"mediaPadding",0,40):-1;
        int cornerRadius=rangeOr(shell,"screenCornerRadius",0,240,0);
        String corner=shell.containsKey("screenCorner")?string(shell,"screenCorner"):"circle";
        ThemeSpec.ScreenCorner screenCorner;
        if("circle".equals(corner))screenCorner=ThemeSpec.ScreenCorner.CIRCLE;
        else if("squircle".equals(corner)||"sqircle".equals(corner))screenCorner=ThemeSpec.ScreenCorner.SQUIRCLE;
        else throw new IllegalArgumentException("invalid screenCorner");
        String appearance=shell.containsKey("navigationAppearance")?string(shell,"navigationAppearance"):"buttons";
        if(!"buttons".equals(appearance)&&!"dock".equals(appearance))throw new IllegalArgumentException("invalid navigationAppearance");
        if("top".equals(position)&&"dock".equals(appearance))throw new IllegalArgumentException("dock requires left navigation");
        if("top".equals(position))return ThemeSpec.Shell.legacy().withScreenOptions(topPadding,mediaPadding,cornerRadius,screenCorner);
        if(width-left-right<48||top+bottom+8*height+7*gap>480)throw new IllegalArgumentException("shell contents do not fit");
        return new ThemeSpec.Shell(position,alignment,width,top,right,bottom,left,gap,height).withScreenOptions(topPadding,mediaPadding,cornerRadius,screenCorner).withDock("dock".equals(appearance));
    }
    private static void parseOverviewCardStyles(ThemeSpec spec,Object defaults,Object types){
        Map<ThemeSpec.OverviewCardKind,ThemeSpec.OverviewCardStyle> styles=new java.util.EnumMap<ThemeSpec.OverviewCardKind,ThemeSpec.OverviewCardStyle>(ThemeSpec.OverviewCardKind.class);
        if(defaults!=null)styles.put(ThemeSpec.OverviewCardKind.STANDARD,
            parseOverviewCardStyle(spec,ThemeSpec.OverviewCardKind.STANDARD,object(defaults,"cardDefaults"),null));
        ThemeSpec.Insets sharedPadding=styles.containsKey(ThemeSpec.OverviewCardKind.STANDARD)
            ?styles.get(ThemeSpec.OverviewCardKind.STANDARD).padding:spec.overviewCardStyle(ThemeSpec.OverviewCardKind.STANDARD).padding;
        Map<String,Object> groups=types==null?Collections.<String,Object>emptyMap():object(types,"cardTypes");
        allowedKeys(groups,new String[]{"tirePressure","dctTemperature","fuelEconomy","stability"});
        for(ThemeSpec.OverviewCardKind kind:ThemeSpec.OverviewCardKind.values())if(kind!=ThemeSpec.OverviewCardKind.STANDARD)
            styles.put(kind,parseOverviewCardStyle(spec,kind,
                groups.containsKey(kind.key)?object(groups.get(kind.key),kind.key):Collections.<String,Object>emptyMap(),sharedPadding));
        spec.overviewCardStyles(styles);
    }
    private static ThemeSpec.OverviewCardStyle parseOverviewCardStyle(ThemeSpec spec,ThemeSpec.OverviewCardKind kind,Map<String,Object> value,ThemeSpec.Insets sharedPadding){
        String[] keys;
        switch(kind){
            case STANDARD: keys=new String[]{"padding","titleSize","valueSize","unitSize","unitPosition","titleValueGap","titleUnitGap","valueUnitGap","valueGaugeGap","barHeight","dialSize"};break;
            case TIRE_PRESSURE: keys=new String[]{"padding","titleSize","valueSize","unitSize","labelSize","titleValueGap","valueUnitGap","rowGap","labelValueGap","columnGap"};break;
            case DCT_TEMPERATURE: keys=new String[]{"padding","titleSize","valueSize","labelSize","titleValueGap","rowGap","labelValueGap","valueGaugeGap","gaugeWidth"};break;
            default: keys=new String[]{"padding","titleSize","valueSize","labelSize","titleValueGap","rowGap","labelValueGap"};
        }
        allowedKeys(value,keys);
        ThemeSpec.OverviewCardStyle d=spec.overviewCardStyle(kind);
        ThemeSpec.Insets padding=sharedPadding==null?d.padding:sharedPadding;
        if(value.containsKey("padding")){
            Map<String,Object> p=object(value.get("padding"),"overview card padding");
            allowedKeys(p,new String[]{"top","right","bottom","left"});
            padding=new ThemeSpec.Insets(rangeOr(p,"top",0,24,padding.top),rangeOr(p,"right",0,24,padding.right),
                rangeOr(p,"bottom",0,24,padding.bottom),rangeOr(p,"left",0,24,padding.left));
        }
        String unitPosition=kind==ThemeSpec.OverviewCardKind.STANDARD&&value.containsKey("unitPosition")
            ?string(value,"unitPosition"):d.unitPosition;
        if(!("topRight".equals(unitPosition)||"bottomLeft".equals(unitPosition)||"bottomRight".equals(unitPosition)||"valueRight".equals(unitPosition)))
            throw new IllegalArgumentException("invalid overview unitPosition");
        return new ThemeSpec.OverviewCardStyle(padding,rangeOr(value,"titleSize",8,36,d.titleSize),
            rangeOr(value,"valueSize",8,64,d.valueSize),rangeOr(value,"unitSize",8,32,d.unitSize),
            rangeOr(value,"labelSize",8,32,d.labelSize),rangeOr(value,"titleValueGap",0,24,d.titleValueGap),
            rangeOr(value,"titleUnitGap",0,24,d.titleUnitGap),unitPosition,
            rangeOr(value,"valueUnitGap",0,24,d.valueUnitGap),rangeOr(value,"valueGaugeGap",0,24,d.valueGaugeGap),
            rangeOr(value,"rowGap",0,24,d.rowGap),rangeOr(value,"labelValueGap",0,24,d.labelValueGap),
            rangeOr(value,"columnGap",0,24,d.columnGap),rangeOr(value,"barHeight",2,20,d.barHeight),
            rangeOr(value,"dialSize",12,64,d.dialSize),rangeOr(value,"gaugeWidth",16,64,d.gaugeWidth));
    }
    private static int rangeOr(Map<String,Object> value,String key,int min,int max,int fallback){
        return value.containsKey(key)?range(value,key,min,max):fallback;
    }
    private static ThemeSpec.Overview parseOverview(Map<String,Object> value){
        exactKeys(value,new String[]{"columns","rows","padding","gapX","gapY","cards"});
        int columns=range(value,"columns",1,6),rows=range(value,"rows",1,4);
        if(columns!=5||rows!=4)throw new IllegalArgumentException("overview must use the fixed 5x4 grid");
        int gx=range(value,"gapX",0,30),gy=range(value,"gapY",0,30);
        Map<String,Object> p=object(value.get("padding"),"overview padding");exactKeys(p,new String[]{"top","right","bottom","left"});
        int top=range(p,"top",0,60),right=range(p,"right",0,60),bottom=range(p,"bottom",0,60),left=range(p,"left",0,60);
        if((764-left-right-(columns-1)*gx)/columns<100||(390-top-bottom-(rows-1)*gy)/rows<95)throw new IllegalArgumentException("overview grid cells too small");
        List<Object> list=array(value.get("cards"),"cards");
        if(list.size()>20)throw new IllegalArgumentException("overview exceeds the 20 fixed slots");
        ThemeSpec.Card[] cards=new ThemeSpec.Card[list.size()];Set<String> ids=new HashSet<String>(),occupied=new HashSet<String>();
        for(int i=0;i<cards.length;i++){
            Map<String,Object> item=object(list.get(i),"card");exactKeys(item,new String[]{"id","source","column","row","columnSpan","rowSpan"});
            String id=string(item,"id");if(!id.matches("[a-z][a-z0-9_]{0,47}")||!ids.add(id))throw new IllegalArgumentException("invalid or duplicate card ID");
            int source=range(item,"source",0,35),column=range(item,"column",0,columns-1),row=range(item,"row",0,rows-1);
            int cs=range(item,"columnSpan",1,columns),rs=range(item,"rowSpan",1,rows);
            if(column+cs>columns||row+rs>rows)throw new IllegalArgumentException("card exceeds overview grid");
            for(int y=row;y<row+rs;y++)for(int x=column;x<column+cs;x++)if(!occupied.add(x+":"+y))throw new IllegalArgumentException("overlapping cards");
            cards[i]=new ThemeSpec.Card(id,source,column,row,cs,rs);
        }
        return new ThemeSpec.Overview(columns,rows,top,right,bottom,left,gx,gy,cards);
    }
    private static Map<String,ThemeSpec.Rect> parseF1(Map<String,Object> value){
        String[] ids={"shift","gear","speed","boost","rpm","temperatures","pedal","brake"};exactKeys(value,ids);
        Map<String,ThemeSpec.Rect> result=new LinkedHashMap<String,ThemeSpec.Rect>();
        for(String id:ids){Map<String,Object> bounds=object(value.get(id),"F1 component");allowedKeys(bounds,new String[]{"x","y","width","height","fontRole","colorRole","padding","alignment"});
            int x=range(bounds,"x",0,799),y=range(bounds,"y",52,447),w=range(bounds,"width",40,800),h=range(bounds,"height",20,396);
            if(x+w>800||y+h>448)throw new IllegalArgumentException("F1 component outside content");
            String font=bounds.containsKey("fontRole")?string(bounds,"fontRole"):null,color=bounds.containsKey("colorRole")?string(bounds,"colorRole"):null;
            String alignment=bounds.containsKey("alignment")?string(bounds,"alignment"):"stretch";
            int top=0,right=0,bottom=0,left=0;
            if(bounds.containsKey("padding")){Map<String,Object> p=object(bounds.get("padding"),"F1 padding");exactKeys(p,new String[]{"top","right","bottom","left"});top=range(p,"top",0,40);right=range(p,"right",0,40);bottom=range(p,"bottom",0,40);left=range(p,"left",0,40);}
            if(w-left-right<20||h-top-bottom<10)throw new IllegalArgumentException("F1 padding exceeds bounds");
            if(!(alignment.equals("stretch")||alignment.equals("start")||alignment.equals("center")||alignment.equals("end")))throw new IllegalArgumentException("invalid F1 alignment");
            if(font!=null&&!(font.equals("body")||font.equals("numeric")||font.equals("display")))throw new IllegalArgumentException("invalid F1 font role");
            if(color!=null&&!(color.equals("primary")||color.equals("secondary")||color.equals("accent")||color.equals("warning")||color.equals("critical")))throw new IllegalArgumentException("invalid F1 color role");
            for(ThemeSpec.Rect previous:result.values())if(x<previous.x+previous.width&&previous.x<x+w&&y<previous.y+previous.height&&previous.y<y+h)throw new IllegalArgumentException("overlapping F1 components");
            result.put(id,new ThemeSpec.Rect(x,y,w,h,font,color,top,right,bottom,left,alignment));
        }
        return result;
    }
    private static ThemeSpec.F1Grid parseF1Grid(Map<String,Object> value){
        exactKeys(value,new String[]{"columns","rowWeights","inset","gap","padding","radius","cells"});
        int columns=range(value,"columns",1,8),inset=range(value,"inset",8,24),gap=range(value,"gap",6,16);
        int padding=range(value,"padding",8,16),radius=range(value,"radius",10,24);
        List<Object> rows=array(value.get("rowWeights"),"F1 row weights");
        if(rows.isEmpty()||rows.size()>8)throw new IllegalArgumentException("invalid F1 row count");
        int[] weights=new int[rows.size()];
        for(int i=0;i<weights.length;i++){
            Object weight=rows.get(i);
            if(!(weight instanceof Long)||(Long)weight<1||(Long)weight>100)throw new IllegalArgumentException("invalid F1 row weight");
            weights[i]=((Long)weight).intValue();
        }
        String[] ids={"shift","gear","speed","boost","rpm","temperatures","pedal","brake"};
        Map<String,Object> cells=object(value.get("cells"),"F1 cells");exactKeys(cells,ids);
        Map<String,ThemeSpec.Card> result=new LinkedHashMap<String,ThemeSpec.Card>();
        boolean[][] occupied=new boolean[weights.length][columns];
        for(String id:ids){
            Map<String,Object> c=object(cells.get(id),"F1 cell");exactKeys(c,new String[]{"column","row","columnSpan","rowSpan"});
            int x=range(c,"column",0,columns-1),y=range(c,"row",0,weights.length-1);
            int w=range(c,"columnSpan",1,columns-x),h=range(c,"rowSpan",1,weights.length-y);
            for(int r=y;r<y+h;r++)for(int k=x;k<x+w;k++){
                if(occupied[r][k])throw new IllegalArgumentException("overlapping F1 cells");
                occupied[r][k]=true;
            }
            result.put(id,new ThemeSpec.Card(id,0,x,y,w,h));
        }
        return new ThemeSpec.F1Grid(columns,weights,inset,gap,padding,radius,result);
    }
    /** Resolve the user's menu setting without altering the catalog or its theme defaults. */
    public static ThemeSpec withMenuPosition(ThemeSpec spec,int position){
        if(position==ThemeSpec.MENU_THEME)return spec;
        ThemeSpec.Shell shell=spec.shell();
        if(position==ThemeSpec.MENU_TOP&&shell.left())shell=ThemeSpec.Shell.legacy();
        else if(position==ThemeSpec.MENU_LEFT&&!shell.left())shell=ThemeSpec.Shell.sidebar();
        else return spec;
        ThemeSpec.Shell defaults=spec.shell();
        ThemeSpec resolved=spec.withShell(shell.withScreenOptions(defaults.topPadding,defaults.mediaPadding,defaults.screenCornerRadius,defaults.screenCorner));
        try{validateContentSize(resolved);return resolved;}
        catch(IllegalArgumentException unsupported){return spec;}
    }
    private static void validateContentSize(ThemeSpec spec){
        float width=800-(spec.shell().left()?spec.shell().width:0),height=480-(spec.shell().left()?0:spec.shell().topMenuHeight());
        if(spec.overview()!=null){
            ThemeSpec.Overview o=spec.overview();ThemeSpec.Viewport v=spec.viewport("overview");
            float w=width-(v==null?0:v.left+v.right),h=height-(v==null?0:v.top+v.bottom);
            if((w-36-o.left-o.right-(o.columns-1)*o.gapX)/o.columns<100||(h-38-o.top-o.bottom-(o.rows-1)*o.gapY)/o.rows<95)
                throw new IllegalArgumentException("overview cells do not fit actual content");
        }
        if(spec.f1Grid()!=null){
            ThemeSpec.F1Grid g=spec.f1Grid();ThemeSpec.Viewport v=spec.viewport("f1");
            float w=(width-(v==null?0:v.left+v.right)-2*g.inset-g.gap)*0.64f;
            float h=height-(v==null?0:v.top+v.bottom)-2*g.inset-44-g.gap;
            float column=(w-(g.columns-1)*g.gap)/g.columns;
            float row=(h-(g.rows-1)*g.gap)/g.rowWeight(0,g.rows);
            for(String id:new String[]{"shift","gear","speed","boost","rpm","temperatures","pedal","brake"}){
                ThemeSpec.Card c=g.cell(id);
                if(column*c.columnSpan+g.gap*(c.columnSpan-1)<2*g.padding+42||
                    row*g.rowWeight(c.row,c.row+c.rowSpan)+g.gap*(c.rowSpan-1)<38)
                    throw new IllegalArgumentException("F1 cells do not fit combined content");
            }
        }
        if(spec.hasF1()){
            ThemeSpec.Viewport v=spec.viewport("f1");float w=width-(v==null?0:v.left+v.right),h=height-(v==null?0:v.top+v.bottom)-54;
            for(String id:new String[]{"shift","gear","speed","boost","rpm","temperatures","pedal","brake"}){
                ThemeSpec.Rect r=spec.f1(id);float rw=r.width*w/800f,rh=r.height*h/(spec.f1Bottom()-52);
                if(rw-r.left-r.right-2*Math.min(12,rw*0.08f)<16||rh-r.top-r.bottom-2*Math.min(10,rh*0.10f)<8)
                    throw new IllegalArgumentException("F1 padding does not fit actual content");
            }
        }
    }
    private static Map<String,ThemeSpec.Viewport> parseViewports(Map<String,Object> value){
        allowedKeys(value,new String[]{"overview","f1","gauges","graphs","performance","settings","diagnostics"});
        Map<String,ThemeSpec.Viewport> result=new LinkedHashMap<String,ThemeSpec.Viewport>();
        for(Map.Entry<String,Object> entry:value.entrySet()){
            Map<String,Object> viewport=object(entry.getValue(),"viewport");exactKeys(viewport,new String[]{"padding","alignment"});
            Map<String,Object> p=object(viewport.get("padding"),"viewport padding");exactKeys(p,new String[]{"top","right","bottom","left"});
            String align=string(viewport,"alignment");if(!("start".equals(align)||"center".equals(align)||"end".equals(align)))throw new IllegalArgumentException("invalid viewport alignment");
            result.put(entry.getKey(),new ThemeSpec.Viewport(range(p,"top",0,64),range(p,"right",0,64),range(p,"bottom",0,64),range(p,"left",0,64),align));
        }
        return result;
    }
    private static void allowedKeys(Map<String,Object> map,String[] keys){
        Set<String> allowed=new HashSet<String>();Collections.addAll(allowed,keys);
        for(String key:map.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("unknown key: "+key);
    }
    private static void merge(Map<String,Object> target,Map<String,Object> overlay){
        for(Map.Entry<String,Object> entry:overlay.entrySet()){
            Object value=entry.getValue(),previous=target.get(entry.getKey());
            if(value==null)throw new IllegalArgumentException("null theme values are not supported");
            if(value instanceof Map&&previous instanceof Map)merge(object(previous,"parent value"),object(value,"child value"));
            else target.put(entry.getKey(),value);
        }
    }

    private static ThemeSpec parseManifest(String id,String json){
        return parseManifestMap(id,object(ThemeJsonParser.parse(json),"manifest"),true,null);
    }
    private static ThemeSpec parseManifestMap(String id,Map<String,Object> root,boolean v2,ThemeSpec.Backdrop backdrop){
        exactKeys(root,new String[]{"schemaVersion","id","displayName","tokens","layout","fonts","assets","typography","spacing","material"});
        if(integer(root,"schemaVersion")!=2)throw new IllegalArgumentException("unsupported manifest version");
        if(!id.equals(string(root,"id")))throw new IllegalArgumentException("manifest ID does not match catalog");
        String display=string(root,"displayName");if(display.length()==0||display.length()>64)throw new IllegalArgumentException("invalid display name");
        Map<String,Object> tokens=object(root.get("tokens"),"tokens");
        exactKeys(tokens,new String[]{"background","panel","panelRaised","stroke","textPrimary","textSecondary","accent","warning","critical","unavailable","cutPanels"});
        Map<String,Object> layout=object(root.get("layout"),"layout");exactKeys(layout,new String[]{"columns","rows"});
        int columns=integer(layout,"columns"),rows=integer(layout,"rows");
        if(columns!=5||rows!=4)throw new IllegalArgumentException("theme overview must use the fixed 5x4 grid");
        Map<String,Object> fonts=object(root.get("fonts"),"fonts");exactKeys(fonts,new String[]{"body","numeric","display"});
        String body=fontPath(string(fonts,"body")),numeric=fontPath(string(fonts,"numeric")),displayFont=fontPath(string(fonts,"display"));
        Map<String,Object> assets=object(root.get("assets"),"assets");exactKeys(assets,new String[]{"background"});
        String backgroundAsset=assetPath(string(assets,"background"));
        Map<String,Object> type=object(root.get("typography"),"typography");
        exactKeys(type,new String[]{"bodyScale","numericScale","displayScale","gearSize","combinedGearSize"});
        ThemeSpec.Typography typography=new ThemeSpec.Typography(range(type,"bodyScale",60,140),range(type,"numericScale",60,140),
                range(type,"displayScale",60,140),range(type,"gearSize",64,220),range(type,"combinedGearSize",64,160));
        Map<String,Object> space=object(root.get("spacing"),"spacing");
        exactKeys(space,new String[]{"buttonPadding","cardPadding","settingsPadding","pedalPadding","f1SidePadding","gearPadding","gridGapX","gridGapY"});
        ThemeSpec.Spacing spacing=new ThemeSpec.Spacing(range(space,"buttonPadding",4,24),range(space,"cardPadding",4,24),
                range(space,"settingsPadding",8,40),range(space,"pedalPadding",8,28),range(space,"f1SidePadding",4,24),
                range(space,"gearPadding",0,24),range(space,"gridGapX",0,24),range(space,"gridGapY",0,24));
        Map<String,Object> mat=object(root.get("material"),"material");
        allowedKeys(mat,new String[]{"style","radius","topAlpha","bottomAlpha","highlight","shadow","backdropEnd"});
        String style=string(mat,"style");
        validateMaterialStyle(style);
        ThemeSpec.Material material=new ThemeSpec.Material(style,range(mat,"radius",0,36),range(mat,"topAlpha",0,255),
                range(mat,"bottomAlpha",0,255),color(mat,"highlight"),color(mat,"shadow"));
        boolean original="original".equals(id);
        // Only the v2 format encoded the backdrop as part of material; translate it at load time.
        if(v2){
            int end=opaqueColor(mat,"backdropEnd");
            if(original&&end!=0xffe5efff)throw new IllegalArgumentException("Original v2 backdrop is immutable");
            backdrop=original?ThemeSpec.Backdrop.LEGACY_GRADIENT
                    :material.glass()?new ThemeSpec.Backdrop(ThemeSpec.Backdrop.Mode.GRADIENT,end)
                    :material.legacy()?ThemeSpec.Backdrop.LEGACY:ThemeSpec.Backdrop.SOLID;
        }else{
            if(mat.containsKey("backdropEnd"))color(mat,"backdropEnd"); // Accept older v3 files without using this value.
            if(backdrop==null)backdrop=original?ThemeSpec.Backdrop.LEGACY_GRADIENT:ThemeSpec.Backdrop.SOLID;
        }
        ThemeSpec spec=ThemeSpec.fromManifest(id,display,opaqueColor(tokens,"background"),color(tokens,"panel"),color(tokens,"panelRaised"),
                color(tokens,"stroke"),color(tokens,"textPrimary"),color(tokens,"textSecondary"),color(tokens,"accent"),
                color(tokens,"warning"),color(tokens,"critical"),color(tokens,"unavailable"),columns,rows,
                bool(tokens,"cutPanels"),body,numeric,displayFont,backgroundAsset,typography,spacing,material,backdrop);
        if(original){
            // Older manifests had implicit component colors. New v3 files can state the same values explicitly.
            spec.palette(ThemeSpec.Palette.COMPONENT_DEFAULTS);
            spec.componentFills(ThemeSpec.LEGACY_FILLS);
            if(!same(spec,v2?ThemeSpec.originalV2():ThemeSpec.original()))
                throw new IllegalArgumentException("Original manifest must match the built-in baseline");
        }
        return spec;
    }

    private static ThemeSpec.Backdrop parseBackdrop(String id,Map<String,Object> values){
        allowedKeys(values,new String[]{"style","startColor","endColor","endXPercent","scanlineColor"});
        String style=string(values,"style");
        ThemeSpec.Backdrop.Mode mode;
        if("gradient".equals(style))mode=ThemeSpec.Backdrop.Mode.GRADIENT;
        else if("solid".equals(style))mode=ThemeSpec.Backdrop.Mode.SOLID;
        else if("legacy".equals(style))mode=ThemeSpec.Backdrop.Mode.LEGACY;
        else throw new IllegalArgumentException("unsupported backdrop style");
        boolean gradient=mode==ThemeSpec.Backdrop.Mode.GRADIENT;
        boolean legacy=mode==ThemeSpec.Backdrop.Mode.LEGACY;
        int end=gradient||values.containsKey("endColor")?opaqueColor(values,"endColor"):0;
        Integer start=values.containsKey("startColor")&&!"background".equals(values.get("startColor"))
                ?Integer.valueOf(opaqueColor(values,"startColor")):null;
        int endX=values.containsKey("endXPercent")?range(values,"endXPercent",0,100):65;
        int scanlines=values.containsKey("scanlineColor")?color(values,"scanlineColor"):legacy?0x18314a50:0;
        // A v3 recovery theme written before these fields used "legacy" for this fixed recipe.
        if(legacy&&"original".equals(id)&&!values.containsKey("startColor")&&
                !values.containsKey("endXPercent")&&!values.containsKey("scanlineColor"))
            return ThemeSpec.Backdrop.LEGACY_GRADIENT;
        return new ThemeSpec.Backdrop(mode,gradient?end:0,gradient?start:null,gradient?endX:65,scanlines);
    }

    private static List<ThemeSpec.Wallpaper> parseWallpapers(List<Object> values,String owner){
        if(values.isEmpty()||values.size()>48)throw new IllegalArgumentException("wallpaper count out of range");
        List<ThemeSpec.Wallpaper> result=new java.util.ArrayList<ThemeSpec.Wallpaper>();
        Set<String> ids=new HashSet<String>();int defaults=0;
        for(Object raw:values){
            Map<String,Object> item=object(raw,"wallpaper");
            exactKeys(item,new String[]{"id","name","asset","thumbnail","default"});
            String id=string(item,"id"),name=string(item,"name");
            if(!id.matches("[a-z][a-z0-9_]{0,63}")||!ids.add(id))throw new IllegalArgumentException("invalid or duplicate wallpaper ID");
            if(name.length()==0||name.length()>32)throw new IllegalArgumentException("invalid wallpaper name");
            String asset=wallpaperPath(string(item,"asset")),thumbnail=wallpaperPath(string(item,"thumbnail"));
            boolean isDefault=bool(item,"default");if(isDefault)defaults++;
            result.add(new ThemeSpec.Wallpaper(id,name,asset,thumbnail,owner,isDefault));
        }
        if(defaults>1)throw new IllegalArgumentException("each wallpaper collection can have at most one default");
        return result;
    }

    private static String wallpaperPath(String path){
        if(!path.matches("backgrounds/(?:[A-Za-z0-9_-][A-Za-z0-9_.-]*/)?[A-Za-z0-9_.-]+\\.(png|jpg|jpeg)"))throw new IllegalArgumentException("invalid wallpaper asset path");
        return path;
    }

    private static void parseDecorations(ThemeSpec spec,Map<String,Object> values){
        allowedKeys(values,new String[]{"overviewCardTopLine","navigationUnderline"});
        boolean topLine=!values.containsKey("overviewCardTopLine")||bool(values,"overviewCardTopLine");
        String value=values.containsKey("navigationUnderline")?string(values,"navigationUnderline"):"auto";
        ThemeSpec.Underline underline;
        if("auto".equals(value))underline=ThemeSpec.Underline.AUTO;
        else if("on".equals(value))underline=ThemeSpec.Underline.ON;
        else if("off".equals(value))underline=ThemeSpec.Underline.OFF;
        else throw new IllegalArgumentException("unsupported navigation underline");
        spec.decorations(topLine,underline);
    }

    private static void parseFills(ThemeSpec spec,Map<String,Object> values){
        allowedKeys(values,new String[]{"navigation","navigationSelected","gaugeToggle"});
        ThemeSpec.ComponentFills defaults=spec.componentFills();
        spec.componentFills(new ThemeSpec.ComponentFills(
            values.containsKey("navigation")?optionalColor(values,"navigation","token"):defaults.navigation,
            values.containsKey("navigationSelected")?optionalColor(values,"navigationSelected","token"):defaults.navigationSelected,
            values.containsKey("gaugeToggle")?optionalColor(values,"gaugeToggle","token"):defaults.gaugeToggle));
    }

    private static Integer optionalColor(Map<String,Object> values,String key,String fallback){
        return !values.containsKey(key)||fallback.equals(values.get(key))?null:Integer.valueOf(color(values,key));
    }

    private static void validateMaterialStyle(String style){
        if(!("legacy".equals(style)||"flat".equals(style)||"glass".equals(style)))
            throw new IllegalArgumentException("unsupported material style");
    }

    private static void parseSurfaceStyles(ThemeSpec spec,Map<String,Object> values){
        if(spec.isOriginal()&&!values.isEmpty())throw new IllegalArgumentException("Original surface styles are immutable");
        ThemeSpec.SurfaceRole[] roles=ThemeSpec.SurfaceRole.values();
        String[] keys=new String[roles.length];
        for(int i=0;i<roles.length;i++)keys[i]=roles[i].key;
        allowedKeys(values,keys);
        Map<ThemeSpec.SurfaceRole,ThemeSpec.Material> resolved=
            new java.util.EnumMap<ThemeSpec.SurfaceRole,ThemeSpec.Material>(ThemeSpec.SurfaceRole.class);
        for(ThemeSpec.SurfaceRole role:roles)if(values.containsKey(role.key)){
            String style=string(values,role.key);
            validateMaterialStyle(style);
            resolved.put(role,spec.material.withStyle(style));
        }
        spec.surfaceMaterials(resolved);
    }

    private static void parseSurfaceBlur(ThemeSpec spec,Map<String,Object> values){
        if(spec.isOriginal())throw new IllegalArgumentException("Original surface blur is immutable");
        ThemeSpec.SurfaceRole[] roles={ThemeSpec.SurfaceRole.DATA_CARD,ThemeSpec.SurfaceRole.GEAR_CARD,
            ThemeSpec.SurfaceRole.SETTINGS_GROUP,ThemeSpec.SurfaceRole.MEDIA_CONTAINER,ThemeSpec.SurfaceRole.NAVIGATION_DOCK};
        String[] keys=new String[roles.length];for(int i=0;i<roles.length;i++)keys[i]=roles[i].key;
        allowedKeys(values,keys);
        java.util.EnumMap<ThemeSpec.SurfaceRole,Integer> result=new java.util.EnumMap<ThemeSpec.SurfaceRole,Integer>(ThemeSpec.SurfaceRole.class);
        for(ThemeSpec.SurfaceRole role:roles)if(values.containsKey(role.key))result.put(role,range(values,role.key,0,36));
        spec.surfaceBlur(result);
    }

    private static boolean same(ThemeSpec a,ThemeSpec b){return a.id.equals(b.id)&&a.displayName.equals(b.displayName)&&a.background==b.background&&a.panel==b.panel&&a.panelRaised==b.panelRaised&&a.stroke==b.stroke&&a.textPrimary==b.textPrimary&&a.textSecondary==b.textSecondary&&a.accent==b.accent&&a.warning==b.warning&&a.critical==b.critical&&a.unavailable==b.unavailable&&a.columns==b.columns&&a.rows==b.rows&&a.cutPanels==b.cutPanels&&a.bodyFont.equals(b.bodyFont)&&a.numericFont.equals(b.numericFont)&&a.displayFont.equals(b.displayFont)&&a.backgroundAsset.equals(b.backgroundAsset)&&sameVisuals(a,b);}

    private static boolean sameVisuals(ThemeSpec a,ThemeSpec b){
        return a.typography.bodyScale==b.typography.bodyScale&&a.typography.numericScale==b.typography.numericScale&&a.typography.displayScale==b.typography.displayScale&&a.typography.gearSize==b.typography.gearSize&&a.typography.combinedGearSize==b.typography.combinedGearSize
            &&a.spacing.buttonPadding==b.spacing.buttonPadding&&a.spacing.cardPadding==b.spacing.cardPadding&&a.spacing.settingsPadding==b.spacing.settingsPadding&&a.spacing.pedalPadding==b.spacing.pedalPadding&&a.spacing.f1SidePadding==b.spacing.f1SidePadding&&a.spacing.gearPadding==b.spacing.gearPadding&&a.spacing.gridGapX==b.spacing.gridGapX&&a.spacing.gridGapY==b.spacing.gridGapY
            &&a.material.style.equals(b.material.style)&&a.material.radius==b.material.radius&&a.material.topAlpha==b.material.topAlpha&&a.material.bottomAlpha==b.material.bottomAlpha&&a.material.highlight==b.material.highlight&&a.material.shadow==b.material.shadow
            &&a.backdrop.same(b.backdrop);
    }
    private static int range(Map<String,Object> value,String key,int min,int max){int number=integer(value,key);if(number<min||number>max)throw new IllegalArgumentException(key+" out of range");return number;}
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
    private static int opaqueColor(Map<String,Object> value,String key){
        int color=color(value,key);
        if((color>>>24)!=255)throw new IllegalArgumentException(key+" must be opaque");
        return color;
    }

    public ThemeSpec find(String id){return themes.get(id);}
    public ThemeSpec resolve(String id){ThemeSpec found=find(id);return found==null?themes.get("original"):found;}
    public int size(){return themes.size();}
    public Map<String,ThemeSpec> all(){return themes;}
    public ThemeSpec.Wallpaper findWallpaper(String id){return wallpapers.get(id);}
    public List<ThemeSpec.Wallpaper> wallpapers(){
        return Collections.unmodifiableList(new ArrayList<ThemeSpec.Wallpaper>(wallpapers.values()));
    }
}
