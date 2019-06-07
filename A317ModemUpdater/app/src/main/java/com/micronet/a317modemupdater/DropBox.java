package com.micronet.a317modemupdater;

import android.util.Log;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.NetworkIOException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.WriteMode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Used to connect to dropbox and upload logs.
 */
class DropBox {

    private final String TAG = "Updater-DropBox";
    private final String ACCESS_TOKEN = "LPPT11VZzEAAAAAAAAAA4ynGYT6dCM7XhuMS0YJcgt4fkehBOmlVAJTb8jhRPj3w";
    private DbxClientV2 client;

    DropBox() {
        client = new DbxClientV2(DbxRequestConfig.newBuilder("LTEModemUpdater/" + BuildConfig.VERSION_NAME).build(), ACCESS_TOKEN);
    }

    boolean uploadLogs(String dt, String id, String data, boolean pass) {
        try {
            InputStream in = new ByteArrayInputStream(data.getBytes(Charset.forName("UTF-8")));
            FileMetadata metadata = client.files().uploadBuilder("/" + id + "/" + (pass ? "PASS " : "FAIL ") + dt + ".txt")
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

    boolean uploadStatusBeforeUpdate(String dt, String id) {
        try {
            InputStream in = new ByteArrayInputStream(("About to check modem firmware version.\nIf update is available then modem firmware"
                    + " will try to be updated.").getBytes(Charset.forName("UTF-8")));
            FileMetadata metadata = client.files().uploadBuilder("/" + id + "/PreCheck " + dt + ".txt")
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
