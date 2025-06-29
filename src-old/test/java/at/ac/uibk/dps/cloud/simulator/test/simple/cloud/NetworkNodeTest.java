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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;


import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class NetworkNodeTest extends PMRelatedFoundation {
	// 1 tick is assumed 1ms
	public final static long inBW = 100000; // bytes/tick
	public final static long outBW = 100000; // bytes/tick
	public final static long diskBW = 50000; // bytes/tick
	public final static int targetlat = 3; // ticks
	public final static int sourcelat = 2; // ticks
	public final static String sourceName = "Source";
	public final static String targetName = "Target";
	public final static String thirdName = "Unconnected";
	NetworkNode source, target, third;
	static final long dataToBeSent = aSecond * inBW;
	static final long dataToBeStored = aSecond * diskBW / 2;

	public static HashMap<String, Integer> setupALatencyMap() {
		HashMap<String, Integer> lm = new HashMap<>();
		lm.put(sourceName, sourcelat);
		lm.put(targetName, targetlat);
		return lm;
	}

	@BeforeEach
	public void nodeSetup() throws NetworkException {
		HashMap<String, Integer> lm = setupALatencyMap();
		source = new NetworkNode(sourceName, inBW, outBW, diskBW, lm, defaultStorageTransitions,
				defaultNetworkTransitions);
		target = new NetworkNode(targetName, inBW, outBW, diskBW, lm, defaultStorageTransitions,
				defaultNetworkTransitions);
		third = new NetworkNode(thirdName, inBW, outBW, diskBW, lm, defaultStorageTransitions,
				defaultNetworkTransitions);
		source.setState(NetworkNode.State.RUNNING);
		target.setState(NetworkNode.State.RUNNING);
		third.setState(NetworkNode.State.RUNNING);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void checkConstruction() {
		assertTrue(source.toString().contains(sourceName), "Node toString does not contain node name:");
		assertEquals(inBW, source.getInputbw(), "Unexpected incoming bandwidth");
		assertEquals(outBW, source.getOutputbw(), "Unexpected outgoing bandwidth");
		assertEquals(diskBW, source.getDiskbw(), "Unexpected disk bandwidth");
		assertEquals(sourceName, source.getName(), "Unexpected name");
		assertEquals(0,
				source.inbws.getTotalProcessed() + source.outbws.getTotalProcessed(), 0, "Already used some bandwidth without requesting transfers");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void checkConnectivity() {
		// Node source tests:
		try {
			assertEquals(0,
					NetworkNode.checkConnectivity(source, source), "Internal connectivity for node source is not without latency");
		} catch (NetworkException ex) {
			fail("Internal connectivity of node source should always work");
		}
		try {
			assertEquals(targetlat, NetworkNode.checkConnectivity(source, target), "External connectivity between node spurce and target is with an unexpected latency");
		} catch (NetworkException ex) {
			fail("There should be no connectivity issues between node source and target");
		}
		try {
			NetworkNode.checkConnectivity(source, third);
			fail("Node 1 should not be able to connect to node third");
		} catch (NetworkException ex) {
			// Expected behavior
		}

		// Node target tests:
		try {
			assertEquals(sourcelat, NetworkNode.checkConnectivity(target, source), "External connectivity between node target and source is with an unexpected latency");
		} catch (NetworkException ex) {
			fail("There should be no connectivity issues between node target and source");
		}
		try {
			assertEquals(0,
					NetworkNode.checkConnectivity(target, target), "Internal connectivity for node target is not without latency");
		} catch (NetworkException ex) {
			fail("Internal connectivity of node target should always work");
		}
		try {
			NetworkNode.checkConnectivity(target, third);
			fail("Node target should not be able to connect to node third");
		} catch (NetworkException ex) {
			// Expected behavior
		}

		// Node third tests:
		try {
			assertEquals(sourcelat, NetworkNode.checkConnectivity(third, source), "External connectivity between node third and source is with an unexpected latency");
		} catch (NetworkException ex) {
			fail("There should be no connectivity issues between node third and source");
		}
		try {
			assertEquals(targetlat, NetworkNode.checkConnectivity(third, target), "External connectivity between node third and target is with an unexpected latency");
		} catch (NetworkException ex) {
			fail("There should be no connectivity issues between node third and target");
		}
		try {
			assertEquals(0,
					NetworkNode.checkConnectivity(third, third), "Internal connectivity for node third is not without latency");
		} catch (NetworkException ex) {
			fail("Internal connectivity of node third should always work");
		}
	}

	private void setupTransfer(final long len, final NetworkNode source, final NetworkNode target,
			final long expectedDelay) throws NetworkException {
		NetworkNode.initTransfer(len, ResourceConsumption.unlimitedProcessing, source, target,
				new ConsumptionEventAssert(Timed.getFireCount() + expectedDelay, true));
	}

	private void simulateThenExpectEventNum(int eventNum) {
		Timed.simulateUntilLastEvent();
		assertEquals(eventNum, ConsumptionEventAssert.hits.size(), "Not enough consumption events received");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void interNodeTransferTest() throws NetworkException {
		setupTransfer(dataToBeSent, source, target, aSecond + targetlat);
		simulateThenExpectEventNum(1);
		assertEquals(0,
				source.inbws.getTotalProcessed(), 0, "It is not expected to have incoming transfers in source");
		assertEquals(0,
				target.outbws.getTotalProcessed(), 0, "It is not expected to have outgoing transfers in target");
		assertEquals(dataToBeSent,
				source.outbws.getTotalProcessed(), 0, "Outgoing transfers on source are reported badly");
		assertEquals(dataToBeSent,
				target.inbws.getTotalProcessed(), 0, "Incoming transfers on target are reported badly");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void intraNodeTransferTest() throws NetworkException {
		setupTransfer(dataToBeStored, source, source, aSecond);
		simulateThenExpectEventNum(1);
		assertEquals(0,
				source.inbws.getTotalProcessed(), 0, "It is not expected to have network transfers within the node");
		assertEquals(0,
				source.outbws.getTotalProcessed(), 0, "It is not expected to have network transfers within the node");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void noInterferenceTest() throws NetworkException {
		setupTransfer(dataToBeSent, source, target, aSecond + targetlat);
		setupTransfer(dataToBeSent, target, source, aSecond + sourcelat);
		setupTransfer(dataToBeStored, source, source, aSecond);
		simulateThenExpectEventNum(3);
		assertEquals(dataToBeSent,
				source.inbws.getTotalProcessed(), 0, "Incoming transfers on source are reported badly");
		assertEquals(dataToBeSent,
				source.outbws.getTotalProcessed(), 0, "Outgoing transfers on source are reported badly");
		assertEquals(dataToBeSent,
				target.inbws.getTotalProcessed(), 0, "Incoming transfers on target are reported badly");
		assertEquals(dataToBeSent,
				target.outbws.getTotalProcessed(), 0, "Outgoing transfers on target are reported badly");
	}

	/**
	 * Expected simulation behavior:
	 * <ul>
	 * <li>Transfer1: lat(targetlat ms), transfer(300ms), transfer(1400ms) complete.
	 * <li>Transfer2: delay(300ms) lat(targetlat ms), transfer(1400ms), transfer
	 * (300ms) complete
	 * </ul>
	 */
	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void interferenceTest() throws NetworkException {
		final int offset = 300;
		setupTransfer(dataToBeSent, source, target, 2 * aSecond - offset + targetlat);
		Timed.simulateUntil(Timed.getFireCount() + 300);
		setupTransfer(dataToBeSent, source, target, 2 * aSecond - offset + targetlat);
		simulateThenExpectEventNum(2);
		assertEquals(2 * dataToBeSent,
				source.outbws.getTotalProcessed(), 0, "Outgoing transfers on source are reported badly");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void midSimulationRxTest() throws NetworkException {
		setupTransfer(dataToBeSent, source, target, aSecond + targetlat);
		Timed.simulateUntil(Timed.getFireCount() + targetlat);
		double startingPoint = source.outbws.getTotalProcessed();
		do {
			Timed.fire();
			final double current = source.outbws.getTotalProcessed();
			if (startingPoint == current && ConsumptionEventAssert.hits.isEmpty()) {
				fail("Output sent bytes should always increase after a timing event " + Timed.getFireCount());
			}
			startingPoint = current;
		} while (ConsumptionEventAssert.hits.isEmpty());
		assertEquals(dataToBeSent,
				source.outbws.getTotalProcessed(), 0, "The final outgoing transfer amount is reported incorrectly");
	}
}
