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

import java.util.*;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.helpers.PMComparators;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;

/**
 * The base class for all VM schedulers, provides the foundational logic and
 * simplifies the implementation of VM schedulers by allowing them to mainly
 * focus on their scheduling logic.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012"
 */
public abstract class Scheduler {

	/**
	 * Implementing this interface allows the implementor to receive events from the
	 * scheduler about cases when it believes the infrastructure is not sufficient
	 * for its needs. Allows the collaboration between PM controllers and VM
	 * schedulers.
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2012"
	 */
	public interface QueueingEvent {

		/**
		 * This function is called when the VM scheduler believes the infrastructure
		 * could be improved for better suiting its needs.
		 */
		void queueingStarted();
	}

	/**
	 * The IaaS for which this VM scheduler manages the VM requests.
	 */
	protected final IaaSService parent;

	/**
	 * The queue of the scheduler. This is intentionally made protected so
	 * subclasses could replace the list implementation to one that suits them
	 * better.
	 */
	protected Queue<QueueingData> queue = new LinkedList<>();
	/**
	 * the amount of resources needed for fulfilling all VM requests in the queue
	 */
	private final AlterableResourceConstraints totalQueued = AlterableResourceConstraints.getNoResources();
	/**
	 * the public version of totalQueued that mirrors its contents but does not
	 * allow changes on it
	 */
	protected final UnalterableConstraintsPropagator publicTQ = new UnalterableConstraintsPropagator(totalQueued);
	/**
	 * This field contains an automatically updated list of all machines in the
	 * parent IaaS. The list is kept in the order of the PM's size to allow rapid
	 * decisions on the possible fitting of VM requests.
	 */
	private final ArrayList<PhysicalMachine> orderedPMcache = new ArrayList<>();
	/**
	 * current length of the pm cache, so we don't need to query its size all the
	 * time
	 */
	private int pmCacheLen;
	/**
	 * the manager of those objects who shown interest in receiving queuing related
	 * event notifications
	 */
	private final StateDependentEventHandler<QueueingEvent, Integer> queueListenerManager = new StateDependentEventHandler<>(
			(QueueingEvent onObject, Integer ignore) -> onObject.queueingStarted());

	/**
	 * Here we keep an account of the amount of resources a particular scheduler
	 * would need before it would be able to schedule a new VM request
	 */
	private ConstantConstraints minimumSchedulerRequirement = ConstantConstraints.noResources;
	/**
	 * In this field the simulator maintains those recently freed up resources that
	 * could be allowing a new scheduling run
	 */
	private final AlterableResourceConstraints freeResourcesSinceLastSchedule = AlterableResourceConstraints.getNoResources();

	/**
	 * This is the action that takes place when one of the PMs at the IaaS changes
	 * its state. If this listener is called because a new PM has turned on then the
	 * scheduler is again given a chance to allocate some VMs.
	 * 
	 * If there are some queued requests even after doing the scheduling then the
	 * queuelisteners are notified, so they can improve the IaaS's infrastructure
	 * setup.
	 */
	protected final PhysicalMachine.StateChangeListener pmstateChanged = (PhysicalMachine pm, State oldState,
			State newState) -> {
		if (newState.equals(PhysicalMachine.State.RUNNING)) {
			freeResourcesSinceLastSchedule.add(pm.freeCapacities);
			if (freeResourcesSinceLastSchedule.compareTo(minimumSchedulerRequirement) >= 0
					&& totalQueued.getRequiredCPUs() != 0) {
				invokeRealScheduler();
			}
		}
		if (totalQueued.getRequiredCPUs() != 0) {
			queueListenerManager.notifyListeners(null);
		}
	};

	/**
	 * This field represents the action to be done when some resources become free
	 * on one of the currently running PMs in the IaaS. If necessary this action
	 * invokes the scheduling and also notifies the queuing listeners about having a
	 * VM queue despite newly free resources.
	 */
	protected final VMManager.CapacityChangeEvent<ResourceConstraints> freeCapacity = new VMManager.CapacityChangeEvent<>() {
		@Override
		public void capacityChanged(final ResourceConstraints newCapacity,
				final List<ResourceConstraints> newlyFreeResources) {
			freeResourcesSinceLastSchedule.add(newlyFreeResources);
			if (totalQueued.getRequiredCPUs() != 0) {
				if (freeResourcesSinceLastSchedule.compareTo(minimumSchedulerRequirement) >= 0) {
					invokeRealScheduler();
				}
				if (totalQueued.getRequiredCPUs() != 0
						&& queue.peek().cumulativeRC.compareTo(parent.getRunningCapacities()) > 0) {
					queueListenerManager.notifyListeners(null);
				}
			}
		}
	};

