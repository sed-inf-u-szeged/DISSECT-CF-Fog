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

package at.ac.uibk.dps.cloud.simulator.test.simple.cloud;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.EnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.MonitorConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.SimpleVMEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

public class PowerMeterTest extends IaaSRelatedFoundation {

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void VMmeasurementTest() throws VMManagementException, NetworkException {
		PhysicalMachine pm = dummyPMcreator();
		Repository repo = dummyRepoCreator(true);
		repo.setState(NetworkNode.State.RUNNING);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		VirtualMachine vm = pm.requestVM((VirtualAppliance) repo.contents().iterator().next(), pm.getCapacities(), repo,
				1)[0];
		Timed.simulateUntilLastEvent();
		final EnergyMeter meter = new SimpleVMEnergyMeter(vm);
		meter.startMeter(aSecond / 10, true);
		Timed.simulateUntil(Timed.getFireCount() + aSecond);
		assertEquals(totalIdle * aSecond, meter.getTotalConsumption(), 0.1, "The idle machine is not consuming as much as expected");
		long before = Timed.getFireCount();
		meter.stopMeter();
		Timed.simulateUntilLastEvent();
		long after = Timed.getFireCount();
		assertEquals(before, after, "Should not be any events if a meter is not executing");
		meter.startMeter(aSecond, true);
		new DeferredEvent(aSecond) {
			@Override
			protected void eventAction() {
				meter.stopMeter();
			}
		};
		Timed.simulateUntilLastEvent();
		assertEquals(totalIdle * aSecond, meter.getTotalConsumption(), 0.1, "The idle machine's consumption should not depend on the measurement frequency of the meter");
		assertFalse(meter.isSubscribed());
		meter.startMeter(aSecond / 10, true);
		ResourceConstraints rc = vm.getResourceAllocation().allocated;
		final long taskleninms = 10 * aSecond;
		assertEquals(0, ConsumptionEventAssert.hits.size());
		vm.newComputeTask(rc.getTotalProcessingPower() * taskleninms, ResourceConsumption.unlimitedProcessing,
				new ConsumptionEventAssert(Timed.getFireCount() + taskleninms, true) {
					@Override
					public void conComplete() {
						super.conComplete();
						meter.stopMeter();
					}
				});
		Timed.simulateUntilLastEvent();
		assertEquals(1, ConsumptionEventAssert.hits.size());
		assertEquals(taskleninms * (maxpower - idlepower + totalIdle), meter.getTotalConsumption(), 0.1, "The consumption is not properly reported if there is a task processed");
		meter.startMeter(aSecond / 10, true);
		vm.newComputeTask(rc.getTotalProcessingPower() * taskleninms, rc.getRequiredProcessingPower() * 0.5,
				new ConsumptionEventAssert());
		Timed.simulateUntil(Timed.getFireCount() + taskleninms);
		assertEquals(taskleninms * (0.5 * (maxpower - idlepower) + totalIdle), meter.getTotalConsumption(), 0.1, "The consumption is not properly reported if there is a task processed");
		meter.stopMeter();
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void PSTest() {
		PowerState psLinear = new PowerState(1, 1, LinearConsumptionModel::new);
		PowerState psConstant = new PowerState(1, 1, ConstantConsumptionModel::new);
		assertEquals(1.5, psLinear.getCurrentPower(0.5),
				0.001, "Linear consumption model is not behaving as expected");
		assertEquals(1,
				psConstant.getCurrentPower(0.5), 0.001, "Constant consumption model is not behaving as expected");
	}

	static class MeterManager extends Timed {
		IaaSService iaas;
		int expectedVMnum;
		List<? extends EnergyMeter> managed;
		long maxExpected;

		public MeterManager(IaaSService iaas, long meterInterval, long managementInterval, int expectedVMs,
				List<? extends EnergyMeter> meters, long maxExpectedTime) {
			this.iaas = iaas;
			expectedVMnum = expectedVMs;
			for (EnergyMeter em : meters) {
				em.startMeter(meterInterval * aSecond, false);
			}
			managed = meters;
			maxExpected = maxExpectedTime;
			subscribe(managementInterval * aSecond);
		}

		@Override
		public void tick(long fires) {
			assertTrue(fires <= maxExpected, "Should have finished the execution of all VMs by this time, current time was:" + fires);
			if (iaas.runningMachines.size() == 0) {
				int totVMcount = 0;
				for (PhysicalMachine pm : iaas.machines) {
					totVMcount += pm.getCompletedVMs();
				}
				if (totVMcount == expectedVMnum) {
					for (EnergyMeter currEm : managed) {
						currEm.stopMeter();
					}
					unsubscribe();
				}
			}
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void checkforPMSchedulerConflict() throws Exception {
		final IaaSService iaas = setupIaaS(FirstFitScheduler.class, SchedulingDependentMachines.class, 2, 1);
		final int vmNum = 3;
		final int parallelThreads = 2;
		final long startAt = 100;
		for (long i = startAt; i < startAt + vmNum; i++) {
			fireVMat(iaas, i, 400, parallelThreads);
		}
		final ArrayList<EnergyMeter> meters = new ArrayList<>();
		for (PhysicalMachine pm : iaas.machines) {
			meters.add(new PhysicalMachineEnergyMeter(pm));
		}
		final long managementFrequency = 60;
		new MeterManager(iaas, 5, managementFrequency, vmNum * parallelThreads, meters, managementFrequency * 1000);
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 300, unit = TimeUnit.MILLISECONDS)
	public void prolongedMeterTestThroughIaaS() throws Exception {
		final long[] fireAt = {1135130133000L, 1135130401000L, 1135130415000L, 1135130438000L, 1135130471000L,
				1135130835000L, 1138120593000L, 1138120603000L, 1138120664000L, 1138120938000L, 1138120952000L,
				1138121042000L, 1138121107000L};
		final double[] process = { 6, 13, 17, 17, 5, 16, 6, 5, 5, 6, 9, 964, 5 };
		final int[] cores = { 1, 1, 20, 1, 4, 1, 1, 1, 1, 80, 80, 80, 1 };
		final IaaSService iaas = new IaaSService(FirstFitScheduler.class, SchedulingDependentMachines.class);
		final ArrayList<EnergyMeter> meters = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			final PhysicalMachine pm = new PhysicalMachine(
					64, 1, 64 * 1024, new Repository(vaSize, generateName("M", 1), 1, 1, 1, globalLatencyMap,
							defaultStorageTransitions, defaultNetworkTransitions),
					89000, 29000, defaultHostTransitions);
			iaas.registerHost(pm);
			meters.add(new PhysicalMachineEnergyMeter(pm));
		}
		iaas.registerRepository(dummyRepoCreator(true));
		for (int i = 0; i < fireAt.length; i++) {
			fireVMat(iaas, fireAt[i], process[i], cores[i]);
		}
		Timed.skipEventsTill(fireAt[0] - 1);
		// FIXME: lower management frequencies should be also possible within
		// the timeout
		new MeterManager(iaas, 1200, 3600, process.length + 3 /*
																 * for the 80 core vms
																 */, meters, 1138200000000L);
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void aggregatedIaaStest() throws Exception {
		final int machineCount = 2;
		final int coreCount = 16;
		final int meterFreq = 500;
		final long[] len = new long[1];
		final double[] meteredResults = new double[3];

		for (int i = 0; i < 4; i++) {
			// Round 0 => getting the timing of the four PM's startup
			// Round 1 => measuring the energy of two PM's operation during that
			// time
			// Round 2 => measuring the energy of the four PM's startup
			// Round 3 => measuring the energy of two PM's operation as it would
			// happen with just the second two PMs within the four PM setup
			final int round = i;
			final IaaSService iaas = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, machineCount,
					coreCount);
			final int[] counter = new int[] { 0 };
			final IaaSEnergyMeter myMeter = new IaaSEnergyMeter(iaas);
			PhysicalMachine.StateChangeListener myListener = new PhysicalMachine.StateChangeListener() {
				@Override
				public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
					if (newState.equals(PhysicalMachine.State.RUNNING)) {
						counter[0]++;
						if (counter[0] == machineCount) {
							// The first two machines are up and running
							final PhysicalMachine.StateChangeListener localListener = this;
							// Round 1: keep this event running until round 0
							// was running.
							new DeferredEvent((round == 1 ? len[0] - Timed.getFireCount() : 1000)) {
								@Override
								protected void eventAction() {
									// We make sure the meter's reading is
									// updated
									switch (round) {
									case 0:
									case 2:
										// Let's add another two machines
										List<PhysicalMachine> newpmlist = Arrays.asList(dummyPMsCreator(machineCount,
												coreCount, IaaSServiceTest.dummyPMPerCorePP,
												IaaSServiceTest.dummyPMMemory));
										for (PhysicalMachine pm : newpmlist) {
											pm.subscribeStateChangeEvents(localListener);
										}
										iaas.bulkHostRegistration(newpmlist);
										break;
									case 1:
										myMeter.stopMeter();
										meteredResults[0] = myMeter.getTotalConsumption();
										break;
									case 3:
										myMeter.stopMeter();
										meteredResults[2] = myMeter.getTotalConsumption();
									default:

									}
								}
							};
						} else if (counter[0] == machineCount * 2) {
							// All four machines running, let's wait a little
							// for some idle consumptions
							new DeferredEvent(1000) {
								@Override
								protected void eventAction() {
									switch (round) {
									case 1:
									case 3:
										// Should not get here!
										break;
									case 0:
										len[0] = Timed.getFireCount();
									case 2:
										myMeter.stopMeter();
										// Final consumption results with all
										// machines running
										meteredResults[1] = myMeter.getTotalConsumption();
									default:
									}
								}
							};
						}
					}
				}
			};
			for (PhysicalMachine pm : iaas.machines) {
				pm.subscribeStateChangeEvents(myListener);
			}
			myMeter.startMeter(meterFreq, true);
			Timed.simulateUntilLastEvent();
			Timed.resetTimed();
		}
		assertEquals(meteredResults[1] - meteredResults[0], meteredResults[2], 0.01, "The energy consumption should be increasing with the two new machine's consumption");
	}

	@Test
	@Timeout(value = 400, unit = TimeUnit.MILLISECONDS)
	public void simpleConsumptionMonitoring() {
		PhysicalMachine pm = dummyPMcreator();
		pm.turnon();
		Timed.simulateUntilLastEvent();
		final MonitorConsumption mon = new MonitorConsumption(pm, aSecond);
		Timed.simulateUntil(Timed.getFireCount() + 1000);
		assertEquals(0, mon.getSubDayProcessing(), 0.0001, "Should not report consumption yet");
		// The size of the consumption results in an over the day simulation
		ResourceConsumption rc = new ResourceConsumption(100000000, ResourceConsumption.unlimitedProcessing,
				pm.directConsumer, pm, new ConsumptionEventAssert() {
					@Override
					public void conComplete() {
						super.conComplete();
						mon.cancelMonitoring();
					}
				});
		rc.registerConsumption();
		Timed.simulateUntilLastEvent();
		assertTrue(mon.getSubDayProcessing() >= mon.getSubHourProcessing()
						&& mon.getSubHourProcessing() >= mon.getSubSecondProcessing(), "Should report consumption with day>hour>sec");
	}
}
