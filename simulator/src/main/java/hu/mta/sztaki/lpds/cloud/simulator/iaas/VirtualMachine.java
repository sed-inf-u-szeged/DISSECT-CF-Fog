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

import java.io.Serial;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;

import org.apache.commons.lang3.tuple.Triple;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinConsumer;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.statenotifications.VMStateChangeNotificationHandler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;

/**
 * 
 * This class represents a single virtual machine in the system. It simulates
 * its behavior. Currently it supports three approaches for VM image storage:
 * <ol>
 * <li>Local: The VA is expected to be in the PM's repository (caching), the
 * class ensures its cloning and operates on the clone for its entire lifetime.
 * During suspend the memory state is also stored in the PM's repository.
 * <li>Mixed: The VA is expected in a remote repository (e.g. it could reside in
 * the central service in an IaaS). The class creates a local copy of this VA
 * (in the PM's repository) so it can use it during the VM's lifetime. During
 * suspend the memory state is also stored in the PM's repository.
 * <li>Remote: The VA is expected in a remote repository where it is cloned. The
 * VM operates on the remote clone for its lifetime. During suspend the memory
 * state is also stored in the remote repository.
 * </ol>
 * 
 * Comparison of the 3 approaches:
 * <ul>
 * <li>Startup: Local could be the slowest if the PM first have to download the
 * VA to its local repo then cloning is needed. However, in case the VA is
 * already in the PM's repo then the Local approach is faster than Mixed because
 * it does not imply external network operations anymore. Remote starts up
 * immediately because there is no transfers are required.
 * <li>Execution: Local and Mixed approaches execute without any disruptions,
 * while the Remote approach continuously uses the network for its disk
 * operations, thus disk&amp;network intensive VMs will suffer significantly.
 * 
 * WARNING: the current implementation does not really reduce the processing
 * speed of a Remote storage based VM. This is future work.
 * <li>Migration: Local and Mixed approaches need to transfer both the memory
 * state and the disk image for the VM. In contrast, the Remote approach only
 * transfers the memory state between the new PM and the old one. Thus migration
 * is almost instant compared to the other two approaches.
 * </ul>
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2016"
 * @author "Vincenzo De Maio, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2015"
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012,2014-15"
 */
public class VirtualMachine extends MaxMinConsumer {
	/**
	 * This class is defined to ensure one can differentiate errors that were caused
	 * because the functions on the VM class are called in an improper order. E.g.
	 * migration cannot be done if the VM is not running already.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013"
	 * 
	 */
	public static class StateChangeException extends VMManagementException {
		@Serial
		private static final long serialVersionUID = 2950595344006507672L;

		/**
		 * The constructor allows a textual message so users of this class can see the
		 * reason of the exception more clearly without debugging.
		 * 
		 * @param e the message to be sent for the users of the simulator
		 */
		public StateChangeException(final String e) {
			super(e);
		}

	}

	/**
	 * This interface helps to receive events on status changes in virtual machines.
	 * One can subscribe to these events by calling the subscribeStateChange
	 * function. Afterwards whenever the VM changes its state it will automatically
	 * notify the subscribed entities.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013"
	 * 
	 */
	public interface StateChange {
		/**
		 * If the state of a VM is changed this function is called on all subscribing
		 * implementations.
		 * 
		 * @param oldState the state before the change was issued
		 * @param newState the state after the change took effect
		 */
		void stateChanged(VirtualMachine vm, State oldState, State newState);
	}

	/**
	 * This internal interface is used to customize internal state change actions in
	 * the VM class.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013"
	 * 
	 */
	private static class EventSetup {

		/**
		 * the state that the VM needs to be after the eventsetup completes
		 */
		public final State expectedState;

		public EventSetup(final State eState) {
			expectedState = eState;
		}

		/**
		 * Implementing this function allows the implementor to provide a custom VM
		 * state change function
		 */
		public void changeEvents(final VirtualMachine onMe) {
			onMe.setState(expectedState);
		}
	}

	/**
	 * Provides an implementation of the eventsetup class for the startup procedure
	 * which is modelled with a single task that utilizes a single core of the VM
	 * for a specified amount of time (in ticks)
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013"
	 * 
	 */
	private static class StartupProcedure extends EventSetup {
		/**
		 * initiates the class and remarks that the modeled state should be startup
		 */
		public StartupProcedure() {
			super(State.STARTUP);
		}

		/**
		 * Once the startup state is reached, the VM's boot process is imitated with a
		 * single core process which runs on the VM for a given amount of ticks.
		 */
		@Override
		public void changeEvents(final VirtualMachine onMe) {
			final State preEventState = onMe.currState;
			super.changeEvents(onMe);
			if (onMe.ra != null) {
				try {
					onMe.newComputeTask(onMe.va.getStartupProcessing(), onMe.ra.allocated.getRequiredProcessingPower(),
							new ConsumptionEventAdapter() {
								/**
								 * Once the startup process is complete we set the VM's state to running
								 */
								@Override
								public void conComplete() {
									super.conComplete();
									onMe.setState(State.RUNNING);
								}
							});
					return;
				} catch (NetworkException e) {
					// Ignore and allow state to be changed back
				}
			}
			if (expectedState.equals(onMe.currState)) {
				// If the failure is caused by us we set the state back
				onMe.setState(preEventState);
				// Otherwise we leave it as any other part of the simulation required it
			}
		}
	}

