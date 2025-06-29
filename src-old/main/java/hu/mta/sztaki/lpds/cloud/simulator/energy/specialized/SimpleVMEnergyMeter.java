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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

/**
 * Derives VM consumption from its hosting PM.
 * 
 * Does not support migrations, and provides only a rough estimate on the energy
 * cost of actions taken by the particular VM.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2014"
 * 
 */
public class SimpleVMEnergyMeter extends PhysicalMachineEnergyMeter {
	/**
	 * Cannot be created for unallocated VMs!
	 * 
	 * @param vm
	 */
	public SimpleVMEnergyMeter(final VirtualMachine vm) {
		super(vm.getResourceAllocation().getHost());
	}

	/**
	 * cons(PM)/NumVMs(PM)
	 */
	@Override
	public double getTotalConsumption() {
		return super.getTotalConsumption() / getObserved().numofCurrentVMs();
	}
}
