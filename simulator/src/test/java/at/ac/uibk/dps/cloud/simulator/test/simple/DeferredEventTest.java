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
 *  (C) Copyright 2017, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package at.ac.uibk.dps.cloud.simulator.test.simple;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

import static org.junit.jupiter.api.Assertions.*;

import at.ac.uibk.dps.cloud.simulator.test.TestFoundation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DeferredEventTest extends TestFoundation {
	final static int limit = 5000;

	final static int[] delayDistribution = new int[limit];

	public static class DeferredTester extends DeferredEvent {
		public boolean eventFired;

		public DeferredTester(final long delay) {
			super(delay);
		}

		@Override
		protected void eventAction() {
			eventFired = true;
		}
	}

	@BeforeAll
	public static void resetSimulation() {
		new DeferredTester(0);
		for (int i = 0; i < limit; i++) {
			delayDistribution[i] = SeedSyncer.centralRnd.nextInt(limit) + 10;
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void eventFireTest() {
		DeferredTester dt = new DeferredTester(10);
		Timed.simulateUntilLastEvent();
		assertTrue( dt.eventFired,"Deferred event was not received");
		assertFalse( dt.isCancelled(),"Deferred event was cancelled unexpectedly");
		dt.cancel();
		assertFalse( dt.isCancelled(),"Deferred event was cancelled unexpectedly");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void eventCancelTest() {
		DeferredTester dt = new DeferredTester(10);
		dt.cancel();
		assertTrue( dt.isCancelled(),"Deferred event was not cancelled");
		long beforeTime = Timed.getFireCount();
		Timed.simulateUntilLastEvent();
		assertEquals( beforeTime, Timed.getFireCount(),"Deferred event cancellation have not succeeded");
		assertFalse( dt.eventFired,"Deferred event was received unexpectedly");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void immediateFireTest() {
		assertTrue( new DeferredTester(0).eventFired,"Deferred event was not received");
	}

	/**
	 * This test ensures that the timed loop remains performant! So the failure of
	 * this test is not the failure of the system but the failure of its
	 * performance!
	 */
	@Test
	@Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
	public void performanceTest() {
		final DeferredTester[] performer = new DeferredTester[limit];
		for (int i = 0; i < limit; i++) {
			performer[i] = new DeferredTester(delayDistribution[i]);
		}
		Timed.simulateUntilLastEvent();
		boolean fired = true;
		for (int i = 0; i < limit; i++) {
			fired &= performer[i].eventFired;
		}
		assertTrue( fired,"Not all events arrived");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void skipEventsEffects() {
		int baseTime = 15;
		new DeferredEvent(baseTime) {
			@Override
			protected void eventAction() {
				fail("We skipped through this event, thus we don't want to see it arrive!");
			}
		};
		Timed.skipEventsTill(baseTime + 5);
		final boolean[] arr = new boolean[1];
		arr[0] = false;
		new DeferredEvent(baseTime) {
			@Override
			protected void eventAction() {
				arr[0] = true;
			}
		};
		Timed.skipEventsTill(baseTime * 2 - 1);
		Timed.simulateUntilLastEvent();
		assertTrue( arr[0],"The second event should arrive");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	void deferActionArrives() {
		var arrival = new AtomicBoolean(false);
		DeferredEvent.deferAction(1,() -> arrival.set(true));
		assertFalse(arrival.get());
		Timed.simulateUntilLastEvent();
		assertTrue(arrival.get());
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	void deferActionCancellable() {
		var arrival = new AtomicBoolean(false);
		var eventHandler = DeferredEvent.deferAction(1,() -> arrival.set(true));
		eventHandler.cancel();
		assertFalse(arrival.get());
		Timed.simulateUntilLastEvent();
		assertFalse(arrival.get());
	}


}