	/**
	 * The operations to do on shutdown
	 */
	private static final EventSetup sdEvent = new EventSetup(State.SHUTDOWN);
	/**
	 * The operations to do on switchon
	 */
	private static final EventSetup switchonEvent = new StartupProcedure();

	/**
	 * the virtual appliance that this VM is using for its disk
	 */
	private final VirtualAppliance va;
	/**
	 * the resource allocation of this VM (this is only not null when the VM is
	 * actually running on a pm, or about to run)
	 */
	private PhysicalMachine.ResourceAllocation ra = null;
	/**
	 * the VM's disk that is stored on the vatarget. if the VM is not past its
	 * initial transfer phase then the disk could be null.
	 */
	private StorageObject disk = null;
	/**
	 * if the VM is suspended then its memory is saved in the following storage
	 * object.
	 */
	private StorageObject savedmemory = null;
	/**
	 * where should the VM's virtual appliance be located
	 */
	private Repository vasource = null;
	/**
	 * where should the VM's disk storage be placed during the VM's initiation
	 * procedure.
	 */
	private Repository vatarget = null;

	/**
	 * the possible states of a virtual machine in DISSECT-CF.
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2012"
	 */
	public enum State {
		/**
		 * The VA of the machine is arranged to be usable for the execution. The VM is
		 * not consuming energy. There is no used storage.
		 */
		INITIAL_TR,
		/**
		 * The VM is booting up, and already consumes energy although it does not offer
		 * useful services for its user. The VM stores a clone of the VA in a
		 * repository.
		 */
		STARTUP,
		/**
		 * The VM is operating according to the user's needs. The VM consumes energy.
		 * The VM stores a clone of the VA in a repository.
		 */
		RUNNING,
		/**
		 * The VM is about to be suspended, and its memory is under serialization. The
		 * VM does not consume energy anymore. The VM stores a clone of the VA in a
		 * repository.
		 */
		SUSPEND_TR,
		/**
		 * The VM is awaiting to be resumed. It can be resumed fast and it can skip the
		 * bootup procedure. The VM does not consume energy. The VM stores a clone of
		 * the VA and its serialized memory in a repository.
		 */
		SUSPENDED,
		/**
		 * This is a rapid transitional state, once the VM's state is transferred but
		 * its tasks not yet resumed.
		 */
		SUSPENDED_MIG,
		/**
		 * The VM is about to be running. Its memory is transferred and deserialized.
		 * The VM still stores a clone of the VA and its serialized memory in a
		 * repository. The VM starts to consume energy for the deserialization.
		 */
		RESUME_TR,
		/**
		 * The VM is on the move between two Physical machines. During this operation it
		 * could happen that the VM and its serialized memory occupies disk space in two
		 * repositories. The VM starts to consume energy during the deserialization of
		 * its memory on the target PM.
		 */
		MIGRATING,
		/**
		 * The VM is not running. It's disk image (but not its memory state) can be
		 * found in the repository. So it is possible to start the VM up without the
		 * need for initial transfer. The VM is not consuming energy.
		 */
		SHUTDOWN,
		/**
		 * The VM is not running and it does not have any storage requirements in any of
		 * the repositories. The VM is not consuming energy.
		 */
		DESTROYED,
		/**
		 * The VM is destroyed, and it is not possible to instantiate it in the current
		 * cloud infrastructure (or the VM was terminated on user request before it was
		 * possible to instantiate it in the cloud)
		 */
		NONSERVABLE
	}

	/**
	 * the set of those VM states that are expected to consume energy
	 */
	public final static EnumSet<State> consumingStates = EnumSet.of(State.STARTUP, State.RUNNING, State.MIGRATING,
			State.RESUME_TR);
	/**
	 * the set of those VM states that are transferring VM related data
	 */
	public final static EnumSet<State> transferringStates = EnumSet.of(State.INITIAL_TR, State.SUSPEND_TR,
			State.RESUME_TR, State.MIGRATING);
	/**
	 * the states in which the VM is suspended to disk
	 */
	public final static EnumSet<State> suspendedStates = EnumSet.of(State.SUSPENDED, State.SUSPENDED_MIG);
	/**
	 * the states that can precede the startup phase
	 */
	public final static EnumSet<State> preStartupStates = EnumSet.of(State.DESTROYED, State.SHUTDOWN);
	/**
	 * the set of states that show that a VM scheduler was not able to schedule the
	 * VM (maybe just yet)
	 */
	public final static EnumSet<State> preScheduleState = EnumSet.of(State.DESTROYED, State.NONSERVABLE);

