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
 *  (C) Copyright 2016, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 *  (C) Copyright 2015, Vincenzo De Maio (vincenzo@dps.uibk.ac.at)
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package hu.mta.sztaki.lpds.cloud.simulator.iaas;

import java.util.*;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.*;
import org.apache.commons.lang3.tuple.Pair;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import org.eclipse.collections.impl.list.mutable.primitive.DoubleArrayList;

/**
 * This class represents a single Physical machine with computing resources as
 * well as local disks and network connections.
 * 
 * The PM is a central part of the infrastructure simulation, it allows VM
 * management, direct access to its resources and also provides several power
 * management operations (like switch off/on).
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2016"
 * @author "Vincenzo De Maio, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2015"
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012"
 */
public class PhysicalMachine extends MaxMinProvider implements VMManager<PhysicalMachine, ResourceConstraints> {

	/**
	 * This is the default length for how long a resource allocation will be kept
	 * before it becomes invalid.
	 * 
	 * It is specified in ticks.
	 */
	public static final int defaultAllocLen = 1000;
	/**
	 * This is the recommended length for how long a resource allocation will be
	 * kept before it becomes invalid in case the allocation is made for a VM
	 * migration.
	 * 
	 * It is specified in ticks.
	 */
	public static final int migrationAllocLen = 1000000000;
	/**
	 * Amount of processing to be done if the PM need to be underutilized. This
	 * constant is used for calculating the emulation of on and off operations if
	 * they are specified with single delays in the PM's constructor.
	 */
	public static final double smallUtilization = 0.001;

	/**
	 * Represents the possible states of the physical machines modeled in the system
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013"
	 * 
	 */
	public enum State {
		/**
		 * The machine is completely switched off, minimal consumption is recorded.
		 */
		OFF,
		/**
		 * The machine is under preparation to serve VMs. Some consumption is recorded
		 * already.
		 */
		SWITCHINGON,
		/**
		 * The machine is currently serving VMs. The machine and its VMs are consuming
		 * energy.
		 */
		RUNNING,
		/**
		 * The machine is about to be switched off. It no longer accepts VM requests but
		 * it still consumes energy.
		 */
		SWITCHINGOFF
	}

	/**
	 * These are the PM states which are either leading to running or already
	 * running.
	 */
	public static final EnumSet<State> ToOnorRunning = EnumSet.of(State.SWITCHINGON, State.RUNNING);
	/**
	 * These are the PM states which are either leading to off or already off.
	 */
	public static final EnumSet<State> ToOfforOff = EnumSet.of(State.SWITCHINGOFF, State.OFF);
	/**
	 * This is the list of PM states that mostly consume energy
	 */
	public static final EnumSet<State> StatesOfHighEnergyConsumption = EnumSet.of(State.SWITCHINGON, State.RUNNING,
			State.SWITCHINGOFF);

	/**
	 * Defines the minimal interface for listeners on PM state changes.
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2012"
	 *
	 */
	public interface StateChangeListener {
		/**
		 * This function is called by the PM on subscribed objects when a PM's state
		 * changes. To simplify the implementation of the receiver objects, this state
		 * changed function actually propagates all state information (pre and post) as
		 * well as the physical machine's reference which just went through the state
		 * change.
		 * 
		 * @param pm
		 *            the physical machine that is involved in the state change
		 * @param oldState
		 *            the state the PM was in before this state change event was
		 *            delivered
		 * @param newState
		 *            the new state the PM will be in after the event is complete
		 */
		void stateChanged(PhysicalMachine pm, State oldState, State newState);
	}

	/**
	 * This class is strongly connected with the physical machine's class, the two
	 * are collaborating to allow a fluid and minimal hassle operation of allocating
	 * resources on a PM. This allocation is then later on expected to be used by
	 * virtual machines to use.
	 * 
	 * Before resource allocations are used they are allocated for a fixed amount of
	 * time. Thus if a user forgets about the allocation, the PMs resources are not
	 * occupied forever.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
	 *         Distributed Systems, MTA SZTAKI (c) 2012"
	 */
	public class ResourceAllocation extends DeferredEvent {
		/**
		 * The resource set that is virtually offered to the VM that uses this
		 * allocation.
		 */
		public final ResourceConstraints allocated;
		/**
		 * The resource set that is actually reserved on the PM. If
		 * allocated&gt;realAllocated, then the VM might get resources up to allocated but
		 * only when the PM is not over-committed.
		 */
		private final ResourceConstraints realAllocated;
		/**
		 * The VM that utilizes the allocation in question
		 */
		private VirtualMachine user = null;
		/**
		 * The index of this allocation in the allocation list located in the PM. Allows
		 * rapid cleaning of the allocation list.
		 */
		private final int myPromisedIndex;
		/**
		 * If the resource allocation is not used before its expiry time then this is
		 * going to be true!
		 */
		private boolean swept = false;

		/**
		 * The constructor of the resource allocation to be used by the physical machine
		 * only!
		 * 
		 * @param realAlloc
		 *            defines the actually reserved resources with the allocation
		 * @param alloc
		 *            defines the promised resources for the user
		 * @param until
		 *            defines the duration in ticks for which the allocation will be
		 *            kept for the user. This period is useful to allow advanced
		 *            scheduling operations where some resources are occupied in advance
		 *            in order to ensure rapid VM creation.
		 */
		private ResourceAllocation(final ResourceConstraints realAlloc, final ResourceConstraints alloc,
				final int until) {
			super(until);
			allocated = alloc;
			realAllocated = realAlloc;
			int prLen = promisedResources.length;
			int i;
			for (i = 0; i < prLen && promisedResources[i] != null; i++)
				;
			if (i == prLen) {
				ResourceAllocation[] alls = new ResourceAllocation[prLen * 2];
				System.arraycopy(promisedResources, 0, alls, 0, prLen);
				promisedResources = alls;
				promisedResources[prLen] = this;
				myPromisedIndex = prLen;
			} else {
				promisedResources[i] = this;
				myPromisedIndex = i;
			}
			promisedCapacities.singleAdd(realAllocated);
			internalReallyFreeCaps.subtract(realAllocated);
			promisedAllocationsCount++;
		}

