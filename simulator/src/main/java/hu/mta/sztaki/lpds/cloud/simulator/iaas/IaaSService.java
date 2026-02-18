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

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;
import hu.mta.sztaki.lpds.cloud.simulator.util.ArrayHandler;

/**
 * This class represents a single IaaS service. It's tasks are the maintenance
 * and management of the physical machines and the scheduling of the VM requests
 * among the PMs.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013"
 * 
 */
public class IaaSService implements VMManager<IaaSService, PhysicalMachine>, PhysicalMachine.StateChangeListener {

	/**
	 * This class represents a generic error that occurred during the operation of
	 * the IaaS service.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013"
	 * 
	 */
	public static class IaaSHandlingException extends Exception {

		@Serial
		private static final long serialVersionUID = 2580735547805541590L;

		/**
		 * Only a generic constructor is defined so a textual message can be propagated
		 * around the system
		 * 
		 * @param s the reason this exception was raised
		 */
		public IaaSHandlingException(final String s) {
			super(s);
		}

		public IaaSHandlingException(final String s, final Throwable ex) {
			super(s, ex);
		}
	}

	/**
	 * The actual writable list of the machine set maintained behind this
	 * IaaSService
	 * 
	 * WARNING: The order of internal machines is not guaranteed
	 */
	private final ArrayList<PhysicalMachine> internalMachines = new ArrayList<>();
	/**
	 * The actual writable list of the running machine set maintained behind this
	 * IaaSService
	 * 
	 * WARNING: The order of internal running machines is not guaranteed
	 */
	private final ArrayList<PhysicalMachine> internalRunningMachines = new ArrayList<>();

	/**
	 * publicly available read only version of the internal machines field
	 */
	public final List<PhysicalMachine> machines = Collections.unmodifiableList(internalMachines);
	/**
	 * publicly available read only version of the internal running machines field
	 */
	public final List<PhysicalMachine> runningMachines = Collections.unmodifiableList(internalRunningMachines);

	/**
	 * the total capacity of all machines in this iaas service, for use only
	 * internally in the IaaS service class
	 */
	private final AlterableResourceConstraints totalCapacity = AlterableResourceConstraints.getNoResources();
	/**
	 * the total capacity to be reported for external users. This field propagates
	 * the totalCapacity field. Keep in mind that the capacity reported by this
	 * field could change without notification.
	 */
	private final ResourceConstraints publicTCap = new UnalterableConstraintsPropagator(totalCapacity);
	/**
	 * the capacity of the machines that are actually running in the system - this
	 * is for internal use only again
	 */
	private final AlterableResourceConstraints runningCapacity = AlterableResourceConstraints.getNoResources();
	/**
	 * The capacity of the running machines to be reported externally. This is
	 * implemented with a propagator thus changes in runningcapacity will be
	 * immediately reflected here as well without notification.
	 */
	private final ResourceConstraints publicRCap = new UnalterableConstraintsPropagator(runningCapacity);

	/**
	 * event handler for capacity changes in terms of added/removed physical
	 * machines
	 */
	private final StateDependentEventHandler<CapacityChangeEvent<PhysicalMachine>, List<PhysicalMachine>> capacityListenerManager = new StateDependentEventHandler<>(
			(CapacityChangeEvent<PhysicalMachine> onObject, List<PhysicalMachine> pmsetchange) -> onObject.capacityChanged(totalCapacity, pmsetchange));

	/**
	 * The list of repositories under direct control of this IaaS service - this is
	 * for internal purposes the list is writeable
	 * 
	 * The order of internal repositories is not guaranteed
	 */
	private final ArrayList<Repository> internalRepositories = new ArrayList<>();
	/**
	 * the read only list of all repositories in the system
	 */
	public final List<Repository> repositories = Collections.unmodifiableList(internalRepositories);

	/**
	 * the VM scheduler applied by this IaaSservice object
	 */
	public final Scheduler sched;
	/**
	 * the PM scheduler applied by this IaaSService object
	 */
	public final PhysicalMachineController pmcontroller;

