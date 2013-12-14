package com.reichart.andreas.rasp.bot;

public enum GyroRegValue {

    /** if 1: resets all internal registers */
    PWR_MGMT_1_DEVICE_RESET(1 << 7),
    /** if 1: sleepmode */
    PWR_MGMT_1_DEVICE_SLEEP(1 << 6),
    /** If 1: sleep disabled, cycle between sleep and wakeup at rate of LP_WAKE_CTRL */
    PWR_MGMT_1_CYCLE(1 << 5),
    /** If1: Disable temperature sensor */
    PWR_MGMT_1_TEMP_DIS(1 << 3), PWR_MGMT_1_CLKSEL_2(1 << 2), PWR_MGMT_1_CLKSEL_1(1 << 1), PWR_MGMT_1_CLKSEL_0(1 << 0),
    /**
     * LP WAKE CONTROL
     * <p>
     * LP_WAKE_1 and LP_WAKE_2:
     * <ul>
     * <li>0 ___ 1.25 Hz</li>
     * <li>1 ___ 5.00 Hz</li>
     * <li>2 __ 20.00 Hz</li>
     * <li>3 __ 40.00 Hz</li>
     * </ul>
     */
    PWR_MGMT_2_LP_WAKE_1(1 << 7),
    /**
     * LP WAKE CONTROL
     * <p>
     * LP_WAKE_1 and LP_WAKE_2:
     * <ul>
     * <li>0 ___ 1.25 Hz</li>
     * <li>1 ___ 5.00 Hz</li>
     * <li>2 __ 20.00 Hz</li>
     * <li>3 __ 40.00 Hz</li>
     * </ul>
     */
    PWR_MGMT_2_LP_WAKE_2(1 << 6),
    /** If 1: X accelerometer to standby */
    PWR_MGMT_2_STBY_XA(1 << 5),
    /** If 1: Y accelerometer to standby */
    PWR_MGMT_2_STBY_YA(1 << 4),
    /** If 1: Z accelerometer to standby */
    PWR_MGMT_2_STBY_ZA(1 << 3),
    /** If 1: X-axis gyroscope to standby */
    PWR_MGMT_2_STBY_XG(1 << 2),
    /** If 1: Y-axis gyroscope to standby */
    PWR_MGMT_2_STBY_YG(1 << 1),
    /** If 1: Z-axis gyroscope to standby */
    PWR_MGMT_2_STBY_ZG(1 << 0);

    final int bit;

    GyroRegValue(int bit) {
	this.bit = bit;
    }
    
    public int getValue() {
	return bit;
    }

}
