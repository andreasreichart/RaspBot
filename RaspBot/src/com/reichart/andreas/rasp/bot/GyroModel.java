package com.reichart.andreas.rasp.bot;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pi4j.io.i2c.I2CDevice;

public class GyroModel {

    /** The poll interval for the gyroscope */
    private final static int POLL_INTERVAL = 200;
    /** Bus-Address for the gyroscope */
    private final static int BUS_ADRESS = 0x68;
    /** Start address of the gyro registers on the device - refering to datasheet */
    private final static int GYRO_START_ADDRESS = 0x43;
    /** Start address of the acceleration sensor */
    private final static int ACCEL_START_ADDRESS = 0x3B;
    /** Start addres of the temperature sensor */
    private final static int TEMP_START_ADDRESS = 0x41;
	    

    /* The logger for this class */
    private final Logger log = LogManager.getLogger(GyroModel.class); 
    
    Lock lock = new ReentrantLock();

    private Map<GyroAxes, Integer> gyroMap;
    private I2CDevice device;
    private volatile byte[] gyroBuffer = new byte[6];
    private volatile byte [] accelBuffer = new byte [6];
    private volatile byte [] tempBuffer = new byte [2];

    public GyroModel() throws IOException {
	device = I2CInterface.getInstance().getDevice(BUS_ADRESS);
	
	log.info("Writing <"+GyroRegValue.PWR_MGMT_1_CYCLE.getValue()+"> to register <"+GyroRegister.PWR_MGMT_1.getValue()+">");
	device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) 0x00);
	device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) GyroRegValue.PWR_MGMT_1_CYCLE.getValue());
//	initGyroMap();
	initTimerTask();

    }

    /**
     * Init the gyroMap with default values.
     */
    private void initGyroMap() {
	gyroMap = new HashMap<GyroAxes, Integer>(3);
	gyroMap.put(GyroAxes.GYRO_X, 0x7FFF);
	gyroMap.put(GyroAxes.GYRO_Y, 0x7FFF);
	gyroMap.put(GyroAxes.GYRO_Z, 0x7FFF);
    }

    private void initTimerTask() {
	TimerTask timerTask = new TimerTask() {

	    @Override
	    public void run() {
		try {
		    pollData();
		} catch (IOException e) {
		    log.error("Error while polling data", e);
		}

	    }
	};
	Timer timer = new Timer("Poll-Timer");
	timer.schedule(timerTask, 0, POLL_INTERVAL);
    }

    private void pollData() throws IOException {
	pollGyro();
    }

    /**
     * Poll all gyro-registers and put the received values into a byte buffer
     * 
     * @throws IOException
     */
    private void pollGyro() throws IOException {
	synchronized (gyroBuffer) {
	    device.write (GyroRegister.PWR_MGMT_1.getValue(),  (byte)0x00);
	    device.read(GYRO_START_ADDRESS, gyroBuffer, 0, 6);
	}
    }

    /**
     * Poll all acceleration register and put received values into a byte buffer.
     * 
     * @throws IOException
     */
    private void pollAccel() throws IOException {
	synchronized (accelBuffer) {
	    device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) 0x00);
	    device.read(ACCEL_START_ADDRESS, accelBuffer, 0, 6);
	}
    }

    /**
     * Poll the temperature register and put received values into a byte buffer.
     * 
     * @throws IOException
     */
    private void pollTemp() throws IOException {
	synchronized (tempBuffer) {
	    device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) 0x00);
	    device.read(TEMP_START_ADDRESS, tempBuffer, 0, 6);
	}
    }

    /**
     * Get the value of the last gyro-poll for the X-axis
     * <p>
     * NOTE: when getting the data from another axis afterwards it cannot be assumed, that both data
     * are consistent.
     * 
     * @return integer value for the X-axis
     */
    public int getGyroX() {
	synchronized (gyroBuffer) {
	    return getGX();
	}
    }

    /**
     * Get the value of the last gyro-poll for the Y-axis
     * <p>
     * NOTE: when getting the data from another axis afterwards it cannot be assumed, that both data
     * are consistent.
     * 
     * @return integer value for the Y-axis
     */
    public int getGyroY() {
	synchronized (gyroBuffer) {
	    return getGY();
	}
    }

    /**
     * Get the value of the last gyro-poll for the Z-axis
     * <p>
     * NOTE: when getting the data from another axis afterwards it cannot be assumed, that both data
     * are consistent.
     * 
     * @return integer value for the Z-axis
     */
    public int getGyroZ() {
	synchronized (gyroBuffer) {
	    return getGZ();
	}
    }

    /**
     * Get a consistent set of gyro bytes.
     * 
     * @return byte array with consistent data
     */
    public byte[] getGyroBytes() {
	synchronized (gyroBuffer) {
	    return gyroBuffer;
	}
    }

    /**
     * Get a consistent Map of the gyro values from the last poll of the gyroscope.
     * 
     * @return Map of consistent data from the last poll.
     */
    public Map<GyroAxes, Integer> getGyroValueList() {
	Map<GyroAxes, Integer> gMap = new HashMap<>(3);
	synchronized (gyroBuffer) {
	    gMap.put(GyroAxes.GYRO_X, getGX());
	    gMap.put(GyroAxes.GYRO_Y, getGY());
	    gMap.put(GyroAxes.GYRO_Z, getGZ());
	}
	return gMap;
    }

    private int getGX() {
	return ((int) gyroBuffer[0] << 8) + (int) gyroBuffer[1];
    }

    private int getGY() {
	return ((int) gyroBuffer[2] << 8) + (int) gyroBuffer[3];
    }

    private int getGZ() {
	return ((int) gyroBuffer[4] << 8) + (int) gyroBuffer[5];
    }

    private int getAX() {
	return ((int) accelBuffer[0] << 8) + (int) accelBuffer[1];
    }

    private int getAY() {
	return ((int) accelBuffer[2] << 8) + (int) accelBuffer[3];
    }

    private int getAZ() {
	return ((int) accelBuffer[4] << 8) + (int) accelBuffer[5];
    }

    private int getTemp() {
	return ((int) tempBuffer[0] << 8) + (int) tempBuffer[1];
    }
}