		/**
		 * This function is called by deferred event when the allocation is expired. The
		 * allocation expiry event is only happening if there are no VMs that use the
		 * allocation by the time the expiry period is over.
		 * 
		 * <i>REMARK:</i> As expiry is considered a problem in most systems, this is
		 * actually printing out to standard error - as the error is not critical and
		 * there would be no way to propagate the error to the entity who issued the
		 * allocation.
		 */
		@Override
		protected void eventAction() {
			System.err.println("Warning! Expiring resource allocation.");
			swept = true;
			promisedCapacityUpdater();
		}

		/**
		 * updates the PM's respective fields about resource availability
		 */
		private void promisedCapacityUpdater() {
			promisedResources[myPromisedIndex] = null;
			promisedAllocationsCount--;
			if (promisedAllocationsCount == 0) {
				promisedCapacities.subtract(promisedCapacities);
			} else {
				promisedCapacities.subtract(realAllocated);
			}
			if (isUnUsed()) {
				internalReallyFreeCaps.singleAdd(realAllocated);
			}
		}

		/**
		 * if a resource allocation is no longer needed (e.g. because another allocation
		 * was used for the same VM), then this way one can not only cancel the
		 * allocation but also ensure that the expiry event is not fired.
		 */
		public void cancel() {
			if (!isCancelled()) {
				super.cancel();
				promisedCapacityUpdater();
			}
		}

		/**
		 * To complete the allocation process the VM must be told to use a particular
		 * allocation (e.g. in its migration or startup calls). The VM then automatically
		 * tells this resource allocation object to finalize the allocation process by
		 * calling this use function.
		 * 
		 * This function subscribes to the VM's state change events in order to ensure
		 * that VMs that are not running are not really using resources from the PM.
		 * 
		 * <i>WARNING:</i> this function is to be used by the VM only as the VM knows
		 * for what operation it needs to use the allocation.
		 * 
		 * @param vm
		 *            the virtual machine that will use the allocated resources in the
		 *            future
		 * @throws VMManagementException
		 *             if the allocation passed to the VM is already expired or if the
		 *             allocation is already used by some VM.
		 */
		void use(final VirtualMachine vm) throws VMManagementException {
			if (swept) {
				throw new VMManagementException("Tried to use an already expired allocation");
			}
			if (user == null) {
				user = vm;
				internalAvailableCaps.subtract(realAllocated);
				vms.add(vm);
				decreasingFreeCapacityListenerManager.notifyListeners(Collections.singletonList(realAllocated));
				cancel();
			} else {
				throw new VMManagementException("Tried to use a resource allocation more than once!");
			}
		}

		/**
		 * If a VM no longer needs the resources then this function must be called. As a
		 * result, the PM ensures that the released resources are again available to all
		 * interested parties.
		 */
		void release() {
			if (user != null) {
				vms.remove(user);
				completedVMs++;
				internalAvailableCaps.singleAdd(realAllocated);
				internalReallyFreeCaps.singleAdd(realAllocated);
				increasingFreeCapacityListenerManager.notifyListeners(Collections.singletonList(realAllocated));
				user = null;
				swept = true;
			}
		}

		/**
		 * Allows users to determine if the allocation is already taken by a VM.
		 * 
		 * @return
		 *         <ul>
		 *         <li><i>true</i> if there is a VM that is using the resource
		 *         allocation
		 *         <li><i>false</i> otherwise
		 *         </ul>
		 */
		public boolean isUnUsed() {
			return user == null;
		}

		/**
		 * Determines if the allocation is still available to use
		 * 
		 * @return
		 *         <ul>
		 *         <li><i>true</i> if the allocation is still ok to use
		 *         <li><i>false</i>otherwise
		 *         </ul>
		 */
		public boolean isAvailable() {
			return isUnUsed() && !swept;
		}

		/**
		 * Shows basic information about the allocation useful for debugging
		 */
		@Override
		public String toString() {
			return "RA(Canc:" + swept + " " + allocated + ")";
		}

		/**
		 * Allows to determine the Physical Machine the particular allocation is bound
		 * to.
		 * 
		 * @return the physical machine which offers this allocation.
		 */
		public PhysicalMachine getHost() {
			return PhysicalMachine.this;
		}
	}

	/**
	 * This class handles the delays and activities during the power state change
	 * procedures (e.g., switching off/turning on)
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
	 *         Distributed Systems, MTA SZTAKI (c) 2014-"
	 */
	public class PowerStateDelayer extends ConsumptionEventAdapter {
		/**
		 * The state that the delayer must switch to after the power state change has
		 * finished its activities.
		 */
		private State newState;
		/**
		 * The list of tasks to do for the particular power state change. These tasks
		 * are expected to represent activities like the boot process.
		 * 
		 * IMPORTANT: For performance reasons, the list is handled from its tailing:
		 * <ul>
		 * <li>The last item is always the amount processing to be done in the upcoming
		 * task of the power change
		 * <li>the penultimate item is always the maximum amount of processing to be
		 * done in a single tick for the above task.
		 * </ul>
		 */
		final DoubleArrayList tasksDue;
		/**
		 * when did the particular power state transition start
		 */
		public final long transitionStart;
		/**
		 * What is the actual resource consumption (task) that is executed on the PM's
		 * direct consumer (i.e. the pure machine)
		 */
		ResourceConsumption currentConsumption = null;

		/**
		 * The constructor of the delayer, it allows the specification of the tasks
		 * (that represent the boot/shutdown or similar operations) to be done before
		 * the power state can be achieved.
		 * 
		 * @param tasklist
		 *            the tasks to execute in a serial order before the specific
		 *            power state can be set.
		 * 
		 *            IMPORTANT: For performance reasons, the list is handled from its
		 *            tailing:
		 *            <ul>
		 *            <li>The last item is always the amount processing to be done in
		 *            the upcoming task of the power change
		 *            <li>the penultimate item is always the maximum amount of
		 *            processing to be done in a single tick for the above task.
		 *            </ul>
		 * @param newPowerState
		 *            the power state to be set after completing all the above tasks.
		 */
		public PowerStateDelayer(final double[] tasklist, final State newPowerState) {
			onOffEvent = this;
			newState = newPowerState;
			tasksDue = new DoubleArrayList();
			tasksDue.addAll(tasklist);
			sendTask();
			transitionStart = Timed.getFireCount();
		}

