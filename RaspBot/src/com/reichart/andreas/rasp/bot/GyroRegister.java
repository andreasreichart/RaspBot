package com.reichart.andreas.rasp.bot;

public enum GyroRegister {

    PWR_MGMT_1(0x6B), PWR_MGMT_2(0x6C), GYRO_CONFIG(0x1B), GYRO_SELFTEST_X(0x0D), GYRO_SELF_TEST_Y(0x0E), GYRO_SELF_TEST_Z(
	    0x0F), GYRO_SELF_TEST_ACCEL (0x10);

    final int register;

    GyroRegister(int register) {
	this.register = register;
    }

    public int getValue() {
	return register;
    }

}
