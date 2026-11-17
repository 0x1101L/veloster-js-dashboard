package com.veloster.dashboard.view;

import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.theme.ThemeSpec;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import com.veloster.i18n.UiText;

/** In-dashboard settings page; individual editors remain in their focused dialogs. */
public final class SettingsPageRenderer {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    public static int hit(float x,float y){
        if(x<36||x>764)return -1;
        if(y>=136&&y<212)return 0;
        if(y>=212&&y<=288)return 1;
        return -1;
    }

    public void draw(Canvas canvas,ThemeSpec theme,ThemeResources resources,String language,String themeName){
        text(canvas,resources,UiText.t("설정"),44,108,28,theme.textPrimary,true);
        paint.setColor(theme.panel);canvas.drawRect(36,136,764,288,paint);
        row(canvas,theme,resources,UiText.t("언어"),language,136);
        row(canvas,theme,resources,UiText.t("테마"),themeName,212);
        paint.setColor(theme.stroke);paint.setStrokeWidth(1);
        canvas.drawLine(60,212,740,212,paint);
    }

    private void row(Canvas canvas,ThemeSpec theme,ThemeResources resources,String label,String value,float top){
        text(canvas,resources,label,60,top+46,22,theme.textPrimary,false);
        paint.setColor(theme.textSecondary);paint.setTextSize(20);
        paint.setTypeface(resources==null?Typeface.DEFAULT:resources.fontForText(value,false));
        canvas.drawText(value,706-paint.measureText(value),top+45,paint);
        paint.setColor(theme.textSecondary);paint.setStrokeWidth(2);
        canvas.drawLine(732,top+32,738,top+38,paint);
        canvas.drawLine(738,top+38,732,top+44,paint);
    }

    private void text(Canvas canvas,ThemeResources resources,String value,float x,float y,float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);
        paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(value,bold));
        canvas.drawText(value,x,y,paint);
    }

}
