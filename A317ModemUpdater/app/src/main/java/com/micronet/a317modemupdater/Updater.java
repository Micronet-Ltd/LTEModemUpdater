package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.DropBox.uploadPreCheck;
import static com.micronet.a317modemupdater.Rild.configureRild;
import static com.micronet.a317modemupdater.Utils.getCurrentDatetime;
import static com.micronet.a317modemupdater.Utils.setUpdated;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import com.micronet.a317modemupdater.interfaces.UpdateState;
import java.io.InputStream;

public class Updater {
    private static final String TAG = "Updater-Main";
    static final int REBOOT_DELAY = 600;

    private final Context context;
    private final UpdateState updateState;
    private final String PORT_PATH = "/dev/ttyACM0";
    private final int V20_00_525_2 = 20;
    private final int V20_00_528_1 = 30;
    private final int PRECHECK_UPLOAD_RETRIES = 30;
    private final int PRECHECK_UPLOAD_WAIT = 1000;

    private final String V20_00_525_2_STR = "20.00.525.2";
    private final String V20_00_528_1_STR = "20.00.528.1";
    private final String ATT_MODEM = "LE910-NA1";
//    private final String VERIZON_MODEM = "LE910-SVL";

    private Port port;
    private byte[] updateFileBytes;
    private int totalUpdateFileSize;
    private int updateFileType;
    private final int NUM_BYTES_TO_SEND = 4096;

    Updater(Context context, UpdateState updateState) {
        this.context = context;
        this.updateState = updateState;

        updateFileType = 0;
    }

    void startUpdateProcess(){
        runOnNewThread(new Runnable() {
            @Override
            public void run() {
                initiateUpdate();
            }
        });
    }

    private void initiateUpdate() {
        Looper.prepare();
        Logger.prepareLogger(context);
        port = new Port(PORT_PATH);

        // Try to upload log stating that you will be trying to check and update modem.
        Logger.addLoggingInfo("About to try to upload precheck.");
        String currentDatetime = getCurrentDatetime();
        for (int i = 0; i < PRECHECK_UPLOAD_RETRIES; i++) {
            try {
                if (uploadPreCheck(currentDatetime)) {
                    Log.i(TAG, "Uploaded precheck log to dropbox.");
                    break;
                } else {
                    if (i == PRECHECK_UPLOAD_RETRIES - 1) {
                        Logger.addLoggingInfo("Could not upload logging information.");
//                        updateState.couldNotUploadPrecheck();
//                        updateState.delayedShutdown(REBOOT_DELAY);
//                        return;
                    }

                    sleep(PRECHECK_UPLOAD_WAIT);
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.toString());
            }
        }

        // Try to kill rild, setup the port, and communicate with the modem.
        // If successful will start trying to update, else will log errors and reboot device.
        setupPortAndModemCommunication();
    }

    /**
     * Handles stopping rild, setting up the port, and trying to communicate with the modem. If it can then it will try to update the modem if an
     * update is available, else it will log the errors and reboot the device.
     */
    private void setupPortAndModemCommunication() {
        // Try to stop rild to communicate with the modem, if it fails then reboot.
        if (!configureRild(false)) {
            // Log errors and update UI
            String err = "Error killing rild. Could not properly update modem firmware. Reboot device and try again.";
            Log.e(TAG, err);
            Logger.addLoggingInfo(err);
            updateState.couldNotConfigureRild();

            // Upload logs and try to handle error
            Logger.uploadLogs(context, false, "FAIL\nCouldn't stop rild properly.\n\n");

            // stopRild() tries 10 times to stop rild. If it can't stop it at that point then reboot device
            // TODO: Decide if anything more needs to be done here
            updateState.delayedShutdown(REBOOT_DELAY);
            return;
        }

        // Try to set up the port to communicate with the modem, if it fails then reboot.
        if (!port.setupPort()) {
            // Log errors and update UI
            String err = "Could not setup the port properly for updating modem firmware. Reboot device and try again.";
            Logger.addLoggingInfo(err);
            updateState.couldNotSetupPort();

            // Upload logs and begin reboot process
            Logger.uploadLogs(context, false, "FAIL\nCouldn't setup port properly to communicate with modem.\n\n");

            // setupPort() tries 10 times to setup port. If it can't set it up at that point then reboot device
            // TODO: Decide if anything more needs to be done here
            updateState.delayedShutdown(REBOOT_DELAY);
            return;
        }

        // Try to communicate with modem, if it fails then reboot.
        String modemType = port.getModemType();
        if (!modemType.equals("UNKNOWN")) {
            Logger.addLoggingInfo("Able to communicate with modem.");
            // If you are able to communicate with the modem then check if this version can be updated.
            checkFirmwareVersion(modemType);
        } else {
            // Log errors and update UI
            Logger.addLoggingInfo("Error communicating with the modem. Cannot update modem.");
            updateState.couldNotCommunicateWithModem();
            configureRild(true);

            // Upload logs and begin reboot process
            Logger.uploadLogs(context, false, "FAIL\nCouldn't communicate with modem.\n\n");

            // testConnection() tries 10 times to communicate with port. If it can't set it up at that point then reboot device
            // TODO: Decide if anything more needs to be done here
            updateState.delayedShutdown(REBOOT_DELAY);
        }
    }

