package com.micronet.a317modemupdater;

import static org.junit.Assert.*;

import android.support.test.InstrumentationRegistry;
import org.junit.Test;

public class PortTest {

    private Port setupPortAndTestConnection(){
        assertTrue(Rild.stopRild());

        Port port = new Port("/dev/ttyACM0");

        assertTrue(port.exists());
        assertTrue(port.setupPort());
        assertTrue(port.testConnection());

        return port;
    }

    @Test
    public void getModemVersionAndTypeStressTest(){
        for(int i = 0; i < 5; i++){
            Logger.createNew(InstrumentationRegistry.getContext());

            Port port = setupPortAndTestConnection();

            for(int j = 0; j < 10; j++){
                assertNotEquals("UNKNOWN", port.getModemType());
                assertNotEquals("UNKNOWN", port.getModemVersion());
            }

            port.closePort();
            assertTrue(Rild.startRild());
        }
    }

    @Test
    public void openClosePortStressTest() {
        for(int i = 0; i < 20; i++){
            Logger.createNew(InstrumentationRegistry.getContext());

            Port port = setupPortAndTestConnection();

            assertNotEquals("UNKNOWN", port.getModemType());
            assertNotEquals("UNKNOWN", port.getModemVersion());

            port.closePort();
            assertTrue(Rild.startRild());
        }
    }

    @Test
    public void testConnectionStressTest() {
        for(int i = 0; i < 5; i++){
            Logger.createNew(InstrumentationRegistry.getContext());

            assertTrue(Rild.stopRild());

            Port port = new Port("/dev/ttyACM0");

            assertTrue(port.exists());
            assertTrue(port.setupPort());
            for(int j = 0; j < 30; j++){
                assertTrue(port.testConnection());
            }

            port.closePort();
            assertTrue(Rild.startRild());
        }
    }
}