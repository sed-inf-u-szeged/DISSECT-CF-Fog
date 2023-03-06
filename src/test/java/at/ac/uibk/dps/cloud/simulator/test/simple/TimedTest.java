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

package at.ac.uibk.dps.cloud.simulator.test.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import at.ac.uibk.dps.cloud.simulator.test.TestFoundation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

public class TimedTest extends TestFoundation {
	final static long setFrequency = 20;
	final static long expectedFires = 10;

	public class SingleFire extends Timed {
		public long expectedFire;
		public int myfires = 0;

		public void cancel() {
			unsubscribe();
		}

		public SingleFire() {
			subscribe(setFrequency);
			calcExpectedFire(setFrequency);
		}

		protected void doCancel() {
			cancel();
		}

		public void changeFreq(long newFreq) {
			updateFrequency(newFreq);
			calcExpectedFire(newFreq);
		}

		public void calcExpectedFire(long freq) {
			expectedFire = Timed.getFireCount() + freq;
		}

		@Override
		public void tick(final long fires) {
			assertEquals(expectedFire, fires,"Timed event on the wrong time");
			myfires++;
			doCancel();
		}
	}

	public class RepeatedFire extends SingleFire {
		public final long maxTime;

		public RepeatedFire() {
			super();
			maxTime = expectedFire + (expectedFires - 1) * setFrequency;
		}

		protected void doCancel() {

		}

