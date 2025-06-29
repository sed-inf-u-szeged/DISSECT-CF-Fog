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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.statenotifications;

import org.apache.commons.lang3.tuple.Pair;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.PowerBehaviorChangeListener;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceSpreader;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.SingleNotificationHandler;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;

/**
 * implements a notification handler for sending out notifications about power
 * state changes in resource spreaders
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2015"
 *
 */

public class PowerStateChangeNotificationHandler
		implements SingleNotificationHandler<PowerBehaviorChangeListener, Pair<ResourceSpreader, PowerState>> {

	/**
	 * the single object that will handle all notification operations on the same
	 * way
	 */
	private static final PowerStateChangeNotificationHandler handlerSingleton = new PowerStateChangeNotificationHandler();

	/**
	 * disables the instantiation of the handler, so we really just have a single
	 * instance for all handling operations
	 */

	private PowerStateChangeNotificationHandler() {
	}

	/**
	 * gets the event handler that will manage the notification subscriptions for
	 * the particular resource spreader object that asked for the handler.
	 * 
	 * @return the event handler
	 */
	public static StateDependentEventHandler<PowerBehaviorChangeListener, Pair<ResourceSpreader, PowerState>> getHandlerInstance() {
		return new StateDependentEventHandler<>(handlerSingleton);
	}

	/**
	 * The event handling mechanism for power state change notifications about
	 * resource spreaders
	 * 
	 * @param onObject         The listener to send the event to
	 * @param newPowerBehavior a data pair containing the resource spreader that
	 *                         changed its state and the power state that the
	 *                         spreader just switches to.
	 */
	@Override
	public void sendNotification(final PowerBehaviorChangeListener onObject,
			final Pair<ResourceSpreader, PowerState> newPowerBehavior) {
		onObject.behaviorChanged(newPowerBehavior.getLeft(), newPowerBehavior.getRight());
	}
}
