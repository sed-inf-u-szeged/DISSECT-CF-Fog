package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.fl.FLAggregator;
import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.fl.FLOrchestrator;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimRandom; 
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 
 * Demo that models a minimal FL scenario on top of DISSECT-CF-Fog and runs it to completion.
 * It showcases the end-to-end FL workflow using the module’s high-level abstractions:
 *   - Set global random seed and FL knobs (timeouts, DP).
 *   - Create a single {@link FLAggregator} with an initial model.
 *   - Instantiate a pool of heterogeneous {@link FLEdgeDevice} nodes with varied
 *     compute throughput and network characteristics.
 *   - Schedule the first {@link FLOrchestrator} round (round 0), which then drives
 *     sampling, broadcast, local training, uploads, aggregation, and evaluation.
 *   - Run the discrete-event simulation until all events complete.
 * Units: This demo uses ticks for time and bytes for payload sizes.
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
        
        //     Configure the timeout ratio for early aggregation vs. stragglers
        FLAggregator.setTimeoutRatio(0.60); // wait 60% of the round interval before forcing aggregation
        
        //     Set early-aggregation rate
        final double minCompletionRate = 0.80;   // e.g., 0.80 = aggregate after 80 % of uploads
        
        //     Global Differential Privacy Settings (client-side) 
        final double clientClipNorm = 0.10;   // L2 bound
        final double clientDP_Sigma  = 0.02;  // Gaussian σ
        
        // Initialize the Global Model - tiny Gaussian-initialised weight vector
        double[] initWeights = {
                rng.nextGaussian() * 0.01,
                rng.nextGaussian() * 0.01,
                rng.nextGaussian() * 0.01
        };
        
        // 1) Create the FL aggregator:
        FLAggregator aggregator = new FLAggregator("FL-Aggregator-1", initWeights);

        // 2) Build a list of FL-enabled edge devices:
        List<FLEdgeDevice> flDevices = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            try {
                long startTime = 0;
                long stopTime = 1 * 60 * 60 * 1000;      // 1 hour of simulation time.
                // Heterogeneous data & workload              
                long fileSize = 50 + rng.nextInt(151);   // dummy file size (50 – 200 B)
                long freq = 30_000 + rng.nextInt(90_001); // dummy frequency (30 – 120 s)
                // Mobility
                GeoLocation location = new GeoLocation(47.0 + (i * 0.01), 19.0 + (i * 0.01));
                RandomWalkMobilityStrategy mobilityStrategy = new RandomWalkMobilityStrategy(location, 0.0027, 0.0055, 10000);
                RandomDeviceStrategy deviceStrategy = new RandomDeviceStrategy();

                // Dummy Repository & Power Transitions:
                HashMap<String, Integer> latencyMap = new HashMap<>();
                EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                        PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
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
                System.out.println("Created FLEdgeDevice " + flDevice.hashCode());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 3) FL hyper-parameters
        int    desiredRounds      = 10;    // rounds 0..9
        double samplingFraction   = 1.0;   // 100% sampled each round
        double dropoutProbability = 0.5;   // 0% drop-out
        double preUploadFail      = 0.10;  // pre-send loss
        double inTransitFail      = 0.05;  // in-flight loss
        // Pacing Policy 
        final boolean fixedCadence = true; // true = start-to-start heartbeat; false = cool-down after finish
        // Broadcast Policy
        final boolean broadcastSelectedOnly = true; // true  = broadcast model only to selected participants (typical FL)
        // false = broadcast model to all the participants 
        
        //    Privacy and Security Knobs
        boolean secureAgg      = true;     // enable Secure Aggregation
        long    extraBytes     = 256;      // per-client ciphertext+MAC
        double  dlCompFactor   = 0.5;      // downlink model compression factor [0..1]
        double  ulCompFactor   = 0.5;      // uplink model compression factor [0..1]
        
        double  sigma          = 0.01;     // server-side DP Gaussian σ
        
        //    Enable fixed-k sampling (kept OFF to preserve Bernoulli sampling behavior)
        boolean useFixedKSampling = false;
        int     fixedK            = 3;
        
        // 4) Create and schedule the first FLOrchestrator (round 0):
        new FLOrchestrator(1000, 
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