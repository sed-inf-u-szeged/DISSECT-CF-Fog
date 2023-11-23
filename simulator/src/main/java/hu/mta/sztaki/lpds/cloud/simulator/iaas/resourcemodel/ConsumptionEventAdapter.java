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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;

/**
 * This class simplifies the implementation of consumption events and provides
 * basic functions to determine if a resource consumption has already been
 * completed (either with a failure or success).
 * 
 * The simplification allows the following: Instead of implementing the complete
 * consumption event interface, one can only concentrate on actions to do on
 * success/failure only.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 *
 */
public class ConsumptionEventAdapter implements ConsumptionEvent {

	/**
	 * shows if the resource consumption has failed to complete for some reason
	 */
	private boolean cancelled = false;
	/**
	 * shows if the resource consumption has successfully completed
	 */
	private boolean completed = false;

	/**
	 * This function simply marks the success of the consumption in the
	 * completed field of the class. If this function is overridden please make
	 * sure super.conComplete() is called (this allows the other functions of
	 * this class to still operate correctly).
	 */
	@Override
	public void conComplete() {
		completed = true;
	}

	/**
	 * This function simply marks the failure of the consumption in the
	 * cancelled field of the class. If this function is overridden please make
	 * sure super.conCancelled() is called (this allows the other functions of
	 * this class to still operate correctly).
	 */
	@Override
	public void conCancelled(ResourceConsumption problematic) {
		cancelled = true;
	}

	/**
	 * Determines whether there was a failure in the resource consumption this
	 * event adapter is/was observing
	 * 
	 * @return <i>true</i> if the consumption has failed
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * Determines if successful completion was marked for the resource
	 * consumption this event adapter is/was observing
	 * 
	 * @return <i>true</i> if the consumption has successfully terminated
	 */
	public boolean isCompleted() {
		return completed;
	}
}
