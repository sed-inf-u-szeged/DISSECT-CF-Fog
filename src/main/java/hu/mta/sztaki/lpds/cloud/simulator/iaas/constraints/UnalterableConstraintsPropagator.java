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
 * Allows an alterable resource constraints object to be propagated as
 * unalterable. It forwards all data read requests to the encapsulated object.
 * 
 * WARNING: this class does not make a copy of the original state of the
 * alterable resource constraints. Thus allows the reduction of new object
 * creations. Users of this class are expected to know that queries of on its
 * interface are not guaranteed to return the same value between two calls
 * (unlike constant constraints).
 * 
 * 
 * This class is intentionally non-mutable, allowing those users (e.g. the class
 * of IaaSService or Scheduler) who would want to share their current capacities
 * not to create a new instance for every new query.
 * 
 * @author 
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
 * 
 */
public class UnalterableConstraintsPropagator extends ResourceConstraints {
	/**
	 * The other resource constraints object to encapsulate. Typically, this will
	 * be an alterableresourceconstraints object.
	 */
	private final ResourceConstraints whatToPropagate;

	/**
	 * Allows the construction of the propagator. Ensures that calls to the
	 * default RC functions will be propagated to the encapsulated object.
	 * 
	 * @param toPropagate
	 *            the object to encapsulate (where the RC calls are going to be
	 *            propagated to)
	 */
	public UnalterableConstraintsPropagator(
			final ResourceConstraints toPropagate) {
		whatToPropagate = toPropagate;
	}

	// Standard set of RC calls

	@Override
	public double getRequiredCPUs() {
		return whatToPropagate.getRequiredCPUs();
	}

	@Override
	public long getRequiredMemory() {
		return whatToPropagate.getRequiredMemory();
	}

	@Override
	public double getRequiredProcessingPower() {
		return whatToPropagate.getRequiredProcessingPower();
	}

	@Override
	public double getTotalProcessingPower() {
		return whatToPropagate.getTotalProcessingPower();
	}

	@Override
	public boolean isRequiredProcessingIsMinimum() {
		return whatToPropagate.isRequiredProcessingIsMinimum();
	}
}
