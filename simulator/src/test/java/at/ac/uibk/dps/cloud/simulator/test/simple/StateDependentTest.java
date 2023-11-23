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
 *  (C) Copyright 2016, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package at.ac.uibk.dps.cloud.simulator.test.simple;

import at.ac.uibk.dps.cloud.simulator.test.TestFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

public class StateDependentTest extends TestFoundation {
	interface MyHandler {
		void handle(String payload);
	}

	StateDependentEventHandler<MyHandler, String> sdeh;

	@BeforeEach
	public void setupSDEH() {
		sdeh = new StateDependentEventHandler<MyHandler, String>(
				(MyHandler onObject, String data) -> onObject.handle(data));
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void basicOperation() {
		final boolean[] handled = new boolean[1];
		handled[0] = false;
		MyHandler listener = new MyHandler() {
			@Override
			public void handle(String data) {
				handled[0] = true;
			}
		};
		sdeh.subscribeToEvents(listener);
		assertFalse(handled[0], "Should not have any handling events delivered.");
		sdeh.notifyListeners(null);
		assertTrue( handled[0],"We should receive our event by now.");
		handled[0] = false;
		sdeh.unsubscribeFromEvents(listener);
		sdeh.notifyListeners(null);
		assertFalse( handled[0],"Should not have any further handling events delivered.");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void removalDuringNestedNotification() {
		final boolean[] handled = new boolean[2];
		handled[0] = false;
		handled[1] = false;
		MyHandler listener = new MyHandler() {
			@Override
			public void handle(String data) {
				if (!handled[0]) {
					handled[0] = true;
					sdeh.unsubscribeFromEvents(this);
					sdeh.notifyListeners(null);
				}
			}
		};
		MyHandler easyListener = (String data) -> handled[1] = true;
		sdeh.subscribeToEvents(listener);
		sdeh.subscribeToEvents(easyListener);
		sdeh.notifyListeners(null);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void nestedAdditionWithRepeatedNotify() {
		final int[] counters = new int[2];
		final MyHandler easyListener = new MyHandler() {
			@Override
			public void handle(String data) {
				counters[0]++;
			}
		};
		MyHandler listener = new MyHandler() {
			@Override
			public void handle(String data) {
				counters[1]++;
				sdeh.subscribeToEvents(easyListener);
			}
		};
		sdeh.subscribeToEvents(listener);
		sdeh.notifyListeners(null);
		sdeh.notifyListeners(null);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void nestedCounterNotification() {
		final int maxCount = 10;
		final int[] count = new int[1];
		count[0] = 0;
		MyHandler listener = new MyHandler() {
			@Override
			public void handle(String data) {
				if (count[0] < maxCount) {
					count[0]++;
					sdeh.notifyListeners(null);
				}
			}
		};
		sdeh.subscribeToEvents(listener);
		sdeh.notifyListeners(null);
		assertEquals( maxCount, count[0],"Should receive multiple handling events");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void payloadTest() {
		final int textBase = 10;
		final boolean[] happened = new boolean[1];
		happened[0] = false;
		MyHandler listener = new MyHandler() {
			@Override
			public void handle(String data) {
				happened[0] = true;
				assertEquals( textBase, Integer.parseInt(data),"Does not receive the correct payload");
			}
		};
		sdeh.subscribeToEvents(listener);
		sdeh.notifyListeners(Integer.toString(textBase));
		assertTrue(happened[0]);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void noApparentChangeDuringNesting() {
		final int[] handleCount = new int[1];
		MyHandler listener = new MyHandler() {
			@Override
			public void handle(String data) {
				handleCount[0]++;
				sdeh.unsubscribeFromEvents(this);
				sdeh.subscribeToEvents(this);
				if (handleCount[0] == 1) {
					new DeferredEvent(1) {
						@Override
						protected void eventAction() {
							// Second notification
							sdeh.notifyListeners(null);
						}
					};
				}
			}
		};
		sdeh.subscribeToEvents(listener);
		// First notification
		sdeh.notifyListeners(null);
		Timed.simulateUntilLastEvent();
		assertEquals( 2, handleCount[0],"There should be two notifications coming");
	}
}
