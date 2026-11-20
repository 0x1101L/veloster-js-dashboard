package com.veloster.dashboard.view;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.theme.ThemeSpec;

/** Stable preference IDs and header hit targets in the dashboard's 800x480 space. */
public final class DashboardPage {
    public static final int OVERVIEW=0,F1=1,GAUGES=2,GRAPHS=3,PERFORMANCE=4; // The retired diagnostics ID (5) falls back to Overview.
    public static final int SETTINGS=6,CLOCK=7,BACK=8;
    static final int FOOTER_HEIGHT=44;
    private static final int[] LEFT={144,250,328,418,18},WIDTH={100,72,84,110,120};
    private static final int TOP=0, ORIGINAL_TOP=8, BUTTON_HEIGHT=ThemeSpec.Shell.TOP_BUTTON_HEIGHT, BACK_HEIGHT=34, ICON_WIDTH=96;
    private static final int SETTINGS_LEFT=534, SETTINGS_WIDTH=68, MENU_RIGHT=800, ORIGINAL_MENU_RIGHT=782, CLOCK_GAP=12;
    private static final String[][] F1_ORDERS={{"speed","gear","rpm"},{"rpm","gear","speed"},{"gear","speed","rpm"},{"gear","rpm","speed"},{"speed","rpm","gear"},{"rpm","speed","gear"}};
    public static String f1OrderNode(int order,int slot){return F1_ORDERS[order][slot];}
    private DashboardPage(){}
    /** Page state is independent of Android views and persistent storage. */
    public static final class State {
        private int current=OVERVIEW;
        public int current(){return current;}
        public int restore(int value){current=normalize(value);return current;}
        public boolean is(int page){return current==page;}
    }
    public static int normalize(int page){return page>=OVERVIEW&&page<=PERFORMANCE?page:OVERVIEW;}
    public static int left(int page){return LEFT[normalize(page)];}
    public static int width(int page){return WIDTH[normalize(page)];}
    public static int hit(float x,float y){
        if(y<8||y>46)return -1;
        for(int i=0;i<LEFT.length;i++)if(x>=LEFT[i]&&x<=LEFT[i]+WIDTH[i])return i;
        return -1;
    }
    public static int tap(float downX,float downY,float x,float y){
        int page=hit(x,y);return page>=0&&page==hit(downX,downY)?page:-1;
    }
    private static final int[] MENU_ORDER={PERFORMANCE,OVERVIEW,F1,GAUGES,GRAPHS};
    private static final int[] MENU_BUTTONS={PERFORMANCE,OVERVIEW,F1,GAUGES,GRAPHS,SETTINGS,BACK};
    public static int menuPageAt(int index){return MENU_ORDER[index];}
    /** User preferences affect navigation visibility and labels; settings/clock/back stay accessible. */
    public static final class Menu {
        public static final int ALL=31;
        private int mask;
        private boolean icons;
        private boolean segmented;
        public Menu(){this(ALL);}
        public Menu(int value){mask=value>=0&&value<=ALL?value:ALL;}
        public int mask(){return mask;}
        public boolean icons(){return icons;}
        public void icons(boolean value){icons=value;}
        public boolean segmented(){return segmented;}
        public void segmented(boolean value){segmented=value;}
        public boolean visible(int page){return page>=0&&page<=PERFORMANCE?(mask&(1<<page))!=0:page==SETTINGS||page==CLOCK||page==BACK;}
        public void visible(int page,boolean value){if(page<0||page>PERFORMANCE)throw new IllegalArgumentException("menu page");if(value)mask|=1<<page;else mask&=~(1<<page);}
        public int count(){int n=0;for(int id:MENU_ORDER)if(visible(id))n++;return n;}
    }
    private static float topPageGroupWidth(Menu menu){
        float width=0;int count=0;
        for(int id:MENU_ORDER)if(menu.visible(id)){width+=menuWidth(menu,id);count++;}
        return width+Math.max(0,count-1)*6;
    }
    public static float menuLeft(Menu menu,int page){
        if(menu.segmented()){int index=menuIndex(menu,page),count=menu.count();return index<0||count==0?-1:index*topPageGroupWidth(menu)/count;}
        float x=0,itemWidth=equalMenuButtonWidth(menu);
        for(int id:MENU_ORDER){if(id==page)return menu.visible(id)?x:-1;if(menu.visible(id))x+=itemWidth+6;}return -1;
    }
    private static float topPageGroupWidth(ThemeSpec theme,Menu menu){
        float width=0;int count=0;
        for(int id:MENU_ORDER)if(menu.visible(id)){width+=menuWidth(menu,id);count++;}
        return width+Math.max(0,count-1)*theme.shell().topMenuItemGap;
    }
    private static float menuLeft(ThemeSpec theme,Menu menu,int page){
        if(menu.segmented()){
            int index=menuIndex(menu,page),count=menu.count();
            return index<0||count==0?-1:index*topPageGroupWidth(theme,menu)/count;
        }
        float x=0,itemWidth=equalMenuButtonWidth(theme,menu);
        for(int id:MENU_ORDER){if(id==page)return menu.visible(id)?x:-1;if(menu.visible(id))x+=itemWidth+theme.shell().topMenuItemGap;}return -1;
    }
    private static float equalMenuButtonWidth(Menu menu){
        int count=menu.count();return count==0?0:(topPageGroupWidth(menu)-Math.max(0,count-1)*6)/count;
    }
    private static float equalMenuButtonWidth(ThemeSpec theme,Menu menu){
        int count=menu.count();return count==0?0:(topPageGroupWidth(theme,menu)-Math.max(0,count-1)*theme.shell().topMenuItemGap)/count;
    }
    public static int menuIndex(Menu menu,int page){
        int index=0;for(int id:MENU_ORDER){if(id==page)return menu.visible(id)?index:-1;if(menu.visible(id))index++;}return -1;
    }
    private static int menuWidth(Menu menu,int page){return menu.icons()?ICON_WIDTH:width(page);}
    private static Bounds topBounds(Menu menu,int page){
        int backWidth=menu.icons()?ICON_WIDTH:100,backLeft=MENU_RIGHT-backWidth;
        if(page==BACK)return new Bounds(backLeft,TOP+(BUTTON_HEIGHT-BACK_HEIGHT)/2f,backWidth,BACK_HEIGHT);
        if(page==SETTINGS)return new Bounds(SETTINGS_LEFT,TOP,SETTINGS_WIDTH,BUTTON_HEIGHT);
        if(page==CLOCK){
            int left=SETTINGS_LEFT+SETTINGS_WIDTH+CLOCK_GAP;
            return new Bounds(left,TOP,backLeft-CLOCK_GAP-left,BUTTON_HEIGHT);
        }
        if(menu.segmented()&&menu.count()>0){
            int index=menuIndex(menu,page);float groupWidth=topPageGroupWidth(menu);
            float inset=Math.min(4,Math.min(groupWidth/menu.count(),BUTTON_HEIGHT/4f));
            float segment=(groupWidth-2*inset)/menu.count();
            return index<0?new Bounds(-1,TOP,0,0):new Bounds(inset+index*segment,TOP+inset,segment,BUTTON_HEIGHT-2*inset);
        }
        return new Bounds(menuLeft(menu,page),TOP,equalMenuButtonWidth(menu),BUTTON_HEIGHT);
    }
    private static Bounds topBounds(ThemeSpec theme,Menu menu,int page){
        if("original".equals(theme.id))return originalTopBounds(menu,page);
        int backWidth=menu.icons()?ICON_WIDTH:100,backLeft=MENU_RIGHT-backWidth;
        Bounds bounds;
        if(page==BACK)bounds=new Bounds(backLeft,TOP+(BUTTON_HEIGHT-BACK_HEIGHT)/2f,backWidth,BACK_HEIGHT);
        else if(page==SETTINGS)bounds=new Bounds(SETTINGS_LEFT,TOP,SETTINGS_WIDTH,BUTTON_HEIGHT);
        else if(page==CLOCK){int left=SETTINGS_LEFT+SETTINGS_WIDTH+CLOCK_GAP;bounds=new Bounds(left,TOP,backLeft-CLOCK_GAP-left,BUTTON_HEIGHT);}
        else if(menu.segmented()&&menu.count()>0){
            ThemeSpec.Insets p=theme.shell().topMenuPadding;
            float groupWidth=Math.max(0,topPageGroupWidth(theme,menu)-p.left-p.right);
            float groupHeight=Math.max(0,BUTTON_HEIGHT-p.top-p.bottom);
            float inset=Math.min(4,Math.min(groupWidth/menu.count(),groupHeight/4));
            float segment=(groupWidth-2*inset)/menu.count();int index=menuIndex(menu,page);
            bounds=index<0?new Bounds(-1,TOP,0,0):new Bounds(p.left+inset+index*segment,p.top+inset,segment,groupHeight-2*inset);
            return insetTopButton(bounds,theme.shell().topMenuButtonMargin);
        }else bounds=new Bounds(menuLeft(theme,menu,page),TOP,equalMenuButtonWidth(theme,menu),BUTTON_HEIGHT);
        ThemeSpec.Insets p=theme.shell().topMenuPadding;
        bounds=new Bounds(bounds.x+p.left,bounds.y+p.top,Math.max(0,bounds.width-p.left-p.right),Math.max(0,bounds.height-p.top-p.bottom));
        return page==CLOCK?bounds:insetTopButton(bounds,theme.shell().topMenuButtonMargin);
    }
    private static Bounds originalTopBounds(Menu menu,int page){
        int backWidth=menu.icons()?ICON_WIDTH:100,backLeft=ORIGINAL_MENU_RIGHT-backWidth;
        if(page==BACK)return new Bounds(backLeft,ORIGINAL_TOP+(BUTTON_HEIGHT-BACK_HEIGHT)/2f,backWidth,BACK_HEIGHT);
        if(page==SETTINGS)return new Bounds(SETTINGS_LEFT,ORIGINAL_TOP,SETTINGS_WIDTH,BUTTON_HEIGHT);
        if(page==CLOCK){int left=SETTINGS_LEFT+SETTINGS_WIDTH+CLOCK_GAP;return new Bounds(left,ORIGINAL_TOP,backLeft-CLOCK_GAP-left,BUTTON_HEIGHT);}
        float itemWidth=equalMenuButtonWidth(menu);
        if(menu.segmented()&&menu.count()>0){
            int index=menuIndex(menu,page);float groupWidth=topPageGroupWidth(menu);
            float inset=Math.min(4,Math.min(groupWidth/menu.count(),BUTTON_HEIGHT/4f));
            float segment=(groupWidth-2*inset)/menu.count();
            return index<0?new Bounds(-1,ORIGINAL_TOP,0,0):new Bounds(18+inset+index*segment,ORIGINAL_TOP+inset,segment,BUTTON_HEIGHT-2*inset);
        }
        float left=menuLeft(menu,page);
        return new Bounds(left<0?-1:18+left,ORIGINAL_TOP,itemWidth,BUTTON_HEIGHT);
    }
    private static Bounds insetTopButton(Bounds bounds,ThemeSpec.Insets margin){
        return new Bounds(bounds.x+margin.left,bounds.y+margin.top,
            Math.max(0,bounds.width-margin.left-margin.right),Math.max(0,bounds.height-margin.top-margin.bottom));
    }
    public static float topMenuContentX(ThemeSpec theme,Bounds bounds){
        ThemeSpec.Insets margin=theme.shell().topMenuButtonMargin;
        switch(theme.shell().topMenuContentAlignment){
            case "start": return bounds.x+Math.min(8,margin.left+4);
            case "end": return bounds.x+bounds.width-Math.min(8,margin.right+4);
            default: return bounds.x+bounds.width/2f;
        }
    }
    private static Bounds topBounds(Menu menu,int page,float layoutWidth,float padding){
        Bounds base=topBounds(menu,page);
        float factor=Math.max(0,layoutWidth-2*padding)/800f;
        return new Bounds(padding+base.x*factor,padding+base.y,base.width*factor,base.height);
    }
    private static Bounds topBounds(Menu menu,int page,float layoutWidth){
        return topBounds(menu,page,layoutWidth,0);
    }
    public static Bounds navigationBounds(ThemeSpec theme,Menu menu,int page){
        return navigationBounds(theme,menu,page,800);
    }
    public static Bounds navigationBounds(ThemeSpec theme,Menu menu,int page,float layoutWidth){
        if(theme.shell().left())return sidebarBounds(theme,menu,page);
        if("original".equals(theme.id)){
            Bounds base=topBounds(theme,menu,page);float factor=Math.max(0,layoutWidth)/800f;
            return new Bounds(base.x*factor,base.y,base.width*factor,base.height);
        }
        Bounds base=topBounds(theme,menu,page);float horizontal=theme.layoutSpacing().horizontalPadding,vertical=theme.layoutSpacing().verticalPadding;
        float factor=Math.max(0,layoutWidth-2*horizontal)/800f;
        return new Bounds(horizontal+base.x*factor,vertical+base.y,base.width*factor,base.height);
    }
    /** Shared track around the top page segments; settings, clock and back remain separate controls. */
    public static Bounds topPageMenuBounds(ThemeSpec theme,Menu menu,float layoutWidth){
        if("original".equals(theme.id)){
            float factor=Math.max(0,layoutWidth)/800f;
            return new Bounds(18*factor,ORIGINAL_TOP,topPageGroupWidth(menu)*factor,BUTTON_HEIGHT);
        }
        float horizontal=theme.layoutSpacing().horizontalPadding,vertical=theme.layoutSpacing().verticalPadding;
        float factor=Math.max(0,layoutWidth-2*horizontal)/800f;
        ThemeSpec.Insets p=theme.shell().topMenuPadding;
        return new Bounds(horizontal+p.left*factor,vertical+p.top,
                Math.max(0,topPageGroupWidth(theme,menu)-p.left-p.right)*factor,
                Math.max(0,ThemeSpec.Shell.TOP_BUTTON_HEIGHT-p.top-p.bottom));
    }
    public static int menuHit(Menu menu,float x,float y){
        if(y<TOP||y>=TOP+BUTTON_HEIGHT)return -1;
        for(int id:MENU_BUTTONS)if(menu.visible(id)&&topBounds(menu,id).contains(x,y))return id;
        return -1;
    }
    public static int menuHit(Menu menu,float x,float y,float layoutWidth){
        for(int id:MENU_BUTTONS)if(menu.visible(id)&&topBounds(menu,id,layoutWidth).contains(x,y))return id;
        return -1;
    }
    private static int menuHit(ThemeSpec theme,Menu menu,float x,float y,float layoutWidth){
        if("original".equals(theme.id)){
            for(int id:MENU_BUTTONS)if(menu.visible(id)&&navigationBounds(theme,menu,id,layoutWidth).contains(x,y))return id;
            return -1;
        }
        float horizontal=theme.layoutSpacing().horizontalPadding,vertical=theme.layoutSpacing().verticalPadding;
        float factor=Math.max(0,layoutWidth-2*horizontal)/800f;
        for(int id:MENU_BUTTONS)if(menu.visible(id)){
            Bounds base=topBounds(theme,menu,id);
            Bounds bounds=new Bounds(horizontal+base.x*factor,vertical+base.y,base.width*factor,base.height);
            if(bounds.contains(x,y))return id;
        }
        return -1;
    }
    public static int tap(Menu menu,float downX,float downY,float x,float y){int id=menuHit(menu,x,y);return id>=0&&id==menuHit(menu,downX,downY)?id:-1;}
    public static int tap(Menu menu,float downX,float downY,float x,float y,float layoutWidth){int id=menuHit(menu,x,y,layoutWidth);return id>=0&&id==menuHit(menu,downX,downY,layoutWidth)?id:-1;}
    public static int tap(ThemeSpec theme,Menu menu,float downX,float downY,float x,float y,float layoutWidth){
        int id=theme.shell().left()?shellHit(theme,menu,x,y):menuHit(theme,menu,x,y,layoutWidth);
        int down=theme.shell().left()?shellHit(theme,menu,downX,downY):menuHit(theme,menu,downX,downY,layoutWidth);
        return id>=0&&id==down?id:-1;
    }
    public static float shellLeft(ThemeSpec theme,int id){
        return theme.layoutSpacing().horizontalPadding+theme.shell().left+(id==CLOCK?0:(shellWidth(theme)-sidebarSide(theme))/2);
    }
    public static float shellTop(ThemeSpec theme,int id){return shellTop(theme,new Menu(),id);}
    public static float shellTop(ThemeSpec theme,Menu menu,int id){
        ThemeSpec.Shell s=theme.shell();
        float padding=theme.layoutSpacing().verticalPadding;
        if(id==CLOCK)return padding+s.top;
        float side=sidebarSide(theme);
        if(id==BACK)return 480-padding-s.bottom-side;
        if(!menu.visible(id))return -10000;
        int slot=0;for(int page:MENU_ORDER){if(page==id)break;if(menu.visible(page))slot++;}
        int count=menu.count()+1; // Keep the reserved back-button slot used by the established vertical rhythm.
        float groupGap=s.groupGap;
        float end=480-padding-s.bottom-side-groupGap,groupHeight=count*side+Math.max(0,count-1)*s.itemGap;
        float start=padding+s.top+s.buttonHeight+Math.max(groupGap,CLOCK_GAP);
        float free=end-start-groupHeight;
        float offset="center".equals(s.alignment)?free/2:"end".equals(s.alignment)?free:0;
        return start+offset+slot*(side+s.itemGap);
    }
    public static float shellWidth(ThemeSpec theme){ThemeSpec.Shell s=theme.shell();return s.width-s.left-s.right;}
    /** Full left-navigation background bounds; sideMenuBar padding affects contents only. */
    public static Bounds leftMenuBounds(ThemeSpec theme){
        ThemeSpec.Shell s=theme.shell();
        float horizontal=theme.layoutSpacing().horizontalPadding,vertical=theme.layoutSpacing().verticalPadding;
        return new Bounds(horizontal,vertical,s.width,Math.max(0,480-2*vertical));
    }
    private static float sidebarSide(ThemeSpec theme){
        ThemeSpec.Shell s=theme.shell();
        // Keep the same button size when pages are hidden and reserve room for the clock.
        int space=480-2*theme.layoutSpacing().verticalPadding-s.top-s.bottom-s.buttonHeight-Math.max(s.groupGap,CLOCK_GAP)-(MENU_BUTTONS.length-1)*s.itemGap;
        return Math.min(shellWidth(theme),space/MENU_BUTTONS.length);
    }
    private static Bounds sidebarBounds(ThemeSpec theme,Menu menu,int id){
        if(id==CLOCK)return new Bounds(theme.layoutSpacing().horizontalPadding+theme.shell().left,
            theme.layoutSpacing().verticalPadding+theme.shell().top,shellWidth(theme),theme.shell().buttonHeight);
        float side=sidebarSide(theme);
        return new Bounds(shellLeft(theme,id),shellTop(theme,menu,id),side,side);
    }
    public static int shellHit(ThemeSpec theme,float x,float y){return shellHit(theme,new Menu(),x,y);}
    public static int shellHit(ThemeSpec theme,Menu menu,float x,float y){
        for(int id:MENU_BUTTONS)if(menu.visible(id)&&sidebarBounds(theme,menu,id).contains(x,y))return id;
        return -1;
    }
    public static Regions regions(float width,float height){
        return regions(width,height,null);
    }
    public static Regions regions(float width,float height,ThemeSpec theme){
        return regions(width,height,theme,false);
    }
    public static Regions regions(float width,float height,ThemeSpec theme,boolean expandedMedia){
        int topHeight=theme==null?52:theme.shell().left()?0:"original".equals(theme.id)?52:ThemeSpec.Shell.TOP_BUTTON_HEIGHT;
        float scale=Math.min((width>=1800&&height>=700?width*5f/7f:width)/800f,height/480f);
        float horizontalGap=theme==null?0:theme.layoutSpacing().horizontalGap()*scale;
        float verticalGap=theme==null?0:theme.layoutSpacing().verticalGap()*scale;
        float paddingX=theme==null?0:theme.layoutSpacing().horizontalPadding*scale;
        float paddingY=theme==null?0:theme.layoutSpacing().verticalPadding*scale;
        boolean mediaEnabled=width>=1800&&height>=700;
        boolean mediaPanel=mediaEnabled||expandedMedia;
        float innerWidth=Math.max(0,width-2*paddingX);
        float contentWidth=width-paddingX;
        if(mediaEnabled)contentWidth=paddingX+(innerWidth-horizontalGap)*5f/7f;
        else if(expandedMedia){
            float fixedNavigation=theme!=null&&theme.shell().left()?theme.shell().width*scale:0;
            contentWidth=paddingX+fixedNavigation+(innerWidth-fixedNavigation-horizontalGap)*5f/7f;
        }
        return new Regions(width,height,contentWidth,mediaEnabled,mediaPanel,topHeight>0,scale,topHeight*scale,horizontalGap,verticalGap,paddingX,paddingY,theme);
    }
    public static float screenScale(float width,float height){return regions(width,height).scale;}
    public static float screenLeft(float width,float height){return regions(width,height).left;}
    public static float screenTop(float width,float height){return regions(width,height).top;}
    public static float screenX(float x,float width,float height){
        Regions r=regions(width,height);
        return (x-r.left)/r.scale;
    }
    public static float screenY(float y,float width,float height){return (y-regions(width,height).top)/regions(width,height).scale;}
    /** Three display regions: optional top navigation, page content, and an optional right media area. */
    public static final class Regions {
        public final float displayWidth,displayHeight,contentWidth,scale,left,top,headerHeight,logicalContentWidth,logicalDisplayWidth,logicalHeight,logicalOuterPadding,logicalOuterPaddingX,logicalOuterPaddingY;
        public final boolean mediaEnabled,mediaPanelVisible;
        public final Bounds topMenu,content,media;
        private Regions(float width,float height,float bodyWidth,boolean mediaEnabled,boolean mediaPanelVisible,boolean topVisible,float scale,float headerHeight,float horizontalGap,float verticalGap,float outerPaddingX,float outerPaddingY,ThemeSpec theme){
            displayWidth=width;displayHeight=height;contentWidth=bodyWidth;this.mediaEnabled=mediaEnabled;this.mediaPanelVisible=mediaPanelVisible;
            this.scale=scale;this.headerHeight=headerHeight;left=0;top=0;
            logicalContentWidth=bodyWidth/scale;logicalDisplayWidth=width/scale;logicalHeight=(height-outerPaddingY)/scale;logicalOuterPadding=outerPaddingY/scale;logicalOuterPaddingX=outerPaddingX/scale;logicalOuterPaddingY=outerPaddingY/scale;
            float bodyTop=(theme!=null&&"original".equals(theme.id)?0:outerPaddingY)+headerHeight+(topVisible?verticalGap:0);
            ThemeSpec.Insets cp=theme==null?new ThemeSpec.Insets(0,0,0,0):theme.shell().contentPadding;
            ThemeSpec.Insets mp=theme==null?new ThemeSpec.Insets(0,0,0,0):theme.shell().mediaPadding;
            float cl=cp.left*scale,cr=cp.right*scale,ct=cp.top*scale,cb=cp.bottom*scale;
            content=new Bounds(outerPaddingX+cl,bodyTop+ct,Math.max(0,bodyWidth-outerPaddingX-cl-cr),Math.max(0,height-outerPaddingY-bodyTop-ct-cb));
            float mx=bodyWidth+horizontalGap,my=bodyTop,mw=Math.max(0,width-outerPaddingX-bodyWidth-horizontalGap),mh=Math.max(0,height-outerPaddingY-bodyTop);
            media=mediaPanelVisible?new Bounds(mx+mp.left*scale,my+mp.top*scale,Math.max(0,mw-(mp.left+mp.right)*scale),Math.max(0,mh-(mp.top+mp.bottom)*scale)):new Bounds(width,my,0,mh);
            topMenu=topVisible?theme!=null&&"original".equals(theme.id)?new Bounds(0,0,width,headerHeight):new Bounds(outerPaddingX,outerPaddingY,Math.max(0,width-2*outerPaddingX),headerHeight):new Bounds(width,outerPaddingY,0,0);
        }
    }
    /** The actual page rectangle after navigation and theme viewport insets. */
    public static final class Content {
        public final float x,y,width,height;
        public final ThemeSpec.F1Grid f1Grid;
        public final float bodyViewportHeight;
        private final float pageBottom;
        private final boolean f1Page;
        private final boolean defaultF1;
        private final int f1Side,f1GapX,f1GapY,f1Padding;
        private final float horizontalGap,verticalGap;
        private final ThemeSpec.Insets footerPadding;
        private final String[] f1Slots={"speed","gear","rpm"};
        public Content(ThemeSpec theme,String page){
            this(theme,page,800,480);
        }
        public Content(ThemeSpec theme,String page,float viewportWidth,float viewportHeight){
            this(theme,page,viewportWidth,viewportHeight,false);
        }
        public Content(ThemeSpec theme,String page,float viewportWidth,float viewportHeight,boolean footer){
            this(theme,page,viewportWidth,viewportHeight,footer,false);
        }
        public Content(ThemeSpec theme,String page,Regions regions,boolean footer){
            this(theme,page,regions.logicalContentWidth+regions.logicalOuterPaddingX,
                regions.logicalHeight+regions.logicalOuterPaddingY,footer,regions.mediaPanelVisible);
        }
        public Content(ThemeSpec theme,String page,float viewportWidth,float viewportHeight,boolean footer,boolean sideMedia){
            ThemeSpec.Viewport v=theme.viewport(page);
            horizontalGap=theme.layoutSpacing().horizontalGap();
            verticalGap=theme.layoutSpacing().verticalGap();
            f1Page="f1".equals(page);
            f1Grid=f1Page?theme.f1Grid():null;
            defaultF1=f1Page&&f1Grid==null&&!theme.hasF1();
            f1Side=0;
            f1GapX=f1Grid==null?theme.layoutSpacing().gap:f1Grid.gap;
            f1GapY=f1Grid==null?theme.layoutSpacing().gap:f1Grid.gap;
            f1Padding=0;
            footerPadding=theme.shell().bottomMediaPadding;
            for(int i=0;i<3;i++)for(int j=i+1;j<3;j++)if(f1SlotX(theme,f1Slots[i])>f1SlotX(theme,f1Slots[j])){String swap=f1Slots[i];f1Slots[i]=f1Slots[j];f1Slots[j]=swap;}
            float outerPaddingX=theme.layoutSpacing().horizontalPadding,outerPaddingY=theme.layoutSpacing().verticalPadding;
            ThemeSpec.Insets area=theme.shell().contentPadding;
            x=outerPaddingX+(theme.shell().left()?theme.shell().width+horizontalGap:0)+(v==null?0:v.left)+area.left;
            y=outerPaddingY+(theme.shell().left()?0:theme.shell().topMenuHeight()+verticalGap)+(v==null?0:v.top)+area.top;
            pageBottom=viewportHeight-outerPaddingY-(v==null?0:v.bottom)-area.bottom;
            float reserve=footer?FOOTER_HEIGHT+verticalGap:0;
            bodyViewportHeight=Math.max(0,pageBottom-reserve);
            width=Math.max(0,viewportWidth-outerPaddingX-x-(v==null?0:v.right)-area.right);height=Math.max(0,pageBottom-y-reserve);
        }
        public boolean contains(float px,float py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
        public Bounds bounds(){return new Bounds(x,y,width,height);}
        public Bounds padded(ThemeSpec theme){return bounds();}
        public Bounds notice(ThemeSpec theme){
            Bounds b=padded(theme);
            float h=Math.min(b.height,76);
            return new Bounds(b.x,b.y+b.height-h,b.width,h);
        }
        private float f1SlotX(ThemeSpec theme,String id){
            if(f1Grid!=null)return f1Grid.cell(id).column;
            ThemeSpec.Rect r=theme.f1(id);
            return r!=null?r.x:"speed".equals(id)?18:"gear".equals(id)?294:522;
        }
        public Bounds f1(ThemeSpec theme,String id){
            if("speed".equals(id)||"gear".equals(id)||"rpm".equals(id)){
                for(int i=0;i<3;i++)if(f1OrderNode(theme.f1Order(),i).equals(id))return f1Raw(theme,f1Slots[i]);
            }
            return f1Raw(theme,id);
        }
        private Bounds f1Raw(ThemeSpec theme,String id){
            if(f1Grid!=null){
                ThemeSpec.Card cell=f1Grid.cell(id);Bounds grid=f1Frame();
                float gap=f1Grid.gap;
                float cw=(grid.width-(f1Grid.columns-1)*gap)/f1Grid.columns;
                float rh=(grid.height-(f1Grid.rows-1)*gap)/f1Grid.rowWeight(0,f1Grid.rows);
                return new Bounds(grid.x+cell.column*(cw+gap),grid.y+cell.row*gap+rh*f1Grid.rowWeight(0,cell.row),
                    cell.columnSpan*cw+(cell.columnSpan-1)*gap,
                    rh*f1Grid.rowWeight(cell.row,cell.row+cell.rowSpan)+(cell.rowSpan-1)*gap);
            }
            if(defaultF1)return defaultF1Cell(id);
            ThemeSpec.Rect r=theme.f1(id);
            Bounds b=f1Frame();
            if(r==null)throw new IllegalArgumentException("F1 component");
            // Only the container edges use proportional coordinates. Glyphs and circles never stretch.
            return new Bounds(b.atX(r.x/800f),b.atY((r.y-52f)/(theme.f1Bottom()-52)),r.width*b.width/800f,r.height*b.height/(theme.f1Bottom()-52));
        }
        private Bounds f1Frame(){
            float side=f1Side;
            float vertical=0;
            float gapX=f1Grid==null?f1GapX:f1Grid.gap;
            float left=side,innerWidth=width-left-side;
            return new Bounds(x+left,y+vertical,innerWidth,height-2*vertical);
        }
        private Bounds defaultF1Cell(String id){
            Bounds frame=f1Frame();
            float unit=(frame.height-3*f1GapY)/(48+158+89+57);
            float shift=unit*48,main=unit*158,detail=unit*89,bottom=unit*57;
            float mainY=frame.y+shift+f1GapY,detailY=mainY+main+f1GapY;
            float column=(frame.width-2*f1GapX)/3f,half=(frame.width-f1GapX)/2f;
            if("shift".equals(id))return new Bounds(frame.x,frame.y,frame.width,shift);
            if("speed".equals(id))return new Bounds(frame.x,mainY,column,main);
            if("gear".equals(id))return new Bounds(frame.x+column+f1GapX,mainY,column,main+f1GapY+detail);
            if("rpm".equals(id))return new Bounds(frame.x+2*(column+f1GapX),mainY,column,main);
            if("boost".equals(id))return new Bounds(frame.x,detailY,column,detail);
            if("temperatures".equals(id))return new Bounds(frame.x+2*(column+f1GapX),detailY,column,detail);
            if("pedal".equals(id)||"brake".equals(id))
                return new Bounds(frame.x+("brake".equals(id)?half+f1GapX:0),detailY+detail+f1GapY,half,bottom);
            throw new IllegalArgumentException("F1 component");
        }
        public Bounds footer(int action){
            float inset=f1Padding;
            float h=FOOTER_HEIGHT;
            float l=x+inset,w=Math.max(0,width-2*inset),t=pageBottom-h-inset;
            if(action==-10)return new Bounds(l,t,w,h);
            l+=footerPadding.left;w=Math.max(0,w-footerPadding.left-footerPadding.right);
            t+=footerPadding.top;h=Math.max(0,h-footerPadding.top-footerPadding.bottom);
            if(action==2)return new Bounds(l,t,48,h);
            float bw=Math.min(82,w*0.16f);
            int slot=action==-1?0:action==0?1:2;
            return new Bounds(l+w-3*bw+slot*bw,t,bw,h);
        }
    }
    public static final class Bounds {
        public final float x,y,width,height;
        public Bounds(float x,float y,float width,float height){this.x=x;this.y=y;this.width=width;this.height=height;}
        public boolean contains(float px,float py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
        public float right(){return x+width;}
        public float bottom(){return y+height;}
        public float atX(float fraction){return x+width*fraction;}
        public float atY(float fraction){return y+height*fraction;}
        public Bounds inset(float pad){
            float dx=Math.min(pad,width/2),dy=Math.min(pad,height/2);
            return new Bounds(x+dx,y+dy,width-2*dx,height-2*dy);
        }
    }
}
