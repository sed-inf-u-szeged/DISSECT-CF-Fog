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
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.pmscheduler;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService.IaaSHandlingException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class SchedulerDependentTest extends IaaSRelatedFoundation {
	IaaSService basic;
	Repository repo;

	@BeforeEach
	public void resetSim() throws Exception {
		basic = setupIaaS(FirstFitScheduler.class, SchedulingDependentMachines.class, 2, 1);
		repo = basic.repositories.get(0);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void withoutLoadTest() {
		assertEquals(0,	basic.runningMachines.size(), "Should not have machines running at the beginning of the simulation");
		Timed.simulateUntilLastEvent();
		assertEquals(0, basic.runningMachines.size(), "Should not switch any machine on because there is no load");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void simpleLoadTest() throws VMManagementException {
		final HashSet<PhysicalMachine> affectedpms = new HashSet<>();
		for (final PhysicalMachine pm : basic.machines) {
			pm.subscribeStateChangeEvents(
					(PhysicalMachine pmInt, State oldState, State newState) -> affectedpms.add(pmInt));
		}
		VirtualMachine vm = basic.requestVM((VirtualAppliance) repo.contents().iterator().next(),
				basic.machines.get(0).getCapacities(), repo, 1)[0];
		Timed.simulateUntilLastEvent();
		assertEquals(1, affectedpms.size(), "Should only affect a single PM");
		assertEquals(1, basic.runningMachines.size(), "Should only switch on a single PM");
		assertEquals(VirtualMachine.State.RUNNING, vm.getState(), "VM should be able to switch on");
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
		assertEquals(0, basic.runningMachines.size(), "Should switch off all PMs if there is no load");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void overLoadTest() throws VMManagementException {
		VirtualMachine[] vms = basic.requestVM((VirtualAppliance) repo.contents().iterator().next(),
				basic.machines.get(0).getCapacities(), repo, basic.machines.size());
		Timed.simulateUntilLastEvent();
		assertEquals(basic.machines.size(), basic.runningMachines.size(), "Should switch all PMs");
		VirtualMachine extraVM = basic.requestVM((VirtualAppliance) repo.contents().iterator().next(),
				basic.machines.get(0).getCapacities(), repo, 1)[0];
		Timed.simulateUntilLastEvent();
		assertEquals(VirtualMachine.State.DESTROYED, extraVM.getState(), "Should not allow the extra VM to run");
		vms[0].destroy(false);
		Timed.simulateUntilLastEvent();
		assertEquals(VirtualMachine.State.RUNNING, extraVM.getState(), "Now the extra VM should run");
		for (VirtualMachine vm : vms) {
			vm.destroy(false);
		}
		extraVM.destroy(false);
		Timed.simulateUntilLastEvent();
		assertEquals(0, basic.runningMachines.size(), "Should switch off all PMs if there is no load");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void smallVMLoadTest() throws VMManagementException {
		AlterableResourceConstraints rc = new AlterableResourceConstraints(basic.machines.get(0).getCapacities());
		rc.multiply(0.5);
		VirtualMachine[] vms = basic.requestVM((VirtualAppliance) repo.contents().iterator().next(), rc, repo, 2);
		Timed.simulateUntilLastEvent();
		assertEquals(1, basic.runningMachines.size(), "Should only switch on a single PM");
		vms[0].destroy(false);
		Timed.simulateUntilLastEvent();
		assertEquals(1, basic.runningMachines.size(), "Should keep the single PM running");
		assertArrayEquals(new VirtualMachine.State[] { VirtualMachine.State.DESTROYED, VirtualMachine.State.RUNNING },
				new VirtualMachine.State[] { vms[0].getState(), vms[1].getState() }, "VMs not in the expected states");
		vms[1].destroy(false);
		Timed.simulateUntilLastEvent();
		assertEquals(0, basic.runningMachines.size(), "Should switch off all PMs if there is no load");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void decreasingCapacityTest() throws IaaSHandlingException, VMManagementException {
		basic.deregisterHost(basic.machines.get(0));
		VirtualMachine vm = basic.requestVM((VirtualAppliance) repo.contents().iterator().next(),
				basic.machines.get(0).getCapacities(), repo, 1)[0];
		Timed.simulateUntilLastEvent();
		assertEquals(VirtualMachine.State.RUNNING, vm.getState(), "VM should be able to switch on");
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
		assertEquals(0, basic.runningMachines.size(), "Should not have any running PMs");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void deregistrationTest() throws VMManagementException, IaaSHandlingException {
		PhysicalMachine pm = basic.machines.get(0);
		VirtualMachine[] vms = basic.requestVM((VirtualAppliance) repo.contents().iterator().next(), pm.getCapacities(),
				repo, basic.machines.size());
		Timed.simulateUntilLastEvent();
		for (VirtualMachine vm : vms) {
			vm.destroy(false);
		}
		Timed.simulateUntilLastEvent();
		assertEquals(PhysicalMachine.State.OFF, pm.getState(), "Should be switched off");
		basic.deregisterHost(pm);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		assertEquals(PhysicalMachine.State.RUNNING, pm.getState(), "Should be running");
	}
}
