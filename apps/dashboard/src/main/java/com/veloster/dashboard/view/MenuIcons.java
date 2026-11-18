package com.veloster.dashboard.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Canvas icons keep menu rendering independent of the installed font. */
final class MenuIcons {
    private static final float SCALE=0.80f;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dial=new RectF(2,3,22,23);

    void draw(Canvas c,int page,float centerX,float centerY,int color){
        c.save();
        c.translate(centerX,centerY);
        c.scale(SCALE,SCALE);
        c.translate(-12,-12);
        ink.setColor(color);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(2);
        switch(page){
        case DashboardPage.PERFORMANCE:
            c.drawCircle(12,14,8,ink);
            line(c,9,2,15,2);line(c,12,2,12,6);line(c,18,7,21,4);
            line(c,12,14,12,9);line(c,12,14,16,14);
            break;
        case DashboardPage.OVERVIEW:
            for(int y=3;y<=14;y+=11)for(int x=3;x<=14;x+=11)c.drawRect(x,y,x+7,y+7,ink);
            break;
        case DashboardPage.F1:
            line(c,4,3,4,22);c.drawRect(4,3,20,15,ink);
            ink.setStyle(Paint.Style.FILL);
            for(int row=0;row<3;row++)for(int col=0;col<4;col++)if((row+col)%2==0){
                int x=4+4*col,y=3+4*row;
                c.drawRect(x,y,x+4,y+4,ink);
            }
            break;
        case DashboardPage.GAUGES:
            c.drawArc(dial,180,180,false,ink);
            line(c,2,13,2,19);line(c,22,13,22,19);line(c,2,19,22,19);
            line(c,12,14,17,8);c.drawCircle(12,14,1,ink);
            break;
        case DashboardPage.GRAPHS:
            line(c,3,3,3,21);line(c,3,21,22,21);
            line(c,6,16,10,10);line(c,10,10,15,13);line(c,15,13,21,5);
            break;
        case DashboardPage.SETTINGS:
            c.drawCircle(12,12,7,ink);c.drawCircle(12,12,2.5f,ink);
            ink.setStrokeWidth(3);
            line(c,12,2,12,6);line(c,12,18,12,22);line(c,2,12,6,12);line(c,18,12,22,12);
            line(c,5,5,8,8);line(c,16,16,19,19);line(c,5,19,8,16);line(c,16,8,19,5);
            break;
        case DashboardPage.BACK:
            line(c,3,12,21,12);line(c,3,12,11,4);line(c,3,12,11,20);
            break;
        }
        c.restore();
    }

    private void line(Canvas c,float x,float y,float toX,float toY){c.drawLine(x,y,toX,toY,ink);}
}
