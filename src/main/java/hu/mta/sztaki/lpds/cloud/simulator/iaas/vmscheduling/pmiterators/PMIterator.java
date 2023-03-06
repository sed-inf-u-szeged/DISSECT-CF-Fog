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

import java.util.Iterator;
import java.util.List;

/**
 * Provides the archetype of PM iterators.
 * 
 * This iterator allows the user to get from the very first PM to the last one
 * in the PM list.
 * 
 * The iterator also allows to repeatedly use the same list for multiple times.
 * This is accomplished by the reset function which sets the iterator to its
 * starting position. The implementation of all iterators assume that after the
 * reset function is called there will be no change in the overseen PM list -
 * e.g., while calling the next function there are no cases when the PM list
 * gets new PMs.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2015"
 */
public class PMIterator implements Iterator<PhysicalMachine> {

	/**
	 * Current index to be used when the next PM is asked for
	 */
	protected int index = 0;
	/**
	 * The total amount of PMs the iterator should go thorough - this is assumed
	 * not to change until a new reset function call.
	 */
	protected int maxIndex = 0;
	/**
	 * The index of the PM that was marked - this allows the users of the
	 * iterator to remember particular positions in the PM list
	 */
	protected int marked = 0;
	/**
	 * The PM list to be operated on. This list is not altered by the iterator.
	 * It is assumed to not change while a single iteration is in progress.
	 */
	protected final List<PhysicalMachine> pmList;

	/**
	 * Constructs the PM list and stores the received list internally. Only the
	 * reference is stored to the list, and it is not copied allowing external
	 * parties to alter the list if necessary without creating a new iterator
	 * all the time. To ensure the PM list is properly iterated every time a new
	 * iteration needs to start the reset function must be called.
	 * 
	 * @param pmList
	 *            the pm list on which this iterator will operate
	 */
	public PMIterator(List<PhysicalMachine> pmList) {
		this.pmList = pmList;
	}

	/**
	 * Prepares the iterator so its other functions will now operate properly.
	 * The reset function needs to be called every time a new iteration needs to
	 * start with the iterator or when the iterated PM list changes in size.
	 */
	public void reset() {
		maxIndex = pmList.size();
		restart(false);
	}

	/**
	 * Allows the iterator to restart the iteration from the beginning or from a
	 * previously remembered position.
	 * 
	 * @param fromMarked
	 *            <ul>
	 *            <li><i>true</i> if the iterator should start from the PM with
	 *            the index 'marked'.
	 *            <li><i>false</i> if the iterator should start from the very
	 *            first PM in the PM list
	 *            </ul>
	 */
	public void restart(boolean fromMarked) {
		index = fromMarked ? marked : 0;
	}

	/**
	 * This call can mark the last PM that was offered by the iterator with its
	 * previous next() call. The marked item then can be used with the restart
	 * operation.
	 */
	public void markLastCollected() {
		marked = index - 1;
	}

	@Override
	public boolean hasNext() {
		return index < maxIndex;
	}

	@Override
	public PhysicalMachine next() {
		return pmList.get(index++);
	}
}
