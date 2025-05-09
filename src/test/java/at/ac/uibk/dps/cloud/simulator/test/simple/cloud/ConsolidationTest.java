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
package at.ac.uibk.dps.cloud.simulator.test.simple.cloud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.concurrent.TimeUnit;

public class ConsolidationTest extends IaaSRelatedFoundation {
	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void simpleConsolidate() throws Exception {
		IaaSService iaas = new IaaSService(FirstFitScheduler.class, SchedulingDependentMachines.class);
		Repository r = dummyRepoCreator(true);
		iaas.registerRepository(r);
		VirtualAppliance va = (VirtualAppliance) r.contents().iterator().next();
		// Smaller PM expected to be used
		PhysicalMachine small = dummyPMcreator();
		ResourceConstraints smallCaps = small.getCapacities();
		final double sPcPP = smallCaps.getRequiredProcessingPower();
		// Bigger PM to be shut down
		PhysicalMachine big = dummyPMsCreator(1, 2, sPcPP, smallCaps.getRequiredMemory())[0];
		iaas.registerHost(big);
		iaas.registerHost(small);
		// Half of the computing resources of small vm, memory is irrelevant
		ResourceConstraints baseConstraints = new ConstantConstraints(smallCaps.getRequiredCPUs(), sPcPP / 2, 1);
		VirtualMachine[] vms = iaas.requestVM(va, baseConstraints, r, 6);
		Timed.simulateUntilLastEvent();
		// Setup by now: big PM 4 VMs, small PM 2 VMs.
		for (VirtualMachine virtualMachine : vms) {
			assertEquals(virtualMachine.getState(), VirtualMachine.State.RUNNING);
		}
		// 1 VM from small is dropped:
		for (VirtualMachine vm : vms) {
			if (vm.getResourceAllocation().getHost() == small) {
				vm.destroy(true);
				break;
			}
		}
		int count = 0;
		// 3 VMs from big are dropped.
		for (VirtualMachine vm : vms) {
			ResourceAllocation ra = vm.getResourceAllocation();
			if (ra != null && ra.getHost() == big) {
				vm.destroy(true);
				count++;
				if (count == 3)
					break;
			}
		}
		Timed.simulateUntilLastEvent();
		// Setup by now: big 1 VM, small 1 VM. (thus big has more free
		// resources)
		assertTrue(big.publicVms.size() == 1 && small.publicVms.size() == 1);
		// Now we can ask the consolidator to move away the VM from big.
		new SimpleConsolidator(iaas, 100);
		Timed.simulateUntil(Timed.getFireCount() + 1000);
		// Setup by now: big shut down, small 2 VMs.
		assertTrue(!big.isHostingVMs() && small.publicVms.size() == 2);
		// Cleanup.
		for (VirtualMachine vm : vms) {
			if (vm.getResourceAllocation() != null) {
				vm.destroy(true);
			}
		}
		Timed.simulateUntilLastEvent();
	}
}
