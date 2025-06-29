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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * This class offers a VM scheduler that keeps the VM request queue in order and
 * always places those VM requests first that have the smallest resource
 * demands. <i>WARNING:</i> this scheduler could potentially starve bigger VM
 * requests in the queue.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013"
 * 
 */
public class SmallestFirstScheduler extends FirstFitScheduler {
	/**
	 * This comparator allows ordering the VM request queue primarily by cumulative
	 * resource request size and secondarily by request arrival time. (e.g. a
	 * request with the same size but earlier arrival time will be ordered to be
	 * ahead of the other)
	 */
	public static final Comparator<QueueingData> vmQueueSmallestFirstComparator = (final QueueingData o1,
			final QueueingData o2) -> {
		final int compRC = o1.cumulativeRC.compareTo(o2.cumulativeRC);
		return compRC == 0 ? Long.signum(o1.receivedTime - o2.receivedTime) : compRC;
	};

	/**
	 * A priority queue that implements the necessary list related operations used
	 * in the first fit scheduler and scheduler classes.
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2014"
	 *
	 */
	public static class SFQueue extends PriorityQueue<QueueingData> implements List<QueueingData> {
		/**
		 * A message to show if the scheduler/first fit scheduler implementation would
		 * try to use previously unused List operations that were not implemented so
		 * far.
		 */
		private static final String UFCmessage = "Unexpected function call";
		private static final long serialVersionUID = 2693241597335321816L;

		/**
		 * Prepares the underlying priority queue
		 */
		public SFQueue() {
			super(5, vmQueueSmallestFirstComparator);
		}

		private IllegalStateException genISE() {
			return new IllegalStateException(UFCmessage);
		}

		/**
		 * Not supported operation
		 */
		@Override
		public boolean addAll(int index, Collection<? extends QueueingData> c) {
			throw genISE();
		}

		/**
		 * only the head of the queue is allowed to be queried
		 */
		@Override
		public QueueingData get(int index) {
			if (index == 0)
				return peek();
			throw genISE();
		}

		/**
		 * Not supported operation
		 */
		@Override
		public QueueingData set(int index, QueueingData element) {
			throw genISE();
		}

		/**
		 * Not supported operation
		 */
		@Override
		public void add(int index, QueueingData element) {
			throw genISE();
		}

		/**
		 * Only the head of the queue can be removed!
		 */
		@Override
		public QueueingData remove(int index) {
			if (index == 0)
				return poll();
			throw genISE();
		}

		/**
		 * Not supported operation
		 */
		@Override
		public int indexOf(Object o) {
			throw genISE();
		}

		/**
		 * Not supported operation
		 */
		@Override
		public int lastIndexOf(Object o) {
			throw genISE();
		}

		/**
		 * Not supported operation
		 */
		@Override
		public ListIterator<QueueingData> listIterator() {
			throw genISE();
		}

		/**
		 * Not supported operation
		 */
		@Override
		public ListIterator<QueueingData> listIterator(int index) {
			throw genISE();
		}

		/**
		 * Not supported operation
		 */
		@Override
		public List<QueueingData> subList(int fromIndex, int toIndex) {
			throw genISE();
		}

	}

	/**
	 * Passes the IaaSService further to its super class. Overwrites the request
	 * queuing mechanism with one that orders the VM requests according to their
	 * total resource requirements.
	 * 
	 * @param parent the IaaS Service which this SmallestFirstScheduler operates on
	 */
	public SmallestFirstScheduler(final IaaSService parent) {
		super(parent);
		queue = new SFQueue();
	}
}
