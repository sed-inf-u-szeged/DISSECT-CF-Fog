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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation;

import java.util.Arrays;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.helpers.PMComparators;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

/**
 * Basic consolidator algorithm which tries to compact the used PM pool by
 * allocating VMs to more heavily allocated PMs.
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017-8"
 */
public class SimpleConsolidator extends Consolidator {

	/**
	 * This is a simple counter that one can query to determine how many migrations
	 * this algorithm ordered.
	 */
	public static long migrationCount = 0;

	/**
	 * The maximum free processing capacity needed to consider a PM fully loaded.
	 * 
	 * This might need to be updated if the per core performance of a PM is
	 * expressed in a different unit.
	 */
	public static final double pmFullLimit = 0.00000001;

	/**
	 * Just passes its parameters to the superclass's constructor
	 * 
	 * @param toConsolidate
	 * @param consFreq
	 */
	public SimpleConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
	}

	/**
	 * The actual consolidation algorithm, note this is rather computational heavy,
	 * and still provides just minimal gains over not consolidating at all. This is
	 * just offered as an example implementation of a consolidator.
	 */
	@Override
	protected void doConsolidation(PhysicalMachine[] pmList) {
		int lastItem = 0;
		// Keeps the machines from/to one can move a VM
		for (int i = 0; i < pmList.length; i++) {
			if (pmList[i].isHostingVMs() && pmList[i].freeCapacities.getTotalProcessingPower() > pmFullLimit) {
				pmList[lastItem++] = pmList[i];
			}
		}
		boolean didMove;
		lastItem--;
		int beginIndex = 0;
		do {
			didMove = false;
			// Reorders the machine array, so it has the heaviest loaded machines last
			Arrays.sort(pmList, beginIndex, lastItem + 1, PMComparators.highestToLowestFreeCapacity);
			for (int i = beginIndex; i < lastItem; i++) {
				// Tries to move VMs from the lightest loaded PMs
				PhysicalMachine source = pmList[i];
				VirtualMachine[] vmList = source.publicVms.toArray(new VirtualMachine[0]);
				int vmc = 0;
				for (VirtualMachine vm : vmList) {
					if (!VirtualMachine.State.RUNNING.equals(vm.getState())) {
						continue;
					}
					ResourceConstraints allocation = vm.getResourceAllocation().allocated;
					for (int j = lastItem; j > i; j--) {
						// to the heaviest loaded ones that can still accommodate the VMs in question
						PhysicalMachine target = pmList[j];
						ResourceAllocation alloc = null;
						try {
							// TODO: we do not keep the possibility of underprovisioning with strict
							// allocation = true
							alloc = target.allocateResources(allocation, true, PhysicalMachine.migrationAllocLen);
							if (alloc != null) {
								// A move is possible, migration is requested
								vm.migrate(alloc);
								if (target.freeCapacities.getTotalProcessingPower() < pmFullLimit) {
									// If the PM became heavily loaded because of the newly migrated VM, then it is
									// ignored for the rest of this consolidation run
									if (j != lastItem) {
										if (j == lastItem - 1) {
											pmList[j] = pmList[lastItem];
										} else {
											System.arraycopy(pmList, j + 1, pmList, j, lastItem - j);
										}
									}
									lastItem--;
								}
								migrationCount++;
								vmc++;
								didMove = true;
								break;
							}
						} catch (VMManagementException pmNotRunning) {
							System.err.println(
									"Error while handling vm " + vm.hashCode() + " === " + pmNotRunning.getMessage());
						} catch (NetworkException nex) {
							System.err.println(
									"NW Error while handling vm " + vm.hashCode() + " === " + nex.getMessage());
							if (alloc != null) {
								alloc.cancel();
							}
						}
					}
				}
				if (vmc == vmList.length) {
					// If all VMs were removed from the current PM, then the PM is free we don't
					// need to do anything with it (we assume the PM scheduler will take care of it)
					pmList[i] = pmList[beginIndex++];
				}
			}
		} while (didMove);
	}
}
