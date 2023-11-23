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

package hu.mta.sztaki.lpds.cloud.simulator.notifications;

/**
 * the handler for a particular kind of notification. the implementer of this
 * interface should be prepared to notify the interested party (T) that a state
 * change has happened and it should send the payload to the interested party.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
 *
 * @param <T>
 *            the kind of state change for which this handler is prepared to
 *            notify about.
 * @param <P>
 *            the kind of data to be passed on to the notified party
 * 
 */
public interface SingleNotificationHandler<T, P> {
	/**
	 * this function is called by the statedependenteventhandler class when a
	 * notification is needed for a particular kind of event.
	 * 
	 * @param onObject
	 *            the subscribed object that is expecting to receive the
	 *            notifications
	 * @param payload
	 *            the data to be sent alongside the notification
	 */
	void sendNotification(final T onObject, final P payload);
}
