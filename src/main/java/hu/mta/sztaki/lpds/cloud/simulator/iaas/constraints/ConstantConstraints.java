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
 * Defines the non-mutable main resource constraints representation
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 * 
 */
public class ConstantConstraints extends ResourceConstraints {
	/**
	 * Provides easy access to the one and original zero constraints
	 */
	public static final ConstantConstraints noResources = new ConstantConstraints(
			0, 0, 0);

	// data members to represent the state required for the standard RC calls
	final private double requiredCPUs;
	final private double requiredProcessingPower;
	final private boolean requiredProcessingIsMinimum;
	final private long requiredMemory;
	final private double totalProcessingPower;

	/**
	 * A constructor to define resource constraints with exact amount of
	 * resources
	 * 
	 * @param cpu
	 *            number of cores
	 * @param processing
	 *            per core processing power in instructions/tick
	 * @param memory
	 *            number of bytes
	 */
	public ConstantConstraints(final double cpu, final double processing,
			final long memory) {
		this(cpu, processing, false, memory);
	}

	/**
	 * The main constructor to define resource constraints
	 * 
	 * @param cpu
	 *            number of cores
	 * @param processing
	 *            per core processing power in instructions/tick
	 * @param isMinimum
	 *            <ul>
	 *            <li><i>true</i> if the constraints define an absolute minimum
	 *            (i.e., the constraints object could represent any exact
	 *            constraints object with at least as much memory/processors)
	 *            <li><i>false</i> if the constraints defined here are exactly
	 *            defined no difference from them are accepted.
	 *            </ul>
	 * @param memory
	 *            number of bytes
	 */
	public ConstantConstraints(final double cpu, final double processing,
			boolean isMinimum, final long memory) {
		requiredCPUs = cpu;
		requiredMemory = memory;
		requiredProcessingPower = processing;
		totalProcessingPower = cpu * processing;
		requiredProcessingIsMinimum = isMinimum;
	}

	/**
	 * Allows to make an arbitrary resourceconstraints object into a constant
	 * one
	 * 
	 * @param toCopy
	 *            the other resource constraints to copy
	 */
	public ConstantConstraints(final ResourceConstraints toCopy) {
		this(toCopy.getRequiredCPUs(), toCopy.getRequiredProcessingPower(),
				toCopy.isRequiredProcessingIsMinimum(), toCopy
						.getRequiredMemory());
	}

	// Standard set of RC calls

	@Override
	public double getRequiredCPUs() {
		return requiredCPUs;
	}

	@Override
	public double getRequiredProcessingPower() {
		return requiredProcessingPower;
	}

	@Override
	public boolean isRequiredProcessingIsMinimum() {
		return requiredProcessingIsMinimum;
	}

	@Override
	public long getRequiredMemory() {
		return requiredMemory;
	}

	@Override
	public double getTotalProcessingPower() {
		return totalProcessingPower;
	}

}
