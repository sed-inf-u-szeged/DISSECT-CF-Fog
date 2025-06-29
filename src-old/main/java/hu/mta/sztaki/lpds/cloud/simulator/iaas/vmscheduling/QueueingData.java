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

import java.util.Collections;
import java.util.Map;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

/**
 * The data stored about a single queued VM request.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 *         Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class QueueingData {
	/**
	 * The VMs to be placed as part of the request
	 */
	public final VirtualMachine[] queuedVMs;
	/**
	 * A single VM should have this many resources allocated to it
	 */
	public final ResourceConstraints queuedRC;
	/**
	 * All VMs in the request should have this many resources in total
	 */
	public final ResourceConstraints cumulativeRC;
	/**
	 * The repository that is storing the VM's virtual appliance.
	 */
	public final Repository queuedRepo;
	/**
	 * Data for custom schedulers (e.g., like specific placement requirements -
	 * please put me on this machine and this machine only), if null then there
	 * is no data.
	 */
	public final Map<String, Object> schedulingConstraints;
	/**
	 * The time stamp when the VM request has been received by the VM scheduler
	 */
	public final long receivedTime;

	/**
	 * Instantiates the queueing data object which auto-populates the derivable
	 * fields and safeguards all data stored.
	 * 
	 * @param vms
	 *            the virtual machine set to work on
	 * @param rc
	 *            the resource requirements for a single VM
	 * @param vaSource
	 *            the repository which hosts the virtual appliance required for
	 *            the instantiation of the VMs
	 * @param schedulingConstraints
	 *            custom scheduler data
	 * @param received
	 *            the timestamp
	 */
	public QueueingData(final VirtualMachine[] vms, final ResourceConstraints rc, final Repository vaSource,
			Map<String, Object> schedulingConstraints, final long received) {
		queuedVMs = vms;
		queuedRC = rc;
		queuedRepo = vaSource;
		AlterableResourceConstraints cRC = new AlterableResourceConstraints(rc);
		cRC.multiply(queuedVMs.length);
		cumulativeRC = new ConstantConstraints(cRC);
		receivedTime = received;
		this.schedulingConstraints = schedulingConstraints == null ? null
				: Collections.unmodifiableMap(schedulingConstraints);
	}

	/**
	 * Provides a user readable single line representation of the queued VMs.
	 * Good for debugging and tracing.
	 */
	@Override
	public String toString() {
		return "QueueingData(" + queuedVMs.length + " * " + queuedRC + " @" + receivedTime + ")";
	}
}