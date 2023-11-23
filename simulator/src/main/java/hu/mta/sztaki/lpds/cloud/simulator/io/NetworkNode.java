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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.*;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

/**
 * This class represents a networked element in the system. The class also
 * contains the definitions for the helper classes in the network simulation
 * that together are responsible to introduce and simulate network delays in the
 * system. The instances of this class are always present and represent the
 * general network capabilities in the hosts.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 *         Distributed Systems, MTA SZTAKI (c) 2012,2014-"
 * 
 */
public class NetworkNode {

	public static class NetworkException extends Exception {
		private static final long serialVersionUID = 5173643896341066497L;

		public NetworkException(String msg) {
			super(msg);
		}
	}

	/**
	 * The instances of this class represent an individual data transfer in the
	 * system. The visibility of the class and its members are defined so the
	 * compiler does not need to generate access methods for the members thus
	 * allowing fast and prompt changes in its contents.
	 * 
	 * To create a new instance of this class, one must use the initTransfer method
	 * of the NetworkNode.
	 * 
	 * <i>WARNING</i> this is an internal representation of the transfer. This class
	 * is not supposed to be used outside of the context of the NetworkNode.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
	 *         of Innsbruck (c) 2013"
	 * 
	 */
	static class SingleTransfer extends ResourceConsumption {

		/**
		 * This constructor describes the basic properties of an individual transfer.
		 * 
		 * @param tottr The amount of data to be transferred during the lifetime of the
		 *              just created object
		 * @param e     Specify here the event to be fired when the just created object
		 *              completes its transfers. With this event it is possible to
		 *              notify the entity who initiated the transfer.
		 */
		private SingleTransfer(final int latency, final long tottr, final double limit, final MaxMinConsumer in,
				final MaxMinProvider out, final ResourceConsumption.ConsumptionEvent e) {
			super(tottr, limit, in, out, e);
			new DeferredEvent(latency) {
				@Override
				protected void eventAction() {
					regAndCancelOnFailure();
				}
			};
		}

		private void regAndCancelOnFailure() {
			if (!registerConsumption()) {
				cancel();
			}
		}
	}

	/**
	 * Represents the possible states of the network nodes modeled in the system
	 * 
	 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
	 *         Moores University, (c) 2017"
	 * 
	 */
	public enum State {
		/**
		 * The machine is completely switched off, minimal consumption is recorded.
		 */
		OFF,
		/**
		 * The machine is currently serving VMs. The machine and its VMs are consuming
		 * energy.
		 */
		RUNNING
	}

	State currState;
	/**
	 * Models the incoming network connections of this network node
	 */
	public final MaxMinConsumer inbws;
	/**
	 * Models the outgoing network connections of this network node
	 */
	public final MaxMinProvider outbws;
	/**
	 * Models the write bandwidth of the disk of this network node
	 */
	public final MaxMinConsumer diskinbws;
	/**
	 * Models the read bandwidth of the disk of this network node
	 */
	public final MaxMinProvider diskoutbws;
	/**
	 * Models the memory write bandwidth on this network node
	 */
	public final MaxMinConsumer meminbws;
	/**
	 * Models the memory read bandwidth on this network node
	 */
	public final MaxMinProvider memoutbws;

	private final ResourceSpreader[] allSpreaders;

	/**
	 * The name of this network node (this could be an IP or what is most suitable
	 * for the simulation at hand).
	 */
	private String name;
	
	/**
	 * The direct network connections of this network node.
	 * 
	 * Contents of the map:
	 * <ul>
	 * <li>The key of this map lists the names of the network nodes to which this
	 * particular network node is connected.
	 * <li>The value of this map lists the latencies in ticks between this network
	 * node and the node named in the key.
	 * </ul>
	 * 
	 */
	private final Map<String, Integer> latencies;
	/**
	 * Mapping between the various PM states and its representative disk power
	 * behaviors.
	 */
	private final Map<String, PowerState> storagePowerBehavior;
	/**
	 * Mapping between the various PM states and its representative network power
	 * behaviors.
	 */
	private final Map<String, PowerState> networkPowerBehavior;

