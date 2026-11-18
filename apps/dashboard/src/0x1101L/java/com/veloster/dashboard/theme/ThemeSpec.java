package com.veloster.dashboard.theme;

/** Immutable visual values consumed by Dashboard renderers. Vehicle semantics stay outside this type. */
public final class ThemeSpec {
    public static final int VERSION = 3;
    public static final int MENU_THEME=0,MENU_TOP=1,MENU_LEFT=2;
    public static final int DOCK_THEME=0,DOCK_ON=1,DOCK_OFF=2;
    private int pedalBarStyle,brakeBarStyle,f1Order;
    public int pedalBarStyle(){return pedalBarStyle;}
    public int brakeBarStyle(){return brakeBarStyle;}
    public int f1Order(){return f1Order;}
    /** Render copy only. These user choices are stored independently of theme manifests. */
    public ThemeSpec withDisplayOptions(int pedal,int brake,int order){
        ThemeSpec copy=withShell(shell);
        copy.pedalBarStyle=pedal==1?1:0;copy.brakeBarStyle=brake==1?1:0;
        copy.f1Order=order>=0&&order<6?order:0;
        return copy;
    }
    public final Typography typography;
    public final Spacing spacing;
    public final Material material;
    public final Backdrop backdrop;
    public enum Palette { THEME, COMPONENT_DEFAULTS }
    public enum Underline { AUTO, ON, OFF }
    private Palette palette=Palette.THEME;
    private boolean overviewCardTopLine=true;
    private Underline navigationUnderline=Underline.AUTO;
    private static final ComponentFills DEFAULT_FILLS=new ComponentFills(null,null,null);
    static final ComponentFills LEGACY_FILLS=new ComponentFills(0xff111415,null,0xff233b46);
    private ComponentFills fills=DEFAULT_FILLS;
    public boolean usesComponentPalette(){return palette==Palette.COMPONENT_DEFAULTS;}
    public boolean overviewCardTopLine(){return overviewCardTopLine;}
    public boolean navigationUnderline(){
        return navigationUnderline==Underline.ON||
            (navigationUnderline==Underline.AUTO&&!materialFor(SurfaceRole.NAVIGATION_BUTTON).glass());
    }
    public int navigationFill(boolean selected){
        return selected?colorOr(fills.navigationSelected,panelRaised):colorOr(fills.navigation,panel);
    }
    public int gaugeToggleFill(){return colorOr(fills.gaugeToggle,panelRaised);}
    private static int colorOr(Integer override,int fallback){return override==null?fallback:override.intValue();}
    void palette(Palette value){palette=value;}
    void decorations(boolean topLine,Underline underline){overviewCardTopLine=topLine;navigationUnderline=underline;}
    ComponentFills componentFills(){return fills;}
    void componentFills(ComponentFills value){fills=value;}
    boolean sameComponentAppearance(ThemeSpec other){
        return palette==other.palette&&overviewCardTopLine==other.overviewCardTopLine&&
            navigationUnderline==other.navigationUnderline&&fills.same(other.fills);
    }
    static final class ComponentFills {
        final Integer navigation,navigationSelected,gaugeToggle;
        ComponentFills(Integer navigation,Integer navigationSelected,Integer gaugeToggle){
            this.navigation=navigation;this.navigationSelected=navigationSelected;this.gaugeToggle=gaugeToggle;
        }
        boolean same(ComponentFills other){
            return equal(navigation,other.navigation)&&equal(navigationSelected,other.navigationSelected)&&equal(gaugeToggle,other.gaugeToggle);
        }
    }
    private static boolean equal(Integer a,Integer b){return a==null?b==null:a.equals(b);}
    public enum SurfaceRole {
        NAVIGATION_BUTTON("navigationButton"), CONTROL_BUTTON("controlButton"), MEDIA_BUTTON("mediaButton"),
        DATA_CARD("dataCard"), GEAR_CARD("gearCard"), PANEL("panel"), SETTINGS_GROUP("settingsGroup"),
        MEDIA_CONTAINER("mediaContainer"), NAVIGATION_DOCK("navigationDock");
        final String key;
        SurfaceRole(String key){this.key=key;}
    }
    private java.util.Map<SurfaceRole,Material> surfaceMaterials=java.util.Collections.emptyMap();
    void surfaceMaterials(java.util.Map<SurfaceRole,Material> values){
        surfaceMaterials=java.util.Collections.unmodifiableMap(new java.util.EnumMap<SurfaceRole,Material>(values));
    }
    public Material materialFor(SurfaceRole role){
        return materialFor(role,role==SurfaceRole.GEAR_CARD?SurfaceRole.DATA_CARD:role);
    }
    public Material materialFor(SurfaceRole role,SurfaceRole fallback){
        Material override=surfaceMaterials.get(role);
        if(override==null)override=surfaceMaterials.get(fallback);
        return override==null?material:override;
    }
    public enum MediaControls { BOTH, CONTAINER, BUTTONS }
    private MediaControls mediaControls=MediaControls.BOTH;
    public MediaControls mediaControls(){return mediaControls;}
    void mediaControls(MediaControls value){mediaControls=value;}
    private Integer mediaButtonGap;
    public boolean hasMediaButtonGap(){return mediaButtonGap!=null;}
    public int mediaButtonGap(int fallback){return mediaButtonGap==null?fallback:mediaButtonGap.intValue();}
    void mediaButtonGap(Integer value){mediaButtonGap=value;}
    private Boolean footerButtonBackground;
    private Boolean footerContainerVisible;
    public boolean footerButtonBackground(){
        return footerButtonBackground==null?mediaControls!=MediaControls.CONTAINER:footerButtonBackground.booleanValue();
    }
    public boolean footerUsesLegacyIcons(){
        return footerButtonBackground==null&&mediaControls==MediaControls.CONTAINER;
    }
    public boolean footerContainerVisible(){
        return footerContainerVisible==null?mediaControls!=MediaControls.BUTTONS:footerContainerVisible.booleanValue();
    }
    void mediaFooter(Boolean buttonBackground,Boolean containerVisible){
        footerButtonBackground=buttonBackground;footerContainerVisible=containerVisible;
    }
    private java.util.Map<SurfaceRole,Integer> surfaceBlur=java.util.Collections.emptyMap();
    void surfaceBlur(java.util.Map<SurfaceRole,Integer> values){surfaceBlur=java.util.Collections.unmodifiableMap(values);}
    public int blurFor(SurfaceRole role){Integer value=surfaceBlur.get(role);return value==null?0:value.intValue();}
    int maxBlur(){int max=0;for(int value:surfaceBlur.values())max=Math.max(max,value);return max;}
    private Shell shell=Shell.legacy();
    public Shell shell(){return shell;}
    void shell(Shell value){shell=value;}
    public ThemeSpec withMenuDock(int mode){
        if(!shell.left()||(mode!=DOCK_ON&&mode!=DOCK_OFF)||shell.dock()==(mode==DOCK_ON))return this;
        return withShell(shell.withDock(mode==DOCK_ON));
    }
    ThemeSpec withShell(Shell value){
        ThemeSpec copy=new ThemeSpec(id,displayName,background,panel,panelRaised,stroke,textPrimary,textSecondary,
            accent,warning,critical,unavailable,columns,rows,cutPanels,bodyFont,numericFont,displayFont,backgroundAsset,
            typography,spacing,material,backdrop);
        copy.surfaceMaterials=surfaceMaterials;copy.shell=value;copy.overview=overview;copy.f1=f1;copy.f1Bottom=f1Bottom;copy.f1Grid=f1Grid;
        copy.overviewCardStyles=overviewCardStyles;
        copy.surfaceBlur=surfaceBlur;copy.mediaControls=mediaControls;copy.mediaButtonGap=mediaButtonGap;
        copy.mediaFooter(footerButtonBackground,footerContainerVisible);
        copy.viewports=viewports;copy.weights=weights;
        copy.palette=palette;copy.overviewCardTopLine=overviewCardTopLine;copy.navigationUnderline=navigationUnderline;copy.fills=fills;
        copy.pedalBarStyle=pedalBarStyle;copy.brakeBarStyle=brakeBarStyle;copy.f1Order=f1Order;
        copy.bodyFontOwner=bodyFontOwner;copy.numericFontOwner=numericFontOwner;
        copy.displayFontOwner=displayFontOwner;copy.backgroundAssetOwner=backgroundAssetOwner;
        copy.wallpapers=wallpapers;copy.defaultWallpaperId=defaultWallpaperId;
        return copy;
    }
    private Overview overview;
    public Overview overview(){return overview;}
    void overview(Overview value){overview=value;}
    public enum OverviewCardKind {
        STANDARD("cardDefaults"), TIRE_PRESSURE("tirePressure"), DCT_TEMPERATURE("dctTemperature"),
        FUEL_ECONOMY("fuelEconomy"), STABILITY("stability");
        final String key;
        OverviewCardKind(String key){this.key=key;}
    }
    public static final class OverviewCardStyle {
        public final Insets padding;
        public final int titleSize,valueSize,unitSize,labelSize,titleValueGap,titleUnitGap,valueUnitGap,valueGaugeGap;
        public final int rowGap,labelValueGap,columnGap,barHeight,dialSize,gaugeWidth;
        public final String unitPosition;
        OverviewCardStyle(Insets padding,int title,int value,int unit,int label,int titleValueGap,int titleUnitGap,String unitPosition,int valueUnitGap,
                int valueGaugeGap,int rowGap,int labelValueGap,int columnGap,int barHeight,int dialSize,int gaugeWidth){
            this.padding=padding;titleSize=title;valueSize=value;unitSize=unit;labelSize=label;
            this.titleValueGap=titleValueGap;this.titleUnitGap=titleUnitGap;this.unitPosition=unitPosition;
            this.valueUnitGap=valueUnitGap;this.valueGaugeGap=valueGaugeGap;
            this.rowGap=rowGap;this.labelValueGap=labelValueGap;this.columnGap=columnGap;
            this.barHeight=barHeight;this.dialSize=dialSize;this.gaugeWidth=gaugeWidth;
        }
        static OverviewCardStyle defaults(OverviewCardKind kind,int side){
            Insets padding=new Insets(6,side,6,side);
            switch(kind){
                case TIRE_PRESSURE: return new OverviewCardStyle(padding,13,20,11,10,4,4,"bottomLeft",3,0,3,3,8,0,0,0);
                case DCT_TEMPERATURE: return new OverviewCardStyle(padding,13,16,0,11,4,4,"bottomLeft",0,5,4,4,0,0,0,32);
                case FUEL_ECONOMY: return new OverviewCardStyle(padding,13,13,0,10,4,4,"bottomLeft",0,0,3,4,0,0,0,0);
                case STABILITY: return new OverviewCardStyle(padding,13,16,0,12,4,4,"bottomLeft",0,0,3,4,0,0,0,0);
                default: return new OverviewCardStyle(padding,12,24,10,0,4,4,"bottomLeft",4,4,0,0,0,7,36,0);
            }
        }
    }
    private java.util.Map<OverviewCardKind,OverviewCardStyle> overviewCardStyles=java.util.Collections.emptyMap();
    public OverviewCardStyle overviewCardStyle(OverviewCardKind kind){
        OverviewCardStyle value=overviewCardStyles.get(kind);
        return value==null?OverviewCardStyle.defaults(kind,spacing.cardPadding):value;
    }
    void overviewCardStyles(java.util.Map<OverviewCardKind,OverviewCardStyle> value){
        overviewCardStyles=java.util.Collections.unmodifiableMap(value);
    }
    private java.util.Map<String,Rect> f1;
    private int f1Bottom=434;
    public int f1Bottom(){return f1Bottom;}
    public Rect f1(String id){return f1==null?null:f1.get(id);}
    public boolean hasF1(){return f1!=null;}
    void f1(java.util.Map<String,Rect> value){f1=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String,Rect>(value));f1Bottom=52;for(Rect r:value.values())f1Bottom=Math.max(f1Bottom,r.y+r.height);}
    private F1Grid f1Grid;
    public F1Grid f1Grid(){return f1Grid;}
    void f1Grid(F1Grid value){f1Grid=value;}
    public static final class F1Grid {
        public final int columns,rows,inset,gap,padding,radius;
        private final int[] weights;
        private final java.util.Map<String,Card> cells;
        F1Grid(int columns,int[] weights,int inset,int gap,int padding,int radius,java.util.Map<String,Card> cells){
            this.columns=columns;this.rows=weights.length;this.weights=weights.clone();this.inset=inset;
            this.gap=gap;this.padding=padding;this.radius=radius;
            this.cells=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String,Card>(cells));
        }
        public Card cell(String id){return cells.get(id);}
        public int rowWeight(int from,int to){int sum=0;for(int i=from;i<to;i++)sum+=weights[i];return sum;}
    }
    private java.util.Map<String,Viewport> viewports=java.util.Collections.emptyMap();
    public Viewport viewport(String page){return viewports.get(page);}
    void viewports(java.util.Map<String,Viewport> value){viewports=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String,Viewport>(value));}
    private java.util.Map<String,String> weights=java.util.Collections.emptyMap();
    public boolean fontBold(String role,boolean requested){String weight=weights.get(role);return "bold".equals(weight)||(!"regular".equals(weight)&&requested);}
    void weights(java.util.Map<String,String> value){weights=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String,String>(value));}
    public static final class Viewport {
        public final int top,right,bottom,left;
        public final String alignment;
        Viewport(int top,int right,int bottom,int left,String alignment){this.top=top;this.right=right;this.bottom=bottom;this.left=left;this.alignment=alignment;}

    }
    public static final class Rect {
        public final int x,y,width,height;
        public final String fontRole,colorRole;
        public final int top,right,bottom,left;
        public final String alignment;
        Rect(int x,int y,int width,int height,String fontRole,String colorRole,int top,int right,int bottom,int left,String alignment){this.x=x;this.y=y;this.width=width;this.height=height;this.fontRole=fontRole;this.colorRole=colorRole;this.top=top;this.right=right;this.bottom=bottom;this.left=left;this.alignment=alignment;}
    }

    public static final class Card {
        public final String id;
        public final int source,page,column,row,columnSpan,rowSpan;
        Card(String id,int source,int page,int column,int row,int columnSpan,int rowSpan){
            this.id=id;this.source=source;this.page=page;this.column=column;this.row=row;this.columnSpan=columnSpan;this.rowSpan=rowSpan;
        }
    }
    public static final class Overview {
        public final int columns,rows,top,right,bottom,left,gapX,gapY,pages;
        private final Card[] cards;
        Overview(int columns,int rows,int top,int right,int bottom,int left,int gapX,int gapY,int pages,Card[] cards){
            this.columns=columns;this.rows=rows;this.top=top;this.right=right;this.bottom=bottom;this.left=left;this.gapX=gapX;this.gapY=gapY;this.pages=pages;this.cards=cards.clone();
        }
        public int count(){return cards.length;}
        public Card card(int index){return cards[index];}
    }

    public enum ScreenCorner { CIRCLE, SQUIRCLE }

    /** Screen and navigation options in the 800×480 design space. */
    public static final class Shell {
        public static final int TOP_BUTTON_HEIGHT=38;
        public final String position,alignment;
        public final int width,top,right,bottom,left,gap,buttonHeight;
        public final Insets topPadding;
        public final int mediaPadding,screenCornerRadius;
        public final ScreenCorner screenCorner;
        private final boolean dock;
        Shell(String position,String alignment,int width,int top,int right,int bottom,int left,int gap,int buttonHeight){
            this(position,alignment,width,top,right,bottom,left,gap,buttonHeight,null,-1,0,ScreenCorner.CIRCLE,false);
        }
        private Shell(String position,String alignment,int width,int top,int right,int bottom,int left,int gap,int buttonHeight,Insets topPadding,int mediaPadding,int screenCornerRadius,ScreenCorner screenCorner,boolean dock){
            this.position=position;this.alignment=alignment;this.width=width;this.top=top;this.right=right;
            this.bottom=bottom;this.left=left;this.gap=gap;this.buttonHeight=buttonHeight;
            this.topPadding=topPadding;this.mediaPadding=mediaPadding;this.screenCornerRadius=screenCornerRadius;this.screenCorner=screenCorner;this.dock=dock;
        }
        Shell withScreenOptions(Insets topPadding,int mediaPadding,int screenCornerRadius,ScreenCorner screenCorner){
            return new Shell(position,alignment,width,top,right,bottom,left,gap,buttonHeight,topPadding,mediaPadding,screenCornerRadius,screenCorner,dock);
        }
        Shell withDock(boolean value){return new Shell(position,alignment,width,top,right,bottom,left,gap,buttonHeight,topPadding,mediaPadding,screenCornerRadius,screenCorner,value);}
        public boolean dock(){return left()&&dock;}
        public int topMenuHeight(){return topPadding==null?52:topPadding.top+TOP_BUTTON_HEIGHT+topPadding.bottom;}
        static Shell legacy(){return new Shell("top","start",0,0,0,0,0,0,0);}
        static Shell sidebar(){return new Shell("left","center",96,10,0,20,0,5,40);}
        public boolean left(){return "left".equals(position);}
    }
    public static final class Insets {
        public final int top,right,bottom,left;
        Insets(int top,int right,int bottom,int left){this.top=top;this.right=right;this.bottom=bottom;this.left=left;}
    }

    public static final class Typography {
        public final int bodyScale,numericScale,displayScale,gearSize,combinedGearSize;
        public Typography(int body,int numeric,int display,int gear,int combined){
            bodyScale=body;numericScale=numeric;displayScale=display;gearSize=gear;combinedGearSize=combined;
        }
    }
    public static final class Spacing {
        public final int buttonPadding,cardPadding,settingsPadding,pedalPadding,f1SidePadding,gearPadding,gridGapX,gridGapY;
        public Spacing(int button,int card,int settings,int pedal,int side,int gear,int gapX,int gapY){
            buttonPadding=button;cardPadding=card;settingsPadding=settings;pedalPadding=pedal;
            f1SidePadding=side;gearPadding=gear;gridGapX=gapX;gridGapY=gapY;
        }
    }
    public static final class Material {
        public final String style;
        public final int radius,topAlpha,bottomAlpha,highlight,shadow;
        public Material(String style,int radius,int top,int bottom,int highlight,int shadow){
            this.style=style;this.radius=radius;topAlpha=top;bottomAlpha=bottom;
            this.highlight=highlight;this.shadow=shadow;
        }
        Material withStyle(String value){return new Material(value,radius,topAlpha,bottomAlpha,highlight,shadow);}
        public boolean glass(){return "glass".equals(style);}
        public boolean legacy(){return "legacy".equals(style);}
    }
    public static final class Backdrop {
        public enum Mode { SOLID, GRADIENT, LEGACY }
        public final Mode mode;
        public final int endColor;
        public final Integer startColor;
        public final int endXPercent,scanlineColor;
        static final Backdrop SOLID=new Backdrop(Mode.SOLID,0);
        static final Backdrop LEGACY=new Backdrop(Mode.LEGACY,0);
        static final Backdrop LEGACY_GRADIENT=new Backdrop(Mode.GRADIENT,0xff030405,0xff111315,100,0x063f4948);
        Backdrop(Mode mode,int endColor){this(mode,endColor,null,65,mode==Mode.LEGACY?0x18314a50:0);}
        Backdrop(Mode mode,int endColor,Integer startColor,int endXPercent,int scanlineColor){
            this.mode=mode;this.endColor=endColor;this.startColor=startColor;
            this.endXPercent=endXPercent;this.scanlineColor=scanlineColor;
        }
        int startColor(int fallback){return colorOr(startColor,fallback);}
        boolean same(Backdrop other){
            return mode==other.mode&&endColor==other.endColor&&equal(startColor,other.startColor)&&
                endXPercent==other.endXPercent&&scanlineColor==other.scanlineColor;
        }
    }

    public static Typography defaultTypography(){return new Typography(100,100,100,184,130);}
    public static Spacing defaultSpacing(int columns){return new Spacing(12,10,24,14,4,0,columns==4?10:8,columns==4?12:6);}
    public static Material defaultMaterial(){return new Material("legacy",0,255,255,0xd9ffffff,0x0f2859a0);}

    /** Same role rule as ThemeResources.fontForText; measurement and drawing share this scale. */
    public float textSize(String text,float size,boolean bold){
        return size*("numeric".equals(textRole(text,bold))?typography.numericScale:bold?typography.displayScale:typography.bodyScale)/100f;
    }
    public float textSize(String role,String text,float size,boolean bold){
        if(role==null)return textSize(text,size,bold);
        return size*("numeric".equals(role)?typography.numericScale:"display".equals(role)?typography.displayScale:typography.bodyScale)/100f;
    }
    public static String textRole(String text,boolean bold){
        if(text!=null)for(int i=0;i<text.length();i++)if(text.charAt(i)>='0'&&text.charAt(i)<='9')return "numeric";
        return bold?"display":"body";
    }
    public final String id;
    public final String displayName;
    public final int background;
    public final int panel;
    public final int panelRaised;
    public final int stroke;
    public final int textPrimary;
    public final int textSecondary;
    public final int accent;
    public final int warning;
    public final int critical;
    public final int unavailable;
    public final int columns;
    public final int rows;
    public final int pageSize;
    public final boolean cutPanels;
    public final String bodyFont;
    public final String numericFont;
    public final String displayFont;
    public final String backgroundAsset;
    private java.util.List<Wallpaper> wallpapers=java.util.Collections.emptyList();
    private String defaultWallpaperId="";
    public java.util.List<Wallpaper> wallpapers(){return wallpapers;}
    public String defaultWallpaperId(){return defaultWallpaperId;}
    void wallpapers(java.util.List<Wallpaper> values,String defaultId){
        wallpapers=java.util.Collections.unmodifiableList(new java.util.ArrayList<Wallpaper>(values));
        defaultWallpaperId=defaultId==null?"":defaultId;
    }
    public static final class Wallpaper {
        public final String id,name,asset,thumbnail,owner;
        public final boolean isDefault;
        public final Integer color;
        public final Backdrop backdrop;
        Wallpaper(String id,String name,String asset,String thumbnail,String owner,boolean isDefault){
            this.id=id;this.name=name;this.asset=asset;this.thumbnail=thumbnail;this.owner=owner;this.isDefault=isDefault;
            color=null;backdrop=null;
        }
        Wallpaper(ThemeSpec theme){
            id=theme.id+":color";name=theme.displayName;asset="";thumbnail="";owner=theme.id;isDefault=true;
            color=Integer.valueOf(theme.background);backdrop=theme.backdrop;
        }
    }
    /** Bundle owning each resolved asset; inherited resources remain in the parent bundle. */
    String bodyFontOwner, numericFontOwner, displayFontOwner, backgroundAssetOwner;

    ThemeSpec(String id, String displayName, int background, int panel, int panelRaised,
              int stroke, int textPrimary, int textSecondary, int accent, int warning,
              int critical, int unavailable, int columns, int rows, boolean cutPanels,
              String bodyFont, String numericFont, String displayFont, String backgroundAsset,
              Typography typography,Spacing spacing,Material material,Backdrop backdrop) {
        if (id == null || displayName == null || columns < 1 || rows < 1) {
            throw new IllegalArgumentException("invalid theme definition");
        }
        this.typography=typography;this.spacing=spacing;this.material=material;this.backdrop=backdrop;
        this.id = id;
        this.displayName = displayName;
        this.background = background;
        this.panel = panel;
        this.panelRaised = panelRaised;
        this.stroke = stroke;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.accent = accent;
        this.warning = warning;
        this.critical = critical;
        this.unavailable = unavailable;
        this.columns = columns;
        this.rows = rows;
        this.pageSize = columns * rows;
        this.cutPanels = cutPanels;
        this.bodyFont=bodyFont;
        this.numericFont=numericFont;
        this.displayFont=displayFont;
        this.backgroundAsset=backgroundAsset;
        bodyFontOwner=id;numericFontOwner=id;displayFontOwner=id;backgroundAssetOwner=id;
    }

    public static ThemeSpec original() {
        return original(new Spacing(12,10,24,14,4,0,16,12));
    }

    static ThemeSpec originalV2() {
        return original(defaultSpacing(5));
    }

    private static ThemeSpec original(Spacing spacing) {
        ThemeSpec spec=new ThemeSpec("original", "Original", 0xff040506, 0xff101315,
                0xff233b46, 0xff303638, 0xffe1edf2, 0xff91a5ae, 0xffa4d9ef,
                0xffffb547, 0xffff4059, 0xff91a5ae, 5, 4, true,
                "", "", "", "",defaultTypography(),spacing,defaultMaterial(),Backdrop.LEGACY_GRADIENT);
        spec.palette(Palette.COMPONENT_DEFAULTS);
        spec.componentFills(LEGACY_FILLS);
        return spec;
    }

    public static ThemeSpec auroraFocus() {
        return new ThemeSpec("aurora_focus", "Aurora Focus", 0xff071118,
                0xff10232c, 0xff173641, 0xff28505a, 0xffedf8fa, 0xff9bb9c0,
                0xff65e0d0, 0xffffc45c, 0xffff6679, 0xff8299a0, 5, 4, false,
                "", "", "", "backgrounds/dashboard.png",defaultTypography(),defaultSpacing(5),defaultMaterial(),Backdrop.LEGACY);
    }

    static ThemeSpec fromManifest(String id,String displayName,int background,int panel,int panelRaised,
            int stroke,int textPrimary,int textSecondary,int accent,int warning,int critical,int unavailable,
            int columns,int rows,boolean cutPanels,String bodyFont,String numericFont,String displayFont,String backgroundAsset,
            Typography typography,Spacing spacing,Material material,Backdrop backdrop) {
        return new ThemeSpec(id,displayName,background,panel,panelRaised,stroke,textPrimary,textSecondary,
                accent,warning,critical,unavailable,columns,rows,cutPanels,bodyFont,numericFont,displayFont,backgroundAsset,typography,spacing,material,backdrop);
    }

    boolean isOriginal() { return "original".equals(id); }
}