		/**
		 * this function picks the next item (i.e. always the very last members of the
		 * list) from the tasklist and executes it, the function also ensures that the
		 * powerstatedelayer object will receive a notification once the task is
		 * completed.
		 * 
		 * if there are no further tasks to execute this function even sets the new
		 * power state of the physical machine with the PM's corresponding function.
		 */
		private void sendTask() {
			// Did we finish all the tasks for the state change?
			if (tasksDue.size() == 0) {
				// Mark the completion of the state change
				onOffEvent = null;
				class NetworkCausedStateDelay extends Timed {
					int infiniteLoopTest = 0;

					public NetworkCausedStateDelay() {
						tick(Timed.getFireCount());
					}

					@Override
					public void tick(long fires) {
						try {
							setState(newState);
							// Everything is fine we are good to go.
							unsubscribe();
						} catch (NetworkException nex) {
							// Some network activities stop the transition
							if (infiniteLoopTest++ > 10000) {
								throw new RuntimeException("Unsettling amount of network activity...");
							}
							long whenToCheckNext = localDisk.getLastEvent();
							if (whenToCheckNext > 0) {
								updateFrequency(whenToCheckNext + 1 - fires);
							}
						}
					}
				}
				new NetworkCausedStateDelay();
				return;
			}

			// No we did not, lets send some more to our direct consumer
			final double totalConsumption = tasksDue.removeAtIndex(0);
			final double limit = tasksDue.removeAtIndex(0);
			currentConsumption = new ResourceConsumption(totalConsumption, limit, directConsumer, PhysicalMachine.this,
					this);
			if (!currentConsumption.registerConsumption()) {
				throw new IllegalStateException(
						"PowerStateChange was not successful because resource consumption could not be registered");
			}
		}

		/**
		 * the notification handler when a task from the tasklist is complete
		 * 
		 * this handler actually sends the next task in.
		 */
		@Override
		public void conComplete() {
			sendTask();
		}

		/**
		 * if we receive a cancelled event we consider it as an unexpected behavior and
		 * throw an exception. When enabling faults in the system this behavior might
		 * need to change.
		 * 
		 * @param problematic
		 *            the task that did not succeed
		 */
		@Override
		public void conCancelled(ResourceConsumption problematic) {
			throw new IllegalStateException("Unexpected termination of one of the state changing tasks");
		}

		/**
		 * if there seems to be further operations needed before the particular power
		 * state can be achieved then this function should be called.
		 * 
		 * This function is especially useful to model cases when one state change has
		 * already started but then the user opts for another one before the previous
		 * state change can complete
		 * 
		 * @param tasklist
		 *            the new tasks to be included
		 */
		public void addFurtherTasks(final double[] tasklist) {
			tasksDue.addAll(tasklist);
		}

		/**
		 * if after the list of tasks are completed we need to reach a different power
		 * state than initially planned then this is the function to go for.
		 * 
		 * This is an optional operation, you only need to call it if the PM started to
		 * go for some other state beforehand that you don't like.
		 * 
		 * @param newState
		 *            the new power state to switch to after all tasks complete
		 */
		public void setNewState(State newState) {
			this.newState = newState;
		}
	}

	/**
	 * the complete resource set of the pm
	 */
	private final ConstantConstraints totalCapacities;
	/**
	 * the resource set that does not have a VM running on it
	 * 
	 * availableCapacities = freeCapacities + promisedCapacities
	 */
	private final AlterableResourceConstraints internalAvailableCaps;
	/**
	 * This is the publicly disclosed set of those resources that are not having a
	 * VM running on them. This field is <b>read only</b>!
	 * 
	 * This field can automatically update between two checks! If you need unaltered
	 * data please make a copy (e.g., new ConstantConstraints(pm.freeCapacities)).
	 */
	public final UnalterableConstraintsPropagator availableCapacities;
	/**
	 * the amount of resources currently allocated but that have no VM assigned to
	 * them
	 */
	private final AlterableResourceConstraints promisedCapacities = AlterableResourceConstraints.getNoResources();
	/**
	 * the amount of resources that are not running a VM or not allocated by some
	 * resource allocation
	 */
	private final AlterableResourceConstraints internalReallyFreeCaps;
	/**
	 * This is the publicly disclosed set of those resources that are not even
	 * having an allocation. This field is <b>read only</b>!
	 * 
	 * This field can automatically update between two checks! If you need unaltered
	 * data please make a copy (e.g., new ConstantConstraints(pm.freeCapacities)).
	 */
	public final UnalterableConstraintsPropagator freeCapacities;
	/**
	 * the internal disk of the physical machine. this field is also used to
	 * represent the PM's network connections as the disk is represented with the
	 * repository interface.
	 */
	public final Repository localDisk;
	/**
	 * this is the array of resource allocations which contain all not yet
	 * used/expired resource allocations.
	 * 
	 * This array might contain null values in between two resourceallocation
	 * objects.
	 */
	private ResourceAllocation[] promisedResources = new ResourceAllocation[2];
	/**
	 * the amount of resource allocations in the promisedResources array.
	 */
	private int promisedAllocationsCount = 0;

	/**
	 * The current state of the PM
	 */
	private State currentState = null;
	/**
	 * the tasks to do when turning the PM on. The format of the array is documented
	 * in the powerstatedelayer class.
	 */
	private final double[] onTransition;
	/**
	 * the tasks to do when switching the PM off. The format of the array is
	 * documented in the powerstatedelayer class.
	 */
	private final double[] offTransition;
	/**
	 * around how many ticks the PM is estimated to run the tasks in the
	 * onTransition array.
	 */
	private final long onDelayEstimate;
	/**
	 * around how many ticks the PM is estimated to run the tasks in the
	 * offTransition array.
	 */
	private final long offDelayEstimate;

	/**
	 * Mapping between the various PM states and its representative CPU/memory power
	 * behaviors.
	 */
	private final Map<String, PowerState> hostPowerBehavior;
	/**
	 * the manager of the PM's state change notifications.
	 */
	private final StateDependentEventHandler<StateChangeListener, Pair<State, State>> stateListenerManager=new StateDependentEventHandler<>(
			(final StateChangeListener onObject, final Pair<State, State> states) ->
				onObject.stateChanged(PhysicalMachine.this, states.getLeft(), states.getRight())
			);

