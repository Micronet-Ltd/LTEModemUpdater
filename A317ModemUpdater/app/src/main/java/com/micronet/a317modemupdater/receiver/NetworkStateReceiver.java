package com.micronet.a317modemupdater.receiver;

import android.app.Application;
import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.micronet.a317modemupdater.*;

import com.micronet.a317modemupdater.database.LogDatabase;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkStateReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkStateReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                Log.d(TAG, "Received connectivity change intent");
                if(canReachDropbox()) {
                    Log.d(TAG, "Connected");
                    handleReconnect(context);
                }
                else {
                    Log.d(TAG, "no connectivity");
                }
            }
        });
        t.start();
    }

    private void handleReconnect(Context context) {
//        Toast.makeText(context, "Uploading logs", Toast.LENGTH_LONG).show();
        Logger.uploadSavedLogs(context);
    }

    private boolean canReachDropbox() {
        try {
            HttpURLConnection urlc = (HttpURLConnection) (new URL("http://api.dropbox.com").openConnection());
            urlc.setRequestProperty("User-Agent", "Test");
            urlc.setRequestProperty("Connection", "close");
            urlc.setConnectTimeout(15000);
            urlc.connect();
            Log.d(TAG, String.format("Response code: %d", urlc.getResponseCode()));
            return (urlc.getResponseCode() != 404);
        } catch (IOException e) {
            Log.e(TAG, "Error checking Dropbox connection");
            Log.e(TAG, e.getMessage());
            return false;
        }
    }
}
