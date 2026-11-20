package com.veloster.dashboard.view;

import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.view.DashboardPage.Bounds;
import com.veloster.dashboard.theme.ThemeSpec;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import com.veloster.i18n.UiText;

/** Settings and selection pages share the same grouped rows and content geometry. */
public final class SettingsPageRenderer {
    private ThemeSpec theme=ThemeSpec.original();
    private final com.veloster.dashboard.theme.ThemeSurface surface;
    private final com.veloster.dashboard.theme.ThemeSurface wallpaperSurface=new com.veloster.dashboard.theme.ThemeSurface();
    public SettingsPageRenderer(){this(new com.veloster.dashboard.theme.ThemeSurface());}
    SettingsPageRenderer(com.veloster.dashboard.theme.ThemeSurface surface){this.surface=surface;}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    static final int LANGUAGE=0,THEME=1,SHIFT_TARGET=2,MEDIA_BAR=3,PAGE_MENU=4,MENU_POSITION=5,VEHICLE_SETTINGS=40;
    static final int PEDAL_BAR=6,BRAKE_BAR=7,F1_ORDER=8,UI_GROUP=9,F1_GROUP=10,MENU_STYLE=12,MEDIA_NOTICES=13,MEDIA_BUTTONS=29,MEDIA_BUTTON_STYLE=30,PAGE_GROUP=31,THEME_SETTINGS=32,MENU_SHAPE=33;
    static final int OVERVIEW_GROUP=36,OVERVIEW_SIGNALS=37,OVERVIEW_ORDER=38,OVERVIEW_RESET=39;
    static final int WALLPAPER=14,DIAGNOSTICS=15,MENU_DOCK=16;
    static final int LIQUID_GLASS=17,LIQUID_GLASS_TRANSPARENCY=18,LIQUID_GLASS_BLUR=19,LIQUID_GLASS_HIGHLIGHT=27;
    static final int ACCENT_PALETTE=28;
    static final int WALLPAPER_COLUMNS=3;
    public static final int GAUGE_MENU=20,GAUGE_SIGNAL=21,GAUGE_STYLE=22,GAUGE_SWAP=23,GAUGE_RESET=24;
    public static final int GRAPH_SIGNAL=25,TRANSMISSION=26;
    private static final int TITLE_TEXT_SIZE=22,ROW_TEXT_SIZE=18,HEADER_ACTION_TEXT_SIZE=16;
    private static final int ITEM_HORIZONTAL_INSET=12;
    private static final int HEADER_ACTION_INSET=12;
    private static final int GROUP_INSET=0;
    private static boolean choicePage(int screen){return screen>=0&&screen<=8||screen==MENU_STYLE||screen==MENU_SHAPE||screen==MEDIA_NOTICES||screen==MEDIA_BUTTONS||screen==MEDIA_BUTTON_STYLE||screen==OVERVIEW_SIGNALS||screen==MENU_DOCK||screen==LIQUID_GLASS_TRANSPARENCY||screen==LIQUID_GLASS_BLUR||screen==LIQUID_GLASS_HIGHLIGHT||screen==ACCENT_PALETTE||screen>=GAUGE_SIGNAL&&screen<=GAUGE_SWAP||screen==GRAPH_SIGNAL||screen==TRANSMISSION;}
    private static boolean applyPage(int screen){return choicePage(screen)||screen==GAUGE_RESET;}
    public static final class Editor {
        public interface Preview {void draw(Canvas canvas,int width,int height);void tap(float x,float y,int width,int height);}
        int screen=-1,parent=-1,selected;boolean[] checked;
        float pageScrollY=Float.NaN;
        Preview preview;
        ThemeSpec.Wallpaper[] wallpapers=new ThemeSpec.Wallpaper[0];
        Bitmap[] wallpaperThumbnails=new Bitmap[0];
        boolean[] wallpaperThumbnailTried=new boolean[0];
        int[] swatches;
        String title="",message="";String[] labels=new String[0],values=new String[0];int[] actions=new int[0];
        int[] sectionStarts={0};String[] sectionTitles={""};
        public void page(int id,int parent,String title,String[] labels,String[] values,int[] actions){
            this.screen=id;this.parent=parent;this.title=title;this.labels=labels;this.values=values;this.actions=actions;pageScrollY=Float.NaN;checked=null;message="";preview=null;
            sectionStarts=new int[]{0};sectionTitles=new String[]{""};
            wallpapers=new ThemeSpec.Wallpaper[0];wallpaperThumbnails=new Bitmap[0];wallpaperThumbnailTried=new boolean[0];swatches=null;
        }
        void sections(int[] starts,String[] titles){sectionStarts=starts.clone();sectionTitles=titles.clone();}
        void root(String language,boolean js){
            if(js)page(-1,-1,UiText.t("설정"),UiText.list(new String[]{"UI 설정","페이지 설정","차량 설정","언어","진단"}),
                new String[]{"","","",language,""},new int[]{UI_GROUP,PAGE_GROUP,VEHICLE_SETTINGS,LANGUAGE,DIAGNOSTICS});
            else page(-1,-1,UiText.t("설정"),UiText.list(new String[]{"UI 설정","페이지 설정","언어"}),
                new String[]{"","",language},new int[]{UI_GROUP,PAGE_GROUP,LANGUAGE});
        }
        void wallpaperPage(ThemeSpec.Wallpaper[] items,int selected,int parent){
            page(WALLPAPER,parent,UiText.t("배경화면"),new String[items.length],new String[0],new int[0]);
            wallpapers=items;this.selected=selected;
            wallpaperThumbnails=new Bitmap[items.length];wallpaperThumbnailTried=new boolean[items.length];
            for(int i=0;i<items.length;i++)labels[i]=items[i].name;
        }
        void swatchPage(String title,String[] labels,int[] colors,int selected,int parent){
            page(ACCENT_PALETTE,parent,title,labels,new String[0],new int[0]);
            this.selected=selected;swatches=colors.clone();
        }
    }
    /** One page-level scroll viewport containing one or more independent list groups. */
    public static final class Layout {
        final Bounds area,back,apply,previewButton,preview,viewport;
        final int gap,offset,count,rowPad,sectionCount,columns;
        final float tileGap,tileWidth,tileHeight;
        final float rowHeight,firstRowY,pageScrollY,maxPageScroll,contentHeight;
        final float[] rowTops,sectionHeaderTops,sectionBodyTops,sectionBottoms;
        final boolean wallpaperGrid;
        public Layout(ThemeSpec theme,DashboardPage.Content content,Editor editor){
            area=content.padded(theme).inset(GROUP_INSET);
            gap=theme.layoutSpacing().gap;
            rowPad=ITEM_HORIZONTAL_INSET;
            float header=40;
            float button=Math.min(area.width*.23f,96+2*HEADER_ACTION_INSET);
            back=new Bounds(area.x,area.y,button,header);
            apply=new Bounds(area.right()-button,area.y,button,header);
            previewButton=new Bounds(apply.x-button-theme.layoutSpacing().gap,area.y,button,header);
            float top=back.bottom()+gap+(editor.screen==F1_ORDER||editor.screen==OVERVIEW_ORDER||editor.preview!=null?24:0);
            float message=editor.message.length()>0?24:0;
            viewport=new Bounds(area.x,top,area.width,Math.max(0,area.bottom()-top-message));
            preview=new Bounds(area.x,top,(int)area.width,(int)Math.max(0,area.bottom()-top));
            wallpaperGrid=editor.screen==WALLPAPER;
            columns=wallpaperGrid?WALLPAPER_COLUMNS:1;
            sectionCount=wallpaperGrid||editor.sectionStarts.length==0?1:editor.sectionStarts.length;
            rowTops=new float[editor.labels.length];
            sectionHeaderTops=new float[sectionCount];sectionBodyTops=new float[sectionCount];sectionBottoms=new float[sectionCount];
            int rows=(editor.labels.length+columns-1)/columns;
            if(wallpaperGrid){
                tileGap=Math.max(8,Math.min(16,gap));
                tileWidth=Math.max(1,(viewport.width-(columns-1)*tileGap)/columns);
                tileHeight=26+Math.max(1,Math.min(tileWidth*.56f,128));
                rowHeight=tileHeight+tileGap;
                sectionHeaderTops[0]=0;sectionBodyTops[0]=0;
                for(int i=0;i<rows;i++)rowTops[i]=i*rowHeight;
                sectionBottoms[0]=Math.max(0,rows*rowHeight-tileGap);
                contentHeight=sectionBottoms[0];
            }else{
                tileGap=tileWidth=tileHeight=0;
                rowHeight=44;
                float cursor=0;
                for(int s=0;s<sectionCount;s++){
                    int start=editor.sectionStarts.length==0?0:editor.sectionStarts[s];
                    int end=s+1<sectionCount?editor.sectionStarts[s+1]:editor.labels.length;
                    if(s>0)cursor+=gap;
                    sectionHeaderTops[s]=cursor;
                    boolean hasTitle=editor.sectionTitles.length>s&&editor.sectionTitles[s].length()>0;
                    if(hasTitle)cursor+=28;
                    sectionBodyTops[s]=cursor;
                    for(int i=start;i<end&&i<rowTops.length;i++)rowTops[i]=cursor+(i-start)*rowHeight;
                    cursor+=(end-start)*rowHeight;
                    sectionBottoms[s]=cursor;
                }
                contentHeight=cursor;
            }
            maxPageScroll=viewport.height>0?Math.max(0,contentHeight-viewport.height):0;
            float selectedTop=editor.selected>=0&&editor.selected<editor.labels.length
                    ?(wallpaperGrid?(editor.selected/columns)*rowHeight:rowTops[editor.selected]):0;
            float initial=(wallpaperGrid||choicePage(editor.screen))&&editor.selected>=0
                    ?selectedTop+rowHeight*.5f-viewport.height*.5f:0;
            pageScrollY=clampScroll(Float.isNaN(editor.pageScrollY)?initial:editor.pageScrollY);
            int firstRow=wallpaperGrid?(int)(pageScrollY/rowHeight):0;
            offset=firstRow*columns;
            firstRowY=viewport.y+(wallpaperGrid?firstRow*rowHeight:0)-pageScrollY;
            int visibleRows=(int)Math.ceil((pageScrollY+viewport.height)/rowHeight)-firstRow;
            count=wallpaperGrid?Math.max(0,Math.min(editor.labels.length-offset,visibleRows*columns)):editor.labels.length;
        }
        public int hit(Editor editor,float x,float y){
            if(back.contains(x,y))return -2;
            if(editor.screen==WALLPAPER&&apply.contains(x,y))return -8;
            if(applyPage(editor.screen)&&apply.contains(x,y))return -3;
            if(editor.screen==THEME&&previewButton.contains(x,y))return -6;
            if(editor.preview!=null)return preview.contains(x,y)?-7:-1;
            if(!viewport.contains(x,y))return -1;
            float pageY=y-viewport.y+pageScrollY;
            if(editor.screen==WALLPAPER){
                int gridRow=(int)(pageY/rowHeight),column=(int)((x-viewport.x)/(tileWidth+tileGap));
                int index=gridRow*columns+column;
                return column>=0&&column<columns&&index<editor.labels.length&&wallpaperTile(index).contains(x,y)?index:-1;
            }
            for(int i=0;i<rowTops.length;i++)if(pageY>=rowTops[i]&&pageY<rowTops[i]+rowHeight)return i;
            return -1;
        }
        Bounds wallpaperTile(int index){
            int column=index%WALLPAPER_COLUMNS,row=index/WALLPAPER_COLUMNS;
            return new Bounds(viewport.x+column*(tileWidth+tileGap),viewport.y+row*rowHeight-pageScrollY,tileWidth,tileHeight);
        }
        Bounds rowBounds(int index){return new Bounds(viewport.x,viewport.y+rowTops[index]-pageScrollY,viewport.width,rowHeight);}
        private float clampScroll(float position){return Math.max(0,Math.min(maxPageScroll,position));}
        boolean scrollPageBy(Editor editor,float delta){
            float next=clampScroll(pageScrollY+delta);
            if(next==pageScrollY)return false;
            editor.pageScrollY=next;
            return true;
        }
        public void tapPreview(Editor editor,float x,float y){
            editor.preview.tap(x-preview.x,y-preview.y,(int)preview.width,(int)preview.height);
        }
    }
    public void draw(Canvas c,ThemeSpec theme,ThemeResources resources,Editor editor,DashboardPage.Content content){
        this.theme=theme;
        Layout l=new Layout(theme,content,editor);
        float pad=HEADER_ACTION_INSET;
        String backLabel="‹ "+UiText.t("돌아가기");
        float backBaseline=centeredBaseline(l.back,resources,backLabel,HEADER_ACTION_TEXT_SIZE,true);
        text(c,resources,backLabel,l.back.x+pad,backBaseline,HEADER_ACTION_TEXT_SIZE,theme.accent,true);
        float titleLeft=l.back.right()+theme.layoutSpacing().gap;
        float titleRight=editor.screen==THEME?l.previewButton.x:
            (applyPage(editor.screen)||editor.screen==WALLPAPER)?l.apply.x:l.area.right();
        float titleBaseline=centeredBaseline(l.back,resources,editor.title,TITLE_TEXT_SIZE,true);
        fitted(c,theme,resources,editor.title,titleLeft,titleBaseline,TITLE_TEXT_SIZE,Math.max(1,titleRight-titleLeft),theme.textPrimary,true);
        if(applyPage(editor.screen)){
            String applyLabel=UiText.t(editor.screen==GAUGE_RESET?"복원":"적용");
            text(c,resources,applyLabel,l.apply.x+pad,centeredBaseline(l.apply,resources,applyLabel,ROW_TEXT_SIZE,true),ROW_TEXT_SIZE,theme.accent,true);
        }
        if(editor.screen==WALLPAPER){String label=UiText.t("테마 기본값");text(c,resources,label,l.apply.x+pad,centeredBaseline(l.apply,resources,label,HEADER_ACTION_TEXT_SIZE,true),HEADER_ACTION_TEXT_SIZE,theme.accent,true);}
        if(editor.screen==THEME){String label=UiText.t("미리보기");text(c,resources,label,l.previewButton.x+pad,centeredBaseline(l.previewButton,resources,label,HEADER_ACTION_TEXT_SIZE,true),HEADER_ACTION_TEXT_SIZE,theme.accent,true);}
        if(editor.preview!=null){
            text(c,resources,UiText.t("예시 데이터"),l.preview.x+pad,l.preview.y-6,14,theme.textSecondary,false);
            c.save();c.clipRect(l.preview.x,l.preview.y,l.preview.right(),l.preview.bottom());
            c.translate(l.preview.x,l.preview.y);editor.preview.draw(c,(int)l.preview.width,(int)l.preview.height);c.restore();
            return;
        }
        if(editor.screen==WALLPAPER){
            c.save();c.clipRect(l.viewport.x,l.viewport.y,l.viewport.right(),l.viewport.bottom());
            drawWallpapers(c,resources,editor,l);c.restore();
            return;
        }
        if(editor.screen==F1_ORDER)text(c,resources,UiText.t("왼쪽 → 오른쪽"),l.viewport.x+l.rowPad,l.viewport.y-6,14,theme.textSecondary,false);
        if(editor.screen==OVERVIEW_ORDER)text(c,resources,UiText.t("카드를 누르면 한 칸 위로 이동"),l.viewport.x+l.rowPad,l.viewport.y-6,14,theme.textSecondary,false);
        c.save();c.clipRect(l.viewport.x,l.viewport.y,l.viewport.right(),l.viewport.bottom());
        for(int section=0;section<l.sectionCount;section++){
            int start=editor.sectionStarts.length==0?0:editor.sectionStarts[section];
            int end=section+1<l.sectionCount?editor.sectionStarts[section+1]:editor.labels.length;
            float headerTop=l.viewport.y+l.sectionHeaderTops[section]-l.pageScrollY;
            float bodyTop=l.viewport.y+l.sectionBodyTops[section]-l.pageScrollY;
            float bodyBottom=l.viewport.y+l.sectionBottoms[section]-l.pageScrollY;
            if(editor.sectionTitles.length>section&&editor.sectionTitles[section].length()>0)
                text(c,resources,UiText.t(editor.sectionTitles[section]),l.viewport.x+l.rowPad,headerTop+19,15,theme.textSecondary,true);
            if(end>start)surface.settingsGroup(c,theme,l.viewport.x,bodyTop,l.viewport.width,bodyBottom-bodyTop);
            for(int index=start;index<end;index++){
                float top=l.viewport.y+l.rowTops[index]-l.pageScrollY;
                if(index>start){paint.setColor(theme.stroke);paint.setStrokeWidth(1);c.drawLine(l.viewport.x+l.rowPad,top,l.viewport.right()-l.rowPad,top,paint);}
            String value=editor.values.length>index?editor.values[index]:"";
            if(choicePage(editor.screen))value=editor.checked!=null?(editor.checked[index]?"✓":""):(editor.selected==index?"✓":"");
            boolean arrowSlot=!choicePage(editor.screen)&&editor.screen!=GAUGE_RESET||editor.screen==VEHICLE_SETTINGS;
            boolean showChevron=arrowSlot&&(editor.screen!=VEHICLE_SETTINGS||editor.actions.length<=index||editor.actions[index]!=0);
            row(c,theme,resources,editor.labels[index],value,l,top,showChevron,arrowSlot);
            if(editor.swatches!=null&&index<editor.swatches.length){
                float cx=l.viewport.right()-l.rowPad-16,cy=top+l.rowHeight*.5f,radius=Math.min(12,l.rowHeight*.25f);
                paint.setColor(editor.swatches[index]);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,radius,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(index==editor.selected?3:1);
                paint.setColor(index==editor.selected?theme.accent:theme.stroke);c.drawCircle(cx,cy,radius+2,paint);paint.setStyle(Paint.Style.FILL);
            }
            }
        }
        c.restore();
        if(editor.message.length()>0)fitted(c,theme,resources,editor.message,l.viewport.x+pad,l.viewport.bottom()+19,16,l.viewport.width-2*pad,theme.critical,false);
    }
    private void drawWallpapers(Canvas c,ThemeResources resources,Editor editor,Layout layout){
        for(int i=0;i<layout.count;i++){
            int index=layout.offset+i;
            Bounds tile=layout.wallpaperTile(index);
            Bitmap thumbnail=editor.wallpaperThumbnails[index];
            float imageHeight=tile.height-26;
            if(thumbnail!=null&&imageHeight>0){
                float scale=Math.max(tile.width/thumbnail.getWidth(),imageHeight/thumbnail.getHeight());
                float dx=(tile.width-thumbnail.getWidth()*scale)/2,dy=(imageHeight-thumbnail.getHeight()*scale)/2;
                c.save();
                surface.clipRoundRect(c,theme,new android.graphics.RectF(tile.x,tile.y,tile.right(),tile.y+imageHeight),8);
                paint.setColor(0xffffffff);
                c.drawBitmap(thumbnail,null,new android.graphics.RectF(tile.x+dx,tile.y+dy,tile.right()-dx,tile.y+imageHeight-dy),paint);
                c.restore();
            }
            else if(editor.wallpapers[index].color!=null&&imageHeight>0){
                c.save();
                surface.clipRoundRect(c,theme,new android.graphics.RectF(tile.x,tile.y,tile.right(),tile.y+imageHeight),8);
                c.translate(tile.x,tile.y);
                wallpaperSurface.wallpaperPreview(c,editor.wallpapers[index],Math.max(1,(int)tile.width),Math.max(1,(int)imageHeight));
                c.restore();
            }else{
                paint.setColor(theme.panelRaised);
                surface.roundRect(c,theme,new android.graphics.RectF(tile.x,tile.y,tile.right(),tile.y+tile.height-26),8,8,paint);
            }
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(index==editor.selected?3:1);
            paint.setColor(index==editor.selected?theme.accent:theme.stroke);
            surface.roundRect(c,theme,new android.graphics.RectF(tile.x,tile.y,tile.right(),tile.y+tile.height-26),8,8,paint);
            paint.setStyle(Paint.Style.FILL);
            fitted(c,theme,resources,UiText.t(editor.labels[index]),tile.x+4,tile.y+tile.height-5,15,tile.width-8,theme.textPrimary,index==editor.selected);
            if(index==editor.selected){
                float x=tile.right()-22,y=tile.y+6;
                paint.setColor(theme.accent);c.drawCircle(x,y,11,paint);
                text(c,resources,"✓",x-6,y+6,14,0xffffffff,true);
            }
        }
    }
    public static String menuPositionLabel(int position){
        return UiText.t(position==ThemeSpec.MENU_TOP?"상단":position==ThemeSpec.MENU_LEFT?"왼쪽":"테마 기본값");
    }

