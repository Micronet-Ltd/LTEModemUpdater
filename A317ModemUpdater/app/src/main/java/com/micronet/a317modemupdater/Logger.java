package com.micronet.a317modemupdater;

import android.annotation.SuppressLint;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Calendar;

class Logger {
    private static final String TAG = "Updater-Logger";
    private static StringBuffer stringBuffer;

    static synchronized void createNew(){
        stringBuffer = new StringBuffer();
    }

    static synchronized void addLoggingInfo(String info){
        String temp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime()) + ": "
                + info.replaceAll("\n+", "\n") + "\n";

        stringBuffer.append(temp);
    }

    static synchronized String getLog(){
        return stringBuffer.toString();
    }

    // Don't need to worry about the runtime permission because we are at API 15
    @SuppressLint("MissingPermission")
    static synchronized void uploadLog(final Context context){
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String temp = "UNKNOWN";
        if(telephonyManager.getDeviceId() != null){
            temp = telephonyManager.getDeviceId();
        }

        final String imei = temp;

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Sleep initial 15 secs
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                // Initially 10 secs
                int timeoutPeriod = 10000;
                DropBox dropBox = new DropBox(context);
                for(int i = 0; i < 5; i++){
                    for(int j = 0; j < 8; j++){
                        if(dropBox.uploadFile(imei, stringBuffer.toString())){
                            Log.d(TAG, "Successfully uploaded logging information.");
                            return;
                        }else{
                            Log.d(TAG, "Not able to upload logging information at this time.");
                            try {
                                Thread.sleep(timeoutPeriod);
                            } catch (InterruptedException e) {
                                Log.e(TAG, e.toString());
                            }
                        }
                    }

                    // Adjust timeout period
                    timeoutPeriod *= 2;
                }
            }
        }).start();
    }
}
