package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.MainActivity.SHARED_PREF_KEY;
import static com.micronet.a317modemupdater.MainActivity.UPDATED_KEY;
import static com.micronet.a317modemupdater.MainActivity.UPLOADED_KEY;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.micronet.a317modemupdater.database.LogDatabase;
import com.micronet.a317modemupdater.database.LogEntity;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Logger class is used to keep track of the results of trying to update the modem.
 */
public class Logger {

    private static final String TAG = "Updater-Logger";
    private static final String UPDATE_SUCCESSFUL = "com.micronet.dsc.resetrb.modemupdater.UPDATE_SUCCESSFUL_ACTION";
    private static AtomicBoolean uploadRunning;
    private static ExecutorService executorService;
    private static String imei;
    private static StringBuffer stringBuffer;
    static String serial;
    static LogDatabase db;

    public static boolean isPrepared = false;

    static synchronized void prepareLogger(Context context) {
        stringBuffer = new StringBuffer();
        db = Room.databaseBuilder(context, LogDatabase.class, "logdb").build();
        uploadRunning = new AtomicBoolean(false);
        getSerialAndImei(context);
        logDeviceInfo();
        isPrepared = true;
    }

    static synchronized void addLoggingInfo(String info) {
        String temp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime()) + ": "
                + info.replaceAll("\n+", "\n") + "\n";

        stringBuffer.append(temp);
    }

    static synchronized void uploadLogs(final Context context, boolean pass, @Nullable String summary) {
        uploadRunning.set(true);
        executorService = Executors.newFixedThreadPool(1);
        executorService.execute(getUploadRunnable(context, pass, summary));
    }

    public static synchronized void uploadSavedLogs(Context context) {
        if(isPrepared && !uploadRunning.get() && !allLogsUploaded()){
            uploadRunning.set(true);
            executorService = Executors.newFixedThreadPool(1);
            executorService.execute(getUploadRunnable(context, true, null));
        }else{
            if(allLogsUploaded()){
                Log.v(TAG, "All logs are already uploaded.");
            }else {
                Log.v(TAG, "Upload already running.");
            }
        }
    }

    // ---------------------
    //    Helper Methods
    // ---------------------

    private static Runnable getUploadRunnable(final Context context, final boolean pass, @Nullable final String summary) {
        return new Runnable() {
            @Override
            public void run() {
                if (summary != null) {
                    // Add result header and imei to top of file
                    stringBuffer.insert(0, summary + "IMEI: " + imei + "\n" + "Serial: " + serial + "\n");

                    // Save logging information just in case
                    db.logDao().insert(new LogEntity(stringBuffer.toString(), false, pass));
                }

                // Get a list of all logs not uploaded
                ArrayList<LogEntity> logs = (ArrayList<LogEntity>) db.logDao().getAllWhereNotUploaded();
                if(!logs.isEmpty()){
                    context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE).edit()
                            .putBoolean(UPLOADED_KEY, false).apply();
                }

                Log.i(TAG, String.format("There are %d logs to upload.", logs.size()));

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast toast = Toast.makeText(context, "Uploading logs. Do not power off.", Toast.LENGTH_LONG);
                        LinearLayout toastLayout = new LinearLayout(context);
                        toastLayout.setOrientation(LinearLayout.HORIZONTAL);
                        toastLayout.setBackgroundColor(Color.YELLOW);
                        ImageView warningView = new ImageView(context);
                        warningView.setImageResource(R.mipmap.warning);
                        toastLayout.addView(warningView);
                        TextView textView = new TextView(context);
                        textView.setTextColor(Color.BLACK);
                        textView.setTextSize(36f);
                        textView.setText("Uploading logs.\nDo not power off.");
                        toastLayout.addView(textView);
                        toast.setView(toastLayout);
                        toast.show();
                    }
                });

                for (LogEntity log : logs) {
                    // Initially backoff time 10 secs
                    int timeoutPeriod = 10000;
                    Log.i(TAG, "Trying to upload log with id " + log.id + " from " + log.dt + ".");

                    uploadHelper(log, timeoutPeriod);
                }

                if(allLogsUploaded()){
                    context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE).edit()
                            .putBoolean(UPLOADED_KEY, true).apply();

                    // If all logs are uploaded and the device is updated then send broadcast to clean up to resetrb.
                    if(isUpdated(context)){
                        Intent successfulUpdateIntent = new Intent(UPDATE_SUCCESSFUL);
                        context.sendBroadcast(successfulUpdateIntent);
                        Log.i(TAG, "All logs uploaded and firmware is updated. Sending broadcast to cleanup.");
                        uploadRunning.set(false);
                    }else{
                        Log.i(TAG, "All logs uploaded but firmware is not updated.");
                        uploadRunning.set(false);
                    }
                }else{
                    context.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE).edit()
                            .putBoolean("uploaded", false).apply();
                    uploadRunning.set(false);
                }
            }
        };
    }

    private static void uploadHelper(LogEntity log, int timeoutPeriod) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                // Check if there is internet connection
//                if (hasInternetConnection(context)) {
                    // Try to upload logs to dropbox
                    DropBox dropBox = new DropBox();
                    if (dropBox.uploadLogs(log.dt, serial, log.summary, log.pass)) {
                        Log.i(TAG, "Successfully uploaded logging information for log with id " + log.id + ".");
                        db.logDao().updateLogStatus(log.id);
                        return;
                    }
//                }

                try {
                    Thread.sleep(timeoutPeriod);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }
            }
            Log.i(TAG, "Not able to upload at this time. Trying again for log with id " + log.id + ".");

            // Adjust timeout period
            timeoutPeriod *= 2;
        }

        Log.e(TAG, "Not able to upload logging information for log with id " + log.id + ".");
    }

    private static void logDeviceInfo() {
        Logger.addLoggingInfo(String.format("OS Build string: %s", Build.FINGERPRINT));

        try {
            for (String key : getDeviceProperties()) {
                Logger.addLoggingInfo(key);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private static ArrayList<String> getDeviceProperties() throws IOException {
        ArrayList<String> deviceProperties = new ArrayList<>();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("/system/bin/getprop").getInputStream()));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            deviceProperties.add(line);
        }

        bufferedReader.close();

        return deviceProperties;
    }

    private static void getSerialAndImei(Context context) {
        serial = Build.SERIAL;

        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        imei = "UNKNOWN";
        try {
            if (telephonyManager.getDeviceId() != null) {
                imei = telephonyManager.getDeviceId();
            }
        } catch (SecurityException e) {
            Log.e(TAG, e.toString());
        }
    }

    private static synchronized boolean allLogsUploaded(){
        ArrayList<LogEntity> logs = (ArrayList<LogEntity>) db.logDao().getAllWhereNotUploaded();
        return logs.isEmpty();
    }

    private static synchronized boolean isUpdated(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(UPDATED_KEY, false);
    }
}
