package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.Utils.getImei;
import static com.micronet.a317modemupdater.Utils.getSerial;
import static com.micronet.a317modemupdater.Utils.isUpdated;
import static com.micronet.a317modemupdater.Utils.isUploaded;
import static com.micronet.a317modemupdater.Utils.runShellCommand;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.micronet.a317modemupdater.interfaces.UpdateState;
import com.micronet.a317modemupdater.receiver.NetworkStateReceiver;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements UpdateState {

    private static final String TAG = "Updater-Main";
    private static final String UPDATE_SUCCESSFUL_ACTION = "com.micronet.dsc.resetrb.modemupdater.UPDATE_SUCCESSFUL_ACTION";
    private static CountDownTimer countDownTimer;
    private final Context context = this;

    private TextView tvInfo;
    private TextView tvModemType;
    private TextView tvModemVersion;
    private TextView tvWarning;
    private Button btnCancelShutdown;
    private ProgressBar progressBar;
    private ConstraintLayout mainLayout;
    private Updater updater;
    private NetworkStateReceiver networkStateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // First check to see if device is already updated and uploaded.
        boolean updated = isUpdated(this);
        boolean uploaded = isUploaded(this);

        // We check three cases:
        //  - If the modem is updated and logs are uploaded then don't start the application and send broadcast of a successful update.
        //  - If the modem is updated but the logs aren't uploaded then keep trying to upload the logs.
        //  - Else upload precheck log to Dropbox, check the modem firmware version, and update if needed.
        if (updated && uploaded) {
            // Don't start the application and display toast.
            String modemResult = "Modem firmware already updated and uploaded. Activity not starting.";
            Toast.makeText(context, modemResult, Toast.LENGTH_LONG).show();
            Log.i(TAG, modemResult);

            // Resend broadcast to ResetRB to begin clean up. ResetRB will clear Communitake data and uninstall this application.
            Intent successfulUpdateIntent = new Intent(UPDATE_SUCCESSFUL_ACTION);
            sendBroadcast(successfulUpdateIntent);
            finish();
        } else if (updated) {
            // Only work on uploading logs
            String logStatus = "Modem firmware already updated but logs not uploaded.";
            Toast.makeText(context, logStatus, Toast.LENGTH_LONG).show();
            Log.i(TAG, logStatus);

            // Set up UI to inform user that logs are trying to be uploaded.
            setContentView(R.layout.activity_main);
            setUpUi();
            tvInfo.setText("Modem Firmware already updated. Trying to upload logs if any.");

            // Start process of uploading logs from database.
            Logger.prepareLogger(context);
            Logger.uploadSavedLogs(context);
        } else {
            // Check modem firmware and update if needed.
            String modemStatus = "Modem firmware hasn't been updated yet.";
            Log.i(TAG, modemStatus);

            // Setup UI.
            setContentView(R.layout.activity_main);
            setUpUi();
            tvInfo.setText("Preparing to check modem version...");

            updater = new Updater(getApplicationContext(), this);
            updater.startUpdateProcess();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "App kept alive during configuration change.");
    }

    @Override
    protected void onStart() {
        super.onStart();

        networkStateReceiver = new NetworkStateReceiver();
        this.registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onStop() {
        super.onStop();

        this.unregisterReceiver(networkStateReceiver);
        networkStateReceiver = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Logger.db != null && Logger.db.isOpen()) {
            Log.d(TAG, "Closing modem upgrader log database");
            Logger.db.close();
        }
    }

    private void setUpUi() {
        setTitle("A317 Modem Updater - App Version: " + BuildConfig.VERSION_NAME + ", Device API: " + Build.VERSION.SDK_INT);

        tvInfo = findViewById(R.id.tvInfo);
        tvModemType = findViewById(R.id.tvModemType);
        tvModemVersion = findViewById(R.id.tvModemVersion);
        tvWarning = findViewById(R.id.tvWarning);
        btnCancelShutdown = findViewById(R.id.btnCancelShutdown);
        progressBar = findViewById(R.id.progressBar);
        mainLayout = findViewById(R.id.mainLayout);

        progressBar.setMax(156);
        progressBar.setProgress(0);
        progressBar.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.MULTIPLY);

        btnCancelShutdown.setVisibility(View.INVISIBLE);
        btnCancelShutdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(countDownTimer != null) {
                    cancelShutdown();
                }
            }
        });

        ((TextView)findViewById(R.id.tvSerial)).setText("Serial: " + getSerial());
        ((TextView)findViewById(R.id.tvImei)).setText("IMEI: " + getImei(context));

        findViewById(R.id.btnRedbendCheckIn).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    runShellCommand(new String[]{"am", "broadcast", "-a", "SwmClient.CHECK_FOR_UPDATES_NOW"});
                    Toast.makeText(context, "Sent intent to check in on Redbend.", Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    Log.e("CheckForUpdates", e.toString());
                    Toast.makeText(context, "Error sending intent to check in on Redbend.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // **************************************************************************************
    // **************************************************************************************
    // *********************************** Helper Methods ***********************************
    // **************************************************************************************
    // **************************************************************************************

    public void delayedShutdown(final int delaySeconds) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                countDownTimer = new CountDownTimer(delaySeconds * 1000, 30000) {
                    public void onTick(long millisUntilFinished) {
                        String display = String.format(Locale.getDefault(), "Rebooting the device in %d seconds.",
                                (int) Math.ceil((float) millisUntilFinished / (float) 1000));
                        Log.d(TAG, "Tick: " + display);
                        Toast.makeText(context, display, Toast.LENGTH_LONG).show();
                    }

                    public void onFinish() {
                        // Reboot the device
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        pm.reboot(null);
                    }
                }.start();
                btnCancelShutdown.setVisibility(View.VISIBLE);
                btnCancelShutdown.setEnabled(true);
            }
        });

    }

    public void cancelShutdown() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                countDownTimer.cancel();
                btnCancelShutdown.setVisibility(View.INVISIBLE);
                btnCancelShutdown.setEnabled(false);
                Toast.makeText(context, "Shutdown timer canceled.", Toast.LENGTH_LONG).show();
            }
        });
    }

    ///////////////////////////////////////////////////////
    ////////////////// Update UI Methods //////////////////
    ///////////////////////////////////////////////////////

    @Override
    public void couldNotConfigureRild() {
        updateTvInfo("Error killing rild. Could not properly update modem firmware. Reboot device and try again.");
        updateBackgroundColor(Color.YELLOW);
    }

    @Override
    public void couldNotSetupPort() {
        updateTvInfo("Could not setup the port properly for updating modem firmware. Reboot device and try again.");
        updateBackgroundColor(Color.YELLOW);
        updateTvWarning("Firmware not updated successfully.");
    }

    @Override
    public void couldNotCommunicateWithModem() {
        updateTvInfo("Error communicating with the modem. Cannot update modem.\nRestart and try again. Restarting rild.");
        updateBackgroundColor(Color.YELLOW);
        updateTvWarning("Firmware not updated successfully.");
    }

    @Override
    public void initialModemTypeAndVersion(String modemType, String modemVersion) {
        updateTvModemType(modemType);
        updateTvModemVersion(modemVersion);
    }

    @Override
    public void noUpdateFileForModem() {
        updateTvInfo("Device's modem cannot be updated because there is no update file for this modem version.");
        updateBackgroundColor(Color.RED);
        updateTvWarning("Firmware not updated successfully.");
    }

    @Override
    public void alreadyUpdated(String modemVersion) {
        updateTvInfo("Device has " + modemVersion + ". Already updated.");
        updateBackgroundColor(Color.GREEN);
    }

    @Override
    public void attemptingToUpdate(String modemVersion) {
        updateTvInfo("Device has " + modemVersion + ". Trying to update.");
    }

    @Override
    public void sendingUpdateFileToModem() {
        setProgressBarVisibility(View.VISIBLE);
        updateTvInfo("Sending update file to modem...");
    }

    @Override
    public void loadedUpdateFile(int max) {
        setProgressBarMax(max);
        setProgressBarProgress(0);
    }

    @Override
    public void errorLoadingUpdateFile() {
        updateTvInfo("Error loading update file or no update file found.");
        updateBackgroundColor(Color.YELLOW);
        updateTvWarning("Firmware not updated successfully.");
    }

    @Override
    public void errorConnectingToModemToSendUpdateFile() {
        updateTvInfo("Error updating modem firmware. Reboot device and try again.");
        updateBackgroundColor(Color.RED);
    }

    @Override
    public void updateSendProgress(int packetsSent) {
        setProgressBarProgress(packetsSent);
    }

    @Override
    public void errorFileNotSentSuccessfully() {
        updateTvInfo("File not sent successfully. Reboot device and try again.");
        updateBackgroundColor(Color.RED);
    }

    @Override
    public void fileSentSuccessfully() {
        updateTvInfo("File sent. Validating file.");
    }

    @Override
    public void errorFileNotValidated() {
        updateTvInfo("File not sent properly. Reboot device and try again.");
        updateBackgroundColor(Color.RED);
    }

    @Override
    public void fileValidatedSuccessfully() {
        updateTvInfo("File validated.");
    }

    @Override
    public void errorFileNotValidatedAndUpdateProcessNotStarting() {
        updateTvInfo("Modem not updated successfully. Reboot device and try again.");
        updateBackgroundColor(Color.RED);
    }

    @Override
    public void updateProcessStarting() {
        updateTvInfo("Waiting 2-5 minutes to check if modem updated properly.");
    }

    @Override
    public void updatedModemFirmwareVersion(String modemVersion) {
        updateTvModemVersion("Modem Version: " + modemVersion);
    }

    @Override
    public void errorRestartModem() {
        updateTvInfo("Trying to reboot modem to try again.");
    }

    @Override
    public void successfullyUpdatedUploadingLogs() {
        updateBackgroundColor(Color.YELLOW);
        updateTvInfo("Do not power off. Sending logs");
    }

    @Override
    public void failureUpdatingUploadingLogs() {
        updateTvInfo("ERROR: Modem not upgraded successfully. Trying to upload logs.");
        updateBackgroundColor(Color.RED);
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

    private void updateTvWarning(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvWarning.setText(text);
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

    private void setProgressBarProgress(final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setProgress(progress);
            }
        });
    }

    private void setProgressBarMax(final int max) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setMax(max);
            }
        });
    }
}