	/**
	 * This function initializes the bandwidth spreaders for the node to ensure
	 * equal network share for each transfer occurring on the node.
	 * 
	 * @param maxInBW  the input bw of the node
	 * @param maxOutBW the output bw of the node
	 * @param diskBW   the disk bw of the node
	 */
	public NetworkNode(final String id, final long maxInBW, final long maxOutBW, final long diskBW,
			final Map<String, Integer> latencymap, Map<String, PowerState> diskPowerTransitions,
			Map<String, PowerState> networkPowerTransitions) {
		if (diskPowerTransitions == null || networkPowerTransitions == null) {
			throw new IllegalStateException("Cannot initialize network node without a complete power behavior set");
		}
		storagePowerBehavior = Collections.unmodifiableMap(diskPowerTransitions);
		networkPowerBehavior = Collections.unmodifiableMap(networkPowerTransitions);
		name = id;
		outbws = new MaxMinProvider(maxOutBW);
		inbws = new MaxMinConsumer(maxInBW);
		diskinbws = new MaxMinConsumer(diskBW / 2f);
		diskoutbws = new MaxMinProvider(diskBW / 2f);
		// Just making sure we will have enough bandwidth for every operation we
		// could possibly have
		final double memBW = (maxOutBW + maxInBW + diskBW);
		meminbws = new MaxMinConsumer(memBW);
		memoutbws = new MaxMinProvider(memBW);
		latencies = latencymap;
		allSpreaders = new ResourceSpreader[] { diskinbws, diskoutbws, outbws, inbws, meminbws, memoutbws };
		forceState(State.OFF);
	}

	/**
	 * Determines the total output bandwidth available for the node
	 * 
	 * @return the maximum bandwidth with this network node can send data to the
	 *         outside world
	 */
	public long getOutputbw() {
		return (long) outbws.getPerTickProcessingPower();
	}

	/**
	 * Determines the total input bandwidth available for the node
	 * 
	 * @return the maximum bandwidth with this network node can receive data from
	 *         the outside world
	 */
	public long getInputbw() {
		return (long) inbws.getPerTickProcessingPower();
	}

	/**
     * Determines the latency values of the nodes
     */
    public Map<String, Integer> getLatencies() {
        return this.latencies;
    }
    
    /**
     * Updates or adds a new latency connection 
     */
    public void addLatencies(String repository,int latencyValue) {
        this.latencies.put(repository, latencyValue);
    }
    
	/**
	 * The bandwidth available when duplicating local disk contents.
	 * 
	 * @return the maximal bandwidth usable by the network node while copying a file
	 *         or raw disk blocks on its storage subsystem
	 */
	public long getDiskbw() {
		return (long) diskinbws.getPerTickProcessingPower() * 2;
	}

	/**
	 * This function ensures the proper initialization of an individual transfer.
	 * 
	 * @param size defines the size of the transfer to be simulated
	 * @param from defines the source of the transfer
	 * @param to   defines the destination of the transfer
	 * @param e    defines the way the initiator will be notified upon the
	 *             completion of the transfer
	 * @return the resource consumption object representing the transfer. This is
	 *         returned to allow the cancellation of the object or to allow the
	 *         observation of its state.
	 */
	public static ResourceConsumption initTransfer(final long size, final double limit, final NetworkNode from,
			final NetworkNode to, final ResourceConsumption.ConsumptionEvent e) throws NetworkException {
		from.ensureRunning();
		to.ensureRunning();
		if (from == to) {
			return new SingleTransfer(0, size, limit, from.diskinbws, from.diskoutbws, e);
		} else {
			return new SingleTransfer(checkConnectivity(from, to), size, limit, to.inbws, from.outbws, e);
		}
	}

	private void ensureRunning() throws NetworkException {
		if (!currState.equals(State.RUNNING)) {
			throw new NetworkException("This networknode is not ready for communication");
		}
	}

	/**
	 * This function allows the simplified creation of singletransfer objects for
	 * modeling the operation of writing data to the disk/network of this node from
	 * its memory.
	 * 
	 * @param size   the amount of data to be transferred from the memory to the
	 *               disk/network (in bytes)
	 * @param limit  the maximum bandwidth allowed to be available for this
	 *               particular transfer (in bytes/tick)
	 * @param toDisk
	 *               <ul>
	 *               <li><i>true</i> if the transfer should be managed to the
	 *               network node's disk
	 *               <li><i>false</i> if the bytes read from memory should be sent
	 *               over the network
	 *               </ul>
	 * @param e      to be fired when the transfer completes
	 * @return the resource consumption object that models the transfer. This is
	 *         returned to allow the cancellation of the object or to allow the
	 *         observation of its state.
	 */
	public ResourceConsumption pushFromMemory(final long size, final double limit, boolean toDisk,
			final ResourceConsumption.ConsumptionEvent e) throws NetworkException {
		ensureRunning();
		return new SingleTransfer(0, size, limit, toDisk ? diskinbws : inbws, memoutbws, e);
	}