	/**
	 * Constructs an IaaS service object directly. The VM and PM schedulers for this
	 * IaaS service will be created during the creation of the IaaSService itself.
	 * This ensures that users cannot alter the link between the IaaSService and the
	 * various schedulers. The <i>exceptions</i> are thrown because reflection is
	 * used to create the scheduler objects.
	 * 
	 * @param s class of the VM scheduler to be used
	 * @param c class of the PM scheduler to be used
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	public IaaSService(Class<? extends Scheduler> s, Class<? extends PhysicalMachineController> c)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException {
		sched = s.getConstructor(IaaSService.class).newInstance(this);
		pmcontroller = c.getConstructor(IaaSService.class).newInstance(this);
	}

	/**
	 * Not implemented! Will allow migrating VMs across IaaSServices.
	 */
	@Override
	public void migrateVM(final VirtualMachine vm, final IaaSService target) {
	}

	/**
	 * Determines if the VM is hosted locally in one of the physical machines of
	 * this IaaSService
	 * 
	 * @param vm the VM to be checked
	 * @return the PM that hosts the VM
	 * @throws NoSuchVMException if the VM is not hosted by any of the PMs in the
	 *                           system
	 */
	private PhysicalMachine checkVMHost(final VirtualMachine vm) throws NoSuchVMException {
		ResourceAllocation ra = vm.getResourceAllocation();
		PhysicalMachine host;
		if (!(ra != null && runningMachines.contains(host = ra.getHost()))) {
			throw new NoSuchVMException("This VM is not run by any of the managed PMs in this IaaS service");
		}
		return host;
	}

	/**
	 * NOT IMPLEMENTED! Reallocates the VM's resources according to the newresources
	 * on the host of the VM.
	 */
	@Override
	public void reallocateResources(VirtualMachine vm, ResourceConstraints newresources)
			throws VMManagementException {
		checkVMHost(vm).reallocateResources(vm, newresources);
	}

	/**
	 * Allows the request of multiple VMs without propagating any scheduling
	 * constraints.
	 * 
	 * @param va       the VA to be used as the disk of the VM
	 * @param rc       the resource requirements of the future VM
	 * @param vaSource the repository that currently stores the VA
	 * @param count    the number of VMs that this request should be returning with
	 * @return the list of VMs created by the call (please note the VMs returned
	 *         might never actually get running, if the PMs are always blocked by
	 *         some other activities, or if they go to NONSERVABLE state)
	 * @throws VMManagementException        if there are no pms that could run the
	 *                                      VMs. or if the request is too big to be
	 *                                      hosted across the complete
	 *                                      infrastructure
	 * @throws NetworkNode.NetworkException if there are network connectivity
	 *                                      problems within the infrastructure
	 */
	public VirtualMachine[] requestVM(final VirtualAppliance va, final ResourceConstraints rc,
			final Repository vaSource, final int count) throws VMManagementException {
		return requestVM(va, rc, vaSource, count, null);
	}

	/**
	 * Allows the request of multiple VMs.
	 * 
	 * @param va                    the VA to be used as the disk of the VM
	 * @param rc                    the resource requirements of the future VM
	 * @param vaSource              the repository that currently stores the VA
	 * @param count                 the number of VMs that this request should be
	 *                              returning with
	 * @param schedulingConstraints The VM scheduler dependent additional
	 *                              requirements for the newly requested VMs (e.g.
	 *                              please go for a specific host etc.) For
	 *                              understanding what you can send here please have
	 *                              a look at the documentation of the particular VM
	 *                              scheduler in question.
	 * @return the list of VMs created by the call (please note the VMs returned
	 *         might never actually get running, if the PMs are always blocked by
	 *         some other activities, or if they go to NONSERVABLE state)
	 * @throws VMManagementException        if there are no pms that could run the
	 *                                      VMs. or if the request is too big to be
	 *                                      hosted across the complete
	 *                                      infrastructure
	 * @throws NetworkNode.NetworkException if there are network connectivity
	 *                                      problems within the infrastructure
	 */
	@Override
	public VirtualMachine[] requestVM(VirtualAppliance va, ResourceConstraints rc, Repository vaSource, int count,
			HashMap<String, Object> schedulingConstraints) throws VMManager.VMManagementException {
		if (machines.isEmpty()) {
			throw new VMManagementException("There are no physical machines that can run VMs!");
		}
		VirtualMachine[] vms = new VirtualMachine[count];
		for (int i = 0; i < count; i++) {
			vms[i] = new VirtualMachine(va);
		}
		sched.scheduleVMrequest(vms, rc, vaSource, schedulingConstraints);
		return vms;
	}

