package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.fl.*;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.*;

/**
 * A real-world Federated Learning use case simulation for "Holographic-Type Communications"
 * based on the infrastructure (AI Cluster, Extreme Edge).
 *
 * This scenario models:
 * 1.  A central Aggregator: Representing the "cloud1/master" (A100 GPU) from the AI Cluster.
 * 2.  10 Heterogeneous Client Devices: Representing the 5 workers from the "AI Cluster"
 * (with/without GPUs) and the 5 workers from the "Extreme Edge" cluster (RPi 5, Jetson).
 *
 * Heterogeneity is modeled in:
 * -   Compute: Cores, MIPS (throughput), and compute cost (instrPerByte). Nodes with
 * GPUs (A40, P2000, Jetson) are given higher throughput and lower compute cost.
 * -   Network: Varied latency and bandwidth for each device.
 * -   Data: Varied local dataset sizes (fileSize).
 * 
 * -- SCENARIO S2: POLICIES ----
 */
public class FLHolographicUseCase_S2 {
	// -- SCENARIO S2: POLICIES
    // Define geographic location (e.g., Bristol, UK)
    private static final double SCENARIO_LAT = 51.4545;
    private static final double SCENARIO_LON = -2.5879;
    
    // Helper constant for RAM definition
    private static final long GB = 1024L * 1024 * 1024;
    // Helper constant for Data definition
    private static final long MB = 1024L * 1024;