	/**
	 * This function allows the simplified creation of singletransfer objects for
	 * modeling the operation of reading data from the disk/network of this node to
	 * its memory.
	 * 
	 * @param size     the amount of data to be transferred to the memory from the
	 *                 disk/network (in bytes)
	 * @param limit    the maximum bandwidth allowed to be available for this
	 *                 particular transfer (in bytes/tick)
	 * @param fromDisk
	 *                 <ul>
	 *                 <li><i>true</i> if the transfer should be managed from the
	 *                 network node's disk
	 *                 <li><i>false</i> if the bytes written to memory should be
	 *                 received over the network
	 *                 </ul>
	 * @param e        to be fired when the transfer completes
	 * @return the resource consumption object that models the transfer. This is
	 *         returned to allow the cancellation of the object or to allow the
	 *         observation of its state.
	 */
	public ResourceConsumption readToMemory(final long size, final double limit, boolean fromDisk,
			final ResourceConsumption.ConsumptionEvent e) throws NetworkException {
		ensureRunning();
		return new SingleTransfer(0, size, limit, meminbws, fromDisk ? diskoutbws : outbws, e);
	}

	/**
	 * Determines if there is direct network connection possible between two network
	 * nodes
	 * 
	 * @param from the network node which is expected to send some data
	 * @param to   the network node which is expected to receive the sent data
	 * @return the network latency of the connection between the two
	 * @throws NetworkException if there is no direct connection possible between
	 *                          the two specified nodes.
	 */
	public static int checkConnectivity(final NetworkNode from, final NetworkNode to) throws NetworkException {
		if (from == to) {
			return 0;
		}
		final Integer lat = from.latencies.get(to.name);
		if (lat == null)
			throw new NetworkException("No connection between: '" + from.name + "' and '" + to.name + "'");
		return lat;
	}

	/**
	 * Allows to query the networknode's name
	 * 
	 * @return the name of the node
	 */
	public String getName() {
		return name;
	}
	
	/**
     * Allows to modify the networknode's name. If the name is changed, it may be necessary to update the corresponding
     * latencyMaps as well.
     * @param name the new name of the node
     */
    public void setName(String name) {
        this.name = name;
    }

	/**
	 * provides an overview of the network node concentrating on the network's
	 * properties. useful for tracing and debug.
	 */
	@Override
	public String toString() {
		return "NetworkNode(Id:" + name + " NI:" + getInputbw() + ",NO:" + getOutputbw() + " -- RX:"
				+ inbws.getTotalProcessed() + " TX:" + outbws.getTotalProcessed() + " --, D:" + getDiskbw() + ")";
	}

	public void setState(State newState) throws NetworkException {
		if (!currState.equals(newState)) {
			if (currState.equals(State.RUNNING) &&
				Arrays.stream(allSpreaders).anyMatch(rs -> rs.toBeAdded.size() + rs.underProcessing.size() > 0)) {
				throw new NetworkException(
						"There is still some network activity in progress, cannot transition to a non-running state");
			}
			forceState(newState);
		}
	}

	private void forceState(State newState) {
		currState = newState;
		PowerState curStBehaviour = PowerTransitionGenerator.getPowerStateFromMap(storagePowerBehavior,
				newState.toString());
		PowerState curNwBehaviour = PowerTransitionGenerator.getPowerStateFromMap(networkPowerBehavior,
				newState.toString());
		diskinbws.setCurrentPowerBehavior(curStBehaviour);
		diskoutbws.setCurrentPowerBehavior(curStBehaviour);
		inbws.setCurrentPowerBehavior(curNwBehaviour);
		outbws.setCurrentPowerBehavior(curNwBehaviour);
	}

	public long getLastEvent() {
		long latest = -1;
		for (ResourceSpreader rs : allSpreaders) {
			FreqSyncer fs = rs.getSyncer();
			if (fs.isSubscribed()) {
				latest = Math.max(latest, fs.getNextEvent());
			}
		}
		return latest;
	}
}
