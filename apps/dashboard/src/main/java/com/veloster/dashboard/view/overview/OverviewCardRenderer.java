package com.veloster.dashboard.view.overview;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSpec.OverviewCardKind;
import com.veloster.dashboard.theme.ThemeSpec.OverviewCardStyle;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.vehicle.Telemetry;
import com.veloster.i18n.UiText;

/** Shared Canvas geometry for the overview page's standard and compound cards. */
final class OverviewCardRenderer {
    private static final String BODY="body", NUMERIC="numeric";
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ThemeSurface surface;
    private final OverviewCardStyle[] styles=new OverviewCardStyle[OverviewCardKind.values().length];
    private ThemeSpec theme;
    private ThemeResources resources;

    OverviewCardRenderer(ThemeSurface surface){this.surface=surface;setTheme(ThemeSpec.original(),null);}
    void setTheme(ThemeSpec theme,ThemeResources resources){
        this.theme=theme;this.resources=resources;
        for(OverviewCardKind kind:OverviewCardKind.values())styles[kind.ordinal()]=theme.overviewCardStyle(kind);
    }

    void standard(Canvas c,OverviewLayout layout,int slot,String title,String value,String unit,int color,int accent,
            int mode,boolean position,float fraction,float zero){
        OverviewCardStyle s=styles[OverviewCardKind.STANDARD.ordinal()];
        Area a=frame(c,layout,slot,s,accent);
        if(a.w<=0||a.h<=0)return;
        boolean bar=mode==1,dial=mode==0;
        float diameter=dial?Math.min(s.dialSize,a.w*.42f):0;
        float gaugeGap=dial?Math.min(s.valueGaugeGap,(a.w-diameter)*.5f):0;
        Line suffix=unit.isEmpty()?null:line(unit,BODY,s.unitSize,a.w,theme.textSecondary,false);
        boolean unitAtTop=suffix!=null&&"topRight".equals(s.unitPosition);
        boolean unitBesideValue=suffix!=null&&"valueRight".equals(s.unitPosition);
        float valueUnitGap=unitBesideValue?s.valueUnitGap:0;
        float titleWidth=unitAtTop?Math.max(0,a.w-suffix.width-s.titleUnitGap):a.w;
        Line heading=line(title,BODY,s.titleSize,titleWidth,theme.textSecondary,false);
        float numberWidth=Math.max(0,a.w-diameter-gaugeGap-(unitBesideValue?suffix.width+valueUnitGap:0));
        Line number=line(value,NUMERIC,s.valueSize,numberWidth,color,false);
        float headingHeight=Math.max(heading.height(),unitAtTop?suffix.height():0);
        float bodyHeight=Math.max(number.height(),diameter)+(bar?s.valueGaugeGap+s.barHeight:0);
        float bottomUnitHeight=suffix!=null&&!unitAtTop&&!unitBesideValue?suffix.height():0;
        float bottomGap=bottomUnitHeight==0?0:s.valueUnitGap;
        float scale=fit(a.h,headingHeight+s.titleValueGap+bodyHeight+bottomGap+bottomUnitHeight);
        heading.draw(c,a.x,a.y,headingHeight*scale,scale,false,titleWidth);
        if(unitAtTop)suffix.draw(c,a.x,a.y,headingHeight*scale,scale,true,a.w);
        float valueTop=a.y+(headingHeight+s.titleValueGap)*scale;
        float valueAreaHeight=Math.max(0,a.h-(headingHeight+s.titleValueGap+bottomGap+bottomUnitHeight)*scale);
        float valueHeight=Math.max(number.height(),diameter)*scale;
        float y=valueTop+Math.max(0,(valueAreaHeight-bodyHeight*scale)/2);
        if(dial)drawDial(c,a.x,y+(valueHeight-diameter*scale)/2,diameter*scale,position,fraction);
        float numberTop=y+(valueHeight-number.height()*scale)/2;
        float unitTop=0;
        if(unitBesideValue){
            float rowAscent=Math.max(number.ascent,suffix.ascent);
            float rowDescent=Math.max(number.descent,suffix.descent);
            float baseline=y+(valueHeight-(rowAscent+rowDescent)*scale)/2+rowAscent*scale;
            numberTop=baseline-number.ascent*scale;
            unitTop=baseline-suffix.ascent*scale;
        }
        number.draw(c,a.x+diameter*scale+gaugeGap,numberTop,number.height()*scale,scale,false,a.w-diameter*scale-gaugeGap);
        if(unitBesideValue){
            suffix.draw(c,a.x+diameter*scale+gaugeGap+number.width*scale+valueUnitGap*scale,
                unitTop,suffix.height()*scale,scale,false,a.w);
        }
        y+=valueHeight;
        if(bar){
            y+=s.valueGaugeGap*scale;
            float h=s.barHeight*scale;
            box(c,a.x,y,a.x+a.w,y+h,theme.stroke);
            if(position)box(c,a.x,y,a.x+a.w*fraction,y+h,theme.accent);
            if(zero>0&&zero<1)stroke(c,a.x+a.w*zero,y,a.x+a.w*zero,y+h,theme.textSecondary,1);
            y+=h;
        }
        if(bottomUnitHeight>0){
            float unitY=a.y+a.h-bottomUnitHeight*scale;
            suffix.draw(c,a.x,unitY,bottomUnitHeight*scale,scale,"bottomRight".equals(s.unitPosition),a.w);
        }
    }

