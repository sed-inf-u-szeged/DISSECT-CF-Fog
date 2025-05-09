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

/**
 * Virtual appliances represent functional virtual machine images in the system.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 * 
 */
public class VirtualAppliance extends StorageObject {
	/**
	 * The background network load expected to be simulated between the
	 * appliance's hosting repository and the PM that hosts the VM while the VM
	 * runs its tasks.
	 * 
	 * Unit: bytes/tick
	 * 
	 * Rationale: if the VM is backed by a shared storage, then all its disk
	 * operations (while doing compute intensive tasks) are expected to generate
	 * network activities as well.
	 */
	private long bgNetworkLoad;
	/**
	 * The number of processing instructions to be done during the startup of
	 * the VM that utilizes this VA.
	 * 
	 * unit: instructions
	 */
	private double startupProcessing;

	/**
	 * Allows updating the appliance's properties in a single step.
	 * 
	 * @param startupProcess
	 *            number of processing instructions on startup
	 * @param nl
	 *            background network load while running tasks on a VM that has
	 *            storage backed by a remote repository
	 */
	private void setDetails(final double startupProcess, final long nl) {
		setBgNetworkLoad(nl);
		setStartupProcessing(startupProcess);
	}

	/**
	 * Creates a virtual appliance with default size and variance (for details,
	 * see StorageObject)
	 * 
	 * @param id
	 *            the id of the appliance
	 * @param startupProcess
	 *            number of processing instructions on startup
	 * @param nl
	 *            background network load while running tasks on a VM that has
	 *            storage backed by a remote repository
	 */
	public VirtualAppliance(final String id, final double startupProcess, final long nl) {
		super(id);
		setDetails(startupProcess, nl);
	}

	/**
	 * Creates a virtual appliance with custom size and size variance.
	 * 
	 * @param id
	 *            the id of the appliance
	 * @param startupProcess
	 *            number of processing instructions on startup
	 * @param nl
	 *            background network load while running tasks on a VM that has
	 *            storage backed by a remote repository
	 * @param vary
	 *            <i>true</i> if the requested appliance size is not fixed,
	 *            <i>false</i> otherwise
	 * @param reqDisk
	 *            the size of the disk image to host the virtual appliance (this
	 *            might not be the actual size, but could be used for generating
	 *            varying sizes according to the param <i>vary</i>). Unit: bytes.
	 */
	public VirtualAppliance(final String id, final double startupProcess, final long nl, boolean vary,
			final long reqDisk) {
		super(id, reqDisk, vary);
		setDetails(startupProcess, nl);
	}

	/**
	 * creates an object that holds the exact copy of the current virtual
	 * appliance with a different id
	 * 
	 * Please note this copy is not going to be registered in any repositories
	 * (unlike the other VA), nor will it be used by any virtual machines.
	 */
	@Override
	public VirtualAppliance newCopy(final String myid) {
		return new VirtualAppliance(myid, startupProcessing, bgNetworkLoad, false, size);
	}

	/**
	 * Allows to determine the background network load expected to be simulated
	 * between the appliance's hosting repository and the PM that hosts the VM
	 * while the VM runs its tasks.
	 * 
	 * Unit: bytes/tick
	 */
	public long getBgNetworkLoad() {
		return bgNetworkLoad;
	}

	/**
	 * Allows to determine the number of processing instructions to be done
	 * during the startup of the VM that utilizes this VA.
	 * 
	 * unit: number of instructions
	 */
	public double getStartupProcessing() {
		return startupProcessing;
	}

	/**
	 * Allows to set the background network load expected to be simulated
	 * between the appliance's hosting repository and the PM that hosts the VM
	 * while the VM runs its tasks.
	 * 
	 * Unit: bytes/tick
	 * 
	 * Warning: handle with care, this has no effect on already running virtual
	 * machines
	 */
	public void setBgNetworkLoad(long bgNetworkLoad) {
		this.bgNetworkLoad = bgNetworkLoad;
	}

	/**
	 * Allows to set the number of processing instructions to be done during the
	 * startup of the VM that utilizes this VA.
	 * 
	 * unit: number of instructions
	 * 
	 * Warning: handle with care, this has no effect on already running virtual
	 * machines
	 */
	public void setStartupProcessing(double startupProcessing) {
		this.startupProcessing = startupProcessing;
	}

	/**
	 * Provides a compact output of all data represented in this VA.
	 * 
	 * Useful for debugging.
	 */
	@Override
	public String toString() {
		return "VA(" + super.toString() + " sp:" + startupProcessing + " bgnl:" + bgNetworkLoad + ")";
	}
}
