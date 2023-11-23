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
 *  (C) Copyright 2015, Gabor Kecskemeti (kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

import java.util.List;

/**
 * This iterator alters the restart operator so it no longer resets the pointer
 * of the PM iterator, but instead it marks the last PM encountered during the
 * previous iteration. This ensures that the first next operation of the new
 * iteration cycle will continue just as it would be the next operation of the
 * last iteration cycle.
 * 
 * <i>NOTE:</i> this iterator allows the index to overflow (i.e., to point over
 * the last element of the list). To handle these cases the iterator overwrites
 * the index dependent operations of PM Iterator.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2015"
 */
public class RoundRobinIterator extends PMIterator {

	/**
	 * The index at which the restart took place, and thus we use this as the
	 * marker for the end of the iteration instead of the end of the PM list.
	 */
	int stopIndex = -1;

	/**
	 * The constructor of the round-robin iterator just passes the pm list to
	 * its superclass.
	 * 
	 * @param pmList
	 */
	public RoundRobinIterator(List<PhysicalMachine> pmList) {
		super(pmList);
	}

	/**
	 * marks the index at which the iteration cycle must stop
	 */
	@Override
	public void restart(boolean fromMarked) {
		stopIndex = index + maxIndex;
	}

	/**
	 * Other than doing regular PM iterator reset, it also reinstantiates the
	 * index to keep it from overflowing.
	 */
	@Override
	public void reset() {
		index = maxIndex == 0 ? maxIndex : index % maxIndex;
		super.reset();
	}

	@Override
	public boolean hasNext() {
		return index < stopIndex;
	}

	@Override
	public PhysicalMachine next() {
		return pmList.get(index++ % maxIndex);
	}
}
