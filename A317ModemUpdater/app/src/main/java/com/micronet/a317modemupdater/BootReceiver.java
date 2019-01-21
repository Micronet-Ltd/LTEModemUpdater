package com.micronet.a317modemupdater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "Updater-BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent != null) {
            String action = intent.getAction();
            if(action != null) {
                if(action.equals("android.intent.action.BOOT_COMPLETED")) {
                    Log.d(TAG, "Received BOOT_COMPLETED broadcast.");

                    Intent mainIntent = new Intent(context, MainActivity.class);
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(mainIntent);
                }
            }
        }
    }
}
