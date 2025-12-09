package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.fl.FLOrchestrator;
import hu.u_szeged.inf.fog.simulator.fl.FLAggregator;
import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimRandom; 
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;

import java.util.*;

/** 
 * Demo that models a minimal FL scenario on top of DISSECT-CF-Fog and runs it to completion.
 * It showcases the end-to-end FL workflow using the module’s high-level abstractions:
 * 
 *   - Set global random seed and FL knobs (timeouts, DP).
 *   - Create a single {@link FLAggregator} with an initial model.
 *   - Instantiate a pool of heterogeneous {@link FLEdgeDevice} nodes with varied
 *     compute throughput and network characteristics.
 *   - Schedule the first {@link FLOrchestrator} round (round 0), which then drives
 *     sampling, broadcast, local training, uploads, aggregation, and evaluation.
 *   - Run the discrete-event simulation until all events complete.
 * 
 * Units: This demo uses ticks for time and bytes for payload sizes.
 *
 * Scope: The FL mechanics are intentionally abstract to enable
 * large-scale trend analysis (no cycle-accurate computation or cryptography).
*/

public class FLSimulationExample {
	
	/**
     * Starts the demo FL simulation. See source for tunables.
     *
     * @param args unused.
     * @throws Exception if the underlying simulator throws during setup or execution.
     */

