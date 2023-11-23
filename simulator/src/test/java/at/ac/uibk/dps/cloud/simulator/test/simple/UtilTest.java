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
package at.ac.uibk.dps.cloud.simulator.test.simple;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.util.CloudLoader;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;

import at.ac.uibk.dps.cloud.simulator.test.TestFoundation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class UtilTest extends TestFoundation {
	public String cloudDef = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<cloud id=\"test\"	scheduler=\"hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler\" pmcontroller=\"hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines\">\n"
			+ "<machine id=\"testPM\" cores=\"64\" processing=\"0.001\" memory=\"256000000000\">\n"
			+ "<powerstates kind=\"host\">\n"
			+ "<power	model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel\" idle=\"296\" max=\"493\" inState=\"default\" />\n"
			+ "<power	model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel\" idle=\"20\" max=\"20\" inState=\"OFF\" />\n"
			+ "</powerstates>\n"
			+ "<statedelays startup=\"89000\" shutdown=\"29000\" />\n"
			+ "<repository id=\"disk\" capacity=\"5000000000000\" inBW=\"250000\" outBW=\"250000\" diskBW=\"50000\">\n"
			+ "<powerstates kind=\"storage\">\n"
			+ "<power model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel\" idle=\"6.5\" max=\"9\" inState=\"default\" />\n"
			+ "<power model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel\" idle=\"0\" max=\"0\" inState=\"OFF\" />\n"
			+ "</powerstates>\n"
			+ "<powerstates kind=\"network\">\n"
			+ "<power model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel\" idle=\"3.4\" max=\"3.8\" inState=\"default\" />\n"
			+ "<power model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel\" idle=\"0\" max=\"0\" inState=\"OFF\" />\n"
			+ "</powerstates>\n"
			+ "<latency towards=\"repo\" value=\"5\" />\n"
			+ "</repository>\n"
			+ "</machine>\n"
			+ "<repository id=\"repo\" capacity=\"38000000000000\" inBW=\"250000\" outBW=\"250000\" diskBW=\"100000\">\n"
			+ "<powerstates kind=\"storage\">\n"
			+ "<power model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel\" idle=\"65\" max=\"90\" inState=\"default\" /> \n"
			+ "<power model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel\" idle=\"0\" max=\"0\" inState=\"OFF\" />\n"
			+ "</powerstates>\n"
			+ "<powerstates kind=\"network\">\n"
			+ "<power model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel\" idle=\"3.4\" max=\"3.8\" inState=\"default\" />\n"
			+ "<power model=\"hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel\" idle=\"0\" max=\"0\" inState=\"OFF\" />\n"
			+ "</powerstates>\n" + "<latency towards=\"disk\" value=\"5\" />\n"
			+ "</repository>\n" + "</cloud>\n";

	@Test
	@Timeout(value = 600, unit = TimeUnit.MILLISECONDS)
	public void cloudLoaderTest() throws Exception {
		File temp = File.createTempFile("dissect-test", "cloudLoader");
		RandomAccessFile raf = new RandomAccessFile(temp, "rw");
		raf.writeBytes(cloudDef);
		raf.close();
		IaaSService cloud = CloudLoader.loadNodes(temp.toString());
		Assertions.assertTrue(cloud.sched instanceof FirstFitScheduler,"FirstFitScheduler should be the VM scheduler");
		Assertions.assertTrue(cloud.pmcontroller instanceof AlwaysOnMachines, "AlwaysonMachines should be the PM scheduler");
		Assertions.assertEquals(1,
				cloud.machines.size(),"Only one PM should be loaded");
		Assertions.assertEquals(1,
				cloud.repositories.size(),"Only one repository should be loaded");
		temp.delete();
	}

}
