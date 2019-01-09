package com.micronet.a317modemupdater;

import static org.junit.Assert.*;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.telephony.TelephonyManager;
import com.dropbox.core.v2.sharing.InsufficientPlan;
import org.junit.Before;
import org.junit.Test;

public class DropBoxTest {

    private DropBox dropBox;

    @Before
    public void setUp(){
        dropBox = new DropBox(InstrumentationRegistry.getContext());
    }

    @Test
    public void uploadFile() {
        TelephonyManager telephonyManager = (TelephonyManager) InstrumentationRegistry.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        for(int i = 0; i < 10; i++){
            assertTrue(dropBox.uploadFile(telephonyManager.getDeviceId(), "This is " + i));
        }
    }
}