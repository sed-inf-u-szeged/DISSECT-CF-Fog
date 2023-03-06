package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;

public class HoldDownApplicationStrategy extends ApplicationStrategy{

	@Override
	public void findApplication(long dataForTransfer) {
		if(this.application.computingAppliance.neighbors.size() > 0) {
			double min = Double.MAX_VALUE;
			ComputingAppliance selectedCa = null;
			for(ComputingAppliance ca : this.application.computingAppliance.neighbors) {
				if(ca.getLoadOfResource() < min) {
					min = ca.getLoadOfResource();
					selectedCa = ca;
				}
			}
			Application chosenApplication = selectedCa.applications.get(SeedSyncer.centralRnd.nextInt(selectedCa.applications.size()));
			this.startDataTranfer(chosenApplication, dataForTransfer);
		}
	}
}
