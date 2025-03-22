package hu.u_szeged.inf.fog.simulator.energyprovider;

public class Battery {

    float capacity;
    float outputCapacity;
    float batteryLevel;
    float condition;

    /**
     *
     * @param capacity          Maximum capacity of a Battery
     * @param outputCapacity    Maximum output of a Battery
     * @param batteryLevel      Currently stored energy
     * @param condition         Condition of a Battery
     */
    public Battery(float capacity, float outputCapacity, float batteryLevel, float condition) {
        this.capacity = capacity;
        this.outputCapacity = outputCapacity;
        this.batteryLevel = batteryLevel;
        this.condition = condition;
    }

    /**
     *
     * @param batteryLevel      Ammount to add to Battery
     * @return                  The Battery level after the addition
     */

    public float addBatteryLevel(float batteryLevel) {
        if (this.batteryLevel + batteryLevel > this.capacity) {
            this.batteryLevel = this.capacity;
        }else {
            this.batteryLevel += batteryLevel;
        }
        return this.batteryLevel;
    }

    /**
     * @return                  The current ammount stored in the Battery
     */

    float getBatteryLevel() {
        return this.batteryLevel;
    }

    /**
     * @return                  The fullness of a Battery in percentage
     */

    public float getBatteryPercentage() {
        return this.batteryLevel / this.capacity * 100;
    }

    /**
     * @return                  The max energy output of the Battery
     */

    public float getCurrentMaxOutput () {

        if (this.outputCapacity < this.batteryLevel) {
            return this.outputCapacity;
        }else {
            return  this.batteryLevel;
        }
    }

    /**
     *
     * @param Ammount           The Ammount to remove from Battery
     * @return                  The energy stored in Battery after removal
     */

    public float removeBatteryLevel(float Ammount) {
        if (this.batteryLevel - Ammount < 0) {
            this.batteryLevel = 0;
        }else {
            this.batteryLevel -= Ammount;
        }
        return this.batteryLevel;
    }

    //TODO battery degradation
}
