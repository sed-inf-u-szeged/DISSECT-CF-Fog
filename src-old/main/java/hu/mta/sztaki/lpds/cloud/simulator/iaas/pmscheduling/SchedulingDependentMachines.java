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

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;

/**
 * A reactive PM controller that increases/decreases the powered on pm set on
 * demands of the vm scheduler. This controller implementation is always
 * changing the machine set size by one, thus it is not applicable in cases when
 * there are rapid demand changes on the infrastructure.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 *         Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class SchedulingDependentMachines extends PhysicalMachineController {

	/**
	 * this map lists all the currently controlled PMs and their controllers.
	 */
	private final HashMap<PhysicalMachine, CapacityChangeManager> capacityManagers = new HashMap<>();
	/**
	 * ensures that we only have a single machine switching on at a time and shows
	 * what is the actual machine that is switching on.
	 */
	PhysicalMachine currentlyStartingPM = null;

	/**
	 * Constructs the scheduler and passes the parent IaaSService to the superclass.
	 * 
	 * @param parent the IaaSService to serve
	 */
	public SchedulingDependentMachines(final IaaSService parent) {
		super(parent);
	}

	/**
	 * Defines to do the following when a new host is (de)registered to the parent
	 * IaaSService:
	 * <ul>
	 * <li>if the current event is a registration event then the function creates
	 * and locally registers a new capacity change manager for the newly registered
	 * pms
	 * <li>if the current event is a deregistration event then the function cancels
	 * the capacity management for all deregistered pms
	 * </ul>
	 */
	@Override
	protected VMManager.CapacityChangeEvent<PhysicalMachine> getHostRegEvent() {
		return (final ResourceConstraints newCapacity, final List<PhysicalMachine> alteredPMs) -> {
			Consumer<PhysicalMachine> pmaction;
			if (parent.isRegisteredHost(alteredPMs.get(0))) {
				// Management of capacity increase
				pmaction = pm -> capacityManagers.put(pm, new CapacityChangeManager(this, pm));
			} else {
				// Management of capacity decrease
				pmaction = pm -> capacityManagers.remove(pm).cancelEvents();
			}
			alteredPMs.forEach(pmaction);
		};
	}

	/**
	 * Defines to do the following when VM requests arrive:
	 * <ol>
	 * <li>check if there is already a PM under preparation (if there is one then
	 * does nothing)
	 * <li>if there are no PM that is currently prepared then starts to prepare one
	 * for accepting VM requests.
	 * </ol>
	 */
	@Override
	protected Scheduler.QueueingEvent getQueueingEvent() {
		return () -> {
			if (currentlyStartingPM == null || PhysicalMachine.State.RUNNING.equals(currentlyStartingPM.getState())) {
				// If there are no machines under their startup procedure,
				// or the currently started up machine is already running,
				// and we still receive the queueingstarted event
				turnOnAMachine();
			}
		};
	}

	/**
	 * switches on a not yet switched on machine from the parent IaaS's PM set. if
	 * there are no more machines in the IaaS that can be turned on then the calling
	 * of this function is ignored.
	 */
	protected void turnOnAMachine() {
		if (parent.runningMachines.size() != parent.machines.size()) {
			var thePm=parent.machines.stream().filter(pm -> PhysicalMachine.ToOfforOff.contains(pm.getState())).findFirst();
			thePm.ifPresent(pm -> { currentlyStartingPM=pm; pm.turnon(); });
		}
	}
}
