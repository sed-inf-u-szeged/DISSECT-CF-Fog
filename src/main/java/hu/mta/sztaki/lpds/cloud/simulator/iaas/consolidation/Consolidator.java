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
 */

package hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

/**
 * This class is to be sub-classed by all workload consolidators. The only focus
 * of this class is to make sure we have a consolidation algorithm running
 * periodically when there is a need for it (i.e., there are machines hosting
 * virtual machines in the underlying IaaS)
 *
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 */
public abstract class Consolidator extends Timed {
	private final long consFreq;
	private long consolidationRuns = 0;
	protected final IaaSService toConsolidate;
	private boolean resourceAllocationChange = false;
	private boolean omitAllocationCheck = false;

	/**
	 * This inner class ensures that the consolidator receives its periodic events
	 * if new VMs were just added to the IaaS under consolidation.
	 *
	 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
	 *         Moores University, (c) 2016"
	 */
	private class VMListObserver implements VMManager.CapacityChangeEvent<ResourceConstraints> {
		private final PhysicalMachine pm;

		/**
		 * Subscribes for free capacity change events.
		 *
		 * @param forMe The to be observed PhysicalMachine object.
		 */
		public VMListObserver(PhysicalMachine forMe) {
			forMe.subscribeToDecreasingFreeapacityChanges(this);
			forMe.subscribeToIncreasingFreeapacityChanges(this);
			pm = forMe;
			capacityChanged(null, null);
		}

		/**
		 * Receives the usage related notifications from the observed physical machine
		 * and ensures the subscription of the consolidator in case this is the first VM
		 * the consolidator should know about.
		 */
		@Override
		public void capacityChanged(ResourceConstraints newCapacity, List<ResourceConstraints> affectedCapacity) {
			if (pm.isHostingVMs() && !isSubscribed()) {
				subscribe(consFreq);
			}
			resourceAllocationChange = true;
		}

		/**
		 * To be called, so we don't keep the observer object in the pm's subscriber list
		 * if there is no need for observing anymore.
		 */
		public void cancelSubscriptions() {
			pm.unsubscribeFromDecreasingFreeCapacityChanges(this);
			pm.unsubscribeFromIncreasingFreeCapacityChanges(this);
		}
	}

	/**
	 * All PMs in the to be consolidated IaaSService are observed by these observer
	 * objects.
	 */
	private final HashMap<PhysicalMachine, VMListObserver> observers = new HashMap<>();

	private final Consumer<PhysicalMachine> extendObsevers = pm -> observers.put(pm, new VMListObserver(pm)); // Management of capacity increase

	/**
	 * This constructor ensures the proper maintenance of the observer list - i.e.,
	 * the list of objects that ensure we start to receive periodic events for
	 * consolidation as soon as we have some VMs running on the infrastructure.
	 *
	 * @param toConsolidate The cloud infrastructure to be continuously consolidated
	 * @param consFreq      The frequency with which the actual consolidator
	 *                      algorithm should be called. Note: this class does not
	 *                      necessarily hold on to this frequency. But guarantees
	 *                      there will be no more frequent events. In cases when the
	 *                      infrastructure is not used, the actual applied frequency
	 *                      could be dramatically lengthened.
	 */
	public Consolidator(IaaSService toConsolidate, long consFreq) {
		// TODO: merge some of the below functionality with several similar
		// others from other parts of the simulator
		this.consFreq = consFreq;
		this.toConsolidate = toConsolidate;
        // Let's see if there are machines to observe already
        toConsolidate.machines.forEach(extendObsevers);
        // Let's make sure we observe all machines even if they are added to the
        // system later on
        toConsolidate.subscribeToCapacityChanges(
                (ResourceConstraints newCapacity, List<PhysicalMachine> affectedCapacity) -> {
                    var newRegistration = Consolidator.this.toConsolidate
                            .isRegisteredHost(affectedCapacity.get(0));
                    final Consumer<PhysicalMachine> pmAction =
                            newRegistration ? extendObsevers :
                                    pm -> observers.remove(pm).cancelSubscriptions(); // Management of capacity decrease
                    affectedCapacity.forEach(pmAction);
                });
	}

	/**
	 * This function checks the PM list of the IaaS service under consolidation and
	 * ensures that the consolidation algorithm only runs if there are VMs in the
	 * system. If it does not find any VMs, then it actually cancels further
	 * periodic events, and waits for the help of the VM observers to get a
	 * subscription again.
	 *
	 * If allocation checks are not omitted then the consolidation algorithm is only
	 * called when VM allocation changes were made. Notice that this is not
	 * necessarily related to the VM's workload or its impact on the PM's actual
	 * resource utilisation. Thus, consolidators which want to take into account
	 * actual resource usage for their decisions should always set the allocation
	 * check omission to true!
	 */
	@Override
	public void tick(long fires) {
		if (resourceAllocationChange || omitAllocationCheck) {
            if (toConsolidate.runningMachines.isEmpty() && toConsolidate.sched.getQueueLength() == 0) {
                unsubscribe();
                return;
            }
            prepConsolidation();
			resourceAllocationChange = false;
		}
	}

	/**
	 * This method makes sure the doConsolidation method is called with the appropriate set of pms while it also ensures
	 * that the consolidator no longer receives timing events if there is no need for it anymore
	 */
    private void prepConsolidation() {
        // Should we consolidate next time?
        var thereWereVMs = toConsolidate.machines.stream().anyMatch(PhysicalMachine::isHostingVMs);
        consolidationRuns++;
        // Make a copy as machines as they could be sold/removed if they are not used because of
        // consolidation...
        doConsolidation(toConsolidate.machines
                .toArray(new PhysicalMachine[0]));
        if (!thereWereVMs) {
            // No we should not
            unsubscribe();
        }
    }

	/**
	 * Allows querying if allocation checks could limit consolidator calls
	 *
	 * @return
	 */
	public boolean isOmitAllocationCheck() {
		return omitAllocationCheck;
	}

	/**
	 * Subclasses can influence their invocations by disabling the effect of
	 * allocation change based consolidator calls
	 *
	 * @param omitAllocationCheck
	 */
	protected void setOmitAllocationCheck(boolean omitAllocationCheck) {
		this.omitAllocationCheck = omitAllocationCheck;
	}

	/**
	 * The implementations of this function should provide the actual consolidation
	 * algorithm.
	 *
	 * @param pmList the list of PMs that are currently in the IaaS service. The
	 *               list can be modified at the will of the consolidator algorithm,
	 *               as it is a copy of the state of the machine list before the
	 *               consolidation was invoked.
	 */
	protected abstract void doConsolidation(PhysicalMachine[] pmList);

    public long getConsolidationRuns() {
        return consolidationRuns;
    }
}
