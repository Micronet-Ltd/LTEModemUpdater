package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.Rild.stopRild;
import static com.micronet.a317modemupdater.Rild.startRild;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import android.widget.Toast;
import java.io.InputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "Updater-Main";
    private final Context context = this;

    private Port port;

    private byte[] updateFileBytes;
    private int totalUpdateFileSize;
    private int updateFileType;
    final int NUM_BYTES_TO_SEND = 4096;
    int packetsSent = 0;

    private TextView tvInfo;
    private TextView tvModemType;
    private TextView tvModemVersion;
    private Button btnUpdateModem;
    private ProgressBar progressBar;
    private ConstraintLayout mainLayout;

    private CountDownTimer countDownTimer;

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateModemFirmware();
        }
    };

    private void runOnNewThread(Runnable runnable) {
        new Thread(runnable).start();
    }

    private final String PORT_PATH = "/dev/ttyACM0";

    enum FirmwareVersions {
        V20_00_032, V20_00_032_B041, V20_00_034_4, V20_00_034_6, V20_00_034_10, V20_00_522_4, V20_00_522_7
    }

    // Modem Firmware Versions
    private final int V20_00_032 = 1;
    private final int V20_00_032_B041 = 2;
    private final int V20_00_034_4 = 3;
    private final int V20_00_034_6 = 4;
    private final int V20_00_034_10 = 5;

    private final int V20_00_522_4 = 11;
    private final int V20_00_522_7 = 12;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setUpUi();

        // Create a new logging object
        Logger.createNew(this);

        // Create a new port
        port = new Port(PORT_PATH);

        // Kill rild to be able to communicate with the modem
        if (!stopRild()) {
            Log.e(TAG, "Error killing rild. Could not properly update modem firmware.");
            Logger.addLoggingInfo("Error killing rild. Could not properly update modem firmware.");
            tvInfo.setText("Error killing rild. Could not properly update modem firmware. Reboot device and try again.");
            tvModemType.setText("");
            mainLayout.setBackgroundColor(Color.YELLOW);
            Logger.uploadLogs(this, false, "FAIL\nCouldn't stop rild properly.\n\n");
            delayedShutdown(120);
            return;
        }

        // Setup port
        if (!port.setupPort()) {
            Logger.addLoggingInfo("Could not setup the port properly for updating modem firmware.");
            tvInfo.setText("Could not setup the port properly for updating modem firmware. Reboot device and try again.");
            mainLayout.setBackgroundColor(Color.YELLOW);
            Logger.uploadLogs(this, false, "FAIL\nCouldn't set up port properly to communicate with modem.\n\n");
            delayedShutdown(120);
            return;
        }

        // Test the connection
        if (port.testConnection()) {
            Logger.addLoggingInfo("Able to communicate with modem.");
            checkIfModemUpdatesAreAvailable();
        } else {
            Logger.addLoggingInfo("Error communicating with the modem. Cannot update modem.");
            tvInfo.setText("Error communicating with the modem. Cannot update modem.\nRestart and try again. Restarting rild.");
            mainLayout.setBackgroundColor(Color.YELLOW);
            startRild();

            Logger.uploadLogs(this, false, "FAIL\nCouldn't communicate with modem.\n\n");
            delayedShutdown(120);
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
     * Used to detect when a configuration change happens and makes sure to not restart the app.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "App kept alive during configuration change.");
    }

    // **************************************************************************************
    // **************************************************************************************
    // ******************************* Application Set Up ***********************************
    // **************************************************************************************
    // **************************************************************************************

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

    private void checkIfModemUpdatesAreAvailable() {
        // Get modem type and version
        String modemType = port.getModemType();
        String modemFirmwareVersion = port.getModemVersion();
        String modemTypeDisplay = "Modem Type: " + modemType;
        String modemVersionDisplay = "Modem Version: " + modemFirmwareVersion;

        // Update modem type and version
        tvModemType.setText(modemTypeDisplay);
        tvModemVersion.setText(modemVersionDisplay);

        Logger.addLoggingInfo(modemTypeDisplay);
        Logger.addLoggingInfo(modemVersionDisplay);

        switch (modemType) {
            case "LE910-SVL":
                if (modemFirmwareVersion.equals("20.00.034.10")) {
                    tvInfo.setText("Device has 20.00.034.10. Device already updated.");
                    mainLayout.setBackgroundColor(Color.GREEN);
                    Logger.addLoggingInfo("Device has 20.00.034.10. Device already updated.");
                    startRild();

                    Logger.uploadLogs(this, true, "OK\nDevice already updated to 20.00.034.10.\n\n");
                } else if (modemFirmwareVersion.equals("20.00.034.6")) {
                    tvInfo.setText("Device has 20.00.034.6. Updating to 20.00.034.10.");
                    updateFileType = V20_00_034_6;
                    Logger.addLoggingInfo("Device has 20.00.034.6. Updating to 20.00.034.10.");
                    updateModem();
                } else if (modemFirmwareVersion.equals("20.00.034.4")) {
                    tvInfo.setText("Device has 20.00.034.4. Updating to 20.00.034.10.");
                    Logger.addLoggingInfo("Device has 20.00.034.4. Updating to 20.00.034.10.");
                    updateFileType = V20_00_034_4;
                    updateModem();
                } else if (modemFirmwareVersion.contains("20.00.032-B041")) {
                    tvInfo.setText("Device has 20.00.032-B041. Updating to 20.00.034.4.");
                    Logger.addLoggingInfo("Device has 20.00.032-B041. Updating to 20.00.034.4.");
                    updateFileType = V20_00_032_B041;
                    updateModem();
                } else if (modemFirmwareVersion.contains("20.00.032")) {
                    tvInfo.setText("Device has 20.00.032. Updating to 20.00.034.4.");
                    Logger.addLoggingInfo("Device has 20.00.032. Updating to 20.00.034.4.");
                    updateFileType = V20_00_032;
                    updateModem();
                } else {
                    tvInfo.setText("Device's modem cannot be updated because there is no update file for this modem version.");
                    mainLayout.setBackgroundColor(Color.RED);
                    Logger.addLoggingInfo("Device's modem cannot be updated because there is no update file for this modem version.");
                    startRild();

                    Logger.uploadLogs(this, false, "FAIL\nNo update file for this modem version.\n\n");
                }
                break;
            case "LE910-NA1":
                if (modemFirmwareVersion.equals("20.00.522.7")) {
                    tvInfo.setText("Device has 20.00.522.7. Need to add update for this firmware.");
                    updateFileType = V20_00_522_7;
                    mainLayout.setBackgroundColor(Color.YELLOW);
                    Logger.addLoggingInfo("Device has 20.00.522.7. Need to add update for this firmware.");
                    startRild();

                    Logger.uploadLogs(this, false, "FAIL\nNo update file for this modem version.\n\n");
//                    delayedShutdown(180);
                } else if (modemFirmwareVersion.equals("20.00.522.4")) {
                    tvInfo.setText("Device has 20.00.522.4. Need to add update for this firmware.");
                    updateFileType = V20_00_522_4;
                    mainLayout.setBackgroundColor(Color.YELLOW);
                    Logger.addLoggingInfo("Device has 20.00.522.4. Need to add update for this firmware.");
                    startRild();

                    Logger.uploadLogs(this, false, "FAIL\nNo update file for this modem version.\n\n");
//                    delayedShutdown(180);
                } else {
                    tvInfo.setText("Device's modem cannot be updated because there is no update file for this modem version.");
                    mainLayout.setBackgroundColor(Color.RED);
                    Logger.addLoggingInfo("Device's modem cannot be updated because there is no update file for this modem version.");
                    startRild();

                    Logger.uploadLogs(this, false, "FAIL\nNo update file for this modem version.\n\n");
                }
                break;
            default:
                tvInfo.setText("Device's modem cannot be updated because there is no update file for this modem type.");
                mainLayout.setBackgroundColor(Color.RED);
                Logger.addLoggingInfo("Device's modem cannot be updated because there is no update file for this modem version.");
                startRild();

                Logger.uploadLogs(this, false, "FAIL\nNo update file for this modem version.\n\n");
                break;
        }
    }

    // **************************************************************************************
    // **************************************************************************************
    // ********************************** Update Functions **********************************
    // **************************************************************************************
    // **************************************************************************************

    private void updateModem() {
        readInUpdateFile();
        runOnNewThread(updateRunnable);

        progressBar.setVisibility(View.VISIBLE);
        tvInfo.setText("Sending update file to modem...");
        Logger.addLoggingInfo("Sending update file to modem...");
    }

    private void readInUpdateFile() {
        byte[] bytesFromFile = new byte[6000000];
        InputStream updateInputStream;

        // Select correct delta update
        switch (updateFileType) {
            case V20_00_032:
                updateInputStream = getResources().openRawResource(R.raw.update_032_to_034_4);
                break;
            case V20_00_032_B041:
                updateInputStream = getResources().openRawResource(R.raw.update_032_b041_to_034_4);
                break;
            case V20_00_034_4:
                updateInputStream = getResources().openRawResource(R.raw.update_034_4_to_034_10);
                break;
            case V20_00_034_6:
                updateInputStream = getResources().openRawResource(R.raw.update_034_6_to_034_10);
                break;
            default:
                Log.e(TAG, "ERROR: No update file selected properly. Cannot read in update file.");
                Logger.addLoggingInfo("Error: No update file selected properly. Cannot read in update file.");
                return;
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
        }
    }

    private void updateModemFirmware() {
        packetsSent = 0;

        // Try to connect to modem to send delta to modem
        String resultFromRequestToSend = port.writeRead("AT#OTAUPW\r");
        if (!resultFromRequestToSend.contains("CONNECT")) {
            updateTvInfo("Error updating modem firmware. Reboot device and try again.");
            updateBackgroundColor(Color.RED);
            Logger.addLoggingInfo("Error: after sending AT#OTAUPW, CONNECT not received.");
            return;
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
                    break;
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
                    break;
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
            return;
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
            return;
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
            startRild();
            return;
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

            // updateFileType 1 and 2 should go to 20.00.034.4
            // updateFileType 3 and 4 should go to 20.00.034.10
            if (updateFileType == 1 || updateFileType == 2) {
                if (updatedSoftwareVersion.contains("20.00.034") && updatedExtendedSoftwareVersion
                        .contains("#CFVR: 4")) { // Modem updated successfully
                    pass = true;
                    updateTvModemVersion(formatModemVersion(updatedSoftwareVersion, updatedExtendedSoftwareVersion));

                    port.closePort();
                    Log.d(TAG, "Loop: " + i + ", Str is: " + updatedSoftwareVersion);
                    Log.d(TAG, "Version updated successfully.");
                    break;
                } else if (updatedSoftwareVersion.contains("20.00.032") || updatedSoftwareVersion
                        .contains("20.00.032-B041")) { // Modem not updated successfully
                    updateTvModemVersion(formatModemVersion(updatedSoftwareVersion, updatedExtendedSoftwareVersion));

                    port.closePort();

                    Log.d(TAG, "Loop: " + i + ", Str is: " + updatedSoftwareVersion);
                    Log.d(TAG, "Version not updated successfully");
                    break;
                }
            } else if (updateFileType == 3 || updateFileType == 4) {
                if (updatedSoftwareVersion.contains("20.00.034") && updatedExtendedSoftwareVersion
                        .contains("#CFVR: 10")) { // Modem updated successfully
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
            if (updateFileType == 3 || updateFileType == 4) {
                updateTvInfo("SUCCESS: Device modem updated successfully to 20.00.034.10.");
                Logger.addLoggingInfo("SUCCESS: Device modem updated successfully to 20.00.034.10.");
            } else {
                updateTvInfo("SUCCESS: Device modem updated successfully to 20.00.034.4.\nRerun app to update to 20.00.034.10.");
                Logger.addLoggingInfo("SUCCESS: Device modem updated successfully to 20.00.034.4.\nRerun app to update to 20.00.034.10.");

            }

            updateBackgroundColor(Color.GREEN);
            startRild();
        } else {
            updateTvInfo("ERROR: Device modem not upgraded successfully. Reboot device and try again.");
            Logger.addLoggingInfo("ERROR: Device modem not upgraded successfully. Reboot device and try again.");
            updateBackgroundColor(Color.RED);
            startRild();
        }
    }

    // **************************************************************************************
    // **************************************************************************************
    // *********************************** Helper Methods ***********************************
    // **************************************************************************************
    // **************************************************************************************

    private void delayedShutdown(final int delaySeconds) {
        countDownTimer = new CountDownTimer(delaySeconds * 1000, 15000) {
            public void onTick(long millisUntilFinished) {
                Toast.makeText(context, String.format(Locale.getDefault(), "Rebooting the device in %d seconds.",
                        (int) Math.ceil((float) millisUntilFinished / (float) 1000)), Toast.LENGTH_LONG).show();
            }

            public void onFinish() {
                // Reboot the device
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                pm.reboot(null);
            }
        }.start();
    }

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


}