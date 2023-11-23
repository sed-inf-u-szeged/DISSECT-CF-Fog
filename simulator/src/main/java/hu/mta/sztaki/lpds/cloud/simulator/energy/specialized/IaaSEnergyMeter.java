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

package hu.mta.sztaki.lpds.cloud.simulator.energy.specialized;

import hu.mta.sztaki.lpds.cloud.simulator.energy.AggregatedEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.EnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows a complete IaaS system to be monitored energywise with single energy
 * metering operations.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
 */
public class IaaSEnergyMeter extends AggregatedEnergyMeter implements VMManager.CapacityChangeEvent<PhysicalMachine> {
	/**
	 * The IaaSService to be observed with this meter
	 */
	private final IaaSService observed;
	/**
	 * record of the past capacity on the observed IaaSservice (used to
	 * determine in which direction did its capacity change).
	 */
	private ResourceConstraints oldCapacity;

	/**
	 * Allows the construction of a new metering object building on top of the
	 * generic aggregated energy meter concept
	 * 
	 * @param iaas
	 *            the IaaS to be monitored energywise
	 */
	public IaaSEnergyMeter(IaaSService iaas) {
		super(subMeterCreator(iaas.machines));
		observed = iaas;
		observed.subscribeToCapacityChanges(this);
		oldCapacity = observed.getCapacities();
	}

	/**
	 * This function creates a list of PhysicalMachineMeters from a list of
	 * physical machines.
	 * 
	 * @param machines
	 *            the list of machines from which the meter set must be created
	 * @return the list of meters
	 */
	private static List<EnergyMeter> subMeterCreator(List<PhysicalMachine> machines) {
		return machines.stream().map(PhysicalMachineEnergyMeter::new).collect(Collectors.toList());
	}

	/**
	 * manages the changes in size of the infrastructure (e.g. PM additions or
	 * removals) this is important to support dynamic IaaS systems where the
	 * metering results are still properly recorded
	 */
	@Override
	public void capacityChanged(ResourceConstraints newCapacity, List<PhysicalMachine> affectedCapacity) {
		long freq = -1;
		if (isSubscribed()) {
			freq = getFrequency();
			stopMeter();
		}
		if (newCapacity.compareTo(oldCapacity) < 0) {
			// Decreased
			Iterator<? extends EnergyMeter> meters = supervised.iterator();
			while (meters.hasNext()) {
				PhysicalMachineEnergyMeter m = (PhysicalMachineEnergyMeter) meters.next();
				if (affectedCapacity.remove(m.getObserved())) {
					meters.remove();
				}
			}
		} else {
			// Increased
			supervised.addAll(subMeterCreator(affectedCapacity));
		}
		oldCapacity = newCapacity;
		if (freq != -1) {
			startMeter(freq, false);
		}
	}

	/**
	 * Allows determining what is
	 * 
	 * @return the observed IaaS system
	 */
	public IaaSService getObserved() {
		return observed;
	}
}
