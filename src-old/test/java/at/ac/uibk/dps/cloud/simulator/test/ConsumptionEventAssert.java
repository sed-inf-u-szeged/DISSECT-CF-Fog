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

package at.ac.uibk.dps.cloud.simulator.test;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConsumptionEventAssert extends ConsumptionEventAdapter {
	private final static ArrayList<Long> hitsInternal = new ArrayList<Long>();
	public final static List<Long> hits = Collections
			.unmodifiableList(hitsInternal);
	long arrivedAt = -1;
	final long expectedTime;
	final boolean failOnCancel;

	public ConsumptionEventAssert(final long expectedTime,
			final boolean failOnCancel) {
		this.expectedTime = expectedTime;
		this.failOnCancel = failOnCancel;
	}

	public ConsumptionEventAssert(final boolean failOnCancel) {
		this(-1L, failOnCancel);
	}

	public ConsumptionEventAssert(final long expectedTime) {
		this(expectedTime, false);
	}

	public ConsumptionEventAssert() {
		this(-1L, false);
	}

	private void updateArrivedAt() {
		arrivedAt = Timed.getFireCount();
	}

	public long getArrivedAt() {
		return arrivedAt;
	}

	@Override
	public void conComplete() {
		if (expectedTime > 0) {
			Assertions.assertEquals(expectedTime, Timed.getFireCount(), "Resource consumption completed at wrong time");
		}
		Assertions.assertFalse(
				isCancelled() || isCompleted(), "Should not receive consumption events multiple times");
		super.conComplete();
		updateArrivedAt();
		hitsInternal.add(arrivedAt);
	}

	@Override
	public void conCancelled(ResourceConsumption problematic) {
		if (failOnCancel) {
			Assertions.fail("Consumption got cancelled before transfer completed");
		}
		Assertions.assertFalse(
			isCancelled() || isCompleted(), "Should not receive consumption events multiple times");
		super.conCancelled(problematic);
		updateArrivedAt();
	}

	public static void resetHits() {
		hitsInternal.clear();
	}
}
