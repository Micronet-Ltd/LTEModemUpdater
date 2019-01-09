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
            sb.append(line);
        }

        return sb.toString();
    }

    static boolean startRild() {
        try {
            // Start rild
            runShellCommand(new String[]{"/system/bin/setprop", "ctl.start", "ril-daemon"});

            // Check to make sure that rild is running
            String output = runShellCommand(new String[]{"/system/bin/getprop"});

            if(output.toLowerCase().contains("[init.svc.ril-daemon]: [running]")){
                Log.i(TAG, "Rild started");
                Logger.addLoggingInfo("Rild started");
                return true;
            }else{
                Log.e(TAG, "Error rild not started correctly");
                Logger.addLoggingInfo("Error starting rild");
                return false;
            }

        } catch (IOException e) {
            Log.e(TAG, "Error rild not started correctly" + e.toString());
            Logger.addLoggingInfo("Error starting rild: " + e.toString());
            return false;
        }
    }

    static boolean killRild() {

        try {
            // Start rild
            runShellCommand(new String[]{"/system/bin/setprop", "ctl.stop", "ril-daemon"});

            // Check to make sure that rild is running
            String output = runShellCommand(new String[]{"/system/bin/getprop"});

            if(output.toLowerCase().contains("[init.svc.ril-daemon]: [stopped]")){
                Log.i(TAG, "Rild stopped");
                Logger.addLoggingInfo("Rild stopped");
                return true;
            }else{
                Log.e(TAG, "Error rild not stopped correctly");
                Logger.addLoggingInfo("Error stopping rild");
                return false;
            }

        } catch (IOException e) {
            Log.e(TAG, "Error rild not stopped correctly" + e.toString());
            Logger.addLoggingInfo("Error stopping rild: " + e.toString());
            return false;
        }
    }

}
