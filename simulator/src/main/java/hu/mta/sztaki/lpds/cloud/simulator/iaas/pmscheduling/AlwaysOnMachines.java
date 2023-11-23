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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler.QueueingEvent;

import java.util.List;

/**
 * This PM controller ensures that all newly registered PMs are switched on
 * immediately and newer turned off again.
 *
 * <i>WARNING:</i> using this PM controller does not guarantee that all the PMs
 * will be switched on in a given time instance. There are transient cases when
 * a just registered PM is not yet turned on completely.
 *
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 * of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 * Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class AlwaysOnMachines extends PhysicalMachineController {
    /**
     * Constructs the scheduler and passes the parent IaaSService to the superclass.
     *
     * @param parent the IaaSService to serve
     */
    public AlwaysOnMachines(final IaaSService parent) {
        super(parent);
    }

    /**
     * When a new PM is registered to the IaaS service the below controller
     * automatically turns it on.
     *
     * <i>WARNING</i> if one independently switches off a PM while it is registered
     * with the IaaS service, this controller will not turn it on again.
     */
    @Override
    protected VMManager.CapacityChangeEvent<PhysicalMachine> getHostRegEvent() {
        return (ResourceConstraints newCapacity, List<PhysicalMachine> alteredPMs) -> {
            final boolean newRegistration = parent.isRegisteredHost(alteredPMs.get(0));
            if (newRegistration) {
                alteredPMs.stream().filter(pm -> PhysicalMachine.ToOfforOff.contains(pm.getState())).forEach(PhysicalMachine::turnon);
            }
        };
    }

    /**
     * Describes an event handler that does nothing upon the start of VM queueing.
     * This PM controller would not have anything to do anyway as all the PMs in the
     * iaas are ensured to be on all the time.
     */
    @Override
    protected QueueingEvent getQueueingEvent() {
        // do nothing, we already have all the machines running
        return () -> {
        };
    }
}