	/**
	 * the current state of the VM
	 */
	private State currState = State.DESTROYED;
	/**
	 * the local handler of VM state change events.
	 */
	private final StateDependentEventHandler<StateChange, Triple<VirtualMachine, State, State>> vmStateChangelistenerManager = VMStateChangeNotificationHandler
			.getHandlerInstance();

	/**
	 * the list of resourceconsumptions (i.e. compute tasks) that were suspended and
	 * need to be resumed after the VM itself is resumed.
	 */
	private ArrayList<ResourceConsumption> suspendedTasks;

	/**
	 * represents operations required for handling the virtual machine monitor
	 */
	private final HashMap<String, ResourceConsumption> currentVMMOperations = new HashMap<>();

	/**
	 * Allows easy ignoration of compute task completion in case the VM's storage is
	 * network backed (ignores network comm failures!)
	 */
	private final ConsumptionEvent computeTaskHelper = new ConsumptionEventAdapter();

	/**
	 * Instantiates a VM object
	 * 
	 * @param va the virtual appliance that should be the base for this VM,
	 * 
	 * @throws IllegalStateException if the va is <i>null</i>
	 */
	public VirtualMachine(final VirtualAppliance va) {
		super(0);
		if (va == null) {
			throw new IllegalStateException("Cannot accept nonexistent virtual appliances on instantiation");
		}
		this.va = va;
	}

	/**
	 * Always use this function to set the current VM state. This way all the
	 * interested parties get notified on the changes.
	 * 
	 * @param newstate The new state the VM is in.
	 */
	private void setState(final State newstate) {
		final State oldState = currState;
		currState = newstate;
		vmStateChangelistenerManager.notifyListeners(Triple.of(this, oldState, newstate));
	}

	/**
	 * Queries the VA used by this VM
	 * 
	 * @return the va used by this VM
	 */
	public VirtualAppliance getVa() {
		return va;
	}

	/**
	 * Queries the current state of the VM
	 * 
	 * @return the current vm state
	 */
	public State getState() {
		return currState;
	}

	/**
	 * Prepares the VM so it can be started without the need to clone its VA first.
	 * This function is useful in advanced scheduling situations.
	 * 
	 * @param vatarget the disk that will host the VM.
	 * @param vasource the repository where the VA for this VM is found. If null,
	 *                 the function assumes it is found in the hosting PM's
	 *                 repository.
	 * @throws StateChangeException  if the VM is not destroyed
	 * @throws VMManagementException if the VA transfer failed and the state change
	 *                               was reverted
	 */
	public void prepare(final Repository vasource, final Repository vatarget)
			throws VMManagementException, NetworkNode.NetworkException {
		if (currState != State.DESTROYED) {
			throw new StateChangeException("The VM is not destroyed");
		}
		initialTransfer(vasource, vatarget, sdEvent);
	}

	/**
	 * The event that will be received upon the completion of the VA's copy from
	 * vasource to vatarget.
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2012"
	 * 
	 * 
	 */
	class InitialTransferEvent extends ConsumptionEventAdapter {
		/**
		 * the target repository where the VA is expected to turn up
		 */
		final Repository target;
		/**
		 * the event to be fired after the transfer is complete
		 */
		final EventSetup esetup;
		/**
		 * the expected id of the storage object in the target repository
		 */
		final String diskid;

		/**
		 * Initiates the event handler object
		 * 
		 * @param t     the repository to observe
		 * @param event the event to fire after the transfer to the target repo happened
		 * @param did   the disk id of the newly created storage object in the target
		 *              repo for the new runnable VA.
		 */
		public InitialTransferEvent(final Repository t, final EventSetup event, final String did) {
			target = t;
			esetup = event;
			diskid = did;
		}

		/**
		 * stores the newly transferred storage object into the VM's disk field, and
		 * then continues with the next step in the VM state management (represented
		 * with the event setup)
		 */
		@Override
		public void conComplete() {
			currentVMMOperations.clear();
			disk = target.lookup(diskid);
			esetup.changeEvents(VirtualMachine.this);
		}

		@Override
		public void conCancelled(ResourceConsumption problematic) {
			currentVMMOperations.clear();
			releaseRa(State.DESTROYED);
		}

	}

	private void ensureEmptyVMMOperationList() throws VMManagementException {
		if (!currentVMMOperations.isEmpty()) {
			throw new VMManagementException("Other VMM related background operation is in progress!");
		}
	}

