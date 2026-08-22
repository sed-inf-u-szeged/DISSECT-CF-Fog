package hu.u_szeged.inf.fog.simulator.agent.demo;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto.ExhaustiveMappingStrategy;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Config {

    // TODO: create a common config map for shared parameters
    public static final Map<String, Object> DUMMY_CONFIGURATION =
            Map.ofEntries(
                    Map.entry("simLength", 24 * 60 * 60 * 1000L), // 1 day
                    Map.entry("submissionDelay", List.of(0)), // 1 app
                    Map.entry("maxRebroadCast", 2),
                    Map.entry("samplingFreq", 10_000L), // 10 sec.
                    Map.entry("resFileSize", 1_024L), // 1 kB
                    Map.entry("computeTaskBase", 8_000.0), // fixed CPU work for processing one received file (no. instructions)
                    Map.entry("computeTaskPerByte", 16.0), // additional CPU work per received byte (no. instructions)
                    Map.entry("inputDir", Paths.get(ScenarioBase.RESOURCE_PATH + "AGENT_examples")),
                    Map.entry("csvLogging", true),

                    Map.entry("rankingMethod", "random"),
                    //Map.entry("rankingMethod", "rank_no_re"),
                    Map.entry("onlyFirstOffer", false),
                    Map.entry("mappingStrategy", new ExhaustiveMappingStrategy()),
                    Map.entry("atomicOffers", true),
                    //Map.entry("mappingStrategy", new SimulatedAnnealingStrategy()),
                    //Map.entry("mappingStrategy", new FirstFitMappingStrategy(true)),
                    //Map.entry("atomicOffers", false),


                    Map.entry("atomicConstructionRestarts", 100),
                    Map.entry("atomicRepairRestarts", 20),

                    Map.entry("saNeighborAttempts", 20),
                    Map.entry("saMaxIterations", 10_000),
                    Map.entry("saInitialTemperature", 1.0),
                    Map.entry("saMinimumTemperature", 0.0001),
                    Map.entry("saCoolingRate", 0.999),

                    Map.entry("atomicSaInitialHardPenaltyWeight", 1.0),
                    Map.entry("atomicSaFinalHardPenaltyWeight", 100.0),
                    Map.entry("atomicSaAdditionalRemovalProbability", 0.25)

                    // latency heavy
                    //Map.entry("samplingFreq", 1_000L),
                    //Map.entry("resFileSize", 1_024L),
                    //Map.entry("computeTaskBase", 1_000.0),
                    //Map.entry("computeTaskPerByte", 0.0),
                    // bandwidth heavy
                    //Map.entry("samplingFreq", 60_000L),
                    //Map.entry("resFileSize", 100L * ScenarioBase.MB_IN_BYTE),
                    //Map.entry("computeTaskBase", 1_000.0),
                    //Map.entry("computeTaskPerByte", 0.0,
                    // compute heavy
                    //Map.entry("samplingFreq", 10_000L),
                    //Map.entry("resFileSize", 1_024L),
                    //Map.entry("computeTaskBase", 10_000_000.0),
                    //Map.entry("computeTaskPerByte", 0.0),

            );

    public static final Map<String, Object> PARKING_CONFIGURATION =
            Map.ofEntries(
                    Map.entry("simLength",24 * 60 * 60 * 1000L), // 1 day
                    //Map.entry("simLength",5 * 365 * 24 * 60 * 60 * 1000L), // 5 years

                    Map.entry("submissionDelay", List.of(0)), // 1 app

                    Map.entry("batteryCapacity", 600_000), // unit

                    Map.entry("cooldownFreq", 60_000L), // 1 min.
                    Map.entry("gatewayFreq", 60_000L), // 1 min.

                    Map.entry("inputDir", Paths.get(ScenarioBase.RESOURCE_PATH + "AGENT_examples")),
                    Map.entry("csvLogging", true)
            );

    public static final Map<String, Object> NOISE_CLASS_CONFIGURATION =
            new HashMap<>(Map.ofEntries(
                    Map.entry("simLength",24 * 60 * 60 * 1000L), // 1 day
                    Map.entry("submissionDelay", List.of(0)),
                    //Map.entry("submissionDelay", List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                    //Map.entry("submissionDelay", List.of(0, 0, 0, 60, 60, 120, 150, 150, 150, 150)), // 10 app
                    Map.entry("noiseSensorCount", 10),
                    Map.entry("samplingFreq", 10_000L), // 10 sec.
                    Map.entry("soundFileSize", 655_360L), // 640 kB
                    Map.entry("resFileSize", 1_024L), // 1 kB
                    Map.entry("minSoundLevel", 30), // dB
                    Map.entry("maxSoundLevel", 130), // dB
                    Map.entry("soundThreshold", 0), // dB
                    Map.entry("minCpuTemp", 55D), // ℃
                    Map.entry("maxCpuTemp", 85D), // ℃
                    Map.entry("cpuTempTreshold", 80D), // ℃
                    Map.entry("minContainerCount", 2), // pc.
                    Map.entry("lengthOfProcessing", 1_700D), // ms
                    Map.entry("cpuTimeWindow", 60_000L), // 1 min.
                    Map.entry("cpuLoadScaleUp", 70D), // %
                    Map.entry("cpuLoadScaleDown", 30D), // %
                    Map.entry("offloadLimitPerIteration", 0.5),  // [0.0-1.0]
                    //Map.entry("samplingStrategy", "random"),
                    //Map.entry("samplingStrategy", "lazy"),
                    Map.entry("samplingStrategy", "file"),
                    Map.entry("inputDir", Paths.get(ScenarioBase.RESOURCE_PATH + "AGENT_examples")),
                    Map.entry("rankingMethod", "random"),
                    Map.entry("onlyFirstOffer", "true"),
                    //Map.entry("rankingMethod", "rank_no_re"),
                    //Map.entry("rankingMethod", "rank_re_add"),
                    //Map.entry("rankingMethod", "rank_re_mul"),
                    //Map.entry("rankingMethod", "vote_wo_reliability"),
                    //Map.entry("rankingMethod", "vote_w_reliability"),
                    //Map.entry("rankingMethod", "vote_w_reliability_mul"),
                    Map.entry("swarmAgentType", "greedy"),
                    //Map.entry("swarmAgentType", "forecast"),
                    //Map.entry("swarmAgentType", "scalable_greedy"),
                    Map.entry("csvLogging", true),
                    Map.entry("predictorDir", "/home/markusa/Swarmchestrate-TSforecasting/"),
                    Map.entry("predictorModelPath", "/home/markusa/Swarmchestrate-TSforecasting/checkpoints/" +
                            "simulator1__UNC-1-Noise-Sensor-6_1min_pl128")
                    /*
                    public static final String RANKING_SCRIPT = "/home/markusa/Documents/SZTE/repos/swarm-deployment/for_simulator/call_ranking_func.py";
                    public static final String PREDICTOR_SCRIPT = "/;
                    */
            ));

    public static final Map<String, Object> APP_TYPE = DUMMY_CONFIGURATION;
    //public static final Map<String, Object> APP_TYPE = NOISE_CLASS_CONFIGURATION;
    
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