    void tirePressure(Canvas c,OverviewLayout layout,int slot,String[] values,boolean bold){
        drawGroup(c,layout,slot,OverviewCardKind.TIRE_PRESSURE,UiText.t("타이어 공기압"),
            new String[]{"FL","FR","RL","RR"},values,null,"psi",2,bold,null);
    }
    void stability(Canvas c,OverviewLayout layout,int slot,int first,Telemetry.Sample[] samples){
        boolean enabled=first==Telemetry.PD_TCS_ENABLED;
        String[] labels=new String[3],values=new String[3];
        int[] colors=new int[3];
        for(int n=0;n<3;n++){
            Telemetry.Sample sample=samples[first+n];
            boolean known=sample.valid&&(!enabled||sample.value==0||sample.value==1);
            labels[n]=enabled?(n==0?"TCS":n==1?"ESC":UiText.t("ABS 경고등")):UiText.t(Telemetry.LABELS[first+n]);
            values[n]=!known?"—":enabled?(sample.value==1?"ON":"OFF"):UiText.t(sample.value==1?"작동":"비작동");
            boolean warning=enabled?(n<2?sample.value==0:sample.value==1):sample.value==1;
            colors[n]=known&&warning?theme.critical:theme.accent;
        }
        drawGroup(c,layout,slot,OverviewCardKind.STABILITY,UiText.t(enabled?"TCS · ESC · ABS 상태":"TCS · ESC · ABS"),
            labels,values,colors,"",1,!enabled,null);
    }
    void fuelEconomy(Canvas c,OverviewLayout layout,int slot,String trial,String cluster,String range){
        drawGroup(c,layout,slot,OverviewCardKind.FUEL_ECONOMY,UiText.t("연비"),
            new String[]{UiText.t("실주행 시험"),UiText.t("계기판"),UiText.t("주행가능")},
            new String[]{trial,cluster,range},null,"",1,false,null);
    }
    void dctTemperature(Canvas c,OverviewLayout layout,int slot,Telemetry.DctTemperatures dct,String odd,String even){
        drawGroup(c,layout,slot,OverviewCardKind.DCT_TEMPERATURE,UiText.t("DCT 온도"),
            dct.single?new String[]{UiText.t("온도")}:new String[]{UiText.t("홀수단"),UiText.t("짝수단")},
            dct.single?new String[]{odd}:new String[]{odd,even},
            new int[]{Float.isNaN(dct.oddC)?theme.textSecondary:theme.accent,Float.isNaN(dct.evenC)?theme.textSecondary:theme.accent},
            "",1,false,dct);
    }
    private void drawGroup(Canvas c,OverviewLayout layout,int slot,OverviewCardKind kind,String title,String[] labels,
            String[] values,int[] colors,String unit,int columns,boolean bold,Telemetry.DctTemperatures dct){
        OverviewCardStyle s=styles[kind.ordinal()];
        Area a=frame(c,layout,slot,s,theme.accent);
        if(a.w<=0||a.h<=0)return;
        float gaugeWidth=dct==null?0:Math.min(s.gaugeWidth,a.w*.32f);
        float gaugeGap=dct==null?0:Math.min(s.valueGaugeGap,(a.w-gaugeWidth)*.35f);
        float columnGap=Math.min(s.columnGap,a.w*.25f);
        float colWidth=(a.w-gaugeWidth-gaugeGap-(columns-1)*columnGap)/columns;
        float labelGap=Math.min(s.labelValueGap,colWidth*.15f);
        float labelWidth=(colWidth-labelGap)*(columns==1?.43f:.35f);
        float valueWidth=colWidth-labelWidth-labelGap;
        int rows=(values.length+columns-1)/columns;
        float[] heights=new float[rows];
        Line[] names=new Line[values.length],readings=new Line[values.length];
        for(int i=0;i<values.length;i++){
            names[i]=line(labels[i],BODY,s.labelSize,labelWidth,theme.textSecondary,false);
            readings[i]=line(values[i],kind==OverviewCardKind.STABILITY?BODY:NUMERIC,s.valueSize,valueWidth,
                colors==null?theme.accent:colors[i],bold);
            heights[i/columns]=Math.max(heights[i/columns],Math.max(names[i].height(),readings[i].height()));
        }
        Line heading=line(title,BODY,s.titleSize,a.w,theme.textSecondary,false);
        Line suffix=unit.isEmpty()?null:line(unit,BODY,s.unitSize,a.w,theme.textSecondary,false);
        Line lane=dct==null?null:line("L",BODY,s.labelSize,gaugeWidth/2,theme.textSecondary,false);
        float bodyHeight=(rows-1)*s.rowGap;
        for(float height:heights)bodyHeight+=height;
        if(lane!=null)bodyHeight=Math.max(bodyHeight,lane.height()+12);
        float scale=fit(a.h,heading.height()+s.titleValueGap+bodyHeight+(suffix==null?0:s.valueUnitGap+suffix.height()));
        heading.draw(c,a.x,a.y,heading.height()*scale,scale,false,a.w);
        float y=a.y+(heading.height()+s.titleValueGap)*scale;
        if(lane!=null)drawDctGauge(c,a.x+a.w-gaugeWidth,y,gaugeWidth,bodyHeight*scale,lane,s,scale,dct);
        for(int row=0;row<rows;row++){
            float height=heights[row]*scale;
            for(int col=0;col<columns;col++){
                int i=row*columns+col;
                if(i>=values.length)break;
                float x=a.x+col*(colWidth+columnGap);
                names[i].draw(c,x,y,height,scale,false,labelWidth);
                readings[i].draw(c,x+labelWidth+labelGap,y,height,scale,true,valueWidth);
            }
            y+=height+(row+1<rows?s.rowGap*scale:0);
        }
        if(suffix!=null)suffix.draw(c,a.x,y+s.valueUnitGap*scale,suffix.height()*scale,scale,false,a.w);
    }
    private void drawDctGauge(Canvas c,float x,float y,float w,float h,Line lane,OverviewCardStyle s,float scale,Telemetry.DctTemperatures dct){
        float half=w/2,laneHeight=lane.height()*scale,top=y+laneHeight,bottom=y+h;
        lane.draw(c,x,y,laneHeight,scale,false,half);
        if(!dct.single)line("R",BODY,s.labelSize,half,theme.textSecondary,false).draw(c,x+half,y,laneHeight,scale,false,half);
        box(c,x,top,x+w,bottom,theme.stroke);
        float inset=Math.min(1,Math.min(w,h)*.1f);
        box(c,x+inset,top+inset,x+w-inset,bottom-inset,theme.panel);
        dctFill(c,dct.oddC,x+inset,x+half-inset,top+inset,bottom-inset);
        if(!dct.single)dctFill(c,dct.evenC,x+half+inset,x+w-inset,top+inset,bottom-inset);
    }
    private void dctFill(Canvas c,float temp,float l,float r,float t,float b){
        if(Float.isNaN(temp)||r<=l||b<=t)return;
        float lo=Telemetry.DctTemperatures.MIN_C,hi=Telemetry.DctTemperatures.MAX_C;
        float fraction=Math.max(0,Math.min(1,(temp-lo)/(hi-lo)));
        float fill=Math.min(b-t,Math.max(1,fraction*(b-t)));
        box(c,l,b-fill,r,b,theme.accent);
    }
    private void drawDial(Canvas c,float x,float y,float size,boolean position,float fraction){
        if(size<1)return;
        float width=Math.min(3,size*.14f),radius=(size-width)/2,cx=x+size/2,cy=y+size/2;
        RectF arc=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(theme.stroke);
        c.drawArc(arc,150,240,false,paint);
        if(position){paint.setColor(theme.accent);c.drawArc(arc,150,240*fraction,false,paint);}
        paint.setStyle(Paint.Style.FILL);
        if(position){
            double angle=Math.toRadians(150+240*fraction);
            stroke(c,cx,cy,cx+(float)Math.cos(angle)*radius*.74f,cy+(float)Math.sin(angle)*radius*.74f,theme.textPrimary,Math.min(1.5f,width));
            paint.setColor(theme.accent);c.drawCircle(cx,cy,Math.min(2,radius*.25f),paint);
        }
    }
    private Area frame(Canvas c,OverviewLayout layout,int slot,OverviewCardStyle s,int accent){
        float x=layout.leftAt(slot),y=layout.topAt(slot),w=layout.widthAt(slot),h=layout.heightAt(slot);
        if(w>0&&h>0)surface.overviewCard(c,theme,paint,x,y,w,h,accent);
        return new Area(x+s.padding.left,y+s.padding.top,w-s.padding.left-s.padding.right,h-s.padding.top-s.padding.bottom);
    }
    private static float fit(float available,float desired){return desired>available?available/desired:1;}
    private void box(Canvas c,float l,float t,float r,float b,int color){
        if(r<=l||b<=t)return;
        paint.setColor(color);c.drawRect(l,t,r,b,paint);
    }
    private void stroke(Canvas c,float x,float y,float x2,float y2,int color,float width){
        paint.setColor(color);paint.setStrokeWidth(width);c.drawLine(x,y,x2,y2,paint);
    }
    private Line line(String text,String role,float base,float width,int color,boolean bold){
        Typeface face=resources==null?(theme.fontBold(role,bold)?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.font(role,bold);
        float size=theme.textSize(role,text,base,bold);
        paint.setTypeface(face);paint.setTextSize(size);
        size*=fit(width,paint.measureText(text));paint.setTextSize(size);
        Paint.FontMetrics metrics=paint.getFontMetrics();
        return new Line(text,face,size,-metrics.ascent,metrics.descent,paint.measureText(text),color);
    }
    private final class Line {
        final String text;
        final Typeface face;
        final float size,ascent,descent,width;
        final int color;
        Line(String text,Typeface face,float size,float ascent,float descent,float width,int color){
            this.text=text;this.face=face;this.size=size;this.ascent=ascent;this.descent=descent;this.width=width;this.color=color;
        }
        float height(){return ascent+descent;}
        void draw(Canvas c,float x,float y,float height,float scale,boolean right,float slotWidth){
            paint.setTypeface(face);paint.setTextSize(size*scale);paint.setColor(color);
            c.drawText(text,x+(right?slotWidth-width*scale:0),y+(height-height()*scale)/2+ascent*scale,paint);
        }
    }
    private static final class Area {
        final float x,y,w,h;
        Area(float x,float y,float w,float h){this.x=x;this.y=y;this.w=w;this.h=h;}
    }
}
