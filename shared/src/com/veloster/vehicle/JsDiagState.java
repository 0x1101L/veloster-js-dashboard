package com.veloster.vehicle;

/** Observable facts only; service presence is not a communication success. */
public final class JsDiagState {
    private int cluster=-1,tbt=-1,micom=-1,nav=-1,navValue=-1,navNotice=-1,hscan=-1;
    private int wifiFeature=-1,wifiService=-1,wifiState=-1,wifiWatch=-1;
    private int clusterService=-1,routeWatch=-1,routeNotice=-1,routeValue;
    private int routeSource=-1,routeError,navError;
    private boolean navValueKnown;
    private long routeCount,routeInvalidCount,navQueryCount,navNoticeCount;
    private long routeAt=-1,navAt=-1,navNoticeAt=-1;
    public static final class Snapshot {
        public final int cluster,tbt,micom,nav,navValue,navNotice,hscan,wifiFeature,wifiService,wifiState,wifiWatch;
        public final int clusterService,routeWatch,routeNotice,routeValue;
        public final int routeSource,routeError,navError;
        public final boolean navValueKnown;
        public final long routeCount,routeInvalidCount,navQueryCount,navNoticeCount,routeAgeMs,navAgeMs,navNoticeAgeMs;
        private Snapshot(JsDiagState s,long now){
            cluster=s.cluster;tbt=s.tbt;micom=s.micom;nav=s.nav;navValue=s.navValue;navNotice=s.navNotice;hscan=s.hscan;
            wifiFeature=s.wifiFeature;wifiService=s.wifiService;wifiState=s.wifiState;wifiWatch=s.wifiWatch;
            clusterService=s.clusterService;routeWatch=s.routeWatch;routeNotice=s.routeNotice;routeValue=s.routeValue;
            routeSource=s.routeSource;routeError=s.routeError;navError=s.navError;navValueKnown=s.navValueKnown;
            routeCount=s.routeCount;routeInvalidCount=s.routeInvalidCount;navQueryCount=s.navQueryCount;navNoticeCount=s.navNoticeCount;
            routeAgeMs=age(s.routeAt,now);navAgeMs=age(s.navAt,now);navNoticeAgeMs=age(s.navNoticeAt,now);
        }
    }
    private static long age(long at,long now){return at<0||now<at?-1:now-at;}
    public synchronized void reset(){
        cluster=tbt=micom=nav=navValue=navNotice=hscan=wifiFeature=wifiService=wifiState=wifiWatch=clusterService=routeWatch=routeNotice=routeSource=-1;
        routeValue=routeError=navError=0;navValueKnown=false;
        routeCount=routeInvalidCount=navQueryCount=navNoticeCount=0;routeAt=navAt=navNoticeAt=-1;
    }
    public synchronized void wifi(int feature,int service,int state,int watch){wifiFeature=feature;wifiService=service;wifiState=state;wifiWatch=watch;}
    public synchronized void hscan(int value){hscan=value;}
    public synchronized void event(String name,String detail){event(name,detail,-1);}
    public synchronized void event(String name,String detail,long now){
        if(name==null||detail==null)return;
        if("class".equals(name)&&"com.hkmc.cluster.NavigationCluster".equals(detail))cluster=1;
        else if("unavailable".equals(name)&&detail.startsWith("com.hkmc.cluster.NavigationCluster "))cluster=0;
        else if("method".equals(name)&&detail.contains("updateTBTInfo"))tbt=1;
        else if("micom.service".equals(name))micom=detail.startsWith("present")?1:0;
        else if("micom.service.error".equals(name))micom=2;
        else if("cluster.service".equals(name))clusterService=detail.startsWith("present")?1:detail.startsWith("descriptor mismatch")?2:0;
        else if("cluster.service.error".equals(name))clusterService=2;
        else if("navigation.route.starting".equals(name)){routeWatch=-1;routeNotice=0;routeValue=0;routeSource=-1;routeAt=-1;routeError=0;}
        else if("navigation.route.observer".equals(name)){routeWatch=1;if(routeNotice<0&&routeError==0)routeNotice=0;}
        else if("navigation.route.observer.error".equals(name)){routeWatch=2;routeNotice=-1;routeValue=0;routeSource=-1;routeAt=-1;routeError=detail.endsWith("SecurityException")?1:3;}
        else if("navigation.route.stopped".equals(name)){routeWatch=0;routeNotice=-1;routeValue=0;routeSource=-1;routeAt=-1;}
        else if("navigation.route.source".equals(name))routeSource="sticky".equals(detail)?1:"broadcast".equals(detail)?0:-1;
        else if("navigation.route.invalid".equals(name)){routeNotice=-1;routeValue=0;routeSource=-1;routeAt=-1;routeError=2;routeInvalidCount++;}
        else if("navigation.route_state".equals(name)){
            try{routeValue=Integer.parseInt(detail);routeNotice=1;routeCount++;routeAt=now;routeError=0;}
            catch(NumberFormatException ignored){routeValue=0;routeNotice=-1;routeSource=-1;routeAt=-1;routeError=2;routeInvalidCount++;}
        }
        else if("navigation.observer".equals(name)){nav=1;navNotice=0;navError=0;navValueKnown=false;navAt=navNoticeAt=-1;}
        else if("navigation.observer.error".equals(name)||"navigation.state.error".equals(name)){nav=2;navValue=-1;navValueKnown=false;navAt=-1;navError=detail.endsWith("SecurityException")?1:3;}
        else if("navigation.observer.stopped".equals(name)){nav=0;navValue=-1;navNotice=-1;navValueKnown=false;navAt=navNoticeAt=-1;}
        else if("navigation.notification".equals(name)){navNotice=1;navNoticeCount++;navNoticeAt=now;}
        else if("navigation.state_destination".equals(name)){
            try{navValue=Integer.parseInt(detail);nav=1;navValueKnown=true;navQueryCount++;navAt=now;navError=0;}
            catch(NumberFormatException ignored){navValue=-1;navValueKnown=false;navAt=-1;navError=2;}
        }
        else if("navigation.state".equals(name)){navValue=-1;navValueKnown=false;navAt=-1;}
    }
    public synchronized Snapshot snapshot(){return snapshot(-1);}
    public synchronized Snapshot snapshot(long now){return new Snapshot(this,now);}
}
