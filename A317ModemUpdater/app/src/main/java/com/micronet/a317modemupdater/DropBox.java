package com.micronet.a317modemupdater;

import android.content.Context;
import android.util.Log;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.NetworkIOException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.WriteMode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Used to connect to dropbox and upload logs.
 */
class DropBox {

    private final String TAG = "Updater-DropBox";
    private final String ACCESS_TOKEN = "LPPT11VZzEAAAAAAAAAAU47-w7F3dzDyGLmL0IagOX5HsECjVqkVRUa6Rum2vGam";
    private DbxClientV2 client;

    DropBox(Context context) {
        // Create Dropbox client
        DbxRequestConfig config = DbxRequestConfig.newBuilder("A317ModemUpdater/" + BuildConfig.VERSION_NAME).build();
        client = new DbxClientV2(config, ACCESS_TOKEN);
    }

    boolean uploadLogs(String dt, String id, String data, boolean pass) {
        try {
            InputStream in = new ByteArrayInputStream(data.getBytes(Charset.forName("UTF-8")));
            FileMetadata metadata = client.files().uploadBuilder("/a317ModemUpdater/" + id + "/" + (pass ? "PASS " : "FAIL ") + dt + ".txt")
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

    boolean preUploadLog(String dt, String id) {
        try {
            InputStream in = new ByteArrayInputStream(("About to check modem firmware version.\nIf update is available then modem firmware"
                    + " will try to be updated.").getBytes(Charset.forName("UTF-8")));
            FileMetadata metadata = client.files().uploadBuilder("/a317ModemUpdater/" + id + "/PreCheck " + dt + ".txt")
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