    private void checkFirmwareVersion(String modemType) {
        // Get modem type and version
        String modemFirmwareVersion = port.getModemVersion();
        String modemTypeDisplay = "Modem Type: " + modemType;
        String modemVersionDisplay = "Modem Version: " + modemFirmwareVersion;

        // Update modem type/version and add logging info
        Logger.addLoggingInfo(modemTypeDisplay);
        Logger.addLoggingInfo(modemVersionDisplay);
        updateState.initialModemTypeAndVersion(modemTypeDisplay, modemVersionDisplay);

        // Check for updates and update if necessary
        checkIfUpdatesAreAvailable(modemType, modemFirmwareVersion);
    }

    private void checkIfUpdatesAreAvailable(String modemType, String modemFirmwareVersion) {
        switch (modemType) {
            case ATT_MODEM:
                switch (modemFirmwareVersion) {
                    case V20_00_528_1_STR:
                        String info = "Device has 20.00.528.1. Already updated.";
                        Logger.addLoggingInfo(info);
                        configureRild(true);
                        updateState.alreadyUpdated(V20_00_528_1_STR);

                        setUpdated(context, true);

                        // Upload results
                        updateFileType = V20_00_528_1;
                        Logger.uploadLogs(context, true, "PASS\nModem already updated to 20.00.528.1.\n\n");
                        break;
                    case V20_00_525_2_STR:
                        Logger.addLoggingInfo("Device has 20.00.525.2. Trying to update.");
                        updateState.attemptingToUpdate(V20_00_525_2_STR);

                        // Update modem
                        updateFileType = V20_00_525_2;
                        updateModem();
                        break;
                    default:
                        Logger.addLoggingInfo("Device's modem cannot be updated because there is no update file for this modem version.");
                        configureRild(true);
                        updateState.noUpdateFileForModem();

                        Logger.uploadLogs(context, false, "FAIL\nNo update file for this modem version.\n\n");
//                        updateState.delayedShutdown(REBOOT_DELAY);
                        break;
                }
                break;
            default: // Unknown modem type.
                Logger.addLoggingInfo("Device's modem cannot be updated because there is no update file for this modem type.");
                configureRild(true);
                updateState.noUpdateFileForModem();

                Logger.uploadLogs(context, false, "FAIL\nNo update file for this modem version.\n\n");
//                updateState.delayedShutdown(REBOOT_DELAY);
                break;
        }
    }

    // **************************************************************************************
    // **************************************************************************************
    // ********************************** Update Functions **********************************
    // **************************************************************************************
    // **************************************************************************************

    private void updateModem() {
        boolean result = readInUpdateFile();

        if (result) {
            runOnNewThread(updateRunnable);
            Logger.addLoggingInfo("Sending update file to modem...");
            updateState.sendingUpdateFileToModem();
        } else {
            Logger.addLoggingInfo("Error loading update file or no update file found.");
            configureRild(true);
            updateState.errorLoadingUpdateFile();

            Logger.uploadLogs(context, false, "FAIL\nError loading update file or no update file found.\n\n");
            updateState.delayedShutdown(REBOOT_DELAY);
        }
    }

    private boolean readInUpdateFile() {
        byte[] bytesFromFile = new byte[6000000];
        InputStream updateInputStream;

        // Select correct delta update
        switch (updateFileType) {
            case V20_00_525_2:
                updateInputStream = context.getResources().openRawResource(R.raw.update_525_2_to_528_1);
                break;
            default:
                String info = "ERROR: No update file selected properly. Cannot read in update file.";
                Log.e(TAG, info);
                Logger.addLoggingInfo(info);

                return false;
        }

        try {
            int bytesRead = updateInputStream.read(bytesFromFile);
            updateFileBytes = new byte[bytesRead];
            System.arraycopy(bytesFromFile, 0, updateFileBytes, 0, bytesRead);

            totalUpdateFileSize = bytesRead;
            updateInputStream.close();
            Log.i(TAG, "Update File read in. Bytes read in: " + bytesRead);
            Logger.addLoggingInfo("Update file loaded from memory. Bytes loaded: " + bytesRead);

            int num = bytesRead / NUM_BYTES_TO_SEND;
            if (bytesRead % NUM_BYTES_TO_SEND > 0) {
                num++;
            }

            // Send event to update progress bar
            updateState.loadedUpdateFile(num);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return false;
        }

        return true;
    }

