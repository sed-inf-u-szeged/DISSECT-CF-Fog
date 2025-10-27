package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class Battery extends Timed {

    //TODO enum könnyebb battery inicializáláshoz
    /*
    - előre definiált batteryk lesznek majd itt statikus adattagokkal, amikhez lesz egy konstruktor ahol az enum alapján hozza létre a batteryt
    - Lithium-ion , Lithium Polymer (pl.: telefonok, drónok, esetleg szenzorok - a legtöbb modern dolog kisebb akkui)
    - Lead-acid autóhoz? ha akarunk autót modellezni és releváns oda idk?

    - esetlegesen BatteryType helyett BatteryForDevice / DeviceBattery lehetne ahol pl telefonokhoz és drónokhoz általános batteryket
      rakunk, mert azoknál lehet azonos típus, de különböző kapacitás, mert különböző modellek is vannak egyértelműen

    @AllArgsConstructor
    public enum BatteryType{

    }
    */

    /**
     * Maximum capacity of the battery (mAh)
     */
    @Getter
    private float maxCapacity;

    /**
     * Current level of the battery (mAh). The battery starts fully charged at maximum capacity,
     * the value gets constantly lowered until 0, when the battery has to be charged.
     */
    @Getter
    private float currLevel;

    /**
     * The battery's voltage (V)
     */
    @Getter
    private float voltage;

    /**
     * Constant value used to drain the battery when the battery device is idle (mAh/h), for an average phone it's around 5 mAh/h.
     */
    @Getter
    private float drainRate; //lehet discharge rate

    /**
     * The time it takes to charge the battery from 0% to 100% (in ticks).
     */
    @Getter
    private long chargeTime;

    /**
     * Used to stop battery drainage or task execution while charging.
     */
    @Getter
    private boolean isCharging;


    /**
     * Constructor for battery objects
     *
     * @param maxCapacity the maximum capacity of the battery (mAh)
     * @param voltage     the voltage of the battery (V)
     * @param drainRate   the value the battery is drained by when the device is idle (mAh/h)
     * @param chargeTime  the time it takes to charge the battery to full (in ticks).
     */
    public Battery(float maxCapacity, float voltage, float drainRate, long chargeTime) {
        this.maxCapacity = maxCapacity;
        this.voltage = voltage;
        this.drainRate = drainRate;
        this.chargeTime = chargeTime;

        this.currLevel = maxCapacity;
        this.isCharging = false;

        subscribe(60000);
    }

    /**
     * The tick method is called to simulate a time step for the battery.
     * It handles idle battery drainage.
     */
    @Override
    public void tick(long fires) {
        if(currLevel - drainRate / 60 < 0){
            unsubscribe();
            charge(Timed.getFireCount());
            return;
        }

        // mAh/h a drainRate, de az event percenkénti
        currLevel -= drainRate / 60;
    }

    /**
     * This function starts simulating the charging of the battery,
     * then resubscribes the idle drain event.
     *
     * @param chargeStartTime the time at which the battery starts charging.
     */
    public void charge(long chargeStartTime){
        isCharging = true;
        currLevel = maxCapacity;
        Timed.jumpTime(chargeTime);
        isCharging = false;
        subscribe(60000);
    }
}
