package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.Map;

public class Config {

    public static IaaSService createNode(String name, double cpu, long memory, long storage,
                                         double minpower, double idlepower, double maxpower,
                                         long bandwidth, int latency, Map<String, Integer> latencyMap) {

        IaaSService iaas = null;
        try {
            iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                 | NoSuchMethodException | SecurityException e) {
            SimLogger.logError("Node creation has failed: " + e);
        }

        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(minpower, idlepower, maxpower, 10, 20);

        // Physical Machine
        Repository pmRepo = new Repository(storage, name + "-internalRepo",
                bandwidth, bandwidth, bandwidth, latencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        PhysicalMachine pm = new PhysicalMachine(cpu, 1, memory, pmRepo, 60_000, 60_000,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));

        iaas.registerHost(pm);

        // External Repository
        Repository nodeRepo = new Repository(storage, name + "-externalRepo",
                bandwidth, bandwidth, bandwidth, latencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        iaas.registerRepository(nodeRepo);

        latencyMap.put(name + "-internalRepo", latency);
        latencyMap.put(name + "-externalRepo", latency);

        return iaas;
    }
}
