package com.veloster.carplayhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Private receiver restores opt-in shortcuts after boot/QuickBoot or package replacement. */
public final class QuickEntryReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){
        String action=intent==null?null:intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||"android.intent.action.MY_PACKAGE_REPLACED".equals(action)
                ||"android.intent.action.QUICKBOOT_POWERON".equals(action))
            QuickEntryService.restore(context);
    }
}
