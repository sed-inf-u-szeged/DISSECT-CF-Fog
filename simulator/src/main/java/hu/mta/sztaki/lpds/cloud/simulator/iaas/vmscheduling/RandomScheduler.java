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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.PMIterator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.RandomIterator;

/**
 * Provides a scheduler that uses the random PM iterator to traverse through the
 * IaaS's running machines list. This ensures uniform use of the PMs on the long
 * run. Other than the random PM selection this class utilizes the
 * FirstFitScheduler's logic of VM placement and queue management.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2015"
 */
public class RandomScheduler extends FirstFitScheduler {

	/**
	 * Passes the IaaSService further to its super class.
	 * 
	 * @param parent
	 *            the IaaS Service which this RandomScheduler operates on
	 */
	public RandomScheduler(IaaSService parent) {
		super(parent);
	}

	/**
	 * Returns with the Random PM iterator.
	 */
	@Override
	protected PMIterator instantiateIterator() {
		return new RandomIterator(parent.runningMachines);
	}
}
