package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.ArrayList;

public class Provider {

    ArrayList<EnergySource> renewableSources;
    Battery renewableBattery;

    public Provider(Battery renewableBattery, ArrayList<EnergySource> renewableSources,long chargeFreq) {
        this.renewableSources = renewableSources;
        this.renewableBattery = renewableBattery;
        new Charge(chargeFreq,this);
    }

    public void addEnergySource(EnergySource energySource) {
        this.renewableSources.add(energySource);
    }

}
