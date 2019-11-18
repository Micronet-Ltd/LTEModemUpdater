package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.Utils.runShellCommand;

import android.util.Log;
import java.io.IOException;

/**
 * Contains static methods for starting and stopping rild
 */
class Rild {

    private final static String TAG = "Updater-Rild";

    static boolean configureRild(boolean tryToStart) {
        for (int i = 0; i < 10; i++) {
            try {
                // Start or stop rild
                runShellCommand(new String[]{"/system/bin/setprop", (tryToStart? "ctl.start": "ctl.stop"), "ril-daemon"});

                // Check state of rild
                String output = runShellCommand(new String[]{"/system/bin/getprop"});

                if (output.toLowerCase().contains((tryToStart? "[init.svc.ril-daemon]: [running]": "[init.svc.ril-daemon]: [stopped]"))) {
                    String loggingOutput = (tryToStart? "Rild started": "Rild stopped");
                    Log.i(TAG, loggingOutput);
                    Logger.addLoggingInfo(loggingOutput);
                    return true;
                } else {
                    Log.d(TAG, "Rild not " + (tryToStart? "started": "stopped") + " correctly, trying again.");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error rild not " + (tryToStart? "started": "stopped") + " correctly" + e.toString());
                Logger.addLoggingInfo("Error " + (tryToStart? "starting": "stopping") + " rild: " + e.toString());
                return false;
            }
        }

        Log.e(TAG, "Error rild not " + (tryToStart? "started": "stopped") + " correctly.");
        Logger.addLoggingInfo("Error " + (tryToStart? "starting": "stopping") + " rild.");
        return false;
    }
}
