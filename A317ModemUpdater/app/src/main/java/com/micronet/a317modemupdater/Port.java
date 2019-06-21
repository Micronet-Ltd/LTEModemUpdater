package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.Rild.configureRild;

import android.support.annotation.Keep;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Keep
class Port {

    private static final String TAG = "Updater-Port";
    private static final String READ_ERROR_STR = "ERROR";
    private static final String PORT_PATH = "/dev/ttyACM0";
    private static final int NUM_MODEM_TYPE_RETRIES = 10;
    private static final int NUM_MODEM_VERSION_RETRIES = 10;
    private static final int NUM_PORT_SETUP_RETRIES = 10;
    private static final int SKIP_AVAILABLE_TIMEOUT = 500;
    private static final int READ_FROM_PORT_TIEMOUT = 8000;
    private static final int READ_FROM_PORT_EXTENDED_TIMEOUT = 10000;
    private static final int READ_BYTES_SIZE = 2056;
    private static final int PORT_BAUDRATE = 9600;
    private static final int PORT_SETUP_WAIT = 200;
    private static final int NO_BYTES_TO_READ = -1;
    private static final int SKIP_AVAILABLE_WAIT = 50;

    @Keep
    private FileDescriptor mFd;
    private File port;
    private BufferedInputStream inputStream;
    private BufferedOutputStream outputStream;
    private byte[] readBytes;
    private char[] readChars;

    // JNI Code
    static {
        System.loadLibrary("port");
    }

    private native static FileDescriptor open(String path, int Baudrate);
    private native void close();

    Port(String path) {
        port = new File(path);
    }

    boolean exists() {
        return port.exists();
    }

    boolean openPort() {
        try {
            // Create streams to and from the port
            inputStream = new BufferedInputStream(new FileInputStream(port));
            outputStream = new BufferedOutputStream(new FileOutputStream(port));
        } catch (Exception e) {
            Log.e(TAG, e.toString());

            Logger.addLoggingInfo("Error opening port: " + e.toString());
            return false;
        }

        Logger.addLoggingInfo("Port opened successfully");
        return true;
    }

