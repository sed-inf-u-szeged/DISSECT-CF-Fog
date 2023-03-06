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
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides an implementation of a resource constraints class that allows in
 * place alterations on its instances
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2015"
 */
public class AlterableResourceConstraints extends ResourceConstraints {
	// data members to represent the state required for the standard RC calls
	private double requiredCPUs;
	private double requiredProcessingPower;
	private final boolean requiredProcessingIsMinimum;
	private long requiredMemory;
	private double totalProcessingPower;

	/**
	 * A constructor to define resource constraints with exact amount of
	 * resources to start with
	 * 
	 * @param cpu
	 *            number of cores
	 * @param processing
	 *            per core processing power in instructions/tick
	 * @param memory
	 *            number of bytes
	 */
	public AlterableResourceConstraints(final double cpu, final double processing, final long memory) {
		this(cpu, processing, false, memory);
	}

	/**
	 * The main constructor to define alterable resource constraints
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
	public AlterableResourceConstraints(final double cpu, final double processing, boolean isMinimum,
			final long memory) {
		requiredCPUs = cpu;
		requiredMemory = memory;
		requiredProcessingPower = processing;
		updateTotal();
		requiredProcessingIsMinimum = isMinimum;
	}

	/**
	 * Allows to make an arbitrary resourceconstraints object into an alterable
	 * one
	 * 
	 * @param toCopy
	 *            the other resource constraints to copy
	 */
	public AlterableResourceConstraints(final ResourceConstraints toCopy) {
		this(toCopy.getRequiredCPUs(), toCopy.getRequiredProcessingPower(), toCopy.isRequiredProcessingIsMinimum(),
				toCopy.getRequiredMemory());
	}

	/**
	 * An easy way to get alterable constraints with zero resources - as a basis
	 * for calculations with RCs
	 * 
	 * @return an alterable version of ConstantConstraints.noResources
	 */
	public static AlterableResourceConstraints getNoResources() {
		return new AlterableResourceConstraints(ConstantConstraints.noResources);
	}

	/**
	 * Allows increasing/decreasing the amount of cpu cores and required memory
	 * by this constraints object linearly
	 * 
	 * @param times
	 *            the number of times the cpu/memory count should be multiplied
	 */
	public void multiply(final double times) {
		if (times != 1) {
			requiredCPUs *= times;
			requiredMemory *= times;
			updateTotal();
		}
	}

	/**
	 * Allows a single resource constraints object to be added to this one
	 * 
	 * WARNING: this is for internal purposes only. It does not update the total
	 * values for performance reasons!
	 * 
	 * @param singleAdd
	 *            the other resource constraints object to be added to this one
	 */
	private void simpleAddition(final ResourceConstraints singleAdd) {
		changeProcessingByScaling(singleAdd.getRequiredCPUs(), singleAdd.getRequiredProcessingPower());
		requiredMemory += singleAdd.getRequiredMemory();
	}

	/**
	 * Adds the specified processing power to the total processing of this
	 * resource constraints object but keeps the per core processing power fixed
	 * (essentially transforms all operations to changes in the required cpu
	 * cores)
	 * 
	 * @param withCores
	 *            the number of cpu cores to be added
	 * @param withProc
	 *            with the processing capacity per each core
	 */
	private void changeProcessingByScaling(final double withCores, final double withProc) {
		if (requiredProcessingPower == 0) {
			requiredCPUs = withCores;
			requiredProcessingPower = withProc;
		} else {
			requiredCPUs = requiredCPUs + withProc * withCores / requiredProcessingPower;
		}
	}

	/**
	 * Allows a single resource constraints object to be added to this one
	 * 
	 * @param toAdd
	 *            the other resource constraints object to be added
	 */
	public void singleAdd(final ResourceConstraints toAdd) {
		simpleAddition(toAdd);
		updateTotal();
	}

	/**
	 * Allows chaining single add ops
	 * @param toAdd
	 * @return self
	 */
	public AlterableResourceConstraints singleAddCont(final ResourceConstraints toAdd) {
		singleAdd(toAdd);
		return this;
	}

	/**
	 * Allows multiple RC objects to be added to this one with variable
	 * parameter length
	 * 
	 * This operation is good when the RC objects are held in an array. This way
	 * they don't need a conversion
	 * 
	 * @param toAdd
	 *            several resource constraints objects to be added
	 */
	public void add(final ResourceConstraints... toAdd) {
		addStream(Arrays.stream(toAdd));
	}

	/**
	 * Allows multiple RC objects to be added to this one with variable
	 * parameter length
	 * 
	 * This operation expects a list of RC objects thus it is optimally used
	 * when the RC objects are stored in a List anyways.
	 * 
	 * @param toAdd
	 *            A list of several resource constraints objects to be added
	 */
	public void add(final List<ResourceConstraints> toAdd) {
		addStream(toAdd.stream());
	}

	private void addStream(final Stream<ResourceConstraints> toAdd) {
		toAdd.forEach(this::simpleAddition);
		updateTotal();
	}

	/**
	 * Subtracts another RC object from this one
	 * 
	 * @param what
	 *            the other party to subtract
	 */
	public void subtract(final ResourceConstraints what) {
		changeProcessingByScaling(-what.getRequiredCPUs(), what.getRequiredProcessingPower());
		requiredMemory -= what.getRequiredMemory();
		updateTotal();
	}

	/**
	 * the total processing field is updated with this function. This is to be
	 * used for the internal operations of the class.
	 */
	private void updateTotal() {
		requiredProcessingPower = requiredCPUs == 0 ? 0
				: requiredProcessingPower;
		totalProcessingPower = requiredCPUs * requiredProcessingPower;
	}

	/**
	 * Allows the conversion of this resource constraints object to an
	 * alternative processing power unit
	 * 
	 * @param multiplier
	 *            to be applied to change the processing power
	 */
	public void scaleProcessingPower(final double multiplier) {
		if (multiplier != 1) {
			requiredCPUs /= multiplier;
			requiredProcessingPower *= multiplier;
			updateTotal();
		}
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
