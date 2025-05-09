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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel;

/**
 * This class is part of the unified resource consumption model of DISSECT-CF.
 * 
 * This class offers provider (e.g., the source of the resource extraction)
 * specific behavior for the MaxMinFairness resource spreading technique.
 * 
 * This is a generic provider. The implementation does not assume any particular
 * use (e.g. network/cpu). It can even be used to model usual resource
 * bottlenecks in computing environments (e.g. memory bandwidth, GPU).
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *
 */
public class MaxMinProvider extends MaxMinFairSpreader {
	/**
	 * Constructs a generic Max Min fairness based resource producer.
	 * 
	 * @param initialProcessing
	 *            determines the amount of resources this producer could offer
	 *            in a single tick
	 */
	public MaxMinProvider(final double initialProcessing) {
		super(initialProcessing);
	}

	/**
	 * Translates the consumption limit update request to actually changing a
	 * field in the resource consumption that is related to providers.
	 * 
	 * The limit set here is expected to reflect the processing this provider
	 * could offer in the particular time instance in regard to the
	 * particular resource consumption.
	 */

	@Override
	protected void updateConsumptionLimit(final ResourceConsumption con, final double limit) {
		con.providerLimit = limit;
	}

	/**
	 * Uses the resource consumption's provider related processing operation to
	 * actually offer the resources to those who are in need of them (e.g. in
	 * case of a network transfer this means the provider actually pushes the
	 * data to the network. In case of a virtual machine this could mean that
	 * the physical CPU of offers some computational resources for the VM to
	 * actually execute some instructions)
	 */
	@Override
	protected double processSingleConsumption(final ResourceConsumption con, final long ticksPassed) {
		return con.doProviderProcessing(ticksPassed);
	}

	/**
	 * Determines what is the consumer this provider is connected with via the
	 * resource consumption specified.
	 */
	@Override
	protected ResourceSpreader getCounterPart(final ResourceConsumption con) {
		return con.getConsumer();
	}

	/**
	 * Determines what is the provider referred by the resource consumption
	 * specified.
	 */
	@Override
	protected ResourceSpreader getSamePart(final ResourceConsumption con) {
		return con.getProvider();
	}

	/**
	 * Tells the world that this particular resource spreader is a provider.
	 */
	@Override
	protected boolean isConsumer() {
		return false;
	}

	@Override
	protected void manageRemoval(final ResourceConsumption con) {
		// Do nothing, consumers can do everything
	}
	
	/**
	 * provides some textual representation of this provider, good for debugging
	 * and tracing
	 */
	@Override
	public String toString() {
		return "MaxMinProvider(Hash-" + hashCode() + " " + super.toString() + ")";
	}

	@Override
	public FreqSyncer.DepKind spreaderType() {
		return FreqSyncer.DepKind.PROVIDER;
	}
}
