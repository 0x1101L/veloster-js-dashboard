package com.veloster.dashboard.vehicle.js;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import com.veloster.dashboard.view.DashboardView;

/** JS TCU temperature read, one request at a time, while the activity and ignition are active. */
public final class JsDctClient implements Runnable {
    public interface Gate { boolean open(); }
    private static final String SERVICE="com.hkmc.micom.hscanmicomservice";
    private static final String SIFACE="com.hkmc.hscan.IHscanMicomService", LIFACE="com.hkmc.hscan.IHscanMicomListener";
    private final Gate gate;
    private final Object lock=new Object();
    private volatile boolean active;
    private Thread worker;
    private boolean restart;
    private byte[] reply;
    public JsDctClient(Gate gate){this.gate=gate;}
    public synchronized void start(){if(active)return;if(worker!=null){restart=true;return;}active=true;worker=new Thread(this,"js-dct-read");worker.start();}
    public synchronized void stop(){restart=false;active=false;synchronized(lock){lock.notifyAll();}if(worker!=null)worker.interrupt();}
    public void run(){
        IBinder service=null,token=new Binder(),listener=new Listener();boolean registered=false;
        try{
            service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,SERVICE);
            if(service==null||!SIFACE.equals(service.getInterfaceDescriptor()))throw new IllegalStateException("service unavailable");
            call(service,1,listener,token);registered=true;
            int failures=0;
            while(active){
                if(!gate.open()){Thread.sleep(500);continue;}
                synchronized(lock){reply=null;}
                request(service);
                byte[] value;
                synchronized(lock){long until=SystemClock.elapsedRealtime()+2500;while(active&&reply==null){long left=until-SystemClock.elapsedRealtime();if(left<=0)break;lock.wait(left);}value=reply;}
                if(!active)break;
                if(value!=null&&value.length>=36&&value[0]==0x62&&value[1]==1&&(value[2]&255)==0xa4){
                    if(gate.open())DashboardView.jsDctTemperatureReply(value,SystemClock.elapsedRealtime());
                    failures=0;
                }else if(++failures>=3){Log.w("JsDctClient","DCT request stopped after three missing, negative or short responses");break;}
                Thread.sleep(3500);
            }
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        catch(Throwable e){Log.e("JsDctClient","DCT read unavailable",e);}
        finally{if(registered)try{call(service,2,listener,token);}catch(Exception e){Log.e("JsDctClient","listener removal failed",e);}synchronized(this){active=false;worker=null;if(restart){restart=false;start();}}}
    }
    private static void call(IBinder s,int code,IBinder listener,IBinder token)throws RemoteException{
        Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInterfaceToken(SIFACE);in.writeStrongBinder(listener);in.writeStrongBinder(token);if(!s.transact(code,in,out,0))throw new RemoteException("transaction rejected");out.readException();}finally{in.recycle();out.recycle();}
    }
    private static void request(IBinder s)throws RemoteException{
        Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInterfaceToken(SIFACE);in.writeByte((byte)0xc7);in.writeByte((byte)0x40);in.writeByte((byte)0x80);in.writeByte((byte)0x40);in.writeByte((byte)0xa6);in.writeByteArray(new byte[]{0x22,0x01,(byte)0xa4});if(!s.transact(3,in,out,0))throw new RemoteException("request rejected");out.readException();}finally{in.recycle();out.recycle();}
    }
    private final class Listener extends Binder {
        protected boolean onTransact(int code,Parcel data,Parcel out,int flags)throws RemoteException{
            if(code==INTERFACE_TRANSACTION){if(out!=null)out.writeString(LIFACE);return true;}
            if(code<1||code>3)return super.onTransact(code,data,out,flags);
            data.enforceInterface(LIFACE);
            if(code==3){int source=data.readByte()&255;data.readByte();int kind=data.readByte()&255;byte[] r=data.createByteArray();
                if(source==0xc7&&kind==0xa6&&r!=null&&r.length>=3&&((r[0]==0x62&&r[1]==1&&(r[2]&255)==0xa4)||((r[0]&255)==0x7f&&r[1]==0x22&&(r[2]&255)!=0x78)))synchronized(lock){reply=r;lock.notifyAll();}}
            if(out!=null)out.writeNoException();return true;
        }
    }
}
