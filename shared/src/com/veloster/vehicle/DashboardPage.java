package com.veloster.vehicle;

/** Stable preference IDs and header hit targets in the dashboard's 800x480 space. */
public final class DashboardPage {
    public static final int OVERVIEW=0,F1=1,GAUGES=2,GRAPHS=3,PERFORMANCE=4;
    private static final int[] LEFT={144,250,328,418,18},WIDTH={100,72,84,110,120};
    private DashboardPage(){}
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
}
