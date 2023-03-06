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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

/**
 * This class offers a scheduler implementation that practically eliminates the
 * use of the queue. If all PMs are running in the infrastructure then this
 * scheduler rejects the VMs that none of the PMs can host immediately. The VMs
 * are rejected by marking them with the NONSERVABLE state.
 * 
 * The VM placement logic follows the first fit scheduler's approach.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 * 
 */
public class NonQueueingScheduler extends FirstFitScheduler {

	/**
	 * Passes the IaaSService further to its super class.
	 * 
	 * @param parent
	 *            the IaaS Service which this NonQueueingScheduler operates on
	 */

	public NonQueueingScheduler(IaaSService parent) {
		super(parent);
	}

	/**
	 * The actual scheduling technique that is invoking the first fit scheduler
	 * as many times as many times it gets stuck with a VM request in the queue.
	 * Between the first fit scheduling requests this scheduler always removes
	 * the unschedulable head of the queue. For the removed requests the VMs are
	 * transformed to their NONSERVABLE state.
	 */
	@Override
	protected ConstantConstraints scheduleQueued() {
		while (true) {
			super.scheduleQueued();
			if (queue.size() != 0) {
				if (parent.runningMachines.size() == parent.machines.size()) {
					QueueingData request;
					if ((request = manageQueueRemoval()) != null) {
						for (final VirtualMachine vm : request.queuedVMs) {
							vm.setNonservable();
						}
					} else {
						break;
					}
				} else {
					break;
				}
			} else {
				break;
			}
		}
		return ConstantConstraints.noResources;
	}
}
