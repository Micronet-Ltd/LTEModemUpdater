package com.micronet.a317modemupdater;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
public class Logger {

    private static final String TAG = "Updater-Logger";
    private static StringBuffer stringBuffer;
    private static String imei;
    static String serial;
    static LogDatabase db;
    private static ExecutorService executorService;
    private static Context context;

    private static final String UPDATE_SUCCESSFUL = "com.micronet.dsc.resetrb.modemupdater.UPDATE_SUCCESSFUL";

    /**
     * Initializes a new Logger instance.
     */
    static synchronized void createNew(Context context) {
        Logger.context = context;
        stringBuffer = new StringBuffer();

        // Get the imei and serial number
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

        db = Room.databaseBuilder(context, LogDatabase.class, "logdb").build();
    }

    /**
     * Logs the text with the datetime.
     *
     * @param info The text that you want to log.
     */
    static synchronized void addLoggingInfo(String info) {
        // Note: The tag being separate is intentional.  We want to filter stuff being added.
        Log.d("addLoggingInfo", info);
        String temp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime()) + ": "
                + info.replaceAll("\n+", "\n") + "\n";

        showLog(info);
        stringBuffer.append(temp);
    }


    private static void showLog(final String log) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(context, log, Toast.LENGTH_SHORT).show();
            }
        }).start();
    }

    /**
     * Insert new log in database and upload all logs.
     */
    public static synchronized void uploadLogs(final Context context, boolean pass, @Nullable String summary) {
        Log.d(TAG, "About to toast");

//        Context applicationContext = context.getApplicationContext();
//        Toast toast = Toast.makeText(applicationContext, "Uploading logs. Do not power off.", Toast.LENGTH_LONG);
//        LinearLayout toastLayout = new LinearLayout(applicationContext);
//        toastLayout.setOrientation(LinearLayout.HORIZONTAL);
//        toastLayout.setBackgroundColor(Color.YELLOW);
//        ImageView warningView = new ImageView(applicationContext);
//        warningView.setImageResource(R.mipmap.warning);
//        toastLayout.addView(warningView);
//        TextView textView = new TextView(applicationContext);
//        textView.setText("Uploading logs. Do not power off.");
//        toastLayout.addView(textView);
//        toast.setView(toastLayout);
//        toast.show();
        Log.d(TAG, "toasted");
        executorService = Executors.newFixedThreadPool(1);
        executorService.execute(getUploadRunnable(context, pass, summary));
    }

    private static Runnable getUploadRunnable(final Context context, final boolean pass, @Nullable final String summary) {
        Runnable runnable = new Runnable() {
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
                    context.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE).edit()
                            .putBoolean("uploaded", false).apply();
                }

                Log.i(TAG, String.format("There are %d logs to upload.", logs.size()));

                Log.d(TAG, "Making toast");
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "In toast runnable");
                        Toast toast = Toast.makeText(context, "Uploading logs. Do not power off.", Toast.LENGTH_LONG);
                        LinearLayout toastLayout = new LinearLayout(context);
                        toastLayout.setOrientation(LinearLayout.HORIZONTAL);
                        toastLayout.setBackgroundColor(Color.YELLOW);
                        ImageView warningView = new ImageView(context);
                        warningView.setImageResource(R.mipmap.warning);
                        toastLayout.addView(warningView);
                        TextView textView = new TextView(context);
                        textView.setTextColor(Color.BLACK);
                        textView.setTextSize(72f);
                        textView.setText("Uploading logs. Do not power off.");
                        toastLayout.addView(textView);
                        toast.setView(toastLayout);
                        toast.show();
                    }
                });

                for (LogEntity log : logs) {
                    // Initially backoff time 10 secs
                    int timeoutPeriod = 10000;
                    Log.i(TAG, "Trying to upload log with id " + log.id + " from " + log.dt + ".");

                    uploadHelper(log, timeoutPeriod, context);
                }

                if(allLogsUploaded()){
                    context.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE).edit()
                            .putBoolean("uploaded", true).apply();

                    // If all logs are uploaded and the device is updated then send broadcast to clean up to resetrb.
                    if(isUpdated(context)){
                        Intent successfulUpdateIntent = new Intent(UPDATE_SUCCESSFUL);
                        context.sendBroadcast(successfulUpdateIntent);
                        Log.i(TAG, "All logs uploaded and firmware is updated. Sending broadcast to cleanup.");
                    }else{
                        Log.i(TAG, "All logs uploaded but firmware is not updated.");
                    }
                }else{
                    context.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE).edit()
                            .putBoolean("uploaded", false).apply();
                }
            }
        };

        return runnable;
    }

    public static void uploadHelper(LogEntity log, int timeoutPeriod, final Context context) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                // Check if there is internet connection
//                if (hasInternetConnection(context)) {
                    // Try to upload logs to dropbox
                    DropBox dropBox = new DropBox(context);
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

    public static synchronized boolean hasInternetConnection(Context context) {
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

    private static synchronized boolean allLogsUploaded(){
        ArrayList<LogEntity> logs = (ArrayList<LogEntity>) db.logDao().getAllWhereNotUploaded();
        return logs.isEmpty();
    }

    private static synchronized boolean isUpdated(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("updated", false);
    }
}
