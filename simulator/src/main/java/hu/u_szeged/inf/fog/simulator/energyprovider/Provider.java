package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.ArrayList;

public class Provider {

    ArrayList<EnergySource> renewableSources;
    FossilSource fossilSource;
    Battery renewableBattery;
    float renewablePrice;
    float fossilPrice;
    float chargeFreq;

    public Provider(Battery renewableBattery, ArrayList<EnergySource> renewableSources,FossilSource fossilSource,long chargeFreq) {
        this.renewableSources = renewableSources;
        this.renewableBattery = renewableBattery;
        this.fossilSource = fossilSource;
        this.chargeFreq = chargeFreq;
        new Charge(chargeFreq,this);
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

    //TODO calculate max output of a provider
    //TODO changing price based on battery level


}
