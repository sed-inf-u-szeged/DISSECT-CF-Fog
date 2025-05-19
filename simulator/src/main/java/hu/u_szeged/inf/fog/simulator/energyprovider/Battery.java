package hu.u_szeged.inf.fog.simulator.energyprovider;

public class Battery {

    float capacity;
    float batteryLevel;

    /**
     *
     * @param capacity          Maximum capacity of a Battery
     * @param batteryLevel      Currently stored energy
     */
    public Battery(float capacity, float batteryLevel) {
        this.capacity = capacity;
        this.batteryLevel = batteryLevel;
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

    public float getBatteryLevel() {
        return this.batteryLevel;
    }

    /**
     * @return                  The fullness of a Battery in percentage
     */

    public float getBatteryPercentage() {
        return this.batteryLevel / this.capacity * 100;
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

}
