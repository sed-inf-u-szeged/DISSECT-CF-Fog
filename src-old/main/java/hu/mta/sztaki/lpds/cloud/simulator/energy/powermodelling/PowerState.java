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
package hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Represents a particular power state of a resource spreader
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2014"
 *
 */
public class PowerState {
	/**
	 * By sub-classing this class one can define arbitrary consumption models.
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2014"
	 *
	 */
	public static abstract class ConsumptionModel {
		/**
		 * backlink to the power state with useful data to determine the consumption
		 * model
		 */
		private PowerState myPowerState;

		/**
		 * This allows power state to inject itself to consumption models without
		 * @param ps
		 */
		private void setMyPowerState(PowerState ps) {
			myPowerState=ps;
		}

		protected PowerState getMyPowerState() {
			return myPowerState;
		}

		/**
		 * calculates the instantaneous power draw of a resource spreader under a
		 * particular load.
		 * 
		 * @param load the load in the range of [0..1] of the resource spreader
		 * @return the estimated instantaneous power draw of the spreader in W.
		 */
		protected abstract double evaluateConsumption(final double load);
	}

	/**
	 * allows notifications before the consumption characteristics would change.
	 * Useful to update instantaneous power draw values before a power
	 * characteristic change (like going from turboboost-&gt;using all cpu cores in
	 * modern processors)
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2014"
	 *
	 */
	public interface PowerCharacteristicsChange {
		/**
		 * this function is called when there is a powerstate characteristic change on
		 * the powerstate onMe.
		 * 
		 * @param onMe with this parameter the change event propagates the power state
		 *             for which the change is happening
		 */
		void prePowerChangeEvent(final PowerState onMe);
	}

	/**
	 * The minimum instantaneous power draw in W
	 */
	private double minConsumption;
	/**
	 * The power draw range in Watts (the maximum possible offset from minimum power
	 * draw)
	 */
	private double consumptionRange;
	/**
	 * Records the last time in ticks when the power characteristics change was
	 * propagated to listeners
	 */
	private long pastNotification = -1;
	/**
	 * The current consumption model (which transforms the load to wattage based on
	 * the power state's characteristics (e.g., mincons/range)
	 */
	private final ConsumptionModel model;
	/**
	 * the list of those objects who are prepared to handle power state changes
	 */
	private final ArrayList<PowerCharacteristicsChange> listeners = new ArrayList<>();

	/**
	 * Allow the creation of a new power state object with initial power state
	 * characteristics.
	 * 
	 * @param minConsumption   The minimum consumption that could be reported by
	 *                         this powerstate (e.g. idle power if we define a
	 *                         Physical Machine's powerstate) - in watts
	 * @param consumptionRange The maximum offset of the consumption that could be
	 *                         still reported by this power state object. (e.g., the
	 *                         max-idle power if we define a Physical Machine) - in
	 *                         watts
	 * @param modelSupplier    Defines the consumption model which transforms a load
	 *                         value and is capable of outputting arbitrary
	 *                         instantaneous power draw estimates between
	 *                         minConsumption and minConsumption+consumptionRange
	 */
	public PowerState(final double minConsumption, final double consumptionRange,
			Supplier<? extends ConsumptionModel> modelSupplier) {
		this.minConsumption = minConsumption;
		this.consumptionRange = consumptionRange;
		model = modelSupplier.get();
		model.setMyPowerState(this);
	}

	/**
	 * determines the current power draw of the system given that it has the load
	 * specified in the parameter
	 * 
	 * @param load for which load value should we use the consumption model to
	 *             determine the instantaneous power draw of the system. the load
	 *             value is expected to be within the range of [0..1]
	 * @return the estimated instantaneous power draw value in watts
	 */
	public double getCurrentPower(final double load) {
		if (load > 1.01 || load < -0.01) {
			throw new IllegalStateException("Received an out of range load evaluation request:" + load);
		}
		return model.evaluateConsumption(load);
	}

	/**
	 * allows read access to the minconsumption field
	 * 
	 * @return the minconsumption of this power state
	 */
	public double getMinConsumption() {
		return minConsumption;
	}

	/**
	 * Allows read access to the consumption range field
	 * 
	 * @return the consumption range of this power state
	 */
	public double getConsumptionRange() {
		return consumptionRange;
	}

	/**
	 * used to send out notifications before any of the power state characteristics
	 * change
	 */
	private void notifyCharacteristicsListeners() {
		final long currentTime = Timed.getFireCount();
		if (currentTime != pastNotification) {
			for (PowerCharacteristicsChange l : listeners) {
				l.prePowerChangeEvent(this);
			}
			pastNotification = currentTime;
		}
	}

	/**
	 * Allows to set the minimum consumption for this power state (good for DVFS and
	 * similar behavior). Before the change the power state change listeners are
	 * notified.
	 * 
	 * @param minConsumption the new consumption value to be set in W
	 */
	public void setMinConsumption(final double minConsumption) {
		notifyCharacteristicsListeners();
		this.minConsumption = minConsumption;
	}

	/**
	 * Allows to set the consumption range for this power state (good for DVFS and
	 * similar behavior). Before the change the power state change listeners are
	 * notified.
	 * 
	 * @param cr the new consumption range value to be set in W
	 */
	public void setConsumptionRange(final double cr) {
		notifyCharacteristicsListeners();
		this.consumptionRange = cr;
	}

	/**
	 * allows users of this powerstate object to get notifications about power state
	 * characteristics changes
	 * 
	 * @param listener the object where the events should be sent
	 */
	public void subscribePowerCharacteristicsChanges(PowerCharacteristicsChange listener) {
		listeners.add(listener);
	}

	/**
	 * if some party gets uninterested in power state changes this is the way to
	 * cancel event notifications about them
	 * 
	 * @param listener the object which no longer needs notification event
	 */
	public void unsubscribePowerCharacteristicsChanges(PowerCharacteristicsChange listener) {
		listeners.remove(listener);
	}

	/**
	 * A convenient and compact way to represent the main characteristics of this
	 * power state. Good for debugging and tracing
	 */
	@Override
	public String toString() {
		return "PowSt(I: " + minConsumption + " C: " + consumptionRange + " " + model.getClass().getName() + ")";
	}
}
