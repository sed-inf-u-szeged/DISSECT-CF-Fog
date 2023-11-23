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

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class RepositoryTest extends PMRelatedFoundation {
	public final static long storageCapacity = 5000000000000L;
	public final static String storageObjectID = "SOID";
	Repository source, target;
	StorageObject so = new StorageObject(storageObjectID);

	@BeforeEach
	public void repoSetup() throws NetworkException {
		HashMap<String, Integer> lm = NetworkNodeTest.setupALatencyMap();
		source = new Repository(storageCapacity, NetworkNodeTest.sourceName, NetworkNodeTest.inBW,
				NetworkNodeTest.outBW, NetworkNodeTest.diskBW, lm, defaultStorageTransitions,
				defaultNetworkTransitions);
		target = new Repository(storageCapacity, NetworkNodeTest.targetName, NetworkNodeTest.inBW,
				NetworkNodeTest.outBW, NetworkNodeTest.diskBW, lm, defaultStorageTransitions,
				defaultNetworkTransitions);
		source.setState(NetworkNode.State.RUNNING);
		target.setState(NetworkNode.State.RUNNING);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void checkConstruction() {
		assertTrue(source.toString().contains(NetworkNodeTest.sourceName), "The toString of Repository should contain the node name");
		assertEquals(storageCapacity, source.getMaxStorageCapacity(), "Repositry size mismatch");
	}

	private void registerWithCheck(StorageObject toRegister) {
		source.registerObject(toRegister);
		assertTrue(source.contents().contains(toRegister), "Storage object is not amongst contents");
		assertEquals(toRegister, source.lookup(toRegister.id), "Returned with different storage object than expected");
	}

	private void emptyCheck() {
		assertFalse(source.contents().contains(so), "Storage object is still amongst contents");
		assertNull(source.lookup(storageObjectID), "After deletion the storage object can still be reclaimed");
		assertEquals(0, source.contents().size(), "The repository should be empty by now!");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void registrationTest() {
		registerWithCheck(so);
		source.registerObject(so);
		assertEquals(1,
				source.contents().size(), "Multiple registrations result in multiple storage objects in the repository!");
		assertEquals(source.getMaxStorageCapacity() - so.size, source.getFreeStorageCapacity(), "The source repository is not reporting accurate size");
		// Removal by object
		source.deregisterObject(so);
		emptyCheck();
		// These should not cause null pointers!
		source.deregisterObject((StorageObject) null);
		source.deregisterObject((String) null);
		registerWithCheck(so);
		// Removal by ID
		source.deregisterObject(storageObjectID);
		emptyCheck();
		assertFalse(source.deregisterObject(so), "It was possible to remove a non stored object!");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void simpleTransferTest() throws NetworkException {
		final String extendedID = storageObjectID + ".ext";
		registerWithCheck(so);
		// 1st transfer
		source.requestContentDelivery(storageObjectID, target, new ConsumptionEventAssert() {
			@Override
			public void conComplete() {
				super.conComplete();
				assertNotNull(target.lookup(storageObjectID), "Transferred storage object has not got the same name");
			}

		});
		// 2nd transfer
		source.requestContentDelivery(storageObjectID, extendedID, target, new ConsumptionEventAssert() {
			@Override
			public void conComplete() {
				super.conComplete();
				assertNotNull(target.lookup(extendedID), "Transferred storage object has not got the new name");
			}
		});
		// 3rd transfer
		source.duplicateContent(storageObjectID, extendedID, new ConsumptionEventAssert() {
			@Override
			public void conComplete() {
				super.conComplete();
				assertNotNull(source.lookup(extendedID), "Duplicated storage object has not got the new name");
			}
		});
		source.requestContentDelivery(null, null, null, null);
		source.requestContentDelivery(null, null, null, new ConsumptionEventAssert());
		source.requestContentDelivery(null, null, target, null);
		source.requestContentDelivery(null, null, target, new ConsumptionEventAssert());
		source.requestContentDelivery(null, null, source, null);
		source.requestContentDelivery(null, null, source, new ConsumptionEventAssert());
		source.requestContentDelivery(null, extendedID, null, null);
		source.requestContentDelivery(null, extendedID, null, new ConsumptionEventAssert());
		source.requestContentDelivery(null, extendedID, target, null);
		source.requestContentDelivery(null, extendedID, target, new ConsumptionEventAssert());
		source.requestContentDelivery(null, extendedID, source, null);
		source.requestContentDelivery(null, extendedID, source, new ConsumptionEventAssert());
		source.requestContentDelivery(storageObjectID, null, null, null);
		source.requestContentDelivery(storageObjectID, null, null, new ConsumptionEventAssert());
		source.requestContentDelivery(storageObjectID, null, target, null);
		// 4th transfer
		source.requestContentDelivery(storageObjectID, null, target, new ConsumptionEventAssert());
		source.requestContentDelivery(storageObjectID, null, source, null);
		source.requestContentDelivery(storageObjectID, null, source, new ConsumptionEventAssert());
		source.requestContentDelivery(storageObjectID, extendedID, null, null);
		source.requestContentDelivery(storageObjectID, extendedID, null, new ConsumptionEventAssert());
		source.requestContentDelivery(storageObjectID, extendedID, target, null);
		// 5th transfer
		source.requestContentDelivery(storageObjectID, extendedID, target, new ConsumptionEventAssert());
		source.requestContentDelivery(storageObjectID, extendedID, source, null);
		// 6th transfer
		source.requestContentDelivery(storageObjectID, extendedID, source, new ConsumptionEventAssert());
		Timed.simulateUntilLastEvent();
		assertEquals(6, ConsumptionEventAssert.hits.size(), "Not all events finished");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void obeseTransferTest() throws NetworkException {
		final String obeseID = "Obese";
		final StorageObject obeseObject = new StorageObject(obeseID, storageCapacity, false);
		registerWithCheck(obeseObject);
		assertEquals(0, source.getFreeStorageCapacity(), "Source repository should be full");
		assertFalse(source.registerObject(so), "It was possible to register a storage object to a full repository!");
		source.requestContentDelivery(obeseID, target, new ConsumptionEventAssert());
		assertFalse(source.deregisterObject(obeseObject), "It was possible to deregister an under transfer storage object");
		assertNull(source.requestContentDelivery(obeseID, target, new ConsumptionEventAssert()), "It was possible to initiate a new transfer to an already promised full repository");
		assertFalse(target.registerObject(so), "It was possible to register a storage object to a promised full repository!");
		Timed.simulateUntilLastEvent();
		assertEquals(0, target.getFreeStorageCapacity(), "Target repository should be full");
	}

	/**
	 * Since this is a performance test one might expect this to easily overcome the
	 * timeout in case some performance degradations!
	 */
	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void registrationPerformance() {
		final int vmNum = 9000;
		final StorageObject[] vaBase = new StorageObject[vmNum];
		for (int i = 0; i < vmNum; i++) {
			vaBase[i] = so.newCopy("AutoCreate" + i);
			source.registerObject(vaBase[i]);
		}
		for (int i = 0; i < vmNum; i++) {
			source.deregisterObject(vaBase[i]);
		}
	}

	private void genericCircularTest(int repoCount, long size, int[] fromIndex, int[] toIndex, long[] expectedTimings)
			throws NetworkException {
		SeedSyncer.resetCentral();
		final long bandwidth = 111111; // bytes/ms
		HashMap<String, Integer> latencyMap = new HashMap<>();
		for (int i = 0; i < repoCount; i++) {
			latencyMap.put("Repo" + i, 6);
		}
		Repository[] repos = new Repository[repoCount];
		for (int i = 0; i < repoCount; i++) {
			repos[i] = new Repository(111111111111111111L, "Repo" + i, bandwidth, bandwidth, bandwidth, latencyMap,
					defaultStorageTransitions, defaultNetworkTransitions);
			repos[i].setState(NetworkNode.State.RUNNING);
		}
		StorageObject[] sos = new StorageObject[fromIndex.length];
		final long startTiming = Timed.getFireCount();
		for (int i = 0; i < fromIndex.length; i++) {
			sos[i] = new StorageObject("Test" + i, size, true);
			repos[fromIndex[i]].registerObject(sos[i]);
			repos[fromIndex[i]].requestContentDelivery(sos[i].id, repos[toIndex[i]],
					new ConsumptionEventAssert(expectedTimings[i] + startTiming));
		}
		Timed.simulateUntilLastEvent();
		assertEquals(fromIndex.length, ConsumptionEventAssert.hits.size(), "Not enough transfers arrived");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void circularTransferTest() throws NetworkException {
		int trNum = 9;
		int repoCount = 3;
		int[] fromindex = new int[trNum];
		int[] toindex = new int[trNum];
		for (int i = 0; i < trNum; i++) {
			fromindex[i] = i % repoCount;
			toindex[i] = (i + 1) % repoCount;
		}
		genericCircularTest(repoCount, 1000000L, fromindex, toindex, new long[] { 16, 18, 36, 23, 8, 39, 8, 9, 9 });
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void secondCircularTransferTest() throws NetworkException {
		genericCircularTest(3, 10000000L, new int[] { 0, 1, 2, 2 }, new int[] { 1, 0, 1, 0 },
				new long[] { 103, 218, 269, 246 });
	}

	interface CancelAction {
		void doCancel(ResourceConsumption con);
	}

	private void genericCancelTransferTest(CancelAction ca) throws NetworkException {
		registerWithCheck(so);
		long targetStorage = target.getFreeStorageCapacity();
		final ResourceConsumption con = source.requestContentDelivery(so.id, target, new ConsumptionEventAssert() {
			@Override
			public void conComplete() {
				super.conComplete();
				fail("Should not receive a consumption complete event!");
			}
		});
		ca.doCancel(con);
		Timed.simulateUntilLastEvent();
		assertEquals(targetStorage,
				target.getFreeStorageCapacity(), "Should not have any storage occupied after a cancelled transfer");

	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void immediateCancelTransferTest() throws NetworkException {
		genericCancelTransferTest((ResourceConsumption con) -> {
			assertFalse(con.isRegistered(), "Should not have registered yet");
			con.cancel();
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void delayedCancelTransferTest() throws NetworkException {
		genericCancelTransferTest((final ResourceConsumption con) -> {
			try {
				new DeferredEvent(NetworkNode.checkConnectivity(target, source) + 1L) {
					@Override
					protected void eventAction() {
						assertTrue(con.isRegistered(), "By this time the registration should have happened");
						con.cancel();
					}
				};
			} catch (NetworkException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void cancelMemoryWriteOut() throws NetworkException {
		ConsumptionEventAssert cae = new ConsumptionEventAssert() {
			@Override
			public void conComplete() {
				super.conComplete();
				fail("Should not receive a consumption complete event!");
			}
		};
		ResourceConsumption con = source.storeInMemoryObject(so, cae);
		con.cancel();
		Timed.simulateUntilLastEvent();
		assertEquals(source.getMaxStorageCapacity(),
				source.getFreeStorageCapacity(), "Should not have any occupied storage");
		assertTrue(cae.isCancelled(), "Should receive cancel event");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void cancelReadToMemory() throws NetworkException {
		ConsumptionEventAssert cae = new ConsumptionEventAssert() {
			@Override
			public void conComplete() {
				super.conComplete();
				fail("Should not receive a consumption complete event!");
			}
		};
		registerWithCheck(so);
		ResourceConsumption con = source.fetchObjectToMemory(so, cae);
		con.cancel();
		Timed.simulateUntilLastEvent();
		assertTrue(cae.isCancelled(), "Should receive cancel event");
	}
}
