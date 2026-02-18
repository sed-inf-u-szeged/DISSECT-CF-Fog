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

package hu.mta.sztaki.lpds.cloud.simulator.notifications;

import java.util.List;

public class LoopedDispatcher implements EventDispatcherCore {
	public static final LoopedDispatcher instance = new LoopedDispatcher();

	@Override
	public <T, P> void mainNotificationLoop(final StateDependentEventHandler<T, P> handler, final P payload) {
		handler.listeners.forEach(listener -> handler.myHandler.sendNotification(listener, payload));
	}

	@Override
	public <T, P> void add(final StateDependentEventHandler<T, P> handler, final T item) {
		handler.listeners.add(item);
	}

	@Override
	public <T, P> void addAll(final StateDependentEventHandler<T, P> handler, final List<T> items) {
		handler.listeners.addAll(items);
	}

	@Override
	public <T, P> void remove(final StateDependentEventHandler<T, P> handler, final T item) {
		handler.listeners.remove(item);
		if (handler.listeners.size() == 1) {
			handler.eventing = DirectDispatcher.instance;
		}
	}

	@Override
	public <T, P> void removeAll(final StateDependentEventHandler<T, P> handler, final List<T> items) {
		handler.listeners.removeAll(items);
		switch (handler.listeners.size()) {
			case 0 -> handler.eventing = NullDispatcher.instance;
			case 1 -> handler.eventing = DirectDispatcher.instance;
		}
	}
}
