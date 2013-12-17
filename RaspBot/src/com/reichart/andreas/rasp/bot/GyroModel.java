package com.reichart.andreas.rasp.bot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    private final static int POLL_INTERVAL = 10;
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
    private final static float GYRO_SENSITIVITY = 131F;
    private final static float ACCEL_SENSITIVITY = 16384F;
    
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
    private static long timestamp;

    public GyroModel() throws IOException, InterruptedException {
	device = I2CInterface.getInstance().getDevice(BUS_ADRESS);

	log.info("Writing <" + GyroRegValue.PWR_MGMT_1_CYCLE.getValue() + "> to register <"
		+ GyroRegister.PWR_MGMT_1.getValue() + ">");
	device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) 0x00);
	device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) (1 << 7));
	synchronized (this) {
	    this.wait(10);
	}

	// device.write(GyroRegister.PWR_MGMT_1.register, (byte)0x00);
	// device.write(GyroRegister.PWR_MGMT_1.getValue(), (byte) ((byte)
	// GyroRegValue.PWR_MGMT_1_CYCLE.getValue()|(byte)GyroRegValue.PWR_MGMT_1_CLKSEL_1.getValue()));
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
//	timer.scheduleAtFixedRate(timerTask, 0, POLL_INTERVAL);
    }
    
    /**
     * Init the axes for the intertial system.
     * @throws IOException 
     * @throws InterruptedException 
     */
    private void initInertialSystem() throws IOException, InterruptedException {
	resetAllRegisters();
	/* Set FS_SEL to +/-250°s and 131LSB/°/s
	 * 0x1B, Bit 4 Bit 3
	 */
	
	/* Set DLPF to 1: delay 2ms */
	device.write(0x1A, (byte) (1<<1));
	
	/* Divide the sample rate: 1khz/(1+x) */
	device.write(0x19, (byte) 0x09);
	
	offsetMap = getOffsetMap();
	for (GyroAxes axis : GyroAxes.values()) {
	    initAngles(axis);
	    initGyroMap(axis);
	}

	log.debug("Acceleration Test: " + getStringForLog(getSelfTestAccelerationResults()));
	log.debug("Gyro Selftest:     " + getStringForLog(getSelfTestGyroResults()));
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
    public Map<GyroAxes, Integer> getOffsetMap() throws IOException, InterruptedException {
	final long startTime = System.currentTimeMillis();
	final int iterations = 400;
	int xAxis = 0;
	int yAxis = 0;
	int zAxis = 0;
	for (int i = 0; i < iterations; ++i) {
	    pollGyro();
	    xAxis += getGX();
	    yAxis += getGY();
	    zAxis += getGZ();
	    synchronized(this) {
		this.wait(8);
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
	log.info("Temperature: "+String.valueOf(getTemperature())+"°C");
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
	    device.read(GYRO_START_ADDRESS, gyroBuffer, 0, 6);
	}
    }
    
    private int getGyroInt (int address) throws IOException {
	return device.read(address);
    }

    /**
     * Poll all acceleration register and put received values into a byte buffer.
     * 
     * @throws IOException
     */
    private void pollAccel() throws IOException {
	synchronized (accelBuffer) {
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
	    device.read(TEMP_START_ADDRESS, tempBuffer, 0, 2);
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
     * @return float representation of the temperature poll
     */
    public float getTemperature() {
        synchronized (tempBuffer) {
            return ( getTemp() + 12412.0f) / 340.0f;
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

	int resultX = getGyroInt(GyroRegister.GYRO_SELF_TEST_X.register);
	int resultY = getGyroInt(GyroRegister.GYRO_SELF_TEST_Y.register);
	int resultZ = getGyroInt(GyroRegister.GYRO_SELF_TEST_Z.register);

	resultX &= 0x20;
	resultY &= 0x20;
	resultZ &= 0x20;

	selfTestMap.put(GyroAxes.GYRO_X, (Integer) resultX);
	selfTestMap.put(GyroAxes.GYRO_Y, (Integer) resultY);
	selfTestMap.put(GyroAxes.GYRO_Z, (Integer) resultZ);
	log.debug("Selftest-Results: " + getStringForLog(selfTestMap));

	return selfTestMap; 
    }
    
    /**
     * Get a map with results from the acceleration test.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private Map<GyroAxes, Integer> getSelfTestAccelerationResults() throws IOException, InterruptedException {
	device.write(GyroRegister.GYRO_CONFIG.register, (byte) 0xE0); // 0b11100000

	synchronized (this) {
	    this.wait(10);
	}

	int resultX = (getGyroInt(GyroRegister.GYRO_SELF_TEST_X.register) & 0xE0)
		| (getGyroInt(GyroRegister.GYRO_SELF_TEST_ACCEL.register) & 0x30);
	int resultY = (getGyroInt(GyroRegister.GYRO_SELF_TEST_Y.register) & 0xE0)
		| (getGyroInt(GyroRegister.GYRO_SELF_TEST_ACCEL.register) & 0x0C);
	int resultZ = (getGyroInt(GyroRegister.GYRO_SELF_TEST_X.register) & 0xE0)
		| (getGyroInt(GyroRegister.GYRO_SELF_TEST_ACCEL.register) & 0x03);

	Map<GyroAxes, Integer> selfTestAccMap = new HashMap<GyroAxes, Integer>(3);
	selfTestAccMap.put(GyroAxes.GYRO_X, (Integer) resultX);
	selfTestAccMap.put(GyroAxes.GYRO_Y, (Integer) resultY);
	selfTestAccMap.put(GyroAxes.GYRO_Z, (Integer) resultZ);

	return selfTestAccMap;
    }
    
    /**
     * Poll gyro and convert it directly into degrees
     */
    private void updateGyroAngles() {
	getGyroValueList();
	long elapsedTime = System.currentTimeMillis() - timestamp;
	if (elapsedTime > 30)
	    elapsedTime = 10;
	log.debug("Elapsed time since last computation: " + String.valueOf(elapsedTime) + " and we estimated "
		+ POLL_FACTOR);
	timestamp = System.currentTimeMillis();
	for (GyroAxes axis : GyroAxes.values()) {
	    float gyroRate = ((float) gyroMap.get(axis)) / GYRO_SENSITIVITY;
	    float gyroAngle = (float) angles.get(axis);
	    gyroAngle += (gyroRate * ((float) elapsedTime)) / 1000F;
	    angles.put(axis, gyroAngle);
	}
    }

    /**
     * Use the complementary filter to get rid of the gyro drift. All filtered data are put into the
     * filteredAngles map.
     */
    private void filterAngles() {
	double accelXAngle;
	double accelYAngle;
	synchronized (accelBuffer) {
	    accelXAngle = 57.295 * Math.atan((float) getAX()
		    / Math.sqrt(Math.pow((float) getAY(), 2) + Math.pow((float) getAZ(), 2)));
	    accelYAngle = 57.295 * Math.atan((float) getAY()
		    / Math.sqrt(Math.pow((float) getAX(), 2) + Math.pow((float) getAZ(), 2)));

	    // float accelXAngle2 = (float) Math.toDegrees(Math.atan2((float) getAY(), (float)
	    // getAZ() + Math.PI));
	    // float accelYAngle2 = (float) Math.toDegrees(Math.atan2((float) getAX(), (float)
	    // getAZ() + Math.PI));

	    log.debug("Acceleration values: " + getAX()  + " : " + getAY() 
		    + " : " + getAZ());
	    log.debug("Acceleration angles: " + accelXAngle + " : " + accelYAngle);
	}
	
	if (accelXAngle > 180) accelXAngle -= 360.0f;

	if (accelYAngle > 180) accelYAngle -= 360.0f;

	float combinedXAngle = (float) (complementary * angles.get(GyroAxes.GYRO_X) + (1 - complementary) * (float) accelXAngle);
	float combinedYAngle = (float) (complementary * angles.get(GyroAxes.GYRO_Y) + (1 - complementary) * (float) accelYAngle);
	filteredAngles.put(GyroAxes.GYRO_X, combinedXAngle);
	filteredAngles.put(GyroAxes.GYRO_Y, combinedYAngle);
    }

    private int getGX() {
	return  getShortValue(gyroBuffer, 0);
    }

    private int getGY() {
	return  getShortValue(gyroBuffer, 2);
    }

    private int getGZ() {
	return  getShortValue(gyroBuffer, 4);
    }

    private int getAX() {
	return  getShortValue(accelBuffer, 0);
    }

    private int getAY() {
	return  getShortValue(accelBuffer, 2);
    }

    private int getAZ() {
	return  getShortValue(accelBuffer, 4);
    }

    private int getTemp() {
	return  getShortValue(tempBuffer, 0);
    }
    
    private short getShortValue (byte [] bytebuffer , int pointerHi) {
	return ByteBuffer.wrap(bytebuffer, pointerHi, 2).order(ByteOrder.BIG_ENDIAN).getShort();
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
