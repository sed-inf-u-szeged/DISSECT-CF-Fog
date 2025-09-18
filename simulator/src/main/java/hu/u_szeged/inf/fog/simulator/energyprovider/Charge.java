package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class Charge extends Timed {

    Provider provider;
    float lastTotalAdded;


    /**
     * Recurring Timed event that charges the battery of a Provider with the given renewable sources.
     *
     * @param provider The provider to be charged
     */
    Charge(Provider provider) {
        this.provider = provider;
        subscribe(this.provider.chargeFreq);
    }

    /**
     * Charges the battery of it's corresponding provider and logs every charge.
     */
    @Override
    public void tick(long fires) {
        this.lastTotalAdded = getTotalProduction();
        if (this.lastTotalAdded > 0) {
            File file = new File(ScenarioBase.resultDirectory +"/Provider-"+ provider.id +"/Charge.txt");
            file.getParentFile().mkdirs();
            try {
                PrintStream out = new PrintStream(
                        new FileOutputStream(ScenarioBase.resultDirectory +"/Provider-"+ provider.id +"/Charge.txt", true), true);
                System.setOut(out);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            System.out.print("Current energy: " + provider.renewableBattery.getBatteryLevel() + " Wh");
            System.out.print("  -------  Added energy: " + this.lastTotalAdded + " Wh");
            System.out.print("  -------  Time: " + Timed.getFireCount());
            System.out.println("  -------  Energy after charge: " + chargeUp() + " Wh");
            provider.calculatePrice();
        }
    }

    private float chargeUp() {
        return this.provider.renewableBattery.addBatteryLevel(this.lastTotalAdded);
    }

    /**
     *
     * @return The total ammount produced by all of the renewable sources belonging to this Provider
     */

    private float getTotalProduction() {
        float sum = 0;
        for (EnergySource source : this.provider.renewableSources) {
            float[] helper = {Timed.getFireCount(), source.production(Timed.getFireCount(), getFrequency())};
            if (source instanceof Solar) {
                this.provider.solarRecords.add(helper);
                this.provider.totalSolarProduced += source.production(Timed.getFireCount(), getFrequency());
            } else {
                this.provider.windRecords.add(helper);
                this.provider.totalWindProduced += source.production(Timed.getFireCount(), getFrequency());
            }
            sum += source.production(Timed.getFireCount(), getFrequency());
        }
        float[] helper = {Timed.getFireCount(), this.provider.renewableBattery.getBatteryLevel()};
        this.provider.energyRecords.add(helper);
        this.provider.totalRenewableProduced += sum;
        return sum;
    }

    public boolean stop() {
        return unsubscribe();
    }

}
