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

import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.StateChangeException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

public class ParallelUsageTest extends PMRelatedFoundation {
	public final static int taskLen = 1000; // in seconds

	@BeforeEach
	public void resetSeed() {
		SeedSyncer.resetCentral();
	}

	private ResourceConsumption addCons(VirtualMachine vm, long tL, boolean assertonFail)
			throws NetworkException {
		tL += taskLen / 2 - SeedSyncer.centralRnd.nextInt(taskLen);
		return vm.newComputeTask(tL * aSecond, ResourceConsumption.unlimitedProcessing,
				new ConsumptionEventAssert(assertonFail));
	}

	private IaaSService createMiniCloud(String cloudName, VirtualAppliance initialVA, long repoNBW, long repoDBW,
			int lat, double cores, long diskNBW, long diskDBW) throws Exception {
		IaaSService iaas = new IaaSService(FirstFitScheduler.class, SchedulingDependentMachines.class);
		HashMap<String, Integer> lmap = new HashMap<>();
		String repoID = cloudName + "-Repo", machineID = cloudName + "-Machine";
		lmap.put(repoID, lat);
		lmap.put(machineID, lat);
		Repository repo = new Repository(6000000000000L, repoID, repoNBW, repoNBW, repoDBW, lmap,
				defaultStorageTransitions, defaultNetworkTransitions);
		Repository disk = new Repository(6000000000000L, machineID, diskNBW, diskNBW, repoDBW, lmap,
				defaultStorageTransitions, defaultNetworkTransitions);
		repo.registerObject(initialVA);
		iaas.registerRepository(repo);
		PhysicalMachine pm = new PhysicalMachine(cores, 1.0, 128000000000L, disk, 89000, 29000, defaultHostTransitions);
		iaas.registerHost(pm);
		return iaas;
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void taskParallelismTest() throws Exception {
		int mxvms = 3;
		VirtualAppliance va = new VirtualAppliance("a", 1000, 0);
		IaaSService iaas = createMiniCloud("TestCloud", va, 250000L, 100000L, 11, 48.0, 125000L, 50000L);
		Repository repo = iaas.repositories.get(0);
		final Vector<ResourceConsumption> cons = new Vector<>();
		final long offset = Timed.getFireCount();
		final VirtualMachine[] vms = iaas.requestVM(va,
				new UnalterableConstraintsPropagator(new AlterableResourceConstraints(1, 1, 512000000)), repo, mxvms);
		final long[] expectedRunningTimes = { 444062, 444062, 444062 };
		for (int i = 0; i < vms.length; i++) {
			final int ireplica = i;
			vms[i].subscribeStateChange(new VirtualMachine.StateChange() {
				@Override
				public void stateChanged(VirtualMachine vm, VirtualMachine.State oldState,
						VirtualMachine.State newState) {
					if (newState.equals(VirtualMachine.State.RUNNING)) {
						assertEquals(expectedRunningTimes[ireplica] + offset,
								Timed.getFireCount(), "VM started at the wrong time");
						try {
							if (ireplica == 0) {
								cons.add(addCons(vms[ireplica], taskLen, true));
							}
						} catch (Exception e) {
							throw new IllegalStateException(e);
						}
					}
				}
			});
		}

		while (cons.isEmpty()) {
			Timed.jumpTime(Long.MAX_VALUE);
			Timed.fire();
		}

		ResourceConsumption toCancel = null;
		for (int i = 0; i < 8; i++) {
			long relativejump = 81;
			while (relativejump > 0) {
				relativejump = Timed.jumpTime(relativejump - 1);
				Timed.fire();
			}
			// Make sure the last one should assert on failure!
			cons.add(toCancel = addCons(vms[SeedSyncer.centralRnd.nextInt(mxvms)], taskLen, i < 7));

		}

		while (ConsumptionEventAssert.hits.isEmpty()) {
			Timed.jumpTime(Long.MAX_VALUE);
			Timed.fire();
		}
		toCancel.cancel();
		Timed.simulateUntilLastEvent();
		long[] result = new long[ConsumptionEventAssert.hits.size()];
		int index = 0;
		for (Long r : ConsumptionEventAssert.hits) {
			result[index++] = r - offset;
		}
		assertArrayEquals(new long[] { 3054090, 3055041, 3625610, 3933407, 4598522, 4667320, 4937077, 5054225 }, result, "Incorrect computing task completion times");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void cloudParallelismTest() throws Exception {
		final VirtualAppliance[] vas = new VirtualAppliance[2];
		vas[0] = new VirtualAppliance("testVA1", 3000, 0, false, 100000000);
		vas[1] = new VirtualAppliance("testVA2", 3000, 0, false, 100000000);
		final IaaSService[] iaass = new IaaSService[2];
		iaass[0] = createMiniCloud("TestCloud1", vas[0], 250000L, 100000L, 11, 48.0, 125000L, 50000L);
		iaass[1] = createMiniCloud("TestCloud2", vas[1], 1250000L, 250000L, 5, 64.0, 250000L, 50000L);
		ResourceConstraints rc = new UnalterableConstraintsPropagator(
				new AlterableResourceConstraints(1, 1, 1000000000));
		final long offset = Timed.getFireCount();
		final long[] itr = { 89000, 89000 };
		final long[] str = { 89811, 89405 };
		final long[] run = { 92811, 92405 };
		final long[] shd = { 392811, 392405 };
		final long[] dst = { 392811, 392405 };
		for (int i = 0; i < 2; i++) {
			final int isaved = i;
			Repository repoToUse = iaass[i].repositories.get(0);
			final VirtualMachine vm = iaass[i].requestVM(vas[i], rc, repoToUse, 1)[0];
			vm.subscribeStateChange((VirtualMachine vmInt, State oldState, State newState) -> {
				switch (newState) {
				case INITIAL_TR:
					assertEquals(itr[isaved] + offset, Timed.getFireCount(), "INITIAL_TR not on time");
					break;
				case STARTUP:
					assertEquals(str[isaved] + offset, Timed.getFireCount(), "STARTUP not on time");
					break;
				case RUNNING:
					assertEquals(run[isaved] + offset, Timed.getFireCount(), "RUNNING not on time");
					break;
				case SHUTDOWN:
					assertEquals(shd[isaved] + offset, Timed.getFireCount(), "SHUTDOWN not on time");
					break;
				case DESTROYED:
					assertEquals(dst[isaved] + offset, Timed.getFireCount(), "DESTROYED not on time");
					break;
				default:
					fail("Unexpected VM state");
				}
				if (newState.equals(VirtualMachine.State.RUNNING)) {
					try {
						vm.newComputeTask(300 * aSecond, ResourceConsumption.unlimitedProcessing,
								new ResourceConsumption.ConsumptionEvent() {
									@Override
									public void conComplete() {
										try {
											vm.destroy(false);
										} catch (VMManagementException e) {
											e.printStackTrace();
										}
									}

									@Override
									public void conCancelled(ResourceConsumption problematic) {
									}
								});
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
				}
			});
		}
		Timed.simulateUntilLastEvent();
	}
}
