package com.veloster.dashboard.vehicle;

import com.veloster.dashboard.view.DashboardView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

/** Passive subscription to the same OEM broadcasts consumed by the home widget. */
public final class MediaReceiver {
    private static final String MODE_CHANGED="com.hkmc.intent.action.MODE_CHANGED";
    private final MediaState state;
    private Context registered;
    private BroadcastReceiver receiver;
    public MediaReceiver(MediaState state){this.state=state;}
    public synchronized void start(Context context){
        if(registered!=null)return;
        IntentFilter filter=new IntentFilter();for(String action:MediaState.ACTIONS)filter.addAction(action);
        filter.addAction(MODE_CHANGED);
        final BroadcastReceiver next=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            synchronized(MediaReceiver.this){if(receiver==this)receive(c,i);}
        }};
        receiver=next;
        try{
            context.registerReceiver(next,filter);registered=context;DashboardView.diagEvent("binding.media","registered");mediaSources("registered");
            try{
                Class<?> type=Class.forName("com.hkmc.mode.ModeManager");
                Object manager=type.getConstructor(Context.class).newInstance(context);
                state.mode(((Integer)type.getMethod("getCurrentMode").invoke(manager)).intValue());
            }catch(Exception e){android.util.Log.i("VehicleDash","Initial media mode unavailable; waiting for mode broadcast");}
        }
        catch(RuntimeException e){receiver=null;DashboardView.diagEvent("binding.media",e instanceof SecurityException?"permission denied":"error");mediaSources(e instanceof SecurityException?"permission denied":"error");android.util.Log.e("VehicleDash","Media receiver unavailable",e);}
    }
    public synchronized void stop(){
        if(registered==null)return;
        BroadcastReceiver old=receiver;receiver=null;
        try{registered.unregisterReceiver(old);DashboardView.diagEvent("binding.media","stopped");mediaSources("stopped");}
        catch(RuntimeException e){DashboardView.diagEvent("binding.media",e instanceof SecurityException?"permission denied":"error");mediaSources(e instanceof SecurityException?"permission denied":"error");android.util.Log.e("VehicleDash","Media receiver detach failed",e);}
        finally{registered=null;}
    }
    private void mediaSources(String status){
        DashboardView.diagEvent("binding.media.mode",status);
        for(int i=0;i<MediaState.ACTIONS.length;i++)DashboardView.diagEvent("binding.media."+i,status);
    }
    private void receive(Context context,Intent intent){
        if(intent==null)return;
        if(MODE_CHANGED.equals(intent.getAction())){
            DashboardView.diagEvent("binding.media.mode","received");
            try{if(intent.hasExtra("com.hkmc.mode.EXTRA_MODE_TYPE")){state.mode(intent.getIntExtra("com.hkmc.mode.EXTRA_MODE_TYPE",-1));DashboardView.diagEvent("binding.media.mode","processed");}
                else DashboardView.diagEvent("binding.media.mode","invalid");}
            catch(RuntimeException e){DashboardView.diagEvent("binding.media.mode","invalid");android.util.Log.e("VehicleDash","Malformed mode update",e);}
            return;
        }
        int source=MediaState.source(intent.getAction());if(source<0)return;
        DashboardView.diagEvent("binding.media."+source,"received");
        try{
            // Android Auto uses start_time/end_time; the other widget producers use audio_*.
            String position=source==4?"start_time":"audio_current_time";
            String duration=source==4?"end_time":"audio_total_time";
            state.update(source,intent.hasExtra("filename"),intent.getStringExtra("filename"),
                intent.hasExtra("artist"),intent.getStringExtra("artist"),
                intent.hasExtra(position),intent.getLongExtra(position,-1),
                intent.hasExtra(duration),intent.getLongExtra(duration,-1),
                intent.getBooleanExtra("invisible",false),SystemClock.elapsedRealtime());
            state.artwork(source,intent.hasExtra("artwork_uri"),intent.getStringExtra("artwork_uri"),
                intent.getByteArrayExtra("artwork_raw_image"),intent.getBooleanExtra("default",false));
            if(source==1&&intent.hasExtra("playing_state"))state.playback(source,intent.getBooleanExtra("playing_state",false)?1:0);
            else if(source==4&&intent.hasExtra("state")){
                int status=intent.getIntExtra("state",0);state.playback(source,status==2?1:status==1?0:-1);
            }else if(source!=1&&source!=4&&intent.hasExtra("playstate")){
                // OEM partial widget broadcasts use -1 for an omitted field.
                int status=intent.getIntExtra("playstate",-1);if(status==0||status==1)state.playback(source,status);
            }
            DashboardView.diagEvent("binding.media."+source,"processed");
        }catch(RuntimeException e){DashboardView.diagEvent("binding.media."+source,"invalid");android.util.Log.e("VehicleDash","Malformed media update",e);}
    }
}
