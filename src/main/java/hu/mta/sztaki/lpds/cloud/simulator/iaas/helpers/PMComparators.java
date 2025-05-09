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
 *  (C) Copyright 2012, Gabor Kecskemeti (kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.helpers;

import java.util.Comparator;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

/**
 * A collection of comparators useful for dealing with PM lists.
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012"
 *
 */
public class PMComparators {
	/**
	 * A PM comparator that offers inverse ordering of PMs in terms of free capacity
	 * (this is not the actual utilisation of the PM, but unallocated capacities
	 * left on the PM!)
	 */
	public static final Comparator<PhysicalMachine> highestToLowestFreeCapacity = (PhysicalMachine o1,
			PhysicalMachine o2) -> -o1.freeCapacities.compareTo(o2.freeCapacities);

	/**
	 * A PM comparator that offers inverse ordering of PMs in terms of total
	 * capacity. Ensures inverse order based on capacities.	
	 */
	public final static Comparator<PhysicalMachine> highestToLowestTotalCapacity = (final PhysicalMachine o1,
			final PhysicalMachine o2) -> -o1.getCapacities().compareTo(o2.getCapacities());

}
