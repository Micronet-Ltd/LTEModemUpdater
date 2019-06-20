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
import com.micronet.a317modemupdater.receiver.NetworkStateReceiver;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

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

            updater = new Updater(this);
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

    void delayedShutdown(final int delaySeconds) {
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

    void cancelShutdown() {
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

    void updateTvInfo(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvInfo.setText(text);
            }
        });
    }

    void updateTvModemType(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvModemType.setText(text);
            }
        });
    }

    void updateTvModemVersion(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvModemVersion.setText(text);
            }
        });
    }

    void updateTvWarning(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvWarning.setText(text);
            }
        });
    }

    void updateBackgroundColor(final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainLayout.setBackgroundColor(color);
            }
        });
    }

    void setSendProgress(final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setProgress(progress);
            }
        });
    }

    void setProgressBarVisibility(final int visibility) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(visibility);
            }
        });
    }

    void setProgressBarProgress(final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setProgress(progress);
            }
        });
    }

    void setProgressBarMax(final int max) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setMax(max);
            }
        });
    }
}