	/**
	 * the set of currently running virtual machines on this PM
	 */
	private final HashSet<VirtualMachine> vms = new HashSet<>(); // current
	/**
	 * the publicly available, read only set of currently running virtual machines
	 * on this PM
	 */
	public final Set<VirtualMachine> publicVms = Collections.unmodifiableSet(vms);
	/**
	 * the number of VMs that were using resources from this PM at any time of the
	 * PM's existence
	 */
	private long completedVMs = 0; // Past
	/**
	 * The currently operating state change handler - this field actually shows if a
	 * state change is on the way.
	 * 
	 * in order to be really consistent, the field is directly managed by the
	 * powerstatedelayer class.
	 */
	private PowerStateDelayer onOffEvent = null;
	/**
	 * this notification handler is used to send out events when some of the PM's
	 * resources are getting available for others to use
	 */
	private final StateDependentEventHandler<CapacityChangeEvent<ResourceConstraints>, List<ResourceConstraints>> increasingFreeCapacityListenerManager=new StateDependentEventHandler<>(this::sendCapacityUpdate);
	/**
	 * this notification handler is used to send out events when some of the PM's
	 * resources are getting used
	 */
	private final StateDependentEventHandler<CapacityChangeEvent<ResourceConstraints>, List<ResourceConstraints>> decreasingFreeCapacityListenerManager=new StateDependentEventHandler<>(this::sendCapacityUpdate);

	/**
	 * This consumer is added to the PM help simulate the pure (VM less) operations
	 * on the PM. E.g., the VMM's operations can be resourceconsumptions registered
	 * between this consumer and the PM.
	 */
	public final MaxMinConsumer directConsumer;
	/**
	 * shows when the PM's direct consumer cannot be used as a consumer for tasks
	 * (in general it is only usable for external users when the PM is running)
	 */
	private boolean directConsumerUsageMoratory = true;

	private boolean secureExtensions = false;

	/**
	 * Defines a new physical machine, ensures that there are no VMs running so far
	 * 
	 * @param cores
	 *            defines the number of CPU cores this machine has under control
	 * @param perCoreProcessing
	 *            defines the processing capabilities of a single CPU core in this
	 *            machine (in instructions/tick)
	 * @param memory
	 *            defines the total physical memory this machine has under control
	 *            (in bytes)
	 * @param disk
	 *            defines the local physical disk &amp; networking this machine has
	 *            under control
	 * @param onD
	 *            defines the time delay between the machine's switch on and the
	 *            first time it can serve VM requests
	 * @param offD
	 *            defines the time delay the machine needs to shut down all of its
	 *            operations while it does not serve any more VMs
	 * @param cpuPowerTransitions
	 *            determines the applied power state transitions while the physical
	 *            machine state changes. This is the principal way to alter a PM's
	 *            energy consumption behaviour.
	 */
	public PhysicalMachine(double cores, double perCoreProcessing, long memory, Repository disk, int onD, int offD,
			Map<String, PowerState> cpuPowerTransitions) {
		this(cores, perCoreProcessing, memory, disk,
				new double[] { onD * perCoreProcessing * smallUtilization, perCoreProcessing * smallUtilization },
				new double[] { offD * perCoreProcessing * smallUtilization, perCoreProcessing * smallUtilization },
				cpuPowerTransitions);
	}

	/**
	 * Makes a copy of the given array to the correct target (on/offTransition), and
	 * calculates the estimates for the on/offDelay
	 * 
	 * @param on
	 *            which taskset needs to be prepared
	 * @param array
	 *            the task array - for format see powerstatedelayer
	 * @return the estimated runtime of all tasks in the array
	 */
	private long prepareTransitionalTasks(boolean on, double[] array) {
		final double[] writeHere = on ? onTransition : offTransition;
		System.arraycopy(array, 0, writeHere, 0, array.length);
		long odSum = 0;
		for (int i = 0; i < array.length; i += 2) {
			odSum += (long) (array[i] / array[i + 1]);
		}
		return odSum;
	}

	/**
	 * Defines a new physical machine, ensures that there are no VMs running so far
	 * 
	 * @param cores
	 *            defines the number of CPU cores this machine has under control
	 * @param perCoreProcessing
	 *            defines the processing capabilities of a single CPU core in this
	 *            machine (in instructions/tick)
	 * @param memory
	 *            defines the total physical memory this machine has under control
	 *            (in bytes)
	 * @param disk
	 *            defines the local physical disk &amp; networking this machine has
	 *            under control
	 * @param turnonOperations
	 *            defines the tasks to execute before the PM can be turned on - this
	 *            can be considered as the simulation of the boot process. for the
	 *            complete definition of this array have a look at the
	 *            powerstatedelayer class.
	 * @param switchoffOperations
	 *            defines the tasks to execute before the PM can be switched off -
	 *            this can be considered as the simulation of the shutdown process.
	 *            for the complete definition of this array have a look at the
	 *            powerstatedelayer class.
	 * @param cpuPowerTransitions
	 *            determines the applied power state transitions while the physical
	 *            machine state changes. This is the principal way to alter a PM's
	 *            energy consumption behaviour.
	 */

	public PhysicalMachine(double cores, double perCoreProcessing, long memory, Repository disk,
			double[] turnonOperations, double[] switchoffOperations, Map<String, PowerState> cpuPowerTransitions) {
		super(cores * perCoreProcessing);
		if (cpuPowerTransitions == null) {
			throw new IllegalStateException("Cannot initialize physical machine without a complete power behavior set");
		}

		// Init resources:
		totalCapacities = new ConstantConstraints(cores, perCoreProcessing, memory);
		internalAvailableCaps = new AlterableResourceConstraints(totalCapacities);
		availableCapacities = new UnalterableConstraintsPropagator(internalAvailableCaps);
		internalReallyFreeCaps = new AlterableResourceConstraints(totalCapacities);
		freeCapacities = new UnalterableConstraintsPropagator(internalReallyFreeCaps);
		localDisk = disk;

		hostPowerBehavior = Collections.unmodifiableMap(cpuPowerTransitions);
		onTransition = new double[turnonOperations.length];
		onDelayEstimate = prepareTransitionalTasks(true, turnonOperations);
		offTransition = new double[switchoffOperations.length];
		offDelayEstimate = prepareTransitionalTasks(false, switchoffOperations);

		try {
			setState(State.OFF);
		} catch (NetworkException e) {
			// This should never happen
			throw new RuntimeException(e);
		}
		directConsumer = new MaxMinConsumer(getPerTickProcessingPower());
	}

