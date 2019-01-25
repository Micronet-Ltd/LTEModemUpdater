package com.micronet.a317modemupdater;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.micronet.a317modemupdater.database.LogDatabase;
import com.micronet.a317modemupdater.database.LogEntity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Logger class is used to keep track of the results of trying to update the modem.
 */
class Logger {
    private static final String TAG = "Updater-Logger";
    private static StringBuffer stringBuffer;
    private static String imei;
    private static String serial;
    static LogDatabase db;
    private static ExecutorService executorService;

    /**
     * Initializes a new Logger instance.
     */
    static synchronized void createNew(Context context){
        stringBuffer = new StringBuffer();

        // Get the imei and serial number
        serial = Build.SERIAL;

        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        imei = "UNKNOWN";
        try{
            if(telephonyManager.getDeviceId() != null){
                imei = telephonyManager.getDeviceId();
            }
        }catch(SecurityException e){
            Log.e(TAG, e.toString());
        }

        db = Room.databaseBuilder(context, LogDatabase.class, "logdb").build();
    }

    /**
     * Logs the text with the datetime.
     * @param info The text that you want to log.
     */
    static synchronized void addLoggingInfo(String info){
        String temp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime()) + ": "
                + info.replaceAll("\n+", "\n") + "\n";

        stringBuffer.append(temp);
    }

    /**
     * Insert new log in database and upload all logs.
     * @param context
     * @param summary
     */
    static synchronized void uploadLogs(Context context, boolean pass, @Nullable String summary){
        executorService = Executors.newFixedThreadPool(1);
        executorService.execute(getUploadRunnable(context, pass, summary));
    }

    private static Runnable getUploadRunnable(final Context context, final boolean pass, @Nullable final String summary){
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if(summary != null){
                    // Add result header and imei to top of file
                    stringBuffer.insert(0, summary + "IMEI: " + imei + "\n" + "Serial: " + serial);

                    // Save logging information just in case
                    db.logDao().insert(new LogEntity(stringBuffer.toString(), false, pass));
                }

                // Get a list of all logs not uploaded
                ArrayList<LogEntity> logs = (ArrayList<LogEntity>) db.logDao().getAllWhereNotUploaded();

                Log.i(TAG, String.format("There are %d logs to upload.", logs.size()));

                for (LogEntity log: logs) {
                    // Initially backoff time 10 secs
                    int timeoutPeriod = 10000;
                    Log.i(TAG, "Trying to upload log with id " + log.id + " from " + log.dt + ".");

                    uploadHelper(log, timeoutPeriod, context, pass);
                }

            }
        };

        return runnable;
    }

    private static void uploadHelper(LogEntity log, int timeoutPeriod, Context context, boolean pass) {
        for(int i = 0; i < 5; i++){
            for(int j = 0; j < 5; j++){
                // Check if there is internet connection
                if(hasInternetConnection(context)){
                    // Try to upload logs to dropbox
                    DropBox dropBox = new DropBox(context);
                    if(dropBox.uploadLogs(log.dt, serial, log.summary, pass)){
                        Log.i(TAG, "Successfully uploaded logging information for log with id " + log.id + ".");
                        db.logDao().updateLogStatus(log.id);
                        return;
                    }
                }

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

    private static synchronized boolean hasInternetConnection(Context context){
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if (networkInfo != null) { // connected to the internet
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                // connected to wifi
                return true;
            } else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                // connected to the mobile provider's data plan
                return true;
            }
        }
        return false;
    }
}
