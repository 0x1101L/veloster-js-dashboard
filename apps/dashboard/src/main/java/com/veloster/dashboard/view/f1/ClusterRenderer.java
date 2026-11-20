package com.veloster.dashboard.view.f1;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.view.MediaBar;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSpec.SurfaceRole;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemePalette;

import com.veloster.dashboard.vehicle.js.JsGearState;

import com.veloster.dashboard.vehicle.js.JsGearState;

import android.graphics.*;
import com.veloster.i18n.UiText;
import java.util.Locale;

/** F1 and media pages for the three Dashboard profiles. */
public final class ClusterRenderer {
    private final ThemeSurface surface;
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeResources resources;
    private ThemeSpec.Rect activeF1;
    public void setTheme(ThemeSpec value,ThemeResources assets){theme=value==null?ThemeSpec.original():value;resources=assets;}
    private final boolean jsPresentation;
    private boolean pdProfile,jsProfile,jsnProfile,profileSupported;
    private Telemetry.Sample[] samples;
    private ShiftGuide.Cue cue;
    private MediaState.Snapshot music;
    private Bitmap artwork;
    private int shiftTarget;
    private boolean mediaBarVisible=true;
    public void setMediaBarVisible(boolean value){mediaBarVisible=value;}
    private long frameTime;

    public ClusterRenderer(boolean jsPresentation){this(jsPresentation,new ThemeSurface());}
    public ClusterRenderer(boolean jsPresentation,ThemeSurface surface){this.jsPresentation=jsPresentation;this.surface=surface;}

    public void prepare(Telemetry model,Telemetry.Sample[] samples,long now,int target,boolean media,
                 MediaState.Snapshot track,Bitmap artwork){
        pdProfile=model.pdProfile();jsProfile=model.jsProfile();jsnProfile=model.jsnProfile();
        profileSupported=model.supportedProfile();this.samples=samples;frameTime=now;
        shiftTarget=target;cue=ShiftGuide.evaluate(samples,target,model.pdLayoutProfile());
        music=track;this.artwork=artwork;
    }

    private void temperature(Canvas c,String value,float x,float y,float width){
        if(jsPresentation)fitted(c,value,x,y,22,width,ThemePalette.primary(theme,0xffe1edf2));
        else text(c,value,x,y,22,ThemePalette.primary(theme,0xffe1edf2),true);
    }
    private final Paint paint=new Paint(3);