	/**
	 * Requesting the destruction of a VM in a DESTROYED state will dequeue the VM
	 * from the scheduler's request queue. If the VM was not requested from this
	 * IaaSService then a nosuchvmexception is thrown.
	 */
	@Override
	public void terminateVM(final VirtualMachine vm, final boolean killTasks)
			throws VMManagementException {
		if (VirtualMachine.State.DESTROYED.equals(vm.getState())) {
			// The VM is still under scheduling, the queue needs to be cleared
			if (!sched.dropVMrequest(vm)) {
				throw new NoSuchVMException("This VM is not queued in this IaaS service");
			}
			return;
		}
		checkVMHost(vm).terminateVM(vm, killTasks);
	}

	/**
	 * lists all VMs running or requested (and queued at a VM scheduler) from the
	 * IaaSservice
	 */
	@Override
	public Collection<VirtualMachine> listVMs() {
		final ArrayList<VirtualMachine> completeList = new ArrayList<>(sched.getQueuedVMs());
		for (PhysicalMachine internalMachine : internalMachines) {
			completeList.addAll(internalMachine.listVMs());
		}
		return completeList;
	}

	/**
	 * This function allows the IaaS to grow in size with a single PM
	 * 
	 * @param pm the new physical machine to be utilized within the system
	 */
	public void registerHost(final PhysicalMachine pm) {
		bulkHostRegistration(Collections.singletonList(pm));
	}

	/**
	 * This function allows rapid registration of several PMs
	 * 
	 * @param newPMs the list of PMs to be registered
	 */
	public void bulkHostRegistration(final List<PhysicalMachine> newPMs) {
		internalMachines.addAll(newPMs);
		final int size = newPMs.size();
		final ResourceConstraints[] caps = new ResourceConstraints[size];
		double maxPcPP = totalCapacity.getRequiredProcessingPower();
		for (int i = 0; i < size; i++) {
			final PhysicalMachine pm = newPMs.get(i);
			if (PhysicalMachine.State.RUNNING.equals(pm.getState())) {
				stateChanged(pm, PhysicalMachine.State.RUNNING, PhysicalMachine.State.RUNNING);
			}
			pm.subscribeStateChangeEvents(this);
			caps[i] = pm.getCapacities();
			maxPcPP = Math.max(caps[i].getRequiredProcessingPower(), maxPcPP);
		}
		totalCapacity.add(caps);
		totalCapacity.scaleProcessingPower(maxPcPP / totalCapacity.getRequiredProcessingPower());
		capacityListenerManager.notifyListeners(newPMs);
	}

	/**
	 * Really deregisters a PM from the list of PMs.
	 * 
	 * @param pm the PM to be deregistered
	 */
	private void realDeregistration(PhysicalMachine pm) {
		pm.unsubscribeStateChangeEvents(this);
		ResourceConstraints caps = pm.getCapacities();
		final double suspectedmax = caps.getRequiredProcessingPower();
		totalCapacity.subtract(caps);
		if (totalCapacity.getRequiredProcessingPower() == suspectedmax) {
			final double maxPcPP = internalMachines.stream().mapToDouble(im -> im.getCapacities().getRequiredProcessingPower()).max().getAsDouble();
			totalCapacity.scaleProcessingPower(maxPcPP / suspectedmax);
		}
		capacityListenerManager.notifyListeners(Collections.singletonList(pm));
	}