	/**
	 * Helper method to send notifications to particular objects about resource utilisation changes
	 * @param onObject The receiver of the notification
	 * @param recentResources The resource usage changes
	 */
	private void sendCapacityUpdate(final CapacityChangeEvent<ResourceConstraints> onObject,
								   final List<ResourceConstraints> recentResources) {
		onObject.capacityChanged(freeCapacities, recentResources);
	}

	/**
	 * Starts the turn off procedure for the physical machine so it no longer
	 * accepts VM requests but it does not consume anymore
	 * 
	 * @param migrateHere
	 *            the physical machine where the currently hosted VMs of this VM
	 *            should go before the actual switch off operation will happen
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the switch off procedure has started
	 *         <li><i>false</i> if there are still VMs running and migration target
	 *         was not specified thus the switch off is not possible
	 *         </ul>
	 * @throws NetworkException
	 *             if the migration would need to use network connections that are
	 *             not set up correctly.
	 * @throws VMManager.VMManagementException
	 *             if the migration fails because the VM is not in correct state or
	 *             the PM does not have enough storage for the memory/disk state
	 */
	public boolean switchoff(final PhysicalMachine migrateHere) throws VMManagementException, NetworkException {
		if (migrateHere != null) {
			final VirtualMachine[] vmarr = vms.toArray(new VirtualMachine[0]);
			class MultiMigrate implements VirtualMachine.StateChange {
				private int counter = 0;

				@Override
				public void stateChanged(VirtualMachine vm, VirtualMachine.State oldState,
						VirtualMachine.State newState) {
					if (Objects.requireNonNull(newState) == VirtualMachine.State.RUNNING) {
						counter++;
					}
					if (counter == vmarr.length) {
						for (VirtualMachine virtualMachine : vmarr) {
							virtualMachine.unsubscribeStateChange(this);
						}
						actualSwitchOff();
					}
				}
			}
			MultiMigrate mm = new MultiMigrate();
			for (VirtualMachine virtualMachine : vmarr) {
				virtualMachine.subscribeStateChange(mm);
				migrateVM(virtualMachine, migrateHere);
			}
		} else {
			if (vms.size() + promisedAllocationsCount > 0) {
				return false;
			}
			actualSwitchOff();
		}
		return true;
	}

	/**
	 * does the actual switchoff, expected to be run only when there are no VMs
	 * running on the PM
	 */
	private void actualSwitchOff() {
		try {
			switch (currentState) {
				case SWITCHINGON -> {
					setState(State.SWITCHINGOFF);
					onOffEvent.addFurtherTasks(offTransition);
					onOffEvent.setNewState(State.OFF);
				}
				case RUNNING -> {
					setState(State.SWITCHINGOFF);
					new Timed() {
						@Override
						public void tick(final long fires) {
							if (State.SWITCHINGOFF.equals(currentState)) {
								FreqSyncer syncer = getSyncer();
								// Ensures that the switching off activities are only
								// started once all runtime activities complete for the
								// directConsumer
								if (syncer != null && syncer.isSubscribed()
										&& (underProcessing.size() + toBeAdded.size() - toBeRemoved.size() > 0)) {
									updateFrequency(syncer.getNextEvent() - fires + 1);
								} else {
									unsubscribe();
									new PowerStateDelayer(offTransition, State.OFF);
								}
							}
							// else: Another transition dropped the switchoff task. do
							// nothing
						}
					}.tick(Timed.getFireCount());
				}
				case OFF ->
					// Nothing to do
						System.err.println("WARNING: an already off PM was tasked to switch off!");
				case SWITCHINGOFF ->
					// Nothing to do
						System.err.println("WARNING: an already switching-off PM was tasked to switch off!");
			}
		} catch (NetworkException nex) {
			// Should not happen as long as the network node don't have a SWITCHINGOFF state
			throw new RuntimeException(nex);
		}

	}

	/**
	 * Determines if the machine can be used for VM instantiation.
	 * 
	 * @return
	 *         <ul>
	 *         <li>true if the machine is ready to accept VM requests
	 *         <li>false otherwise
	 *         </ul>
	 */
	public boolean isRunning() {
		return currentState.equals(State.RUNNING);
	}

	/**
	 * retrieves the current state of the PM
	 * 
	 * @return the current state
	 */
	public State getState() {
		return currentState;
	}

	/**
	 * Turns on the physical machine so it allows energy and resource consumption
	 * (i.e. compute tasks) and opens the possibility to receive VM requests.
	 */
	public void turnon() {
		switch (currentState) {
			case SWITCHINGOFF, OFF -> {
				try {
					setState(State.SWITCHINGON);
				} catch (NetworkException nex) {
					// Should not happen as long as the networknode don't have a switchingon state
					throw new RuntimeException(nex);
				}
				if (onOffEvent == null) {
					new PowerStateDelayer(onTransition, State.RUNNING);
				} else {
					onOffEvent.addFurtherTasks(onTransition);
					onOffEvent.setNewState(State.RUNNING);
				}
			}
			case RUNNING, SWITCHINGON ->
				// Nothing to do
					System.err.println("WARNING: an already running PM was tasked to switch on!");
		}
	}

	/**
	 * Initiates the migration of a VM to another PM.
	 * 
	 * @param vm
	 *            the VM to be migrated
	 * @param target
	 *            the PM to be used by the VM after the migration completes
	 */
	@Override
	public void migrateVM(final VirtualMachine vm, final PhysicalMachine target)
			throws VMManagementException, NetworkNode.NetworkException {
		vm.migrate(prepareMigration(vm, target));
	}

