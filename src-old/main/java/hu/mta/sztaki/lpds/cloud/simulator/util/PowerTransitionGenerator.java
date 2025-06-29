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
package hu.mta.sztaki.lpds.cloud.simulator.util;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This helper class provides a simple way to generate the necessary power
 * transition functions for physical machine behavior.
 * <p>
 * WARNING: this is not intended to be used for realistic modeling of physical
 * machines but allows a simplified creation of physical machines for which the
 * power model is not expected to be used.
 *
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 * MTA SZTAKI (c) 2014"
 */
public class PowerTransitionGenerator {

    public static final String defaultPowerState = "default";

    /**
     * The generator function that derives the power transition and power state
     * definitions from a few simple parameters. The generated power states will all
     * be based on the linear consumption model (except during power off state).
     *
     * @param minpower    the power (in W) to be drawn by the PM while it is
     *                    completely switched off (but plugged into the wall socket)
     * @param idlepower   the power (in W) to be drawn by the PM's CPU while it is
     *                    running but not doing any useful tasks.
     * @param maxpower    the power (in W) to be drawn by the PM's CPU if it's CPU
     *                    is completely utilized
     * @param diskDivider the ratio of the PM's disk power draw values compared to
     *                    the it's CPU's power draw values
     * @param netDivider  the ratio of the PM's network power draw values compared
     *                    to the it's CPU's power draw values
     * @return a power state setup useful for instantiating PMs
     */
    public static EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> generateTransitions(
            double minpower, double idlepower, double maxpower, double diskDivider, double netDivider) {
        return new EnumMap<PowerStateKind, Map<String, PowerState>>(Arrays.stream(PowerStateKind.values()).map(kind -> {
            final HashMap<String, PowerState> statemap = new HashMap<>();
            switch (kind) {
                case host:
                    final PowerState hostDefault = new PowerState(idlepower, maxpower - idlepower, LinearConsumptionModel::new);
                    statemap.putAll(PhysicalMachine.StatesOfHighEnergyConsumption.stream().collect(Collectors.toMap(Enum::toString, astate -> hostDefault)));
                    statemap.put(PhysicalMachine.State.OFF.toString(),
                            new PowerState(minpower, 0, ConstantConsumptionModel::new));
                    break;
                case network:
                    putBasicStateBehaviour(statemap, idlepower, maxpower, netDivider);
                    break;
                case storage:
                    putBasicStateBehaviour(statemap, idlepower, maxpower, diskDivider);
                    break;
            }
            return Pair.of(kind, statemap);
        }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight)));
    }

    /**
     * Generates basic power behaviour properties for off/running states. Generates off states with no consumption, and
     * running/on states with consumption derived from the overall consumption of the machine. So the individual
     * consumptions of the various components are assumed to be linearly proportional to the overall consumption of a machine.
     *
     * @param statemap  The output will go here
     * @param idlepower The machine's idle power draw
     * @param maxpower  The machine's maximum power draw
     * @param divider   The ratio between the total power draw figures of the machine and the per component power draw figures
     */
    private static void putBasicStateBehaviour(HashMap<String, PowerState> statemap, double idlepower, double maxpower, double divider) {
        statemap.put(NetworkNode.State.OFF.toString(), new PowerState(0, 0, ConstantConsumptionModel::new));
        statemap.put(NetworkNode.State.RUNNING.toString(), new PowerState(idlepower / divider / 2,
                (maxpower - idlepower) / divider / 2, LinearConsumptionModel::new));
    }

    /**
     * fetches the required power state from the corresponding power state map. If
     * the new state is not listed, it serves back the default mapping
     *
     * @param theMap   The map to look up the new power state
     * @param newState the textual spec of the power state
     * @return the power state to be used in accordance to the textual spec
     */
    public static PowerState getPowerStateFromMap(final Map<String, PowerState> theMap, final String newState) {
        PowerState returner;
        if ((returner = theMap.get(newState)) == null) {
            returner = theMap.get(defaultPowerState);
        }
        return returner;
    }

    /**
     * When defining powertransitions for the PM one has to label each transition's
     * properties with a kind
     *
     * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
     * MTA SZTAKI (c) 2014"
     */
    public enum PowerStateKind {
        /**
         * the powerstate definitions belong to the cpu and memory resources of the PM
         */
        host,
        /**
         * the powerstate definitions belong to the local disk of the PM
         */
        storage,
        /**
         * the powerstate definitions belong to the network interface of the PM
         */
        network
    }

}
