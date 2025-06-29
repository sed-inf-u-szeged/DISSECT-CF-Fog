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

package at.ac.uibk.dps.cloud.simulator.test.simple.cloud;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.StateChangeException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class VMTest extends IaaSRelatedFoundation {

	PhysicalMachine pm;
	VirtualAppliance va, vaWithBG;
	VirtualMachine centralVM, centralVMwithBG;
	Repository repo;
	final static long defaultMemory = 1000;

	@BeforeEach
	public void initializeObject() throws Exception {
		pm = dummyPMcreator();
		va = new VirtualAppliance("BaseVA", 1000, 0, false, pm.localDisk.getMaxStorageCapacity() / 10);
		centralVM = new VirtualMachine(va);
		vaWithBG = new VirtualAppliance("BaseVAWithBG", 1000, 10, false, pm.localDisk.getMaxStorageCapacity() / 10);
		repo = dummyRepoCreator(false);
		repo.registerObject(va);
		repo.registerObject(vaWithBG);
		repo.setState(NetworkNode.State.RUNNING);
		centralVMwithBG = new VirtualMachine(vaWithBG);
		pm.localDisk.registerObject(va);
		pm.localDisk.registerObject(vaWithBG);
		pm.turnon();
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void constructionTest() {
		assertEquals( va, centralVM.getVa(),"The collected VA is not matching");
		assertEquals( VirtualMachine.State.DESTROYED,
				centralVM.getState(),"The VM is not in the expected state");
		assertTrue(
				centralVM.toString().contains(centralVM.getState().toString()),"The VM state should be in its string output");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void faultyConstructionTest() {
		assertThrows(IllegalStateException.class, () -> new VirtualMachine(null));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void nonServableTest() throws VMManagementException, NetworkException {
		centralVM.setNonservable();
		PhysicalMachine.ResourceAllocation ra = pm.allocateResources(pm.getCapacities(), true,
				PhysicalMachine.defaultAllocLen);
		try {
			centralVM.migrate(ra);
			fail("It should not be possible to do migration if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		ra.cancel();
		try {
			centralVM.prepare(dummyRepoCreator(false), dummyRepoCreator(false));
			fail("It should not be possible to do preparation if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			centralVM.resume();
			fail("It should not be possible to do resume if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			centralVM.suspend();
			fail("It should not be possible to do suspend if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			centralVM.switchoff(false);
			fail("It should not be possible to do switchoff if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			switchOnVMwithMaxCapacity(centralVM, true);
			fail("It should not be possible to do switchon if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			centralVM.destroy(false);
		} catch (StateChangeException ex) {
			fail("It should be possible to do a destroy if the VM is non servable");
		}
	}

	private void switchOnVMwithMaxCapacity(VirtualMachine target, boolean simulate)
			throws VMManagementException, NetworkException {
		target.switchOn(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen),
				target.getVa().getBgNetworkLoad() > 0 ? repo : pm.localDisk);
		if (simulate) {
			Timed.simulateUntilLastEvent();
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void simpleVMStartup() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		assertEquals( VirtualMachine.State.RUNNING,
				centralVM.getState(),"Regular VM not in the excpected state after switchon");
		centralVM.destroy(false);
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void bgLoadVMStatrup() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVMwithBG, true);
		assertEquals( VirtualMachine.State.RUNNING,
				centralVMwithBG.getState(),"BGLoad VM not in the excpected state after switchon");
		centralVMwithBG.destroy(false);
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void faultyBgLoadVMStartup() throws VMManagementException, NetworkException {
		assertThrows(VMManagementException.class, () -> centralVMwithBG.switchOn(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen),
				pm.localDisk));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void startupAfterPrepare() throws VMManagementException, NetworkException {
		centralVM.prepare(pm.localDisk, pm.localDisk);
		Timed.simulateUntilLastEvent();
		assertEquals( VirtualMachine.State.SHUTDOWN,
				centralVM.getState(),"VM is not in expected state after prepare");
		switchOnVMwithMaxCapacity(centralVM, true);
		assertEquals( VirtualMachine.State.RUNNING,
				centralVM.getState(),"Regular VM not in the excpected state after switchon");
		centralVM.destroy(false);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void faultyStartupAfterPrepare() throws VMManagementException, NetworkException {
		PhysicalMachine target = dummyPMcreator();
		target.turnon();
		Timed.simulateUntilLastEvent();
		centralVM.prepare(pm.localDisk, target.localDisk);
		Timed.simulateUntilLastEvent();
		assertEquals( VirtualMachine.State.SHUTDOWN,
				centralVM.getState(),"VM is not in expected state after prepare");
		assertThrows(VMManagementException.class, () -> switchOnVMwithMaxCapacity(centralVM, true));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void prepareOnlyTest() throws VMManagementException, NetworkException {
		long beforeFreeStorage = pm.localDisk.getFreeStorageCapacity();
		centralVM.prepare(null, pm.localDisk);
		Timed.simulateUntilLastEvent();
		assertEquals( VirtualMachine.State.SHUTDOWN,
				centralVM.getState(),"VM is not in expected state after prepare");
		long afterFreeStorage = pm.localDisk.getFreeStorageCapacity();
		assertEquals( beforeFreeStorage - va.size,
				afterFreeStorage,"PM disk free capacity mismatch after prepare");
		centralVM.destroy(false);
		assertEquals( beforeFreeStorage,
				pm.localDisk.getFreeStorageCapacity(),"PM disk free capacity mismatch after destroy");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void nullVATargetTest() throws VMManagementException, NetworkException {
		assertThrows(VMManagementException.class, () -> centralVM.prepare(pm.localDisk, null));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void doubleNullPrepareTest() throws VMManagementException, NetworkException {
		assertThrows(VMManagementException.class, () -> centralVM.prepare(null, null));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void noFreeSpacePrepareTest() throws VMManagementException, NetworkException {
		pm.localDisk
				.registerObject(new StorageObject("MakesLocalDiskFull", pm.localDisk.getFreeStorageCapacity(), false));
		assertThrows(VMManagementException.class, () -> centralVM.prepare(null, pm.localDisk));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void taskKillingSwitchOff() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		ResourceConsumption con = centralVM.newComputeTask(2, ResourceConsumption.unlimitedProcessing, cae);
		Timed.fire();
		centralVM.switchoff(true);
		Timed.fire();
		assertTrue( cae.isCancelled(),"The compute task should be cancelled after switchoff");
		assertFalse( con.registerConsumption(),"The compute task should be non-resumable after switchoff");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failingSwitchOff() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		centralVM.newComputeTask(1, ResourceConsumption.unlimitedProcessing, cae);
		Timed.fire();
		try {
			centralVM.switchoff(false);
			fail(
					"On a VM with compute tasks, switchoff should not succeed if it was not asked to kill the compute tasks");
		} catch (VMManagementException ex) {
			// Expected
		}
		assertFalse( cae.isCancelled(),"The compute task should not be cancelled after faulty switchoff");
		Timed.simulateUntilLastEvent();
		assertTrue( cae.isCompleted(),"It should be possible to finish the compute task after faulty switchoff");
		centralVM.destroy(false);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void subscriptionTest() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		final ArrayList<VirtualMachine.State> receivedStates = new ArrayList<>();
		VirtualMachine.StateChange sc = (VirtualMachine vmInt, State oldState, State newState) -> receivedStates
				.add(newState);
		centralVM.subscribeStateChange(sc);
		centralVM.destroy(false);
		centralVM.unsubscribeStateChange(sc);
		assertArrayEquals(
				new VirtualMachine.State[] { VirtualMachine.State.SHUTDOWN, VirtualMachine.State.DESTROYED },
				receivedStates.toArray(new VirtualMachine.State[receivedStates.size()]),"Did not receive the necessary state changes");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void consumptionRejectionTest() throws VMManagementException, NetworkException {
		final ResourceConsumption con = new ResourceConsumption(1, ResourceConsumption.unlimitedProcessing, centralVM,
				pm, new ConsumptionEventAssert());
		double initialUnpr = con.getUnProcessed();
		assertFalse(
				con.registerConsumption(),"Consumption registration should not succeed unless in consuming state");
		switchOnVMwithMaxCapacity(centralVM, false);
		assertFalse(
				con.registerConsumption(),"Consumption registration should not succeed in inital transfer phase");
		assertEquals( initialUnpr, con.getUnProcessed(), 0,"Unprocessed consumption mismatch");
		centralVM.subscribeStateChange((VirtualMachine vmInt, State oldState, State newState) -> {
			if (VirtualMachine.consumingStates.contains(newState) && !con.isRegistered()) {
				assertTrue( con.registerConsumption(),"Consumption registration should now proceed");
			}
		});
		Timed.simulateUntilLastEvent();
		assertEquals( 0, con.getUnProcessed(),0, "Unprocessed consumption mismatch");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void newConsumptionRejectionTest() throws VMManagementException, NetworkException {
		assertEquals( null,
				centralVM.newComputeTask(1, 1, new ConsumptionEventAssert()),"VM should not accept consumption while destroyed");
		switchOnVMwithMaxCapacity(centralVM, false);
		assertEquals( null,
				centralVM.newComputeTask(1, 1, new ConsumptionEventAssert()),"VM should not accept consumption in initial transfer phase");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void errenousAllocationRequest() throws VMManagementException, NetworkException {
		AlterableResourceConstraints constraints = new AlterableResourceConstraints(pm.getCapacities());
		constraints.multiply(0.1);
		centralVM.switchOn(pm.allocateResources(constraints, true, PhysicalMachine.defaultAllocLen), pm.localDisk);
		assertThrows(StateChangeException.class, () -> centralVM.setResourceAllocation(pm.allocateResources(constraints, true, PhysicalMachine.defaultAllocLen)));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void allocationHandlingCorrectness() throws VMManagementException, NetworkException {
		PhysicalMachine.ResourceAllocation alloc = pm.allocateResources(pm.getCapacities(), true,
				PhysicalMachine.defaultAllocLen);
		centralVM.switchOn(alloc, pm.localDisk);
		assertEquals( alloc, centralVM.getResourceAllocation(),"Resource allocation mismatch");
		Timed.simulateUntilLastEvent();
		centralVM.destroy(false);
		assertEquals( null, centralVM.getResourceAllocation(),"Resource allocation should be released");
	}

	private void simpleSusResume(VirtualMachine vmToUse) throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(vmToUse, true);
		ConsumptionEventAssert cea = new ConsumptionEventAssert();
		PhysicalMachine.ResourceAllocation ra = vmToUse.getResourceAllocation();
		vmToUse.newComputeTask(1, 1, cea);
		long memless = pm.localDisk.getFreeStorageCapacity();
		long memsize = ra.allocated.getRequiredMemory();
		vmToUse.suspend();
		assertEquals( VirtualMachine.State.SUSPEND_TR, vmToUse.getState(),"Not in expected state");
		Timed.simulateUntilLastEvent();
		assertEquals( memless - memsize,
				pm.localDisk.getFreeStorageCapacity(),"PM's local storage should decrease with memory size");
		assertEquals( VirtualMachine.State.SUSPENDED, vmToUse.getState(),"Not in expected state");
		assertFalse(
				cea.isCancelled() || cea.isCompleted(),"The consumption should not finish because it should be suspended");
		assertTrue( ra.isUnUsed(),"The allocation should be unused by now");
		vmToUse.setResourceAllocation(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen));
		vmToUse.resume();
		Timed.simulateUntilLastEvent();
		assertTrue( cea.isCompleted(),"The consumption should finish after resume");
		assertEquals( memless,
				pm.localDisk.getFreeStorageCapacity(),"The PM's local storage should return to the original levels of free capacities");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void successfulSuspendResumeTest() throws VMManagementException, NetworkException {
		simpleSusResume(centralVM);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void successfulSuspendResumeTestWithBGLoad() throws VMManagementException, NetworkException {
		simpleSusResume(centralVMwithBG);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void ensureSuspendUsesJustEnoughDisk() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		long memless = pm.localDisk.getFreeStorageCapacity();
		long memsize = centralVM.getResourceAllocation().allocated.getRequiredMemory();
		pm.localDisk.registerObject(new StorageObject("SpaceFiller", memless - memsize, false));
		centralVM.suspend();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void ensureSuspendFailsIfNotEnoughDiskSpace() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		pm.localDisk.registerObject(new StorageObject("SpaceFiller", pm.localDisk.getFreeStorageCapacity(), false));
		assertThrows(VMManagementException.class, centralVM::suspend);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void ensureResumeUsesJustEnoughDisk() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		centralVM.suspend();
		Timed.simulateUntilLastEvent();
		pm.localDisk.registerObject(new StorageObject("SpaceFiller", pm.localDisk.getFreeStorageCapacity(), false));
		centralVM
				.setResourceAllocation(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen));
		centralVM.resume();
		Timed.simulateUntilLastEvent();
	}

	private PhysicalMachine createAndExecutePM() {
		PhysicalMachine pmtarget = dummyPMcreator();
		pmtarget.turnon();
		Timed.simulateUntilLastEvent();
		return pmtarget;
	}

	private void doMigration(PhysicalMachine from, PhysicalMachine to, VirtualMachine vm, boolean sim)
			throws VMManagementException, NetworkException {
		from.migrateVM(vm, to);
		if (sim) {
			Timed.simulateUntilLastEvent();
		}
	}

	private PhysicalMachine simpleMigrate(final VirtualMachine toUse) throws VMManagementException, NetworkException {
		final PhysicalMachine pmtarget = createAndExecutePM();
		assertTrue(
				pmtarget.localDisk.getMaxStorageCapacity() == pmtarget.localDisk.getFreeStorageCapacity(),"The target PM should not have anything in its storage");
		switchOnVMwithMaxCapacity(toUse, true);
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		final double ctLen = 100 * aSecond;
		toUse.newComputeTask(ctLen, 1, cae);
		Timed.simulateUntil(Timed.getFireCount() + aSecond);
		doMigration(pm, pmtarget, toUse, true);
		assertTrue( pmtarget.publicVms.contains(toUse),"VM is not on its new host");
		assertFalse( pm.publicVms.contains(toUse),"VM is still on its old host");
		assertEquals( VirtualMachine.State.RUNNING, toUse.getState(),"VM is not properly resumed");
		assertTrue(
				pm.getTotalProcessed() < pmtarget.getTotalProcessed(),"Source VM should have minority of the consumption");
		return pmtarget;
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void simpleMigration() throws VMManagementException, NetworkException {
		final long beforeSize = pm.localDisk.getFreeStorageCapacity();
		PhysicalMachine pmtarget = simpleMigrate(centralVM);
		assertEquals(
				beforeSize, pm.localDisk.getFreeStorageCapacity(),"The source of the migration should not have any storage occupied by the VM's remainders");
		assertEquals(
				pmtarget.localDisk.getMaxStorageCapacity() - va.size, pmtarget.localDisk.getFreeStorageCapacity(),"The target of the migration should only have the disk of the VM");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void simpleLiveMigration() throws VMManagementException, NetworkException {
		final long beforeSize = pm.localDisk.getFreeStorageCapacity();
		PhysicalMachine pmtarget = simpleMigrate(centralVMwithBG);
		assertEquals(
				beforeSize, pm.localDisk.getFreeStorageCapacity(),"The source of the migration should not have any storage occupied by the VM's remainders");
		assertEquals(
				pmtarget.localDisk.getMaxStorageCapacity(), pmtarget.localDisk.getFreeStorageCapacity(),"The target of the migration should not have any storage occupied by the VM's remainders");
	}

	private void doubleMigrate(final VirtualMachine toUse) throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		final PhysicalMachine pmtarget = createAndExecutePM();
		final double beforePmCon = pm.getTotalProcessed();
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		final double ctLen = 100 * aSecond;
		centralVM.newComputeTask(ctLen, 1, cae);
		Timed.simulateUntil(Timed.getFireCount() + aSecond);
		doMigration(pm, pmtarget, centralVM, false);
		final boolean[] haveNotBeenThere = new boolean[1];
		haveNotBeenThere[0] = true;
		centralVM.subscribeStateChange((VirtualMachine vmInt, State oldState, State newState) -> {
			if (newState.equals(VirtualMachine.State.RUNNING) && haveNotBeenThere[0]) {
				haveNotBeenThere[0] = false;
				new DeferredEvent(aSecond) {
					@Override
					protected void eventAction() {
						try {
							doMigration(pmtarget, pm, centralVM, false);
						} catch (Exception e) {
							throw new IllegalStateException("Second migration failed", e);
						}
					}
				};
			}
		});
		Timed.simulateUntilLastEvent();
		assertFalse( pmtarget.publicVms.contains(centralVM),"VM is still on its new host");
		assertTrue( pm.publicVms.contains(centralVM),"VM is not on its old host");
		assertEquals( VirtualMachine.State.RUNNING, centralVM.getState(),"VM is not properly resumed");
		assertEquals( beforePmCon + ctLen - aSecond,
				pm.getTotalProcessed(),0.01, "Source VM should have the majority of the consumption");
		assertEquals( aSecond,
				pmtarget.getTotalProcessed(),0.01 ,"Target VM should have the minority of the consumption");

	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void doubleMigration() throws VMManagementException, NetworkException {
		doubleMigrate(centralVM);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void doubleMigrationWithBgNWL() throws VMManagementException, NetworkException {
		doubleMigrate(centralVMwithBG);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void migrationAfterSuspend() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		ResourceConstraints original = centralVM.getResourceAllocation().allocated;
		centralVM.suspend();
		final PhysicalMachine pmtarget = createAndExecutePM();
		centralVM.migrate(pmtarget.allocateResources(original, true, PhysicalMachine.migrationAllocLen));
		Timed.simulateUntilLastEvent();
		assertTrue( pm.publicVms.isEmpty(),"Source should not host the VM anymore");
		assertTrue( pmtarget.publicVms.contains(centralVM),"Target should host the VM");
	}

	private void failMigrationProcedureAfterSuspendPhase(long spaceToLeave)
			throws VMManagementException, NetworkException {
		final PhysicalMachine pmtarget = createAndExecutePM();
		pmtarget.localDisk.registerObject(new StorageObject("TargetStorageFiller",
				pmtarget.localDisk.getFreeStorageCapacity() - spaceToLeave, false));
		switchOnVMwithMaxCapacity(centralVM, true);
		doMigration(pm, pmtarget, centralVM, true);
		fail("Migration should fail with an exception, should not reach this point");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failMigrationProcedureAfterSuspendPhaseNoAvalableStorage()
			throws VMManagementException, NetworkException {
		// Does not allow the copying of the disk to the target machine
		assertThrows(VMManagementException.class, () -> failMigrationProcedureAfterSuspendPhase(centralVM.getVa().size - 1));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void migrationFailureandRetry() throws VMManagementException, NetworkException {
		final PhysicalMachine pmnewtarget = createAndExecutePM();
		try {
			failMigrationProcedureAfterSuspendPhase(centralVM.getVa().size - 1);
		} catch (VMManagementException e) {
			// Expected
		}
		centralVM.migrate(
				pmnewtarget.allocateResources(pmnewtarget.getCapacities(), true, PhysicalMachine.migrationAllocLen));
		Timed.simulateUntilLastEvent();
		assertEquals( VirtualMachine.State.RUNNING, centralVM.getState(),"The VM is now expected to be running");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void migrationFailureandResume() throws VMManagementException, NetworkException {
		try {
			failMigrationProcedureAfterSuspendPhase(centralVM.getVa().size - 1);
		} catch (VMManagementException e) {
			// Expected
		}
		assertEquals(
				VirtualMachine.State.RUNNING, centralVM.getState(),"The VM should be running just fine after a failed migration attempt");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void suspendUseForOtherResume() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		ConsumptionEventAssert first = new ConsumptionEventAssert(), second = new ConsumptionEventAssert();
		centralVM.newComputeTask(1, 1, first);
		long memless = pm.localDisk.getFreeStorageCapacity();
		centralVM.suspend();
		Timed.simulateUntilLastEvent();
		VirtualMachine otherVM = pm.requestVM(va, new AlterableResourceConstraints(pm.freeCapacities), repo, 1)[0];
		Timed.simulateUntilLastEvent();
		otherVM.newComputeTask(1, 1, second);
		Timed.simulateUntilLastEvent();
		assertFalse(
				first.isCompleted() || first.isCancelled(),"The first task should not be executed as it is in the suspended VM");
		assertTrue( second.isCompleted(),"The second task should be complete by now");
		otherVM.destroy(false);
		centralVM
				.setResourceAllocation(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen));
		centralVM.resume();
		Timed.simulateUntilLastEvent();
		assertTrue( first.isCompleted(),"The consumption should finish after resume");
		assertEquals( memless,
				pm.localDisk.getFreeStorageCapacity(),"The PM's local storage should return to the original levels of free capacities");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void consumptionBlocking() throws VMManagementException, NetworkException {
		final VirtualMachine vm = pm.requestVM(va, new AlterableResourceConstraints(pm.availableCapacities),
				pm.localDisk, 1)[0];
		ResourceConsumption conVM = new ResourceConsumption(100000, ResourceConsumption.unlimitedProcessing, vm, pm,
				new ConsumptionEventAssert());
		assertFalse(
				conVM.registerConsumption(),"A virtual machine should not allow registering a consumption in a non-running state");
		Timed.simulateUntilLastEvent();
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
	}

	private boolean vmDestroyerinState(VirtualMachine.State st, VirtualMachine vm) throws VMManagementException {
		if (vm.getState().equals(st)) {
			vm.destroy(true);
			return true;
		}
		return false;
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void ensuringReleasewithDestroy() throws VMManagementException, NetworkException {
		for (final VirtualMachine.State st : VirtualMachine.State.values()) {
			if (vmDestroyerinState(st, centralVM))
				continue;
			final boolean[] marker = new boolean[1];
			marker[0] = false;
			switchOnVMwithMaxCapacity(centralVM, false);
			try {
				if (!vmDestroyerinState(st, centralVM)) {
					centralVM.subscribeStateChange((VirtualMachine vmInt, State oldState, State newState) -> {
						try {
							vmDestroyerinState(st, centralVM);
						} catch (VMManagementException ex) {
							marker[0] = true;
						}
					});
				}
			} catch (VMManagementException e) {
				marker[0] = true;
			}
			Timed.simulateUntilLastEvent();
			if (marker[0]) {
				assertEquals(VirtualMachine.State.RUNNING,
						centralVM.getState(), "Should be running from state " + st);
				centralVM.destroy(true);
			} else {
				assertEquals(VirtualMachine.State.DESTROYED,
						centralVM.getState(), "Should be destroyed from state " + st);
			}
			Timed.simulateUntilLastEvent();
			assertEquals(VirtualMachine.State.DESTROYED, centralVM.getState(), "After the second destroy it should be destroyed from state " + st);
			assertNull(centralVM.getResourceAllocation(), "Should not have any resource allocations starting from state " + st);
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void ensureVMKeepsItsAllocationDuringInitialMigration() throws VMManagementException, NetworkException {
		final EnumSet<VirtualMachine.State> needsAllocationOnSource = EnumSet.of(VirtualMachine.State.MIGRATING,
				VirtualMachine.State.SUSPEND_TR);
		PhysicalMachine target = createAndExecutePM();
		switchOnVMwithMaxCapacity(centralVM, true);
		final ConstantConstraints needToKeepThisCapacity = new ConstantConstraints(pm.freeCapacities);
		centralVM.subscribeStateChange((VirtualMachine vm, State oldState, State newState) -> {
			if (needsAllocationOnSource.contains(newState)) {
				assertTrue(needToKeepThisCapacity.compareTo(pm.freeCapacities) == 0, "Allocation for source disappeared prematurely, current VM state is " + newState);
			}
		});
		pm.migrateVM(centralVM, target);
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void allowDestroyDuringInitialTransfer() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, false);
		new DeferredEvent(1) {
			@Override
			protected void eventAction() {
				try {
					centralVM.destroy(false);
				} catch (VMManagementException e) {
					throw new RuntimeException(e);
				}
			}
		};
		Timed.simulateUntilLastEvent();
		assertEquals( VirtualMachine.State.DESTROYED, centralVM.getState(),"Should get to destroyed");
		switchOnVMwithMaxCapacity(centralVM, true);
		assertEquals( VirtualMachine.State.RUNNING,
				centralVM.getState(),"Cancel should not ruin the chance to get to running");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void allowDestroyWhileMigrating() throws VMManagementException, NetworkException {
		PhysicalMachine target = createAndExecutePM();
		switchOnVMwithMaxCapacity(centralVM, true);
		pm.migrateVM(centralVM, target);
		assertEquals( VirtualMachine.State.MIGRATING,
				centralVM.getState(),"The VM should already be in MIGRATING phase");
		centralVM.destroy(false);
		Timed.simulateUntil(Timed.getFireCount() + aSecond);
		assertEquals( 0,
				pm.getCapacities().compareTo(pm.freeCapacities),"There should be no allocation kept for the VM on the source");
		assertEquals( 0,
				target.getCapacities().compareTo(target.freeCapacities),"There should be no allocation kept for the VM on the target");
		Timed.simulateUntilLastEvent();
		assertEquals( VirtualMachine.State.DESTROYED, centralVM.getState(),"The VM should be destroyed by now");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void disallowLiveIfNotRunning() throws VMManagementException, NetworkException {
		PhysicalMachine target = createAndExecutePM();
		switchOnVMwithMaxCapacity(centralVMwithBG, true);
		ConstantConstraints rc = new ConstantConstraints(centralVMwithBG.getResourceAllocation().allocated);
		centralVMwithBG.suspend();
		Timed.simulateUntilLastEvent();
		assertThrows(StateChangeException.class, () -> centralVMwithBG.migrate(target.allocateResources(rc, true, PhysicalMachine.migrationAllocLen), true));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void disallowLiveForNonRemotelyStored() throws VMManagementException, NetworkException {
		PhysicalMachine target = createAndExecutePM();
		switchOnVMwithMaxCapacity(centralVM, true);
		assertThrows(VMManagementException.class,() -> centralVM.migrate(target.allocateResources(centralVM.getResourceAllocation().allocated, true,
				PhysicalMachine.migrationAllocLen), true));
	}

}
