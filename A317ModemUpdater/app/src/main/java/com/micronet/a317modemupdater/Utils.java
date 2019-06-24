package com.micronet.a317modemupdater;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

class Utils {
    static final String UPDATE_SUCCESSFUL = "com.micronet.dsc.resetrb.modemupdater.UPDATE_SUCCESSFUL_ACTION";
    static final String UPDATED_KEY = "Updated";
    static final String UPLOADED_KEY = "Uploaded";
    static final String REBOOT_KEY = "Reboot";
    static final String SHARED_PREF_KEY = "LTEModemUpdater";

    // Make class not instantiable
    private Utils() {}

    static synchronized String getSerial(){
        return Build.SERIAL;
    }

    static synchronized String getImei(Context context){
        String imei = "UNKNOWN";
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        try {
            if (telephonyManager != null && telephonyManager.getDeviceId() != null) {
                imei = telephonyManager.getDeviceId();
            }
        } catch (SecurityException e) {
            // Log.e(TAG, e.toString());
        }
        return imei;
    }

    static synchronized void setUpdated(Context context, boolean updated) {
        context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE).edit().putBoolean(UPDATED_KEY, updated).apply();
    }

    static synchronized void setUploaded(Context context, boolean uploaded) {
        context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE).edit().putBoolean(UPLOADED_KEY, uploaded).apply();
    }

    static synchronized void setReboot(Context context, boolean reboot) {
        context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE).edit().putBoolean(REBOOT_KEY, reboot).apply();
    }

    static synchronized boolean isReboot(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(REBOOT_KEY, true);
    }

    static synchronized boolean isUpdated(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(UPDATED_KEY, false);
    }

    static synchronized boolean isUploaded(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(UPLOADED_KEY, false);
    }

    static synchronized String getCurrentDatetime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime());
    }

    static synchronized void sendUpdateSuccessfulBroadcast(Context context) {
        Intent successfulUpdateIntent = new Intent(UPDATE_SUCCESSFUL);
        context.sendBroadcast(successfulUpdateIntent);
    }

    static synchronized String runShellCommand(String[] commands) throws IOException {
        StringBuilder sb = new StringBuilder();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(commands).getInputStream()));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            sb.append(line);
        }

        bufferedReader.close();

        return sb.toString();
    }
}
