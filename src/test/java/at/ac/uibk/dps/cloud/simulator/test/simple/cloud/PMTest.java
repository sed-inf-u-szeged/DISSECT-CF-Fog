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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class PMTest extends IaaSRelatedFoundation {
	final static int reqcores = 2, reqProcessing = 3, reqmem = 1000, reqond = 2 * (int) aSecond,
			reqoffd = (int) aSecond;
	final static ResourceConstraints smallConstraints = new ConstantConstraints(reqcores / 2, reqProcessing,
			reqmem / 2);
	final static ResourceConstraints overCPUConstraints = new ConstantConstraints(reqcores * 2, reqProcessing, reqmem);
	final static ResourceConstraints overMemoryConstraints = new ConstantConstraints(reqcores, reqProcessing,
			reqmem * 2);
	final static ResourceConstraints overProcessingConstraints = new ConstantConstraints(reqcores, reqProcessing * 2,
			reqmem);
	final static String pmid = "TestingPM";
	PhysicalMachine pm;
	Repository reqDisk;
	HashMap<String, Integer> latmap = new HashMap<>();

	@BeforeEach
	public void initializeTests() {
		latmap.put(pmid, 1);
		reqDisk = new Repository(123, pmid, 456, 789, 12, new HashMap<>(), defaultStorageTransitions,
				defaultNetworkTransitions);
		pm = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk, reqond, reqoffd, defaultHostTransitions);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void constructionTest() {
		assertEquals(reqcores, (int) pm.getCapacities().getRequiredCPUs(), "Cores mismatch");
		assertEquals(reqmem, (int) pm.getCapacities().getRequiredMemory(), "Memory mismatch");
		assertEquals(reqProcessing,
				(int) pm.getCapacities().getRequiredProcessingPower(), "Per core processing power mismatch");
		assertEquals(reqcores * reqProcessing,
				(int) pm.getPerTickProcessingPower(), "Total processing power mismatch");
		assertEquals(reqond, pm.getCurrentOnOffDelay(), "On delay mismatch");
		assertEquals(0, pm.freeCapacities.compareTo(pm.getCapacities()), "Free capacity mismatch");
		assertTrue(pm.toString().contains(pmid), "Machine's id is not in the machine's toString");
		assertEquals(PhysicalMachine.State.OFF, pm.getState(), "Machine's state is not initial");
		assertEquals(0, pm.getCompletedVMs(), "Machine should not have any completed VMs");
		assertFalse(pm.isHostingVMs(), "Machine should not host any VMs after construction");
		assertTrue(pm.isHostableRequest(smallConstraints), "The PM should report this request as hostable");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void regularSwitchOnOffTest() throws VMManagementException, NetworkException {
		final ArrayList<String> list = new ArrayList<>();
		pm.subscribeStateChangeEvents(
				(PhysicalMachine pmInt, State oldState, State newState) -> list.add(newState.toString()));
		assertFalse(pm.isRunning(), "The PM should not be running now");
		assertEquals(0, list.size(), "So far we should not have any events");
		pm.turnon();
		assertEquals(1, list.size(), "We should have been notified about the switching on event");
		Timed.simulateUntilLastEvent();
		assertEquals(2, list.size(), "We should have been notified about the running event");
		assertTrue(pm.isRunning(), "The PM should be running now");
		assertTrue(pm.switchoff(null));
		assertEquals(3, list.size(), "We should have been notified about the switching off event");
		Timed.simulateUntilLastEvent();
		assertEquals(4, list.size(), "We should have been notified about the off event");
		assertFalse(pm.isRunning(), "The PM should not be running now");
	}

	private PhysicalMachine.StateChangeListener getFailingListener(final String message) {
		return (PhysicalMachine pm, State oldState, State newState) -> fail(message);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void irregularSwithchOnOffTest() throws VMManagementException, NetworkException {
		long before = Timed.getFireCount();
		pm.turnon(); // Turnon request that will be interrupted
		Timed.simulateUntil(Timed.getFireCount() + pm.getCurrentOnOffDelay() - 5);
		assertFalse(pm.isRunning(), "The PM should not be running now");
		assertTrue(pm.switchoff(null)); // while turning on
		Timed.simulateUntil(Timed.getFireCount() + pm.getCurrentOnOffDelay() - 5);
		pm.turnon(); // Turnon request that will succeed
		assertFalse(pm.isRunning(), "The PM should not be running now");
		Timed.simulateUntil(Timed.getFireCount() + pm.getCurrentOnOffDelay() - 5);
		assertFalse(pm.isRunning(), "The PM should not be running now");
		PhysicalMachine.StateChangeListener sl = getFailingListener(
				"We should not receive any state change events because of a repeated turnon!");
		pm.subscribeStateChangeEvents(sl);
		pm.turnon(); // While turning on
		pm.unsubscribeStateChangeEvents(sl);
		Timed.simulateUntilLastEvent();
		assertTrue(pm.isRunning(), "The PM should be running now");
		long after = Timed.getFireCount();
		assertEquals(2 * reqond + reqoffd,
				after - before - 1, "Off and on delays are not executed to their full extent");
		pm.subscribeStateChangeEvents(sl);
		pm.turnon(); // After already running
		pm.unsubscribeStateChangeEvents(sl);
		assertTrue(pm.switchoff(null)); // Swithcoff request that will
												// succeed
		Timed.simulateUntil(Timed.getFireCount() + pm.getCurrentOnOffDelay() - 5);
		sl = getFailingListener("We should not receive any state change events because of a repeated switchoff!");
		assertTrue(pm.switchoff(null)); // Switchoff request while
												// swithcing off
		pm.unsubscribeStateChangeEvents(sl);
		Timed.simulateUntilLastEvent();
		assertEquals(PhysicalMachine.State.OFF, pm.getState(), "Machine could not swithced off properly");
		pm.subscribeStateChangeEvents(sl);
		assertTrue(pm.switchoff(null)); // Switchoff request to an
												// already switched off machine
		pm.unsubscribeStateChangeEvents(sl);
	}

	private void registerVA(PhysicalMachine pm) {
		VirtualAppliance va = new VirtualAppliance("VAID", 1500, 0, false, pm.localDisk.getMaxStorageCapacity() / 10);
		pm.localDisk.registerObject(va);
	}

	private void preparePM() {
		registerVA(pm);
		pm.turnon();
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void simpleTwoPhasedSmallVMRequest() throws VMManagementException, NetworkException {
		preparePM();
		ResourceAllocation ra = pm.allocateResources(smallConstraints, true, PhysicalMachine.defaultAllocLen);
		assertTrue(ra.toString().contains(smallConstraints.toString()), "Resource allocation does not have a proper tostring");
		pm.deployVM(newVMfromLocalDisk(null), ra, pm.localDisk);
		Timed.simulateUntilLastEvent();
		assertTrue(pm.isHostingVMs(), "PM should report that it hosts VMs");
		pm.publicVms.iterator().next().destroy(false);
		Timed.simulateUntilLastEvent();
		assertFalse(pm.isHostingVMs(), "PM should report that it does not host VMs");
	}

	private VirtualMachine newVMfromLocalDisk(VirtualAppliance va) {
		return new VirtualMachine(va == null ? (VirtualAppliance) pm.localDisk.contents().iterator().next() : va);
	}

	private VirtualMachine[] requestVMs(ResourceConstraints rc, VirtualAppliance va, int count)
			throws VMManagementException, NetworkException {
		return pm.requestVM(va == null ? (VirtualAppliance) pm.localDisk.contents().iterator().next() : va, rc,
				pm.localDisk, count);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void simpleDirectVMrequest() throws VMManagementException, NetworkException {
		preparePM();
		VirtualMachine[] vms = requestVMs(smallConstraints, null, 2);
		Timed.simulateUntilLastEvent();
		for (VirtualMachine vm : vms) {
			assertEquals(VirtualMachine.State.RUNNING, vm.getState(), "All VMs should be running");
			vm.destroy(false);
		}
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failingDirectVMRequest() throws VMManagementException, NetworkException {
		preparePM();
		AlterableResourceConstraints arc = new AlterableResourceConstraints(smallConstraints);
		arc.multiply(2);
		VirtualMachine[] vms = requestVMs(arc, null, 2);
		assertArrayEquals(new VirtualMachine[] { null, null }, vms, "If the PM cannot fulfill all VM requests then it should not accept a single one");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void swithchoffWhileRunningVMs() throws VMManagementException, NetworkException {
		preparePM();
		AlterableResourceConstraints arc = new AlterableResourceConstraints(smallConstraints);
		arc.multiply(0.5);
		VirtualMachine[] vms = requestVMs(arc, null, 4);
		Timed.simulateUntilLastEvent();
		assertFalse(pm.switchoff(null), "Should not be able to terminate a PM while VMs are running on it");
		for (VirtualMachine vm : vms) {
			vm.destroy(false);
		}
		Timed.simulateUntilLastEvent();
		assertTrue(pm.switchoff(null));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failingVMRequestBecauseofMalformedVA() {
		assertThrows(VMManagementException.class, () -> {
			preparePM();
			requestVMs(smallConstraints, new VirtualAppliance("MAKEMEFAILED", 10, 10), 2);
			fail("We should not reach this point as the VMs requested were using an unknown VA for the PM");
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failingVMRequestBecauseofExpiredRA() {
		assertThrows(VMManagementException.class, () -> {
			preparePM();
			ResourceAllocation ra = pm.allocateResources(smallConstraints, true, PhysicalMachine.defaultAllocLen);
			Timed.simulateUntilLastEvent();
			pm.deployVM(newVMfromLocalDisk(null), ra, pm.localDisk);
			fail("The PM should not accept a VM request with an already expired allocation");
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failingVMRequestBecauseofNotRunningPM() {
		assertThrows(VMManagementException.class, () -> {
			registerVA(pm);
			requestVMs(smallConstraints, null, 2);
			fail("The PM should not accept a VM request while it is not running!");
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failingSwitchoffBecauseofPendingAllocation() throws VMManagementException, NetworkException {
		preparePM();
		ResourceAllocation ra = pm.allocateResources(smallConstraints, true, PhysicalMachine.defaultAllocLen);
		assertFalse(pm.switchoff(null), "PM should not be able to switch off while there are pending resource allocations for it");
		pm.cancelAllocation(ra);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void cancelNotownAllocation() throws VMManagementException, NetworkException {
		preparePM();
		PhysicalMachine other = dummyPMcreator();
		other.turnon();
		registerVA(other);
		Timed.simulateUntilLastEvent();
		ResourceAllocation ra = other.allocateResources(other.getCapacities(), true, PhysicalMachine.defaultAllocLen);
		assertFalse(pm.cancelAllocation(ra), "Should not be possible to cancel an allocation of another machine");
		VirtualMachine dpl = new VirtualMachine((VirtualAppliance) other.localDisk.contents().iterator().next());
		other.deployVM(dpl, ra, other.localDisk);
		Timed.simulateUntilLastEvent();
		assertEquals(VirtualMachine.State.RUNNING,
				dpl.getState(), "The VM on the other PM is not in the expected state");
		dpl.destroy(false);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void checkIncreasingFreeCapacityNotifications() throws VMManagementException, NetworkException {
		preparePM();
		ResourceConstraints beforeCreation = new AlterableResourceConstraints(pm.freeCapacities);
		VirtualMachine[] vm = requestVMs(smallConstraints, null, 2);
		ResourceConstraints afterRequest = new AlterableResourceConstraints(pm.freeCapacities);
		assertEquals(0,afterRequest.getRequiredCPUs(), "Unallocated resouce capacity maintanance failure");
		Timed.simulateUntilLastEvent();
		assertTrue(pm.isHostingVMs(), "PM should report that it hosts VMs");
		assertEquals(0, afterRequest.compareTo(pm.freeCapacities), "Unallocated resource capacity should not change after request");
		final ArrayList<ResourceConstraints> eventReceived = new ArrayList<>();
		PhysicalMachine.CapacityChangeEvent<ResourceConstraints> ev = (ResourceConstraints newCapacity,
				List<ResourceConstraints> newlyFreeCapacity) -> eventReceived.add(newCapacity);
		pm.subscribeToIncreasingFreeapacityChanges(ev);
		vm[0].destroy(false);
		Timed.simulateUntilLastEvent();
		assertTrue(pm.isHostingVMs(), "PM should still report that it hosts VMs");
		assertEquals(1, eventReceived.size(), "Mismach in the expected number of free capacity increase events");
		AlterableResourceConstraints upd = new AlterableResourceConstraints(eventReceived.get(0));
		upd.multiply(2);
		assertEquals(0,
				beforeCreation.compareTo(upd),"Mismach in the expected free capacity between the event and query");
		pm.unsubscribeFromIncreasingFreeCapacityChanges(ev);
		vm[1].destroy(false);
		Timed.simulateUntilLastEvent();
		assertEquals(1, eventReceived.size(), "After unsubscription we should not receive more events");
		assertFalse(pm.isHostingVMs(), "PM should not report any hosted VMs by now");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void checkOnOffdelayBehavior() throws VMManagementException, NetworkException {
		assertEquals(reqond, pm.getCurrentOnOffDelay(), "On delay misreported");
		pm.turnon();
		assertEquals(reqond, pm.getCurrentOnOffDelay(), "Still the on delay should be reported");
		final int timeDiff = 5;
		Timed.simulateUntil(Timed.getFireCount() + timeDiff);
		assertEquals(reqond - timeDiff,
				pm.getCurrentOnOffDelay(), "We should have the delay reduced a little bit by now");
		Timed.simulateUntilLastEvent();
		assertEquals(reqoffd, pm.getCurrentOnOffDelay(), "Off delay misreported");
		pm.switchoff(null);
		assertEquals(reqoffd, pm.getCurrentOnOffDelay(), "Still the off delay should be reported");
		Timed.simulateUntil(Timed.getFireCount() + timeDiff);
		assertEquals(reqoffd - timeDiff,
				pm.getCurrentOnOffDelay(), "We should have the delay reduced a little bit by now");
		Timed.simulateUntilLastEvent();
		assertEquals(reqond, pm.getCurrentOnOffDelay(), "On delay misreported after on-off cycle");
		long switchOnRequestTime = Timed.getFireCount();
		pm.turnon();
		Timed.simulateUntil(switchOnRequestTime + timeDiff);
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		long completeSwithcOffTime = Timed.getFireCount();
		assertEquals(reqoffd + reqond,
				completeSwithcOffTime - switchOnRequestTime - 1, "Delays should add up together");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void checkConsumptionAcceptance() throws VMManagementException, NetworkException {
		preparePM();
		VirtualAppliance va = (VirtualAppliance) pm.localDisk.contents().iterator().next();
		VirtualMachine fraudent = new VirtualMachine(va);
		ResourceConsumption fraudentCon = new ResourceConsumption(1, ResourceConsumption.unlimitedProcessing, fraudent,
				pm, new ConsumptionEventAssert());
		assertFalse(fraudentCon.registerConsumption(), "Registering an unknown should fail");
		VirtualMachine proper = pm.requestVM(va, smallConstraints, pm.localDisk, 1)[0];
		assertNull(proper.newComputeTask(1, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert()), "Registration should not succeed since VM is not yet started up");
		Timed.simulateUntilLastEvent();
		assertTrue(proper.newComputeTask(1, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert())
						.isRegistered(), "Registration should succeed since VM is now running");
		Timed.simulateUntilLastEvent();
		proper.destroy(false);
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void checkVMNumReport() throws VMManagementException, NetworkException {
		preparePM();
		assertEquals(0, pm.numofCurrentVMs(), "Should not report any VM yet");
		assertEquals(0, pm.getCompletedVMs(), "Should not report any past VMs");
		VirtualMachine[] vms = requestVMs(smallConstraints, null, 2);
		assertEquals(0, pm.getCompletedVMs(), "Should not report any past VMs");
		assertEquals(pm.publicVms, pm.listVMs(), "Should offer the same VM list");
		Timed.simulateUntilLastEvent();
		assertEquals(2, pm.numofCurrentVMs(), "Should report both VMs");
		assertEquals(0, pm.getCompletedVMs(), "Should not report any past VMs");
		vms[0].destroy(false);
		assertEquals(1, pm.numofCurrentVMs(), "Should report the non destroyed VM only");
		assertEquals(1, pm.getCompletedVMs(), "Should report the destroyed VM only");
		vms[1].destroy(false);
		assertEquals(0, pm.numofCurrentVMs(), "Should not report any VM by now");
		assertEquals(2, pm.getCompletedVMs(), "Should report both destroyed VMs");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void successfulTerminateVMTest() throws VMManagementException, NetworkException {
		preparePM();
		VirtualMachine vm = requestVMs(smallConstraints, null, 1)[0];
		ResourceAllocation ra = vm.getResourceAllocation();
		assertFalse(ra.isUnUsed(), "The allocation should already be used");
		assertFalse(ra.isAvailable(), "The allocation should not be reported available");
		Timed.simulateUntilLastEvent();
		pm.terminateVM(vm, false);
		assertEquals(VirtualMachine.State.SHUTDOWN, vm.getState(), "Should reach a proper termination state");
		assertTrue(ra.isUnUsed(), "The allocation should be unused");
		assertFalse(ra.isAvailable(), "The allocation should not be reported available");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failedTerminateTest() {
		assertThrows(VMManager.NoSuchVMException.class, () -> {
			preparePM();
			VirtualMachine vm = new VirtualMachine((VirtualAppliance) pm.localDisk.contents().iterator().next());
			pm.terminateVM(vm, false);
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void obeseConsumptionRequestTest() throws VMManagementException {
		preparePM();
		AlterableResourceConstraints obese = new AlterableResourceConstraints(smallConstraints);
		obese.multiply(3);
		assertFalse(pm.isHostableRequest(obese), "The PM should not allow such big requests as hostable");
		assertFalse(pm.isHostableRequest(overCPUConstraints), "The PM should not allow such big requests as hostable");
		assertFalse(pm.isHostableRequest(overMemoryConstraints), "The PM should not allow such big requests as hostable");
		assertFalse(pm.isHostableRequest(overProcessingConstraints), "The PM should not allow such big requests as hostable");
		assertNull(pm.allocateResources(obese, true, PhysicalMachine.defaultAllocLen), "The PM should not allocate such a big request in strict mode");
		assertNull(pm.allocateResources(overCPUConstraints, true, PhysicalMachine.defaultAllocLen), "The PM should not allocate such a big request in strict mode");
		assertNull(pm.allocateResources(overMemoryConstraints, true, PhysicalMachine.defaultAllocLen), "The PM should not allocate such a big request in strict mode");
		assertNull(pm.allocateResources(overProcessingConstraints, true, PhysicalMachine.defaultAllocLen), "The PM should not allocate such a big request in strict mode");
		assertNull(pm.allocateResources(overProcessingConstraints, false, PhysicalMachine.defaultAllocLen), "The PM should not allocate a request with not possible to match per core processing power");
		ResourceAllocation maxed = pm.allocateResources(overCPUConstraints, false, PhysicalMachine.defaultAllocLen);
		assertEquals(0, maxed.allocated.compareTo(pm.getCapacities()), "In non strict mode, the PM should allocate its maximum capacities");
		assertTrue(maxed.allocated.compareTo(overCPUConstraints) < 0, "In non strict mode, the PM should allocate less than requested");
		maxed.cancel();
		maxed = pm.allocateResources(overMemoryConstraints, false, PhysicalMachine.defaultAllocLen);
		assertEquals(0, maxed.allocated.compareTo(pm.getCapacities()), "In non strict mode, the PM should allocate its maximum capacities");
		assertTrue(maxed.allocated.compareTo(overMemoryConstraints) < 0, "In non strict mode, the PM should allocate less than requested");
		maxed.cancel();
		assertEquals(-1, Timed.getNextFire(), "Should not have anything to do");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void duplicateVMStartup() {
		assertThrows(VMManagementException.class, () -> {
			preparePM();
			ResourceAllocation ra = pm.allocateResources(smallConstraints, true, PhysicalMachine.defaultAllocLen);
			VirtualAppliance va = (VirtualAppliance) pm.localDisk.contents().iterator().next();
			VirtualMachine vmSuccessful = new VirtualMachine(va);
			VirtualMachine vmFail = new VirtualMachine(va);
			vmSuccessful.switchOn(ra, pm.localDisk);
			vmFail.switchOn(ra, pm.localDisk);
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void shutdownWithMigrateTest() throws VMManagementException, NetworkException {
		PhysicalMachine first = dummyPMcreator();
		PhysicalMachine second = dummyPMcreator();
		first.turnon();
		second.turnon();
		Timed.simulateUntilLastEvent();
		VirtualAppliance va = new VirtualAppliance("MyVA", 3000, 0, false,
				first.localDisk.getFreeStorageCapacity() / 10);
		first.localDisk.registerObject(va);
		AlterableResourceConstraints rc = new AlterableResourceConstraints(first.getCapacities());
		rc.multiply(0.5);
		VirtualMachine[] vms = first.requestVM(va, rc, first.localDisk, 2);
		Timed.simulateUntilLastEvent();
		vms[0].newComputeTask(1, 1, new ConsumptionEventAssert());
		vms[1].newComputeTask(1, 1, new ConsumptionEventAssert());
		first.switchoff(second);
		Timed.simulateUntilLastEvent();
		assertEquals(2, ConsumptionEventAssert.hits.size(), "Both tasks should be finished by now");
		assertEquals(second, vms[0].getResourceAllocation().getHost(), "The VM should be on the second machine now");
		assertEquals(PhysicalMachine.State.OFF, first.getState(), "The first PM should be off now");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void unsubscriptionInStateChangeEventHandler() throws VMManagementException, NetworkException {
		final ArrayList<PhysicalMachine.State> changehits = new ArrayList<>();
		PhysicalMachine.StateChangeListener sl = new PhysicalMachine.StateChangeListener() {
			@Override
			public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
				changehits.add(newState);
				pm.unsubscribeStateChangeEvents(this);
			}
		};
		pm.subscribeStateChangeEvents(sl);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		assertEquals(1, changehits.size(), "Unsubscription failed, unexpected number of state changes");
		changehits.clear();
		pm.subscribeStateChangeEvents(sl);
		pm.switchoff(null);
		pm.subscribeStateChangeEvents(sl);
		Timed.simulateUntilLastEvent();
		assertEquals(2, changehits.size(), "Subscription failed, unexpected number of state changes");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void doubleStateChangeEntry() {
		final ArrayList<PhysicalMachine.State> statelist = new ArrayList<>();
		pm.subscribeStateChangeEvents((PhysicalMachine pmInt, State oldState, State newState) -> {
			statelist.add(newState);
			try {
				if (newState.equals(PhysicalMachine.State.RUNNING)) {
					pmInt.switchoff(null);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

		});
		assertEquals(0, statelist.size(), "Should not have any state updates yet");
		pm.turnon();
		assertEquals(1, statelist.size(), "Should already receive the switchingon state");
		Timed.simulateUntilLastEvent();
		assertEquals(4, statelist.size(), "Should pass through all the states by now");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void consumptionBlocking() {
		PhysicalMachine pm = dummyPMsCreator(1, 8, 2500, 8000000000L)[0];
		VirtualAppliance va = new VirtualAppliance("Test", 1, 0);
		pm.localDisk.registerObject(va);
		VirtualMachine vm = new VirtualMachine(va);
		ResourceConsumption conVM = new ResourceConsumption(100000, ResourceConsumption.unlimitedProcessing, vm, pm,
				new ConsumptionEventAssert());
		assertFalse(conVM.registerConsumption(), "A physical machine should not allow registering consumption from a nonhosted VM");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void parallelVMMuse() throws VMManagementException, NetworkException {
		preparePM();
		VirtualAppliance va = (VirtualAppliance) pm.localDisk.contents().iterator().next();
		final VirtualMachine vm = pm.requestVM(va, pm.getCapacities(), pm.localDisk, 1)[0];
		Timed.simulateUntilLastEvent();
		long startTime = Timed.getFireCount();
		vm.newComputeTask(aSecond, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert());
		Timed.simulateUntilLastEvent();
		vm.newComputeTask(aSecond, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert() {
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
		ResourceConsumption consumption = new ResourceConsumption(2 * aSecond, ResourceConsumption.unlimitedProcessing,
				pm.directConsumer, pm, new ConsumptionEventAdapter());
		consumption.registerConsumption();
		Timed.simulateUntilLastEvent();
		assertEquals(ConsumptionEventAssert.hits.get(1) - ConsumptionEventAssert.hits.get(0),
				(ConsumptionEventAssert.hits.get(0) - startTime) * 2, "The second task execution should take longer due to the VMM activity registered");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void terminatePMduringVMMActivity() throws VMManagementException, NetworkException {
		preparePM();
		long startTime = Timed.getFireCount();
		final long totalProcessing = 1000 * aSecond;
		// Check how long the first consumption takes
		ResourceConsumption consumption = new ResourceConsumption(totalProcessing,
				ResourceConsumption.unlimitedProcessing, pm.directConsumer, pm, new ConsumptionEventAssert());
		consumption.registerConsumption();
		Timed.simulateUntilLastEvent();
		// Ensure the second consumption takes that much time as well despite
		// the switchoff
		consumption = new ResourceConsumption(totalProcessing, ResourceConsumption.unlimitedProcessing,
				pm.directConsumer, pm, new ConsumptionEventAssert(
						Timed.getFireCount() + ConsumptionEventAssert.hits.get(0) - startTime, true));
		consumption.registerConsumption();
		Timed.fire();
		long currentTime = Timed.getFireCount();
		Timed.simulateUntil(currentTime + (Timed.getNextFire() - currentTime) / 2);
		pm.switchoff(null);
		final ArrayList<Long> lastHit = new ArrayList<>();
		pm.subscribeStateChangeEvents((PhysicalMachine pmInt, State oldState, State newState) -> {
			if (newState.equals(PhysicalMachine.State.OFF)) {
				lastHit.add(Timed.getFireCount());
			}
		});
		Timed.simulateUntilLastEvent();
		assertTrue(lastHit.get(0) > ConsumptionEventAssert.hits.get(1), "The PM should not get switched off before its VMM finishes its activities");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void registerWhileSwitchedOff() throws VMManagementException, NetworkException {
		preparePM();
		ResourceConsumption consumption = new ResourceConsumption(aSecond, ResourceConsumption.unlimitedProcessing,
				pm.directConsumer, pm, new ConsumptionEventAdapter());
		assertTrue(consumption.registerConsumption(), "Should be able to register to a running PM");
		Timed.simulateUntilLastEvent();
		pm.switchoff(null);
		ConsumptionEventAdapter cae = new ConsumptionEventAdapter();
		consumption = new ResourceConsumption(aSecond, ResourceConsumption.unlimitedProcessing, pm.directConsumer, pm,
				cae);
		assertFalse(consumption.registerConsumption(), "Should not be able to register to a switching off PM");
		Timed.simulateUntilLastEvent();
		assertFalse(cae.isCompleted(), "Should not ever complete if it was not registered");
		assertFalse(consumption.registerConsumption(), "Should not be able to register to an off PM");
		pm.turnon();
		assertFalse(consumption.registerConsumption(), "Should not be able to register to turning on PM");
		Timed.simulateUntilLastEvent();
		assertTrue(consumption.registerConsumption(), "Should be able to register to a running PM");
		Timed.simulateUntilLastEvent();
		assertTrue(cae.isCompleted(), "Should be complete by now");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void complexBootup() throws VMManagementException, NetworkException {
		pm = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk, new double[] { 1, 0.1, 2, 0.05, 3, 3 },
				new double[] { 3, 0.3, 12, ResourceConsumption.unlimitedProcessing }, defaultHostTransitions);
		final long turnOnTime = 10 + 40 + 1;
		final long switchOffTime = 10 + 2;
		long before = Timed.getFireCount();
		pm.turnon();
		Timed.simulateUntilLastEvent();
		assertEquals(turnOnTime, Timed.getFireCount() - before - 1, "Turnon should take this much time: ");
		before = Timed.getFireCount();
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		assertEquals(switchOffTime, Timed.getFireCount() - before - 1, "Switchoff should take this much time: ");
		before = Timed.getFireCount();
		pm.turnon();
		Timed.simulateUntil(before + turnOnTime / 2);
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		assertEquals(turnOnTime + switchOffTime, Timed.getFireCount() - before - 1, "A complete turnon-switchoff cycle should take this much time: ");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void testZeroLenDelays() throws VMManagementException, NetworkException {
		pm = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk, 0, 1, defaultHostTransitions);
		pm.turnon();
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		pm.turnon();
		Timed.simulateUntilLastEvent();
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		pm = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk, 1, 0, defaultHostTransitions);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		pm.switchoff(null);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		pm.switchoff(null);
		pm.turnon();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void testTurnOnWhileInTransition() {
		pm.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {
			boolean needsSORequest = true;

			@Override
			public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
				assertFalse(oldState.equals(PhysicalMachine.State.RUNNING) && newState.equals(PhysicalMachine.State.OFF), "Inproper state transition R->O");
				if (newState.equals(PhysicalMachine.State.RUNNING) && needsSORequest) {
					needsSORequest = false;
					try {
						pm.switchoff(null);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				if (newState.equals(PhysicalMachine.State.SWITCHINGOFF)) {
					pm.turnon();
				}
			}
		});
		pm.turnon();
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void doubleRaCancel() throws VMManagementException {
		pm.turnon();
		Timed.simulateUntilLastEvent();
		PhysicalMachine.ResourceAllocation ra = pm.allocateResources(pm.getCapacities(), true,
				PhysicalMachine.defaultAllocLen);
		ra.cancel();
		ra.cancel();
		assertEquals(0, pm.getCapacities().compareTo(pm.freeCapacities), "Should have no free capacity change");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void explainMigrationFailure() {
		assertThrows(VMManagementException.class, () -> {
			PhysicalMachine firstPM = dummyPMcreator();
			PhysicalMachine otherPM = dummyPMcreator();
			firstPM.turnon();
			otherPM.turnon();
			Timed.simulateUntilLastEvent();
			VirtualAppliance va = new VirtualAppliance("occupy", 1, 0, false,
					firstPM.localDisk.getMaxStorageCapacity() / 10);
			firstPM.localDisk.registerObject(va);
			firstPM.requestVM(va, firstPM.getCapacities(), firstPM.localDisk, 1);
			VirtualMachine vm = otherPM.requestVM(va, otherPM.getCapacities(), firstPM.localDisk, 1)[0];
			Timed.simulateUntilLastEvent();
			otherPM.migrateVM(vm, firstPM);
		});
	}
}
