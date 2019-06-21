package com.micronet.a317modemupdater.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.micronet.a317modemupdater.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkStateReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkStateReceiver";
    private static AtomicBoolean connected = new AtomicBoolean(false);

    @Override
    public void onReceive(final Context context, Intent intent) {
        boolean dataConnection = hasInternetConnection(context);

        // Do we have a connection now.
        if (!connected.get() && dataConnection) {
            connected.set(true);

            if(Logger.isPrepared.get()) {
                Logger.uploadSavedLogs(context);
            }
        } else if (connected.get() && !dataConnection) { // Did we lose our connection.
            connected.set(false);
        }
    }

    private boolean hasInternetConnection(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager != null){
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                // Make sure you also have a data connection to the internet.
                if (!networkInfo.isConnected()) {
                    return false;
                }

                return networkInfo.getType() == ConnectivityManager.TYPE_WIFI || networkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        }
        return false;
    }
}