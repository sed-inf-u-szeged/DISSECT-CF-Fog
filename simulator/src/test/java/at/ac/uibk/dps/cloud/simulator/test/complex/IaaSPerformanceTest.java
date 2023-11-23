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

package at.ac.uibk.dps.cloud.simulator.test.complex;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.RoundRobinScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class IaaSPerformanceTest extends IaaSRelatedFoundation {
	public IaaSService basic;
	public Repository repo;
	public VirtualAppliance va;
	public ResourceConstraints baseRC;
	static final int hostCount = 40;
	static final int vmCount = 2000;
	static final int maxTaskCount = 5;
	static final double maxTaskLen = 50;
	public int runningCounter = 0;
	public int destroyCounter = 0;

	@BeforeEach
	public void resetSim() {
		SeedSyncer.resetCentral();
	}

	public class VMHandler implements VirtualMachine.StateChange, ConsumptionEvent {
		private final VirtualMachine vm;
		private int myTaskCount;

		public VMHandler() throws Exception {
			AlterableResourceConstraints mRC = new AlterableResourceConstraints(baseRC);
			mRC.multiply(SeedSyncer.centralRnd.nextDouble());
			vm = basic.requestVM(va, mRC, repo, 1)[0];
			vm.subscribeStateChange(this);
			Timed.simulateUntil(Timed.getFireCount() + SeedSyncer.centralRnd.nextInt((int) maxTaskLen));
		}

		@Override
		public void stateChanged(VirtualMachine vm, final State oldState, final State newState) {
			switch (newState) {
			case RUNNING:
				runningCounter++;
				myTaskCount = 1 + SeedSyncer.centralRnd.nextInt(maxTaskCount - 1);
				try {
					for (int j = 0; j < myTaskCount; j++) {
						vm.newComputeTask(SeedSyncer.centralRnd.nextDouble() * maxTaskLen,
								ResourceConsumption.unlimitedProcessing, this);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				break;
			case DESTROYED:
				destroyCounter++;
			default:
			}
		}

		@Override
		public void conComplete() {
			if (--myTaskCount == 0) {
				try {
					vm.destroy(false);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override
		public void conCancelled(final ResourceConsumption problematic) {
			throw new RuntimeException("No cancellations should happen");
		}
	}

	private void genericPerformanceCheck(Class<? extends Scheduler> vmsch,
			Class<? extends PhysicalMachineController> pmsch) throws Exception {
		basic = setupIaaS(vmsch, pmsch, hostCount, 1);
		baseRC = basic.machines.get(0).getCapacities();
		repo = basic.repositories.get(0);
		va = (VirtualAppliance) repo.contents().iterator().next();
		for (int i = 0; i < vmCount; i++) {
			new VMHandler();
		}
		Timed.simulateUntilLastEvent();
		assertEquals(vmCount, runningCounter, "Not all VMs ran");
		assertEquals(vmCount, destroyCounter, "Not all VMs terminated");
		for (PhysicalMachine pm : basic.runningMachines) {
			assertFalse(pm.isHostingVMs(), "Should not have any running VMs registered");
		}
	}

	@Test
	@Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
	public void performanceTest() throws Exception {
		genericPerformanceCheck(FirstFitScheduler.class, SchedulingDependentMachines.class);
		assertEquals(0, basic.runningMachines.size(), "Should not have any running PMs");
	}

	@Test
	@Timeout(value = 1500, unit = TimeUnit.MILLISECONDS)
	public void roundRobinPerformance() throws Exception {
		genericPerformanceCheck(RoundRobinScheduler.class, AlwaysOnMachines.class);
	}

	// FIXME: this should be below 100ms!
	@Test
	@Timeout(value = 700, unit = TimeUnit.MILLISECONDS)
	public void pmRegistrationPerformance() throws Exception {
		setupIaaS(FirstFitScheduler.class, SchedulingDependentMachines.class, 10000, 1);
	}

}
