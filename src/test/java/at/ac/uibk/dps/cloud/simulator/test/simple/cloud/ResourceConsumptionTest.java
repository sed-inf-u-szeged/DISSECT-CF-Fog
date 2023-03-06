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

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinConsumer;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinProvider;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


public class ResourceConsumptionTest extends ConsumptionEventFoundation {
	public static final double processingTasklen = 1;
	public static final double permsProcessing = processingTasklen / aSecond;
	MaxMinProvider offer;
	MaxMinConsumer utilize;
	protected ResourceConsumption con;

	public ResourceConsumption createAUnitConsumption(final ConsumptionEvent ce) {
		return new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, utilize, offer,
				ce == null ? new ConsumptionEventAssert() : ce);
	}

	@BeforeEach
	public void setupConsumption() {
		offer = new MaxMinProvider(permsProcessing);
		utilize = new MaxMinConsumer(permsProcessing);
		con = createAUnitConsumption(null);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void testConsumption() {
		con.setConsumer(utilize);
		con.setProvider(offer);
		assertEquals(utilize, con.getConsumer(), "Consumer mismatch");
		assertEquals(offer, con.getProvider(), "Provider mismatch");
		assertEquals(con.getHardLimit(), con.getProcessingLimit(), 0, "Processing limit mismatch");
		assertTrue(con.getRealLimit() <= con.getProcessingLimit(), "Real processing power should be always smaller than equal to the processing limit");
		con.registerConsumption();
		try {
			con.setConsumer(utilize);
			fail("Consumer change is not allowed after registration");
		} catch (IllegalStateException ex) {
			// Expected after registration
		}
		try {
			con.setProvider(offer);
			fail("Provider change is not allowed after registration");
		} catch (IllegalStateException ex) {
			// Expected after registration
		}
		assertFalse(con.registerConsumption(), "Second registration should not succeed");
		assertEquals(processingTasklen, con.getUnProcessed(), "Already consumed");
		assertTrue(con.toString().contains("" + processingTasklen), "Unprocessed quantity is not in string output");
		Timed.simulateUntilLastEvent();
		assertEquals(0, con.getUnProcessed(), 0, "Not consumed");
		con = new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, null, offer,
				new ConsumptionEventAssert());
		con.registerConsumption();
		assertFalse(con.isRegistered(), "Should not be possible to register without consumer");
		con = new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, utilize, null,
				new ConsumptionEventAssert());
		con.registerConsumption();
		assertFalse(con.isRegistered(), "Should not be possible to register without provider");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void testNullEvent() {
		assertThrows(IllegalStateException.class, () -> {
			// Below we should not receive a null pointer
			con = new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, utilize, offer, null);
			fail("Should not reach tis point because we asked for a consumption with a null event");
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void zeroLenConsumption() {
		ConsumptionEventAdapter ce = new ConsumptionEventAssert();
		con = new ResourceConsumption(0, ResourceConsumption.unlimitedProcessing, utilize, offer, ce);
		assertFalse(ce.isCompleted(), "Consumption should not be complete before registration");
		assertFalse(ce.isCancelled(), "Consumption should not be cancelled before registration");
		con.registerConsumption();
		assertTrue(ce.isCompleted(), "Consumption should be complete immediately after registration");
		assertFalse(ce.isCancelled(), "Consumption should not be cancelled before registration");
	}

	protected double preSuspendPhase() {
		assertTrue(con.registerConsumption(), "Should be able to register an unprocessed consumption");
		double before = con.getUnProcessed();
		Timed.simulateUntil(Timed.getFireCount() + 500);
		return before;
	}

	protected void pastSuspendChecks(double before) {
		double after = con.getUnProcessed();
		assertTrue(after != before, "Should already have some consumption recorded");
		assertTrue(after != 0, "Consumption should be incomplete");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void suspendConsumption() {
		con.suspend();
		double before = preSuspendPhase();
		con.suspend();
		Timed.fire();
		pastSuspendChecks(before);
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
		assertEquals(0, con.getUnProcessed(), 0, "Not consumed the necessary amount");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void cancelConsumption() {
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		con = createAUnitConsumption(cae);
		double before = preSuspendPhase();
		con.cancel();
		Timed.fire();
		pastSuspendChecks(before);
		assertFalse(con.registerConsumption(), "Should not be possible to register after cancellation");
		long timeBefore = Timed.getFireCount();
		Timed.simulateUntilLastEvent();
		assertEquals(timeBefore - 1, cae.getArrivedAt(), "Cancellation arrived at improper time");
		assertTrue(cae.getArrivedAt() + 1 >= Timed.getFireCount(), "There should be no events after cancellation");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void cancelUnregistered() {
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		con = createAUnitConsumption(cae);
		con.cancel();
		assertTrue(cae.isCancelled(), "Should receive cancellation even if not registered");
	}

	@Disabled
	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void cancellationJustBeforeCompletion() {
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		long before = Timed.getFireCount();
		con = createAUnitConsumption(cae);
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
		long len = cae.getArrivedAt() - before;
		con = createAUnitConsumption(null);
		con.registerConsumption();
		ConsumptionEventAssert.resetHits();
		Timed.simulateUntil(Timed.getFireCount() + len - 1);
		assertTrue(ConsumptionEventAssert.hits.isEmpty(), "It should not yet arrive");
		Timed.fire();
		con.cancel();
		Timed.fire();
		// Ambiguous behavior! This needs to be thinked through
		assertFalse(ConsumptionEventAssert.hits.isEmpty(), "It should arrive by now");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void failedRegistrationTest() {
		assertFalse(new ResourceConsumption(100000, ResourceConsumption.unlimitedProcessing, new MaxMinProvider(1) {
					protected boolean isAcceptableConsumption(ResourceConsumption con) {
						return false;
					}
				}, utilize, new ConsumptionEventAssert()).registerConsumption(), "Provider should not accept this consumption");
		assertFalse(new ResourceConsumption(100000, ResourceConsumption.unlimitedProcessing, new MaxMinConsumer(1) {
					@Override
					protected boolean isAcceptableConsumption(ResourceConsumption con) {
						return false;
					}
				}, utilize, new ConsumptionEventAssert()).registerConsumption(), "Consumer should not accept this consumption");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void testLessPerformantProvider() {
		offer = new MaxMinProvider(permsProcessing / 2);
		con = new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, utilize, offer,
				new ConsumptionEventAssert(
						Timed.getFireCount() + (long) (processingTasklen / offer.getPerTickProcessingPower())));
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void testNegativeRC() {
		assertThrows(IllegalArgumentException.class, () -> {
			con = new ResourceConsumption(-1, ResourceConsumption.unlimitedProcessing, utilize, offer,
				new ConsumptionEventAssert());
			con.registerConsumption();
			Timed.simulateUntilLastEvent();
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void testSuspendRightBeforeCompletionNotification() {
		long before = Timed.getFireCount();
		// benchmark consumption
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
		// needed to determine length of benchmark
		long after = ConsumptionEventAssert.hits.get(0);
		// The tested consumption
		con = createAUnitConsumption(null);
		con.registerConsumption();
		new DeferredEvent(after - before) {
			@Override
			protected void eventAction() {
				// This should come just on the same time the consumption
				// finishes = allowing to test if RS handles these situations
				// correctly
				con.suspend();
			}
		};
		Timed.fire();
		Timed.simulateUntilLastEvent();
		// Resume right after the suspend
		con.registerConsumption();
		// By this time we should not receive two notifications...
		Timed.simulateUntilLastEvent();
	}
}
