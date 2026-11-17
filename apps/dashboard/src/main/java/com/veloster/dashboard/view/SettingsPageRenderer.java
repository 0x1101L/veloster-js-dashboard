package com.veloster.dashboard.view;

import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemeSpec;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/** In-dashboard settings page; individual editors remain in their focused dialogs. */
public final class SettingsPageRenderer {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    public static int hit(float x,float y){
        if(x<112||x>688)return -1;
        if(y>=126&&y<=224)return 0;
        if(y>=246&&y<=344)return 1;
        return -1;
    }

    public void draw(Canvas canvas,ThemeSpec theme,ThemeResources resources,String language,String themeName){
        panel(canvas,theme,112,126,576,98);
        panel(canvas,theme,112,246,576,98);
        centered(canvas,resources,"SETTINGS / 설정",400,91,22,theme.textPrimary,true);
        text(canvas,resources,"Language / 언어",140,164,20,theme.accent,true);
        text(canvas,resources,language,140,197,16,theme.textPrimary,false);
        text(canvas,resources,"Theme / 테마",140,284,20,theme.accent,true);
        text(canvas,resources,themeName,140,317,16,theme.textPrimary,false);
    }

    private void panel(Canvas canvas,ThemeSpec theme,float x,float y,float width,float height){
        paint.setColor(theme.panel);canvas.drawRect(x,y,x+width,y+height,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(theme.stroke);
        canvas.drawRect(x,y,x+width,y+height,paint);paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.accent);canvas.drawRect(x,y,x+4,y+height,paint);
    }

    private void text(Canvas canvas,ThemeResources resources,String value,float x,float y,float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);
        paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(value,bold));
        canvas.drawText(value,x,y,paint);
    }

    private void centered(Canvas canvas,ThemeResources resources,String value,float centerX,float y,float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);
        paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(value,bold));
        canvas.drawText(value,centerX-paint.measureText(value)/2,y,paint);
    }
}
