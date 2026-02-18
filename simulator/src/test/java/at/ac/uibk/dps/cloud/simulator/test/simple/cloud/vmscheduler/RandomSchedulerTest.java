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
 *  (C) Copyright 2023, Gabor Kecskemeti (kecskemeti@iit.uni-miskolc.hu)
 */
package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmscheduler;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.RandomScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RandomSchedulerTest extends IaaSRelatedFoundation {
    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void testRandomSchedule() throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        var iaas = setupIaaS(RandomScheduler.class, SchedulingDependentMachines.class, 2, 2);
        fireVMat(iaas, 1, 5000, 2);
        Timed.simulateUntilLastEvent();
        assertEquals(1,iaas.machines.stream().mapToLong(PhysicalMachine::getCompletedVMs).sum());
    }
}
