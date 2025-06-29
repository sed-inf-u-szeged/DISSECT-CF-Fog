/*
 *  ========================================================================
 *  DIScrete event baSed Energy Consumption simulaTor 
 *    					             for Clouds and Federations (DISSECT-CF)
 *  ========================================================================
 *  
 *  This file is part of DISSECT-CF.
 *  
 *  DISSECT-CF is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *  
 *  DISSECT-CF is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 *  General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with DISSECT-CF.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  (C) Copyright 2017, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 */
package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmscheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.concurrent.TimeUnit;

public class FirstFitSchedulerTest extends IaaSRelatedFoundation {
	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void toxicSequenceTest() throws Exception {
		IaaSService s = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, 2, 2);
		Repository vaStore = s.repositories.get(0);
		VirtualAppliance va = (VirtualAppliance) vaStore.contents().iterator().next();
		// Boot up the machines
		Timed.simulateUntilLastEvent();
		ResourceConstraints fitsOnePM = s.machines.get(0).getCapacities();
		AlterableResourceConstraints reallySmall = new AlterableResourceConstraints(fitsOnePM);
		reallySmall.multiply(0.5);
		VirtualMachine[] completelyOccupy = s.requestVM(va, reallySmall, vaStore, 4);
		// Fill in the machines
		Timed.simulateUntilLastEvent();
		// Queue some more VMs
		VirtualMachine vmJustFitsOne = s.requestVM(va, fitsOnePM, vaStore, 1)[0];
		ConstantConstraints needsTwo = new ConstantConstraints(fitsOnePM.getRequiredCPUs() * 0.75,
				fitsOnePM.getRequiredProcessingPower(), fitsOnePM.getRequiredMemory());
		VirtualMachine[] vmsNeedingTwoPMs = s.requestVM(va, needsTwo, vaStore, 2);
		Timed.simulateUntilLastEvent();
		// Drop most of the fillers
		for(PhysicalMachine pm:s.runningMachines) {
			// Removes 1 VM from each PM
			pm.listVMs().iterator().next().destroy(false);
		}
		// Remove the last VM from the last PM
		s.runningMachines.get(s.runningMachines.size()-1).listVMs().iterator().next().destroy(false);
		// Let the others get on the system
		Timed.simulateUntilLastEvent();
		assertEquals(VirtualMachine.State.RUNNING, vmJustFitsOne.getState(), "First VM should be running");
		for (VirtualMachine vm : vmsNeedingTwoPMs) {
			assertEquals(VirtualMachine.State.DESTROYED, vm.getState(), "No other VMs should be running");
		}
		// Let the final set of machines on the system
		vmJustFitsOne.destroy(false);
		Timed.simulateUntilLastEvent();
		// Get rid of the very last filler
		for(VirtualMachine vm:completelyOccupy) {
			if(vm.getState().equals(VirtualMachine.State.RUNNING)) {
				vm.destroy(false);
				break;
			}
		}
		Timed.simulateUntilLastEvent();
		for (VirtualMachine vm : vmsNeedingTwoPMs) {
			assertEquals(VirtualMachine.State.RUNNING, vm.getState(), "Second set needs to be running by now");
			vm.destroy(false);
		}
	}
}
