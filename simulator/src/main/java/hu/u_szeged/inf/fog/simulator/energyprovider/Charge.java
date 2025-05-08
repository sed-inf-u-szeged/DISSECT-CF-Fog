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
    public List<float[]> charges = new ArrayList<>();
    public List<float[]> solarProd = new ArrayList<>();
    public List<float[]> windProd = new ArrayList<>();

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
            try {
                new File(new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath());
                PrintStream out = new PrintStream(
                        new FileOutputStream(ScenarioBase.resultDirectory +"/output.txt", true), true);
                System.setOut(out);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            log();
            System.out.print("Current energy: " + provider.renewableBattery.getBatteryLevel() + " Wh");
            System.out.print("  -------  Added energy: " + this.lastTotalAdded + " Wh");
            System.out.print("  -------  Time: " + Timed.getFireCount());
            System.out.println("  -------  Energy after charge: " + chargeUp() + " Wh");
            provider.calculatePrice();
        }
    }

    void log() {
        float[] helper = {Timed.getFireCount(), provider.renewableBattery.getBatteryLevel()};
        this.charges.add( helper );
    }

    private float chargeUp() {
        return this.provider.renewableBattery.addBatteryLevel(this.lastTotalAdded);
    }

    private float getTotalProduction() {
        float sum = 0;
        for (EnergySource source : this.provider.renewableSources) {
            float[] helper = {Timed.getFireCount(), source.Production(Timed.getFireCount(), getFrequency())};
            if (source instanceof Solar) {
                this.solarProd.add( helper );
            }else {
                this.windProd.add( helper );
            }
            sum += source.Production(Timed.getFireCount(), getFrequency());
        }
        return sum;
    }

    public boolean stop() {
        return unsubscribe();
    }

}
