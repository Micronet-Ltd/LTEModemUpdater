package com.micronet.a317modemupdater.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.micronet.a317modemupdater.Logger;

public class NetworkStateReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkStateReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Received connectivity change intent");
                if(Logger.isPrepared) {
                    if(hasInternetConnection(context)) {
                        Log.d(TAG, "Connected");
                        Logger.uploadSavedLogs(context);
                    } else {
                        Log.d(TAG, "Not Connected");
                    }
                }
            }
        }).start();
    }

    private void handleReconnect(Context context) {
//        Toast.makeText(context, "Uploading logs", Toast.LENGTH_LONG).show();
        Logger.uploadSavedLogs(context);
    }

    private boolean hasInternetConnection(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager != null){
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

            if (networkInfo != null) { // connected to the internet
                return networkInfo.getType() == ConnectivityManager.TYPE_WIFI || networkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        }
        return false;
    }
}