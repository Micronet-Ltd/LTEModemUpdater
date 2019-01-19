package com.micronet.a317modemupdater;

import static org.junit.Assert.*;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.telephony.TelephonyManager;
import org.junit.Before;
import org.junit.Test;

public class DropBoxTest {

    private DropBox dropBox;

    @Before
    public void setUp(){
        dropBox = new DropBox(InstrumentationRegistry.getContext());
    }

    @Test
    public void multipleUploadFile() {
        TelephonyManager telephonyManager = (TelephonyManager) InstrumentationRegistry.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        for(int i = 0; i < 10; i++){
            // Needs to have a connection to the internet to pass
            assertTrue(dropBox.uploadLogs("FAKE_TIME_TEST", telephonyManager.getDeviceId(), "This is " + i + ".", true));
        }
    }
}