	private ResourceAllocation prepareMigration(final VirtualMachine vm, final PhysicalMachine target)
			throws VMManagementException {
		if (vms.contains(vm)) {
			ResourceAllocation ra = target.allocateResources(vm.getResourceAllocation().allocated, true,
					migrationAllocLen);
			if (ra == null) {
				throw new VMManagementException("Not enough resources on target host for migration");
			}
			return ra;
		} else {
			throw new VMManagementException("VM not hosted on source PM");
		}

	}

	public void migrateVMLive(final VirtualMachine vm, final PhysicalMachine target)
			throws VMManagementException, NetworkNode.NetworkException {
		vm.migrateLive(prepareMigration(vm, target));
	}

	/**
	 * Not implemented, would allow VMs to receive more resources in the future
	 */
	@Override
	public void reallocateResources(final VirtualMachine vm, final ResourceConstraints newresources) {

	}

	/**
	 * Ensures the requested amount of resources are going to be available in the
	 * foreseeable future on this physical machine.
	 * 
	 * @param requested
	 *            The amount of resources needed by the caller
	 * @param strict
	 *            if the PM should not return an allocation if it cannot completely
	 *            meet the request.
	 * @param allocationValidityLength
	 *            for how long the PM should keep the allocation in place. Depending
	 *            on the purpose of the allocation, it is recommended to use either
	 *            the defaultAllocLen or the migrationAllocLen constants of the PM
	 *            for this field.
	 * @return With a time limited offer on the requested resources. If the
	 *         requested resourceconstraints cannot be met by the function then it
	 *         returns with the maximum amount of resources it can serve. If there
	 *         are no available resources it returns with <i>null</i>! If the
	 *         requested resources can be exactly met then the original requested
	 *         resourceconstraints object is stored in the returned resource
	 *         allocation. If the resourceconstraints only specified a minimum
	 *         resource limit, then a new resourceconstraints object is returned
	 *         with details of the maximum possible resource constraints that can
	 *         fit into the machine.
	 */
	public ResourceAllocation allocateResources(final ResourceConstraints requested, final boolean strict,
			final int allocationValidityLength) throws VMManagementException {
		if (!currentState.equals(State.RUNNING)) {
			throw new VMManagementException("The PM is not running and thus cannot offer resources yet");
		}
		// Basic tests for resource availability for the host
		if (internalReallyFreeCaps.getRequiredCPUs() == 0 || internalReallyFreeCaps.getRequiredMemory() == 0
				|| requested.getRequiredProcessingPower() > internalReallyFreeCaps.getRequiredProcessingPower()) {
			return null;
		}
		// Allocation type test (i.e. do we allow underprovisioning?)
		for (ResourceAllocation olderAllocation : promisedResources) {
			if (olderAllocation != null) {
				if (olderAllocation.allocated.isRequiredProcessingIsMinimum() == requested
						.isRequiredProcessingIsMinimum()) {
					break;
				} else {
					return null;
				}
			}
		}
		// Promised resources for the virtual machine
		double vmCPU = requested.getRequiredCPUs();
		long vmMem = requested.getRequiredMemory();
		final double vmPrPow = requested.isRequiredProcessingIsMinimum() ? totalCapacities.getRequiredProcessingPower()
				: requested.getRequiredProcessingPower();

		// Actually allocated resources (memory is equivalent in both cases)
		final double allocPrPow = totalCapacities.getRequiredProcessingPower();
		final double allocCPU = vmCPU * requested.getRequiredProcessingPower() / allocPrPow;
		if (0 <= internalReallyFreeCaps.getRequiredCPUs() - allocCPU) {
			if (0 <= internalReallyFreeCaps.getRequiredMemory() - requested.getRequiredMemory()) {
				return new ResourceAllocation(new ConstantConstraints(allocCPU, allocPrPow, vmMem),
						requested.isRequiredProcessingIsMinimum() ? new ConstantConstraints(vmCPU, vmPrPow, true, vmMem)
								: requested,
						allocationValidityLength);
			} else {
				vmMem = internalReallyFreeCaps.getRequiredMemory();
			}
		} else if (0 <= internalReallyFreeCaps.getRequiredMemory() - requested.getRequiredMemory()) {
			vmCPU = internalReallyFreeCaps.getRequiredCPUs();
		} else {
			vmCPU = internalReallyFreeCaps.getRequiredCPUs();
			vmMem = internalReallyFreeCaps.getRequiredMemory();
		}
		if (strict) {
			return null;
		} else {
			final ResourceConstraints updatedConstraints = new ConstantConstraints(vmCPU, vmPrPow,
					requested.isRequiredProcessingIsMinimum(), vmMem);
			return new ResourceAllocation(updatedConstraints, updatedConstraints, allocationValidityLength);
		}
	}

	/**
	 * check if a particular resource allocation is really issued by this pm.
	 * 
	 * @param allocation
	 *            the untrusted allocation
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the PM issued the allocation
	 *         <li><i>false</i> otherwise
	 *         </ul>
	 */
	private boolean checkAllocationsPresence(final ResourceAllocation allocation) {
		return promisedResources.length > allocation.myPromisedIndex
				&& promisedResources[allocation.myPromisedIndex] == allocation;
	}

	/**
	 * Terminate a resource allocation through the PM's interfaces
	 * 
	 * @param allocation
	 *            the resource allocation to terminate
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the allocation was cancelled
	 *         <li><i>false</i> otherwise
	 *         </ul>
	 */
	public boolean cancelAllocation(final ResourceAllocation allocation) {
		if (checkAllocationsPresence(allocation)) {
			allocation.cancel();
			return true;
		}
		return false;
	}

	/**
	 * checks if at least in theory the requested resources could be hosted on the
	 * PM (i.e., when there are no other VMs hosted on the PM).
	 * 
	 * @param requested
	 *            the resource set to be checked for hostability
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the such resource request has a chance of
	 *         acceptance
	 *         <li><i>false</i> otherwise
	 *         </ul>
	 */
	public boolean isHostableRequest(final ResourceConstraints requested) {
		return requested.compareTo(totalCapacities) <= 0;
	}

