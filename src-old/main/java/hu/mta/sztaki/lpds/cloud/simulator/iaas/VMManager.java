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

package hu.mta.sztaki.lpds.cloud.simulator.iaas;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * This interface intends to provide a generic overview of the VM management
 * functionalities in a system.
 * 
 * @author Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013
 * 
 * @param <E>
 *            The type of the system that will manage the VMs.
 * 
 * @param <F>
 *            The kind of the managed capacities behind the VMManager.
 */
public interface VMManager<E, F> {

	/**
	 * This is a generic class to represent all kinds of problems that could
	 * occur while managing VMs on the system.
	 * 
	 * @author Gabor Kecskemeti, Distributed and Parallel Systems Group,
	 *         University of Innsbruck (c) 2013
	 * 
	 */
	class VMManagementException extends Exception {

		private static final long serialVersionUID = 4420666848803005233L;

		public VMManagementException(final String message) {
			super(message);
		}

		public VMManagementException(final String message, final Throwable ex) {
			super(message, ex);
		}
	}

	/**
	 * Receiving this kind of exception shows that the system does not recognize
	 * the VM it should operate on.
	 * 
	 * @author Gabor Kecskemeti, Distributed and Parallel Systems Group,
	 *         University of Innsbruck (c) 2013
	 * 
	 */
	class NoSuchVMException extends VMManagementException {
		private static final long serialVersionUID = 777996106319988828L;

		public NoSuchVMException(final String message) {
			super(message);
		}

		public NoSuchVMException(final String message, final Throwable ex) {
			super(message, ex);
		}
	}

	/**
	 * The interface to implement for those events where a VMManager object
	 * changes its capacity.
	 * 
	 * @author Gabor Kecskemeti, Distributed and Parallel Systems Group,
	 *         University of Innsbruck (c) 2013
	 * 
	 * @param <F>
	 *            the kind of capacity that changes
	 */
	interface CapacityChangeEvent<F> {
		/**
		 * This function is called by the object that has changed its capacities
		 * 
		 * @param newCapacity
		 *            the size of the new capacity in terms of physical
		 *            resources
		 * @param affectedCapacity
		 *            the list of those objects (representing the computing
		 *            capacity of the particular VMManager) that were
		 *            added/removed during the change
		 */
		void capacityChanged(ResourceConstraints newCapacity,
				List<F> affectedCapacity);
	}

	/**
	 * Creates a new VM in the system and immediately returns with a new VM
	 * object. The user have to check if the VM is actually running through the
	 * VM object.
	 * 
	 * Implementors should ensure that the particular VA is accessible for the
	 * system before the VM gets started.
	 * 
	 * @param va
	 *            the kind of VM to be created
	 * @return the new VM
	 * @throws VMManagementException
	 *             if the request cannot be fulfilled for some reason
	 */
	VirtualMachine[] requestVM(VirtualAppliance va, ResourceConstraints rc,
			Repository vaSource, int count,
			HashMap<String, Object> schedulingConstraints)
			throws VMManagementException, NetworkNode.NetworkException;

	/**
	 * Terminates a VM in the system. Ensures that it is not running after the
	 * request completes anymore.
	 * 
	 * @param vm
	 *            the VM to be terminated
	 * @throws NoSuchVMException
	 *             if the request was issued for a VM unknown in the system
	 * @throws VMManagementException
	 *             if the request cannot be fulfilled for some reason
	 */
	void terminateVM(VirtualMachine vm, boolean killTasks)
			throws NoSuchVMException, VMManagementException;

	/**
	 * Migrates a VM from the current system to another. The VM is not going to
	 * be running during this period. It will not consume resources on the
	 * current system anymore
	 * 
	 * @param vm
	 *            the VM to be relocated
	 * @param target
	 *            the target system that should host the VM in the future
	 * @throws NoSuchVMException
	 *             if the request was issued for a VM unknown in the system
	 * @throws VMManagementException
	 *             if the request cannot be fulfilled for some reason
	 */
	void migrateVM(VirtualMachine vm, E target) throws NoSuchVMException,
			VMManagementException, NetworkNode.NetworkException;

	/**
	 * Allows fine-grained resource utilization setup of the particular VM after
	 * it was allocated on the system. Even during its runtime. Don't forget
	 * that in real VMs resource availability changes might cause troubles.
	 * Apply it rarely if you are not sure it will also work in the real world!
	 * 
	 * @param vm
	 *            The VM to be adjusted
	 * @param newresources
	 *            The new amount of resources needed (this could not only raise
	 *            the available resources of the VM but also decrease them
	 * @throws NoSuchVMException
	 *             if the request was issued for a VM unknown in the system
	 * @throws VMManagementException
	 *             if the request cannot be fulfilled for some reason
	 */
	void reallocateResources(VirtualMachine vm, ResourceConstraints newresources)
			throws NoSuchVMException, VMManagementException;

	/**
	 * Provides an overview on the VMs currently in the system
	 * 
	 * @return the VMs currently registered with the system (they are either
	 *         alredy running or will run in the foreseeable future
	 */
	Collection<VirtualMachine> listVMs();

	/**
	 * Allows the query of the total capacities
	 * 
	 * @return the total computing capacities of this VMManager
	 */
	ResourceConstraints getCapacities();

	/**
	 * manages the subscriptions for capacity change (increase/decrease) events
	 * 
	 * @param e
	 *            the listener object which expects capacity change events
	 */
	void subscribeToCapacityChanges(CapacityChangeEvent<F> e);

	/**
	 * manages the subscriptions for capacity change (increase/decrease) events
	 * 
	 * @param e
	 *            the listener object that no longer expects capacity change events
	 */
	void unsubscribeFromCapacityChanges(CapacityChangeEvent<F> e);
}