    public void draw(Canvas canvas,ThemeSpec theme,ThemeResources resources,String language,boolean js,DashboardPage.Content content){
        Editor editor=new Editor();
        editor.root(language,js);
        draw(canvas,theme,resources,editor,content);
    }

    private void row(Canvas c,ThemeSpec t,ThemeResources r,String label,String value,Layout l,float top,boolean chevron,boolean reserveChevron){
        float x=l.viewport.x+l.rowPad,y=top+l.rowHeight*.65f,right=l.viewport.right()-l.rowPad;
        float split=l.viewport.atX(.54f);
        fitted(c,t,r,label,x,y,ROW_TEXT_SIZE,Math.max(1,split-x),t.textPrimary,false);
        float size=t.textSize(value,ROW_TEXT_SIZE,false);
        paint.setTypeface(r==null?Typeface.DEFAULT:r.fontForText(value,false));paint.setTextSize(size);
        float max=Math.max(1,right-split-(reserveChevron?20:0));
        float w=paint.measureText(value);
        if(w>max){size*=max/w;paint.setTextSize(size);w=paint.measureText(value);}
        paint.setColor(t.textSecondary);c.drawText(value,right-(reserveChevron?20:0)-w,y,paint);
        if(chevron){float mid=top+l.rowHeight*.5f;paint.setStrokeWidth(2);c.drawLine(right-7,mid-5,right,mid,paint);c.drawLine(right,mid,right-7,mid+5,paint);}
    }
    private void fitted(Canvas c,ThemeSpec t,ThemeResources r,String value,float x,float y,float size,float width,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(t.textSize(value,size,bold));
        paint.setTypeface(r==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):r.fontForText(value,bold));
        float w=paint.measureText(value);if(w>width)paint.setTextSize(paint.getTextSize()*width/w);
        c.drawText(value,x,y,paint);
    }
    private float centeredBaseline(Bounds bounds,ThemeResources resources,String value,int size,boolean bold){
        paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(value,bold));
        paint.setTextSize(theme.textSize(value,size,bold));
        Paint.FontMetrics metrics=paint.getFontMetrics();
        return bounds.y+bounds.height/2-(metrics.ascent+metrics.descent)/2;
    }
    private void text(Canvas c,ThemeResources r,String value,float x,float y,float size,int color,boolean bold){
        fitted(c,theme,r,value,x,y,size,Float.MAX_VALUE,color,bold);
    }
}
