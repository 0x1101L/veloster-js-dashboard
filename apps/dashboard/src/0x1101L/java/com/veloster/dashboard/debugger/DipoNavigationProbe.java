package com.veloster.dashboard.debugger;

import android.content.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Read-only polling of OEM DiPO guidance state; it does not provide turn geometry. */
public final class DipoNavigationProbe {
    public interface Sink { void event(String name,String detail); }
    private static final long POLL_MS=5000;
    private final Context context;
    private final Sink sink;
    private volatile boolean active;
    private Thread worker;

    public DipoNavigationProbe(Context context,Sink sink){
        if(context==null||sink==null)throw new IllegalArgumentException("context and sink required");
        this.context=context;this.sink=sink;
    }

    public void inspectOnce(){
        try{
            Object manager=context.getSystemService("dipo");
            if(manager==null){sink.event("navigation.dipo.service","unavailable");return;}
            Method getStat=manager.getClass().getMethod("getCurrentDiPOStat");
            Method getMode=manager.getClass().getMethod("getCurrentDiPOMode");
            Object stat=getStat.invoke(manager);
            Object mode=getMode.invoke(manager);
            String turn="unknown(mode-null)";
            if(mode!=null){
                Method getTurn=mode.getClass().getMethod("getTurnByTurn");
                turn=String.valueOf(getTurn.invoke(mode));
            }
            sink.event("navigation.dipo.service","present");
            sink.event("navigation.dipo.status","stat="+String.valueOf(stat)+" turnByTurn="+turn);
        }catch(InvocationTargetException e){
            Throwable cause=e.getCause();
            sink.event("navigation.dipo.error",cause==null?e.getClass().getSimpleName():cause.getClass().getSimpleName());
        }catch(Exception e){sink.event("navigation.dipo.error",e.getClass().getSimpleName());}
        catch(LinkageError e){sink.event("navigation.dipo.error",e.getClass().getSimpleName());}
    }

    public synchronized void start(){
        if(active)return;
        active=true;
        worker=new Thread(new Runnable(){public void run(){
            while(active){
                inspectOnce();
                try{Thread.sleep(POLL_MS);}catch(InterruptedException e){if(!active)return;}
            }
        }},"js-dipo-state");
        worker.setDaemon(true);worker.start();
    }

    public synchronized void close(){
        active=false;
        Thread current=worker;worker=null;
        if(current!=null)current.interrupt();
    }
}
