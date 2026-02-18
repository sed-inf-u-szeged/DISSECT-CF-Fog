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

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

/**
 * Base class for all energy meters. Defines the basic operations but allows
 * high level of customization
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2014-5"
 */
public abstract class EnergyMeter extends Timed {

	/**
	 * the currently collected totalConsumption in all metering sessions so far.
	 */
	private double totalConsumption = 0;
	/**
	 * determines the time instance when the current metering session has
	 * started. This does not actually specify the time instance when the first
	 * metering session starts if there are gaps between metering sessions.
	 * instead, it gives the time instance when a continuous metering session
	 * would start.
	 */
	private long meteringStarted;
	/**
	 * when was the last metering result collected.
	 */
	protected long lastMetered;
	/**
	 * when did the metering stopped reported in absolute time.
	 */
	private long meteringStopped = 0;

	/**
	 * Initiates an energy metering session.
	 * 
	 * @param interval
	 *            The meter refresh frequency.
	 * @param dropPriorReading
	 *            <ul>
	 *            <it>False: the totalConsumption values will accumulate from a
	 *            previous metering session <it>True: the totalconsumption
	 *            values will start from 0 after the completion of this
	 *            function.
	 *            </ul>
	 * @return <ul>
	 *         <li>False: if a metering session is already underway
	 *         <li>True: if the metering session was successfully initiated
	 *         </ul>
	 * 
	 */
	public boolean startMeter(final long interval, boolean dropPriorReading) {
		if (meteringStopped == -1) {
			return false;
		}
		subscribe(interval);
		lastMetered = Timed.getFireCount();
		if (dropPriorReading) {
			totalConsumption = 0;
		}
		if (totalConsumption == 0) {
			meteringStarted = lastMetered;
		} else {
			meteringStarted += lastMetered - meteringStopped;
		}
		meteringStopped = -1;
		return true;
	}

	/**
	 * Terminates the metering session, the totalconsumption values will no
	 * longer be updated!
	 */
	public void stopMeter() {
		if (unsubscribe()) {
			final long now = getFireCount();
			if (now != lastMetered) {
				tick(now);
			}
			meteringStopped = lastMetered;
		}
	}

	/**
	 * Allows the reading of the meter's current consumption report.
	 * 
	 * @return the total consumption collected during the metering sessions
	 */
	public double getTotalConsumption() {
		return totalConsumption;
	}

	/**
	 * Allows the reading of the beginning time instance of the metering session
	 * 
	 * If there were several metering sessions done by this meter, then this
	 * function reports the time instance when a hypothetical single continuous
	 * metering session would have started
	 * 
	 * @return The time instance when the metering session started
	 */
	public long getMeteringStarted() {
		return meteringStarted;
	}

	/**
	 * Enables access to the time when the last reading was stored for the last
	 * stopped metering session.
	 * 
	 * @return Three values are possible:
	 *         <ul>
	 *         <li>-1: The metering session is ongoing it is not yet terminated
	 *         <li>0: There was no metering session so far with this meter
	 *         <li>arbitrary positive number: The last reading time of the last
	 *         stopped session.
	 *         </ul>
	 */
	public long getMeteringStopped() {
		return meteringStopped;
	}

	/**
	 * increases the total consumption value with the specified amount. this is
	 * one of the main functions to be used by energy meters. Ensures that
	 * metering values are continuously increasing from a particular meter.
	 * 
	 * @param amount
	 *            the amount of additional energy consumed to be reported in
	 *            this meter
	 */
	protected void increaseTotalConsumption(final double amount) {
		totalConsumption += amount;
	}

	/**
	 * Allows internal realignment to new metering situations that need an
	 * immediate utilization reading
	 */
	protected void readjustMeter() {
		stopMeter();
		startMeter(getFrequency(), false);
	}
}
