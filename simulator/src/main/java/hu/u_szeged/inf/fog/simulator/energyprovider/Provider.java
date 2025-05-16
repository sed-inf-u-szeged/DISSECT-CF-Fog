package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class Provider {

    public String id;
    ArrayList<EnergySource> renewableSources;
    FossilSource fossilSource;
    public Battery renewableBattery;
    float renewableBasePrice;
    float renewablePrice;
    float fossilBasePrice;
    long chargeFreq;
    long priceFreq;
    public Charge charge;
    double multiplier;
    float batteryStartingCharge;
    public List<float[]> energyRecords = new ArrayList<>();
    public List<float[]> solarRecords = new ArrayList<>();
    public List<float[]> windRecords = new ArrayList<>();
    public float totalRenewableProduced = 0;
    public float totalWindProduced = 0;
    public float totalSolarProduced = 0;
    public float totalFossilUsed = 0;
    public float totalRenewableUsed = 0;
    public float moneySpentOnFossil = 0;
    public float moneySpentOnRenewable = 0;

    public Provider(String id, Battery renewableBattery, ArrayList<EnergySource> renewableSources,FossilSource fossilSource,
                    long chargeFreq, long priceFreq,
                    float renewableBasePrice, float fossilBasePrice,
                    int maxPriceChange)
        {
        this.id = id;
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
    }

    public void calculatePrice() {
        try {
            File file = new File(ScenarioBase.resultDirectory +"/Provider-"+ this.id +"/Price.txt");
            file.getParentFile().mkdirs();
            PrintStream out = new PrintStream(
                    new FileOutputStream(ScenarioBase.resultDirectory +"/Provider-"+ this.id +"/Price.txt", true), true);
            System.setOut(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.print("Price before change: " + this.renewablePrice);
        this.renewablePrice = (float) (this.renewableBasePrice * (1 + multiplier * ((50 - this.renewableBattery.getBatteryPercentage()) / 50.0)));
        System.out.println("  -------  Time: " + Timed.getFireCount() + "  -------  Price after change: " + this.renewablePrice);
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

        File file = new File(ScenarioBase.resultDirectory +"/Provider-"+ this.id +"/log.txt");
        file.getParentFile().mkdirs();
        try {
            PrintStream out = new PrintStream(
                    new FileOutputStream(ScenarioBase.resultDirectory +"/Provider-"+ this.id +"/log.txt", true), true);
            System.setOut(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Total renewable produced (Wh): " + totalRenewableProduced);
        System.out.println("Total solar produced (Wh): " + totalSolarProduced);
        System.out.println("Total wind produced (Wh): " + totalWindProduced);
        System.out.println("Total fossil used (Wh): " + totalFossilUsed);
        System.out.println("Total renewable used (Wh): " + totalRenewableUsed);
        System.out.println("Money spent on fossil (EUR): " + moneySpentOnFossil);
        System.out.println("Money spent on renewable (EUR): " + moneySpentOnRenewable);
        System.out.println("Total money spent (EUR): " + (moneySpentOnRenewable+moneySpentOnFossil));
    }

    public float getRenewablePrice(){
        return this.renewablePrice;
    }
    public float getFossilBasePrice(){
        return this.renewableBasePrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Provider provider = (Provider) o;

        return id != null ? id.equals(provider.id) : provider.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

}
