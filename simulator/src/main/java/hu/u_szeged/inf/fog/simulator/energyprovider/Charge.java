package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;

import java.io.*;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

public class Charge extends Timed {

    Provider provider;
    float lastTotalAdded;


    /**
     * Recurring Timed event that charges the battery of a Provider with the given renewable sources
     * @param provider The provider to be charged
     */
    Charge(Provider provider) {
        this.provider = provider;
        subscribe(this.provider.chargeFreq);
    }

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

    private float getTotalProduction() {
        float sum = 0;
        for (EnergySource source : this.provider.renewableSources) {
            float[] helper = {Timed.getFireCount(), source.Production(Timed.getFireCount(), getFrequency())};
            if (source instanceof Solar) {
                this.provider.solarRecords.add( helper );
                this.provider.totalSolarProduced += source.Production(Timed.getFireCount(), getFrequency());
            }else {
                this.provider.windRecords.add( helper );
                this.provider.totalWindProduced += source.Production(Timed.getFireCount(), getFrequency());
            }
            sum += source.Production(Timed.getFireCount(), getFrequency());
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
