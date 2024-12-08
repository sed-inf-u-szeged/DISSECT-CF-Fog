package hu.u_szeged.inf.fog.simulator.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import java.util.ArrayList;
import java.util.List;

public class EnergyDataCollector extends Timed {
    
    public static List<EnergyDataCollector> energyCollectors = new ArrayList<>();
        
    public double energyConsumption;
    
    PhysicalMachineEnergyMeter pmEnergyMeter;
    
    IaaSEnergyMeter iaasEnergyMeter;
    
    public EnergyDataCollector(IaaSService iaas, long freq) {
        subscribe(freq);
        energyCollectors.add(this);
        this.iaasEnergyMeter = new IaaSEnergyMeter(iaas);
        this.iaasEnergyMeter.startMeter(freq, true);
    }
    
    public EnergyDataCollector(PhysicalMachine pm, long freq) {
        subscribe(freq);
        energyCollectors.add(this);
        this.pmEnergyMeter = new PhysicalMachineEnergyMeter(pm);
        this.pmEnergyMeter.startMeter(freq, true);
    }

    public void stop() {
        unsubscribe();
        if (this.pmEnergyMeter != null) {
            this.pmEnergyMeter.stopMeter();
        }
        if (this.iaasEnergyMeter != null) {
            this.iaasEnergyMeter.stopMeter();
        }
    }
    
    @Override
    public void tick(long fires) {
        energyConsumption = iaasEnergyMeter.getTotalConsumption();
    }
}