    private boolean updateModemFirmware() {
        int packetsSent = 0;

        // Check to make sure the context and port aren't null.
        if (context == null || port == null) {
            Log.e(TAG, "Error sending delta: context or port is null.");
            Logger.addLoggingInfo("Error sending delta: context or port is null.");
            return false;
        }

        // Try to connect to modem to send delta to modem
        String resultFromRequestToSend = port.writeRead("AT#OTAUPW\r");
        if (!resultFromRequestToSend.contains("CONNECT")) {
            Logger.addLoggingInfo("Error: after sending AT#OTAUPW, CONNECT not received.");
            updateState.errorConnectingToModemToSendUpdateFile();
            return false;
        }

        sleep(1000);

        // Send over delta file to modem from application
        int counter = 0;
        while (counter < totalUpdateFileSize) {
            if (!((totalUpdateFileSize - counter) < NUM_BYTES_TO_SEND)) {
                try {
                    port.write(updateFileBytes, counter, NUM_BYTES_TO_SEND);

                    packetsSent++;
                    Log.d(TAG, "Packet " + packetsSent + " sent. Total Bytes sent: " + (counter + NUM_BYTES_TO_SEND) + " Sent Bytes: "
                            + NUM_BYTES_TO_SEND);
                    Logger.addLoggingInfo("Packet " + packetsSent + " sent. Total Bytes sent: " + (counter + NUM_BYTES_TO_SEND) + " Sent Bytes: "
                            + NUM_BYTES_TO_SEND);
                    counter += NUM_BYTES_TO_SEND;
                    updateState.updateSendProgress(packetsSent);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    Logger.addLoggingInfo("Error sending delta: " + e.toString());
                    return false;
                }
            } else { // Final send
                try {
                    port.write(updateFileBytes, counter, (totalUpdateFileSize - counter));

                    packetsSent++;
                    Log.d(TAG,
                            "Packet " + packetsSent + " sent. Total Bytes sent: " + (counter + (totalUpdateFileSize - counter)) + " Sent Bytes: " + (
                                    totalUpdateFileSize - counter));
                    Logger.addLoggingInfo(
                            "Packet " + packetsSent + " sent. Total Bytes sent: " + (counter + (totalUpdateFileSize - counter)) + " Sent Bytes: " + (
                                    totalUpdateFileSize - counter));
                    counter += totalUpdateFileSize - counter;
                    updateState.updateSendProgress(packetsSent);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    Logger.addLoggingInfo("Error sending delta: " + e.toString());
                    return false;
                }
            }
            // Sleep 300 ms between each send
            sleep(300);
        }

        sleep(5000);

        // Send +++ to signal end of updating. Should receive NO CARRIER back.
        String result = port.writeRead("+++");
        if (!result.contains("NO CARRIER")) {
            updateState.errorFileNotSentSuccessfully();
            Log.e(TAG, "After sending \"+++\", \"NO CARRIER\" not received.");
            Logger.addLoggingInfo("Error: after sending \"+++\", \"NO CARRIER\" not received.");
            return false;
        } else {
            updateState.fileSentSuccessfully();
            Logger.addLoggingInfo("Update file send to modem. Validating update file.");
        }

        sleep(5000);

        // Validate delta after it is sent
        String resultValidate = port.writeExtendedRead("AT#OTAUP=1\r", "OK");
        if (!resultValidate.contains("OK")) {
            updateState.errorFileNotValidated();
            Log.e(TAG, "After sending \"at#otaup=1\", \"OK\" not received.");
            Logger.addLoggingInfo("After sending \"at#otaup=1\", \"OK\" not received.");
            return false;
        } else {
            updateState.fileValidatedSuccessfully();
            Logger.addLoggingInfo("Update file validated.");
        }

        // Validation and start of firmware update, should return "OK" to signal update start
        final String updateResult = port.writeExtendedRead("AT#OTAUP=0,2\r", "OK");
        if (updateResult.contains("OK")) {
            Log.d(TAG, "File validated and update process starting");
            Logger.addLoggingInfo("Update process starting.");
        } else {
            updateState.errorFileNotValidatedAndUpdateProcessNotStarting();
            Log.e(TAG, "After sending \"at#otaup=0,2\", \"OK\" not received.");
            Logger.addLoggingInfo("After sending \"at#otaup=0,2\", \"OK\" not received.");
            return false;
        }

        // Wait and see if the update completed successfully
        boolean pass = false;
        port.closePort();
        updateState.updateProcessStarting();
        Logger.addLoggingInfo("Waiting 2-5 minutes to check if modem updated properly.");

        sleep(30000);

        for (int i = 0; i < 45; i++) {
            port = new Port(PORT_PATH);

            if (!port.exists()) {
                Log.e(TAG, "Loop: " + i + ", Port does not exist. Sleeping then checking next iteration.");
                Logger.addLoggingInfo("Loop: " + i + ", Port does not exist. Sleeping then checking next iteration.");
                sleep(3000);
                continue;
            }

            if (!port.openPort()) {
                Log.e(TAG, "Loop: " + i + ", Error opening port. Sleeping then checking next iteration.");
                Logger.addLoggingInfo("Loop: " + i + ", Error opening port. Sleeping then checking next iteration.");
                sleep(3000);
                continue;
            }

            final String updatedSoftwareVersion = port.writeRead("AT+CGMR\r");
            final String updatedExtendedSoftwareVersion = port.writeRead("AT#CFVR\r");

            // 20.00.034 .4, .6, and .10 should go to 20.10.034.0
            // 20.00.522 .4 and .7 should go to 20.10.522.0
            if (updateFileType == V20_00_525_2) {
                if (updatedSoftwareVersion.contains("20.00.528.1") && updatedExtendedSoftwareVersion
                        .contains("#CFVR: 0")) { // Modem updated successfully
                    pass = true;
                    updateState.updatedModemFirmwareVersion(formatModemVersion(updatedSoftwareVersion, updatedExtendedSoftwareVersion));

                    port.closePort();
                    Log.d(TAG, "Loop: " + i + ", Str is: " + updatedSoftwareVersion);
                    Log.d(TAG, "Version updated successfully.");
                    break;
                }
            }

            port.closePort();
            Log.d(TAG, "Loop: " + i + ", Str is: " + updatedSoftwareVersion);
            Logger.addLoggingInfo("Loop: " + i + ", Str is: " + updatedSoftwareVersion);

            sleep(3000);
        }

        // Handle whether the update succeeded or failed
        if (pass) {
           if (updateFileType == V20_00_525_2) {
                Logger.addLoggingInfo("SUCCESS: Device modem updated successfully to 20.00.528.1.");
            }
            return true;
        } else {
            Logger.addLoggingInfo("ERROR: Modem not upgraded successfully. Reboot device and try again.");
            return false;
        }
    }

