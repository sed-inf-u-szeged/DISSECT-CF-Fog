package hu.u_szeged.inf.fog.simulator.energyprovider;

public class Battery {

    float capacity;
    float outputCapacity;
    float batteryLevel;
    float condition;

    public Battery(float capacity, float outputCapacity, float batteryLevel, float condition) {
        this.capacity = capacity;
        this.outputCapacity = outputCapacity;
        this.batteryLevel = batteryLevel;
        this.condition = condition;
    }

    private float addBatteryLevel(float batteryLevel) {
        if (this.batteryLevel + batteryLevel > this.capacity) {
            this.batteryLevel = this.capacity;
        }else {
            this.batteryLevel += batteryLevel;
        }
        return this.batteryLevel;
    }

    float getBatteryLevel() {
        return this.batteryLevel;
    }

    public float chargeUp(float charge) {
        return addBatteryLevel(charge);
    }

    public float discharge(float discharge) {
        return removeBatteryLevel(discharge);
    }

    public float getCurrentMaxOutput () {

        if (this.outputCapacity < this.batteryLevel) {
            return this.outputCapacity;
        }else {
            return  this.batteryLevel;
        }
    }

    private float removeBatteryLevel(float batteryLevel) {
        if (this.batteryLevel - batteryLevel < 0) {
            this.batteryLevel = 0;
        }else {
            this.batteryLevel -= batteryLevel;
        }
        return this.batteryLevel;
    }

    //TODO battery degradation
}
