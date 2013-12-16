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

    /** The poll interval in ms for the gyroscope */
    private final static int POLL_INTERVAL = 5;
    private final static float POLL_FACTOR = POLL_INTERVAL/1000;
    /** Bus-Address for the gyroscope */
    private final static int BUS_ADRESS = 0x68;
    /** Start address of the gyro registers on the device - refering to datasheet */
    private final static int GYRO_START_ADDRESS = 0x43;
    /** Start address of the acceleration sensor */
    private final static int ACCEL_START_ADDRESS = 0x3B;
    /** Start addres of the temperature sensor */
    private final static int TEMP_START_ADDRESS = 0x41;
    /** The sensitivity of the gyro sensor: <br>can be  <code> 131, 65.5, 32.8 or 16.4 °/LSB</code> */
    private final static int GYRO_SENSITIVITY = 131;
    
    private final static float complementary = 0.98f; 
	    

    /* The logger for this class */
    private final Logger log = LogManager.getLogger(GyroModel.class); 
    
    Lock lock = new ReentrantLock();

    private final I2CDevice device;
    private static byte[] gyroBuffer = new byte[6];
    private static byte [] accelBuffer = new byte [6];
    private static byte [] tempBuffer = new byte [2];
    private static Map<GyroAxes, Long> timestamps = new HashMap<GyroAxes, Long>(3);
    private static Map<GyroAxes, Float> angles = new HashMap<GyroAxes, Float>(3);
    private static Map<GyroAxes, Float> filteredAngles = new HashMap<GyroAxes, Float>(3);
    private static Map<GyroAxes, Integer> gyroMap = new HashMap<GyroAxes, Integer>(3);
    private Map<GyroAxes, Integer> offsetMap = new HashMap<GyroAxes, Integer>(3);

    public GyroModel() throws IOException {
	device = I2CInterface.getInstance().getDevice(BUS_ADRESS);
	
	log.info("Writing <"+GyroRegValue.PWR_MGMT_1_CYCLE.getValue()+"> to register <"+GyroRegister.PWR_MGMT_1.getValue()+">");
	device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) 0x00);
	device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) ((byte) GyroRegValue.PWR_MGMT_1_CYCLE.getValue()|(byte)GyroRegValue.PWR_MGMT_1_CLKSEL_1.getValue()));
	try {
	    initInertialSystem();
	} catch (InterruptedException e) {
	    	log.error("Cannot init inertialsystem", e);
	}
	initTimerTask();

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
    
    /**
     * Init the axes for the intertial system.
     * @throws IOException 
     * @throws InterruptedException 
     */
    private void initInertialSystem() throws IOException, InterruptedException {
	resetAllRegisters();
	offsetMap = getOffsetMap();
	for (GyroAxes axis : GyroAxes.values()) {
	    initAngles(axis);
	    initGyroMap(axis);
	}

    }
    
    /**
     * Write a 0x00 into all registers of the device.
     * 
     * @throws IOException
     */
    private void resetAllRegisters() throws IOException {
	for (GyroReg register : GyroReg.values()) {
	    device.write(register.register, (byte) 0x00);
	}
    }
    
    /**
     * Get some offset values for the gyroscope.
     * 
     * @return Map of offsets.
     * @throws IOException
     * @throws InterruptedException 
     */
    private Map<GyroAxes, Integer> getOffsetMap() throws IOException, InterruptedException {
	final long startTime = System.currentTimeMillis();
	final int iterations = 1000;
	int xAxis = 0;
	int yAxis = 0;
	int zAxis = 0;
	for (int i = 0; i < iterations; ++i) {
	    pollGyro();
	    xAxis += getGX();
	    yAxis += getGY();
	    zAxis += getGZ();
	    synchronized(this) {
		this.wait(1);
	    }
	}
	Map<GyroAxes, Integer> offsetMap = new HashMap<>(3);
	offsetMap.put(GyroAxes.GYRO_X, (Integer) (xAxis / iterations));
	offsetMap.put(GyroAxes.GYRO_Y, (Integer) (yAxis / iterations));
	offsetMap.put(GyroAxes.GYRO_Z, (Integer) (zAxis / iterations));

	final long neededTime = System.currentTimeMillis() - startTime;

	log.debug("Getting offset values took <"+String.valueOf(neededTime)+"ms> for <"+iterations+"> imterations");
	log.debug("Offset values: " + getStringForLog(offsetMap));

	return offsetMap;
    }
    
    /**
     * Init the timestamps Map with default timestamps.
     * 
     * @param axis
     *            The axis to be added
     */
    private void initTimeStamps(GyroAxes axis) {
	timestamps.put(axis, System.nanoTime());
    }

    /**
     * Init the angles with default zero values.
     * 
     * @param axis
     *            The axis to be added
     */
    private void initAngles(GyroAxes axis) {
	angles.put(axis, 0f);
    }

    /**
     * Init the gyroMap with default values.
     */
    private void initGyroMap(GyroAxes axis) {
        gyroMap.put(axis, 0x00);
    }

    /**
     * Poll and compute data.
     * @throws IOException
     */
    private synchronized void pollData() throws IOException {
	pollGyro();
	pollAccel();
	pollTemp();
	updateGyroAngles();
	filterAngles();
    }
    

    /**
     * Poll all gyro-registers and put the received values into a byte buffer
     * 
     * @throws IOException
     */
    private void pollGyro() throws IOException {
	synchronized (gyroBuffer) {
//	    device.write (GyroRegister.PWR_MGMT_1.register,  (byte)0x00);
	    device.read(GYRO_START_ADDRESS, gyroBuffer, 0, 6);
	}
    }
    
    /**
     * Get one byte from the gyroscope.
     * @param address	The address that should be polled.
     * @return
     * @throws IOException
     */
    private byte getGyroByte(int address) throws IOException {
//	device.write(GyroRegister.PWR_MGMT_1.register, (byte) 0x00);
	byte[] buffer = new byte[1];
	device.read(address, buffer, 0, 1);
	return buffer[0];
    }

    /**
     * Poll all acceleration register and put received values into a byte buffer.
     * 
     * @throws IOException
     */
    private void pollAccel() throws IOException {
	synchronized (accelBuffer) {
//	    device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) 0x00);
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
//	    device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) 0x00);
	    device.read(TEMP_START_ADDRESS, tempBuffer, 0, 6);
	}
    }

    /**
     * Get a fresh map with the current angles of the gyroboard.
     * <p>
     * 
     * @return Map with the angles of the gyroboard.
     */
    public synchronized Map<GyroAxes, Float> getAngles() {
        return filteredAngles;
    }

    /**
     * Get a consistent Map of the acceleration values from the last poll of the acceleration
     * module.
     * 
     * @return Map of consistent data from the last poll.
     */
    private Map<GyroAxes, Integer> getAccelValueList() {
        Map<GyroAxes, Integer> aMap = new HashMap<GyroAxes, Integer>(3);
        synchronized (accelBuffer) {
            aMap.put(GyroAxes.GYRO_X, getAX());
            aMap.put(GyroAxes.GYRO_Y, getAY());
            aMap.put(GyroAxes.GYRO_Z, getAZ());
        }
        return aMap;
    }

    /**
     * Get a consistent Map of the gyro values from the last poll of the gyroscope that is already
     * compensated with the offset.
     * 
     * @return Map of consistent data from the last poll.
     */
    private Map<GyroAxes, Integer> getGyroValueList() {
	synchronized (gyroBuffer) {
	    gyroMap.put(GyroAxes.GYRO_X, getGX() - offsetMap.get(GyroAxes.GYRO_X));
	    gyroMap.put(GyroAxes.GYRO_Y, getGY() - offsetMap.get(GyroAxes.GYRO_Y));
	    gyroMap.put(GyroAxes.GYRO_Z, getGZ() - offsetMap.get(GyroAxes.GYRO_Z));
	}
	return gyroMap;
    }

    /**
     * Get the value for the last temperature poll.
     * 
     * @return integer representation of the temperature poll
     */
    public int getTemperature() {
        synchronized (tempBuffer) {
            return getTemp();
        }
    }

    /**
     * Get a consistent set of the acceleration bytes.
     * 
     * @return byte array with consistent data of the acceleration buffer
     */
    public byte[] getAccelBytes() {
        synchronized (accelBuffer) {
            return accelBuffer;
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
     * Get the value of the last gyro-poll for accelerations on the X-axis
     * <p>
     * NOTE: when getting the data from another axis afterwards it cannot be assumed, that both data
     * are consistent.
     * 
     * @return integer value for the X-axis
     */
    public int getAccelX() {
	synchronized (accelBuffer) {
	    return getAX();
	}
    }

    /**
     * Get the value of the last gyro-poll for accelerations on the Y-axis
     * <p>
     * NOTE: when getting the data from another axis afterwards it cannot be assumed, that both data
     * are consistent.
     * 
     * @return integer value for the YS-axis
     */
    public int getAccelY() {
	synchronized (accelBuffer) {
	    return getAY();
	}
    }

    /**
     * Get the value of the last gyro-poll for accelerations on the Z-axis
     * <p>
     * NOTE: when getting the data from another axis afterwards it cannot be assumed, that both data
     * are consistent.
     * 
     * @return integer value for the Z-axis
     */
    public int getAccelZ() {
	synchronized (accelBuffer) {
	    return getAZ();
	}
    }
    
    /**
     * Get a map with the result of the selftest.
     * @return Map containing self test results.
     * @throws IOException
     * @throws InterruptedException 
     */
    public Map<GyroAxes, Integer> getSelfTestGyroResults() throws IOException, InterruptedException {
	Map<GyroAxes, Integer> selfTestMap = new HashMap<GyroAxes, Integer>(3);

	device.write(GyroRegister.GYRO_CONFIG.register, (byte) 0xE0); // 0b11100000

	synchronized (this) {
	    this.wait(10);
	}

	byte resultX = getGyroByte(GyroRegister.GYRO_SELF_TEST_X.register);
	byte resultY = getGyroByte(GyroRegister.GYRO_SELF_TEST_Y.register);
	byte resultZ = getGyroByte(GyroRegister.GYRO_SELF_TEST_Z.register);

	log.debug("Selftest-Bytes: " + (int) (resultX & 0xFF) + " - " + (int) (resultY & 0xFF) + " - "
		+ (int) (resultZ & 0xFF));

	resultX &= 0x20;
	resultY &= 0x20;
	resultZ &= 0x20;

	selfTestMap.put(GyroAxes.GYRO_X, (Integer) (int) resultX);
	selfTestMap.put(GyroAxes.GYRO_Y, (Integer) (int) resultY);
	selfTestMap.put(GyroAxes.GYRO_Z, (Integer) (int) resultZ);
	log.debug("Selftest-Results: " + getStringForLog(selfTestMap));

	return selfTestMap; 
    }
    
    /**
     * Poll gyro and convert it directly into degrees
     */
    private void updateGyroAngles() {
	getGyroValueList();
	for (GyroAxes axis : GyroAxes.values()) {
	    float gyroRate = (float) gyroMap.get(axis)/GYRO_SENSITIVITY;
	    float gyroAngle = angles.get(axis);
	    gyroAngle += gyroRate * POLL_FACTOR;
	    angles.put(axis, gyroAngle);
	}
    }
    
    /**
     * Use the complementary filter to get rid of the gyro drift. All filtered data are put into the
     * filteredAngles map.
     */
    private void filterAngles() {
//	double accelXAngle = 57.295 * Math.atan((float) getAX()
//		/ Math.sqrt(Math.pow((float) getAY(), 2) + Math.pow((float) getAZ(), 2)));
//	double accelYAngle = 57.295 * Math.atan((float) getAY()
//		/ Math.sqrt(Math.pow((float) getAX(), 2) + Math.pow((float) getAZ(), 2)));
	
	float accelXAngle2 = (float) Math.toDegrees(Math.atan2((float) getAY(), (float) getAZ() + Math.PI));
	float accelYAngle2 = (float) Math.toDegrees(Math.atan2((float) getAX(), (float) getAZ() + Math.PI));

//	if (accelXAngle > 180) {
//	    accelXAngle -= 360.0f;
//	}
//	if (accelYAngle > 180)
//	    accelYAngle -= 360.0f;

	float combinedXAngle = (float) (complementary * angles.get(GyroAxes.GYRO_X) + (1 - complementary) * accelXAngle2);
	float combinedYAngle = (float) (complementary * angles.get(GyroAxes.GYRO_Y) + (1 - complementary) * accelYAngle2);
	filteredAngles.put(GyroAxes.GYRO_X, combinedXAngle);
	filteredAngles.put(GyroAxes.GYRO_Y, combinedYAngle);
    }

    private int getGX() {
	return getIntFromHiLoByte(gyroBuffer, 0);
    }

    private int getGY() {
	return getIntFromHiLoByte(gyroBuffer, 2);
    }

    private int getGZ() {
	return getIntFromHiLoByte(gyroBuffer, 4);
    }

    private int getAX() {
	return getIntFromHiLoByte(accelBuffer, 0);
    }

    private int getAY() {
	return getIntFromHiLoByte(accelBuffer, 2);
    }

    private int getAZ() {
	return getIntFromHiLoByte(accelBuffer, 4);
    }

    private int getTemp() {
	return getIntFromHiLoByte(tempBuffer, 0);
    }

    private static int getInt(byte hi, byte lo) {
	return hi << 8 & 0xFF00 | lo & 0xFF;
    }

    private static int getIntFromHiLoByte(byte[] byteBuffer, int hiByte) {
	return getInt(byteBuffer[hiByte], byteBuffer[++hiByte]);
    }

    /**
     * Get a String for a log output.
     * 
     * @param map
     *            The map with the values that should be prepared for log output.
     * @return
     */
    private String getStringForLog(Map<?, ?> map) {
	StringBuilder builder = new StringBuilder();
	for (Object anInt : map.values()) {
	    builder.append("<");
	    builder.append(anInt.toString());
	    builder.append("> ");
	}
	return builder.toString();
    }
}