    public static void main(String[] args) throws Exception {
        // ---------- 0) Simulation Parameters ----------
        
        // FL Round Parameters
        final int    ROUNDS           = 20;        // Number of FL rounds
        final long   ROUND_INTERVAL   = 60_000L;   // 60 seconds per round
        final long   SEED             = 123;       // RNG seed for reproducibility
        
        // Model Parameters (8MB model)
        final int    MODEL_DIMENSION_W = 1_000_000; 

        // Client Selection & Failure Parameters
        final double SAMPLING_FRACTION    = 0.5;   // Select 50% of clients each round
        final double DROPOUT_PROBABILITY  = 0.1;   // 10% of *selected* clients drop out
        final double PRE_UPLOAD_FAIL      = 0.0;   // 0% pre-send loss
        final double IN_TRANSIT_FAIL      = 0.05;  // 5% in-flight loss
        
        // *** POLICIES CHANGES FOR SCENARIO S2 ***
        final double TIMEOUT_RATIO    = 0.50;      // Aggressive timeout (30s)
        final double MIN_COMPLETION_RATE  = 1.0;  //  Strict: Wait for 100% of participants arrive

        // Resource file for the Aggregator's IaaS
        final String cloudfile = ScenarioBase.resourcePath + "LPDS_FL_Holographic.xml";
        
        // Unique ID for the Aggregator (must match the repo name in the XML)
        // XML repo name: "ceph-Holographic-Aggregator"
        final String AGGREGATOR_ID = "Holographic-Aggregator";
        
        // Define the expected repo ID to match the XML exactly.
        System.setProperty("fl.serverRepoId", "ceph-Holographic-Aggregator");

        // Tag for exported files
        String tag = String.format("HolographicUseCase_S2_R%d_W%d_seed%d", ROUNDS, MODEL_DIMENSION_W, SEED);
        
        System.out.println("[HolographicUseCase_S2] Starting Policy run with "
                + "Rounds=" + ROUNDS
                + ", |w|=" + MODEL_DIMENSION_W
                + ", seed=" + SEED
                + ", roundInterval=" + ROUND_INTERVAL
                + ", sampling=" + SAMPLING_FRACTION);

        // ---------- 1) Setup Simulation Environment ----------
        SimRandom.setSeed(SEED);
        Random rng = SimRandom.get();
        FLAggregator.setTimeoutRatio(TIMEOUT_RATIO);
        
        // Set epoch multiplier (from GlobalModelBroadcastEvent)
        GlobalModelBroadcastEvent.setEpochMultiplier(2.0); 

        // ---------- 2) Initial global weights ----------
        double[] initWeights = new double[MODEL_DIMENSION_W];
        for (int i = 0; i < initWeights.length; i++) {
            initWeights[i] = rng.nextGaussian() * 0.01;
        }

        // ---------- 3) Create Aggregator (Cloud Master) ----------
        GeoLocation aggLoc = new GeoLocation(SCENARIO_LAT, SCENARIO_LON);
        FLAggregator aggregator = new FLAggregator(AGGREGATOR_ID, cloudfile, aggLoc, 0, initWeights);
        
        // The aggregator's repo ID match the one in the XML for wiring
        final String SERVER_REPO_ID = aggregator.getServerRepositoryId();
        System.out.println("[HolographicUseCase] Aggregator created, server repo ID: " + SERVER_REPO_ID);

        // Configure energy metering and CSV/PNG export paths
        aggregator.enableEnergyFallbackEstimator(false);
        aggregator.setNativeTransferMeteringEnabled(true);
        new EnergyDataCollectorFL("aggregator", aggregator.iaas, true);

        aggregator.setExportPaths(
                ScenarioBase.resultDirectory + "/fl_telemetry_S2__" + tag + ".csv",
                ScenarioBase.resultDirectory + "/fl_telemetry_S2__" + tag + ".png"
        );
        aggregator.setEnergyExportPaths(
                ScenarioBase.resultDirectory + "/fl_energy_S2__" + tag + ".csv",
                ScenarioBase.resultDirectory + "/fl_energy_S2__" + tag + ".png"
        );

        // Setup shutdown hook
        aggregator.setFinishedCallback(() -> {
            try {
                EnergyDataCollectorFL.writeToFile(ScenarioBase.resultDirectory);
            } catch (Throwable t) {
                System.out.println("EnergyDataCollectorFL write failed: " + t.getMessage());
            }
            System.out.println("[HolographicUseCase_S2] Finished run " + tag + ". Exiting.");
            System.out.flush(); System.err.flush();
            System.exit(0);
        });

        // ---------- 4) Create Heterogeneous FL Devices (Workers) ----------
        List<FLEdgeDevice> flDevices = new ArrayList<>();
        
        // We will create devices programmatically, matching the specs from the scenario.
        // The device repo names match the <latency> tags in LPDS_FL_Holographic.xml

        // --- AI Cluster Devices ---
        final double AI_CLUSTER_CORE_MIPS = 0.003; 
        
        // Realistic bandwidth: 50,000-150,000 B/tick (50-150 MB/s)
        // Realistic compute cost: 1e-7 to 5e-6 instr/Byte
        
        // worker1: 8 core, 16GB RAM, no GPU
        flDevices.add(createFLDevice(
                "worker1-cloud", 8, AI_CLUSTER_CORE_MIPS, 16 * GB,
                "repo-worker-cloud-1", SERVER_REPO_ID, aggLoc, rng,
                5.0e-6,  // instrPerByte (standard cost)
                (50 + rng.nextInt(51)) * MB, // fileSize (50-100MB)
                10, 50000 + rng.nextInt(50001) // latency (10ms), bandwidth (50-100 MB/s)
        ));
        
        // worker2: 15 core, 30GB RAM, Quadro P2000
        flDevices.add(createFLDevice(
                "worker2-cloud-p2000", 15, AI_CLUSTER_CORE_MIPS * 2.0, 30 * GB, // Double MIPS for GPU
                "repo-worker-cloud-2", SERVER_REPO_ID, aggLoc, rng,
                1.0e-6,  // instrPerByte (low cost due to GPU)
                (100 + rng.nextInt(101)) * MB, // fileSize (100-200MB)
                12, 100000 + rng.nextInt(50001) // latency (12ms), bandwidth (100-150 MB/s)
        ));

        // worker3: 15 core, 30GB RAM, Quadro P2000
        flDevices.add(createFLDevice(
                "worker3-cloud-p2000", 15, AI_CLUSTER_CORE_MIPS * 2.0, 30 * GB, // Double MIPS for GPU
                "repo-worker-cloud-3", SERVER_REPO_ID, aggLoc, rng,
                1.0e-6,  // instrPerByte (low cost due to GPU)
                (100 + rng.nextInt(101)) * MB, // fileSize (100-200MB)
                11, 100000 + rng.nextInt(50001) // latency (11ms), bandwidth (100-150 MB/s)
        ));

        // worker4: 8 core, 16GB RAM, no GPU
        flDevices.add(createFLDevice(
                "worker4-cloud", 8, AI_CLUSTER_CORE_MIPS, 16 * GB,
                "repo-worker-cloud-4", SERVER_REPO_ID, aggLoc, rng,
                5.0e-6,  // instrPerByte (standard cost)
                (50 + rng.nextInt(51)) * MB, // fileSize (50-100MB)
                10, 50000 + rng.nextInt(50001) // latency (10ms), bandwidth (50-100 MB/s)
        ));

        // worker5: 12 core, 16GB RAM, A40
        flDevices.add(createFLDevice(
                "worker5-cloud-a40", 12, AI_CLUSTER_CORE_MIPS * 3.0, 16 * GB, // Triple MIPS for A40
                "repo-worker-cloud-5", SERVER_REPO_ID, aggLoc, rng,
                5.0e-7,  // instrPerByte (very low cost due to A40, 0.5e-6)
                (150 + rng.nextInt(101)) * MB, // fileSize (150-250MB)
                8, 150000 + rng.nextInt(50001) // latency (8ms), bandwidth (150-200 MB/s)
        ));

        // --- Extreme Edge Devices ---
        final double RPI_CORE_MIPS = 0.001; 
        final double JETSON_CORE_MIPS = 0.0015; // Jetson CPU core is decent
        
        // worker-rpi-1 (4 core @ 2.4Ghz, 8GB RAM)
        flDevices.add(createFLDevice(
                "worker-rpi-1", 4, RPI_CORE_MIPS, 8 * GB,
                "repo-worker-rpi-1", SERVER_REPO_ID, aggLoc, rng,
                8.0e-6,  // instrPerByte (high cost, slow CPU)
                (20 + rng.nextInt(11)) * MB, // fileSize (20-30MB)
                25, 20000 + rng.nextInt(30001) // latency (25ms), bandwidth (20-50 MB/s)
        ));

        // worker-rpi-2 (4 core @ 2.4Ghz, 8GB RAM)
        flDevices.add(createFLDevice(
                "worker-rpi-2", 4, RPI_CORE_MIPS, 8 * GB,
                "repo-worker-rpi-2", SERVER_REPO_ID, aggLoc, rng,
                8.0e-6,  // instrPerByte (high cost, slow CPU)
                (20 + rng.nextInt(11)) * MB, // fileSize (20-30MB)
                28, 20000 + rng.nextInt(30001) // latency (28ms), bandwidth (20-50 MB/s)
        ));
        
        // worker-rpi-3 (4 core @ 2.4Ghz, 8GB RAM)
        flDevices.add(createFLDevice(
                "worker-rpi-3", 4, RPI_CORE_MIPS, 8 * GB,
                "repo-worker-rpi-3", SERVER_REPO_ID, aggLoc, rng,
                8.0e-6,  // instrPerByte (high cost, slow CPU)
                (20 + rng.nextInt(11)) * MB, // fileSize (20-30MB)
                26, 20000 + rng.nextInt(30001) // latency (26ms), bandwidth (20-50 MB/s)
        ));

        // worker-rpi-4 (4 core @ 2.4Ghz, 8GB RAM)
        flDevices.add(createFLDevice(
                "worker-rpi-4", 4, RPI_CORE_MIPS, 8 * GB,
                "repo-worker-rpi-4", SERVER_REPO_ID, aggLoc, rng,
                8.0e-6,  // instrPerByte (high cost, slow CPU)
                (20 + rng.nextInt(11)) * MB, // fileSize (20-30MB)
                30, 20000 + rng.nextInt(30001) // latency (30ms), bandwidth (20-50 MB/s)
        ));

        // worker-jetson (6 core @ 1.5Ghz, 8GB RAM, Ampere-1024)
        flDevices.add(createFLDevice(
                "worker-jetson", 6, JETSON_CORE_MIPS * 2.5, 8 * GB, // CPU + GPU boost
                "repo-worker-jetson", SERVER_REPO_ID, aggLoc, rng,
                3.0e-6,  // instrPerByte (medium-low cost due to GPU)
                (40 + rng.nextInt(21)) * MB, // fileSize (40-60MB)
                22, 40000 + rng.nextInt(30001) // latency (22ms), bandwidth (40-70 MB/s)
        ));
        
        System.out.println("[HolographicUseCase] Created " + flDevices.size() + " heterogeneous devices.");

        // ---------- 5) FL hyper-parameters for this Use Case ----------
        boolean fixedCadence = false; // 'false' = Cooldown_After_Finish
        boolean broadcastSelectedOnly = true;

        // Privacy/Compression (OFF for this baseline)
        boolean secureAgg    = false;
        long    extraBytes   = 0L;
        double  dlCompFactor = 1.0;
        double  ulCompFactor = 1.0;
        double  serverDP     = 0.0;

        // ---------- 6) Kick off round 0 ----------
        System.out.println("[HolographicUseCase_S2] Starting FLOrchestrator for " + ROUNDS + " rounds...");
        new FLOrchestrator(
                ROUND_INTERVAL,
                ROUNDS,
                flDevices,
                aggregator,
                SAMPLING_FRACTION,
                DROPOUT_PROBABILITY,
                PRE_UPLOAD_FAIL,
                IN_TRANSIT_FAIL,
                MIN_COMPLETION_RATE,
                secureAgg,
                extraBytes,
                dlCompFactor,
                ulCompFactor,
                serverDP,
                fixedCadence,
                broadcastSelectedOnly,
                false,  // useFixedKSampling
                0       // fixedK
        );

        // ---------- 7) Run simulation ----------
        Timed.simulateUntilLastEvent();
        System.out.println("[HolographicUseCase_S2] Simulation finished for " + tag);
    }
    
