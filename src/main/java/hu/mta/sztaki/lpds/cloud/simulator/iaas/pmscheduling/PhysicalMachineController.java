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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;

/**
 * This class contains the main interface for the schedulers of Physical machine
 * states. Although the interface is rather simplistic, its powers lie in the
 * possible events that could come because the returned objects of the
 * interfaces will be used for subscribing to various events.
 * 
 * @author 
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 * 
 */
public abstract class PhysicalMachineController {
	/**
	 * The Infrastructure service that will have the physical machines to be
	 * controlled and overseen by the particular implementations of this class
	 */
	protected final IaaSService parent;
	/**
	 * The event consumer object that will get notifications if the VM scheduler
	 * of the IaaS is under stress.
	 */
	protected final Scheduler.QueueingEvent queueingEvent;

	/**
	 * The main constructor which initiates the class and manages the
	 * subscriptions to the necessary basic events
	 * 
	 * @param parent
	 *            the iaas service the future object will need to oversee
	 */
	public PhysicalMachineController(IaaSService parent) {
		this.parent = parent;
		parent.subscribeToCapacityChanges(getHostRegEvent());
		queueingEvent = getQueueingEvent();
		parent.sched.subscribeQueueingEvents(queueingEvent);
	}

	/**
	 * Calling this function should return an object which knows what to do in
	 * case a new host registration/deregistration happens on the parent IaaS
	 * service.
	 * 
	 * @return the object to handle the registration events
	 */
	protected abstract VMManager.CapacityChangeEvent<PhysicalMachine> getHostRegEvent();

	/**
	 * Calling this function should return an object that knows the necessary
	 * actions to take when the IaaS's VM scheduler alarms us for overutilized
	 * infrastructure.
	 * 
	 * @return the object to handle the VM scheduling related events
	 */
	protected abstract Scheduler.QueueingEvent getQueueingEvent();

}
