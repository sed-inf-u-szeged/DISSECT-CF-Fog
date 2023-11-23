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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.PMIterator;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

import java.util.ArrayList;

/**
 * This class implements one of the simplest VM schedulers: it places every VM
 * on the first PM that would actually accept it. The scheduler does not
 * rearrange the queue, thus if there would be some queued entities that would
 * be possible to schedule this scheduler just ignores them until they reach the
 * head of the queue.
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012"
 */
public class FirstFitScheduler extends Scheduler {

	/**
	 * The set of resource allocations made for the current VM request (this is
	 * important for multi VM requests)
	 */
	private final ArrayList<ResourceAllocation> ras = new ArrayList<>(5);
	/**
	 * the largest allocation that was possible to collect from all running PMs
	 * in the infrastructure. this is important to determine the amount of
	 * resources that need to become free before the scheduler would be able to
	 * place the head of the queue to any of the PMs in the infrastructure.
	 */
	private ResourceAllocation raBiggestNotSuitable = null;
	/**
	 * the iterator of the running PMs allowing to easily traverse the PM set in
	 * a predefined order. The iterator plays a crucial role in this
	 * implementation as several FirstFit derivatives only differ in their
	 * handling of the PMs.
	 */
	private final PMIterator it;

	/**
	 * the constructor of the scheduler that passes on the parent IaaS service
	 * and initiates the basic PM iterator for this scheduler which will
	 * 
	 * @param parent
	 */
	public FirstFitScheduler(IaaSService parent) {
		super(parent);
		it = instantiateIterator();
	}

	/**
	 * Allows the customization of the PM iterator by subclasses. This function
	 * is not intended to be used by anything else but the constructor of this
	 * class.
	 * 
	 * @return the desired PM iterator to be used for traversing the PMs while
	 *         doing the scheduling
	 */
	protected PMIterator instantiateIterator() {
		return new PMIterator(parent.runningMachines);
	}

	/**
	 * Resets the iterator then offers it to the caller
	 * 
	 * @return the PMIterator that is ready to be used during the scheduling
	 *         process
	 */
	protected PMIterator getPMIterator() {
		it.reset();
		return it;
	}

	/**
	 * The actual first fit scheduling implementation. This implementation
	 * supports requests with multiple VMs. It assumes that users want to deploy
	 * all VMs or nothing, so it waits until all VMs could be deployed at once.
	 */
	@Override
	protected ConstantConstraints scheduleQueued() {
		final PMIterator currIterator = getPMIterator();
		ConstantConstraints returner = new ConstantConstraints(getTotalQueued());
		if (currIterator.hasNext()) {
			QueueingData request;
			boolean processableRequest = true;
			while (queue.size() > 0 && processableRequest) {
				request = queue.peek();
				currIterator.restart(false);
				int vmNum = 0;
				do {
					processableRequest = false;
					do {
						final PhysicalMachine pm = currIterator.next();
						if (pm.localDisk.getFreeStorageCapacity() >= request.queuedVMs[vmNum].getVa().size) {
							try {
								final ResourceAllocation allocation = pm.allocateResources(request.queuedRC, false,
										PhysicalMachine.defaultAllocLen);
								if (allocation != null) {
									if (allocation.allocated.compareTo(request.queuedRC) >= 0) {
										// Successful allocation
										if (pm.freeCapacities.getRequiredCPUs() == 0 && currIterator.hasNext()) {
											currIterator.next();
										}
										currIterator.markLastCollected();
										ras.add(allocation);
										processableRequest = true;
										break;
									} else {
										if (raBiggestNotSuitable == null) {
											raBiggestNotSuitable = allocation;
										} else if (allocation.allocated.compareTo(raBiggestNotSuitable.allocated) > 0) {
											raBiggestNotSuitable.cancel();
											raBiggestNotSuitable = allocation;
										} else {
											allocation.cancel();
										}
									}
								}
							} catch (VMManagementException e) {
								//ignore
							}
						}
					} while (currIterator.hasNext());
					currIterator.restart(true);
				} while (++vmNum < request.queuedVMs.length && processableRequest);
				if (processableRequest) {
					try {
						for (int i = request.queuedVMs.length - 1; i >= 0; i--) {
							final ResourceAllocation allocation = ras.remove(i);
							allocation.getHost().deployVM(request.queuedVMs[i], allocation, request.queuedRepo);
						}
						manageQueueRemoval(request);
					} catch (VMManagementException e) {
						processableRequest = false;
					} catch (NetworkException e) {
						// Connectivity issues! Should not happen!
						System.err.println("WARNING: there are connectivity issues in the system." + e.getMessage());
						processableRequest = false;
					}
				} else {
					AlterableResourceConstraints arc = new AlterableResourceConstraints(request.queuedRC);
					if (raBiggestNotSuitable != null) {
						arc.subtract(raBiggestNotSuitable.allocated);
					} else {
						arc.multiply(request.queuedVMs.length - vmNum + 1);
					}
					returner = new ConstantConstraints(arc);
				}
				if (raBiggestNotSuitable != null) {
					raBiggestNotSuitable.cancel();
					raBiggestNotSuitable = null;
				}
			}
			ras.forEach(ResourceAllocation::cancel);
			ras.clear();
		}
		return returner;
	}
}
