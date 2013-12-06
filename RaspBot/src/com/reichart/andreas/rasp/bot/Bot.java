package com.reichart.andreas.rasp.bot;

import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

public class Bot {

    private I2CBus bus;
    private I2CDevice device;
    private final static int AVR_DEVICE = 0x37;

    public static void main (String [] args) {
	try {
	    Bot bot = new Bot();
	} catch (IOException | InterruptedException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }
    
    public Bot() throws IOException, InterruptedException {
	bus = I2CFactory.getInstance(I2CBus.BUS_1);
	device = bus.getDevice(AVR_DEVICE);
	// for (int i = 0; i < 100; ++i) {
	// fillDevice();
	// }
	while (true) {

	    for (int i = 0; i < 255; i++) {
		device.write(0x00, (byte) i);
	    }
	    for (int i = 254; i < 1; i--) {
		device.write(0x00, (byte) i);
	    }
	    synchronized (this) {
		this.wait(100);
	    }
	}
	
//	clearAVRBuffer();	
    }
    
    private void fillDevice () throws IOException {
	for (int i=0;i<=0xFF;++i) {
	    device.write(i, (byte) i);
	}
    }
    
  
    private void clearAVRBuffer () throws IOException {
	for (int i=0;i<=0xFF;i++) {
	    device.write(i, (byte)0x00);
	}
    }
    
    private void setSpeedLeft (boolean forward, int speed) {
	
    }

    private void setSpeed (boolean forward, int speed, PropulsionMotor motor) {
	
    }
    
}