	/**
	 * Bounds a VM to a particular PM on a previously agreed allocation
	 * 
	 * @param vm
	 *            the VM to be deployed on the PM
	 * @param ra
	 *            the resource allocation to be used for the VM
	 * @param vaSource
	 *            the repository that stores the virtual appliance of the VM
	 * @throws VMManagementException
	 *             if the VA is not present on the source, or if the allocation
	 *             specified is already expired/used, or if the VM cannot be
	 *             switched on for some reason
	 * @throws NetworkNode.NetworkException
	 *             if the VA is not transferable from the vaSource to its
	 *             destination
	 */
	public void deployVM(final VirtualMachine vm, final ResourceAllocation ra, final Repository vaSource)
			throws VMManagementException, NetworkNode.NetworkException {
		if (checkAllocationsPresence(ra)) {
			final VirtualAppliance va = vm.getVa();
			final StorageObject foundLocal = localDisk.lookup(va.id);
			final StorageObject foundRemote = vaSource == null ? null : vaSource.lookup(va.id);
			if (foundLocal != null || foundRemote != null) {
				vm.switchOn(ra, vaSource);
			} else {
				throw new VMManagementException("No VA available!");
			}
		} else {
			throw new VMManagementException("Tried to deploy VM with an expired resource allocation");
		}
	}

	/**
	 * Initiates a VM on this physical machine. If the physical machine cannot host
	 * VMs for some reason an exception is thrown, if the machine cannot host this
	 * particular VA then a null VM is returned.
	 * 
	 * @param va
	 *            The appliance for the VM to be created.
	 * @param rc
	 *            The resource requirements of the VM
	 * @param vaSource
	 *            The storage where the VA resides.
	 * @param count
	 *            The number of VMs to be created with the above specification
	 * @return The virtual machine(s) that will be instantiated on the PM. Null if
	 *         the constraints specify VMs that cannot fit the available resources
	 *         of the machine.
	 * 
	 * @throws VMManagementException
	 *             If the machine is not accepting requests currently.<br>
	 *             If the VM startup has failed.
	 */
	public VirtualMachine[] requestVM(final VirtualAppliance va, final ResourceConstraints rc,
			final Repository vaSource, final int count) throws VMManagementException, NetworkException {
		final VirtualMachine[] vms = new VirtualMachine[count];
		final ResourceAllocation[] ras = new ResourceAllocation[count];
		boolean canDeployAll = true;
		for (int i = 0; i < count && canDeployAll; i++) {
			ras[i] = allocateResources(rc, true, defaultAllocLen);
			vms[i] = null;
			canDeployAll &= ras[i] != null && ras[i].allocated == rc;
		}
		if (canDeployAll) {
			for (int i = 0; i < count; i++) {
				vms[i] = new VirtualMachine(va);
				deployVM(vms[i], ras[i], vaSource);
			}
		} else {
			for (int i = 0; i < count && ras[i] != null; i++) {
				ras[i].cancel();
			}
		}
		return vms;
	}

	/**
	 * Requests a few VMs just as before. Scheduling constraints are ignored
	 * currently! As this is too low level to handle them in the current state of
	 * the simulator.
	 */
	@Override
	public VirtualMachine[] requestVM(VirtualAppliance va, ResourceConstraints rc, Repository vaSource, int count,
			HashMap<String, Object> schedulingConstraints) throws VMManagementException, NetworkException {
		return requestVM(va, rc, vaSource, count);
	}

	/**
	 * Switches off the VM in question if the VM is hosted by this particular PM.
	 * 
	 * @param vm
	 *            to be switched off
	 * @param killTasks
	 *            if the VM must be switched off without caring about its tasks then
	 *            this should be true. if this is false, then the VM can only be
	 *            instructed to be switched off if there are no tasks currently
	 *            running on it.
	 * @throws NoSuchVMException
	 *             if the VM is not hosted on this PM
	 * @throws VMManagementException
	 *             if the VM is not in a state to be switched off
	 */
	@Override
	public void terminateVM(final VirtualMachine vm, final boolean killTasks)
			throws NoSuchVMException, VMManagementException {
		if (!vms.contains(vm)) {
			throw new NoSuchVMException("Termination request was received for an unknown VM");

		}
		vm.switchoff(killTasks);
	}

	/**
	 * a method to query the currently running VMs (this can also be accessed
	 * through the public field of publicVms).
	 */
	@Override
	public Collection<VirtualMachine> listVMs() {
		return publicVms;
	}

	/**
	 * a method to determine the number of VMs currently hosted by the PM.
	 * 
	 * @return the number of VMs on the PM at the current time instance.
	 */
	public int numofCurrentVMs() {
		return vms.size();
	}

	/**
	 * Determines if a resource consumption (i.e., a compute task utilizing the CPU
	 * of the PM) can be registered on the PM for execution.
	 * 
	 * Registration is only allowed if (i.e. the return value is true only if):
	 * <ul>
	 * <li>the PM is running
	 * <li>the consumer of the consumption is either a VM or the directconsumer of
	 * the PM
	 * <li>the consumption is coming from the powerstatedelayer.
	 * </ul>
	 */
	@Override
	protected boolean isAcceptableConsumption(final ResourceConsumption con) {
		final ResourceSpreader consumer = con.getConsumer();
		final boolean internalConsumer = (consumer == directConsumer)
				&& (!directConsumerUsageMoratory || (onOffEvent != null && onOffEvent.currentConsumption == con));
		final boolean runningVirtualMachine;
		if (internalConsumer) {
			runningVirtualMachine = false;
		} else {
			if (consumer instanceof VirtualMachine) {
				runningVirtualMachine = vms.contains((VirtualMachine) consumer);
			} else {
				runningVirtualMachine = false;
			}
		}
		return (internalConsumer || runningVirtualMachine) && super.isAcceptableConsumption(con);
	}

	/**
	 * determines if there are any VMs on the PM or not.
	 * 
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if there are some VMs hosted on the PM.
	 *         <li><i>false</i> otherwise
	 *         </ul>
	 */
	public boolean isHostingVMs() {
		return !vms.isEmpty();
	}

	/**
	 * gets the number of VMs that have already left the PM but that were running on
	 * it once
	 * 
	 * @return the number of past VMs related to this PM
	 */
	public long getCompletedVMs() {
		return completedVMs;
	}

