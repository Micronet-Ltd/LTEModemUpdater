package com.micronet.a317modemupdater;

import static com.micronet.a317modemupdater.Rild.startRild;

import android.util.Log;
import java.io.BufferedInputStream;
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

class Port {

    private static final String TAG = "Updater-Port";

    private File port;

    private FileInputStream inputStream;
    private FileOutputStream outputStream;
    private FileDescriptor mFd;

    private byte[] readBytes;
    private char[] readChars;

    static {
        System.loadLibrary("port");
    }

    private native static FileDescriptor open(String path, int Baudrate);
    private native void close();

    Port(String path){
        port = new File(path);
    }

    boolean exists(){
        return port.exists();
    }

    boolean openPort(){
        try {
            // Create streams to and from the port
            inputStream = new FileInputStream(port);
            outputStream = new FileOutputStream(port);
        } catch (Exception e) {
            Log.e(TAG, e.toString());

            Logger.addLoggingInfo("Error opening port: " + e.toString());
            return false;
        }

        Logger.addLoggingInfo("Port opened successfully");
        return true;
    }

    void closePort(){
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

    boolean setupPort(){
        if (!port.exists()) {
            Log.e(TAG, "Port does not exist. Could not properly update modem firmware. Restarting rild.");
            Logger.addLoggingInfo("Port does not exist. Could not properly update modem firmware. Restarting rild.");

            startRild();
            return false;
        }

        try {
            // Set up the port with the correct flags.
            mFd = open("/dev/ttyACM0", 9600);
            if (mFd == null) {
                Log.e(TAG, "Could not open the port properly for updating modem firmware. Restarting rild.");
                Logger.addLoggingInfo("Could not open the port properly for updating modem firmware. Restarting rild.");
                startRild();
                return false;
            }
            close();

            // Create streams to and from the port
            inputStream = new FileInputStream(port);
            outputStream = new FileOutputStream(port);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Logger.addLoggingInfo("Error setting up port: " + e.toString());
        }

        Logger.addLoggingInfo("Successfully setup port");
        return true;
    }

    /**
     * Test the connection with the modem.
     * @return whether a connection was made with the modem
     */
    boolean testConnection(){
        writeToPort("AT\r");
        String result = readFromPort();
        return result.contains("OK");
    }

    void write(byte[] arr, int off, int len) throws IOException {
        outputStream.write(arr, off, len);
        outputStream.flush();
    }

    String writeRead(String output){
        writeToPort(output);
        return readFromPort();
    }

    String writeExtendedRead(String output, String extended){
        writeToPort(output);
        return readFromPortExtended(extended);
    }


    private void writeToPort(String stringToWrite) {
        try {
            skipAvailable();

            outputStream.write(stringToWrite.getBytes());
            outputStream.flush();

            Log.d(TAG, "Sent: " + stringToWrite + ". Sent Bytes: " + Arrays.toString(stringToWrite.getBytes()));
            Logger.addLoggingInfo("WRITE - " + stringToWrite);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    void skipAvailable() {
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
                Logger.addLoggingInfo("SKIPPED - " + readString);
            }
        } catch (TimeoutException e) {
            Log.e(TAG, "Skip available timed out..");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

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
            // Give read eight seconds to finish so it won't block for forever
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
                Logger.addLoggingInfo("READ - " + readString);
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