	/**
	 * Ensures the transfer of the VM to the appropriate location. The location is
	 * determined based on the VM storage approach used. While transferring it
	 * maintains the INITIAL_TR state. If there is a fault it falls back to the
	 * state beforehand. If successful it allows the caller to change the event on
	 * the way it sees fit.
	 * 
	 * @param vatarget the storage that will host the VM's working image (typically
	 *                 this is going to be the disk of the PM that will host the
	 *                 VM).
	 * @param vasource the repository where the VA for this VM is found. If null,
	 *                 the function assumes it is found in the hosting PM's
	 *                 repository.
	 * @param es       The way the VM's state should be changed the function will
	 *                 fire an event on this channel if the VA is cloned properly
	 * @throws VMManagementException if the VA transfer failed and the state change
	 *                               was reverted
	 * @throws NetworkException      if the VA's transfer cannot be completed (e.g.,
	 *                               because of connectivity issues)
	 */
	private void initialTransfer(final Repository vasource, final Repository vatarget, final EventSetup es)
			throws VMManagementException, NetworkNode.NetworkException {
		final State oldState = currState;
		final long bgnwload = va.getBgNetworkLoad();
		if (bgnwload > 0 && vasource == vatarget) {
			throw new VMManagementException("Cannot initiate a transfer for remotely running VM on the remote site!");
		}
		ensureEmptyVMMOperationList();
		setState(State.INITIAL_TR);
		// Allows the immediate cancellation of the transfer i.e., in reaction
		// to the INITIAL_TR change
		if (State.INITIAL_TR.equals(currState)) {
			this.vasource = vasource;
			this.vatarget = vatarget;
			final String diskid = "VMDisk-of-" + hashCode();
			ResourceConsumption currentVMMOperation = null;
			if (bgnwload > 0) {
				// Remote scenario
				currentVMMOperation = vasource.duplicateContent(va.id, diskid,
						new InitialTransferEvent(vasource, es, diskid));
			} else {
				if (vasource == null) {
					// Entirely local scenario
					if (vatarget != null) {
						currentVMMOperation = vatarget.duplicateContent(va.id, diskid,
								new InitialTransferEvent(vatarget, es, diskid));
					}
				} else {
					// Mixed scenario
					currentVMMOperation = vasource.requestContentDelivery(va.id, diskid, vatarget,
							new InitialTransferEvent(vatarget, es, diskid));
				}
			}
			if (currentVMMOperation != null) {
				currentVMMOperations.put(currState.toString(), currentVMMOperation);
			} else {
				setState(oldState);
				throw new VMManagementException("Initial transfer failed");
			}
		}
	}

	/**
	 * Initiates the startup procedure of a VM. If the VM is in destroyed state then
	 * it ensures the disk image for the VM is ready to be used (i.e. first it
	 * starts the inittransfer procedure).
	 * 
	 * @param allocation the resource allocation which will be used to deploy the VM
	 *                   on.
	 * @param vasource   the repository where the VA for this VM is found. If null,
	 *                   the function assumes it is found in the hosting PM's
	 *                   repository.
	 * @throws StateChangeException  if the VM is not destroyed or shutdown
	 * @throws VMManagementException if the VA transfer failed and the state change
	 *                               was reverted
	 */
	public void switchOn(final PhysicalMachine.ResourceAllocation allocation, final Repository vasource)
			throws VMManagementException, NetworkNode.NetworkException {
		switch (currState) {
			case DESTROYED -> {
				setResourceAllocation(allocation);
				initialTransfer(vasource, allocation.getHost().localDisk, switchonEvent);
			}
			case SHUTDOWN -> {
				// Shutdown has already done the transfer, we just need to make sure
				// the VM will get through its boot procedure
				if (allocation.getHost().localDisk != vatarget) {
					// TODO: maybe we can switch back to destroyed
					throw new VMManagementException("VM was not prepared for this PM");
				}
				setResourceAllocation(allocation);
				switchonEvent.changeEvents(this);
			}
			default -> throw new StateChangeException("The VM is not shut down or destroyed");
		}
	}

	/**
	 * Forwards the migration call internally allowing both live/non-live migrations
	 * 
	 * @param target the new resource allocation on which the resume operation
	 *               should take place
	 * @throws StateChangeException  if the VM is not in running state currently
	 * @throws VMManagementException if the system have had troubles during the
	 *                               suspend operation.
	 */
	public void migrate(final PhysicalMachine.ResourceAllocation target)
			throws VMManagementException, NetworkNode.NetworkException {
		migrate(target, false);
	}

