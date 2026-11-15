"""Exercise actual DCT service lifecycle without sending a vehicle request."""
from pathlib import Path
import subprocess, tempfile, unittest
from test_quick_entry_runtime import java_tools
ROOT=Path(__file__).resolve().parents[1]
STUBS={
'android/os/RemoteException.java':'package android.os; public class RemoteException extends Exception {public RemoteException(String s){super(s);}}',
'android/os/IBinder.java':'package android.os; public interface IBinder {int INTERFACE_TRANSACTION=1598968902; String getInterfaceDescriptor() throws RemoteException; boolean transact(int c,Parcel i,Parcel o,int f) throws RemoteException; boolean isBinderAlive();}',
'android/os/Binder.java':'package android.os; public class Binder implements IBinder {public volatile boolean alive=true; public boolean isBinderAlive(){return alive;} public String getInterfaceDescriptor(){return "com.hkmc.hscan.IHscanMicomService";} public boolean transact(int c,Parcel i,Parcel o,int f){return true;} protected boolean onTransact(int c,Parcel i,Parcel o,int f)throws RemoteException{return false;}}',
'android/os/Parcel.java':'package android.os; public class Parcel {public static Parcel obtain(){return new Parcel();} public void writeInterfaceToken(String s){} public void writeStrongBinder(IBinder b){} public void readException(){} public void recycle(){} public void writeByte(byte b){} public void writeByteArray(byte[] b){} public void writeString(String s){} public void enforceInterface(String s){} public byte readByte(){return 0;} public byte[] createByteArray(){return null;} public void writeNoException(){}}',
'android/os/ServiceManager.java':'package android.os; public class ServiceManager {public static volatile boolean missing;public static volatile Binder current;public static IBinder getService(String s){if(missing)return null; current=new Binder();return current;}}',
'android/os/SystemClock.java':'package android.os; public class SystemClock {public static long elapsedRealtime(){return System.currentTimeMillis();}}',
'android/util/Log.java':'package android.util; public class Log {public static int w(String a,String b){return 0;} public static int e(String a,String b,Throwable e){return 0;}}',
'com/veloster/dashboard/view/DashboardView.java':'package com.veloster.dashboard.view; public class DashboardView {public static volatile String status=""; public static void diagEvent(String k,String v){if(k.equals("dct.service"))status=v;} public static void jsDctTemperatureReply(byte[] b,long t){}}',
'Harness.java':'''import com.veloster.dashboard.vehicle.js.JsDctClient;import com.veloster.dashboard.view.DashboardView;import android.os.ServiceManager;public class Harness {static void await(String value,long timeout)throws Exception {long end=System.currentTimeMillis()+timeout;while(!value.equals(DashboardView.status)&&System.currentTimeMillis()<end)Thread.sleep(5);if(!value.equals(DashboardView.status))throw new AssertionError("expected "+value+" got "+DashboardView.status);}public static void main(String[] a)throws Exception{JsDctClient client=new JsDctClient(new JsDctClient.Gate(){public boolean open(){return false;}});ServiceManager.missing=true;client.start();await("error",2000);ServiceManager.missing=false;await("connected",6500);ServiceManager.missing=true;ServiceManager.current.alive=false;await("error",2000);ServiceManager.missing=false;await("connected",6500);client.stop();await("stopped",2000);ServiceManager.missing=true;client.start();await("error",2000);client.stop();await("stopped",2000);Thread.sleep(200);if(!DashboardView.status.equals("stopped"))throw new AssertionError("retry after stop");}}'''
}
class DctServiceStatusTest(unittest.TestCase):
 def test_connected_stopped_and_unavailable(self):
  java,javac=java_tools()
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp)
   for name,body in STUBS.items():
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
   source=ROOT/'apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/js/JsDctClient.java'
   result=subprocess.run([javac,'-d',str(root),*map(str,root.rglob('*.java')),str(source)],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
   result=subprocess.run([java,'-cp',str(root),'Harness'],capture_output=True,text=True,timeout=18)
   self.assertEqual(result.returncode,0,result.stderr)
