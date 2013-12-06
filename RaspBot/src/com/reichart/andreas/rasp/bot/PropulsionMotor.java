package com.reichart.andreas.rasp.bot;

/**
 * Enumeration for holding all motor specific data (currently only address is supported);
 * @author reichart
 *
 */
public enum PropulsionMotor {
    LEFT(0x00, "LEFT_MOTOR"), RIGHT(0x01, "RIGHT_MOTOR");

    private final String name;
    private final int address;

    PropulsionMotor(int address, String name) {
	this.name = name;
	this.address = address;
    }
    
    @Override
    public String toString() {
	return name;
    }
    
    /**
     * Get the target address for the motor.
     * @return
     */
    public int getAddress() {
	return address;
    }
}
