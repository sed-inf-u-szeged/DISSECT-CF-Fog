package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

@NoArgsConstructor
public class Battery extends Timed {

    // jelenleg ott van adattagnak a drainRate is, ez ha számoljuk akkor vszeg nem lesz majd ott (minCpu fogyasztásból? kocsi esetén kínos bár nem tom),
    // de akkor meg kell oldani hogy eszközhöz adás esetén inicializálva legyen (Device setterébe mint a stopTime) addig meg vmi default érték? de akkor
    // meg ne működjön addig amíg nincs eszközhöz kötve a battery? de akkor meg nincs értelme az idlebattery drainnek? a chargeTime is benne van konstruktorba
    // de lesz hozzá setter mert az sztem 2 telefonnál se egyezik meg, vagy ha találok vmi számítási módszert rá akkor le lesz cserélve
    @AllArgsConstructor
    public enum BatteryType{
        //ide még kell research elég sloppy, főleg a drain és charge értékek
        PHONE_BATTERY(4000, 3.7, 10, 2 * 60 * 60 * 1000), //chargeTime ~2h
        DRONE_BATTERY(3500, 12, 10, 90 * 60 * 1000), //chargeTime ~90min
        CAR_BATTERY(55000, 12, 40, 6 * 60 * 60 * 1000); //chargeTime 5-7h = ~6? elég változó

        public final double maxCapacity;
        public final double voltage;
        public final double drainRate;
        public final long chargeTime;
    }


    //logoláshoz minden batterynek külön csv és ehhez kell vmi identifier, sima id meg kevés imo
    @Getter
    private String name;

    /**
     * Maximum capacity of the battery (mAh)
     */
    @Getter
    private double maxCapacity;

    /**
     * Current level of the battery (mAh). The battery starts fully charged at maximum capacity,
     * the value gets constantly lowered until 0, when the battery has to be charged.
     */
    @Getter @Setter
    private double currLevel;

    /**
     * The battery's voltage (V)
     */
    @Getter
    private double voltage;

    /**
     * Constant value used to drain the battery when the battery device is idle. (currently in mAh/h - mAh drained in an hour)
     */
    @Getter
    private double drainRate;

    /**
     * The time it takes to charge the battery from 0% to 100% (in ticks).
     */
    @Getter
    @Setter
    private long chargeTime;

    /**
     * Used to stop battery drainage or task execution while charging.
     */
    @Getter
    private boolean isCharging;

    //STOPTIME adattag hogy lehessen simulate until last eventezni, az eszköz stopTimeja alapján van beállítva
    @Setter
    @Getter
    private long stopTime;

    //hashmap vszeg gyorsabb, de igy olvashatóbb a csv
    public final Map<Long, Double> readings = new TreeMap<>();


    /**
     * Constructor for battery objects
     *
     * @param maxCapacity the maximum capacity of the battery (mAh)
     * @param voltage     the voltage of the battery (V)
     * @param hostMinConsumption the value the battery physical machine's CPU's (and memory) minimal consumption (W)
     * @param chargeTime  the time it takes to charge the battery to full (in ticks).
     */
    public Battery(String name, double maxCapacity, double voltage, double hostMinConsumption, long chargeTime) {
        this.name = name;
        this.maxCapacity = maxCapacity;
        this.voltage = voltage;
        this.drainRate = hostMinConsumption/voltage*1000;
        this.chargeTime = chargeTime;
        this.stopTime = 60 * 60 * 1000; // 1hour base value

        this.currLevel = maxCapacity;
        this.isCharging = false;

        readings.put(Timed.getFireCount(), currLevel);
        subscribe(60000);
    }

    public Battery(String name, BatteryType batteryType) {
        this.name = name;
        this.maxCapacity = batteryType.maxCapacity;
        this.voltage = batteryType.voltage;
        this.drainRate = batteryType.drainRate;
        this.chargeTime = batteryType.chargeTime;
        this.stopTime = 60 * 60 * 1000; // 1hour base value

        this.currLevel = batteryType.maxCapacity;
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
        if(stopTime < Timed.getFireCount()){
            unsubscribe();
        }

        if(currLevel - drainRate / 60 < 0){
            unsubscribe();
            new Charge(chargeTime);
            return;
        }

        // mAh/h a drainRate, de az event percenkénti, ezért lesz a mAh/hból mAh/min
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
            readings.put(Timed.getFireCount(),currLevel);

            isCharging = false;
            subscribe(60000);
        }
    }

    public double getCurrentPercentage(){
        return currLevel / maxCapacity * 100;
    }


    public void writeToFileConsumption(String resultDirectory){
        try {
            FileWriter fw = new FileWriter(resultDirectory + File.separator + name +".csv");

            fw.write("Timestamp (sec)," + name + " (mAh)\n");
            for (var entry : readings.entrySet()) {
                fw.write(entry.getKey()/1000 + "," + entry.getValue() + "\n");
            }

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //jelenleg az eltérő diagram felosztások miatt használhatatlan
    public void writeToFilePercentage(String resultDirectory){
        try {
            FileWriter fw = new FileWriter(resultDirectory + File.separator + name +".csv");

            fw.write("Timestamp (sec)," + name + " (%)\n");
            for (var entry : readings.entrySet()) {
                fw.write(entry.getKey()/1000 + "," + Math.round(entry.getValue() / maxCapacity * 100) + "\n");
            }

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
