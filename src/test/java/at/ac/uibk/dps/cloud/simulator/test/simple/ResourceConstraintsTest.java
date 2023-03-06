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
 *  (C) Copyright 2017, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 */

package at.ac.uibk.dps.cloud.simulator.test.simple;

import static org.junit.jupiter.api.Assertions.*;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

public class ResourceConstraintsTest {
	public void threeWayCompareCheckContract(ResourceConstraints rc1, ResourceConstraints rc2,
			ResourceConstraints rc3) {
		int res1 = rc1.compareTo(rc2);
		int res2 = rc2.compareTo(rc1);
		int res3 = rc1.compareTo(rc3);
		int res4 = rc3.compareTo(rc1);
		int res5 = rc2.compareTo(rc3);
		int res6 = rc3.compareTo(rc2);
		boolean baseCondition = res1 == -res2 && res3 == -res4 && res5 == -res6;
		boolean transitiveCondition1 = res1 == -1 && res5 == -1 && res3 == -1;
		boolean transitiveCondition2 = res1 == 1 && res5 == 1 && res3 == 1;
		boolean otherSituation = (res1 == 1 && res5 == -1) || (res1 == -1 && res5 == 1);
		assertTrue(baseCondition && (otherSituation || (transitiveCondition1 ^ transitiveCondition2)), "The RC set [" + rc1 + " " + rc2 + " " + rc3 + "] violates the comparator's rules");
	}

	public ResourceConstraints convertTripleToConstraints(double[] theTriple) {
		return new ConstantConstraints(theTriple[0], theTriple[1], (long) theTriple[2]);
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void testComparatorContract() {
		double[][] exampleDataTriples = { { 1773, 16, 1121 }, { 1335, 1057, 159 }, { 185, 947, 21 }, // Contract
																										// check
																										// 1
				{ 169, 809, 368 }, { 252, 94, 1470 }, { 193, 44, 483 }, // Contract
																		// check
																		// 2
				{ 765, 16, 1176 }, { 745, 208, 1207 }, { 905, 1442, 1741 }, // Contract
																			// check
																			// 3
				{ 803, 353, 1126 }, { 63, 408, 126 }, { 765, 487, 752 }, // Contract
																			// check
																			// 4
				{ 655, 460, 477 }, { 1573, 1138, 46 }, { 969, 952, 821 }, // Contract
																			// check
																			// 5
				{ 532, 285, 660 }, { 1010, 1645, 578 }, { 295, 619, 126 } // Contract
																			// check
																			// 6
		};

		for (int i = 0; i < exampleDataTriples.length; i += 3) {
			threeWayCompareCheckContract(convertTripleToConstraints(exampleDataTriples[0]),
					convertTripleToConstraints(exampleDataTriples[1]),
					convertTripleToConstraints(exampleDataTriples[2]));
		}

		ResourceConstraints rcEq1 = new ConstantConstraints(3, 4, 5);
		ResourceConstraints rcEq2 = new ConstantConstraints(rcEq1);
		assertEquals(0,
				rcEq1.compareTo(rcEq2) + rcEq2.compareTo(rcEq1) + rcEq1.compareTo(rcEq1), "When the two items are identical compareto must result in 0 return value");
	}

}
