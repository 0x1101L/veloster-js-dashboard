package com.veloster.dashboard.debugger;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/** One-shot capability check; never writes pixels and never interprets navigation. */
public final class CarPlayScreenProbe {
    private CarPlayScreenProbe() {}
    public static String run(Context context){
        try {
            if(context.checkCallingOrSelfPermission("android.permission.READ_FRAME_BUFFER") != PackageManager.PERMISSION_GRANTED)
                return "permission_denied";
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager==null)return "unavailable";
            List<ActivityManager.RunningTaskInfo> tasks=manager.getRunningTasks(1);
            if(tasks==null||tasks.isEmpty()||tasks.get(0).topActivity==null)return "unavailable";
            if(!"com.daudio.app.dipo".equals(tasks.get(0).topActivity.getPackageName()))return "wrong_foreground";
            Object result=Class.forName("android.view.Surface").getMethod("screenshot",Integer.TYPE,Integer.TYPE)
                    .invoke(null,Integer.valueOf(800),Integer.valueOf(480));
            if(!(result instanceof Bitmap))return "unavailable";
            Bitmap bitmap=(Bitmap)result;
            try{return bitmap.getWidth()>0&&bitmap.getHeight()>0?"captured":"unavailable";}
            finally{bitmap.recycle();}
        } catch (SecurityException e){return "permission_denied";}
        catch (InvocationTargetException e){return e.getCause() instanceof SecurityException?"permission_denied":"error";}
        catch (Exception e){return "error";}
        catch (LinkageError e){return "unavailable";}
    }
}
