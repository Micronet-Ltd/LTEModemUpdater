package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.Rild.startRild;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Keep
class Port {

    private static final String TAG = "Updater-Port";

    @Keep
    private FileDescriptor mFd;
    private File port;

    private BufferedInputStream inputStream;
    private BufferedOutputStream outputStream;

    private byte[] readBytes;
    private char[] readChars;

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
            // Create streams to and from the port
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Logger.addLoggingInfo("Error closing port: " + e.toString());
        }
        Logger.addLoggingInfo("Port closed successfully");
    }

    boolean setupPort() {
        int numberOfRetries = 10;

        for (int i = 0; i < numberOfRetries; i++) {
            if (!port.exists()) {
                if (i == numberOfRetries - 1) {
                    // This is the last
                    Log.e(TAG, "Port does not exist. Could not properly update modem firmware. Restarting rild.");
                    Logger.addLoggingInfo("Port does not exist. Could not properly update modem firmware. Restarting rild.");

                    startRild();
                    return false;
                }

                // Sleep and try again to see if ports enumerate
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                continue;
            }

            try {
                // Set up the port with the correct flags.
                mFd = open("/dev/ttyACM0", 9600);
                if (mFd == null) {
                    if (i == numberOfRetries - 1) {
                        Log.e(TAG, "Could not open the port properly for updating modem firmware. Restarting rild.");
                        Logger.addLoggingInfo("Could not open the port properly for updating modem firmware. Restarting rild.");
                        startRild();
                        return false;
                    }

                    // Sleep and try again to see if ports enumerate
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.toString());
                    }

                    continue;
                }
                close();

                // Create streams to and from the port
                inputStream = new BufferedInputStream(new FileInputStream(port));
                outputStream = new BufferedOutputStream(new FileOutputStream(port));
            } catch (Exception e) {
                if (i == numberOfRetries - 1) {
                    Log.e(TAG, e.toString());
                    Logger.addLoggingInfo("Error setting up port: " + e.toString());
                    startRild();
                    return false;
                }

                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Log.e(TAG, ie.toString());
                }

                continue;
            }

            // Successful setup of the port
            break;
        }

        Logger.addLoggingInfo("Successfully setup port");
        return true;
    }

    /**
     * Test the connection with the modem.
     *
     * @return whether a connection was made with the modem
     */
    boolean testConnection() {
        // Try up to 10 times to test connection
        for (int i = 0; i < 10; i++) {
            String output = writeRead("AT\r");

            // Output must contain "OK"
            if (output.contains("OK")) {
                Log.d(TAG, "Able to communicate with modem.");
                return true;
            }
        }

        Log.e(TAG, "Error: unable to communicate with modem.");
        return false;
    }

    String getModemType() {
        // Try 10 times to get the correct modem type
        for (int i = 0; i < 10; i++) {
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
        for (int i = 0; i < 10; i++) {
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

    void write(byte[] arr, int off, int len) throws IOException {
        outputStream.write(arr, off, len);
        outputStream.flush();
    }

    String writeRead(String output) {
        writeToPort(output);
        return readFromPort(8000);
    }

    String writeExtendedRead(String output, String extended) {
        writeToPort(output);
        return readFromPortExtended(extended);
    }


    private void writeToPort(String stringToWrite) {
        try {
            skipAvailable(500);

            outputStream.write(stringToWrite.getBytes());
            outputStream.flush();

            Log.d(TAG, "Sent: " + stringToWrite + ". Sent Bytes: " + Arrays.toString(stringToWrite.getBytes()));
            Logger.addLoggingInfo("WRITE - " + stringToWrite);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    void skipAvailable(int timeout) {
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
                        Thread.sleep(50);
                    }
                }
            };

            ExecutorService executor = Executors.newFixedThreadPool(1);
            Future<Void> future = executor.submit(readTask);
            future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            // Callable will timeout.
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private String readFromPort(int timeout) {
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
            // Give read eight seconds to finish so it won't block for forever
            int numBytesRead = future.get(timeout, TimeUnit.MILLISECONDS);

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
                Logger.addLoggingInfo("READ - " + readString);
                return readString;
            }

        } catch (TimeoutException te) {
            Log.i(TAG, "Timeout exception in read...");
            return "ERROR";
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return "ERROR";
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
            // Give read ten seconds to finish so it won't block for forever
            String result = future.get(10000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            Log.e(TAG, "Looking for " + str + ", " + te.toString());
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Looking for " + str + ", " + e.toString());
            return sb.toString();
        }

        Logger.addLoggingInfo("READ - " + sb.toString());

        return sb.toString();
    }

}
