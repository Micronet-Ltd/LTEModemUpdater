package com.micronet.a317modemupdater;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "ModemUpdater";

    private File port;
    private FileInputStream inputStream;
    private FileOutputStream outputStream;
    private FileDescriptor mFd;

    private byte[] readBytes;
    private char[] readChars;
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

    static {
        System.loadLibrary("port");
    }

    private ConstraintLayout mainLayout;

    private native static FileDescriptor open(String path, int Baudrate);

    private native void close();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Kill rild to be able to communicate with the modem
        killRild();

        // Set up the UI of the application
        setUpApp();

        // Set up the port and communicate with modem if possible
        if (setUpPort()) {
            if (testConnection()) {
                getModemVersionAndType();
            } else {
                tvInfo.setText("Error communicating with the modem. Cannot update modem. Restart and try again.");
                mainLayout.setBackgroundColor(Color.YELLOW);
                startRild();
            }
        }
    }

    // **************************************************************************************
    // **************************************************************************************
    // ******************************* Application Set Up ***********************************
    // **************************************************************************************
    // **************************************************************************************

    private void setUpApp() {
        setTitle("A317 Modem Updater - App Version: " + BuildConfig.VERSION_NAME + ", Device API: " + Build.VERSION.SDK_INT);

        tvInfo = (TextView) findViewById(R.id.tvInfo);
        tvModemType = (TextView) findViewById(R.id.tvModemType);
        tvModemVersion = (TextView) findViewById(R.id.tvModemVersion);
        btnUpdateModem = (Button) findViewById(R.id.btnUpdateModem);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        mainLayout = (ConstraintLayout) findViewById(R.id.mainLayout);

        updateFileType = 0;

        progressBar.setMax(156);
        progressBar.setProgress(0);
        progressBar.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.MULTIPLY);

        btnUpdateModem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readInUpdateFile();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        updateModemFirmware();
                    }
                }).start();

                progressBar.setVisibility(View.VISIBLE);
                tvInfo.setText("Sending update file to modem...");
                btnUpdateModem.setEnabled(false);
            }
        });

        btnUpdateModem.setVisibility(View.GONE);
    }

    private boolean setUpPort() {
        port = new File("/dev/ttyACM0");

        if (!port.exists()) {
            Log.e(TAG, "Port does not exist. Could not properly update modem firmware.");
            tvInfo.setText("Port does not exist. Could not properly update modem firmware. Reboot device and try again.");
            tvModemType.setText("");
            mainLayout.setBackgroundColor(Color.YELLOW);
            startRild();
            return false;
        }

        try {
            // Set up the port with the correct flags.
            mFd = open("/dev/ttyACM0", 9600);
            if (mFd == null) {
                Log.e(TAG, "Could not open the port properly for updating modem firmware.");
                tvInfo.setText("Could not open the port properly for updating modem firmware. Reboot device and try again.");
                mainLayout.setBackgroundColor(Color.YELLOW);
                startRild();
                return false;
            }
            close();

            // Create streams to and from the port
            inputStream = new FileInputStream(port);
            outputStream = new FileOutputStream(port);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        return true;
    }

    private boolean testConnection() {
        writeToPort("AT\r");
        String result = readFromPort();
        return result.contains("OK");
    }

    private void getModemVersionAndType() {
        // Get modem type and version
        writeToPort("AT+CGMM\r");
        String modemType = readFromPort();

        writeToPort("AT+CGMR\r");
        String modemFirmwareVersion = readFromPort();

        writeToPort("AT#CFVR\r");
        String extendedSoftwareVersionNumber = readFromPort();

        // Update modem type and version
        tvModemType.setText("Modem Type: " + modemType.replace("\n", "").replace("OK", ""));
        tvModemVersion.setText("Modem Version: " + modemFirmwareVersion.replace("\n", "").replace("AT+CGMR","").replace("OK", "")
                + "." + extendedSoftwareVersionNumber.replace("\n", "").replace("AT#CFVR","").replace("OK", "").replace("#CFVR: ", ""));

        // Currently not using button
        btnUpdateModem.setEnabled(false);

        // Check modem version to see if it is a version that can be updated.
        if (modemFirmwareVersion.contains("20.00.034") && extendedSoftwareVersionNumber.contains("#CFVR: 10")) {
            tvInfo.setText("Device has 20.00.034.10. Device already updated.");
            mainLayout.setBackgroundColor(Color.GREEN);
            startRild();
        } else if (modemFirmwareVersion.contains("20.00.034") && extendedSoftwareVersionNumber.contains("#CFVR: 6")) {
            tvInfo.setText("Device has 20.00.034.6. Updating to 20.00.034.10.");
            updateFileType = 4;
            updateModem();
        } else if (modemFirmwareVersion.contains("20.00.034") && extendedSoftwareVersionNumber.contains("#CFVR: 4")) {
            tvInfo.setText("Device has 20.00.034.4. Updating to 20.00.034.10.");
            updateFileType = 3;
            updateModem();
        } else if (modemFirmwareVersion.contains("20.00.032-B041")) {
            tvInfo.setText("Device has 20.00.032-B041. Updating to 20.00.034.4.");
            updateFileType = 2;
            updateModem();
        } else if (modemFirmwareVersion.contains("20.00.032")) {
            tvInfo.setText("Device has 20.00.032. Updating to 20.00.034.4.");
            updateFileType = 1;
            updateModem();
        } else {
            tvInfo.setText("Device's modem cannot be updated because there is no update file for this modem version.");
            mainLayout.setBackgroundColor(Color.RED);
            startRild();
        }
    }

    /**
     * Used to detect when an external keyboard has been plugged in or out and makes it so the app does not restart when that happens.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "App kept alive during keyboard plug in and plug out while updating");
    }

    // **************************************************************************************
    // **************************************************************************************
    // ********************************** Update Functions **********************************
    // **************************************************************************************
    // **************************************************************************************

    private void updateModem() {
        readInUpdateFile();
        new Thread(new Runnable() {
            @Override
            public void run() {
                updateModemFirmware();
            }
        }).start();

        progressBar.setVisibility(View.VISIBLE);
        tvInfo.setText("Sending update file to modem...");
    }

    /**
     * Reads in the update file to a byte array.
     */
    private void readInUpdateFile() {

        byte[] bytesFromFile = new byte[6000000];

        InputStream updateInputStream;

        // Select correct delta update
        if (updateFileType == 1) {
            updateInputStream = getResources().openRawResource(R.raw.update_032_to_034_4);
        } else if (updateFileType == 2) {
            updateInputStream = getResources().openRawResource(R.raw.update_032_b041_to_034_4);
        } else if (updateFileType == 3) {
            updateInputStream = getResources().openRawResource(R.raw.update_034_4_to_034_10);
        } else if (updateFileType == 4) {
            updateInputStream = getResources().openRawResource(R.raw.update_034_6_to_034_10);
        }else {
            Log.e(TAG, "ERROR: No update file selected properly. Cannot read in update file.");
            return;
        }

        try {

            int bytesRead = updateInputStream.read(bytesFromFile);

            updateFileBytes = new byte[bytesRead];

            for (int i = 0; i < bytesRead; i++) {
                updateFileBytes[i] = bytesFromFile[i];
            }

            totalUpdateFileSize = bytesRead;

            updateInputStream.close();

            Log.i(TAG, "Update File read in. Bytes read in: " + bytesRead);

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

    private boolean updateModemFirmware() {

        packetsSent = 0;

        // Connect to modem to start sending the update file
        writeToPort("at#otaupw\r");
        String resultFromRequestToSend = readFromPort();

        if (!resultFromRequestToSend.contains("CONNECT")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvInfo.setText("Error updating modem firmware. Reboot device and try again.");
                    mainLayout.setBackgroundColor(Color.RED);
                }
            });
            return false;
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }

        // Send over update file from application
        int counter = 0;
        while (counter < totalUpdateFileSize) {

            if (!((totalUpdateFileSize - counter) < NUM_BYTES_TO_SEND)) {

                try {

                    outputStream.write(updateFileBytes, counter, NUM_BYTES_TO_SEND);

                    outputStream.flush();

                    packetsSent++;

                    Log.d(TAG, "Packet " + packetsSent + " sent. Total Bytes sent: " + (counter + NUM_BYTES_TO_SEND) + " Sent Bytes: " + NUM_BYTES_TO_SEND);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress(packetsSent);
                        }
                    });

                    counter += NUM_BYTES_TO_SEND;

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    break;
                }


            } else {
                try {

                    outputStream.write(updateFileBytes, counter, (totalUpdateFileSize - counter));

                    outputStream.flush();

                    packetsSent++;

                    Log.d(TAG, "Packet " + packetsSent + " sent. Total Bytes sent: " + (counter + (totalUpdateFileSize - counter)) + " Sent Bytes: " + (totalUpdateFileSize - counter));

                    counter += totalUpdateFileSize - counter;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress(packetsSent);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    break;
                }
            }

            try {
                Thread.sleep(300);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }

        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }

        // Send +++ to signal end of updating. Should receive NO CARRIER back.
        writeToPort("+++");
        String result = readFromPort();

        if (!result.contains("NO CARRIER")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainLayout.setBackgroundColor(Color.RED);
                    tvInfo.setText("File not sent successfully. Reboot device and try again.");
                    Log.e(TAG, "After sending \"+++\", \"NO CARRIER\" not received.");
                }
            });
            return false;
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvInfo.setText("File sent. Validating file.");
                }
            });
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }

        // Validate delta
        writeToPort("at#otaup=1\r");
        String resultValidate = readFromPortExtended("OK");

        if (!resultValidate.contains("OK")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainLayout.setBackgroundColor(Color.RED);
                    tvInfo.setText("File not sent properly. Reboot device and try again.");
                    Log.e(TAG, "After sending \"at#otaup=1\", \"OK\" not received.");
                }
            });
            return false;
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvInfo.setText("File validated.");
                }
            });
        }

        // Validation and start of firmware update
        writeToPort("at#otaup=0,2\r");

        final String updateResult = readFromPortExtended("OK");

        if (updateResult.contains("OK")) {
            Log.d(TAG, "File validated and update process starting");
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainLayout.setBackgroundColor(Color.RED);
                    tvInfo.setText("Modem not updated successfully. Reboot device and try again.");
                    Log.e(TAG, updateResult);
                    Log.e(TAG, "After sending \"at#otaup=0,2\", \"OK\" not received.");
                    startRild();
                }
            });

            return false;
        }

        boolean pass = false;

        try {
            inputStream.close();
            outputStream.close();
            port = null;
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvInfo.setText("Waiting 2-5 minutes to check if modem updated properly.");
            }
        });

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }

        for (int i = 0; i < 90; i++) {

            port = new File("/dev/ttyACM0");

            if (!port.exists()) {
                Log.e(TAG, "Loop: " + i + ", Port does not exist. Sleeping then checking next iteration.");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }
                continue;
            }

            try {
                // Create streams to and from the port
                inputStream = new FileInputStream(port);
                outputStream = new FileOutputStream(port);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }

            writeToPort("AT+CGMR\r");
            final String updated = readFromPort();

            writeToPort("AT#CFVR\r");
            final String extendedSoftwareVersionNumber = readFromPort();

            // updateFileType 3 and 4 are the only ones that update to 34.10.
            if (updateFileType != 3 && updateFileType != 4) {
                if (updated.contains("20.00.034") && extendedSoftwareVersionNumber.contains("#CFVR: 4")) {
                    pass = true;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvModemVersion.setText("Modem Version: " + updated.replace("\n", "").replace("AT+CGMR","").replace("OK", "") +
                                    "." + extendedSoftwareVersionNumber.replace("\n", "").replace("AT#CFVR","").replace("OK", "").replace("#CFVR: ", ""));
                        }
                    });

                    try {
                        inputStream.close();
                        outputStream.close();
                        port = null;
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }

                    Log.d(TAG, "Loop: " + i + ", Str is: " + updated);
                    Log.d(TAG, "Version updated successfully.");

                    break;
                }
            } else {
                if (updated.contains("20.00.034") && extendedSoftwareVersionNumber.contains("#CFVR: 10")) {
                    pass = true;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvModemVersion.setText("Modem Version: " + updated.replace("\n", "").replace("AT+CGMR","").replace("OK", "") +
                                    "." + extendedSoftwareVersionNumber.replace("\n", "").replace("AT#CFVR","").replace("OK", "").replace("#CFVR: ", ""));
                        }
                    });

                    try {
                        inputStream.close();
                        outputStream.close();
                        port = null;
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }

                    Log.d(TAG, "Loop: " + i + ", Str is: " + updated);
                    Log.d(TAG, "Version updated successfully.");

                    break;
                }
            }

            if (updated.contains("20.00.032") || updated.contains("20.00.032-B041")) {
                pass = false;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvModemVersion.setText("Modem Version: " + updated.replace("\n", "").replace("AT+CGMR","").replace("OK", "") +
                                "." + extendedSoftwareVersionNumber.replace("\n", "").replace("AT#CFVR","").replace("OK", "").replace("#CFVR: ", ""));
                    }
                });

                try {
                    inputStream.close();
                    outputStream.close();
                    port = null;
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }

                Log.d(TAG, "Loop: " + i + ", Str is: " + updated);
                Log.d(TAG, "Version not updated successfully");

                break;
            }

            try {
                inputStream.close();
                outputStream.close();
                port = null;
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }

            Log.d(TAG, "Loop: " + i + ", Str is: " + updated);

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }

        if (pass) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainLayout.setBackgroundColor(Color.GREEN);
                    if(updateFileType == 3 || updateFileType == 4){
                        tvInfo.setText("SUCCESS: Device modem updated successfully to 20.00.034.10.");
                    }else{
                        tvInfo.setText("SUCCESS: Device modem updated successfully to 20.00.034.4. Rerun app to update to 20.00.034.10.");
                    }

                    startRild();
                }
            });
            return true;

        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainLayout.setBackgroundColor(Color.RED);
                    tvInfo.setText("ERROR: Device modem not upgraded successfully. Reboot device and try again.");
                    startRild();
                }
            });
            return false;

        }
    }

    // **************************************************************************************
    // **************************************************************************************
    // ******************************* Modem Communication **********************************
    // **************************************************************************************
    // **************************************************************************************

    private void startRild() {
        try {
            String commands[] = {"/system/bin/setprop", "ctl.start", "ril-daemon"};
            Process process = Runtime.getRuntime().exec(commands);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Log.d(TAG, line);
            }
            Log.d(TAG, "Rild started");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void killRild() {
        try {
            String commands[] = {"/system/bin/setprop", "ctl.stop", "ril-daemon"};
            Process process = Runtime.getRuntime().exec(commands);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Log.d(TAG, line);
            }
            Log.d(TAG, "Rild killed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Reads from the port.
     *
     * @return A string of the bytes read.
     */
    private String readFromPort() {

        readBytes = new byte[2056];

        try {

            Callable<Integer> readTask = new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return inputStream.read(readBytes);
                }
            };

            ExecutorService executor = Executors.newFixedThreadPool(1);

            Future<Integer> future = executor.submit(readTask);
            // Give read two seconds to finish so it won't block for forever
            int numBytesRead = future.get(8000, TimeUnit.MILLISECONDS);

            if (numBytesRead == -1) {
                return "";
            } else {
                readChars = new char[numBytesRead];
                byte[] onlyReadBytes = new byte[numBytesRead];

                for (int i = 0; i < numBytesRead; i++) {
                    readChars[i] = (char) readBytes[i];
                    onlyReadBytes[i] = readBytes[i];
                }

                String readString = new String(readChars);

                Log.d(TAG, "Received: " + readString + ". Received Bytes: " + Arrays.toString(onlyReadBytes));

                return readString;
            }

        } catch (TimeoutException te) {
            Log.e(TAG, "Timeout exception in read...");
            return "error timeout exception";
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return "error";
        }

    }

    private String readFromPortExtended(final String str) {

        readBytes = new byte[4096];

        final StringBuilder sb = new StringBuilder();

        try {
            Callable<String> readTask = new Callable<String>() {
                @Override
                public String call() throws Exception {
                    boolean continueLoop = true;

                    while (continueLoop) {
                        try {

                            int numBytesRead = inputStream.read(readBytes);

                            if (numBytesRead == -1) {
                                Log.d(TAG, "Num bytes read is -1, stopping loop");
                                continueLoop = false;
                            } else {
                                readChars = new char[numBytesRead];
                                byte[] onlyReadBytes = new byte[numBytesRead];

                                for (int i = 0; i < numBytesRead; i++) {
                                    readChars[i] = (char) readBytes[i];
                                    onlyReadBytes[i] = readBytes[i];
                                }

                                String readString = new String(readChars);

                                sb.append(readString);

                                Log.d(TAG, "Received: " + readString + ". Received Bytes: " + Arrays.toString(onlyReadBytes));
                                Log.d(TAG, "Full string builder from extended: " + sb.toString());
                            }

                            if (sb.length() > 0 && sb.toString().contains(str)) {
                                continueLoop = false;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                            return "error";
                        }
                    }
                    return sb.toString();
                }
            };

            ExecutorService executor = Executors.newFixedThreadPool(1);

            Future<String> future = executor.submit(readTask);
            // Give read two seconds to finish so it won't block for forever
            String result = future.get(10000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            Log.e(TAG, "Looking for " + str + ", " + te.toString());
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Looking for " + str + ", " + e.toString());
            return sb.toString();
        }

        return sb.toString();
    }

    /**
     * Writes to the port.
     *
     * @param stringToWrite - the string to write to the port.
     */
    private void writeToPort(String stringToWrite) {

        try {

            skipAvailable();

            outputStream.write(stringToWrite.getBytes());
            outputStream.flush();

            Log.d(TAG, "Sent: " + stringToWrite + ". Sent Bytes: " + Arrays.toString(stringToWrite.getBytes()));

        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

    }

    /**
     * Skip the other information received back from the modem.
     */
    private void skipAvailable() {

        readBytes = new byte[2056];

        try {

            if (inputStream.available() > 0) {

                Callable<Integer> readTask = new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return inputStream.read(readBytes);
                    }
                };

                ExecutorService executor = Executors.newFixedThreadPool(1);

                Future<Integer> future = executor.submit(readTask);
                // Give read two seconds to finish so it won't block for forever
                int numBytesRead = future.get(1000, TimeUnit.MILLISECONDS);

                readChars = new char[numBytesRead];
                byte[] onlyReadBytes = new byte[numBytesRead];

                for (int i = 0; i < numBytesRead; i++) {
                    readChars[i] = (char) readBytes[i];
                    onlyReadBytes[i] = readBytes[i];
                }

                String readString = new String(readChars);

                Log.d(TAG, "Received and skipped: " + readString + ". Received Bytes: " + Arrays.toString(onlyReadBytes));
            }
        } catch (TimeoutException e) {
            Log.e(TAG, "Skip available timed out..");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}