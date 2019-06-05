package com.micronet.a317modemupdater;

import static org.junit.Assert.*;

import android.support.test.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;

public class RildTest {

    @Before
    public void setUp(){
        Logger.prepareLogger(InstrumentationRegistry.getContext());
    }

    @Test
    public void startRild() {
        assertTrue(Rild.startRild());
    }

    @Test
    public void killRild() {
        assertTrue(Rild.stopRild());
    }

    @Test
    public void rildStressTest() {
        for(int i = 0; i < 200; i++){
            assertTrue(Rild.startRild());
            assertTrue(Rild.stopRild());
        }
    }
}