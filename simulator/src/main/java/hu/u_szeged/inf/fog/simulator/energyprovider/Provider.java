package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;

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
    float fossilPrice;
    long chargeFreq;
    Charge charge;
    float maxPriceChange;
    float referenceLevel;

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
                    long chargeFreq,
                    float renewableBasePrice, float fossilPrice,
                    int maxPriceChange, int referenceLevel)
    {
        this.id = id;
        this.renewableSources = renewableSources;
        this.renewableBattery = renewableBattery;
        this.batteryStartingCharge = this.renewableBattery.batteryLevel;
        this.fossilSource = fossilSource;
        this.chargeFreq = chargeFreq;
        this.renewableBasePrice = renewableBasePrice;
        this.maxPriceChange = (float) (maxPriceChange / 100.0);
        this.referenceLevel = referenceLevel;
        this.renewablePrice = this.renewableBasePrice *
                            (1 + this.maxPriceChange *
                            ((this.referenceLevel - this.renewableBattery.getBatteryPercentage()) /
                            this.referenceLevel));
        this.fossilPrice = fossilPrice;
        this.charge = new Charge(this);
    }

    public void calculatePrice() {
        try {
            File file = new File(ScenarioBase.resultDirectory +"/Provider-"+ this.id +"/price.txt");
            file.getParentFile().mkdirs();
            PrintStream out = new PrintStream(
                    new FileOutputStream(ScenarioBase.resultDirectory +"/Provider-"+ this.id +"/price.txt", true), true);
            System.setOut(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.print("Price before change: " + this.renewablePrice);
        this.renewablePrice = (this.renewableBasePrice * (1 + maxPriceChange * ((this.referenceLevel - this.renewableBattery.getBatteryPercentage()) / this.referenceLevel)));
        System.out.println("  -------  Time: " + Timed.getFireCount() + "  -------  Price after change: " + this.renewablePrice);
    }

    public void addEnergySource(EnergySource energySource) {
        this.renewableSources.add(energySource);
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
    public float getFossilPrice(){
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
