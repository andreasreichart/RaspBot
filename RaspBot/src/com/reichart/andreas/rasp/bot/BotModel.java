package com.reichart.andreas.rasp.bot;

import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Model of the Bot.
 * @author reichart
 */
public class BotModel {

    Map<PropulsionMotor, Integer>motors;
    PropertyChangeSupport pcSupport;
    
    public BotModel(Map<PropulsionMotor, Integer> motors, MotorDriver driver) {
	this.motors = Collections.synchronizedMap(motors);
	pcSupport = new PropertyChangeSupport(this);
	pcSupport.addPropertyChangeListener(driver);
    }
    
    public void setSpeed(int speed) {
	for (Entry<PropulsionMotor, Integer> entry : motors.entrySet()) {
	    
	}
    }
    
    public void stopImmediately() {
	for (Map.Entry<PropulsionMotor, Integer> entry : motors.entrySet()) {
	    setMotorValue(entry.getKey(), 0x00);
	}
    }
    
    public void stop() {
	for (Map.Entry<PropulsionMotor, Integer> entry : motors.entrySet()) {
	    int currentSpeed = (int) entry.getValue();
	    setMotorValue(entry.getKey(), currentSpeed--);
	}
    }
    
    private void setMotorValue (PropulsionMotor motor, int speed) {
	motors.put(motor, (Integer)speed);
	pcSupport.firePropertyChange(motor.toString(), motor, speed);
    }
    
    private void speedRamp(int targetSpeed) {
	
    }

}
