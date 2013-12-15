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
    private final static int POLL_INTERVAL = 2;
    /** Bus-Address for the gyroscope */
    private final static int BUS_ADRESS = 0x68;
    /** Start address of the gyro registers on the device - refering to datasheet */
    private final static int GYRO_START_ADDRESS = 0x43;
    /** Start address of the acceleration sensor */
    private final static int ACCEL_START_ADDRESS = 0x3B;
    /** Start addres of the temperature sensor */
    private final static int TEMP_START_ADDRESS = 0x41;
    
    private final static float complementary = 0.98f; 
	    

    /* The logger for this class */
    private final Logger log = LogManager.getLogger(GyroModel.class); 
    
    Lock lock = new ReentrantLock();

    private final I2CDevice device;
    private static byte[] gyroBuffer = new byte[6];
    private static byte [] accelBuffer = new byte [6];
    private static byte [] tempBuffer = new byte [2];
    private static Map<GyroAxes, Long> timestamps = new HashMap<GyroAxes, Long>(3);
    private static Map<GyroAxes, Integer> angles = new HashMap<GyroAxes, Integer>(3);
    private static Map<GyroAxes, Integer> gyroMap = new HashMap<GyroAxes, Integer>(3);
    private Map<GyroAxes, Integer> offsetMap = new HashMap<GyroAxes, Integer>(3);

    public GyroModel() throws IOException {
	device = I2CInterface.getInstance().getDevice(BUS_ADRESS);
	
	log.info("Writing <"+GyroRegValue.PWR_MGMT_1_CYCLE.getValue()+"> to register <"+GyroRegister.PWR_MGMT_1.getValue()+">");
	device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) 0x00);
	device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) GyroRegValue.PWR_MGMT_1_CYCLE.getValue());
	initInertialSystem();
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
     */
    private void initInertialSystem() throws IOException {
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
     */
    private Map<GyroAxes, Integer> getOffsetMap() throws IOException {
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
	angles.put(axis, 0);
    }

    /**
     * Init the gyroMap with default values.
     */
    private void initGyroMap(GyroAxes axis) {
        gyroMap.put(axis, 0x00);
    }

    private void pollData() throws IOException {
	pollGyro();
	pollAccel();
	pollTemp();
    }

    /**
     * Poll all gyro-registers and put the received values into a byte buffer
     * 
     * @throws IOException
     */
    private void pollGyro() throws IOException {
	synchronized (gyroBuffer) {
	    device.write (GyroRegister.PWR_MGMT_1.register,  (byte)0x00);
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
	device.write(GyroRegister.PWR_MGMT_1.register, (byte) 0x00);
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
     * Get a fresh map with the current angles of the gyroboard.
     * <p>
     * NOTE: currently only values are exported that do not have a dimension!!!
     * 
     * @return Map with the angles of the gyroboard.
     */
    public Map<GyroAxes, Integer> getAngles() {
        updateAngles();
        return angles;
    }

    /**
     * Get a consistent Map of the acceleration values from the last poll of the acceleration
     * module.
     * 
     * @return Map of consistent data from the last poll.
     */
    public Map<GyroAxes, Integer> getAccelValueList() {
        Map<GyroAxes, Integer> aMap = new HashMap<GyroAxes, Integer>(3);
        synchronized (accelBuffer) {
            aMap.put(GyroAxes.GYRO_X, getAX());
            aMap.put(GyroAxes.GYRO_Y, getAY());
            aMap.put(GyroAxes.GYRO_Z, getAZ());
        }
        return aMap;
    }

    /**
     * Get a consistent Map of the gyro values from the last poll of the gyroscope.
     * 
     * @return Map of consistent data from the last poll.
     */
    public Map<GyroAxes, Integer> getGyroValueList() {
        synchronized (gyroBuffer) {
            gyroMap.put(GyroAxes.GYRO_X, getGX());
            gyroMap.put(GyroAxes.GYRO_Y, getGY());
            gyroMap.put(GyroAxes.GYRO_Z, getGZ());
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
     * Update the angles.
     */
    private void updateAngles() {
	getGyroValueList(); /* Dont't need the variable here ... we use the field */
	if (timestamps.size() == 0) {
	    for (GyroAxes axis : GyroAxes.values()) {
		initTimeStamps(axis);
	    }
	}
	long tempTime;
	int tempInt;
	synchronized (gyroBuffer) {
	    for (GyroAxes axis : GyroAxes.values()) {
		tempInt = (int) angles.get(axis);
		tempInt += (gyroMap.get(axis) - offsetMap.get(axis))
			* ((tempTime = System.nanoTime()) - timestamps.get(axis))/1000000000L;
		angles.put(axis, (Integer) tempInt);
		timestamps.put(axis, (Long) tempTime);
	    }
        }
    }
    
    public Map <GyroAxes, Double> getCompensatedAngles() {
	double accelXAngle = 57.295 * Math.atan((float) getAX()
		/ Math.sqrt(Math.pow((float) getAZ(), 2) + Math.pow((float) getAX(), 2)));
	double accelYAngle = 57.295 * Math.atan((float) getAX()
		/ Math.sqrt(Math.pow((float) getAY(), 2) + Math.pow((float) getAY(), 2)));
	
	if (accelXAngle > 180) {
	    accelXAngle -= (float) 360.0;
	}
	if (accelYAngle > 180)
	    accelYAngle -= (float) 360.0;
	
	//TODO: add the ZAxis after verification that this works.
	double combinedXAngle = complementary * angles.get(GyroAxes.GYRO_X) + (1 - complementary) * accelXAngle;
	double combinedYAngle = complementary * angles.get(GyroAxes.GYRO_Y) + (1 - complementary) * accelYAngle;
	Map<GyroAxes, Double> map = new HashMap<>(3);
	map.put(GyroAxes.GYRO_X, combinedXAngle);
	map.put(GyroAxes.GYRO_Y, combinedYAngle);
	return map;
    }

    private int getGX() {
	return (int) (gyroBuffer[0] << 8 | gyroBuffer[1]);
    }

    private int getGY() {
	return (int) (gyroBuffer[2] << 8 | gyroBuffer[3]);
    }

    private int getGZ() {
	return (int) (gyroBuffer[4] << 8 | gyroBuffer[5]);
    }

    private int getAX() {
	return (int) (accelBuffer[0] << 8 | accelBuffer[1]);
    }

    private int getAY() {
	return (int) (accelBuffer[2] << 8 | accelBuffer[3]);
    }

    private int getAZ() {
	return (int) (accelBuffer[4] << 8) | accelBuffer[5];
    }

    private int getTemp() {
	return ((int) tempBuffer[0] << 8) + (int) tempBuffer[1];
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
