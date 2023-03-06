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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints;

/**
 * This class defines the basic properties (cpu core count, per core processing
 * power, and memory size) and operations on resource constraints. These
 * constraints are expected to be used to express resource capacities of
 * physical/virtual machines or complete infrastructures, as well as requests
 * for resource allocations/virtual machines.
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University (c) 2017"
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012"
 */
public abstract class ResourceConstraints implements Comparable<ResourceConstraints> {

	/**
	 * provides a simple one line representation of resource constraints listing
	 * all its inherent properties. good for debugging and tracing.
	 */
	@Override
	public String toString() {
		return "ResourceConstraints(C:" + getRequiredCPUs() + " P:" + getRequiredProcessingPower() + " M:"
				+ getRequiredMemory() + ")";
	}

	/**
	 * offers a comparator between two constraints objects.
	 */
	@Override
	public int compareTo(ResourceConstraints o) {
		return Double.compare((this.getTotalProcessingPower() * this.getRequiredMemory()), o.getTotalProcessingPower() * o.getRequiredMemory());
	}

	/**
	 * Allows querying how many CPUs this constraints object represent
	 * 
	 * @return the amount of CPU cores represented by the object
	 */
	public abstract double getRequiredCPUs();

	/**
	 * Allows to query the performance of a single CPU core represented by this
	 * constraints object represent
	 * 
	 * @return the performance of a CPU core in instructions/tick
	 */
	public abstract double getRequiredProcessingPower();

	/**
	 * Determines if the specified amounts of resources are minimally or exactly
	 * required.
	 * 
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the specified amount of resources could be
	 *         over-fulfilled
	 *         <li><i>false</i> if the object represents an exact amount of
	 *         resources
	 *         </ul>
	 */
	public abstract boolean isRequiredProcessingIsMinimum();

	/**
	 * Allows querying how much memory this constraints object represent
	 * 
	 * @return the amount in bytes
	 */
	public abstract long getRequiredMemory();

	/**
	 * The total processing power of all cores represented by this constraints
	 * object:
	 * 
	 * total=cpus*processingpower
	 * 
	 * @return the total processing power in instructions/tick
	 */
	public abstract double getTotalProcessingPower();

}
