package com.reichart.andreas.rasp.bot;

import java.util.HashMap;

public enum GyroRegister {

    PWR_MGMT_1(0x6B), 
    PWR_MGMT_2(0x6C);

    final int register;
    
    GyroRegister(int register) {
	this.register = register;
    }
    
    public int getValue() {
	return register;
    }

}
