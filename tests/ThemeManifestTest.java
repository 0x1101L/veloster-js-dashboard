import com.veloster.dashboard.theme.ThemeCatalog;
import com.veloster.dashboard.theme.ThemeJsonParser;
import com.veloster.dashboard.theme.ThemePalette;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSpec.SurfaceRole;
import com.veloster.dashboard.theme.ThemeResources;
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
        String catalogJson=read(args[0]);Map<String,String> manifests=docs(args[2]);
        ThemeCatalog catalog=load(catalogJson,manifests);
        final String assetRoot=args[1];
        android.content.Context bundledContext=new android.content.Context(){public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager(){public java.io.InputStream open(String path)throws java.io.IOException{return Files.newInputStream(Paths.get(assetRoot).getParent().resolve(path));}};}};
        ThemeCatalog bundled=ThemeCatalog.load(bundledContext);
        check(bundled.size()==5,"v3 bundle contains five selectable themes");
        check("original".equals(bundled.resolve(null).id),"Original remains the default theme");
        ThemeSpec f12=bundled.find("f1_2");
        check(f12!=null&&"Liquid Glass".equals(f12.displayName)&&f12.f1Grid()!=null&&f12.shell().left()&&
              f12.material.glass(),"F1-2 Glass is the parent theme");
        check(f12.backdrop.mode==ThemeSpec.Backdrop.Mode.GRADIENT&&f12.backdrop.endColor==0xffe5efff,
              "F1-2 Glass defines the shared gradient");
        check(f12.wallpapers().size()==4&&"f1_2_blue".equals(f12.defaultWallpaperId())
              &&"backgrounds/blue.png".equals(f12.backgroundAsset),
              "F1-2 uses Blue by default and exposes all four wallpaper options");
        check(f12.overview()==null&&f12.columns==5&&f12.rows==4,"official F1-2 uses the shared overview grid");
        OverviewLayout originalOverview=new OverviewLayout(),f12Overview=new OverviewLayout();
        originalOverview.theme(bundled.find("original"));f12Overview.theme(f12);
        for(String profile:new String[]{"js","js_n","pd"}){
            for(OverviewLayout overview:new OverviewLayout[]{originalOverview,f12Overview}){
                overview.jsProfile("js".equals(profile));overview.pdProfile("pd".equals(profile));
            }
            check(f12Overview.pageCount()==1&&f12Overview.count()==20,"F1-2 keeps all overview cards on one page");
            for(int slot=0;slot<originalOverview.count();slot++)
                check(f12Overview.signalAt(slot)==originalOverview.signalAt(slot)
                      &&f12Overview.hit(f12Overview.leftAt(slot)+f12Overview.widthAt(slot)/2,
                                        f12Overview.topAt(slot)+f12Overview.heightAt(slot)/2)==f12Overview.signalAt(slot),
                      "F1-2 preserves overview order and uses its own shell geometry for "+profile);
        }
        check(ThemeResources.load(bundledContext,f12).font("body",false)!=android.graphics.Typeface.DEFAULT,
              "F1-2 loads its bundled Pretendard font");
        ThemeSpec flat=bundled.find("f1_2_flat");
        check(bundled.wallpapers().size()==6&&flat.wallpapers().size()==4
              &&"f1_2_blue".equals(flat.defaultWallpaperId()),
              "wallpaper gallery includes color themes and merges inherited image options without duplicates");
        check(bundled.findWallpaper("original:color")!=null
              &&bundled.findWallpaper("original:color").color.intValue()==bundled.find("original").background
              &&"original:color".equals(bundled.find("original").defaultWallpaperId()),
              "image-free theme adds its own backdrop as the default option");
        check(flat!=null&&"Liquid Glass (Flat)".equals(flat.displayName)&&"flat".equals(flat.material.style),
              "Liquid Glass (Flat) is a separate selectable theme");
        check(flat.material.radius==f12.material.radius&&flat.material.topAlpha==f12.material.topAlpha
              &&flat.material.bottomAlpha==f12.material.bottomAlpha&&flat.material.highlight==f12.material.highlight
              &&flat.material.shadow==f12.material.shadow&&flat.backdrop.mode==f12.backdrop.mode
              &&flat.backdrop.endColor==f12.backdrop.endColor&&flat.background==f12.background
              &&flat.panel==f12.panel&&flat.accent==f12.accent&&flat.f1Grid()!=null
              &&flat.shell().left()&&flat.shell().width==f12.shell().width
              &&flat.overviewCardTopLine()==f12.overviewCardTopLine()&&flat.navigationUnderline()==f12.navigationUnderline()
              &&flat.bodyFont.equals(f12.bodyFont)
              &&ThemeResources.load(bundledContext,flat).font("body",false)!=android.graphics.Typeface.DEFAULT,
              "Flat inherits material parameters, palette, background, layout, decoration and font assets");
        ThemeSpec optics=bundled.find("f1_2_optics");
        check(optics!=null&&"Liquid Glass (Optics)".equals(optics.displayName)
              &&optics.shell().left()&&optics.shell().dock()&&optics.shell().width==f12.shell().width
              &&optics.mediaControls()==ThemeSpec.MediaControls.CONTAINER&&f12.usesLiquidGlass()
              &&!flat.usesLiquidGlass()&&optics.usesLiquidGlass(),
              "Optics is selectable with a left dock and retains its wide media container");
        for(ThemeSpec spec:new ThemeSpec[]{f12,flat,optics}){
            check(ThemePalette.accent(spec,0xffa4d9ef)==spec.accent
                  &&spec.navigationFill(false)==spec.panel&&spec.gaugeToggleFill()==spec.panelRaised
                  &&spec.backdrop.startColor==null&&spec.background==0xffffffff
                  &&spec.backdrop.endXPercent==65&&spec.backdrop.scanlineColor==0,
                  spec.id+" uses Glass colors for controls and the backdrop");
            ThemeSpec render=ThemeCatalog.withMenuPosition(spec,ThemeSpec.MENU_TOP).withDisplayOptions(1,0,2);
            check(render.mediaControls()==ThemeSpec.MediaControls.CONTAINER,
                  spec.id+" keeps the wide player card and hides individual button backgrounds");
            check(!render.footerButtonBackground()&&render.footerContainerVisible()&&!render.footerUsesLegacyIcons(),
                  spec.id+" keeps labeled buttons on a shared background even after display overrides");
        }
        for(SurfaceRole role:new SurfaceRole[]{SurfaceRole.DATA_CARD,SurfaceRole.GEAR_CARD,SurfaceRole.SETTINGS_GROUP})
            check(optics.blurFor(role)==10&&"flat".equals(optics.materialFor(role).style),"Optics keeps "+role+" subdued");
        check(optics.blurFor(SurfaceRole.MEDIA_CONTAINER)==16&&optics.blurFor(SurfaceRole.NAVIGATION_DOCK)==12
              &&"flat".equals(optics.materialFor(SurfaceRole.MEDIA_CONTAINER).style)
              &&"flat".equals(optics.materialFor(SurfaceRole.NAVIGATION_DOCK).style)
              &&(optics.panel>>>24)==0x91
              &&!f12.shell().dock()&&f12.blurFor(SurfaceRole.DATA_CARD)==0,
              "Optics separates subdued content surfaces from lightly blurred control containers");
        check(optics.background==f12.background&&optics.backdrop.mode==f12.backdrop.mode
              &&optics.backdrop.endColor==f12.backdrop.endColor&&optics.backgroundAsset.equals(f12.backgroundAsset)
              &&optics.defaultWallpaperId().equals(f12.defaultWallpaperId())&&optics.wallpapers().size()==4
              &&"f1_2".equals(optics.wallpapers().get(0).owner)&&bundled.wallpapers().size()==6,
              "Optics inherits the F1-2 backdrop and wallpaper assets without adding gallery duplicates");
        check(optics.f1Grid()!=null&&optics.f1Grid().gap==f12.f1Grid().gap&&optics.rows==f12.rows
              &&optics.columns==f12.columns&&optics.bodyFont.equals(f12.bodyFont)
              &&ThemeResources.load(bundledContext,optics).font("body",false)!=android.graphics.Typeface.DEFAULT
              &&"themes/f1_2/fonts/Pretendard-Regular.otf".equals(android.graphics.Typeface.lastAssetPath),
              "Optics inherits F1 and overview geometry and loads Pretendard from F1-2");
        for(String id:new String[]{"original","aurora_focus"}){
            ThemeSpec old=catalog.find(id),current=bundled.find(id);
            check(current!=null&&current.background==old.background&&current.accent==old.accent&&current.columns==old.columns&&current.rows==old.rows&&current.bodyFont.equals(old.bodyFont)&&current.spacing.cardPadding==old.spacing.cardPadding&&current.material.style.equals(old.material.style),"v3 migration preserves v2 values for "+id);
            check(current.footerButtonBackground()&&current.footerContainerVisible(),
                  id+" retains the existing footer appearance");
        }
        check(catalog.size()==4,"bundled catalog parses all manifests");
        check(catalog.find("original").background==0xff040506,"Original manifest tokens parse");
        ThemeSpec focus=catalog.find("aurora_focus");
        check(focus!=null&&focus.columns==5&&focus.rows==4&&"aurora_focus".equals(focus.id),"Focus ID and existing grid parse");
        check(bundled.find("carplay_glass_light")==null&&bundled.find("carplay_glass_dark")==null,
              "CarPlay Glass Light and Dark are no longer selectable");
        check(bundled.find("aurora_focus").background==0xff071118
              &&bundled.find("aurora_focus").bodyFont.equals(bundled.find("original").bodyFont),
              "Aurora Focus overrides Original while inheriting its font defaults");
        ThemeSpec originalLeft=ThemeCatalog.withMenuPosition(bundled.find("original"),ThemeSpec.MENU_LEFT);
        check(originalLeft.shell().width==96&&originalLeft.shell().left==0&&originalLeft.shell().right==0,
              "Original left navigation occupies 96px");
        Map<String,String> paged=new LinkedHashMap<String,String>(manifests);
        paged.put("aurora_focus",paged.get("aurora_focus").replace("\"columns\": 5, \"rows\": 4","\"columns\": 4, \"rows\": 3"));
        ThemeSpec pagedTheme=load(catalogJson,paged).find("aurora_focus");
        OverviewLayout pagedLayout=new OverviewLayout();pagedLayout.theme(pagedTheme);
        check(pagedTheme.columns==4&&pagedTheme.rows==3&&pagedLayout.pageCount()==2&&pagedLayout.page(1),"Supported 4x3 themes retain overview pagination");
        check("original".equals(load("{\"schemaVersion\":2,\"themeIds\":[\"original\",\"aurora_focus\",\"aurora_focus\"]}",manifests).resolve("aurora_focus").id),"duplicate catalog ID falls back to Original only");
        check("original".equals(load(catalogJson,new LinkedHashMap<String,String>()).resolve("aurora_focus").id),"missing manifests fall back safely");
        check("original".equals(load(catalogJson.replace("\"schemaVersion\": 2","\"schemaVersion\": 99"),manifests).resolve("aurora_focus").id),"unsupported catalog version falls back");
        Map<String,String> bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus",bad.get("aurora_focus").replace("#FF65E0D0","cyan"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id),"invalid token value rejects catalog and falls back");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus",bad.get("aurora_focus").replace("#FF071118","#80071118"));
        check(load(catalogJson,bad).size()==1,"v2 rejects a translucent screen base");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus",bad.get("aurora_focus").replace("\"rows\": 4","\"rows\": 8"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id),"out-of-range layout rejects catalog");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus",bad.get("aurora_focus").replace("\"columns\": 5","\"columns\": 4"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id),"unsupported renderer grid rejects catalog");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("original",bad.get("original").replace("#FF040506","#FF000000"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id)&&load(catalogJson,bad).size()==1,"altered Original manifest falls back to canonical Original");
        check(ThemeSpec.VERSION==3&&focus.typography.bodyScale==100&&focus.spacing.cardPadding==10&&focus.material.legacy(),"v2 compatibility preserves legacy visuals");
        Map<String,String> custom=new LinkedHashMap<String,String>(manifests);
        custom.put("aurora_focus",custom.get("aurora_focus").replace("\"numericScale\": 100","\"numericScale\": 120").replace("\"gridGapX\": 8","\"gridGapX\": 20"));
        ThemeSpec resized=load(catalogJson,custom).find("aurora_focus");
        check(resized!=null&&resized.textSize("124",50,true)==60&&resized.textSize("SPEED",20,true)==20,"role-based text scale applies once");
        OverviewLayout resizedLayout=new OverviewLayout();resizedLayout.theme(resized);int first=resizedLayout.signalAt(0);
        float end=resizedLayout.left(first)+resizedLayout.width(first);
        check(Math.abs(resizedLayout.leftAt(1)-end-20)<.01f&&resizedLayout.hit(end-1,100)==first&&resizedLayout.hit(end+1,100)==-1,"custom card gap updates drawing and hit geometry together");
        String[] invalidBefore={"\"bodyScale\": 100","\"numericScale\": 100","\"gearSize\": 184","\"buttonPadding\": 12","\"gridGapX\": 8","\"radius\": 0","\"topAlpha\": 255","\"style\": \"legacy\""};
        String[] invalidAfter={"\"bodyScale\": 0","\"numericScale\": 100.5","\"gearSize\": 221","\"buttonPadding\": 25","\"gridGapX\": -1","\"radius\": 37","\"topAlpha\": 256","\"style\": \"unknown\""};
        for(int i=0;i<invalidBefore.length;i++){
            bad=new LinkedHashMap<String,String>(manifests);String value=bad.get("aurora_focus");
            check(value.contains(invalidBefore[i]),"test mutation exists: "+invalidBefore[i]);
            bad.put("aurora_focus",value.replace(invalidBefore[i],invalidAfter[i]));
            check(load(catalogJson,bad).size()==1,"invalid v2 property falls back: "+invalidAfter[i]);
        }
        bad=new LinkedHashMap<String,String>(manifests);bad.put("original",bad.get("original").replace("\"cardPadding\": 10","\"cardPadding\": 12"));
        check(load(catalogJson,bad).size()==1,"Original typography/spacing/material baseline is immutable");
        check(load(catalogJson.replace("\"schemaVersion\": 2","\"schemaVersion\": 1"),manifests).size()==1,"v1 requires explicit migration");
        String v3catalog="{\"schemaVersion\":3,\"themeIds\":[\"original\",\"parent\",\"child\"]}";
        Map<String,String> v3=new LinkedHashMap<String,String>();
        v3.put("original",manifests.get("original"));
        v3.put("parent","{\"schemaVersion\":3,\"id\":\"parent\",\"displayName\":\"Parent\",\"extends\":\"original\",\"files\":{\"colors\":\"colors.json\",\"fonts\":\"fonts.json\"}}");
        v3.put("parent/colors.json","{\"tokens\":{\"accent\":\"#FF123456\"}}");
        v3.put("parent/fonts.json","{\"fonts\":{\"body\":\"fonts/parent.otf\"}}");
        v3.put("child","{\"schemaVersion\":3,\"id\":\"child\",\"displayName\":\"Child\",\"extends\":\"parent\",\"files\":{\"fonts\":\"fonts.json\",\"shell\":\"layouts/shell.json\",\"overview\":\"layouts/overview.json\"}}");
        v3.put("child/fonts.json","{\"typography\":{\"numericScale\":120}}");
        v3.put("child/layouts/shell.json","{\"shell\":{\"position\":\"left\",\"alignment\":\"center\",\"width\":160,\"padding\":{\"top\":10,\"right\":12,\"bottom\":20,\"left\":8},\"gap\":5,\"buttonHeight\":40}}");
        v3.put("child/layouts/overview.json","{\"overview\":{\"columns\":3,\"rows\":3,\"padding\":{\"top\":4,\"right\":8,\"bottom\":6,\"left\":10},\"gapX\":8,\"gapY\":6,\"cards\":[{\"id\":\"speed_wide\",\"source\":7,\"page\":0,\"column\":0,\"row\":0,\"columnSpan\":2,\"rowSpan\":1},{\"id\":\"speed_again\",\"source\":7,\"page\":0,\"column\":2,\"row\":0,\"columnSpan\":1,\"rowSpan\":1},{\"id\":\"rpm\",\"source\":0,\"page\":1,\"column\":0,\"row\":1,\"columnSpan\":1,\"rowSpan\":1}]}}");
        ThemeCatalog inherited=load(v3catalog,v3);
        check(inherited.size()==3&&inherited.find("child").accent==0xff123456&&inherited.find("child").typography.numericScale==120&&"fonts/parent.otf".equals(inherited.find("child").bodyFont),"v3 files inherit and merge individual properties");
        check(inherited.find("original").accent==0xffa4d9ef&&inherited.find("parent").typography.numericScale==100,"child merge leaves ancestors unchanged");
        ThemeSpec child=inherited.find("child");
        check(child.shell().left()&&child.shell().width==160&&child.shell().left==8&&child.shell().right==12,"shell settings load with four-sided padding");
        check(com.veloster.dashboard.view.DashboardPage.shellHit(child,78,com.veloster.dashboard.view.DashboardPage.shellTop(child,1)+10)==1,"left navigation uses calculated bounds");
        OverviewLayout customLayout=new OverviewLayout();customLayout.theme(child);
        check(customLayout.pageCount()==2&&customLayout.count()==2&&customLayout.signalAt(0)==7&&customLayout.signalAt(1)==7,"custom cards preserve order and allow repeated sources");
        check(customLayout.widthAt(0)>customLayout.widthAt(1)*1.9f&&customLayout.hit(customLayout.leftAt(1)+5,customLayout.topAt(1)+5)==7,"spans and repeated card hit geometry");
        check(customLayout.page(1)&&customLayout.count()==1&&customLayout.signalAt(0)==0,"custom overview pagination");
        Map<String,String> topShell=new LinkedHashMap<String,String>(v3);
        topShell.put("child/layouts/shell.json",topShell.get("child/layouts/shell.json").replace("\"position\":\"left\"","\"position\":\"top\""));
        ThemeSpec topTheme=load(v3catalog,topShell).find("child");
        check(topTheme!=null&&!topTheme.shell().left(),"shell position top restores the legacy horizontal geometry");
        android.content.Context assetContext=new android.content.Context(){public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager(){public java.io.InputStream open(String path){return new java.io.ByteArrayInputStream(new byte[]{1});}};}};
        ThemeResources.load(assetContext,inherited.find("child"));
        check("themes/parent/fonts/parent.otf".equals(android.graphics.Typeface.lastAssetPath),"inherited fonts resolve in their owning theme bundle");
        Map<String,String> broken=new LinkedHashMap<String,String>(v3);
        broken.put("child",broken.get("child").replace("parent","child"));
        check(load(v3catalog,broken).size()==1,"v3 inheritance cycles fall back");
        broken=new LinkedHashMap<String,String>(v3);broken.put("child",broken.get("child").replace("parent","absent"));
        check(load(v3catalog,broken).size()==1,"missing parent falls back");
        broken=new LinkedHashMap<String,String>(v3);broken.put("child",broken.get("child").replace("fonts.json","../fonts.json"));
        check(load(v3catalog,broken).size()==1,"parent directory references are rejected");
        broken=new LinkedHashMap<String,String>(v3);broken.put("child/fonts.json","{\"typography\":{\"numericScale\":null}}");
        check(load(v3catalog,broken).size()==1,"null override is rejected");
        broken=new LinkedHashMap<String,String>(v3);broken.put("child/fonts.json","{\"typography\":{\"unknown\":2}}");
        check(load(v3catalog,broken).size()==1,"unknown nested property is rejected");
        broken=new LinkedHashMap<String,String>(v3);broken.put("child/layouts/overview.json",broken.get("child/layouts/overview.json").replace("\"column\":2","\"column\":1"));
        check(load(v3catalog,broken).size()==1,"overlapping overview cards are rejected");
        broken=new LinkedHashMap<String,String>(v3);broken.put("child/layouts/overview.json",broken.get("child/layouts/overview.json").replace("\"columns\":3","\"columns\":6"));
        check(load(v3catalog,broken).size()==1,"grid must fit actual shell content, not just the old 800px page");
        Map<String,String> grandchild=new LinkedHashMap<String,String>(v3);
        grandchild.put("variant","{\"schemaVersion\":3,\"id\":\"variant\",\"displayName\":\"Variant\",\"extends\":\"child\",\"files\":{\"overview\":\"layouts/overview.json\"}}");
        grandchild.put("variant/layouts/overview.json","{\"overview\":{\"cards\":[]}}");
        ThemeCatalog replaced=load("{\"schemaVersion\":3,\"themeIds\":[\"original\",\"parent\",\"child\",\"variant\"]}",grandchild);
        check(replaced.size()==4&&replaced.find("variant").overview().count()==0&&replaced.find("child").overview().count()==3,"empty array replaces inherited cards without changing parent");
        grandchild.put("variant/layouts/overview.json","{\"overview\":{\"padding\":{\"left\":30}}}");
        ThemeSpec partial=load("{\"schemaVersion\":3,\"themeIds\":[\"original\",\"parent\",\"child\",\"variant\"]}",grandchild).find("variant");
        check(partial!=null&&partial.overview().left==30&&partial.overview().right==8&&partial.overview().count()==3,"nested overrides retain inherited siblings and arrays");
        Map<String,String> styled=new LinkedHashMap<String,String>(v3);
        for(String id:new String[]{"parent","child"})
            styled.put(id,styled.get(id).replace("\"files\":{","\"files\":{\"components\":\"components.json\","));
        styled.put("parent/components.json","{\"mediaButtonGap\":12,\"surfaceStyles\":{\"navigationButton\":\"glass\",\"controlButton\":\"flat\",\"dataCard\":\"glass\"}}");
        styled.put("child/components.json","{\"mediaButtonGap\":21,\"material\":{\"radius\":20},\"surfaceStyles\":{\"dataCard\":\"flat\",\"mediaButton\":\"glass\",\"settingsGroup\":\"glass\"}}");
        ThemeCatalog mixed=load(v3catalog,styled);
        check(mixed.size()==3,"mixed styles resolve through a v3 catalog");
        ThemeSpec variant=mixed.find("child").withDisplayOptions(1,0,2);
        check(variant.mediaButtonGap(6)==21&&mixed.find("parent").mediaButtonGap(6)==12,
            "media button spacing is theme-controlled, inherited, overridden, and retained by render copies");
        check(variant.backdrop.mode==ThemeSpec.Backdrop.Mode.SOLID,"v3 without a backdrop defaults to solid independently of surface styles");
        check(variant.materialFor(SurfaceRole.NAVIGATION_BUTTON).glass()&&variant.materialFor(SurfaceRole.MEDIA_BUTTON).glass()
            &&variant.materialFor(SurfaceRole.SETTINGS_GROUP).glass()
            &&"flat".equals(variant.materialFor(SurfaceRole.CONTROL_BUTTON).style)
            &&"flat".equals(variant.materialFor(SurfaceRole.DATA_CARD).style)
            &&variant.materialFor(SurfaceRole.PANEL).legacy(),"surface roles inherit and override independently and survive render copies");
        check(variant.materialFor(SurfaceRole.NAVIGATION_BUTTON).radius==20&&mixed.find("parent").materialFor(SurfaceRole.DATA_CARD).glass(),
            "an inherited role uses the child's shared parameters without mutating the parent");
        Map<String,String> invalidFooter=new LinkedHashMap<String,String>(styled);
        for(String invalid:new String[]{"{\"controls\":\"icons\"}","{\"buttonBackground\":\"false\"}","{\"containerVisible\":\"false\"}","{\"unknown\":false}","null"}){
            invalidFooter.put("child/components.json","{\"mediaFooter\":"+invalid+"}");
            check(load(v3catalog,invalidFooter).size()==1,"invalid footer configuration is rejected: "+invalid);
        }
        for(String invalid:new String[]{"-1","65","2.5","\"12\""}){
            invalidFooter.put("child/components.json","{\"mediaButtonGap\":"+invalid+"}");
            check(load(v3catalog,invalidFooter).size()==1,"invalid media button gap is rejected: "+invalid);
        }
        styled.put("parent/colors.json","{\"tokens\":{\"accent\":\"#FF123456\"},\"palette\":\"componentDefaults\",\"backdrop\":{\"style\":\"gradient\",\"startColor\":\"#FF101010\",\"endColor\":\"#FF303030\",\"scanlineColor\":\"#0F204060\"}}");
        styled.put("parent/components.json","{\"decorations\":{\"overviewCardTopLine\":false,\"navigationUnderline\":\"off\"},\"fills\":{\"navigation\":\"#FF213243\",\"gaugeToggle\":\"#FF345678\"},\"assets\":{\"background\":\"backgrounds/parent.png\"},\"wallpapers\":[{\"id\":\"parent_blue\",\"name\":\"Blue\",\"asset\":\"backgrounds/parent.png\",\"thumbnail\":\"backgrounds/parent_thumb.png\",\"default\":true}]}");
        styled.put("child/components.json","{\"decorations\":{\"navigationUnderline\":\"on\"},\"surfaceStyles\":{\"navigationButton\":\"glass\"},\"fills\":{\"gaugeToggle\":\"token\"}}");
        ThemeCatalog appearance=load(v3catalog,styled);
        ThemeSpec parent=appearance.find("parent");
        ThemeSpec childAppearance=ThemeCatalog.withMenuPosition(appearance.find("child"),ThemeSpec.MENU_TOP).withDisplayOptions(1,1,2);
        check(parent.wallpapers().size()==1&&childAppearance.wallpapers().size()==1
              &&"parent".equals(childAppearance.wallpapers().get(0).owner)&&appearance.wallpapers().size()==2,
              "wallpaper options inherit their owning theme and deduplicate in the global picker");
        Map<String,String> ownWallpaper=new LinkedHashMap<String,String>(styled);
        ownWallpaper.put("child/components.json","{\"assets\":{\"background\":\"backgrounds/child.png\"}}");
        ThemeCatalog ownImages=load(v3catalog,ownWallpaper);
        ThemeSpec ownChild=ownImages.find("child");
        check(ownChild!=null&&ownImages.wallpapers().size()==3&&ownChild.wallpapers().size()==2
              &&"child".equals(ownImages.findWallpaper(ownChild.defaultWallpaperId()).owner)
              &&"parent_blue".equals(ownImages.find("parent").defaultWallpaperId()),
              "an overridden image gets its own entry and default while retaining the parent's option");
        ownWallpaper.put("child/components.json",styled.get("parent/components.json"));
        check(load(v3catalog,ownWallpaper).size()==1,"cross-bundle duplicate wallpaper IDs are rejected at load time");
        ownWallpaper.put("child/components.json",styled.get("child/components.json"));
        ownWallpaper.put("parent/components.json",styled.get("parent/components.json").replace("backgrounds/parent_thumb.png","backgrounds/../parent_thumb.png"));
        check(load(v3catalog,ownWallpaper).size()==1,"wallpaper thumbnail cannot use a parent directory");
        check(!parent.navigationUnderline()&&childAppearance.navigationUnderline()&&!childAppearance.overviewCardTopLine(),
            "decoration values merge by key and persist through menu and display overrides");
        check(childAppearance.navigationFill(false)==0xff213243&&parent.gaugeToggleFill()==0xff345678&&childAppearance.gaugeToggleFill()==childAppearance.panelRaised,
            "inherited component fills can be restored to the shared token");
        check(ThemePalette.accent(childAppearance,0xffdfdfdf)==0xffdfdfdf&&childAppearance.backdrop.scanlineColor==0x0f204060,
            "palette and backdrop apply to an arbitrary theme ID");
        Map<String,String> alphaColors=new LinkedHashMap<String,String>(styled);
        String opaqueBackdrop=styled.get("parent/colors.json").replace(
            "\"accent\":\"#FF123456\"", "\"background\":\"#FF202020\",\"panel\":\"#80202020\",\"panelRaised\":\"#80202020\",\"accent\":\"#FF123456\"");
        alphaColors.put("parent/colors.json",opaqueBackdrop);
        ThemeSpec alphaChild=load(v3catalog,alphaColors).find("child");
        check(alphaChild!=null&&alphaChild.panel==0x80202020&&alphaChild.panelRaised==0x80202020,
            "inherited container fills may be translucent over an opaque backdrop");
        for(String opaque:new String[]{"#FF202020","#FF101010","#FF303030"}){
            alphaColors.put("parent/colors.json",opaqueBackdrop.replace(opaque,"#80"+opaque.substring(3)));
            check(load(v3catalog,alphaColors).size()==1,"v3 rejects alpha in screen base or gradient stops: "+opaque);
        }
        for(String invalid:new String[]{"{\"surfaceStyles\":{\"controlButton\":\"unknown\"}}",
                "{\"surfaceStyles\":{\"controlButton\":3}}","{\"surfaceStyles\":{\"unknown\":\"glass\"}}",
                "{\"surfaceStyles\":{\"controlButton\":null}}","{\"decorations\":{\"overviewCardTopLine\":\"false\"}}",
                "{\"decorations\":{\"navigationUnderline\":true}}","{\"fills\":{\"gaugeToggle\":\"#123456\"}}"}) {
            Map<String,String> invalidRoles=new LinkedHashMap<String,String>(styled);
            invalidRoles.put("child/components.json",invalid);
            check(load(v3catalog,invalidRoles).size()==1,"invalid component appearance recovers to Original");
        }
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