		@Override
		public void tick(long fires) {
			super.tick(fires);
			calcExpectedFire(setFrequency);
			if (maxTime == fires) {
				unsubscribe();
			}
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void singleEventFire() {
		SingleFire fire = new SingleFire();
		assertTrue(fire.toString().contains("" + fire.expectedFire),"ToString does not detail next event ");
		assertEquals(setFrequency, fire.nextEventDistance(),"Event distance is not correct");
		assertTrue(fire.isSubscribed(),"Should be subscribed");
		Timed.simulateUntilLastEvent();
		assertEquals(fire.myfires, 1,"Timed event did not arrive");
		assertEquals(setFrequency, fire.getFrequency(),"Timed frequency is not the one expected");
		assertEquals(Long.MAX_VALUE, fire.nextEventDistance(),"Event distance is not correct");
		assertTrue(!fire.isSubscribed(),"Should not be subscribed");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void repeatedEventFire() {
		RepeatedFire fire = new RepeatedFire();
		Timed.simulateUntilLastEvent();
		assertEquals(expectedFires, fire.myfires,"Not enough timed events");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void overLappingFires() {
		final ArrayList<SingleFire> sf = new ArrayList<SingleFire>();
		RepeatedFire fire = new RepeatedFire() {
			@Override
			public void tick(long fires) {
				super.tick(fires);
				if (myfires == 2) {
					sf.add(new SingleFire());
				}
			}
		};
		Timed.simulateUntilLastEvent();
		assertEquals(expectedFires, fire.myfires,"Not enough timed events");
		assertEquals(sf.get(0).myfires, 1,"Overlapped timed event did not arrive");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void overLappingRepeatedFires() {
		RepeatedFire[] fireobjects = new RepeatedFire[10];
		for (int i = 0; i < fireobjects.length; i++) {
			fireobjects[i] = new RepeatedFire();
			Timed.fire();
		}
		Timed.simulateUntilLastEvent();
		for (int i = 0; i < fireobjects.length; i++) {
			assertEquals(expectedFires, fireobjects[i].myfires,"Not enough timed events");
		}
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void eventSkipper() {
		RepeatedFire fire = new RepeatedFire();
		long reducedFires = expectedFires - 3;
		Timed.skipEventsTill(fire.maxTime - reducedFires * setFrequency);
		fire.expectedFire = fire.getNextEvent();
		Timed.simulateUntilLastEvent();
		assertEquals(reducedFires, fire.myfires,"Not the reduced amount of fires arrived");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void eventJumper() {
		SingleFire fire = new SingleFire();
		long jump = Timed.jumpTime(setFrequency - 10);
		assertEquals(0, jump,"Jump should have had success");
		Timed.simulateUntilLastEvent();
		assertTrue(fire.myfires == 1,"Timed event did not arrive");
		long timebefore = Timed.getFireCount();
		long expectedAfter = timebefore + setFrequency;
		jump = Timed.jumpTime(setFrequency);
		assertEquals(0, jump,"Jump should have had success");
		assertEquals(expectedAfter, Timed.getFireCount(),"Time after jump is not correct");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void prematureStopper() {
		RepeatedFire fire = new RepeatedFire();
		Timed.simulateUntil(Timed.getFireCount() + (expectedFires - 3) * setFrequency);
		assertEquals(7, fire.myfires, "Not the reduced amount of fires arrived");
		Timed.simulateUntil(Long.MAX_VALUE);
		assertEquals(expectedFires, fire.myfires,"Not all events arrived");
		assertTrue(Timed.getFireCount() == fire.maxTime + 1,"The simulation finshed way into the future");
		SingleFire sf = new SingleFire();
		long expectedTime = Timed.getFireCount() + 2;
		Timed.simulateUntil(expectedTime);
		assertTrue(sf.myfires == 0,"Fired events that should not arrive yet");
		assertEquals(expectedTime, Timed.getFireCount(),"The simulation did not finish at the specified time");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void multiSubscribe() {
		RepeatedFire fire = new RepeatedFire() {
			@Override
			public void tick(long fires) {
				assertFalse(subscribe(3),"Unexpected success of subscription");
				super.tick(fires);
				if (maxTime == fires) {
					assertFalse(unsubscribe(),"Unsubscribe should be unsuccessful");
				}
			}
		};
		Timed.simulateUntilLastEvent();
		assertEquals(expectedFires, fire.myfires,"Not enough timed events");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void unSubscribeWhileQueued() {
		RepeatedFire fire = new RepeatedFire();
		long reduction = 3;
		long reducedFires = expectedFires - reduction;
		while (Timed.getFireCount() < fire.maxTime - reducedFires * setFrequency) {
			Timed.jumpTime(Long.MAX_VALUE);
			Timed.fire();
		}
		assertEquals(reduction, fire.myfires,"Incorrect number of timed events before cancel");
		fire.cancel();
		Timed.simulateUntilLastEvent();
		assertEquals(reduction, fire.myfires,"Incorrect number of timed events after cancel");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void freqUpdater() {
		final long changedFreq = 15;

		RepeatedFire fire = new RepeatedFire() {
			@Override
			public void tick(long fires) {
				super.tick(fires);
				if (isSubscribed()) {
					expectedFire = updateFrequency(changedFreq);
				}
			}
		};
		long adjustedFires = 1 + ((fire.maxTime - setFrequency) - Timed.getFireCount()) / changedFreq;
		Timed.simulateUntilLastEvent();
		assertEquals(adjustedFires, fire.myfires,"Not enough timed events");

		SingleFire f = new SingleFire();
		Timed.fire();
		f.changeFreq(changedFreq);
		Timed.fire();
		f.changeFreq(changedFreq - 1);
		Timed.simulateUntilLastEvent();
		assertTrue(f.myfires == 1,"Frequency change unsuccessful if not changed during the tick function");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void negativeFreqTester() {
		assertThrows(IllegalStateException.class, () -> {
					class NFT extends Timed {
						public NFT() {
							subscribe(-1);
						}

						@Override
						public void tick(long fires) {
							fail("This event should never come!");
						}
					}
					new NFT();
					Timed.simulateUntilLastEvent();
					fail("We should never reach the end of this test!");
				});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void zeroFreqTester() {
		SingleFire f = new SingleFire();
		f.changeFreq(0);
		Timed.fire();
		assertEquals(1, f.myfires, "Even a zero length event should arrive");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void hugeFreqTester() {
		assertThrows(IllegalStateException.class, () -> {
			SingleFire f = new SingleFire();
			f.changeFreq(Long.MAX_VALUE);
		});
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void updateFreqWhileUnsubscribed() {
		SingleFire f = new SingleFire();
		f.cancel();
		assertFalse(f.isSubscribed());
		f.changeFreq(setFrequency);
		assertTrue(f.isSubscribed(),"Should be subscribed just as if we did not cancel the fire");
		Timed.simulateUntilLastEvent();
		assertEquals(1, f.myfires, "Should receive the event even after cancel if an update is done afterwards");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void emptyTimedlistTests() {
		assertEquals(Timed.getNextFire(), -1);
		Timed.fire();
		assertEquals(1, Timed.getFireCount());
		Timed.jumpTime(50);
		assertEquals(51, Timed.getFireCount());
		Timed.skipEventsTill(500);
		assertEquals(500, Timed.getFireCount());
		Timed.simulateUntilLastEvent();
		assertEquals(500, Timed.getFireCount());
		Timed.simulateUntil(1000);
		assertEquals(500, Timed.getFireCount());
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void zeroFrequencySkipper() {
		SingleFire sf = new SingleFire();
		sf.changeFreq(0);
		Timed.simulateUntilLastEvent();
		assertEquals(1, sf.myfires, "Should fire the event only once");
		sf.changeFreq(0); // resubscription
		final long targetTime=1000;
		Timed.skipEventsTill(targetTime); // skipping current events
		sf.expectedFire=targetTime;
		Timed.simulateUntilLastEvent();
		assertEquals(2, sf.myfires, "Should receive the second fire after the skip events complete");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void negativeTimeIncrementStopper() {
		Timed.skipEventsTill(100);
		assertEquals(100, Timed.getFireCount(), "Should allow positive increments");
		Timed.skipEventsTill(10);
		assertEquals(100, Timed.getFireCount(), "Should not allow negative time jumps");
	}
}
