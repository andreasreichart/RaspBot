package com.reichart.andreas.rasp.bot;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

public class I2CInterface {

    private static I2CInterface INSTANCE;
    private final I2CBus bus;
    private Set<Integer> deviceSet;

    private I2CInterface() throws IOException {
	/* Private constructor - This is a singleton */
	bus = I2CFactory.getInstance(I2CBus.BUS_1);
	deviceSet = new HashSet<Integer>();
    }

    public static I2CInterface getInstance() throws IOException {
	if (INSTANCE == null) {
	    INSTANCE = new I2CInterface();
	}
	return INSTANCE;
    }

    public I2CDevice getDevice(int busAddress) throws IOException {
	if (deviceSet.contains(busAddress)) {
	    throw new IllegalStateException("Address already in use");
	}
	deviceSet.add(busAddress);
	return bus.getDevice(busAddress);
    }

}
