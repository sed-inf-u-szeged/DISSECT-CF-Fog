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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.lang3.function.Failable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService.IaaSHandlingException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class IaaSServiceTest extends IaaSRelatedFoundation {
	List<IaaSService> services;

	@BeforeEach
	public void resetSim() {
		services = getNewServiceArray();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void repoRegistrationTest() {
		for (IaaSService iaas : services) {
			// Repository management
			assertTrue(iaas.repositories.isEmpty(),"Should not have any repositories before registering one");
			iaas.registerRepository(dummyRepoCreator(true));
			assertFalse(iaas.repositories.isEmpty(), "Should have got a repository registered");
			iaas.deregisterRepository(iaas.repositories.get(0));
			assertTrue(iaas.repositories.isEmpty(), "Should not have any repositories after deregistering the last");
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void faultyRegistrationTest() {
		ArrayList<Exception> exs = new ArrayList<>();
		for (IaaSService iaas : services) {
			try {
				iaas.deregisterHost(dummyPMcreator());
			} catch (Exception e) {
				exs.add(e);
			}
		}
		assertEquals(services.size(), exs.size(), "None of the faulty PM deregistrations should get through");

	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void capacityMaintenanceTest() throws IaaSHandlingException {
		for (IaaSService iaas : services) {
			final PhysicalMachine pm = dummyPMcreator();
			AlterableResourceConstraints beforeCapacities = new AlterableResourceConstraints(iaas.getCapacities()).singleAddCont(pm.getCapacities());
			iaas.registerHost(pm);
			ResourceConstraints midCapacities = iaas.getCapacities();
			assertEquals(0, midCapacities.compareTo(beforeCapacities), "After registration the iaas should have more capacities");
			beforeCapacities.subtract(pm.getCapacities());
			iaas.deregisterHost(pm);
			ResourceConstraints afterCapacities = iaas.getCapacities();
			assertEquals(0, afterCapacities.compareTo(beforeCapacities), "After deregistration the iaas should have the same capacities as before the registration took place");
		}
		Timed.simulateUntilLastEvent();
	}

	abstract static class Capchanger implements IaaSService.CapacityChangeEvent<PhysicalMachine> {
		public int fired = 0;
		public PhysicalMachine pm = dummyPMcreator();

		@Override
		public void capacityChanged(ResourceConstraints newCapacity, List<PhysicalMachine> alteredPMs) {
			doAssertion(newCapacity);
			fired++;
			assertTrue(fired < 2, "Should not receive events for the same capacity more than once");
		}

		protected abstract void doAssertion(ResourceConstraints newCapacity);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void capacityIncreaseSubscriptionTest() throws IaaSHandlingException {
		ArrayList<Capchanger> ccs = new ArrayList<>();
		for (int i = 0; i < services.size(); i++) {
			ccs.add(new Capchanger() {
				protected void doAssertion(ResourceConstraints newCapacity) {
					assertEquals(0, pm.getCapacities().compareTo(newCapacity), "Should receive capacity update with the just registered PM's size");
				}
			});
		}

		int i = 0;
		// Addition test
		for (IaaSService iaas : services) {
			// Proper PM registration
			Capchanger cs = ccs.get(i++);
			iaas.subscribeToCapacityChanges(cs);
			iaas.registerHost(cs.pm);
			assertTrue(iaas.toString().contains(cs.pm.toString()), "The registered PM should be listed in the IaaS description");
			iaas.unsubscribeFromCapacityChanges(cs);
			iaas.deregisterHost(cs.pm);
			assertEquals(1, cs.fired, "Capacity incresed event should have arrived once");
		}
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void capacityDecreaseSubscriptionTest() throws IaaSHandlingException {
		ArrayList<Capchanger> ccs = new ArrayList<>();
		for (int i = 0; i < services.size(); i++) {
			ccs.add(new Capchanger() {
				protected void doAssertion(ResourceConstraints newCapacity) {
					assertEquals(0, newCapacity.getRequiredCPUs(), "Should receive capacity update with no further capacities remaining");
				}
			});
		}

		int i = 0;
		// removal test
		for (IaaSService iaas : services) {
			// Proper PM registration
			Capchanger cs = ccs.get(i++);
			iaas.registerHost(cs.pm);
			iaas.subscribeToCapacityChanges(cs);
			iaas.deregisterHost(cs.pm);
			iaas.unsubscribeFromCapacityChanges(cs);
			assertFalse(iaas.toString().contains(cs.pm.toString()), "The registered PM should not be listed in the IaaS description");
			assertEquals(1, cs.fired, "Capacity incresed event should have arrived once");
		}
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void runningCapacityMaintananceTest() {
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			assertEquals(0, iaas.getRunningCapacities().getRequiredCPUs(), "A newly created IaaS should not have any running capacities");
		}
		Timed.simulateUntilLastEvent();
		int runningPMCount = 0;
		int alwaysRunningSchCount = 0;
		for (IaaSService iaas : services) {
			runningPMCount += iaas.runningMachines.size();
			if (iaas.pmcontroller instanceof AlwaysOnMachines) {
				alwaysRunningSchCount++;
				assertEquals(dummyPMCoreCount, (long) iaas.getRunningCapacities().getRequiredCPUs(), "This iaas should have running PMs already as it switches them on immediately after registration and keeps them running");
			} else {
				assertEquals(0,
						(long) iaas.getRunningCapacities().getRequiredCPUs(), "This iaas should not have running PMs yet as it did not receive VMs so far");
			}
		}
		assertEquals(alwaysRunningSchCount,
				runningPMCount, "Only IaaSs with alwaysrunning pms should have running capacities");
	}

	private void constructMinimalIaaS() {
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			iaas.registerRepository(dummyRepoCreator(true));
		}
	}

	private Stream<VirtualMachine> requestVMStream() {
		return Failable.stream(services).map(iaas ->
				iaas.requestVM((VirtualAppliance) iaas.repositories.get(0).contents().iterator().next(),
						iaas.getCapacities(), iaas.repositories.get(0), 1)[0]
		).stream();
	}

	private List<VirtualMachine> requestVMs() {
		return requestVMStream().toList();
	}

		@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void vmRequestTest() {
		constructMinimalIaaS();
		var vms = requestVMs();
		Timed.simulateUntilLastEvent();
		var runningvms = vms.stream().filter(vm -> vm.getState() == State.RUNNING).count();
		assertEquals(services.size(), runningvms, "All IaaSs should have a running VM");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void vmTerminateTest() throws VMManagementException {
		constructMinimalIaaS();
		var vms = requestVMs();
		Timed.simulateUntilLastEvent();
		int i = 0;
		for (IaaSService iaas : services) {
			iaas.terminateVM(vms.get(i++), false);
		}
		Timed.simulateUntilLastEvent();
		int runningMachineCount = 0;
		int aomCount = 0;
		for (IaaSService iaas : services) {
			runningMachineCount += iaas.runningMachines.size();
			aomCount += iaas.pmcontroller instanceof AlwaysOnMachines ? 1 : 0;
		}
		assertEquals(aomCount,
				runningMachineCount, "Only the iaas's that use alwaysOnMachines policy should have actual running PMs");
		int shutDownVMs = 0;
		for (VirtualMachine vm : vms) {
			shutDownVMs += vm.getState() == VirtualMachine.State.SHUTDOWN ? 1 : 0;
		}
		assertEquals(vms.size(), shutDownVMs, "All VMs should be destroyed by now");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void notRunVMTerminationTest() {
		ArrayList<Exception> exs = new ArrayList<>();
		constructMinimalIaaS();
		for (IaaSService iaas : services) {
			try {
				iaas.terminateVM(new VirtualMachine(new VirtualAppliance("FAULTY", 10, 10)), true);
			} catch (VMManagementException e) {
				exs.add(e);
			}
		}
		assertEquals(services.size(), exs.size(), "Not all IaaSs rejected the VM termination request");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void crossVMFaultyTerminationTest() {
		constructMinimalIaaS();
		List<VirtualMachine> vms = requestVMs();
		Timed.simulateUntilLastEvent();
		int i = services.size() - 1;
		ArrayList<Exception> exs = new ArrayList<>();
		for (IaaSService iaas : services) {
			try {
				iaas.terminateVM(vms.get(i--), false);
			} catch (VMManagementException e) {
				exs.add(e);
			}
		}
		assertEquals((services.size() % 2 == 1) ? services.size() - 1 : services.size(), exs.size(), "Not all IaaSs rejected the VM termination request");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void beforeRegistrationVMCreationTest() {
		final int slen = services.size();
		final ResourceConstraints pmcap = dummyPMcreator().getCapacities();
		final Repository repo = dummyRepoCreator(true);
		final VirtualAppliance va = new VirtualAppliance("VERYFAULTY", 1, 0);
		int excounter = 0;
		for (IaaSService service : services) {
			try {
				service.requestVM(va, pmcap, repo, 1);
			} catch (VMManagementException e) {
				excounter++;
			}
		}
		assertEquals(slen, excounter, "Not all IaaSs rejected the VM creation request");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void vmListingTest() {
		constructMinimalIaaS();
		List<VirtualMachine> vms = requestVMs();
		Timed.simulateUntilLastEvent();
		ArrayList<VirtualMachine> reportedvms = new ArrayList<>();
		for (IaaSService iaas : services) {
			reportedvms.addAll(iaas.listVMs());
		}
		assertTrue(vms.containsAll(reportedvms) && reportedvms.containsAll(vms), "All running VMs should be reported by IaaSs");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void vmQueuedListingTest() {
		constructMinimalIaaS();
		ArrayList<VirtualMachine> vms = new ArrayList<>(Stream.concat(requestVMStream(), requestVMStream()).toList()); // second is unfulfillable vm stream
		Timed.simulateUntilLastEvent();
		ArrayList<VirtualMachine> reportedvms = new ArrayList<>(vms.size());
		for (IaaSService iaas : services) {
			reportedvms.addAll(iaas.listVMs());
		}
		Iterator<VirtualMachine> vmit = vms.iterator();
		int queuedCounter = 0;
		int runningCounter = 0;
		while (vmit.hasNext()) {
			switch (vmit.next().getState()) {
			case NONSERVABLE:
				// Nonservable VMs will not have a chance to be queued
				vmit.remove();
				break;
			case DESTROYED:
				queuedCounter++;
				break;
			case RUNNING:
				runningCounter++;
			default:
			}
		}
		assertTrue(vms.containsAll(reportedvms) && reportedvms.containsAll(vms), "Even queued VMs should be reported by IaaSs");
		assertEquals(vms.size() - runningCounter, queuedCounter, "The count of queued and running VMs should match the num of VMs under or over scheduling");
	}

	@Disabled
	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void deregisterHostWithMigration() throws VMManagementException, IaaSHandlingException {
		constructMinimalIaaS();
		final ArrayList<Integer> markers = new ArrayList<>();
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			Repository repo = iaas.repositories.get(0);
			iaas.requestVM((VirtualAppliance) repo.contents().iterator().next(), iaas.machines.get(0).getCapacities(),
					repo, 1)[0].subscribeStateChange(new VirtualMachine.StateChange() {
						@Override
						public void stateChanged(VirtualMachine vm, State oldState, State newState) {
							if (newState.equals(VirtualMachine.State.RUNNING)) {
								markers.add(vm.hashCode());
							}
						}
					});
		}
		Timed.simulateUntilLastEvent();
		assertEquals(services.size(), markers.size(), "By now all VMs should have been started");
		for (IaaSService iaas : services) {
			iaas.deregisterHost(iaas.runningMachines.get(0));
		}
		Timed.simulateUntilLastEvent();
		assertEquals(services.size() * 2, markers.size(), "By now all VMs should have been resumed");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void multiVMRequest() throws VMManagementException {
		constructMinimalIaaS();
		final int maxVMs = 16;
		final int servNum = services.size();
		final Repository[] repos = new Repository[servNum];
		final ResourceConstraints[] caps = new ResourceConstraints[servNum];
		final VirtualAppliance[] vas = new VirtualAppliance[servNum];
		for (int i = 0; i < servNum; i++) {
			final IaaSService iaas = services.get(i);
			repos[i] = iaas.repositories.get(0);
			caps[i] = iaas.machines.get(0).getCapacities();
			vas[i] = (VirtualAppliance) repos[i].contents().iterator().next();
		}

		final VirtualMachine[] vms = new VirtualMachine[maxVMs * servNum];
		for (int vmnum = 2; vmnum <= maxVMs; vmnum *= 2) {
			for (int i = 0; i < servNum; i++) {
				AlterableResourceConstraints cap = new AlterableResourceConstraints(caps[i]);
				cap.multiply(1f / vmnum / 2);
				VirtualMachine[] lvms = services.get(i).requestVM(vas[i], cap, repos[i], vmnum);
				System.arraycopy(lvms, 0, vms, i * vmnum, vmnum);
			}
			Timed.simulateUntilLastEvent();
			for (int i = 0; i < vmnum * servNum; i++) {
				vms[i].destroy(false);
			}
			Timed.simulateUntilLastEvent();
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void deQueueTest() throws VMManagementException {
		constructMinimalIaaS();
		for (IaaSService s : services) {
			Repository r = s.repositories.get(0);
			VirtualMachine vm = s.requestVM((VirtualAppliance) r.contents().iterator().next(), s.getCapacities(), r,
					1)[0];
			Timed.simulateUntilLastEvent();
			assertEquals(VirtualMachine.State.RUNNING, vm.getState(), "The first VM should be running");
			AlterableResourceConstraints caps = new AlterableResourceConstraints(s.getCapacities());
			caps.multiply(0.4);
			VirtualMachine[] vmsToQueue = s.requestVM((VirtualAppliance) r.contents().iterator().next(),
					new UnalterableConstraintsPropagator(caps), r, 2);
			VirtualMachine.State[] states = new VirtualMachine.State[] { vmsToQueue[0].getState(),
					vmsToQueue[1].getState() };
			for (VirtualMachine.State st : states) {
				assertTrue(VirtualMachine.preScheduleState.contains(st), "The VMs should be queueing or nonservable (in case of non queueing schedulers)");
			}
			if (states[0].equals(VirtualMachine.State.DESTROYED)) {
				// A queueing scheduler is used
				s.terminateVM(vmsToQueue[1], false);
				states = new VirtualMachine.State[] { vmsToQueue[0].getState(), vmsToQueue[1].getState() };
			}
			for (VirtualMachine.State st : states) {
				assertEquals(VirtualMachine.State.NONSERVABLE, st, "The VMs should be marked as nonservable by all schedulers this time");
			}
			s.terminateVM(vm, true);
			Timed.simulateUntilLastEvent();
			vm.destroy(false);
			Timed.simulateUntilLastEvent();
			states = new VirtualMachine.State[] { vm.getState(), vmsToQueue[0].getState(), vmsToQueue[1].getState() };
			for (VirtualMachine.State st : states) {
				assertTrue(VirtualMachine.preScheduleState.contains(st), "None of the VMs should be running by this time");
			}
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void subClassingTest() throws IllegalArgumentException, SecurityException, InstantiationException,
			IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class) {
			// Anonymous subclass. with no content
		};
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void registeringRunningTest() {
		for (IaaSService s : services) {
			PhysicalMachine pm = dummyPMcreator();
			pm.turnon();
			Timed.simulateUntilLastEvent();
			s.registerRepository(dummyRepoCreator(true));
			s.registerHost(pm);
		}
		List<VirtualMachine> vms = requestVMs();
		Timed.simulateUntilLastEvent();
		for (int i = 0; i < vms.size(); i++) {
			IaaSService s = services.get(i);
			assertEquals(VirtualMachine.State.RUNNING, vms.get(i).getState(), "All VMs should be running but they were not on PMC: " + s.pmcontroller.getClass().getName()
					+ " VMS: " + s.sched.getClass().getName());
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void maxRequest() throws Exception {
		IaaSService iaas = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, 3, 2);
		Repository repo = iaas.repositories.get(0);
		VirtualAppliance va = (VirtualAppliance) repo.contents().iterator().next();
		double maxCOrig = Double.MAX_VALUE;
		double maxPOrig = Double.MAX_VALUE;
		long maxMOrig = Long.MAX_VALUE;
		for (int i = 0; i < 10; i++) {
			double maxC = 0;
			double maxP = 0;
			long maxM = 0;
			for (PhysicalMachine pm : iaas.machines) {
				maxC = Math.max(pm.freeCapacities.getRequiredCPUs(), maxC);
				maxM = Math.max(pm.freeCapacities.getRequiredMemory(), maxM);
				maxP = Math.max(pm.freeCapacities.getRequiredProcessingPower(), maxP);
			}
			assertTrue(maxC <= maxCOrig, "MaxC");
			maxCOrig = maxC;
			assertTrue(maxP <= maxPOrig, "MaxP");
			maxPOrig = maxP;
			assertTrue(maxM <= maxMOrig, "MaxM");
			maxMOrig = maxM;

			ResourceConstraints rc = new ConstantConstraints(maxC / 10, maxP / 10, maxM / 10);
			iaas.requestVM(va, rc, repo, 1);
			Timed.simulateUntilLastEvent();
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void dropARunningPM() throws Exception {
		int pmcount = 3;
		IaaSService iaas = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, pmcount, 1);
		Timed.simulateUntilLastEvent();
		assertEquals(pmcount, iaas.runningMachines.size(), "Should have all machines running");
		iaas.deregisterHost(iaas.machines.get(0));
		Timed.simulateUntilLastEvent();
		assertEquals(pmcount - 1, iaas.runningMachines.size(), "Should have one less machine running");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void heterogenousPMCapacityReport() throws Exception {
		IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
		assertEquals(0,	iaas.getCapacities().getRequiredProcessingPower(), 0.00001, "Should not report any processing power if there are no PMs");
		PhysicalMachine small = dummyPMcreator();
		ResourceConstraints sRC = small.getCapacities();
		iaas.registerHost(small);
		assertEquals(sRC.getRequiredProcessingPower(), iaas.getCapacities().getRequiredProcessingPower(), 0.00001, "Should report the small PM's processing power if it is the only one");
		PhysicalMachine big = dummyPMsCreator(1, (int) sRC.getRequiredCPUs(), sRC.getRequiredProcessingPower() * 2,
				sRC.getRequiredMemory())[0];
		iaas.registerHost(big);
		assertEquals(big.getCapacities().getRequiredProcessingPower(), iaas.getCapacities().getRequiredProcessingPower(),
				0.00001, "Should report the big PM's processing power if the IaaS has more than one type of PM");
		iaas.deregisterHost(big);
		assertEquals(sRC.getRequiredProcessingPower(), iaas.getCapacities().getRequiredProcessingPower(), 0.00001, "Should report the small PM's processing power if it is the only one");
		iaas.deregisterHost(small);
		Timed.simulateUntilLastEvent();
	}
}
