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

import org.apache.commons.lang3.tuple.Triple;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.StateChange;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.SingleNotificationHandler;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;

/**
 * implements a notification handler for sending out notifications about VM
 * state changes
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
 *
 */
public class VMStateChangeNotificationHandler
		implements SingleNotificationHandler<StateChange, Triple<VirtualMachine, State, State>> {

	/**
	 * the singleton notification sender object that will send out all
	 * notifications about a VM state changes on a uniform way
	 */
	private static final VMStateChangeNotificationHandler handlerSingleton = new VMStateChangeNotificationHandler();

	/**
	 * disables the instantiation of the handler, so we really just have a single
	 * instance for all handling operations
	 */
	private VMStateChangeNotificationHandler() {

	}

	/**
	 * gets the event handler that will manage the subscriptions for the
	 * particular VM object that asked for the handler. One should be requested for
	 * every VM that expects to send out state change notifications.
	 * 
	 * @return the event handler
	 */
	public static StateDependentEventHandler<StateChange, Triple<VirtualMachine, State, State>> getHandlerInstance() {
		return new StateDependentEventHandler<>(handlerSingleton);
	}

	/**
	 * The event handling mechanism for VM state change notifications
	 * 
	 * @param onObject
	 *            The listener to send the event to
	 * @param stateData
	 *            a data triplet containing the VM and its past and future
	 *            states.
	 */
	@Override
	public void sendNotification(final StateChange onObject, Triple<VirtualMachine, State, State> stateData) {
		onObject.stateChanged(stateData.getLeft(), stateData.getMiddle(), stateData.getRight());
	}
}