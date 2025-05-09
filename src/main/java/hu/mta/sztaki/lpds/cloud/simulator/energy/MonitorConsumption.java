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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceSpreader;

import java.util.PriorityQueue;

/**
 * This class is an initial framework to collect periodical reports on
 * consumptions. In its current state it is too rigid and needs more
 * customization.
 * 
 * However, if extended further it would allow a single consumption monitor to
 * be added to a particular resourcespreader, eliminating the need for multiple
 * queries on the getTotalProcessed function.
 * 
 * <i>NOTE:</i> this class is outdated and might perform badly.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 * 
 */
public class MonitorConsumption extends Timed {
	/**
	 * the collected totalprocessed data with timestamps to allow seeing the
	 * temporal behavior of totalprocessed
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 *
	 */
	private static class SpreadingRecord implements Comparable<SpreadingRecord> {
		/**
		 * the time in ticks when this record was acquired
		 */
		public long timestamp;
		/**
		 * the totalprocessed value at the particular time instance
		 */
		public double totalProcessed;

		/**
		 * a comparator to allow easy ordering of the records based on
		 * timestamps.
		 */
		@Override
		public int compareTo(SpreadingRecord o) {
			return Long.signum(timestamp - o.timestamp);
		}
	}

	/**
	 * what resource spreader to collect the totalprocessed values from
	 */
	final ResourceSpreader toMonitor;
	/**
	 * all spreading records that were collected during the past hour
	 */
	private final PriorityQueue<SpreadingRecord> subHourRecords = new PriorityQueue<>();
	/**
	 * all spreading records collected during the past day
	 */
	private final PriorityQueue<SpreadingRecord> subDayRecords = new PriorityQueue<>();
	/**
	 * the total processing accomplished in the last hour
	 */
	private double subHourProcessing = 0;
	/**
	 * the total processing accomplished in the last day
	 */
	private double subDayProcessing = 0;
	/**
	 * the totalprocessed value of the spreader during the last collection
	 */
	private double totalProcessed;
	/**
	 * the amount of processing done in the last second
	 */
	private double subSecondProcessing = 0;

	/**
	 * the amount of ticks it takes for a single second to pass in the simulated
	 * time
	 */
	private final long aSecond;

	/**
	 * Initiates a monitoring session for the resource consumptions of a
	 * particular resource spreader
	 * 
	 * @param toMonitor
	 *            the resource spreader to be monitored
	 * @param aSecond
	 *            the amount of ticks it takes to get to one second in the
	 *            current configuration of the simulation, if this should be
	 *            below 1 then the monitor consumption class should not be used
	 */
	public MonitorConsumption(final ResourceSpreader toMonitor, final long aSecond) {
		this.toMonitor = toMonitor;
		totalProcessed = toMonitor.getTotalProcessed();
		this.aSecond = aSecond;
		subscribe(aSecond);
	}

	/**
	 * the amount of processing done in the past day (this is a rolling day
	 * always assumed to start a day before this function was called)
	 * 
	 * @return the processing done in the past day
	 */
	public double getSubDayProcessing() {
		return subDayProcessing;
	}

	/**
	 * the amount of processing done in the past hour (this is a rolling hour
	 * always assumed to start a hour before this function was called)
	 * 
	 * @return the processing done in the past hour
	 */
	public double getSubHourProcessing() {
		return subHourProcessing;
	}

	/**
	 * the amount of processing done in the past second (this is a rolling
	 * second always assumed to start a second before this function was called)
	 * 
	 * @return the processing done in the past second
	 */
	public double getSubSecondProcessing() {
		return subSecondProcessing;
	}

	/**
	 * Data collector function that updates all processing data records and
	 * throws out the too old records as well.
	 */
	@Override
	public void tick(final long fires) {
		final SpreadingRecord current = new SpreadingRecord();
		current.timestamp = fires;
		current.totalProcessed = toMonitor.getTotalProcessed();
		subSecondProcessing = current.totalProcessed - totalProcessed;
		totalProcessed = current.totalProcessed;
		subHourRecords.add(current);
		while (subHourRecords.peek().timestamp + 3600 * aSecond < fires) {
			// 60*60*1000
			// Over the past hour
			subDayRecords.add(subHourRecords.poll());
			while (subDayRecords.peek().timestamp + 86400 * aSecond < fires) {
				// Over the past day
				subDayRecords.poll();
			}
		}
		subHourProcessing = totalProcessed - subHourRecords.peek().totalProcessed;
		try {
			subDayProcessing = totalProcessed - subDayRecords.peek().totalProcessed;
		} catch (NullPointerException ne) {
			// No records over an hour
			subDayProcessing = subHourProcessing;
		}
	}

	/**
	 * allows the monitoring to be terminated at any arbitrary point of time.
	 * The processing data reports are not going to be updated anymore, and they
	 * are going to be always valid for the time instance where the cancel
	 * operation was called.
	 */
	public void cancelMonitoring() {
		unsubscribe();
	}
}
