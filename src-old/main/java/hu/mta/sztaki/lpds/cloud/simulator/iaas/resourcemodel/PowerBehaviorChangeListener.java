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

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;

/**
 * This interface should be implemented in case one would like to observe the
 * power behavior changes of a resource spreader. This is expected to be used in
 * the energy related part of the simulator.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2014-5"
 * 
 */
public interface PowerBehaviorChangeListener {
	/**
	 * Until subscribed, this function is called every time when a resource
	 * spreader switches to a new power state.
	 * 
	 * @param onSpreader
	 *            the resource spreader which changed its power state
	 * @param newState
	 *            the new power state that is used by the spreader from now on.
	 */
	void behaviorChanged(final ResourceSpreader onSpreader, final PowerState newState);
}