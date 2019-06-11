package com.micronet.a317modemupdater;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Updater-Main";
    private static CountDownTimer countDownTimer;
    static final String UPDATED_KEY = "Updated";
    static final String UPLOADED_KEY = "Uploaded";
    static final String SHARED_PREF_KEY = "LTEModemUpdater";
    private final Context context = this;

    private TextView tvInfo;
    private TextView tvModemType;
    private TextView tvModemVersion;
    private TextView tvWarning;
    private Button btnCancelShutdown;
    private ProgressBar progressBar;
    private ConstraintLayout mainLayout;
    private Updater updater;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Check modem firmware and update if needed.
        String modemStatus = "Modem firmware hasn't been updated yet.";
        Log.i(TAG, modemStatus);
        Toast.makeText(context, modemStatus, Toast.LENGTH_LONG).show();

        // Setup UI.
        setContentView(R.layout.activity_main);
        setUpUi();
        tvInfo.setText("Preparing to check modem version...");

        updater = new Updater(this);
        updater.startUpdateProcess();
    }

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
    }

    // **************************************************************************************
    // **************************************************************************************
    // *********************************** Helper Methods ***********************************
    // **************************************************************************************
    // **************************************************************************************

    boolean isUpdated() {
        SharedPreferences sharedPreferences = this.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(UPDATED_KEY, false);
    }

    boolean isUploaded() {
        SharedPreferences sharedPreferences = this.getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(UPLOADED_KEY, false);
    }

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