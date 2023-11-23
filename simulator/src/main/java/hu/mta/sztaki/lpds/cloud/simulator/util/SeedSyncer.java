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

import java.util.Random;

/**
 * A class to manage the random generator to be used if reproducible but random
 * results are expected from the simulator
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 */
public class SeedSyncer {
	/**
	 * The random generator that will be used by the system components and that
	 * is recommended to be used by simulations built on top of DISSECT-CF
	 */
	public static final Random centralRnd;
	/**
	 * The random seed for the central random generator.
	 * 
	 * To set this seed, you must define the system property of
	 * "hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer.seed" before running
	 * any simulations.
	 */
	public static final int seed;

	static {
		String seedText = System.getProperty("hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer.seed");
		if (seedText == null) {
			seed = 1;
		} else {
			seed = Integer.parseInt(seedText);
		}
		centralRnd = new Random(seed);
	}

	/**
	 * To restart the simulator's random generator
	 */
	public static void resetCentral() {
		centralRnd.setSeed(seed);
	}
}
