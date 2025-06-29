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
package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmscheduler;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.RoundRobinScheduler;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;

public class RoundRobinTest extends IaaSRelatedFoundation {

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void regularVMSchedule() throws InstantiationException,
			IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		IaaSService iaas = setupIaaS(RoundRobinScheduler.class,
				SchedulingDependentMachines.class, 2, 2);
		assertTrue(iaas.runningMachines.isEmpty(), "No machines should be running now");
		fireVMat(iaas, 50, 5000, 1);
		fireVMat(iaas, 200, 5000, 1);
		fireVMat(iaas, 500, 5000, 1);
		Timed.simulateUntil(Timed.getFireCount() + 400);
		assertEquals(1,
				iaas.runningMachines.size(), "Only one machine should be running now");
		Timed.simulateUntil(Timed.getFireCount() + 300);
		assertEquals(2,
				iaas.runningMachines.size(), "Both machines should be running now");
	}

	@Test
	@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
	public void seqVMSchedule() throws Exception {
		IaaSService iaas = setupIaaS(RoundRobinScheduler.class,
				AlwaysOnMachines.class, 2, 2);
		fireVMat(iaas, 50, 5000, 1);
		fireVMat(iaas, 51, 5000, 1);
		Timed.simulateUntil(Timed.getFireCount() + 400);
		for (PhysicalMachine pm : iaas.machines) {
			assertEquals(1, pm.numofCurrentVMs(), "Should not be any PM with more than one VM");
		}
	}
}
