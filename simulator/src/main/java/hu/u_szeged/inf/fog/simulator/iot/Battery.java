package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

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
    //logoláshoz minden batterynek külön csv és ehhez kell vmi identifier, sima id meg kevés imo
    private String name;

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
     * Constant value used to drain the battery when the battery device is idle. (currently in mAh/h, can be Wh/h or sth)
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

    //hashmap vszeg gyorsabb, de igy olvashatóbb a csv
    private final Map<Long, Float> readings = new TreeMap<>();


    /**
     * Constructor for battery objects
     *
     * @param maxCapacity the maximum capacity of the battery (mAh)
     * @param voltage     the voltage of the battery (V)
     * @param drainRate   the value the battery is drained by when the device is idle (mAh/h)
     * @param chargeTime  the time it takes to charge the battery to full (in ticks).
     */
    public Battery(String name, float maxCapacity, float voltage, float drainRate, long chargeTime) {
        this.name = name;
        this.maxCapacity = maxCapacity;
        this.voltage = voltage;
        this.drainRate = drainRate;
        this.chargeTime = chargeTime;

        this.currLevel = maxCapacity;
        this.isCharging = false;

        readings.put(Timed.getFireCount(), currLevel);
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
            new Charge(chargeTime);
            return;
        }

        // mAh/h a drainRate, de az event percenkénti
        currLevel -= drainRate / 60;

        readings.put(fires, currLevel);
    }

    public class Charge extends DeferredEvent{

        Charge(long chargeTime){
            super(chargeTime);
            isCharging = true;
        }

        @Override
        protected void eventAction() {
            currLevel = maxCapacity;
            isCharging = false;
            subscribe(60000);
        }
    }

    public float getPercentage(){
        return currLevel / maxCapacity * 100;
    }

    public void writeToFileConsumption(String resultDirectory){
        try {
            FileWriter fw = new FileWriter(resultDirectory + File.separator + name +".csv");

            fw.write("Timestamp (min)," + name + "\n");
            for (var entry : readings.entrySet()) {
                fw.write(entry.getKey()/1000 + "," + entry.getValue() + "\n");
            }

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