    // **************************************************************************************
    // **************************************************************************************
    // *********************************** Helper Methods ***********************************
    // **************************************************************************************
    // **************************************************************************************

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            int numberOfRetries = 5;
            boolean updated = false;

            for (int i = 0; i < numberOfRetries; i++) {
                updated = updateModemFirmware();

                // If the modem firmware has been updated successfully then upload results.
                if (updated) {
                    break;
                } else {
                    // Restart the modem and then try to update again.
                    restartModem();
                }
            }

            // Upload results and reboot if needed.
            if (updated) {
                configureRild(true);
                setUpdated(context, true);
                updateState.successfullyUpdatedUploadingLogs();

                Logger.uploadLogs(context, true, "PASS\nSuccessfully update modem firmware version.\n\n");
            } else {
                updateState.failureUpdatingUploadingLogs();
                Logger.uploadLogs(context, false, "FAIL\nError updating modem firmware version.\n\n");
                updateState.delayedShutdown(REBOOT_DELAY);
            }
        }
    };

    // ------------------------
    //      Helper Methods
    // ------------------------

    private void runOnNewThread(Runnable runnable) {
        new Thread(runnable).start();
    }

    private void restartModem() {
        // Sleeping at least 5 seconds from last AT command sent because of recommendation in docs.
        sleep(5000);

        // Send command to reboot modem
        updateState.errorRestartModem();
        port.writeRead("AT#ENHRST=1,0\r");

        // Sleep a certain amount of time to wait for modem to restart
        sleep(30000);
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
    }

    private String formatModemVersion(String modemVersion, String extendedVersion) {
        return modemVersion.replace("\n", "")
                .replace("AT+CGMR", "")
                .replace("OK", "") + "." +
                extendedVersion.replace("\n", "")
                        .replace("AT#CFVR", "")
                        .replace("OK", "")
                        .replace("#CFVR: ", "");
    }
}