    public static void main(String[] args) throws Exception {
        System.out.println("Starting FL simulation...");
        // 0)  Pre-Configurations
        //     Make results reproducible
        SimRandom.setSeed(42L);
        Random rng = SimRandom.get();
        
        // --------- PARAMETERS (override with -D flags) -----------
        final int N             =  10;    // number of devices
        final int MODEL         =  3;      // |w| (weights)
        
        //     Configure the timeout ratio for early aggregation vs. stragglers
        FLAggregator.setTimeoutRatio(0.70); // wait 70% of the round interval before forcing aggregation
        
        //     Set early-aggregation rate
        final double minCompletionRate = 0.80;   // e.g., 0.80 = aggregate after 80 % of uploads
        
        //     Global Differential Privacy Settings (client-side) 
        final double clientClipNorm = 0.10;   // L2 bound
        final double clientDP_Sigma  = 0.02;  // Gaussian σ
        
        // Initialize the Global Model - tiny Gaussian-initialised weight vector        
        double[] initWeights = new double[MODEL];
        for (int i=0; i<MODEL; i++) initWeights[i] = rng.nextGaussian()*0.01;
        
        // Set up an actual server-side ComputingAppliance for the aggregator (for energy metering)
        final String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";
        final GeoLocation aggLoc = new GeoLocation(47.4979, 19.0402); // GeoLocation: Budapest
        
        // 1) Create the FL aggregator:
        // Use the aggregator constructor that attaches to an IaaS stack (Energy Model)
        FLAggregator aggregator = new FLAggregator("FL-Aggregator-1", cloudfile, aggLoc, 0, initWeights);
    	
        //Enable per-round energy fallback estimator fl_energy.csv
        //Enable/disable estimator to energy stays 100% native
        aggregator.enableEnergyFallbackEstimator(false);
        //Meter energy for in-transit failed uploads (optional)
        aggregator.setEnergyCountFailedUploads(true);
        
        // Energy meter for the aggregator (IaaS level)
        new EnergyDataCollectorFL("aggregator", aggregator.iaas, true);
        // Stop the simulator when FL process completes
        aggregator.setFinishedCallback(() -> { //Move the single energy write here so it happens exactly once
            try {
                EnergyDataCollectorFL.writeToFile(ScenarioBase.resultDirectory); // write CSVs/PNGs for energy
            } catch (Throwable t) {
                System.out.println("EnergyDataCollectorFL: failed to write results: " + t.getMessage());
            }
            System.out.println("FL finished – end-of-run callback. Exiting.");
            System.out.flush();
            System.err.flush();
            System.exit(0);
        });
        //end callback
        
        // 2) Build a list of FL-enabled edge devices:
        List<FLEdgeDevice> flDevices = new ArrayList<>();
        
        for (int i = 0; i < N; i++) {
            try {
                long startTime = 0;
                long stopTime = 1 * 60 * 60 * 1000;      // 1 hour of simulation time.
                // Heterogeneous data & workload              
                long fileSize = 50 + rng.nextInt(151);   // dummy file size (50 – 200 B)
                long freq = 30_000 + rng.nextInt(90_001);// dummy frequency (30 – 120 s)
                // Mobility
                GeoLocation location = new GeoLocation(47.0 + (i * 0.01), 19.0 + (i * 0.01));
                RandomWalkMobilityStrategy mobilityStrategy = new RandomWalkMobilityStrategy(location, 0.0027, 0.0055, 10000);
                RandomDeviceStrategy deviceStrategy = new RandomDeviceStrategy();

                // Dummy Repository & Power Transitions:
                HashMap<String, Integer> latencyMap = new HashMap<>();
                EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                        PowerTransitionGenerator.generateTransitions(2.5, 10, 1.0, 3, 3);
                Map<String, PowerState> dummyMapstT  = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
                Map<String, PowerState> dummyMapnwT  = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
                Map<String, PowerState> dummyMapcpuT = transitions.get(PowerTransitionGenerator.PowerStateKind.host);

                Repository repo = new Repository(
                        4_294_967_296L,
                        "dummy-repo-" + i,
                        3250, 3250, 3250,
                        latencyMap,
                        dummyMapstT,
                        dummyMapnwT
                );
                
                // Compute Heterogeneity 
                int cores         = 1 + rng.nextInt(4);             // 1 - 4 Cores (Physical Machine)
                double mipsPerPE  = 0.001 + rng.nextDouble()*0.004; // 0.001–0.005 instr/tick/core (Physical Machine)
                PhysicalMachine localMachine = new PhysicalMachine(cores, mipsPerPE, 2_147_483_648L, repo, 0, 0, dummyMapcpuT);
                
                // Network Heterogeneity
                long bandwidth = 50 + rng.nextInt(151); // 50-200 B/tick
                int  latency   = 30 + rng.nextInt(71);  // 30–100 ticks
                
                // Per-device compute-efficiency heterogeneity 
                double instrPerByte = 0.05 + rng.nextDouble()*0.10;  // 0.05–0.15
                double throughput = cores * mipsPerPE;               // instr/tick
                
                // Construct Device
                FLEdgeDevice flDevice = new FLEdgeDevice(
                        startTime,
                        stopTime,
                        fileSize,
                        freq,
                        mobilityStrategy,
                        deviceStrategy,
                        localMachine,
                        instrPerByte,    // instructionPerByte
                        latency,         // latency (in ticks)
                        bandwidth,       // bandwidth (bytes/tick)
                        throughput,
                        clientClipNorm,
                        clientDP_Sigma,
                        true             // pathLogging
                );
                flDevices.add(flDevice);
                // Energy meter for each device's local physical machine
                new EnergyDataCollectorFL("device-" + i, localMachine, true);
                System.out.println("Created FLEdgeDevice " + flDevice.hashCode());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 3) FL hyper-parameters
        int    desiredRounds      = 10;    // rounds 0..9
        double samplingFraction   = 1.0;   // 100% sampled each round
        double dropoutProbability = 0.5;   // 50% drop-out
        double preUploadFail      = 0.10;  // pre-send loss
        double inTransitFail      = 0.05;  // in-flight loss
        // Pacing Policy 
        final boolean fixedCadence = false; // true = start-to-start heartbeat; false = cool-down after finish [WAS TRUEE. OJOOOOO]
        // Broadcast Policy
        final boolean broadcastSelectedOnly = true; // true  = broadcast model only to selected participants (typical FL)
        											// false = broadcast model to all the participants 
        
        //    Privacy and Security Knobs
        boolean secureAgg      = true;     // enable Secure Aggregation
        long    extraBytes     = 256;      // per-client ciphertext+MAC
        double  dlCompFactor   = 0.5;      // downlink model compression factor [0..1]
        double  ulCompFactor   = 0.5;	   // uplink model compression factor [0..1]
        
        double  sigma          = 0.01;     // server-side DP Gaussian σ
        
        //    Enable fixed-k sampling (kept OFF to preserve Bernoulli sampling behavior)
        boolean useFixedKSampling = false;
        int     fixedK            = 3;
        
        // Increase round interval so most devices can finish before timeout
        final long RoundIntervalTicks = 22_000L; // was: 1000
        
        // Optional: route CSV/PNG to your results directory
        aggregator.setExportPaths(
                ScenarioBase.resultDirectory + "/fl_telemetry.csv", 
                ScenarioBase.resultDirectory + "/fl_telemetry.png"
        );
        
        // Energy telemetry outputs
        aggregator.setEnergyExportPaths(
                ScenarioBase.resultDirectory + "/fl_energy.csv",
                ScenarioBase.resultDirectory + "/fl_energy.png"
        );
        
        // 4) Create and schedule the first FLOrchestrator (round 0):
        new FLOrchestrator(RoundIntervalTicks, 
        		desiredRounds,
                flDevices,
                aggregator,
                samplingFraction,
                dropoutProbability,
                preUploadFail,
                inTransitFail,
                minCompletionRate,
                secureAgg,
                extraBytes,
                dlCompFactor,
                ulCompFactor,
                sigma,
                fixedCadence,
                broadcastSelectedOnly,
                useFixedKSampling,
                fixedK);

        // 5) Run the simulation until no events remain:
        Timed.simulateUntilLastEvent();
        System.out.println("FL simulation finished.");
    }
}