	/**
	 * The main constructor of all schedulers. This constructor ensures that the
	 * orderedPMcache is maintained, and it also connects the scheduler's free
	 * capacity and pm state listeners.
	 * 
	 * @param parent the IaaS service for which this scheduler is expected to act as
	 *               the VM request scheduler
	 */
	public Scheduler(final IaaSService parent) {
		this.parent = parent;
		parent.subscribeToCapacityChanges(
				(final ResourceConstraints newCapacity, final List<PhysicalMachine> alteredPMs) -> {
					final boolean newRegistration = parent.isRegisteredHost(alteredPMs.get(0));
					final int pmNum = alteredPMs.size();
					if (newRegistration) {
						// Increased pm count
						orderedPMcache.addAll(alteredPMs);
						orderedPMcache.sort(PMComparators.highestToLowestTotalCapacity);
						pmCacheLen += pmNum;
						for (final PhysicalMachine pm : alteredPMs) {
							pm.subscribeStateChangeEvents(pmstateChanged);
							pm.subscribeToIncreasingFreeapacityChanges(freeCapacity);
						}
					} else {
						// Decreased pm count
						for (final PhysicalMachine pm : alteredPMs) {
							orderedPMcache.remove(pm);
							pm.unsubscribeStateChangeEvents(pmstateChanged);
							pm.unsubscribeFromIncreasingFreeCapacityChanges(freeCapacity);
						}
						pmCacheLen -= pmNum;
					}
				});
	}

	/**
	 * The main entry point to the schedulers. This function checks if a request
	 * could be possibly hosted on the IaaS's infrastructure, if so then it queues
	 * it. And if necessary it also invokes the actual scheduling operation
	 * implemented in the subclasses.
	 * 
	 * @param vms                   the virtual machines that should be placed on
	 *                              the IaaS's machine set
	 * @param rc                    the resource requirements for the VMs
	 * @param vaSource              the repository that stores the virtual appliance
	 *                              for the above VM set.
	 * @param schedulingConstraints custom data to be passed on to subclassed
	 *                              schedulers
	 * @throws VMManagementException if the request is impossible to schedule on the
	 *                               current infrastructure
	 */
	public final void scheduleVMrequest(final VirtualMachine[] vms, final ResourceConstraints rc,
			final Repository vaSource, final HashMap<String, Object> schedulingConstraints)
			throws VMManagementException {
		if (vaSource.lookup(vms[0].getVa().id) == null) {
			throw new VMManagementException("VA is not stored on source!");
		}
		final long currentTime = Timed.getFireCount();
		final QueueingData qd = new QueueingData(vms, rc, vaSource, schedulingConstraints, currentTime);

		int hostableVMs = 0;
		boolean hostable = false;
		for (int pmid = 0; pmid < pmCacheLen; pmid++) {
			PhysicalMachine machine = orderedPMcache.get(pmid);
			AlterableResourceConstraints biggestHostable = new AlterableResourceConstraints(rc);
			for (int i = 1; i <= vms.length; i++, hostableVMs++) {
				if (!machine.isHostableRequest(biggestHostable)) {
					break;
				}
				biggestHostable.singleAdd(rc);
			}
			if (hostableVMs >= vms.length) {
				hostable = true;
				break;
			}
		}
		if (hostable) {
			boolean wasEmpty = queue.isEmpty();
			queue.add(qd);
			totalQueued.singleAdd(qd.cumulativeRC);
			if (wasEmpty) {
				invokeRealScheduler();
				if (queue.size() == 0) {
					return;
				}
				queueListenerManager.notifyListeners(null);
			} else {
				minimumSchedulerRequirement = ConstantConstraints.noResources;
			}
		} else {
			throw new VMManagementException("No physical machine is capable to serve this request: " + qd);
		}
	}