	/**
	 * Moves all data necessary for the VMs execution from its current physical
	 * machine to another. Supports migration from suspended state.
	 * 
	 * @param target            the new resource allocation on which the resume
	 *                          operation should take place
	 * @param onlyLiveMigration
	 *                          <ul>
	 *                          <li><i>true</i> live migration is explicitly
	 *                          requested (if not possible, a VMManagementException
	 *                          will be thrown)
	 *                          <li><i>false</i> if live migration is prioritised but
	 *                          if not possible, non-live will be performed
	 *                          </ul>
	 * @throws StateChangeException  if the VM is not in running or suspended state
	 *                               currently
	 * @throws VMManagementException if the system have had troubles during the
	 *                               migration operation (e.g., storage and
	 *                               connectivity issues).
	 */
	public void migrate(final PhysicalMachine.ResourceAllocation target, boolean onlyLiveMigration)
			throws VMManagementException, NetworkNode.NetworkException {
		/**
		 * This class prepares and transitions a VM through the phases of migration
		 * (both live and non live)
		 * 
		 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
		 *         Moores University, (c) 2017"
		 * @author "Vincenzo De Maio, Distributed and Parallel Systems Group, University
		 *         of Innsbruck (c) 2015"
		 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
		 *         of Innsbruck (c) 2013"
		 * 
		 */
		class MigrationEvent extends ConsumptionEventAdapter {
			/**
			 * When did the last memory transfer start for the current migration process
			 */
			private long lastMemorySnapshotCreatedAt = -1;
			/**
			 * Maximum number of memory transfers to be done before giving up and doing a
			 * non-live final transfer
			 */
			public final static long maxRounds = 5;
			/**
			 * Smallest written working set size that still allows new memory copy rounds
			 */
			public final static long WWS_TERMINAL_SIZE = 262144;
			/**
			 * the secondary resource allocation of this VM (this is only not null when the
			 * VM is in migration)
			 */
			private final PhysicalMachine.ResourceAllocation migrationRa;
			/**
			 * If we are doing live migration
			 */
			private boolean liveMigration = true;
			/**
			 * Pre migration state to return to in case of an error
			 */
			final State prevState = currState;
			/**
			 * The local storage of the target host for this migration
			 */
			final Repository to;
			/**
			 * Non-live transfer count, 1=only memory, 2= disk and memory together
			 */
			int eventcounter = 1;
			/**
			 * Number of memory copy rounds done during the current migration process
			 */
			int rounds = 0;

			public MigrationEvent(PhysicalMachine.ResourceAllocation newRa, boolean onlyLiveMigration)
					throws VMManagementException, NetworkNode.NetworkException {
				ensureEmptyVMMOperationList();
				switch (currState) {
				case SUSPENDED:
					if (onlyLiveMigration) {
						throw new StateChangeException("Live migration cannot be forced in this state!");
					}
					setNonLive();
				case RUNNING:
					break;
				default:
					throw new StateChangeException("Invalid starting state for a VM migration: "+currState);
				}
				migrationRa = newRa;
				to = migrationRa.getHost().localDisk;
				if (va.getBgNetworkLoad() <= 0) {
					// If the disk is local&mixed image storage
					if (onlyLiveMigration) {
						throw new VMManagementException(
								"Live migration is only allowed if the VM runs from a remote disk");
					}
					NetworkNode.checkConnectivity(vatarget, to);
					setNonLive();
					eventcounter++;
					ResourceConsumption currentVMMOperation;
					if ((currentVMMOperation = vatarget.requestContentDelivery(disk.id, to, this)) != null) {
						currentVMMOperations.put(currState.toString() + disk.id, currentVMMOperation);
					} else {
						setState(prevState);
						throw new VMManagementException(
								"Cannot transfer the disk of the VM during non-live migration.");
					}
				}
				newMemoryRound();
			}

			private void setNonLive() {
				if (liveMigration) {
					liveMigration = false;
					suspendTasks();
					setState(State.MIGRATING);
				}
			}

			private long newMemoryRound() throws NetworkException {
				// If we come from suspend, savedmemory is used.
				final long memSize = savedmemory == null ? identifyWWS() : savedmemory.size;
				// This replaces any previous memory related transfers
				currentVMMOperations.put(currState.toString() + "Migrate Memory",
						NetworkNode.initTransfer(memSize, ResourceConsumption.unlimitedProcessing, vatarget, to, this));
				return memSize;
			}

			/**
			 * Determine the amount of memory dirtied since the last call
			 * <p>
			 * Done as per eq 17 from the paper "<i>De Maio, V., Kecskemeti, G., & Prodan,
			 * R. (2016, December). An improved model for live migration in data centre
			 * simulators. In Utility and Cloud Computing (UCC), 2016 IEEE/ACM 9th
			 * International Conference on (pp. 108-117). IEEE.</i>"
			 * 
			 * @return the total dirtying rate on the VM
			 */
			private long identifyWWS() {
				long previousSnapshot = lastMemorySnapshotCreatedAt;
				lastMemorySnapshotCreatedAt = Timed.getFireCount();
				if (previousSnapshot == -1) {
					return getMemSize();
				}
				long duration = lastMemorySnapshotCreatedAt - previousSnapshot;
				long dirtyBytes = 0;
				for (ResourceConsumption r : underProcessing) {
					dirtyBytes += (Math.min(1, duration * r.getMemDirtyingRate()) * r.getMemSize());
				}
				return dirtyBytes;
			}

			@Override
			public void conComplete() {
				// Cross cloud migration needs an update on the vastorage also,
				// otherwise the VM will use a long distance repository for its
				// background network load!
				if (liveMigration) {
					try {
						long memSizeRemaining = newMemoryRound();
						if (rounds++ >= maxRounds || memSizeRemaining < WWS_TERMINAL_SIZE) {
							// Switching to final, non-live phase
							setNonLive();
						}
					} catch (NetworkException ne) {
						// These exceptions should have occurred while
						// registering the first transfer
						throw new RuntimeException(ne);
					}
				} else {
					// In case we came here from suspend
					if (savedmemory != null) {
						vatarget.deregisterObject(savedmemory);
						savedmemory = null;
					}
					eventcounter--;
					// Only continue if we have done all required transfers
					if (eventcounter == 0) {
						currentVMMOperations.clear();
						// Both the disk and memory state has completed its
						// transfer.
						try {
							if (suspendedTasks != null) {
								for (final ResourceConsumption con : suspendedTasks) {
									con.setProvider(migrationRa.getHost());
								}
							}
							releaseRa(State.SUSPENDED_MIG);
							setResourceAllocation(migrationRa);
							if (va.getBgNetworkLoad() <= 0) {
								// Local&Mixed scenario, the disk was moved
								vatarget.deregisterObject(disk);
								vatarget = ra.getHost().localDisk;
							}
							lastMemorySnapshotCreatedAt = -1;
							resumeTasks();
						} catch (VMManagementException e) {
							// Sudden exceptions that should not really happen
							throw new RuntimeException(e);
						}
					}
				}
			}

			/**
			 * migration related transfers cancelled, revert our actions
			 */
			@Override
			public void conCancelled(ResourceConsumption problematic) {
				lastMemorySnapshotCreatedAt = -1;
				currentVMMOperations.clear();
				migrationRa.cancel();
				if (!currState.equals(prevState)) {
					setState(prevState);
				}
			}
		}

		new MigrationEvent(target, onlyLiveMigration);
	}

