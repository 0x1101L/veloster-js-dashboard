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

    static final int LANGUAGE=0,THEME=1,SHIFT_TARGET=2,MEDIA_BAR=3,PAGE_MENU=4,MENU_POSITION=5;
    static final int PEDAL_BAR=6,BRAKE_BAR=7,F1_ORDER=8,UI_GROUP=9,F1_GROUP=10,MENU_STYLE=12,MEDIA_NOTICES=13;
    static final int WALLPAPER=14,DIAGNOSTICS=15,MENU_DOCK=16;
    static final int LIQUID_GLASS=17,LIQUID_GLASS_TRANSPARENCY=18,LIQUID_GLASS_BLUR=19,LIQUID_GLASS_HIGHLIGHT=27;
    static final int ACCENT_PALETTE=28;
    static final int WALLPAPER_COLUMNS=3;
    public static final int GAUGE_MENU=20,GAUGE_SIGNAL=21,GAUGE_STYLE=22,GAUGE_SWAP=23,GAUGE_RESET=24;
    public static final int GRAPH_SIGNAL=25,TRANSMISSION=26;
    private static final int TITLE_TEXT_SIZE=22,ROW_TEXT_SIZE=18,HEADER_ACTION_TEXT_SIZE=16;
    private static final int GROUP_INSET=4,ROW_PADDING_TRIM=4;
    private static boolean choicePage(int screen){return screen>=0&&screen<=8||screen==MENU_STYLE||screen==MEDIA_NOTICES||screen==MENU_DOCK||screen==LIQUID_GLASS_TRANSPARENCY||screen==LIQUID_GLASS_BLUR||screen==LIQUID_GLASS_HIGHLIGHT||screen==ACCENT_PALETTE||screen>=GAUGE_SIGNAL&&screen<=GAUGE_SWAP||screen==GRAPH_SIGNAL||screen==TRANSMISSION;}
    private static boolean applyPage(int screen){return choicePage(screen)||screen==GAUGE_RESET;}
    public static final class Editor {
        public interface Preview {void draw(Canvas canvas,int width,int height);void tap(float x,float y,int width,int height);}
        int screen=-1,parent=-1,selected;boolean[] checked;
        float scrollY=Float.NaN;
        Preview preview;
        ThemeSpec.Wallpaper[] wallpapers=new ThemeSpec.Wallpaper[0];
        Bitmap[] wallpaperThumbnails=new Bitmap[0];
        boolean[] wallpaperThumbnailTried=new boolean[0];
        int[] swatches;
        String title="",message="";String[] labels=new String[0],values=new String[0];int[] actions=new int[0];
        public void page(int id,int parent,String title,String[] labels,String[] values,int[] actions){
            this.screen=id;this.parent=parent;this.title=title;this.labels=labels;this.values=values;this.actions=actions;scrollY=Float.NaN;checked=null;message="";preview=null;
            wallpapers=new ThemeSpec.Wallpaper[0];wallpaperThumbnails=new Bitmap[0];wallpaperThumbnailTried=new boolean[0];swatches=null;
        }
        void root(String language,boolean js){
            if(js)page(-1,-1,UiText.t("설정"),UiText.list(new String[]{"UI 설정","F1 설정","언어","진단"}),
                new String[]{"","",language,""},new int[]{UI_GROUP,F1_GROUP,LANGUAGE,DIAGNOSTICS});
            else page(-1,-1,UiText.t("설정"),UiText.list(new String[]{"UI 설정","F1 설정","언어"}),
                new String[]{"","",language},new int[]{UI_GROUP,F1_GROUP,LANGUAGE});
        }
        void wallpaperPage(ThemeSpec.Wallpaper[] items,int selected){
            page(WALLPAPER,UI_GROUP,UiText.t("배경화면"),new String[items.length],new String[0],new int[0]);
            wallpapers=items;this.selected=selected;
            wallpaperThumbnails=new Bitmap[items.length];wallpaperThumbnailTried=new boolean[items.length];
            for(int i=0;i<items.length;i++)labels[i]=items[i].name;
        }
        void swatchPage(String title,String[] labels,int[] colors,int selected){
            page(ACCENT_PALETTE,UI_GROUP,title,labels,new String[0],new int[0]);
            this.selected=selected;swatches=colors.clone();
        }
    }
    /** A single geometry source for drawing, scrolling, and hit testing. */
    public static final class Layout {
        final Bounds area,back,apply,previewButton,preview,list;
        final int gap,offset,count,rowPad;
        final float tileGap,tileWidth,tileHeight;
        final float rowHeight,firstRowY,scrollY,maxScroll;
        public Layout(ThemeSpec theme,DashboardPage.Content content,Editor editor){
            area=content.padded(theme).inset(GROUP_INSET);
            gap=theme.spacing.gridGapY;
            rowPad=Math.max(8,theme.spacing.settingsPadding-ROW_PADDING_TRIM);
            float header=40;
            float button=Math.min(area.width*.23f,96+2*theme.spacing.buttonPadding);
            back=new Bounds(area.x,area.y,button,header);
            apply=new Bounds(area.right()-button,area.y,button,header);
            previewButton=new Bounds(apply.x-button-theme.spacing.gridGapX,area.y,button,header);
            float top=back.bottom()+gap+(editor.screen==F1_ORDER||editor.preview!=null?24:0);
            float message=editor.message.length()>0?24:0;
            list=new Bounds(area.x,top,area.width,Math.max(0,area.bottom()-top-message));
            preview=new Bounds(area.x,top,(int)area.width,(int)Math.max(0,area.bottom()-top));
            boolean wallpaperGrid=editor.screen==WALLPAPER;
            int columns=wallpaperGrid?WALLPAPER_COLUMNS:1;
            int rows=(editor.labels.length+columns-1)/columns;
            if(wallpaperGrid){
                tileGap=Math.max(8,Math.min(16,gap));
                tileWidth=Math.max(1,(list.width-(columns-1)*tileGap)/columns);
                tileHeight=26+Math.max(1,Math.min(tileWidth*.56f,(list.height-tileGap-52)/2));
                rowHeight=tileHeight+tileGap;
            }else{
                tileGap=tileWidth=tileHeight=0;
                int fittingRows=Math.max(1,(int)(list.height/44));
                rowHeight=rows>fittingRows?44:Math.max(1,list.height/fittingRows);
            }
            float height=rows==0?0:rows*rowHeight-tileGap;
            maxScroll=list.height>0?Math.max(0,height-list.height):0;
            float initial=(wallpaperGrid||choicePage(editor.screen))&&editor.selected>=0
                    ?(editor.selected/columns+.5f)*rowHeight-list.height*.5f:0;
            scrollY=clampScroll(Float.isNaN(editor.scrollY)?initial:editor.scrollY);
            int firstRow=(int)(scrollY/rowHeight);
            offset=firstRow*columns;
            firstRowY=list.y+firstRow*rowHeight-scrollY;
            int visibleRows=(int)Math.ceil((list.bottom()-firstRowY)/rowHeight);
            count=list.height>0?Math.max(0,Math.min(editor.labels.length-offset,visibleRows*columns)):0;
        }
        public int hit(Editor editor,float x,float y){
            if(back.contains(x,y))return -2;
            if(editor.screen==WALLPAPER&&apply.contains(x,y))return -8;
            if(applyPage(editor.screen)&&apply.contains(x,y))return -3;
            if(editor.screen==THEME&&previewButton.contains(x,y))return -6;
            if(editor.preview!=null)return preview.contains(x,y)?-7:-1;
            if(!list.contains(x,y))return -1;
            if(editor.screen==WALLPAPER){
                for(int i=0;i<count;i++)if(wallpaperTile(i).contains(x,y))return offset+i;
                return -1;
            }
            int row=(int)((y-firstRowY)/rowHeight);
            return row<count?offset+row:-1;
        }
        private Bounds wallpaperTile(int visible){
            int column=visible%WALLPAPER_COLUMNS,row=visible/WALLPAPER_COLUMNS;
            return new Bounds(list.x+column*(tileWidth+tileGap),firstRowY+row*rowHeight,tileWidth,tileHeight);
        }
        private float clampScroll(float position){return Math.max(0,Math.min(maxScroll,position));}
        boolean scrollBy(Editor editor,float delta){
            float next=clampScroll(scrollY+delta);
            if(next==scrollY)return false;
            editor.scrollY=next;
            return true;
        }
        public void tapPreview(Editor editor,float x,float y){
            editor.preview.tap(x-preview.x,y-preview.y,(int)preview.width,(int)preview.height);
        }
    }
    public void draw(Canvas c,ThemeSpec theme,ThemeResources resources,Editor editor,DashboardPage.Content content){
        this.theme=theme;
        Layout l=new Layout(theme,content,editor);
        float pad=theme.spacing.buttonPadding,baseline=l.back.atY(.68f);
        text(c,resources,"‹ "+UiText.t("돌아가기"),l.back.x+pad,baseline,HEADER_ACTION_TEXT_SIZE,theme.accent,true);
        float titleLeft=l.back.right()+theme.spacing.gridGapX;
        float titleRight=editor.screen==THEME?l.previewButton.x:
            (applyPage(editor.screen)||editor.screen==WALLPAPER)?l.apply.x:l.area.right();
        fitted(c,theme,resources,editor.title,titleLeft,baseline,TITLE_TEXT_SIZE,Math.max(1,titleRight-titleLeft),theme.textPrimary,true);
        if(applyPage(editor.screen))text(c,resources,UiText.t(editor.screen==GAUGE_RESET?"복원":"적용"),l.apply.x+pad,baseline,ROW_TEXT_SIZE,theme.accent,true);
        if(editor.screen==WALLPAPER)text(c,resources,UiText.t("테마 기본값"),l.apply.x+pad,baseline,HEADER_ACTION_TEXT_SIZE,theme.accent,true);
        if(editor.screen==THEME)text(c,resources,UiText.t("미리보기"),l.previewButton.x+pad,baseline,HEADER_ACTION_TEXT_SIZE,theme.accent,true);
        if(editor.preview!=null){
            text(c,resources,UiText.t("예시 데이터"),l.preview.x+pad,l.preview.y-6,14,theme.textSecondary,false);
            c.save();c.clipRect(l.preview.x,l.preview.y,l.preview.right(),l.preview.bottom());
            c.translate(l.preview.x,l.preview.y);editor.preview.draw(c,(int)l.preview.width,(int)l.preview.height);c.restore();
            return;
        }
        if(editor.screen==WALLPAPER){
            c.save();c.clipRect(l.list.x,l.list.y,l.list.right(),l.list.bottom());
            drawWallpapers(c,resources,editor,l);c.restore();
            return;
        }
        if(editor.screen==F1_ORDER)text(c,resources,UiText.t("왼쪽 → 오른쪽"),l.list.x+l.rowPad,l.list.y-6,14,theme.textSecondary,false);
        if(l.count>0)surface.settingsGroup(c,theme,l.list.x,l.list.y,l.list.width,Math.min(l.list.height,l.count*l.rowHeight));
        if(l.maxScroll>0){c.save();c.clipRect(l.list.x,l.list.y,l.list.right(),l.list.bottom());}
        for(int i=0;i<l.count;i++){
            int index=l.offset+i;float top=l.firstRowY+i*l.rowHeight;
            if(i>0){paint.setColor(theme.stroke);paint.setStrokeWidth(1);c.drawLine(l.list.x+l.rowPad,top,l.list.right()-l.rowPad,top,paint);}
            String value=editor.values.length>index?editor.values[index]:"";
            if(choicePage(editor.screen))value=editor.checked!=null?(editor.checked[index]?"✓":""):(editor.selected==index?"✓":"");
            row(c,theme,resources,editor.labels[index],value,l,top,!choicePage(editor.screen)&&editor.screen!=GAUGE_RESET);
            if(editor.swatches!=null&&index<editor.swatches.length){
                float cx=l.list.right()-l.rowPad-16,cy=top+l.rowHeight*.5f,radius=Math.min(12,l.rowHeight*.25f);
                paint.setColor(editor.swatches[index]);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,radius,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(index==editor.selected?3:1);
                paint.setColor(index==editor.selected?theme.accent:theme.stroke);c.drawCircle(cx,cy,radius+2,paint);paint.setStyle(Paint.Style.FILL);
            }
        }
        if(l.maxScroll>0)c.restore();
        if(editor.message.length()>0)fitted(c,theme,resources,editor.message,l.list.x+pad,l.list.bottom()+19,16,l.list.width-2*pad,theme.critical,false);
    }
    private void drawWallpapers(Canvas c,ThemeResources resources,Editor editor,Layout layout){
        for(int i=0;i<layout.count;i++){
            int index=layout.offset+i;
            Bounds tile=layout.wallpaperTile(i);
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

    private void row(Canvas c,ThemeSpec t,ThemeResources r,String label,String value,Layout l,float top,boolean chevron){
        float x=l.list.x+l.rowPad,y=top+l.rowHeight*.65f,right=l.list.right()-l.rowPad;
        float split=l.list.atX(.54f);
        fitted(c,t,r,label,x,y,ROW_TEXT_SIZE,Math.max(1,split-x),t.textPrimary,false);
        float size=t.textSize(value,ROW_TEXT_SIZE,false);
        paint.setTypeface(r==null?Typeface.DEFAULT:r.fontForText(value,false));paint.setTextSize(size);
        float max=Math.max(1,right-split-(chevron?20:0));
        float w=paint.measureText(value);
        if(w>max){size*=max/w;paint.setTextSize(size);w=paint.measureText(value);}
        paint.setColor(t.textSecondary);c.drawText(value,right-(chevron?20:0)-w,y,paint);
        if(chevron){float mid=top+l.rowHeight*.5f;paint.setStrokeWidth(2);c.drawLine(right-7,mid-5,right,mid,paint);c.drawLine(right,mid,right-7,mid+5,paint);}
    }
    private void fitted(Canvas c,ThemeSpec t,ThemeResources r,String value,float x,float y,float size,float width,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(t.textSize(value,size,bold));
        paint.setTypeface(r==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):r.fontForText(value,bold));
        float w=paint.measureText(value);if(w>width)paint.setTextSize(paint.getTextSize()*width/w);
        c.drawText(value,x,y,paint);
    }
    private void text(Canvas c,ThemeResources r,String value,float x,float y,float size,int color,boolean bold){
        fitted(c,theme,r,value,x,y,size,Float.MAX_VALUE,color,bold);
    }
}
