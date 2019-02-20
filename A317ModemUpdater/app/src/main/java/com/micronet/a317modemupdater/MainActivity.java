package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.Rild.stopRild;
import static com.micronet.a317modemupdater.Rild.startRild;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Updater-Main";
    private static final String UPDATE_SUCCESSFUL = "com.micronet.dsc.resetrb.modemupdater.UPDATE_SUCCESSFUL";

    private final Context context = this;
    private final String PORT_PATH = "/dev/ttyACM0";

    private Port port;
    private byte[] updateFileBytes;
    private int totalUpdateFileSize;
    private int updateFileType;
    final int NUM_BYTES_TO_SEND = 4096;

    private TextView tvInfo;
    private TextView tvModemType;
    private TextView tvModemVersion;
    private Button btnUpdateModem;
    private ProgressBar progressBar;
    private ConstraintLayout mainLayout;

    private CountDownTimer countDownTimer;
    private final int REBOOT_DELAY = 300;

    // Modem Firmware Versions
    private final int V20_00_032 = 1;
    private final int V20_00_032_B041 = 2;
    private final int V20_00_034_4 = 3;
    private final int V20_00_034_6 = 4;
    private final int V20_00_034_10 = 5;
    private final int V20_00_522_4 = 11;
    private final int V20_00_522_7 = 12;
    private final int V20_00_034_11 = 20;
    private final int V20_00_522_9 = 21;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // First check to see if device is already updated and uploaded.
        boolean updated = isUpdated();
        boolean uploaded = isUploaded();

        // We check three cases:
        //  - If the modem is updated and logs are uploaded then don't start the application and send broadcast of a successful update.
        //  - If the modem is updated but the logs aren't uploaded then keep trying to upload the logs.
        //  - Else upload precheck log to Dropbox, check the modem firmware version, and update if needed.
        if (updated && uploaded) {
            // Don't start the application and display toast.
            String modemResult = "Modem firmware already updated and uploaded. Activity not starting.";
            Toast.makeText(context, modemResult, Toast.LENGTH_LONG).show();
            Log.i(TAG, modemResult);

            // Resend broadcast to resetrb to begin clean up. ResetRB will clear Communitake data and uninstall this application.
            Intent successfulUpdateIntent = new Intent(UPDATE_SUCCESSFUL);
            sendBroadcast(successfulUpdateIntent);
            finish();
        } else if (updated) {
            // Only upload the logs.
            Log.i(TAG, "Modem firmware already updated but logs not uploaded.");

            // Set up UI to inform user that logs are trying to be uploaded.
            setContentView(R.layout.activity_main);
            setUpUi();
            tvInfo.setText("Modem Firmware already updated. Trying to upload logs.");

            // Start process of uploading logs from database.
            Logger.createNew(this);
            Logger.uploadSavedLogs(context);
        } else {
            // Check modem firmware and update if needed.
            Log.i(TAG, "Modem firmware hasn't been updated yet.");

            // Setup UI.
            setContentView(R.layout.activity_main);
            setUpUi();
            tvInfo.setText("Preparing to check modem version...");

            // TODO: Decouple from the UI. Start on new thread. Potentially new service or different class? Whatever would be easiest and best.
            runOnNewThread(new Runnable() {
                @Override
                public void run() {
                    initiateApplication();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (Logger.db != null && Logger.db.isOpen()) {
            Logger.db.close();
        }
    }

    /**
     * Used to detect when a configuration change happens and makes sure to not restart the app. With A317s there is usually a mcc/mnc change on boot
     * that causes the app to restart if we didn't have this method.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "App kept alive during configuration change.");
    }

    private void setUpUi() {
        setTitle("A317 Modem Updater - App Version: " + BuildConfig.VERSION_NAME + ", Device API: " + Build.VERSION.SDK_INT);

        tvInfo = findViewById(R.id.tvInfo);
        tvModemType = findViewById(R.id.tvModemType);
        tvModemVersion = findViewById(R.id.tvModemVersion);
        btnUpdateModem = findViewById(R.id.btnUpdateModem);
        progressBar = findViewById(R.id.progressBar);
        mainLayout = findViewById(R.id.mainLayout);

        updateFileType = 0;

        progressBar.setMax(156);
        progressBar.setProgress(0);
        progressBar.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.MULTIPLY);

        btnUpdateModem.setVisibility(View.INVISIBLE);
        btnUpdateModem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateModem();
                btnUpdateModem.setEnabled(false);
            }
        });
    }

    private void initiateApplication() {
        Logger.createNew(context);
        port = new Port(PORT_PATH);

        // TODO: Implement a backoff method to try to make sure logs are actually uploaded if possible.
        // Try to upload log stating that you will be trying to check and update modem.
        DropBox dropBox = new DropBox(context);
        for (int i = 0; i < 10; i++) {
            if (dropBox.preUploadLog(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime()),
                    Logger.serial)) {
                Log.i(TAG, "Uploaded precheck log to dropbox.");
                break;
            } else {
                sleep(100);
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
        if (!stopRild()) {
            // Log errors and update UI
            String err = "Error killing rild. Could not properly update modem firmware. Reboot device and try again.";
            updateTvInfo(err);
            Log.e(TAG, err);
            Logger.addLoggingInfo(err);
            updateBackgroundColor(Color.YELLOW);

            // Upload logs and try to handle error
            Logger.uploadLogs(this, false, "FAIL\nCouldn't stop rild properly.\n\n");
            // TODO: Never actually seen this happen but it might just be worth waiting and retrying later on.
//            delayedShutdown(REBOOT_DELAY);
            return;
        }

        // Try to set up the port to communicate with the modem, if it fails then reboot.
        if (!port.setupPort()) {
            // Log errors and update UI
            String err = "Could not setup the port properly for updating modem firmware. Reboot device and try again.";
            updateTvInfo(err);
            Logger.addLoggingInfo(err);
            updateBackgroundColor(Color.YELLOW);

            // Upload logs and begin reboot process
            Logger.uploadLogs(this, false, "FAIL\nCouldn't setup port properly to communicate with modem.\n\n");
            // TODO: Better handling of this error without shutting down
//            delayedShutdown(REBOOT_DELAY);
            return;
        }

        // Try to communicate with modem, if it fails then reboot.
        if (port.testConnection()) {
            Logger.addLoggingInfo("Able to communicate with modem.");
            // If you are able to communicate with the modem then check if this version can be updated.
            checkFirmwareVersion();
        } else {
            // Log errors and update UI
            updateTvInfo("Error communicating with the modem. Cannot update modem.\nRestart and try again. Restarting rild.");
            updateBackgroundColor(Color.YELLOW);
            Logger.addLoggingInfo("Error communicating with the modem. Cannot update modem.");
            startRild();

            // Upload logs and begin reboot process
            Logger.uploadLogs(this, false, "FAIL\nCouldn't communicate with modem.\n\n");
//            delayedShutdown(REBOOT_DELAY);
        }
    }

    private void checkFirmwareVersion() {
        // Get modem type and version
        String modemType = port.getModemType();
        String modemFirmwareVersion = port.getModemVersion();
        String modemTypeDisplay = "Modem Type: " + modemType;
        String modemVersionDisplay = "Modem Version: " + modemFirmwareVersion;

        // Update modem type/version and add logging info
        updateTvModemType(modemTypeDisplay);
        updateTvModemVersion(modemVersionDisplay);
        Logger.addLoggingInfo(modemTypeDisplay);
        Logger.addLoggingInfo(modemVersionDisplay);

        // Check for updates and update if necessary
        checkIfUpdatesAreAvailable(modemType, modemFirmwareVersion);
    }

    private void checkIfUpdatesAreAvailable(String modemType, String modemFirmwareVersion) {
        switch (modemType) {
            case "LE910-SVL":
                switch (modemFirmwareVersion) {
                    case "20.00.034.11":
                        String info = "Device has 20.00.034.11. Already updated.";
                        updateTvInfo(info);
                        updateBackgroundColor(Color.GREEN);
                        Logger.addLoggingInfo(info);
                        startRild();

                        // Set boolean to updated
                        context.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE).edit()
                                .putBoolean("updated", true).apply();

                        // Upload results.
                        updateFileType = V20_00_034_11;
                        Logger.uploadLogs(context, true, "PASS\nModem already updated to 20.00.034.11.\n\n");
                        break;
                    case "20.00.034.10":
                        info = "Device has 20.00.034.10. Trying to update.";
                        updateTvInfo(info);
                        Logger.addLoggingInfo(info);

                        // Update modem
                        updateFileType = V20_00_034_10;
                        updateModem();
                        break;
                    case "20.00.034.6":
                        info = "Device has 20.00.034.6. Trying to update.";
                        updateTvInfo(info);
                        Logger.addLoggingInfo(info);

                        // Update modem
                        updateFileType = V20_00_034_6;
                        updateModem();
                        break;
                    case "20.00.034.4":
                        info = "Device has 20.00.034.4. Trying to update.";
                        updateTvInfo(info);
                        Logger.addLoggingInfo(info);

                        // Update modem
                        updateFileType = V20_00_034_4;
                        updateModem();
                        break;
                    default:
                        info = "Device's modem cannot be updated because there is no update file for this modem version. Rebooting.";
                        updateTvInfo(info);
                        updateBackgroundColor(Color.RED);
                        Logger.addLoggingInfo(info);
                        startRild();

                        Logger.uploadLogs(this, false, "FAIL\nNo update file for this modem version.\n\n");
//                        delayedShutdown(REBOOT_DELAY);
                        break;
                }
                break;
            case "LE910-NA1":
                switch (modemFirmwareVersion) {
                    case "20.00.522.9":
                        String info = "Device has 20.00.522.9. Already updated.";
                        updateTvInfo(info);
                        updateBackgroundColor(Color.GREEN);
                        Logger.addLoggingInfo(info);
                        startRild();

                        // Set boolean to updated
                        context.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE).edit()
                                .putBoolean("updated", true).apply();

                        // Upload results
                        updateFileType = V20_00_522_9;
                        Logger.uploadLogs(context, true, "PASS\nModem already updated to 20.00.522.9.\n\n");
                        break;
                    case "20.00.522.7":
                        info = "Device has 20.00.522.7. Trying to update.";
                        updateTvInfo(info);
                        Logger.addLoggingInfo(info);

                        // Update modem
                        updateFileType = V20_00_522_7;
                        updateModem();
                        break;
                    case "20.00.522.4":
                        info = "Device has 20.00.522.4. Trying to update.";
                        updateTvInfo(info);
                        Logger.addLoggingInfo(info);

                        // Update modem
                        updateFileType = V20_00_522_4;
                        updateModem();
                        break;
                    default:
                        info = "Device's modem cannot be updated because there is no update file for this modem version. Rebooting.";
                        updateTvInfo(info);
                        updateBackgroundColor(Color.RED);
                        Logger.addLoggingInfo(info);
                        startRild();

                        Logger.uploadLogs(this, false, "FAIL\nNo update file for this modem version.\n\n");
//                        delayedShutdown(REBOOT_DELAY);
                        break;
                }
                break;
            default: // Unknown modem type.
                String info = "Device's modem cannot be updated because there is no update file for this modem type.";
                updateTvInfo(info);
                updateBackgroundColor(Color.RED);
                Logger.addLoggingInfo(info);
                startRild();

                Logger.uploadLogs(this, false, "FAIL\nNo update file for this modem version.\n\n");
//                delayedShutdown(REBOOT_DELAY);
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

            setProgressBarVisibility(View.VISIBLE);
            String info = "Sending update file to modem...";
            updateTvInfo(info);
            Logger.addLoggingInfo(info);
        } else {
            String info = "Error loading update file or no update file found.";
            updateTvInfo(info);
            updateBackgroundColor(Color.YELLOW);
            Logger.addLoggingInfo(info);
            startRild();

            Logger.uploadLogs(this, false, "FAIL\nError loading update file or no update file found.\n\n");
            // TODO: try not to reboot in this case and handle this
//            delayedShutdown(REBOOT_DELAY);
        }
    }

    private boolean readInUpdateFile() {
        byte[] bytesFromFile = new byte[6000000];
        InputStream updateInputStream;

        // Select correct delta update
        switch (updateFileType) {
            case V20_00_034_4:
                updateInputStream = getResources().openRawResource(R.raw.update_034_4_to_034_11);
                break;
            case V20_00_034_6:
                updateInputStream = getResources().openRawResource(R.raw.update_034_6_to_034_11);
                break;
            case V20_00_034_10:
                updateInputStream = getResources().openRawResource(R.raw.update_034_10_to_034_11);
                break;
            case V20_00_522_4:
                updateInputStream = getResources().openRawResource(R.raw.update_522_4_to_522_9);
                break;
            case V20_00_522_7:
                updateInputStream = getResources().openRawResource(R.raw.update_522_7_to_522_9);
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

            progressBar.setMax(num);
            progressBar.setProgress(0);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return false;
        }

        return true;
    }

    private boolean updateModemFirmware() {
        int packetsSent = 0;

        // Try to connect to modem to send delta to modem
        String resultFromRequestToSend = port.writeRead("AT#OTAUPW\r");
        if (!resultFromRequestToSend.contains("CONNECT")) {
            updateTvInfo("Error updating modem firmware. Reboot device and try again.");
            updateBackgroundColor(Color.RED);
            Logger.addLoggingInfo("Error: after sending AT#OTAUPW, CONNECT not received.");
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
                    setSendProgress(packetsSent);
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
                    setSendProgress(packetsSent);
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
            updateTvInfo("File not sent successfully. Reboot device and try again.");
            updateBackgroundColor(Color.RED);

            Log.e(TAG, "After sending \"+++\", \"NO CARRIER\" not received.");
            Logger.addLoggingInfo("Error: after sending \"+++\", \"NO CARRIER\" not received.");
            return false;
        } else {
            updateTvInfo("File sent. Validating file.");
            Logger.addLoggingInfo("Update file send to modem. Validating update file.");
        }

        sleep(5000);

        // Validate delta after it is sent
        String resultValidate = port.writeExtendedRead("AT#OTAUP=1\r", "OK");
        if (!resultValidate.contains("OK")) {
            updateTvInfo("File not sent properly. Reboot device and try again.");
            updateBackgroundColor(Color.RED);

            Log.e(TAG, "After sending \"at#otaup=1\", \"OK\" not received.");
            Logger.addLoggingInfo("After sending \"at#otaup=1\", \"OK\" not received.");
            return false;
        } else {
            updateTvInfo("File validated.");
            Logger.addLoggingInfo("Update file validated.");
        }

        // Validation and start of firmware update, should return "OK" to signal update start
        final String updateResult = port.writeExtendedRead("AT#OTAUP=0,2\r", "OK");
        if (updateResult.contains("OK")) {
            Log.d(TAG, "File validated and update process starting");
            Logger.addLoggingInfo("Update process starting.");
        } else {
            updateTvInfo("Modem not updated successfully. Reboot device and try again.");
            updateBackgroundColor(Color.RED);
            Log.e(TAG, updateResult);
            Log.e(TAG, "After sending \"at#otaup=0,2\", \"OK\" not received.");
            Logger.addLoggingInfo("After sending \"at#otaup=0,2\", \"OK\" not received.");
            return false;
        }

        // Wait and see if the update completed successfully
        boolean pass = false;
        port.closePort();
        updateTvInfo("Waiting 2-5 minutes to check if modem updated properly.");
        Logger.addLoggingInfo("Waiting 2-5 minutes to check if modem updated properly.");

        sleep(30000);

        for (int i = 0; i < 90; i++) {
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

            // 20.00.034 .4, .6, and .10 should go to 20.00.034.11
            // 20.00.522 .4 and .7 should go to 20.00.522.9
            if (updateFileType == 3 || updateFileType == 4 || updateFileType == 5) {
                if (updatedSoftwareVersion.contains("20.00.034") && updatedExtendedSoftwareVersion
                        .contains("#CFVR: 11")) { // Modem updated successfully
                    pass = true;
                    updateTvModemVersion(formatModemVersion(updatedSoftwareVersion, updatedExtendedSoftwareVersion));

                    port.closePort();
                    Log.d(TAG, "Loop: " + i + ", Str is: " + updatedSoftwareVersion);
                    Log.d(TAG, "Version updated successfully.");
                    break;
                }
            } else if (updateFileType == 11 || updateFileType == 12) {
                if (updatedSoftwareVersion.contains("20.00.522") && updatedExtendedSoftwareVersion
                        .contains("#CFVR: 9")) { // Modem updated successfully
                    pass = true;
                    updateTvModemVersion(formatModemVersion(updatedSoftwareVersion, updatedExtendedSoftwareVersion));

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
            if (updateFileType == 3 || updateFileType == 4 || updateFileType == 5) {
                updateTvInfo("SUCCESS: Device modem updated successfully to 20.00.034.11.");
                Logger.addLoggingInfo("SUCCESS: Device modem updated successfully to 20.00.034.11.");
            } else if (updateFileType == 11 || updateFileType == 12) {
                updateTvInfo("SUCCESS: Device modem updated successfully to 20.00.522.9.");
                Logger.addLoggingInfo("SUCCESS: Device modem updated successfully to 20.00.522.9.");
            }

            updateBackgroundColor(Color.GREEN);
            return true;
        } else {
            updateTvInfo("ERROR: Modem not upgraded successfully. Reboot device and try again.");
            Logger.addLoggingInfo("ERROR: Modem not upgraded successfully. Reboot device and try again.");
            updateBackgroundColor(Color.RED);
            return false;
        }
    }

    // **************************************************************************************
    // **************************************************************************************
    // *********************************** Helper Methods ***********************************
    // **************************************************************************************
    // **************************************************************************************

    private boolean isUpdated() {
        SharedPreferences sharedPreferences = this.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("updated", false);
    }

    private boolean isUploaded() {
        SharedPreferences sharedPreferences = this.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("uploaded", false);
    }

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            boolean updated = updateModemFirmware();
            startRild();

            // Upload results and reboot if needed.
            if (updated) {
                context.getSharedPreferences("LTEModemUpdater", Context.MODE_PRIVATE).edit()
                        .putBoolean("updated", true).apply();
                Logger.uploadLogs(context, true, "PASS\nSuccessfully update modem firmware version.\n\n");
            } else {
                Logger.uploadLogs(context, false, "FAIL\nError updating modem firmware version.\n\n");
//                delayedShutdown(REBOOT_DELAY);
            }
        }
    };

    private void runOnNewThread(Runnable runnable) {
        new Thread(runnable).start();
    }

//    private void delayedShutdown(final int delaySeconds) {
//        countDownTimer = new CountDownTimer(delaySeconds * 1000, 15000) {
//            public void onTick(long millisUntilFinished) {
//                Toast.makeText(context, String.format(Locale.getDefault(), "Rebooting the device in %d seconds.",
//                        (int) Math.ceil((float) millisUntilFinished / (float) 1000)), Toast.LENGTH_LONG).show();
//            }
//
//            public void onFinish() {
//                // Reboot the device
//                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//                pm.reboot(null);
//            }
//        }.start();
//    }

    private void cancelShutdown() {
        countDownTimer.cancel();
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

    private void updateTvInfo(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvInfo.setText(text);
            }
        });
    }

    private void updateTvModemType(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvModemType.setText(text);
            }
        });
    }

    private void updateTvModemVersion(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvModemVersion.setText(text);
            }
        });
    }

    private void updateBackgroundColor(final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainLayout.setBackgroundColor(color);
            }
        });
    }

    private void setSendProgress(final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setProgress(progress);
            }
        });
    }

    private void setProgressBarVisibility(final int visibility) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(visibility);
            }
        });
    }


}