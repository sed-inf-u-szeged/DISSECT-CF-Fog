package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.ArrayList;

public class Provider {

    ArrayList<EnergySource> renewableSources;
    FossilSource fossilSource;
    Battery renewableBattery;
    float renewableBasePrice;
    float renewablePrice;
    float fossilBasePrice;
    long chargeFreq;
    long priceFreq;
    Charge charge;
    ChangePrice changePrice;

    public Provider(Battery renewableBattery, ArrayList<EnergySource> renewableSources,FossilSource fossilSource,
                    long chargeFreq, long priceFreq,
                    float renewableBasePrice, float fossilBasePrice,
                    int maxPriceChange)
        {
        this.renewableSources = renewableSources;
        this.renewableBattery = renewableBattery;
        this.fossilSource = fossilSource;
        this.chargeFreq = chargeFreq;
        this.priceFreq = priceFreq;
        this.renewableBasePrice = renewableBasePrice;
        double multiplier = (maxPriceChange / 100.0);
        this.renewablePrice = (float) (this.renewableBasePrice * (1 + multiplier * ((50 - this.renewableBattery.getBatteryPercentage()) / 50.0)));;
        this.fossilBasePrice = fossilBasePrice;
        this.charge = new Charge(this);
        this.changePrice = new ChangePrice(this, maxPriceChange);
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
        this.changePrice.stop();
    }

}
