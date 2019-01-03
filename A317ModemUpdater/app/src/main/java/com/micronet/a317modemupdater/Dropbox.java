package com.micronet.a317modemupdater;

import android.util.Log;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import java.io.InputStream;

class Dropbox {
    private static final String TAG = "Updater-Dropbox";
    private static final String ACCESS_TOKEN = "";
    private DbxClientV2 client;

    Dropbox(){
        // Create Dropbox client
        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
        client = new DbxClientV2(config, ACCESS_TOKEN);
    }

    boolean upload(InputStream in){
        try {
            FileMetadata metadata = client.files().uploadBuilder("/test.txt").uploadAndFinish(in);

            Log.i(TAG, "Successfully uploaded info to dropbox.");
        }catch(Exception e){
            Log.e(TAG, "Error uploading file to dropbox: " + e.toString());
            return false;
        }

        return true;
    }
}
