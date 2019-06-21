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
    private static final int NUM_UPLOAD_RETRIES = 15;
    private static final int UPLOAD_SLEEP_BETWEEN_ATTEMPTS = 60000;

    private static AtomicBoolean uploadRunning;
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
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.execute(getUploadRunnable(context, pass));
    }

    public static synchronized void uploadSavedLogs(Context context) {
        if(isPrepared.get() && !uploadRunning.get()){
            uploadLogs(context, true, null);
        }else{
            Log.v(TAG, "Upload already running.");
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
                } else {
                    setUploaded(context, true);
                    Log.d(TAG, "All logs already uploaded.");
                    return;
                }

                Log.i(TAG, String.format("There are %d logs to upload.", logs.size()));

                for (LogEntity log : logs) {
                    Log.i(TAG, "Trying to upload log with id " + log.id + " from " + log.dt + ".");
                    uploadHelper(log);
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

    private static void uploadHelper(LogEntity log) {
        // Try to upload log NUM_UPLOAD_RETIES times with UPLOAD_SLEEP_BETWEEN_ATTEMPTS sleeps between attempts.
        for (int i = 0; i < NUM_UPLOAD_RETRIES; i++) {
            // Try to upload logs to dropbox
            try {
                if (uploadResult(log.dt, log.summary, log.pass)) {
                    Log.i(TAG, "Successfully uploaded logging information for log with id " + log.id + ".");
                    if (db != null) {
                        db.logDao().updateLogStatus(log.id);
                    }
                    return;
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.toString());

                // Try to delete bad log from database
                if (db != null) {
                    db.logDao().deleteById(log.id);
                }
                return;
            }

            try {
                Thread.sleep(UPLOAD_SLEEP_BETWEEN_ATTEMPTS);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }

        // Logs couldn't be uploaded at this time, rely on network state receiver to upload when data connection is available.
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
