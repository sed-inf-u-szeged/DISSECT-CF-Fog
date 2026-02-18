package hu.u_szeged.inf.fog.simulator.agent.demo;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Config {

    public static final Map<String, Object> DUMMY_CONFIGURATION =
            Map.ofEntries(
                    Map.entry("simLength",24 * 60 * 60 * 1000L), // 1 day
                    Map.entry("submissionDelay", List.of(0)), // 1 app
                    Map.entry("samplingFreq", 10_000L), // 10 sec.
                    Map.entry("resFileSize", 1_024L), // 1 kB
                    Map.entry("inputDir", Paths.get(ScenarioBase.RESOURCE_PATH + "AGENT_examples")),
                    Map.entry("rankingMethod", "random")
            );

    public static final Map<String, Object> NOISE_CLASS_ONFIGURATION =
            Map.ofEntries(
                    Map.entry("simLength",24 * 60 * 60 * 1000L), // 1 day
                    Map.entry("submissionDelay", List.of(0)),
                    //Map.entry("submissionDelay", List.of(0, 0, 0, 60, 60, 120, 150, 150, 150, 150)), // 10 app
                    Map.entry("samplingFreq", 10_000L), // 10 sec.
                    Map.entry("soundFileSize", 655_360L), // 640 kB
                    Map.entry("resFileSize", 1_024L), // 1 kB
                    Map.entry("minSoundLevel", 30), // dB
                    Map.entry("maxSoundLevel", 130), // dB
                    Map.entry("soundThreshold", 70), // dB
                    Map.entry("minCpuTemp", 55D), // ℃
                    Map.entry("maxCpuTemp", 85D), // ℃
                    Map.entry("cpuTempTreshold", 80D), // ℃
                    Map.entry("minContainerCount", 2), // pc.
                    Map.entry("lengthOfProcessing", 1_700D), // ms
                    Map.entry("cpuTimeWindow", 300_000L), // 5 min.
                    Map.entry("cpuLoadScaleUp", 70D), // %
                    Map.entry("cpuLoadScaleDown", 30D), // %
                    Map.entry("offloadLimitPerIteration", Integer.MAX_VALUE),
                    //Map.entry("offloadLimitPerIteration", 3_600),
                    //Map.entry("samplingStrategy", "random"),
                    Map.entry("samplingStrategy", "lazy"),
                    Map.entry("inputDir", Paths.get(ScenarioBase.RESOURCE_PATH + "AGENT_examples")),
                    Map.entry("rankingMethod", "random"),
                    //Map.entry("rankingMethod", "rank_no_re"),
                    //Map.entry("rankingMethod", "rank_re_add"),
                    //Map.entry("rankingMethod", "rank_re_mul"),
                    //Map.entry("rankingMethod", "vote_wo_reliability"),
                    //Map.entry("rankingMethod", "vote_w_reliability"),
                    //Map.entry("rankingMethod", "vote_w_reliability_mul"),
                    //Map.entry("swarmAgentType", "greedy"),
                    Map.entry("swarmAgentType", "forecast"),
                    Map.entry("csvLogging", true),
                    Map.entry("predictorDir", "/home/markusa/Documents/SZTE/repos/Swarmchestrate-TSforecasting/"),
                    Map.entry("predictorModelPath", "/home/markusa/Documents/SZTE/repos/Swarmchestrate-TSforecasting/checkpoints/" +
                            "simulator1__UNC-1-Noise-Sensor-6_1min_pl128")
                    /*
                    public static final String RANKING_SCRIPT = "/home/markusa/Documents/SZTE/repos/swarm-deployment/for_simulator/call_ranking_func.py";
                    public static final String PREDICTOR_SCRIPT = "/;
                    */
            );

    //public static final Map<String, Object> APP_TYPE = DUMMY_CONFIGURATION;
    public static final Map<String, Object> APP_TYPE = NOISE_CLASS_ONFIGURATION;
    
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
        
        // PM
        Repository pmRepo1 = new Repository(storage, name + "-internalRepo", bandwidth, bandwidth, bandwidth, latencyMap, 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        PhysicalMachine pm1 = new PhysicalMachine(cpu, 1, memory, pmRepo1, 60_000, 60_000, 
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));

        iaas.registerHost(pm1);
        
        // Repository
        Repository nodeRepo = new Repository(storage, name + "-externalRepo", bandwidth, bandwidth, bandwidth, latencyMap, 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        
        iaas.registerRepository(nodeRepo);
        latencyMap.put(name + "-internalRepo", latency);
        latencyMap.put(name + "-externalRepo", latency);
        
        return iaas;
    }
}