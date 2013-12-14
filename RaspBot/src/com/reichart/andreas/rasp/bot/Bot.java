package com.reichart.andreas.rasp.bot;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;

public class Bot {

    private I2CBus bus;
    private I2CDevice device;
    private final static int AVR_DEVICE = 0x37;
    private final static Logger log = LogManager.getLogger(Bot.class);
    
    
    public static void main (String [] args) {
	try {
	    Bot bot = new Bot();
	} catch (IOException | InterruptedException e) {
	    e.printStackTrace();
	}
    }
    
    public Bot() throws IOException, InterruptedException {
	

	GyroModel model = new GyroModel();

	while (true) {

	    logGyroValues(model);
	    synchronized (this) {
		this.wait(300);
	    }
	}
	
	
	
//	bus = I2CFactory.getInstance(I2CBus.BUS_1);
//	device = bus.getDevice(AVR_DEVICE);
//
//	
//	
//	while (true) {
//
//	    for (int i = 0; i < 0xFE; i++) {
//		device.write(0x00, (byte) i);
//		synchronized(this) {
//		    this.wait(1);
//		}
//	    }
//	    for (int i = 254; i >= 0; i--) {
//		device.write(0x00, (byte) i);
//		synchronized(this) {
//		    this.wait(1);
//		}
//	    }
//	    synchronized (this) {
//		this.wait(500);
//	    }
//	}
	
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
    
    private void logGyroValues(GyroModel model) {
	StringBuilder builder = new StringBuilder();
	builder.append("X: ");
	builder.append(model.getGyroX());
	builder.append(" Y: ");
	builder.append(model.getGyroY());
	builder.append(" Z: ");
	builder.append(model.getGyroZ());
	log.debug("Gyro-values: "+ builder.toString());
    }
    
}
