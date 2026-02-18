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

package hu.mta.sztaki.lpds.cloud.simulator.io;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

/**
 * Represents arbitrary data fragments (e.g. files) to be stored in a
 * repository. Also useful for modeling file transfers.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 *         Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class StorageObject {
	// TODO: think about if it would make things easier if we would refer here
	// the repository where this storage object is stored.

	/**
	 * the identifier of the storage object. This might not be unique
	 * everywhere. It is the user's responsibility to keep it unique with the
	 * newcopy function.
	 */
	public final String id;
	/**
	 * The actual size of the object. This is immutable, if a storage object
	 * needs to increase in size in a repository, it is recommended to replace
	 * it with a bigger one while holding the same name.
	 * 
	 * unit: bytes
	 */
	public final long size;

	/**
	 * Allows the creation of the storage object with unknown size (the
	 * simulator will pick a random one!
	 * 
	 * @param myid
	 *            the id of the new storage object
	 */
	public StorageObject(final String myid) {
		id = myid;
		size = 500000000L + (long) (SeedSyncer.centralRnd.nextDouble() * 19500000000L);
	}

	/**
	 * Allows the creation of the storage object with an influence on the size
	 * of the object
	 * 
	 * @param myid
	 *            the id of the new storage object
	 * @param mysize
	 *            the size of the storage object (this might not be the actual
	 *            size, but could be used for generating varying sizes according
	 *            to the param <i>vary</i>. Unit: bytes.
	 * @param vary
	 *            <i>true</i> if the requested storage object size is not fixed,
	 *            <i>false</i> otherwise
	 */
	public StorageObject(final String myid, final long mysize, boolean vary) {
		if (mysize < 0) {
			throw new IllegalArgumentException("Cannot create negative sized Storage Objects");
		}
		id = myid;
		size = vary ? (1 + 2 * mysize - (long) (2 * SeedSyncer.centralRnd.nextDouble() * mysize)) : mysize;
	}

	/**
	 * creates a new storage object based on the current one, but with new name.
	 * The size of the two objects will remain the same.
	 * 
	 * @param myid
	 *            the id of the new storage object
	 * @return the storage object that has the same size as this one but that
	 *         has the new id given in <i>myid</i>
	 */
	public StorageObject newCopy(final String myid) {
		return new StorageObject(myid, size, false);
	}

	/**
	 * Provides a compact output of all data represented in this Storage Object.
	 * 
	 * Useful for debugging.
	 */
	@Override
	public String toString() {
		return "SO(id:" + id + " size:" + size + ")";
	}
}
