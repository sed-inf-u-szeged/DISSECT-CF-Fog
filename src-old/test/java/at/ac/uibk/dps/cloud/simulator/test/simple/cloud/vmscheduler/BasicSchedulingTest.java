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
package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmscheduler;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class BasicSchedulingTest extends IaaSRelatedFoundation {

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void keepFreeUpdated()
			throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException, VMManagementException {
		IaaSService s = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, 1, 1);
		Repository r = s.repositories.get(0);
		VirtualAppliance va = (VirtualAppliance) r.contents().iterator().next();
		AlterableResourceConstraints rc = new AlterableResourceConstraints(s.getCapacities());
		rc.multiply(0.5);
		VirtualMachine vmFirst = s.requestVM(va, rc, r, 1)[0];
		VirtualMachine vmSecond = s.requestVM(va, s.getCapacities(), r, 1)[0];
		Timed.simulateUntilLastEvent();
		vmFirst.destroy(true);
		Timed.simulateUntilLastEvent();
		assertEquals(VirtualMachine.State.DESTROYED,
				vmFirst.getState(), "The first VM should have finished by now");
		assertEquals(VirtualMachine.State.RUNNING, vmSecond.getState(), "The second VM should have a chance to run after the first one");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void multipleVMAndFreeResourceMaintenance()
			throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException, VMManagementException {
		IaaSService s = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, 5, 1);
		Repository r = s.repositories.get(0);
		VirtualAppliance va = (VirtualAppliance) r.contents().iterator().next();
		ResourceConstraints rc = s.machines.get(0).getCapacities();
		VirtualMachine vmFirst = s.requestVM(va, rc, r, 1)[0];
		VirtualMachine vmSecond = s.requestVM(va, rc, r, 5)[0];
		Timed.simulateUntilLastEvent();
		vmFirst.destroy(true);
		Timed.simulateUntilLastEvent();
		assertEquals(VirtualMachine.State.DESTROYED,
				vmFirst.getState(), "The first VM should have finished by now");
		assertEquals(VirtualMachine.State.RUNNING, vmSecond.getState(), "The second VM should have a chance to run after the first one");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void preFailingAllocation()
			throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException, VMManagementException {
		IaaSService s = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, 5, 1);
		Repository r = s.repositories.get(0);
		VirtualAppliance va = (VirtualAppliance) r.contents().iterator().next();
		ResourceConstraints machineCaps = s.machines.get(0).getCapacities();
		AlterableResourceConstraints rc = new AlterableResourceConstraints(machineCaps);
		rc.multiply(0.6);
		VirtualMachine[] vmsFirst = s.requestVM(va, rc, r, 2);
		VirtualMachine vmSecond = s.requestVM(va, machineCaps, r, 5)[0];
		Timed.simulateUntilLastEvent();
		for (VirtualMachine vm : vmsFirst) {
			vm.destroy(true);
		}
		Timed.simulateUntilLastEvent();
		assertEquals(VirtualMachine.State.DESTROYED,
				vmsFirst[0].getState(), "The first VM should have finished by now");
		assertEquals(VirtualMachine.State.RUNNING, vmSecond.getState(), "The second VM should have a chance to run after the first one");
	}

	public static class AssertFulScheduler extends Scheduler {
		public AssertFulScheduler(IaaSService parent) {
			super(parent);
		}

		@Override
		protected ConstantConstraints scheduleQueued() {
			fail("This scheduler should never be called");
			return ConstantConstraints.noResources;
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void dontFireSchedulerOnEmptyQueue() throws IllegalArgumentException, SecurityException,
			InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		setupIaaS(AssertFulScheduler.class, AlwaysOnMachines.class, 10, 1);
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void disallowVMRequestsWithoutVAonSource() {
		assertThrows(VMManagementException.class, () -> {
			IaaSService s = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, 1, 1);
			VirtualAppliance va = new VirtualAppliance("nonregistered", 1, 0);
			s.requestVM(va, s.getCapacities(), s.repositories.get(0), 1);
		});
	}
}
