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

package hu.mta.sztaki.lpds.cloud.simulator;

import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.util.ArrayList;

/**
 * Offers the event handling mechanism for non-recurring but time dependent
 * events. Implementors should provide an implementation of the event action
 * function which will be called once the specified ticks pass.
 * 
 * Aggregates the events that should happen at a single time instance. This
 * approach allows that only one Timed event is registered for a bunch of
 * non-recurring events.
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2015"
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013"
 */
public abstract class DeferredEvent {

	/**
	 * All deferred events that are due in the future are listed here.
	 * 
	 * The map is indexed by expected event arrivals. The stored objects in the map
	 * are MutablePairs where the left item of the pair is the length of the right
	 * item (which is actually the list of events that should be delivered at the
	 * particular time instance identified by the key of the map).
	 */
	private static final LongObjectHashMap<AggregatedEventDispatcher> dispatchers = new LongObjectHashMap<>();

	/**
	 * handles the event aggregations, actual subscriptions to timed events and
	 * dispatches the events if Timed notifies for time instance at which the
	 * non-recurring events should be fired
	 * 
	 * Improves the performance of deferred events significantly if multiple events
	 * should occur at once
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2015"
	 *
	 */
	private static class AggregatedEventDispatcher extends Timed {
		private final ArrayList<DeferredEvent> simultaneouslyOccurringDEs = new ArrayList<>();
		private final long myEv;

		private AggregatedEventDispatcher(long event) {
			subscribe(event - Timed.getFireCount());
			myEv=event;
		}

		/**
		 * The actual event dispatcher. This function is called by Timed on the time
		 * instance when the first not yet dispatched deferred event is due.
		 * 
		 * <i>Note:</i> If multiple events must be delivered at a given time instance,
		 * then the order of the dispatched events are undefined.
		 */
		@Override
		public void tick(long fires) {
			for (DeferredEvent underDelivery : simultaneouslyOccurringDEs) {
				underDelivery.eventAction();
				underDelivery.received = true;
			}
			terminate();
		}

		@Override
		protected void skip() {
			super.skip();
			for (DeferredEvent simultaneouslyOccurringDE : simultaneouslyOccurringDEs) {
				simultaneouslyOccurringDE.cancelled = true;
			}
			simultaneouslyOccurringDEs.clear();
			terminate();
		}
		
		private void remove(DeferredEvent de) {
			simultaneouslyOccurringDEs.remove(de);
			de.cancelled=true;
			if(simultaneouslyOccurringDEs.isEmpty()) {
				terminate();
			}
		}
		
		private void terminate() {
			dispatchers.remove(myEv);
			unsubscribe();
		}
				
	}

	/**
	 * Shows if the event in question was cancelled by the user
	 */
	private boolean cancelled = false;
	/**
	 * Shows if the event was dispatched already
	 */
	private boolean received = false;
	/**
	 * The time instance at which this event should be delivered
	 */
	private final long eventArrival;

	/**
	 * Allows constructing objects that will receive an eventAction() call from
	 * Timed after delay ticks.
	 * 
	 * @param delay
	 *            the number of ticks that should pass before this deferred event
	 *            object's eventAction() will be called.
	 */
	public DeferredEvent(final long delay) {
		if (delay <= 0) {
			eventArrival = Timed.getFireCount();
			eventAction();
			received = true;
			return;
		}
		eventArrival = Timed.calcTimeJump(delay);
		AggregatedEventDispatcher aed=dispatchers.get(eventArrival);
		if(aed==null) {
			aed=new AggregatedEventDispatcher(eventArrival);
			dispatchers.put(eventArrival,aed);
		}
		aed.simultaneouslyOccurringDEs.add(this);
	}

	/**
	 * If the call for eventAction() is no longer necessary at the previously
	 * specified time then the user can cancel this call to arrive with this
	 * function.
	 * 
	 * Calling this function will have no effect on events that are already past
	 * due.
	 */
	public void cancel() {
		if (received)
			return;
		if (!cancelled) {
			dispatchers.get(eventArrival).remove(this);
		}
	}

	/**
	 * Allows to determine whether the actual event was cancelled already or not.
	 * 
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the event will not arrive in the future as it was
	 *         cancelled,
	 *         <li><i>false</i> otherwise
	 *         </ul>
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * When creating a deferred event, implement this function for the actual event
	 * handling mechanism of yours.
	 */
	protected abstract void eventAction();

	/**
	 * Allows the cleanup of all events registered with the aggregator.
	 * 
	 * WARNING: This is not supposed to be called by user code directly.
	 */
	static void reset() {
		dispatchers.clear();
	}

	/**
	 * A shortcut to create deferred events in the simulation
	 * @param distance The number of ticks before the deferred operation below needs to be called
	 * @param deferredAction The operation to be called once the time is up
	 * @return a wrapper around the deferred operation allowing its event to be cancelled
	 */
	public static DeferredEvent deferAction(long distance, Runnable deferredAction) {
		return new DeferredEvent(distance) {
			@Override
			protected void eventAction() {
				deferredAction.run();
			}
		};
	}
}
