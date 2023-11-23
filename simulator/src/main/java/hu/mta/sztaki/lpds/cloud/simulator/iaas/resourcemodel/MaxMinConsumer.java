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
 * This class provides consumer (e.g., the beneficiary of the consumption of the
 * resources of a provider) specific behavior for the MaxMinFairness resource
 * spreading technique.
 * 
 * This is a generic consumer. The implementation does not assume any particular
 * use (e.g. network/cpu). It can even be used to model usual resource
 * bottlenecks in computing environments (e.g. memory bandwidth, GPU).
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013"
 *
 */
public class MaxMinConsumer extends MaxMinFairSpreader {

	/**
	 * Constructs a generic Max Min fairness based resource consumer.
	 * 
	 * @param initialProcessing determines the amount of resources this consumer
	 *                          could utilize in a single tick
	 */
	public MaxMinConsumer(final double initialProcessing) {
		super(initialProcessing);
	}

	/**
	 * Translates the consumption limit update request to actually changing a field
	 * in the resource consumption that is related to consumers.
	 * 
	 * The limit set here is expected to reflect the processing this consumer could
	 * utilize in the particular time instance in regard to the particular
	 * resource consumption.
	 */
	@Override
	protected void updateConsumptionLimit(final ResourceConsumption con, final double limit) {
		con.consumerLimit = limit;
	}

	/**
	 * Determines what is the provider this consumer is connected with via the
	 * resource consumption specified.
	 */
	@Override
	protected ResourceSpreader getCounterPart(final ResourceConsumption con) {
		return con.getProvider();
	}

	/**
	 * Determines what is the consumer referred by the resource consumption
	 * specified.
	 */
	@Override
	protected ResourceSpreader getSamePart(final ResourceConsumption con) {
		return con.getConsumer();
	}

	/**
	 * Uses the resource consumption's consumer related processing operation to
	 * actually use the results of a resource consumption (e.g. in case of a network
	 * transfer this means the consumer actually receives the data that so far just
	 * traveled through the network. In case of a virtual machine this could mean
	 * that the virtual CPU of the VM actually executes some instructions)
	 */
	@Override
	protected double processSingleConsumption(final ResourceConsumption con, final long ticksPassed) {
		return con.doConsumerProcessing(ticksPassed);
	}

	/**
	 * Tells the world that this particular resource spreader is a consumer.
	 */
	@Override
	protected boolean isConsumer() {
		return true;
	}

	@Override
	protected void manageRemoval(final ResourceConsumption con) {
		if (con.getUnProcessed() == 0) {
			con.fireCompleteEvent();
		} else if (!con.isResumable()) {
			con.fireCancelEvent();
		}
	}

	/**
	 * provides some textual representation of this consumer, good for debugging and
	 * tracing
	 */
	@Override
	public String toString() {
		return "MaxMinConsumer(Hash-" + hashCode() + " " + super.toString() + ")";
	}

	@Override
	public FreqSyncer.DepKind spreaderType() {
		return FreqSyncer.DepKind.CONSUMER;
	}
}
