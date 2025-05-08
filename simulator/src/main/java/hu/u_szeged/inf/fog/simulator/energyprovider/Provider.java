package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

public class Provider {

    ArrayList<EnergySource> renewableSources;
    FossilSource fossilSource;
    public Battery renewableBattery;
    float renewableBasePrice;
    public float renewablePrice;
    public float fossilBasePrice;
    long chargeFreq;
    long priceFreq;
    public Charge charge;
    ChangePrice changePrice;
    double multiplier;
    float batteryStartingCharge;

    public Provider(Battery renewableBattery, ArrayList<EnergySource> renewableSources,FossilSource fossilSource,
                    long chargeFreq, long priceFreq,
                    float renewableBasePrice, float fossilBasePrice,
                    int maxPriceChange)
        {
        this.renewableSources = renewableSources;
        this.renewableBattery = renewableBattery;
        this.batteryStartingCharge = this.renewableBattery.batteryLevel;
        this.fossilSource = fossilSource;
        this.chargeFreq = chargeFreq;
        this.priceFreq = priceFreq;
        this.renewableBasePrice = renewableBasePrice;
        this.multiplier = (maxPriceChange / 100.0);
        this.renewablePrice = (float) (this.renewableBasePrice * (1 + multiplier * ((50 - this.renewableBattery.getBatteryPercentage()) / 50.0)));
        this.fossilBasePrice = fossilBasePrice;
        this.charge = new Charge(this);
        //this.changePrice = new ChangePrice(this, maxPriceChange);
    }

    public float calculatePrice() {
        try {
            new File(new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath());
            PrintStream out = new PrintStream(
                    new FileOutputStream(ScenarioBase.resultDirectory +"/charge.txt", true), true);
            System.setOut(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.print("Price before change: " + this.renewablePrice);
        this.renewablePrice = (float) (this.renewableBasePrice * (1 + multiplier * ((50 - this.renewableBattery.getBatteryPercentage()) / 50.0)));
        System.out.println("  -------  Time: " + Timed.getFireCount() + "  -------  Price after change: " + this.renewablePrice);
        return this.renewablePrice;
    }

    public void addEnergySource(EnergySource energySource) {
        this.renewableSources.add(energySource);
    }

    public float getMaxMixedOutput () {
        return this.renewableBattery.getCurrentMaxOutput() + getTotalRenewableProduction() + this.fossilSource.Production();
    }

    public float getMaxRenewableOutput () {
        return this.renewableBattery.getCurrentMaxOutput() + getTotalRenewableProduction();
    }

    public float getMaxFossilOutput () {
        return this.fossilSource.production;
    }

    private float getTotalRenewableProduction() {
        float sum = 0;
        for (EnergySource source : this.renewableSources) {
            sum += source.Production(Timed.getFireCount(), (long) this.chargeFreq);
        }
        return sum;
    }

    public void stopProcessing() {
        this.charge.stop();
        //this.changePrice.stop();
    }

}
