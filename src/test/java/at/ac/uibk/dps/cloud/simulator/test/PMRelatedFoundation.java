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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Map;


import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public class PMRelatedFoundation extends ConsumptionEventFoundation {
	public final static PrintStream realStdOut = System.out;
	public final static PrintStream realStdErr = System.err;
	public static final double minpower = 20;
	public static final double idlepower = 200;
	public static final double maxpower = 300;
	public static final double diskDivider = 10;
	public static final double netDivider = 20;
	public static final double totalIdle = idlepower + idlepower / diskDivider + idlepower / netDivider;
	public final static Map<String, PowerState> defaultHostTransitions;
	public final static Map<String, PowerState> defaultStorageTransitions;
	public final static Map<String, PowerState> defaultNetworkTransitions;

	static {
		try {
			EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator
					.generateTransitions(minpower, idlepower, maxpower, diskDivider, netDivider);
			defaultHostTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
			defaultStorageTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
			defaultNetworkTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot initialize the default transitions");
		}
	}

	@BeforeAll
	public static void initStaticParts() {
		// Ensure that the most important classes are loaded before we do
		// anything (so the timeouts would not occur)
		try {
			new PhysicalMachine(1, 1, 1, new Repository(1, "", 1, 1, 1, null, null, null), 1, 1, null);
		} catch (Exception e) {

		}
	}

	@BeforeEach
	public void introduceRedirections() {
		try {
			System.setOut(new PrintStream(new OutputStream() {

				@Override
				public void write(int arg0) throws IOException {

				}
			}));
			System.setErr(new PrintStream(new OutputStream() {

				@Override
				public void write(int arg0) throws IOException {

				}
			}));

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@AfterEach
	public void revertRedirects() {
		System.setOut(realStdOut);
		System.setErr(realStdErr);
	}

}
