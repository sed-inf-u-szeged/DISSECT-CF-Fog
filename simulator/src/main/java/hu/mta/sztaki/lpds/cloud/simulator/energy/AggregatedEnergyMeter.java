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
package hu.mta.sztaki.lpds.cloud.simulator.energy;

import java.util.List;

/**
 * Allows a group of energy meters to be operated simultaneously.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
 */
public class AggregatedEnergyMeter extends EnergyMeter {

	/**
	 * The list of meters that supposed to be used together.
	 * 
	 * It is defined as public so aggregator users can freely update the
	 * supervised list any time they like
	 */
	public final List<EnergyMeter> supervised;

	/**
	 * Constructs an aggregated meter with a list of energy meters to operate on
	 * top.
	 * 
	 * @param toAggregate
	 *            the list of energy meters to supervise
	 */
	public AggregatedEnergyMeter(List<EnergyMeter> toAggregate) {
		supervised = toAggregate;
	}

	/**
	 * Dispatches start meter calls to all supervised meters. If even one of
	 * them fails, it immediately terminates those meters that were started
	 * before the failure.
	 */
	@Override
	public boolean startMeter(long interval, boolean dropPriorReading) {
		// Make sure the meteringstarted field is updated correctly.
		super.startMeter(interval, dropPriorReading);
		var ret=true;
		if(supervised.stream().anyMatch(s -> !s.startMeter(interval,dropPriorReading))) {
			// Some meters did not start because they already did some
			// metering prior to this startmeter request
			stopMeter();
			ret=false;
		}
		return ret;
	}

	/**
	 * Dispatches stop meter calls to all supervised meters
	 */
	@Override
	public void stopMeter() {
		// Make sure the meteringstopped field is updated correctly.
		super.stopMeter();
		supervised.forEach(EnergyMeter::stopMeter);
	}

	/**
	 * Calculates the sum of total consumptions returned by each and every
	 * supervised meter
	 */
	@Override
	public double getTotalConsumption() {
		return supervised.stream().mapToDouble(EnergyMeter::getTotalConsumption).sum();
	}

	/**
	 * This operation is ignored as the actual metering is done in the
	 * supervised meters, and the totalconsumption values are always calculated
	 * on demand.
	 */
	@Override
	public void tick(long fires) {
		// Do nothing
	}
}
