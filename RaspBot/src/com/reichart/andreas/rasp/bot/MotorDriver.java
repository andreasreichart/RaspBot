package com.reichart.andreas.rasp.bot;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;

public class MotorDriver implements PropertyChangeListener {

    private final I2CDevice device;

    public MotorDriver(I2CBus bus, int address) throws IOException {
	device = bus.getDevice(address);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
	int address = ((PropulsionMotor) evt.getOldValue()).getAddress();
	byte speed = (byte) (int) evt.getNewValue();
	try {
	    device.write(address, speed);
	} catch (IOException e) {
	    // TODO implement sl4j
	    e.printStackTrace();
	}
    }
}
