package com.veloster.vehicle;

/** Observable facts only; service presence is not a communication success. */
public final class JsDiagState {
    private int cluster=-1,tbt=-1,micom=-1,nav=-1,navValue=-1,navNotice=-1,hscan=-1;
    private int wifiFeature=-1,wifiService=-1,wifiState=-1,wifiWatch=-1;
    public static final class Snapshot {
        public final int cluster,tbt,micom,nav,navValue,navNotice,hscan,wifiFeature,wifiService,wifiState,wifiWatch;
        private Snapshot(int c,int t,int m,int n,int nv,int nn,int h,int wf,int ws,int w,int ww){
            cluster=c;tbt=t;micom=m;nav=n;navValue=nv;navNotice=nn;hscan=h;wifiFeature=wf;wifiService=ws;wifiState=w;wifiWatch=ww;
        }
    }
    public synchronized void reset(){cluster=tbt=micom=nav=navValue=navNotice=hscan=wifiFeature=wifiService=wifiState=wifiWatch=-1;}
    public synchronized void wifi(int feature,int service,int state,int watch){wifiFeature=feature;wifiService=service;wifiState=state;wifiWatch=watch;}
    public synchronized void hscan(int value){hscan=value;}
    public synchronized void event(String name,String detail){
        if(name==null||detail==null)return;
        if("class".equals(name)&&"com.hkmc.cluster.NavigationCluster".equals(detail))cluster=1;
        else if("unavailable".equals(name)&&detail.startsWith("com.hkmc.cluster.NavigationCluster "))cluster=0;
        else if("method".equals(name)&&detail.contains("updateTBTInfo"))tbt=1;
        else if("micom.service".equals(name))micom=detail.startsWith("present")?1:0;
        else if("micom.service.error".equals(name))micom=2;
        else if("navigation.observer".equals(name)){nav=1;navNotice=0;}
        else if("navigation.observer.error".equals(name)||"navigation.state.error".equals(name)){nav=2;navValue=-1;}
        else if("navigation.observer.stopped".equals(name)){nav=0;navValue=-1;navNotice=-1;}
        else if("navigation.notification".equals(name))navNotice=1;
        else if("navigation.state_destination".equals(name)){
            try{navValue=Integer.parseInt(detail);nav=1;}catch(NumberFormatException ignored){navValue=-1;}
        }
        else if("navigation.state".equals(name))navValue=-1;
    }
    public synchronized Snapshot snapshot(){return new Snapshot(cluster,tbt,micom,nav,navValue,navNotice,hscan,wifiFeature,wifiService,wifiState,wifiWatch);}
}