	/**
	 * This function allows the IaaS to reduce in size. <br/>
	 * This function might migrate VMs from the deregistered host to ones remaining
	 * in the system. If the deregistered host contains VMs that cannot be migrated,
	 * or there is nowhere to migrate the VMs then the function throws an exception.
	 * 
	 * Currently, there is no migration implemented!
	 * 
	 * @param pm the physical machine to be dropped from the control of the system
	 */
	public void deregisterHost(final PhysicalMachine pm) throws IaaSHandlingException {
		if (ArrayHandler.removeAndReplaceWithLast(internalMachines, pm)) {
			if (pm.isRunning()) {
				ArrayHandler.removeAndReplaceWithLast(internalRunningMachines, pm);
				if (pm.isHostingVMs()) {
					AlterableResourceConstraints needed = new AlterableResourceConstraints(pm.getCapacities());
					needed.subtract(pm.freeCapacities);
					PhysicalMachine receiver = null;
					for (PhysicalMachine curr : machines) {
						if (needed.compareTo(curr.freeCapacities) <= 0) {
							receiver = curr;
							break;
						}
					}
					if (receiver == null) {
						throw new IaaSHandlingException(
								"You cannot deregister the host before all its VMs are terminated, or if all VMs can be migrated to somewhere else");
					}
					try {
						pm.subscribeStateChangeEvents((PhysicalMachine pmInt, State oldState, State newState) -> {
							if (newState.equals(PhysicalMachine.State.OFF)) {
								realDeregistration(pmInt);
							}
						});
						if (receiver.isRunning()) {
							pm.switchoff(receiver);
						} else {
							final PhysicalMachine rcopy = receiver;
							receiver.subscribeStateChangeEvents(
									(PhysicalMachine pmInt, State oldState, State newState) -> {
										try {
											if (newState.equals(PhysicalMachine.State.RUNNING)) {
												pmInt.switchoff(rcopy);
											}
										} catch (VMManagementException | NetworkException e) {
											e.printStackTrace();
										}
									});
							receiver.turnon();
						}
					} catch (VMManagementException e) {
						throw new IaaSHandlingException("Some error has happened during the switchoff procedure", e);
					} catch (NetworkNode.NetworkException e) {
						throw new IaaSHandlingException("All PMs should be able to connect to each other in the IaaS",
								e);
					}
					return;
				}
			}
			realDeregistration(pm);
		} else {
			throw new IaaSHandlingException("No such registered physical machine");
		}
	}

	/**
	 * This function allows the IaaS to grow its storage capacities
	 * 
	 * @param r the new repository to be utilized within the system
	 */
	public void registerRepository(final Repository r) {
		internalRepositories.add(r);
		try {
			r.setState(NetworkNode.State.RUNNING);
		} catch (NetworkException e) {
			// Should not happen
		}
	}

	/**
	 * This function allows the IaaS to reduce its storage capacities.
	 * 
	 * This function might transfer contents from the deregistered repository to
	 * ones remaining in the system. If the deregistered repository contains storage
	 * objects that cannot be transferred, or there is nowhere to transfer a few
	 * storage objects then the function throws an exception.
	 * 
	 * Currently, there is no transfer implemented!
	 * 
	 * @param r the repository to be dropped from the control of the system
	 */
	public void deregisterRepository(final Repository r) {
		ArrayHandler.removeAndReplaceWithLast(internalRepositories, r);
	}

	/**
	 * returns with the total capacities of this service (cumulative value for all
	 * its PMs)
	 */
	public ResourceConstraints getCapacities() {
		return publicTCap;
	}

	/**
	 * returns with the total running capacities of this service (cumulative value
	 * for all its PMs that are in a running state)
	 */
	public ResourceConstraints getRunningCapacities() {
		return publicRCap;
	}

	/**
	 * get notified about capacity changes (PM additions/removals)
	 * 
	 * This call is propagated to StateDependentEventHandler.
	 */
	@Override
	public void subscribeToCapacityChanges(final CapacityChangeEvent<PhysicalMachine> e) {
		capacityListenerManager.subscribeToEvents(e);
	}

	/**
	 * cancel the notifications about capacity changes (PM additions/removals)
	 * 
	 * This call is propagated to StateDependentEventHandler.
	 */
	@Override
	public void unsubscribeFromCapacityChanges(final CapacityChangeEvent<PhysicalMachine> e) {
		capacityListenerManager.unsubscribeFromEvents(e);
	}

	/**
	 * A function to determine if a host is within the premises of this IaaSService.
	 * 
	 * @param pm the host in question
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the host is part of the IaaS
	 *         <li><i>false</i> otherwise
	 *         </ul>
	 */
	public boolean isRegisteredHost(PhysicalMachine pm) {
		return internalMachines.contains(pm);
	}

	/**
	 * Provides a convenient way to debug the IaaS.
	 */
	@Override
	public String toString() {
		return "IaaS(Machines(" + machines + "), Repositories(" + repositories + "))";
	}

	/**
	 * Implements the PhysicalMachine's state change listener to manage the
	 * internalRunningMachines list.
	 */
	@Override
	public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
		if (newState == State.RUNNING) {
			internalRunningMachines.add(pm);
			runningCapacity.singleAdd(pm.getCapacities());
		} else {
			if (oldState.equals(State.RUNNING)) {
				internalRunningMachines.remove(pm);
				runningCapacity.subtract(pm.getCapacities());
			}
		}
	}
}