	/**
	 * Moves all data necessary for the VMs execution from its current physical
	 * machine to another.
	 *
	 * WARNING: in cases when the migration cannot complete, the VM will be left in
	 * a special suspended state: the SUSPENDED_MIG. This allows users to recover
	 * VMs and initiate the migration procedure to someplace else.
	 *
	 *
	 * @param target the new resource allocation on which the resume operation
	 *               should take place
	 * @throws StateChangeException  if the VM is not in running state currently
	 * @throws VMManagementException if the system have had troubles during the
	 *                               suspend operation.
	 * @throws NetworkException      if target host is not reachable
	 */

	public void migrateLive(final PhysicalMachine.ResourceAllocation target)
			throws VMManagementException, NetworkNode.NetworkException {
		migrate(target, true);
	}

	/**
	 * Destroys the VM, and cleans up all repositories that could contain disk or
	 * memory states.
	 * 
	 * @throws StateChangeException if some parts of the VM are under transfer so it
	 *                              cannot be determined what to clean up.
	 */
	public void destroy(final boolean killTasks) throws VMManagementException {
		if (!currentVMMOperations.isEmpty()) {
			ResourceConsumption[] rcList = currentVMMOperations.values()
					.toArray(new ResourceConsumption[0]);
			boolean wasRegistered = false;
			for (ResourceConsumption currentVMMOperation : rcList) {
				wasRegistered |= currentVMMOperation.isRegistered();
				currentVMMOperation.cancel();
			}
			if (wasRegistered) {
				// Wait until the cancel takes effect - registration cleanup
				DeferredEvent.deferAction(1, () -> {
					try {
						destroy(killTasks);
					} catch (VMManagementException e) {
						// TODO: allow more graceful exception handling
						throw new RuntimeException(e);
					}
				});
				return;
			}
		}
		finalizeDestroy(killTasks);
	}

	private void finalizeDestroy(final boolean killTasks) throws VMManagementException {
		if (ra != null) {
			switchoff(killTasks);
		}
		if (vasource != null) {
			vasource.deregisterObject(disk);
			vasource.deregisterObject(savedmemory);
			vasource = null;
		}
		if (vatarget != null) {
			vatarget.deregisterObject(disk);
			vatarget.deregisterObject(savedmemory);
		}
		if (!State.DESTROYED.equals(currState)) {
			setState(State.DESTROYED);
		}
	}

	/**
	 * Switches off an already running machine. After the successful execution of
	 * this function, the VM's state will be shutdown.
	 * 
	 * @throws StateChangeException if is not running currently
	 */
	public void switchoff(final boolean killTasks) throws StateChangeException {
		if (currState != State.RUNNING && currState != State.STARTUP) {
			throw new StateChangeException("Cannot switch off a not running machine");
		}
		if (killTasks) {
			suspendedTasks = new ArrayList<>(underProcessing);
			for (final ResourceConsumption con : suspendedTasks) {
				con.cancel();
			}
			suspendedTasks = null;
		} else if (!underProcessing.isEmpty()) {
			throw new StateChangeException("Cannot switch off a running machine with running tasks");
		}
		releaseRa(State.SHUTDOWN);
	}

