package com.micronet.a317modemupdater;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class Rild {

    private final static String TAG = "Updater-Rild";

    private static String runShellCommand(String[] commands) throws IOException{
        StringBuilder sb = new StringBuilder();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(commands).getInputStream()));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            Log.d(TAG, line);
            sb.append(line);
        }

        return sb.toString();
    }

    static boolean startRild() {
        try {
            // Start rild
            runShellCommand(new String[]{"/system/bin/setprop", "ctl.start", "ril-daemon"});

            // Check to make sure that rild is running
            String output = runShellCommand(new String[]{"/system/bin/getprop", "|", "/system/bin/grep", "init.svc.ril-daemon"});

            if(output.toLowerCase().contains("running")){
                Log.i(TAG, "Rild started");
                return true;
            }else{
                Log.e(TAG, "Error rild not started correctly");
                return false;
            }

        } catch (IOException e) {
            Log.e(TAG, "Error rild not started correctly" + e.toString());
            return false;
        }
    }

    static boolean killRild() {

        try {
            // Start rild
            runShellCommand(new String[]{"/system/bin/setprop", "ctl.stop", "ril-daemon"});

            // Check to make sure that rild is running
            String output = runShellCommand(new String[]{"/system/bin/getprop", "|", "/system/bin/grep", "init.svc.ril-daemon"});

            if(output.toLowerCase().contains("stopped")){
                Log.i(TAG, "Rild stopped");
                return true;
            }else{
                Log.e(TAG, "Error rild not stopped correctly");
                return false;
            }

        } catch (IOException e) {
            Log.e(TAG, "Error rild not stopped correctly" + e.toString());
            return false;
        }
    }

}
