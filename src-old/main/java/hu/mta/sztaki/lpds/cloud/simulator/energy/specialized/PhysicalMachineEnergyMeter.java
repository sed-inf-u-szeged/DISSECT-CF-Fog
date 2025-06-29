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
import hu.mta.sztaki.lpds.cloud.simulator.energy.DirectEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

import java.util.List;

/**
 * Allows to energy meter all resource spreaders (e.g. CPU, Network) associated
 * with a single PhysicalMachine.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2014"
 */
public class PhysicalMachineEnergyMeter extends AggregatedEnergyMeter
		implements VMManager.CapacityChangeEvent<ResourceConstraints> {

	/**
	 * The physical machine that is under monitoring
	 */
	private final PhysicalMachine observed;

	/**
	 * instantiates a physical machine meter based on the meter aggregator
	 * concept of DISSECT-CF
	 * 
	 * @param pm
	 *            the physical machine to be metered
	 */
	public PhysicalMachineEnergyMeter(final PhysicalMachine pm) {
		super(List.of(new DirectEnergyMeter(pm),
				new DirectEnergyMeter(pm.localDisk.diskinbws), new DirectEnergyMeter(pm.localDisk.diskoutbws),
				new DirectEnergyMeter(pm.localDisk.inbws), new DirectEnergyMeter(pm.localDisk.outbws)));
		observed = pm;
	}

	/**
	 * ensures the newly started metering will consider capacity changes in the
	 * PM (e.g. DVFS like behavior)
	 */
	@Override
	public boolean startMeter(long interval, boolean dropPriorReading) {
		boolean returner = super.startMeter(interval, dropPriorReading);
		observed.subscribeToCapacityChanges(this);
		return returner;
	}

	/**
	 * terminates the metering session for the aggregation and ensures that we
	 * no longer consider capacity changes for the PM as they don't need to be
	 * reflected in the metering results anymore
	 */
	@Override
	public void stopMeter() {
		super.stopMeter();
		observed.unsubscribeFromCapacityChanges(this);
	}

	/**
	 * readjusts the meter (and thus evaluate actual consumption values) if the
	 * capacity of the metered physical machine changes
	 */
	@Override
	public void capacityChanged(ResourceConstraints newCapacity, List<ResourceConstraints> affectedCapacity) {
		readjustMeter();
	}

	/**
	 * allows determining which PM is under observation
	 * 
	 * @return the metered pm
	 */
	public PhysicalMachine getObserved() {
		return observed;
	}
}
