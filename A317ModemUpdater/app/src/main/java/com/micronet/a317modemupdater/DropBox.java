package com.micronet.a317modemupdater;

import android.os.Build;
import android.util.Log;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.NetworkIOException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Used to connect to dropbox and upload logs.
 */
class DropBox {

    private static final String TAG = "Updater-DropBox";
    private static final String ACCESS_TOKEN = "LPPT11VZzEAAAAAAAAAA4ynGYT6dCM7XhuMS0YJcgt4fkehBOmlVAJTb8jhRPj3w";
    private static final String id = Build.SERIAL;
    private static DbxClientV2 client;

    // Do not allow this class to be instantiated
    private DropBox() {}

    /**
     * Upload a precheck log to dropbox.
     * @param dt The datetime of the precheck.
     * @return Whether or not upload was successful.
     */
    synchronized static boolean uploadPreCheck(String dt) {
        // Input validation
        if (dt == null || dt.equals("")) {
            Log.d(TAG, "Invalid datetime for log.");
            return false;
        }

        // Try to upload log
        String path = "/" + id + "/" + "PreCheck " + dt + ".txt";
        return uploadLog(path, getByteArrayInputStream("About to check modem firmware version."));
    }

    /**
     * Upload a result log to Dropbox.
     * @param dt The datetime of the test.
     * @param data What happened during the test.
     * @param pass Whether the update was successful or not.
     * @return Whether or not upload was successful.
     */
    synchronized static boolean uploadResult(String dt, String data, boolean pass) {
        // Input validation
        if (dt == null || dt.equals("")) {
            Log.d(TAG, "Invalid datetime for log.");
            return false;
        } else if (data == null || data.equals("")) {
            Log.d(TAG, "Invalid data for log.");
            return false;
        }

        // Try to upload log
        String path = "/" + id + "/" + (pass ? "PASS " : "FAIL ") + dt + ".txt";
        return uploadLog(path, getByteArrayInputStream(data));
    }

    ///////////////////////////////
    // Helper methods
    ///////////////////////////////

    private static DbxClientV2 getClient() {
        if (client == null) {
            client = new DbxClientV2(DbxRequestConfig.newBuilder("LTEModemUpdater/" + BuildConfig.VERSION_NAME).build(), ACCESS_TOKEN);
            return client;
        } else {
            return client;
        }
    }

    private static InputStream getByteArrayInputStream(String data) {
        return new ByteArrayInputStream(data.getBytes(Charset.forName("UTF-8")));
    }

    private static boolean uploadLog(String path, InputStream in) {
        try {
            getClient().files().uploadBuilder(path)
                    .withMode(WriteMode.ADD)
                    .withAutorename(true).uploadAndFinish(in);
        } catch (NetworkIOException e) {
            Log.d(TAG, "Error: no network connection - " + e.toString());
            return false;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return false;
        }

        return true;
    }
}
