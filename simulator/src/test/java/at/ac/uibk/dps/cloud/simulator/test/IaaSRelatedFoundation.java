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
 *  (C) Copyright 2015, Vincenzo De Maio (vincenzo@dps.uibk.ac.at)
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package at.ac.uibk.dps.cloud.simulator.test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.MultiPMController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.*;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import org.apache.commons.lang3.function.Failable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IaaSRelatedFoundation extends VMRelatedFoundation {
	public final static int dummyPMCoreCount = 1;
	public final static double dummyPMPerCorePP = 1;
	public final static int vaSize = 100;
	public final static long dummyPMMemory = vaSize * 40;
	private final static HashMap<String, Integer> globalLatencyMapInternal = new HashMap<String, Integer>(10000);
	public final static Map<String, Integer> globalLatencyMap = Collections.unmodifiableMap(globalLatencyMapInternal);

	@BeforeAll
	public static void preloadIaaS() throws Exception {
		new IaaSRelatedFoundation().getNewServiceArray();
	}

	@BeforeEach
	public void resetLatencyMap() {
		globalLatencyMapInternal.clear();
	}

	public static String generateName(final String prefix, final int latency) {
		return generateNames(1, prefix, latency)[0];
	}

	public static String[] generateNames(final int count, final String prefix, final int latency) {
		final byte[] seed = new byte[4 * count];
		SeedSyncer.centralRnd.nextBytes(seed);
		final String[] names = new String[count];
		for (int i = 0; i < count; i++) {
			final int mult = i * 4;
			names[i] = prefix + seed[mult] + seed[mult + 1] + seed[mult + 2] + seed[mult + 3];
			globalLatencyMapInternal.put(names[i], latency);
		}
		return names;
	}

	public static PhysicalMachine[] dummyPMsCreator(final int machineCount, final int corecount, final double pcpp,
			final long memory) {
		final PhysicalMachine[] pms = new PhysicalMachine[machineCount];
		final String[] names = generateNames(machineCount, "M", 1);
		for (int i = 0; i < machineCount; i++) {
			pms[i] = new PhysicalMachine(corecount, pcpp, memory, dummyRepoWithName(false, names[i]), 1, 1,
					defaultHostTransitions);
		}
		return pms;

	}

	public static PhysicalMachine dummyPMcreator() {
		return dummyPMsCreator(1, dummyPMCoreCount, dummyPMPerCorePP, dummyPMMemory)[0];
	}

	public static Repository dummyRepoWithName(boolean withVa, String name) {
		final Repository repo = new Repository(vaSize * 400, name, 1, 1, 1, globalLatencyMapInternal,
				defaultStorageTransitions, defaultNetworkTransitions);
		if (withVa) {
			final VirtualAppliance va = new VirtualAppliance("VA", 2000, 0, false, vaSize / 5);
			Assertions.assertTrue(repo.registerObject(va), "Registration should succeed");
		}
		return repo;

	}

	public static Repository dummyRepoCreator(boolean withVA) {
		return dummyRepoWithName(withVA, generateName("R", 3));
	}

	public static IaaSService setupIaaS(Class<? extends Scheduler> vmsch,
			Class<? extends PhysicalMachineController> pmsch, final int hostCount, final int coreCount)
			throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException {
		final IaaSService basic = new IaaSService(vmsch, pmsch);
		basic.bulkHostRegistration(
				Arrays.asList(dummyPMsCreator(hostCount, coreCount, dummyPMPerCorePP, dummyPMMemory)));
		basic.registerRepository(dummyRepoCreator(true));
		assertTrue(basic.runningMachines.isEmpty(), "No machines should be running now");
		return basic;
	}

	public void fireVMat(final IaaSService iaas, long distance, final double processing, final int corecount) {
		DeferredEvent.deferAction(distance, () -> {
				final ResourceConstraints pmsize = iaas.machines.get(0).getCapacities();
				int instancecount = pmsize.getRequiredCPUs() >= corecount ? 1
						: (corecount / ((int) pmsize.getRequiredCPUs()))
								+ ((corecount % (int) pmsize.getRequiredCPUs()) == 0 ? 0 : 1);
				double requestedprocs = ((double) corecount) / instancecount;
				final ResourceConstraints vmsize = new UnalterableConstraintsPropagator(
						new AlterableResourceConstraints(requestedprocs, pmsize.getRequiredProcessingPower(), 512));
				final Repository repo = iaas.repositories.get(0);

				try {
					VirtualMachine[] vms = iaas.requestVM((VirtualAppliance) repo.contents().iterator().next(), vmsize,
							repo, instancecount);
					for (int i = 0; i < vms.length; i++) {
						vms[i].subscribeStateChange(new VirtualMachine.StateChange() {
							@Override
							public void stateChanged(final VirtualMachine vm, VirtualMachine.State oldState,
									VirtualMachine.State newState) {
								if (VirtualMachine.State.RUNNING.equals(newState)) {
									try {
										vm.newComputeTask(
												processing * vm.getResourceAllocation().allocated
														.getTotalProcessingPower(),
												ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert() {
													@Override
													public void conComplete() {
														super.conComplete();
														try {
															vm.destroy(false);
														} catch (VMManagementException e) {
															throw new IllegalStateException(e);
														}
													}
												});
									} catch (Exception e) {
										throw new IllegalStateException(e);
									}
								}
							}
						});
					}
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}

		});
	}

	public Stream<Class<? extends Scheduler>> allVMSchedulers() {
		return Stream.of(FirstFitScheduler.class, NonQueueingScheduler.class, SmallestFirstScheduler.class, RandomScheduler.class, RoundRobinScheduler.class);
	}

	public Stream<Class<? extends PhysicalMachineController>> allPMControllers() {
		return Stream.of(AlwaysOnMachines.class, SchedulingDependentMachines.class, MultiPMController.class);
	}

	public List<IaaSService> getNewServiceArray() {
		return allVMSchedulers().flatMap(
				vmScheduler -> allPMControllers().map(
						pmScheduler -> {
							try {
								return new IaaSService(vmScheduler, pmScheduler);
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						}
				)).toList();
	}
}
