package com.reichart.andreas.rasp.bot;

import java.io.IOException;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;

public class Bot {

    private I2CBus bus;
    private I2CDevice device;
    private final static int AVR_DEVICE = 0x37;
    private final static Logger log = LogManager.getLogger(Bot.class);
    private final JFrameGyroDiagram visualizationFrame; 

    public static void main(String[] args) {
	try {
	    Bot bot = new Bot();
	} catch (IOException | InterruptedException e) {
	    e.printStackTrace();
	}
    }

    public Bot() throws IOException, InterruptedException {

	GyroModel model = new GyroModel();

	Map<GyroAxes, Integer> selfTestGyro = model.getSelfTestGyroResults();
	visualizationFrame = new JFrameGyroDiagram();
	visualizationFrame.setVisible(true);

	while (true) {

//	    logGyroValues(model);
//	    logGyroAngles(model);
//	    logCompensatedAngles(model);
//	    Map<GyroAxes, Double> angleMap = model.getCompensatedAngles();
//	    logCompensatedAngles(angleMap);
	    Map<GyroAxes, Float> angleMap = model.getAngles();
	    visualizationFrame.addValues(angleMap);
	    synchronized (this) {
		this.wait(5);
	    }
	}
    }

    private void fillDevice() throws IOException {
	for (int i = 0; i <= 0xFF; ++i) {
	    device.write(i, (byte) i);
	}
    }

    private void clearAVRBuffer() throws IOException {
	for (int i = 0; i <= 0xFF; i++) {
	    device.write(i, (byte) 0x00);
	}
    }

    private void logGyroValues(GyroModel model) {
	StringBuilder builder = new StringBuilder();
	builder.append("X: ");
	builder.append(model.getGyroX());
	builder.append(" Y: ");
	builder.append(model.getGyroY());
	builder.append(" Z: ");
	builder.append(model.getGyroZ());
	log.debug("Gyro-values: " + builder.toString());
    }
    
    private void logGyroAngles(GyroModel model) {
	StringBuilder builder = new StringBuilder();
	Map<GyroAxes, Float> angles = model.getAngles();
	builder.append ("Current angles: ");
	for (Map.Entry<GyroAxes, Float> entry : angles.entrySet()) {
	    builder.append ("  ");
	    builder.append (entry.getKey() + ":");
	    builder.append((double)entry.getValue() /256f );
	}
	log.debug(builder.toString());
    }
    
    private void logCompensatedAngles(Map<GyroAxes, Double> map) {
	StringBuilder builder = new StringBuilder();
	builder.append("Compensated angles: ");
	for (Map.Entry<GyroAxes, Double> entry : map.entrySet()) {
	    builder.append("  ");
	    builder.append(entry.getKey() + ":");
	    builder.append(entry.getValue() / 256f);
	}
	log.debug(builder.toString());
    }
}
