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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ThemeManifestTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String read(String path)throws Exception{return new String(Files.readAllBytes(Paths.get(path)),Charset.forName("UTF-8"));}
    private static Map<String,String> docs(String root)throws Exception{
        Map<String,String> result=new LinkedHashMap<String,String>();
        for(String id:new String[]{"original","aurora_focus","f1_2","f1_2_flat","f1_2_optics","material_auto_cowork"}){
            String manifest=read(root+"/"+id+"/manifest.json");result.put(id,manifest);
            Matcher files=Pattern.compile("\\\"[a-zA-Z0-9_]+\\\"\\s*:\\s*\\\"([^\\\"]+\\.json)\\\"").matcher(
                manifest.substring(manifest.indexOf("\"files\"")+7));
            while(files.find())result.put(id+"/"+files.group(1),read(root+"/"+id+"/"+files.group(1)));
        }
        return result;
    }
    private static ThemeCatalog load(String catalog,Map<String,String> manifests){return ThemeCatalog.fromJsonDocuments(catalog,manifests);}
    public static void main(String[] args)throws Exception{
        check("body".equals(ThemeSpec.textRole("F1",false))&&"body".equals(ThemeSpec.textRole("F1 설정",false)),"F1 labels use the body typography role");
        final String assetRoot=args[0];
        String catalogJson=read(assetRoot+"/catalog.json");Map<String,String> manifests=docs(assetRoot);
        ThemeCatalog catalog=load(catalogJson,manifests);
        android.content.Context bundledContext=new android.content.Context(){public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager(){public java.io.InputStream open(String path)throws java.io.IOException{return Files.newInputStream(Paths.get(assetRoot).getParent().resolve(path));}};}};
        ThemeCatalog bundled=ThemeCatalog.load(bundledContext);
        check(bundled.size()==6,"v3 bundle contains six selectable themes");
        check("original".equals(bundled.resolve(null).id),"Original remains the default theme");
        ThemeSpec f12=bundled.find("f1_2");
        check(f12!=null&&"Liquid Glass".equals(f12.displayName)&&f12.f1Grid()!=null&&f12.shell().left()&&
              f12.material.glass()&&f12.shell().menuIcons()&&f12.shell().dock(),"F1-2 Glass defaults to icon navigation in a dock");
        check(f12.shell().framePaddingHorizontal==16&&f12.shell().framePaddingVertical==16
              &&f12.shell().frameGapHorizontal==16&&f12.shell().frameGapVertical==12
              &&f12.shell().groupGap==8&&f12.shell().itemGap==0,
              "F1-2 frame, menu group and menu item spacing load from their separate theme fields");
        check(f12.f1Grid().padding==14&&f12.f1Grid().gap==10,
              "F1-2 keeps inner card padding separate from the shared layout gap");
        ThemeSpec.OverviewCardStyle overviewDefault=f12.overviewCardStyle(ThemeSpec.OverviewCardKind.STANDARD);
        ThemeSpec.OverviewCardStyle overviewSpecial=f12.overviewCardStyle(ThemeSpec.OverviewCardKind.TIRE_PRESSURE);
        check(overviewDefault.padding.top==12&&overviewDefault.padding.right==12
              &&overviewSpecial.padding.top==12&&overviewSpecial.padding.left==12,
              "overview card types inherit the default inner padding");
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
            check(f12Overview.count()==20,"F1-2 keeps all overview cards in the fixed 5x4 overview");
            for(int slot=0;slot<originalOverview.count();slot++)
                check(f12Overview.signalAt(slot)==originalOverview.signalAt(slot)
                      &&f12Overview.hit(f12Overview.leftAt(slot)+f12Overview.widthAt(slot)/2,
                                        f12Overview.topAt(slot)+f12Overview.heightAt(slot)/2)==f12Overview.signalAt(slot),
                      "F1-2 preserves overview order and uses its own shell geometry for "+profile);
        }
        check(ThemeResources.load(bundledContext,f12).font("body",false)!=android.graphics.Typeface.DEFAULT,
              "F1-2 loads its bundled Pretendard font");
        ThemeSpec flat=bundled.find("f1_2_flat");
        check(bundled.wallpapers().size()==7&&flat.wallpapers().size()==4
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
              &&optics.shell().left()&&optics.shell().dock()&&optics.shell().width==64
              &&f12.shell().width==64&&optics.shell().framePadding==16&&optics.shell().top==16&&optics.shell().bottom==16
              &&optics.mediaControls()==ThemeSpec.MediaControls.CONTAINER&&f12.usesLiquidGlass()
              &&!flat.usesLiquidGlass()&&optics.usesLiquidGlass(),
              "Optics is selectable with a left dock and retains its wide media container");
        check(f12.layoutSpacing().horizontalPadding==16&&f12.layoutSpacing().verticalPadding==16
              &&optics.layoutSpacing().horizontalPadding==16&&optics.layoutSpacing().verticalPadding==16,
              "Liquid Glass themes preserve 16-unit horizontal and vertical frame padding");
        ThemeSpec materialAuto=bundled.find("material_auto_cowork");
        String materialAutoManifest=read(assetRoot+"/material_auto_cowork/manifest.json");
        ThemeSpec originalTheme=bundled.find("original");
        check(flat.shell().menuIcons()&&optics.shell().menuIcons()&&materialAuto.shell().menuIcons()
              &&!originalTheme.shell().menuIcons(),"navigation presentation defaults inherit per theme");
        check(materialAuto!=null&&"Material Auto".equals(materialAuto.displayName)
              &&!materialAutoManifest.contains("\"extends\"")
              &&materialAuto.background==0xff090d12&&materialAuto.accent==0xff9ccbff
              &&materialAuto.warning==0xffffc46b&&materialAuto.critical==0xffff6b72
              &&materialAuto.unavailable==0xff8995a3&&"flat".equals(materialAuto.material.style)
              &&materialAuto.materialFor(SurfaceRole.DATA_CARD).style.equals("flat")
              &&materialAuto.materialFor(SurfaceRole.PANEL).style.equals("flat")
              &&materialAuto.columns==5&&materialAuto.rows==4
              &&materialAuto.layoutSpacing().horizontal==12&&materialAuto.layoutSpacing().vertical==10
              &&materialAuto.typography.bodyScale==104&&materialAuto.typography.numericScale==112
              &&materialAuto.shell().dock()&&materialAuto.shell().width==80
              &&materialAuto.backdrop.mode==ThemeSpec.Backdrop.Mode.SOLID
              &&materialAuto.f1Grid()!=null&&materialAuto.f1Grid().radius==12,
              "Material Auto is standalone with its own palette, typography, spacing, shell and layouts");
        OverviewLayout materialAutoOverview=new OverviewLayout();materialAutoOverview.theme(materialAuto);
        for(String profile:new String[]{"js","js_n","pd"}){
            materialAutoOverview.jsProfile("js".equals(profile));materialAutoOverview.pdProfile("pd".equals(profile));
            check(materialAutoOverview.count()==20,"Material Auto keeps all overview slots on one 5x4 screen for "+profile);
            for(int slot=0;slot<materialAutoOverview.count();slot++){
                int signal=materialAutoOverview.signalAt(slot);
                check(materialAutoOverview.hit(materialAutoOverview.left(signal)+materialAutoOverview.width(signal)/2,
                      materialAutoOverview.top(signal)+materialAutoOverview.height(signal)/2)==signal,
                      "Material Auto page hit geometry matches drawing for "+profile);
            }
        }
        check(materialAuto.color(ThemeSpec.ColorRole.BACKGROUND)==materialAuto.background
              &&materialAuto.color(ThemeSpec.ColorRole.SURFACE)==materialAuto.panel
              &&materialAuto.color(ThemeSpec.ColorRole.SURFACE_RAISED)==materialAuto.panelRaised
              &&materialAuto.color(ThemeSpec.ColorRole.ON_SURFACE)==materialAuto.textPrimary
              &&materialAuto.color(ThemeSpec.ColorRole.ON_SURFACE_VARIANT)==materialAuto.textSecondary
              &&materialAuto.color(ThemeSpec.ColorRole.PRIMARY)==materialAuto.accent
              &&materialAuto.color(ThemeSpec.ColorRole.OUTLINE)==materialAuto.stroke
              &&materialAuto.color(ThemeSpec.ColorRole.WARNING)==materialAuto.warning
              &&materialAuto.color(ThemeSpec.ColorRole.CRITICAL)==materialAuto.critical
              &&materialAuto.color(ThemeSpec.ColorRole.UNAVAILABLE)==materialAuto.unavailable,
              "CoWork semantic color accessors resolve directly to the canonical v3 tokens");
        check(ThemePalette.supportsAccentSelection(materialAuto)&&ThemePalette.supportsAccentSelection(f12)
              &&ThemePalette.supportsAccentSelection(flat)&&ThemePalette.supportsAccentSelection(optics)
              &&!ThemePalette.supportsAccentSelection(originalTheme),
              "accent palette is exposed only for Material Auto and Liquid Glass themes");
        ThemeSpec recolored=materialAuto.withAccent(ThemePalette.accentColor(materialAuto,2));
        check(recolored!=materialAuto&&recolored.accent==0xff48c9b0&&materialAuto.accent==0xff9ccbff
              &&recolored.warning==materialAuto.warning&&recolored.critical==materialAuto.critical,
              "accent selection changes a runtime copy and preserves status colors and manifest values");
        check(ThemePalette.accentColor(f12,0)==f12.accent
              &&ThemePalette.accentColor(materialAuto,0)==materialAuto.accent
              &&ThemePalette.accentColor(f12,1)==0xff0088ff
              &&ThemePalette.accentColor(materialAuto,1)==0xff78b7ff,
              "default accent restores each theme color and engine-specific swatches adapt to appearance");
        check("파랑".equals(ThemePalette.accentName(materialAuto.id,1))
              &&"인디고".equals(ThemePalette.accentName(f12.id,2))
              &&"청록".equals(ThemePalette.accentName(materialAuto.id,2))
              &&"보라".equals(ThemePalette.accentName(f12.id,3))
              &&ThemePalette.accentColor(f12,2)==0xff6155f5
              &&ThemePalette.accentColor(f12,3)==0xffcb30e0
              &&"핑크".equals(ThemePalette.accentName(f12.id,4)),
              "Material and Liquid Glass expose engine-specific palette labels");
        check(materialAuto.typography.bodyScale!=originalTheme.typography.bodyScale
              &&materialAuto.typography.numericScale!=originalTheme.typography.numericScale
              &&materialAuto.layoutSpacing().gap!=originalTheme.layoutSpacing().gap
              &&materialAuto.backdrop.mode==ThemeSpec.Backdrop.Mode.SOLID
              &&materialAuto.backdrop.startColor==null&&materialAuto.backdrop.scanlineColor==0
              &&!materialAuto.footerButtonBackground()&&materialAuto.footerContainerVisible(),
              "Material Auto declares its own typography, spacing, backdrop and media treatment");
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
              &&f12.shell().dock()&&f12.blurFor(SurfaceRole.DATA_CARD)==0,
              "Optics separates subdued content surfaces from lightly blurred control containers");
        check(optics.background==f12.background&&optics.backdrop.mode==f12.backdrop.mode
              &&optics.backdrop.endColor==f12.backdrop.endColor&&optics.backgroundAsset.equals(f12.backgroundAsset)
              &&optics.defaultWallpaperId().equals(f12.defaultWallpaperId())&&optics.wallpapers().size()==4
              &&"f1_2".equals(optics.wallpapers().get(0).owner)&&bundled.wallpapers().size()==7,
              "Optics inherits the F1-2 backdrop and wallpaper assets without adding gallery duplicates");
        check(optics.f1Grid()!=null&&optics.f1Grid().gap==f12.f1Grid().gap&&optics.rows==f12.rows
              &&optics.columns==f12.columns&&optics.bodyFont.equals(f12.bodyFont),
              "Optics inherits F1 and overview geometry from F1-2");
        check(optics.fontStyle("body")!=null&&optics.fontStyle("body").normal==400&&optics.fontStyle("body").emphasis==600
              &&optics.fontStyle("numeric")!=null&&optics.fontStyle("numeric").normal==500&&optics.fontStyle("numeric").emphasis==700
              &&"fonts/Pretendard-Medium.otf".equals(optics.fallbackWeightFont(500)),
              "Optics inherits the four-weight Pretendard role mapping from F1-2");
        ThemeResources opticsResources=ThemeResources.load(bundledContext,optics);
        check(opticsResources.font("body",false)!=android.graphics.Typeface.DEFAULT,
              "Optics loads its inherited body font");
        check("themes/f1_2/fonts/PretendardVariable.ttf".equals(opticsResources.font("numeric",true).sourceAssetPath),
              "Optics loads the inherited variable Pretendard face from F1-2");
        android.graphics.Typeface.failVariableBuild=true;
        ThemeResources opticsStaticResources=ThemeResources.load(bundledContext,optics);
        android.graphics.Typeface.failVariableBuild=false;
        check("themes/f1_2/fonts/Pretendard-Bold.otf".equals(opticsStaticResources.font("numeric",true).sourceAssetPath),
              "Optics uses its inherited API 17 fallback faces from F1-2");
        check(catalog.size()==6,"current schema 3 catalog resolves all bundled themes: "+catalog.size());
        check(catalog.find("original").background==0xff040506,"Original schema 3 tokens parse");
        ThemeSpec focus=catalog.find("aurora_focus");
        check(focus!=null&&focus.columns==5&&focus.rows==4&&"aurora_focus".equals(focus.id),"Focus ID and fixed grid parse");
        check(bundled.find("carplay_glass_light")==null&&bundled.find("carplay_glass_dark")==null,
              "CarPlay Glass Light and Dark are no longer selectable");
        check(bundled.find("aurora_focus").background==0xff071118
              &&bundled.find("aurora_focus").bodyFont.equals(bundled.find("original").bodyFont),
              "Aurora Focus overrides Original while inheriting its font defaults");
        ThemeSpec originalLeft=ThemeCatalog.withMenuPosition(bundled.find("original"),ThemeSpec.MENU_LEFT);
        check(originalLeft.shell().width==96&&originalLeft.shell().left==0&&originalLeft.shell().right==0,
              "Original left navigation occupies 96px");
        check("original".equals(load("{\"schemaVersion\":3,\"themeIds\":[\"original\",\"aurora_focus\",\"aurora_focus\"]}",manifests).resolve("aurora_focus").id),"duplicate catalog ID falls back to Original only");
        check("original".equals(load(catalogJson,new LinkedHashMap<String,String>()).resolve("aurora_focus").id),"missing manifests fall back safely");
        check("original".equals(load(catalogJson.replace("\"schemaVersion\": 3","\"schemaVersion\": 99"),manifests).resolve("aurora_focus").id),"unsupported catalog version falls back");
        Map<String,String> bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus/colors.json",bad.get("aurora_focus/colors.json").replace("#FF65E0D0","cyan"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id),"invalid token value rejects catalog and falls back");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("aurora_focus/colors.json",bad.get("aurora_focus/colors.json").replace("#FF071118","#80071118"));
        check(load(catalogJson,bad).size()==1,"translucent screen base is rejected");
        bad=new LinkedHashMap<String,String>(manifests);
        bad.put("original/colors.json",bad.get("original/colors.json").replace("#FF040506","#FF000000"));
        check("original".equals(load(catalogJson,bad).resolve("aurora_focus").id)&&load(catalogJson,bad).size()==1,"altered Original manifest falls back to canonical Original");
        check(ThemeSpec.VERSION==3&&focus.typography.bodyScale==100&&focus.material.legacy(),"schema 3 values load without legacy spacing fields");
        Map<String,String> retired=new LinkedHashMap<String,String>();
        for(int version:new int[]{1,2}){
            retired.put("legacy","{\"schemaVersion\":"+version+",\"id\":\"legacy\",\"displayName\":\"Legacy\"}");
            check(load("{\"schemaVersion\":"+version+",\"themeIds\":[\"legacy\"]}",retired).find("legacy")==null,
                "schema "+version+" catalog is rejected");
            check(load("{\"schemaVersion\":3,\"themeIds\":[\"legacy\"]}",retired).find("legacy")==null,
                "schema "+version+" manifest is rejected");
        }
        String v3catalog="{\"schemaVersion\":3,\"themeIds\":[\"original\",\"parent\",\"child\"]}";
        Map<String,String> v3=new LinkedHashMap<String,String>();
        v3.putAll(manifests);
        v3.put("parent","{\"schemaVersion\":3,\"id\":\"parent\",\"displayName\":\"Parent\",\"extends\":\"original\",\"files\":{\"colors\":\"colors.json\",\"fonts\":\"fonts.json\"}}");
        v3.put("parent/colors.json","{\"tokens\":{\"accent\":\"#FF123456\"}}");
        v3.put("parent/fonts.json","{\"fonts\":{\"body\":\"fonts/parent.otf\"}}");
        v3.put("child","{\"schemaVersion\":3,\"id\":\"child\",\"displayName\":\"Child\",\"extends\":\"parent\",\"files\":{\"fonts\":\"fonts.json\",\"shell\":\"layouts/shell.json\",\"overview\":\"layouts/overview.json\"}}");
        v3.put("child/fonts.json","{\"typography\":{\"numericScale\":120}}");
        v3.put("child/layouts/shell.json","{\"shell\":{\"navigationPosition\":\"left\",\"frame\":{\"padding\":0,\"gap\":{\"horizontal\":0,\"vertical\":0}},\"sideMenuBar\":{\"alignment\":\"center\",\"width\":160,\"gap\":5,\"itemGap\":5,\"buttonHeight\":40}}}");
        v3.put("child/layouts/overview.json","{\"overview\":{\"grid\":{\"columns\":5,\"rows\":4,\"itemGapX\":8,\"itemGapY\":0},\"cards\":[{\"id\":\"speed_wide\",\"source\":7,\"column\":0,\"row\":0,\"columnSpan\":2,\"rowSpan\":1},{\"id\":\"speed_again\",\"source\":7,\"column\":2,\"row\":0,\"columnSpan\":1,\"rowSpan\":1},{\"id\":\"rpm\",\"source\":0,\"column\":0,\"row\":1,\"columnSpan\":1,\"rowSpan\":1}]}}");
        ThemeCatalog inherited=load(v3catalog,v3);
        check(inherited.size()==3&&inherited.find("child").accent==0xff123456&&inherited.find("child").typography.numericScale==120&&"fonts/parent.otf".equals(inherited.find("child").bodyFont),"v3 files inherit and merge individual properties");
        check(inherited.find("original").accent==0xffa4d9ef&&inherited.find("parent").typography.numericScale==100,"child merge leaves ancestors unchanged");
        ThemeSpec child=inherited.find("child");
        check(child.shell().left()&&child.shell().width==160&&child.shell().left==0&&child.shell().right==0&&child.shell().gap==5,"shell uses item spacing without outer padding");
        check(com.veloster.dashboard.view.DashboardPage.shellHit(child,78,com.veloster.dashboard.view.DashboardPage.shellTop(child,1)+10)==1,"left navigation uses calculated bounds");
        OverviewLayout customLayout=new OverviewLayout();customLayout.theme(child);
        check(customLayout.count()==3&&customLayout.signalAt(0)==7&&customLayout.signalAt(1)==7&&customLayout.signalAt(2)==0,"custom cards preserve order and allow repeated sources on one screen");
        check(customLayout.widthAt(0)>customLayout.widthAt(1)*1.9f&&customLayout.hit(customLayout.leftAt(1)+5,customLayout.topAt(1)+5)==7,"spans and repeated card hit geometry");
        Map<String,String> topShell=new LinkedHashMap<String,String>(v3);
        topShell.put("child/layouts/shell.json",topShell.get("child/layouts/shell.json").replace("\"navigationPosition\":\"left\"","\"navigationPosition\":\"top\""));
        ThemeSpec topTheme=load(v3catalog,topShell).find("child");
        check(topTheme!=null&&!topTheme.shell().left(),"shell position top restores top navigation");
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
        broken=new LinkedHashMap<String,String>(v3);broken.put("child/layouts/overview.json",broken.get("child/layouts/overview.json").replace("\"columns\":5","\"columns\":6"));
        check(load(v3catalog,broken).size()==1,"grid must fit actual shell content, not just the old 800px page");
        broken=new LinkedHashMap<String,String>(v3);broken.put("child/layouts/overview.json",broken.get("child/layouts/overview.json").replace("\"source\":7","\"source\":7,\"page\":1"));
        check(load(v3catalog,broken).find("child")==null,"overview card schema has no page property");
        Map<String,String> grandchild=new LinkedHashMap<String,String>(v3);
        grandchild.put("variant","{\"schemaVersion\":3,\"id\":\"variant\",\"displayName\":\"Variant\",\"extends\":\"child\",\"files\":{\"overview\":\"layouts/overview.json\"}}");
        grandchild.put("variant/layouts/overview.json","{\"overview\":{\"cards\":[]}}");
        ThemeCatalog replaced=load("{\"schemaVersion\":3,\"themeIds\":[\"original\",\"parent\",\"child\",\"variant\"]}",grandchild);
        check(replaced.size()==4&&replaced.find("variant").overview().count()==0&&replaced.find("child").overview().count()==3,"empty array replaces inherited cards without changing parent");
        grandchild.put("variant/layouts/overview.json","{\"overview\":{\"grid\":{\"itemGapX\":12}}}");
        ThemeSpec partial=load("{\"schemaVersion\":3,\"themeIds\":[\"original\",\"parent\",\"child\",\"variant\"]}",grandchild).find("variant");
        check(partial!=null&&partial.overview().gapX==12&&partial.overview().gapY==0&&partial.overview().count()==3,"nested item spacing override retains inherited siblings and cards");
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
        check(variant.backdrop.mode==ThemeSpec.Backdrop.Mode.GRADIENT,"child inherits Original's backdrop independently of surface styles");
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