	/**
	 * When the VM is about to decouple from a PM this function is called.
	 * 
	 * @param newState the state the VM needs to be in after the decoupling is done
	 */
	private void releaseRa(State newState) {
		if (ra != null) {
			ra.release();
			ra = null;
		}
		setState(newState);
	}

	/**
	 * Suspends an already running VM. During the suspend operation, the VM's memory
	 * state is stored to enable the resume operation for fast VM startup.
	 * 
	 * The VM's memory state is first created as a storage object on the PM's local
	 * repository then transferred to its designated location.
	 * 
	 * @throws StateChangeException  if the machine is not in running state
	 * @throws VMManagementException if there is not enough space to store the
	 *                               memory state (first locally, then
	 *                               locally/remotely depending on the VM storage
	 *                               scenario
	 */
	public void suspend() throws VMManagementException, NetworkNode.NetworkException {
		if (currState != State.RUNNING) {
			throw new StateChangeException("Cannot suspend a not running machine");
		}
		suspendTasks();
		final Repository pmdisk = ra.getHost().localDisk;
		savedmemory = new StorageObject("VM-Memory-State-of-" + hashCode(), getMemSize(), false);
		setState(State.SUSPEND_TR);
		ResourceConsumption currentVMMOperation;
		if ((currentVMMOperation = pmdisk.storeInMemoryObject(savedmemory, new ConsumptionEventAdapter() {
			@Override
			public void conComplete() {
				currentVMMOperations.clear();
				releaseRa(State.SUSPENDED);
			}

			@Override
			public void conCancelled(ResourceConsumption problematic) {
				currentVMMOperations.clear();
				setState(State.RUNNING);
				savedmemory = null;
			}
		})) == null) {
			// Set back the status so it is possible to try again
			setState(State.RUNNING);
			String sid = savedmemory.id;
			savedmemory = null;
			throw new VMManagementException("Not enough space on localDisk for the suspend operation of " + sid);
		} else {
			currentVMMOperations.put(currState.toString(), currentVMMOperation);
		}
	}

	/**
	 * Stops the computation of all tasks at this VM, it allows for their resumption
	 * later on
	 */
	private void suspendTasks() {
		if (toBeAdded.size() + underProcessing.size() > 0) {
			if (suspendedTasks == null) {
				suspendedTasks = new ArrayList<>(underProcessing);
			} else {
				suspendedTasks.addAll(underProcessing);
			}
			suspendedTasks.addAll(toBeAdded);

			suspendedTasks.forEach(ResourceConsumption::suspend);
		}
	}

	/**
	 * Re-registers all previously suspended tasks and marks the VM running
	 */
	private void resumeTasks() {
		setState(State.RUNNING);
		if (suspendedTasks != null) {
			suspendedTasks.forEach(ResourceConsumption::registerConsumption);
			suspendedTasks = null;
		}
	}

	/**
	 * Resumes an already suspended VM. During the resume operation, the VM's memory
	 * state is used for fast VM startup.
	 * 
	 * The VM's memory state is first transferred to the PM's repository (simulating
	 * its loading to the VM's memory). Afterwards, this function cleans up the
	 * memory states as after startup the VM already changes them.
	 * 
	 * @throws StateChangeException  if the machine is not in suspended state
	 * @throws VMManagementException if there is not enough space to retrieve the
	 *                               memory state to the PM's repository
	 */
	public void resume() throws VMManagementException, NetworkNode.NetworkException {
		if (!currState.equals(State.SUSPENDED)) {
			throw new StateChangeException("Cannot resume a not suspended machine");
		}
		ensureEmptyVMMOperationList();
		final State priorState = currState;
		setState(State.RESUME_TR);
		final Repository pmdisk = ra.getHost().localDisk;
		class ResumeComplete extends ConsumptionEventAdapter {
			private void cleanUpIntermediateData() {
				savedmemory = null;
				currentVMMOperations.clear();
			}

			@Override
			public void conComplete() {
				// Deregister saved memory
				pmdisk.deregisterObject(savedmemory);
				cleanUpIntermediateData();
				resumeTasks();
			}

			@Override
			public void conCancelled(ResourceConsumption problematic) {
				cleanUpIntermediateData();
				setState(priorState);
			}
		}
		ResourceConsumption currentVMMOperation;
		if ((currentVMMOperation = pmdisk.fetchObjectToMemory(savedmemory, new ResumeComplete())) == null) {
			// Set back the status so it is possible to try again
			setState(priorState);
			throw new VMManagementException("Failed to fetch the stored memory " + savedmemory + " from PM "
					+ pmdisk.getName() + " for the resume operation of VM " + hashCode());
		} else {
			currentVMMOperations.put(currState.toString(), currentVMMOperation);
		}
	}

	/**
	 * Use this function to get notified about state changes on this VM
	 * 
	 * @param consumer the party to be notified when the state changes
	 */
	public void subscribeStateChange(final StateChange consumer) {
		vmStateChangelistenerManager.subscribeToEvents(consumer);
	}

