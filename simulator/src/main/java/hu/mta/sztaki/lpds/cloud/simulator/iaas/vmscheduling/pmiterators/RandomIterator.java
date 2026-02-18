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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The user of the iterator will not know the order in which the PMs are
 * iterated through. This iterator still guarantees that each PM is only visited
 * once in an iteration cycle.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2015"
 */
public class RandomIterator extends PMIterator {

	/**
	 * A counter to show how many times the reset was called. This is used to
	 * determine when to re randomize the order with which the PM list's
	 * contents are returned upon next calls.
	 */
	private long resetCounter = 0;
	/**
	 * The actual order of the indexes used when returning the next PM
	 */
	private List<Integer> randomIndexes = new ArrayList<>();

	/**
	 * The constructor of the random iterator just passes the pm list to its
	 * superclass.
	 * 
	 * @param pmlist
	 */
	public RandomIterator(List<PhysicalMachine> pmlist) {
		super(pmlist);
	}

	/**
	 * Does a regular PM iterator reset, then it provides a new random sequence
	 * for the iteration. The random sequence is only generated if the size of
	 * the PM list changed or if the resetCounter reaches certain amounts.
	 */
	@Override
	public void reset() {
		super.reset();
		resetCounter++;
		var doShuffle = resetCounter % 10000 == 0;
		if (maxIndex != randomIndexes.size()) {
			randomIndexes.clear();
			IntStream.range(0, maxIndex).forEach(randomIndexes::add);
			doShuffle = true;
		}
		if (doShuffle) {
			Collections.shuffle(randomIndexes);
		}
	}

	@Override
	public PhysicalMachine next() {
		return pmList.get(randomIndexes.get(index++));
	}
}