    void closePort() {
        try {
            // Try to close the input/output streams if they aren't null.
            if (inputStream != null) {
                inputStream.close();
            }

            if (outputStream != null) {
                outputStream.close();
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Logger.addLoggingInfo("Error closing port: " + e.toString());
        }
        Logger.addLoggingInfo("Port closed successfully");
    }

    boolean setupPort() {
        for (int i = 0; i < NUM_PORT_SETUP_RETRIES; i++) {
            if (port.exists()) {
                // Set up the port with the correct flags.
                mFd = open(PORT_PATH, PORT_BAUDRATE);
                if (mFd != null) {
                    close();
                    if (openPort()) {
                        Logger.addLoggingInfo("Successfully setup port");
                        return true;
                    }
                }
            }

            try {
                Thread.sleep(PORT_SETUP_WAIT);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }

        String errorString = "Error setting up port for updating modem firmware. Restarting Rild.";
        Log.e(TAG, errorString);
        Logger.addLoggingInfo(errorString);
        configureRild(true);
        return false;
    }

    String getModemType() {
        // Try 10 times to get the correct modem type
        for (int i = 0; i < NUM_MODEM_TYPE_RETRIES; i++) {
            String modemType = writeRead("AT+CGMM\r").replace("\n", "").replace("OK", "").replace("AT+CGMM","");

            // Modem type must match something like LE910-NA1
            if (modemType.matches("\\w+-\\w+")) {
                Log.d(TAG, "Modem type is " + modemType);
                return modemType.trim();
            }
        }

        Log.e(TAG, "Error: modem type is unknown.");
        return "UNKNOWN";
    }

    String getModemVersion() {
        // Try 10 times to get the correct modem version
        for (int i = 0; i < NUM_MODEM_VERSION_RETRIES; i++) {
            String modemFirmwareVersion = writeRead("AT+CGMR\r");
            String extendedSoftwareVersionNumber = writeRead("AT#CFVR\r");

            String modemVersion = formatModemVersion(modemFirmwareVersion, extendedSoftwareVersionNumber).trim();

            // modemVersion must match something like 20.00.522.7
            if (modemVersion.matches("\\d+\\.\\d+\\.\\d+.\\d+")) {
                Log.d(TAG, "Modem version is " + modemVersion);
                return modemVersion.trim();
            }
        }

        Log.e(TAG, "Error: modem version is unknown.");
        return "UNKNOWN";
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

    /////////////////////////////////////////////////////////
    //////// IO Functions
    /////////////////////////////////////////////////////////

    void write(byte[] arr, int off, int len) throws IOException {
        outputStream.write(arr, off, len);
        outputStream.flush();
    }

    private void writeToPort(String stringToWrite) {
        try {
            skipAvailable(SKIP_AVAILABLE_TIMEOUT);

            if (outputStream != null) {
                outputStream.write(stringToWrite.getBytes());
                outputStream.flush();

                Log.d(TAG, "Sent: " + stringToWrite + ". Sent Bytes: " + Arrays.toString(stringToWrite.getBytes()));
                Logger.addLoggingInfo("WRITE - " + stringToWrite);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private String readFromPort(int timeout) {
        try {
            // Try to read from port with timeout.
            readBytes = new byte[READ_BYTES_SIZE];
            Callable<Integer> readTask = new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return inputStream.read(readBytes);
                }
            };
            int numBytesRead = Executors.newFixedThreadPool(1).submit(readTask).get(timeout, TimeUnit.MILLISECONDS);

            // Handle what is returned.
            if (numBytesRead == NO_BYTES_TO_READ) {
                return "";
            } else {
                String readString = convertBytesToString(numBytesRead);
                Log.d(TAG, "Received: " + readString);
                Logger.addLoggingInfo("READ - " + readString);
                return readString;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return READ_ERROR_STR;
        }
    }

    String writeRead(String output) {
        writeToPort(output);
        return readFromPort(READ_FROM_PORT_TIEMOUT);
    }

    String writeExtendedRead(String output, String extended) {
        writeToPort(output);
        return readFromPortExtended(extended);
    }

    private String readFromPortExtended(final String str) {
        final StringBuilder sb = new StringBuilder();

        try {
            Callable<String> readTask = new Callable<String>() {
                @Override
                public String call() {
                    boolean continueLoop = true;
                    while (continueLoop) {
                        try {
                            int numBytesRead = inputStream.read(readBytes);

                            if (numBytesRead == NO_BYTES_TO_READ) {
                                Log.d(TAG, "No bytes to read from input stream.");
                                continueLoop = false;
                            } else {
                                // Get string from read bytes
                                String readString = convertBytesToString(numBytesRead);
                                sb.append(readString);
                                Log.d(TAG, "Received: " + readString + ".");
                                Log.d(TAG, "Full string builder from extended: " + sb.toString());

                                // Check to see if output contains the string we are looking for.
                                if (sb.length() > 0 && sb.toString().contains(str)) {
                                    continueLoop = false;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                            return READ_ERROR_STR;
                        }
                    }
                    return sb.toString();
                }
            };

            Executors.newFixedThreadPool(1).submit(readTask).get(READ_FROM_PORT_EXTENDED_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Looking for " + str + ", " + e.toString());
            return sb.toString();
        }

        Logger.addLoggingInfo("READ - " + sb.toString());
        return sb.toString();
    }

    private String convertBytesToString(int numBytesRead) {
        readChars = new char[numBytesRead];

        for (int i = 0; i < numBytesRead; i++) {
            readChars[i] = (char) readBytes[i];
        }

        return new String(readChars);
    }

    private void skipAvailable(int timeout) {
        try {
            Callable<Void> readTask = new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    while (true) {
                        int available = inputStream.available();
                        if (available > 0) {
                            long skipped = inputStream.skip(available);
                            Log.i(TAG, String.format("Skipped %d bytes", skipped));
                        }
                        Thread.sleep(SKIP_AVAILABLE_WAIT);
                    }
                }
            };

            Executors.newFixedThreadPool(1).submit(readTask).get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Do nothing
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