	/**
	 * Gets an estimate for the duration (in ticks) of the poweron/off operation in
	 * progress.
	 * 
	 * @return the estimated complete duration of the power state changing operation
	 * @throws IllegalStateException
	 *             if there is no power state change in progress
	 */
	public long getCurrentOnOffDelay() {
		if (onOffEvent == null) {
			switch (currentState) {
			case OFF:
				return onDelayEstimate;
			case RUNNING:
				return offDelayEstimate;
			default:
				throw new IllegalStateException("The onOffEvent is null while doing switchon/off");
			}
		} else {
			long remainingTime = onOffEvent.transitionStart - Timed.getFireCount();
			switch (currentState) {
			case SWITCHINGOFF:
				remainingTime += offDelayEstimate;
				break;
			case SWITCHINGON:
				remainingTime += onDelayEstimate;
				break;
			default:
				throw new IllegalStateException("The onOffEvent is not null while not in switchon/off mode");
			}
			return remainingTime;

		}
	}

	/**
	 * offers a nice single line format summary of the properties of this PM - good
	 * for debugging and tracing.
	 */
	@Override
	public String toString() {
		return "Machine(S:" + currentState + " C:" + internalReallyFreeCaps.getRequiredCPUs() + " M:"
				+ internalReallyFreeCaps.getRequiredMemory() + " " + localDisk.toString() + " " + super.toString()
				+ ")";
	}

	/**
	 * manages the subscriptions for state change events
	 * 
	 * @param sl
	 *            the listener object which expects state change events
	 */
	public void subscribeStateChangeEvents(final StateChangeListener sl) {
		stateListenerManager.subscribeToEvents(sl);
	}

	/**
	 * manages the subscriptions for state change events
	 * 
	 * @param sl
	 *            the listener object that no longer expects state change events
	 */
	public void unsubscribeStateChangeEvents(final StateChangeListener sl) {
		stateListenerManager.unsubscribeFromEvents(sl);
	}

	/**
	 * manages the state change operation of the PM.
	 * 
	 * Notifies the state change observers.
	 * 
	 * With the state change it also handles the power state changes according to
	 * the hostPowerBehavior, networkPowerBehavior and storagePowerBehavior maps.
	 * 
	 * @param newState
	 *            the new PM state to be set.
	 */
	private void setState(final State newState) throws NetworkException {
		try {
			localDisk.setState(NetworkNode.State.valueOf(newState.name()));
			// Behaviour change propagated
		} catch (IllegalArgumentException e) {
			// No need to propagate behaviour change
		}
		final State pastState = currentState;
		currentState = newState;
		directConsumerUsageMoratory = newState != State.RUNNING;
		stateListenerManager.notifyListeners(Pair.of(pastState, newState));

		// Power state management:
		setCurrentPowerBehavior(PowerTransitionGenerator.getPowerStateFromMap(hostPowerBehavior, newState.toString()));
	}

	/**
	 * collects the total resource capacity of the PM
	 */
	public ResourceConstraints getCapacities() {
		return totalCapacities;
	}

	/**
	 * not implemented
	 */
	@Override
	public void subscribeToCapacityChanges(final CapacityChangeEvent<ResourceConstraints> e) {
		// FIXME: not important yet
	}

	/**
	 * not implemented
	 */
	@Override
	public void unsubscribeFromCapacityChanges(final CapacityChangeEvent<ResourceConstraints> e) {
		// FIXME: not important yet
	}

	/**
	 * manages the subscriptions for free capacity events (i.e. those cases when
	 * there are some resources that are either not allocated anymore or when there
	 * is a VM that terminates on the PM)
	 * 
	 * @param e
	 *            the listener object which expects free capacity events
	 */
	public void subscribeToIncreasingFreeapacityChanges(final CapacityChangeEvent<ResourceConstraints> e) {
		increasingFreeCapacityListenerManager.subscribeToEvents(e);
	}

	/**
	 * manages the subscriptions for free capacity events (i.e. those cases when
	 * there are some resources that are either not allocated anymore or when there
	 * is a VM that terminates on the PM)
	 * 
	 * @param e
	 *            the listener object that no longer expects free capacity events
	 */
	public void unsubscribeFromDecreasingFreeCapacityChanges(final CapacityChangeEvent<ResourceConstraints> e) {
		decreasingFreeCapacityListenerManager.unsubscribeFromEvents(e);
	}

	/**
	 * manages the subscriptions for free capacity events (i.e. those cases when
	 * there are some resources that are either not allocated anymore or when there
	 * is a VM that terminates on the PM)
	 * 
	 * @param e
	 *            the listener object which expects free capacity events
	 */
	public void subscribeToDecreasingFreeapacityChanges(final CapacityChangeEvent<ResourceConstraints> e) {
		decreasingFreeCapacityListenerManager.subscribeToEvents(e);
	}

	/**
	 * manages the subscriptions for free capacity events (i.e. those cases when
	 * there are some resources that are either not allocated anymore or when there
	 * is a VM that terminates on the PM)
	 * 
	 * @param e
	 *            the listener object that no longer expects free capacity events
	 */
	public void unsubscribeFromIncreasingFreeCapacityChanges(final CapacityChangeEvent<ResourceConstraints> e) {
		increasingFreeCapacityListenerManager.unsubscribeFromEvents(e);
	}

	/**
	 * determines if the direct consumer accepts compute tasks to be registered
	 * 
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if new tasks can be set up between the direct
	 *         consumer and the PM
	 *         <li><i>false</i> otherwise
	 *         </ul>
	 */
	public boolean isDirectConsumerUsageMoratory() {
		return directConsumerUsageMoratory;
	}

	/**
	 * Rudimentary api to query security related behaviour of the PM
	 * 
	 * @return if the PM supports secure VMs (e.g., via Intel SGX) then this returns
	 *         true
	 */
	public boolean isSecure() {
		return secureExtensions;
	}

	/**
	 * Allows PM's secure behavior to be set. This is only recommended to be used
	 * during PM setup.
	 * 
	 * @param secureExtensions
	 */
	public void setSecure(boolean secureExtensions) {
		this.secureExtensions = secureExtensions;
	}
}