    private void box(Canvas c,float l,float t,float r,float b,int color){paint.setColor(color);c.drawRect(l,t,r,b,paint);}
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int edge){
        surface.panelWithLegacy(c,theme,paint,x,y,w,h,fill,edge);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        rawText(c,s,x,y,nodeSize(s,size,bold),color,bold);
    }
    private void rawText(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setColor(nodeColor(color));paint.setTextSize(size);paint.setTypeface(nodeFace(s,bold));c.drawText(s,x,y,paint);
    }
    private void fitted(Canvas c,String s,float x,float y,float size,float width,int color){
        size=nodeSize(s,size,false);
        paint.setTypeface(nodeFace(s,false));paint.setTextSize(size);
        float measured=paint.measureText(s);
        rawText(c,s,x,y,measured>width?size*width/measured:size,color,false);
    }
    private void ellipsis(Canvas c,String s,float x,float y,float size,float width,int color){
        size=nodeSize(s,size,false);
        paint.setTypeface(nodeFace(s,false));paint.setTextSize(size);
        if(paint.measureText(s)>width){
            int end=s.length();while(end>0 && paint.measureText(s.substring(0,end)+"…")>width)end--;
            if(end>0 && Character.isHighSurrogate(s.charAt(end-1)))end--;
            s=s.substring(0,end)+"…";
        }
        paint.setColor(color);paint.setTextSize(size);c.drawText(s,x,y,paint);
    }
    private void centered(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        size=nodeSize(s,size,bold);
        paint.setTextSize(size);paint.setTypeface(nodeFace(s,bold));
        rawText(c,s,x-paint.measureText(s)/2,y,size,color,bold);
    }
    private void centeredFit(Canvas c,String s,float x,float y,float size,float width,int color,boolean bold){
        if(theme.materialFor(SurfaceRole.DATA_CARD).legacy()&&0==0){centered(c,s,x,y,size,color,bold);return;}
        size=nodeSize(s,size,bold);
        paint.setTypeface(nodeFace(s,bold));paint.setTextSize(size);
        float measured=paint.measureText(s);if(measured>width){size*=width/measured;paint.setTextSize(size);}
        rawText(c,s,x-paint.measureText(s)/2,y,size,color,bold);
    }
    private static final String[] PREFIX={"엔진 RPM","오일 온도","냉각수 온도","앞 왼쪽 휠","앞 오른쪽 휠","뒤 왼쪽 휠","뒤 오른쪽 휠","속도 km/h","현재 기어","스로틀 %","브레이크 압력 bar","횡가속","종가속","조향각","외기 온도","출력 (계산)","토크 (계산)","부스트 (계산)","앞 왼쪽 공기압","앞 오른쪽 공기압","뒤 왼쪽 공기압","뒤 오른쪽 공기압","TCS","ESC","ABS","배터리 SOC","배터리 전압","조향 속도","e-LSD 수치","흡기 온도","가속 페달 %","TCS 사용 상태","ESC 사용 상태","ABS 경고등"};
    private int index(String prefix){for(int i=0;i<PREFIX.length;i++)if(PREFIX[i].equals(prefix))return i;return -1;}
    private String value(String prefix){int i=index(prefix);if(i<0||i>=samples.length||!(samples[i].valid||(jsPresentation&&samples[i].held)))return "—";
        if(i==8)return jsPresentation?JsGearState.valueLabel((int)samples[i].value):Telemetry.gearLabel((int)samples[i].value);
        if(jsPresentation&&Telemetry.jsTenthSignal(i))return Telemetry.jsTenth(samples[i].value,i==11||i==12);
        return String.format(Locale.US,i==0||i==7||(i>=Telemetry.TPMS_FL&&i<=Telemetry.BAT_SOC)||i==Telemetry.STEERING_SPEED?"%.0f":i==11||i==12?"%+.2f":i==13?"%+.1f":i==17||i==Telemetry.BAT_VOLTAGE&&!jsPresentation?"%.2f":"%.1f",Telemetry.displayValue(i,samples[i].value));}
    private float numeric(String prefix){int i=index(prefix);return i>=0&&i<samples.length&&samples[i].valid?Telemetry.displayValue(i,samples[i].value):0;}
    private String gearTitle(String title){return title;}
    private void cover(Canvas c,float x,float y,float size){
        float radius=Math.min(theme.material.radius,size/2);
        paint.setColor(theme.material.legacy()?0xff1c303b:theme.panel);
        surface.roundRect(c,theme,new RectF(x,y,x+size,y+size),radius,radius,paint);
        if(artwork!=null){
            MediaBar.drawArtwork(c,artwork,x,y,size,radius,theme,surface);
        }else{
            centered(c,"♪",x+size/2,y+size*.61f,size*.35f,ThemePalette.accent(theme,0xffa4d9ef),true);
            if(size>100)centered(c,"ALBUM ART",x+size/2,y+size*.84f,11,ThemePalette.secondary(theme,0xff91a5ae),false);
        }
    }
    public void drawMedia(Canvas c,com.veloster.dashboard.view.DashboardPage.Bounds b){
        float top=MediaBar.noticeBounds(b,music==null?0:music.noticeAgeMs).y;
        float pad=0;
        panel(c,b.x,top,b.width,b.height,(theme.materialFor(SurfaceRole.PANEL).legacy()?0xff12212a:theme.panelRaised),theme.accent);
        float art=Math.max(1,b.height-2*pad);
        cover(c,b.x+pad,top+pad,art);
        float left=b.x+2*pad+art,right=b.right()-pad,available=Math.max(1,right-left);
        String source=music==null||music.source.length()==0?"MEDIA":music.source;
        String title=music==null||music.title.length()==0?"—":music.title;
        String artist=music==null||music.artist.length()==0?"—":music.artist;
        text(c,source,left,top+b.height*.20f,11,theme.accent,true);
        ellipsis(c,title,left,top+b.height*.45f,21,available,theme.textPrimary);
        ellipsis(c,artist,left,top+b.height*.68f,13,available*.72f,theme.textSecondary);
        if(music!=null&&music.hasProgress()){
            String times=MediaState.time(music.positionMs)+" / "+MediaState.time(music.durationMs);
            text(c,times,left+available*.75f,top+b.height*.68f,12,theme.textPrimary,false);
            box(c,left,top+b.height*.82f,right,top+b.height*.86f,theme.stroke);
            box(c,left,top+b.height*.82f,left+available*music.progress(),top+b.height*.86f,theme.accent);
        }
    }
    private int barStyle(String prefix){return "브레이크 압력 bar".equals(prefix)?theme.brakeBarStyle():theme.pedalBarStyle();}
    private void compactPedal(Canvas c,String label,String prefix,String unit,int x,int width,int color,float maximum){
        text(c,label,x,420,11,ThemePalette.secondary(theme,0xff91a5ae),true);
        fitted(c,value(prefix)+" "+unit,x+66,422,18,width-66,ThemePalette.primary(theme,0xffe1edf2));
        float fraction=Math.max(0,Math.min(1,numeric(prefix)/maximum));
        surface.levelBar(c,paint,x,432,width,6,fraction,color,ThemePalette.stroke(theme,0xff303638),barStyle(prefix));
    }
    private void pedal(Canvas c,String label,String prefix,String unit,int x,int color,float maximum){
        surface.drawWithLegacy(c,theme,SurfaceRole.DATA_CARD,paint,x,377,376,57,
                theme.materialFor(SurfaceRole.DATA_CARD).legacy()?0xff111516:theme.panel,ThemePalette.stroke(theme,0xff303638));
        text(c,label,x+0,396,10,ThemePalette.secondary(theme,0xff91a5ae),true);
        String v=value(prefix);
        fitted(c,v,x+0,423,25,99-0,ThemePalette.primary(theme,0xffe1edf2));
        text(c,unit,x+91+0,422,11,ThemePalette.secondary(theme,0xff91a5ae),false);
        float fraction=Math.max(0,Math.min(1,numeric(prefix)/maximum));
        surface.levelBar(c,paint,x+148,403,208.4f,18,fraction,color,ThemePalette.stroke(theme,0xff303638),barStyle(prefix));
    }
    public void drawCluster(Canvas c){
        if(!theme.materialFor(SurfaceRole.DATA_CARD).legacy()&&!theme.hasF1()){
            surface.draw(c,theme,SurfaceRole.DATA_CARD,18,120,258,247,theme.panel,theme.stroke);
            surface.draw(c,theme,SurfaceRole.DATA_CARD,524,120,258,247,theme.panel,theme.stroke);
        }
        if(theme.hasF1()&&!theme.materialFor(SurfaceRole.DATA_CARD).legacy()){
            for(String id:new String[]{"speed","boost","rpm","temperatures"}){ThemeSpec.Rect r=theme.f1(id);surface.draw(c,theme,SurfaceRole.DATA_CARD,r.x,r.y,r.width,r.height,theme.panel,theme.stroke);}
        }
        float rpm=Math.max(0,Math.min(8000,numeric("엔진 RPM")));
        f1Start(c,"shift",18,60,764,48);
        text(c,cue.shift?"SHIFT ↑":"SHIFT / LED",20,83,11,cue.shift?ThemePalette.critical(theme,0xffff4059):ThemePalette.secondary(theme,0xff91a5ae),true);
        text(c,shiftTarget+" rpm",690,83,11,ThemePalette.secondary(theme,0xff91a5ae),true);
        for(int i=0;i<15;i++){
            int color=i<5?ThemePalette.accent(theme,0xffa4d9ef):i<10?(theme.material.legacy()?0xffd0ebf8:theme.accent):ThemePalette.critical(theme,0xffff4059);
            float x=155+i*35;
            boolean lit=cue.shift?(frameTime/250)%2==0:i<cue.leds;
            paint.setColor(lit?color:(theme.material.legacy()?0xff252a2c:theme.stroke));c.drawCircle(x,79,10,paint);
            if(lit){paint.setColor(0x70ffffff);c.drawCircle(x-2,76,3,paint);}
        }
        box(c,18,105,782,108,ThemePalette.stroke(theme,0xff303638));
        if(rpm>0)box(c,18,105,18+764*rpm/8000,108,ThemePalette.accent(theme,0xffa4d9ef));
        f1End(c);
        f1Start(c,"gear",294,120,212,247);
        // Central gear is the primary cue, like a single-seat race display.
        int gearFill=ThemePalette.accent(theme,0xffa4d9ef);
        if(!surface.draw(c,theme,SurfaceRole.DATA_CARD,294,120,212,247,gearFill,theme.stroke))
            box(c,294,120,506,367,gearFill);
        centered(c,gearTitle(pdProfile||jsProfile?"G E A R":"E M S  G E A R"),400,148,15,ThemePalette.background(theme,0xff040506),true);
        String gear=value("현재 기어");
        centeredFit(c,gear,400,321,theme.typography.gearSize,212-2*0,ThemePalette.background(theme,0xff040506),true);
        centered(c,vehicleName(),400,350,14,ThemePalette.background(theme,0xff040506),true);
        f1End(c);
        f1Start(c,"speed",18,120,258,158);
        text(c,"SPEED",18+0,148,14,ThemePalette.secondary(theme,0xff91a5ae),true);
        text(c,"km/h",233-0,148,12,ThemePalette.secondary(theme,0xff91a5ae),false);
        centered(c,value("속도 km/h"),150,253,88,ThemePalette.primary(theme,0xffe1edf2),true);
        box(c,18,276,276,278,ThemePalette.stroke(theme,0xff303638));
        f1End(c);
        f1Start(c,"boost",18,278,258,89);
        text(c,"OIL TEMP",18+0,307,11,ThemePalette.secondary(theme,0xff91a5ae),true);
        temperature(c,value("오일 온도"),175,310,72);
        text(c,"°C",254-0,310,11,ThemePalette.secondary(theme,0xff91a5ae),false);
        f1End(c);
        f1Start(c,"rpm",522,120,260,158);
        text(c,"ENGINE",522+0,148,14,ThemePalette.secondary(theme,0xff91a5ae),true);
        text(c,"rpm",753-0,148,12,ThemePalette.secondary(theme,0xff91a5ae),false);
        centered(c,value("엔진 RPM"),653,247,69,ThemePalette.primary(theme,0xffe1edf2),true);
        box(c,524,276,782,278,ThemePalette.stroke(theme,0xff303638));
        f1End(c);
        f1Start(c,"temperatures",524,278,258,89);
        text(c,"COOLANT",524+0,307,11,ThemePalette.secondary(theme,0xff91a5ae),true);
        temperature(c,value("냉각수 온도"),685,310,72);
        text(c,"°C",764-0,310,11,ThemePalette.secondary(theme,0xff91a5ae),false);
        f1End(c);
        c.save();if(!mediaBarVisible)c.translate(0,480-(theme.hasF1()?theme.f1("pedal").y+theme.f1("pedal").height:434));
        f1Start(c,"pedal",18,377,376,57);
        pedal(c,pdProfile||jsProfile?"PEDAL":"THROTTLE",pdProfile||jsProfile?"가속 페달 %":"스로틀 %","%",18,ThemePalette.accent(theme,0xffa4d9ef),100);
        f1End(c);
        c.restore();
        c.save();if(!mediaBarVisible)c.translate(0,480-(theme.hasF1()?theme.f1("brake").y+theme.f1("brake").height:434));
        f1Start(c,"brake",406,377,376,57);
        pedal(c,"BRAKE PRESSURE", "브레이크 압력 bar", "bar",406,ThemePalette.critical(theme,0xffff4059),100);
        f1End(c);
        c.restore();

    }
    /** Draw component contents in their resolved rectangles, without scaling the Canvas. */
    public void drawAdaptive(Canvas c,com.veloster.dashboard.view.DashboardPage.Content content){
        boolean grid=content.f1Grid!=null;
        float sideSize=Float.MAX_VALUE;
        for(int slot:new int[]{0,2}){
            String id=com.veloster.dashboard.view.DashboardPage.f1OrderNode(theme.f1Order(),slot);
            activeF1=theme.f1(id);
            com.veloster.dashboard.view.DashboardPage.Bounds b=adaptiveBody(content,content.f1(theme,id),id,grid);
            String reading=mainReading(id);
            sideSize=Math.min(sideSize,adaptiveSize(reading,b,grid?0.78f:0.84f,
                    Math.min(grid?82:88,b.height*(grid?0.64f:0.62f)),true,1));
        }
        activeF1=null;
        for(String id:new String[]{"shift","gear","speed","boost","rpm","temperatures","pedal","brake"}){
            ThemeSpec.Rect node=theme.f1(id);activeF1=node;
            com.veloster.dashboard.view.DashboardPage.Bounds outer=content.f1(theme,id);
            c.save();
            boolean gear="gear".equals(id);
            SurfaceRole role=gear?SurfaceRole.GEAR_CARD:SurfaceRole.DATA_CARD;
            boolean legacy=theme.materialFor(role).legacy();
            if(legacy)c.clipRect(outer.x,outer.y,outer.right(),outer.bottom());
            int fill=gear?adaptiveGearColor():theme.panel;
            if(!"shift".equals(id)){
                if(grid)surface.card(c,theme,role,outer.x,outer.y,outer.width,outer.height,content.f1Grid.radius,fill);
                else surface.drawWithLegacy(c,theme,role,paint,
                        outer.x,outer.y,outer.width,outer.height,fill,theme.stroke);
            }
            if(!legacy)c.clipRect(outer.x,outer.y,outer.right(),outer.bottom());
            com.veloster.dashboard.view.DashboardPage.Bounds b=adaptiveBody(content,outer,id,grid);
            boolean middle=id.equals(com.veloster.dashboard.view.DashboardPage.f1OrderNode(theme.f1Order(),1));
            int primary=theme.textPrimary,secondary=theme.textSecondary;
            if("shift".equals(id)){
                adaptiveText(c,cue.shift?"SHIFT ↑":"SHIFT / LED",b,0.36f,11,secondary,true,false,0.4f);
                String target=shiftTarget+" rpm";
                com.veloster.dashboard.view.DashboardPage.Bounds right=new com.veloster.dashboard.view.DashboardPage.Bounds(b.x+b.width*0.72f,b.y,b.width*0.28f,b.height);
                adaptiveText(c,target,right,0.36f,11,secondary,false,true,1,grid);
                float radius=Math.min(7,b.width/42);
                for(int i=0;i<15;i++){
                    boolean lit=cue.shift?(frameTime/250)%2==0:i<cue.leds;
                    paint.setColor(lit?(i<5?theme.accent:i<10?theme.textPrimary:theme.critical):theme.stroke);
                    c.drawCircle(b.x+radius+(b.width-2*radius)*i/14f,b.y+b.height*0.60f,radius,paint);
                }
                box(c,b.x,b.y+b.height-3,b.x+b.width,b.y+b.height,theme.stroke);
                box(c,b.x,b.y+b.height-3,b.x+b.width*Math.min(1,Math.max(0,numeric("엔진 RPM"))/8000),b.y+b.height,theme.accent);
            }else if("gear".equals(id)){
                int ink=theme.background;
                adaptiveText(c,pdProfile||jsProfile?"GEAR":"EMS GEAR",b,grid?0.13f:0.10f,14,ink,true,true,1);
                if(middle)adaptiveText(c,mainReading(id),b,grid?0.72f:0.74f,Math.min(theme.typography.gearSize,b.height*0.64f),ink,true,true,1);
                else adaptiveFixedText(c,mainReading(id),b,grid?0.78f:0.84f,sideSize,ink);
                adaptiveText(c,vehicleName(),b,grid?0.94f:0.97f,13,ink,false,true,1);
            }else if("speed".equals(id)||"rpm".equals(id)){
                boolean speed="speed".equals(id);
                adaptiveText(c,speed?"SPEED":"ENGINE",b,grid?0.17f:0.16f,14,secondary,true,false,0.65f);
                com.veloster.dashboard.view.DashboardPage.Bounds unit=new com.veloster.dashboard.view.DashboardPage.Bounds(b.x+b.width*0.70f,b.y,b.width*0.30f,b.height);
                adaptiveText(c,speed?"km/h":"rpm",unit,grid?0.17f:0.16f,12,secondary,false,true,1,grid);
                if(middle)adaptiveText(c,mainReading(id),b,grid?0.72f:0.74f,Math.min(theme.typography.gearSize,b.height*0.64f),primary,true,true,1);
                else adaptiveFixedText(c,mainReading(id),b,grid?0.78f:0.84f,sideSize,primary);
            }else if("boost".equals(id)){
                adaptiveText(c,jsPresentation?"BOOST":"OIL TEMP",b,grid?0.37f:0.26f,11,secondary,true,false,grid?0.50f:1);
                String reading=value(jsPresentation?"부스트 (계산)":"오일 온도")+(jsPresentation?" bar":" °C");
                if(grid)adaptiveText(c,reading,new com.veloster.dashboard.view.DashboardPage.Bounds(b.x+b.width*0.51f,b.y,b.width*0.49f,b.height),0.37f,19,primary,false,true,1,true);
                else adaptiveText(c,reading,b,0.87f,24,primary,false,false,1);
            }else if("temperatures".equals(id)){
                adaptiveRow(c,b,"COOLANT",value("냉각수 온도")+" °C",grid?0.37f:0.35f,grid);
                if(jsPresentation)adaptiveRow(c,b,"INTAKE",value("흡기 온도")+" °C",0.88f,grid);
            }else{
                boolean pedal="pedal".equals(id);String prefix=pedal?(pdProfile||jsProfile?"가속 페달 %":"스로틀 %"):"브레이크 압력 bar";
                adaptiveText(c,pedal?(pdProfile||jsProfile?"PEDAL":"THROTTLE"):"BRAKE PRESSURE",b,grid?0.47f:0.26f,10,secondary,true,false,0.52f);
                com.veloster.dashboard.view.DashboardPage.Bounds valueBox=new com.veloster.dashboard.view.DashboardPage.Bounds(b.x+b.width*0.55f,b.y,b.width*0.45f,b.height);
                adaptiveText(c,value(prefix)+(pedal?" %":" bar"),valueBox,grid?0.47f:0.50f,grid?19:23,primary,false,true,1,grid);
                float barY=grid?b.y+b.height-4:b.y+b.height*0.82f;
                surface.levelBar(c,paint,b.x,barY,b.width,grid?4:5,numeric(prefix)/100,pedal?theme.accent:theme.critical,theme.stroke,barStyle(prefix));
            }
            c.restore();activeF1=null;
        }
    }
    private String mainReading(String id){return value("gear".equals(id)?"현재 기어":"speed".equals(id)?"속도 km/h":"엔진 RPM");}
    private com.veloster.dashboard.view.DashboardPage.Bounds adaptiveBody(com.veloster.dashboard.view.DashboardPage.Content content,
            com.veloster.dashboard.view.DashboardPage.Bounds outer,String id,boolean grid){
        ThemeSpec.Rect node=theme.f1(id);
        float px=grid?Math.min(content.f1Grid.padding,outer.width*0.08f):0;
        float py=grid?Math.min(content.f1Grid.padding,outer.height*0.18f):0;
        if(grid&&"shift".equals(id)){px=0;py=0;}
        int left=node==null?0:node.left,top=node==null?0:node.top;
        int right=node==null?0:node.right,bottom=node==null?0:node.bottom;
        return new com.veloster.dashboard.view.DashboardPage.Bounds(outer.x+left+px,outer.y+top+py,
                Math.max(1,outer.width-left-right-2*px),Math.max(1,outer.height-top-bottom-2*py));
    }
    private void adaptiveFixedText(Canvas c,String value,com.veloster.dashboard.view.DashboardPage.Bounds b,
            float baseline,float size,int color){
        paint.setTypeface(nodeFace(value,true));paint.setTextSize(size);
        float factor=0.5f;
        if(activeF1!=null&&!"stretch".equals(activeF1.alignment))factor="end".equals(activeF1.alignment)?1f:"center".equals(activeF1.alignment)?0.5f:0;
        rawText(c,value,b.x+(b.width-paint.measureText(value))*factor,b.y+b.height*baseline,size,color,true);
    }
    private void adaptiveRow(Canvas c,com.veloster.dashboard.view.DashboardPage.Bounds b,String label,String value,float baseline,boolean grid){
        adaptiveText(c,label,b,baseline,10,theme.textSecondary,true,false,0.48f);
        com.veloster.dashboard.view.DashboardPage.Bounds right=new com.veloster.dashboard.view.DashboardPage.Bounds(b.x+b.width*0.50f,b.y,b.width*0.50f,b.height);
        adaptiveText(c,value,right,baseline,19,theme.textPrimary,false,true,1,grid);
    }
    private void adaptiveText(Canvas c,String value,com.veloster.dashboard.view.DashboardPage.Bounds b,float baseline,float size,int color,boolean bold,boolean center,float portion){
        adaptiveText(c,value,b,baseline,size,color,bold,center,portion,false);
    }
    private void adaptiveText(Canvas c,String value,com.veloster.dashboard.view.DashboardPage.Bounds b,float baseline,float size,int color,boolean bold,boolean center,float portion,boolean end){
        size=adaptiveSize(value,b,baseline,size,bold,portion);
        paint.setTypeface(nodeFace(value,bold));paint.setTextSize(size);
        float width=Math.max(1,b.width*portion);
        float factor=end?1:center?0.5f:0;
        if(activeF1!=null&&!"stretch".equals(activeF1.alignment))factor="end".equals(activeF1.alignment)?1f:"center".equals(activeF1.alignment)?0.5f:0;
        float x=b.x+(width-paint.measureText(value))*factor;
        rawText(c,value,x,b.y+b.height*baseline,size,color,bold);
    }
    private float adaptiveSize(String value,com.veloster.dashboard.view.DashboardPage.Bounds b,float baseline,float size,boolean bold,float portion){
        size=Math.min(nodeSize(value,size,bold),b.height*(baseline<0.3f?0.28f:0.75f));
        paint.setTypeface(nodeFace(value,bold));paint.setTextSize(size);
        if(theme.f1Grid()!=null){
            Paint.FontMetrics metrics=paint.getFontMetrics();
            float factor=Math.min(1,b.height*baseline/Math.max(1,-metrics.ascent));
            factor=Math.min(factor,b.height*(1-baseline)/Math.max(1,metrics.descent));
            // The body inset already leaves space for descenders on bottom-aligned rows.
            if(baseline>=0.85f)factor=Math.min(1,b.height*baseline/Math.max(1,-metrics.ascent));
            size*=factor;paint.setTextSize(size);
        }
        float width=Math.max(1,b.width*portion),measured=paint.measureText(value);
        if(measured>width){size*=width/measured;paint.setTextSize(size);}
        return size;
    }
    private int adaptiveGearColor(){return ThemePalette.accent(theme,0xffa4d9ef);}
    private float nodeSize(String text,float size,boolean bold){return theme.textSize(activeF1==null?null:activeF1.fontRole,text,size,bold);}
    private Typeface nodeFace(String text,boolean bold){
        if(resources==null)return bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT;
        return activeF1==null||activeF1.fontRole==null?resources.fontForText(text,bold):resources.font(activeF1.fontRole,bold);
    }
    private int nodeColor(int fallback){
        String role=activeF1==null?null:activeF1.colorRole;
        return role==null?fallback:"primary".equals(role)?theme.textPrimary:"secondary".equals(role)?theme.textSecondary:"accent".equals(role)?theme.accent:"warning".equals(role)?theme.warning:theme.critical;
    }
    private void f1Start(Canvas c,String id,float x,float y,float width,float height){
        if(!theme.hasF1())return;
        ThemeSpec.Rect r=theme.f1(id);activeF1=r;c.save();c.clipRect(r.x,r.y,r.x+r.width,r.y+r.height);
        float availableW=r.width-r.left-r.right,availableH=r.height-r.top-r.bottom;
        float sx=Math.min(availableW/width,availableH/height),sy=sx,ox=0,oy=0;
        if(!"stretch".equals(r.alignment)){
            sx=sy=Math.min(sx,sy);float factor="center".equals(r.alignment)?0.5f:"end".equals(r.alignment)?1f:0f;
            ox=(availableW-width*sx)*factor;oy=(availableH-height*sy)*factor;
        }
        c.translate(r.x+r.left+ox,r.y+r.top+oy);c.scale(sx,sy);c.translate(-x,-y);
    }
    private void f1End(Canvas c){if(theme.hasF1()){c.restore();activeF1=null;}}
    private String vehicleName(){return pdProfile?"i30 N Line":jsProfile?"Veloster (WIP)":jsnProfile?"VELOSTER N":profileSupported?"Dashboard":"UNSUPPORTED";}

}