    /**
     * Helper function to create a single heterogeneous FLEdgeDevice.
     * This encapsulates the creation of the PM, Repo, Mobility, and Energy Collector.
     */
    private static FLEdgeDevice createFLDevice(String name,
                                               int cores,
                                               double mipsPerPE,
                                               long ramBytes,
                                               String repoName,
                                               String serverRepoId,
                                               GeoLocation centerLoc,
                                               Random rng,
                                               double instrPerByte,
                                               long fileSize,
                                               int latency,
                                               long bandwidth) throws Exception {

        // 1. Network: Define latency from this device's repo back to the server repo
        HashMap<String, Integer> latencyMap = new HashMap<>();
        latencyMap.put(serverRepoId, Math.max(1, latency));

        // 2. Power: Generate standard power transitions
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(2.5, 10, 1.0, 3, 3);
        Map<String, PowerState> diskT = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        Map<String, PowerState> netT  = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
        Map<String, PowerState> cpuT  = transitions.get(PowerTransitionGenerator.PowerStateKind.host);

        // 3. Storage: Create the device's local repository
        long capBytes = 256 * GB; // 256 GB capacity for all device repos
        long repoReadEgress  = 100_000_000L; // ~100 MB/tick
        long repoWriteIngress= 50_000_000L;  // ~50 MB/tick
        long repoNetCap      = 100_000_000L;
        
        Repository repo = new Repository(
                capBytes, repoName,
                repoReadEgress, repoWriteIngress, repoNetCap,
                latencyMap, diskT, netT
        );

        // 4. Compute: Create the Physical Machine for the device
        PhysicalMachine pm = new PhysicalMachine(
                cores, mipsPerPE, ramBytes, repo, 0, 0, cpuT);

        // 5. Mobility: Random walk within a small radius of the aggregator
        double rDeg  = 0.1 * Math.sqrt(rng.nextDouble()); // 0.1 degree radius
        double theta = 2 * Math.PI * rng.nextDouble();
        double lat   = centerLoc.latitude + rDeg * Math.cos(theta);
        double lon   = centerLoc.longitude + rDeg * Math.sin(theta);
        
        GeoLocation location = new GeoLocation(lat, lon);
        RandomWalkMobilityStrategy mobility = new RandomWalkMobilityStrategy(
                location, 0.0027, 0.0055, 10_000);
        
        RandomDeviceStrategy deviceStrategy = new RandomDeviceStrategy();
        
        // 6. Device Definition
        long startTime = 0L;
        long stopTime  = 999_999_999L; // Run for the whole simulation
        long freq      = 60_000;       // 60s sensing freq (not critical for FL)

        // Client DP OFF for this use case
        double clientClipNorm = 0.0;
        double clientDP_Sigma = 0.0;
        
        double throughput = cores * mipsPerPE; // Total instructions/tick

        FLEdgeDevice dev = new FLEdgeDevice(
                startTime, stopTime, fileSize, freq,
                mobility, deviceStrategy, pm,
                instrPerByte, latency, bandwidth, throughput,
                clientClipNorm, clientDP_Sigma,
                true // pathLogging
        );
        
        // 7. Energy: Attach an energy collector to this device's PM
        new EnergyDataCollectorFL("device-" + name, pm, true);
        
        System.out.println("  [Device] Created: " + name 
                + " (Cores: " + cores 
                + ", MIPS/core: " + String.format("%.4f", mipsPerPE)
                + ", RAM: " + (ramBytes / GB) + "GB"
                + ", instr/B: " + String.format("%.2e", instrPerByte) // Scientific notation for small numbers
                + ", data: " + (fileSize / MB) + "MB"
                + ", BW: " + (bandwidth / 1000) + " KB/tick)"); // Show KB/tick
                
        return dev;
    }
}