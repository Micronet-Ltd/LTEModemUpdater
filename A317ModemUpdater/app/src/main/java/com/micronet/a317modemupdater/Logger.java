package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.DropBox.uploadResult;
import static com.micronet.a317modemupdater.Utils.getCurrentDatetime;
import static com.micronet.a317modemupdater.Utils.getImei;
import static com.micronet.a317modemupdater.Utils.getSerial;
import static com.micronet.a317modemupdater.Utils.isUpdated;
import static com.micronet.a317modemupdater.Utils.sendUpdateSuccessfulBroadcast;
import static com.micronet.a317modemupdater.Utils.setUploaded;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;

import com.micronet.a317modemupdater.database.LogDatabase;
import com.micronet.a317modemupdater.database.LogEntity;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Logger class is used to keep track of the results of trying to update the modem.
 */
public class Logger {

    private static final String TAG = "Updater-Logger";
    private static AtomicBoolean uploadRunning;
    private static ExecutorService executorService;
    private static StringBuffer stringBuffer;
    static LogDatabase db;

    public static AtomicBoolean isPrepared = new AtomicBoolean(false);

    static synchronized void prepareLogger(Context context) {
        db = Room.databaseBuilder(context, LogDatabase.class, "logdb").build();
        stringBuffer = new StringBuffer();
        uploadRunning = new AtomicBoolean(false);

        logDeviceInformation();
        isPrepared.set(true);
    }

    static synchronized void addLoggingInfo(String info) {
        if (info != null && !info.equals("") && stringBuffer != null) {
            String temp = getCurrentDatetime() + ": " + info.replaceAll("\n+", "\n") + "\n";
            stringBuffer.append(temp);
        }
    }

    static synchronized void uploadLogs(Context context, boolean pass, @Nullable String summary) {
        // Insert new log entity to database
        if (summary != null && !summary.equals("")) {
            // Insert summary and device information
            if (stringBuffer != null) {
                stringBuffer.insert(0, summary + "IMEI: " + getImei(context) + "\n" + "Serial: " + getSerial() + "\n");
            }

            // Insert in database
            if (db != null) {
                db.logDao().insert(new LogEntity(stringBuffer.toString(), false, pass));
            }
        }

        // Start trying to upload logs
        uploadRunning.set(true);
        executorService = Executors.newFixedThreadPool(1);
        executorService.execute(getUploadRunnable(context, pass));
    }

    public static synchronized void uploadSavedLogs(Context context) {
        if(isPrepared.get() && !uploadRunning.get() && !allLogsUploaded()){
            uploadLogs(context, true, null);
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

    private static Runnable getUploadRunnable(final Context context, final boolean pass) {
        return new Runnable() {
            @Override
            public void run() {
                // Get a list of all logs not uploaded
                ArrayList<LogEntity> logs = new ArrayList<>();
                if (db != null && db.logDao().getAllNotUploaded() != null) {
                    logs = (ArrayList<LogEntity>) db.logDao().getAllNotUploaded();
                }

                if(!logs.isEmpty()){
                    setUploaded(context, false);
                }

                Log.i(TAG, String.format("There are %d logs to upload.", logs.size()));

                for (LogEntity log : logs) {
                    // Initially backoff time 10 secs
                    int timeoutPeriod = 10000;
                    Log.i(TAG, "Trying to upload log with id " + log.id + " from " + log.dt + ".");

                    uploadHelper(log, timeoutPeriod);
                }

                // Check if all logs where successfully updated
                if(allLogsUploaded()){
                    setUploaded(context, true);

                    // If all logs are uploaded and the device is updated then send broadcast to clean up to resetrb.
                    if(isUpdated(context)){
                        sendUpdateSuccessfulBroadcast(context);
                        Log.i(TAG, "All logs uploaded and firmware is updated. Sending broadcast to cleanup.");
                    }else{
                        Log.i(TAG, "All logs uploaded but firmware is not updated.");
                    }
                } else {
                    // All logs not successfully updated
                    setUploaded(context, false);
                }

                uploadRunning.set(false);
            }
        };
    }

    private static void uploadHelper(LogEntity log, int timeoutPeriod) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                    // Try to upload logs to dropbox
                    if (uploadResult(log.dt, log.summary, log.pass)) {
                        Log.i(TAG, "Successfully uploaded logging information for log with id " + log.id + ".");
                        if (db != null) {
                            db.logDao().updateLogStatus(log.id);
                        }
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

    private static void logDeviceInformation() {
        Logger.addLoggingInfo(String.format("OS Build string: %s", Build.FINGERPRINT));

        try {
            for (String property : getDeviceProperties()) {
                Logger.addLoggingInfo(property);
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

    private static synchronized boolean allLogsUploaded(){
        ArrayList<LogEntity> logs = new ArrayList<>();
        if (db != null && db.logDao().getAllNotUploaded() != null) {
            logs = (ArrayList<LogEntity>) db.logDao().getAllNotUploaded();
        }

        return logs.isEmpty();
    }
}
