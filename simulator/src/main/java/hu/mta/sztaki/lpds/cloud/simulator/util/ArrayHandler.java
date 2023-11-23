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

package hu.mta.sztaki.lpds.cloud.simulator.util;

import java.util.ArrayList;

/**
 * A class to simplify often used arraylist handling operations
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *
 */
public class ArrayHandler {
	/**
	 * An item removal function that brings the last element from the array to
	 * the place of the to be removed item. It ruins the order of the array, but
	 * it is faster than the stock remove operation.
	 * 
	 * @param toRemoveFrom
	 *            The arraylist which is supposedly containing the element
	 * @param toRemoveWhat
	 *            The element to be removed
	 * @return true if the element was successfully removed, false otherwise.
	 */
	public static <T> boolean removeAndReplaceWithLast(final ArrayList<T> toRemoveFrom, final T toRemoveWhat) {
		final int loc = toRemoveFrom.indexOf(toRemoveWhat);
		if (loc >= 0) {
			final int sizeMinus = toRemoveFrom.size() - 1;
			final T lastItem = toRemoveFrom.remove(sizeMinus);
			if (loc != sizeMinus) {
				toRemoveFrom.set(loc, lastItem);
			}
			return true;
		}
		return false;
	}
}