	/**
	 * Cancels a VM request by dropping the corresponding queuing data from the
	 * scheduler's queue.
	 * 
	 * @param vm the VM which is representing the queued request to be dropped from
	 *           the queue
	 * @return <i>true</i> if the request was actually dropped, <i>false</i>
	 *         otherwise.
	 */
	public final boolean dropVMrequest(final VirtualMachine vm) {
		final Iterator<QueueingData> it = queue.iterator();
		while (it.hasNext()) {
			final QueueingData qd = it.next();
			for (int i = 0; i < qd.queuedVMs.length; i++) {
				if (qd.queuedVMs[i] == vm) {
					it.remove();
					for (i = 0; i < qd.queuedVMs.length; i++) {
						// Mark all VMs in the request to be nonservable
						qd.queuedVMs[i].setNonservable();
					}
					updateTotalQueuedAfterRemoval(qd);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The complete resource set required to fulfill the entirety of the VM requests
	 * in the queue
	 * 
	 * @return the complete resource set
	 */
	public ResourceConstraints getTotalQueued() {
		return publicTQ;
	}

	/**
	 * The number of VM requests queued at the moment
	 * 
	 * @return the length of the request queue
	 */
	public int getQueueLength() {
		return queue.size();
	}

	/**
	 * Removes an arbitrary item from the queue (could be a rather slow operation!)
	 * 
	 * @param qd the item to be removed
	 */
	protected void manageQueueRemoval(final QueueingData qd) {
		queue.remove(qd);
		updateTotalQueuedAfterRemoval(qd);
	}

	/**
	 * Removes the head of the queue
	 * 
	 * @return the queuing data object removed from the queue's head
	 */
	protected QueueingData manageQueueRemoval() {
		if (queue.isEmpty()) {
			return null;
		}
		QueueingData removed = queue.poll();
		updateTotalQueuedAfterRemoval(removed);
		return removed;
	}

	/**
	 * The common functionality required to manage the update of the total queued
	 * field.
	 * 
	 * @param qd the removed queue member that needs to be deducted from the total
	 *           queue field
	 */
	private void updateTotalQueuedAfterRemoval(final QueueingData qd) {
		if (queue.isEmpty()) {
			totalQueued.subtract(totalQueued);
			totalQueued.subtract(totalQueued);
			minimumSchedulerRequirement = ConstantConstraints.noResources;
		} else {
			totalQueued.subtract(qd.cumulativeRC);
		}
	}

	/**
	 * Allows third parties to get notified if the scheduler is not satisfied with
	 * the current infrastructure (e.g. because it cannot fit the current VM request
	 * set to the running PM set)
	 * 
	 * @param e the object that will handle the notifications
	 */
	public final void subscribeQueueingEvents(QueueingEvent e) {
		queueListenerManager.subscribeToEvents(e);
		if (queue.size() != 0) {
			e.queueingStarted();
		}
	}

	/**
	 * Cancels the notifications about queuing events
	 * 
	 * @param e the object that no longer needs notifications
	 */
	public final void unsubscribeQueueingEvents(QueueingEvent e) {
		queueListenerManager.unsubscribeFromEvents(e);
	}

	/**
	 * Prepares a list of all the VMs that are queued at the particular moment in
	 * time
	 * 
	 * @return the list of requested and not yet served VMs
	 */
	public final List<VirtualMachine> getQueuedVMs() {
		final ArrayList<VirtualMachine> vms = new ArrayList<>(queue.size());
		for (final QueueingData qd : queue) {
			vms.addAll(Arrays.asList(qd.queuedVMs));
		}
		return vms;
	}

	/**
	 * This function is actually calling the subclass's scheduler implementation and
	 * handles the management of minimum scheduler requirements and resets the free
	 * resource aggregate
	 */
	private void invokeRealScheduler() {
		minimumSchedulerRequirement = scheduleQueued();
		freeResourcesSinceLastSchedule.subtract(freeResourcesSinceLastSchedule);
	}

	/**
	 * When a new VM scheduler is created this is the function to be implemented.
	 * 
	 * The function is expected to poll or the queue and only remove queued entities
	 * if they are placed on an actual PM. The removal is supported by the
	 * manageQueueRemoval function which allows both in order and out of order
	 * removals thus even allows prioritizing scheduler logic.
	 * 
	 * @return the amount of free resources that the IaaS service needs to offer
	 *         before this scheduler could actually proceed with its next VM
	 *         placement.
	 */
	protected abstract ConstantConstraints scheduleQueued();
}