	/**
	 * Use this function to be no longer notified about state changes on this VM
	 * 
	 * @param consumer the party who previously received notifications
	 */
	public void unsubscribeStateChange(final StateChange consumer) {
		vmStateChangelistenerManager.unsubscribeFromEvents(consumer);
	}

	/**
	 * determines if a resourceconsumption object can be registered. it is going to
	 * be determined acceptable all the time if the VM is in one of its consuming
	 * states.
	 */
	@Override
	protected boolean isAcceptableConsumption(ResourceConsumption con) {
		return consumingStates.contains(currState) && super.isAcceptableConsumption(con);
	}

	/**
	 * This is the function that users are expected to use to create computing tasks
	 * on the VMs (not using resourceconsumptions directly). The computing tasks
	 * created with this function are going to utilize CPUs and if there is a
	 * background load defined for the VM then during the processing of the CPU
	 * related activities there will be constant network activities modelled as
	 * well. The background network load represents cases when the VM's disk is
	 * hosted on the network (and thus inherent disk activities that would occur
	 * during CPU related activities can be modeled). Please note the background
	 * load is the property of the VA (i.e. the VA represents an application and
	 * therefore one who creates the VA should know its disk related activities when
	 * the application is executed)
	 * 
	 * @param total the amount of processing to be done (in number of instructions)
	 * @param limit the amount of processing this new compute task is allowed to do
	 *              in a single tick (in instructions/tick). If there should be no
	 *              limit for the processing then one can use the constant named
	 *              ResourceConsumption.unlimitedProcessing.
	 * @param e     the object to be notified about the completion of the
	 *              computation ordered here
	 * @return the resource consumption object that will represent the CPU
	 *         consumption. Could return null if the consumption cannot be
	 *         registered or when there is no resource for the VM
	 * @throws NetworkException if the background network load is not possible to
	 *                          simulate.
	 */
	public ResourceConsumption newComputeTask(final double total, final double limit,
			final ResourceConsumption.ConsumptionEvent e) throws NetworkException {
		if (ra == null) {
			return null;
		}
		ResourceConsumption cons = new ResourceConsumption(total, limit, VirtualMachine.this, ra.getHost(), e);
		if (cons.registerConsumption()) {
			final long bgnwload = va.getBgNetworkLoad();
			if (bgnwload > 0) {
				final long minBW = Math.min(bgnwload,
						Math.min(ra.getHost().localDisk.getOutputbw(), vasource.getInputbw()));
				NetworkNode.initTransfer(minBW * cons.getCompletionDistance(), minBW, ra.getHost().localDisk, vasource,
						computeTaskHelper);
			}
			return cons;
		} else {
			return null;
		}
	}

	public ResourceConsumption newComputeTask(final double total, final double limit,
			final ResourceConsumption.ConsumptionEvent e, final double dirtyingRate, final long memSize)
			throws NetworkException {
		ResourceConsumption rc = newComputeTask(total, limit, e);
		if (rc != null) {
			rc.setMemDirtyingRate(dirtyingRate);
			rc.setMemSize(memSize);
		}
		return rc;
	}

	/**
	 * Allows to set a new resource allocation for the VM
	 * 
	 * This function will notify the resource allocation about the acquisition of
	 * the resources by utilizing the use function!
	 * 
	 * @param newRA the allocation to be used later on
	 * @throws VMManagementException if the VM is already running using a host
	 */
	public void setResourceAllocation(PhysicalMachine.ResourceAllocation newRA) throws VMManagementException {
		switch (currState) {
			case DESTROYED, SUSPENDED, SHUTDOWN, SUSPENDED_MIG -> {
				ra = newRA;
				ra.use(this);
				setPerTickProcessingPower(ra.allocated.getTotalProcessingPower());
			}
			default -> throw new StateChangeException(
					"The VM is already bound to a host please first resolve the VM-Host association!");
		}
	}

	/**
	 * Determines what is the resource allocation currently used by the VM.
	 * 
	 * @return the resource set used by the VM, null if there is no resource used by
	 *         the VM currently
	 */
	public PhysicalMachine.ResourceAllocation getResourceAllocation() {
		return ra;
	}

	/**
	 * If there are not enough resources for the VM currently, to recover from this
	 * state (and allow the VM to be rescheduled) just issue a destroy command on
	 * the VM
	 */
	public void setNonservable() {
		setState(State.NONSERVABLE);
	}

	/**
	 * Helper function to get the total size of memory easier than through the VM's
	 * allocation
	 * 
	 * @return the amount of memory used by the VM
	 */
	public long getMemSize() {
		return this.ra.allocated.getRequiredMemory();
	}

	/**
	 * A nice single line output for the VM that shows its state and resource
	 * allocation as well as the tasks it is running. Good for debugging and
	 * tracing.
	 */
	@Override
	public String toString() {
		return "VM(" + currState + " " + ra + " " + super.toString() + ")";
	}
}
