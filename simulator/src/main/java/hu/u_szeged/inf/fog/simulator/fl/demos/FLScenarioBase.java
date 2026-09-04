package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

import hu.u_szeged.inf.fog.simulator.fl.FLAggregator;
import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Common FL-demo scaffolding: devices, energy-collector wiring, byte-size constants. 
 * Mirrors the static-helper pattern of 
 * {@link hu.u_szeged.inf.fog.simulator.demo.ScenarioBase} but with focus on FL
 * scenarios so we don't drag in the workflow/IoT/provider infrastructure.
 *
 * Not instantiable — all members are static.
 */

public final class FLScenarioBase {

    private FLScenarioBase() {
        // utility class
    }

    /** Bytes in one gigabyte. */
    public static final long GB = 1024L * 1024 * 1024;
    /** Bytes in one megabyte. */
    public static final long MB = 1024L * 1024;

    /**
     * Attaches an {@link EnergyDataCollectorFL} to the aggregator's IaaS so server-side
     * energy is included in the {@code energy.csv} export.
     *
     * @param aggregator the FL aggregator
     * @param label      logical name used as the CSV column ("aggregator" is the convention)
     */
    public static void attachAggregatorEnergyCollector(FLAggregator aggregator, String label) {
        if (aggregator == null || aggregator.iaas == null) return;
        new EnergyDataCollectorFL(label, aggregator.iaas, true);
    }

    /**
     * Creates one heterogeneous {@link FLEdgeDevice} with its backing {@link PhysicalMachine},
     * {@link Repository}, mobility model and energy collector wired up.
     *
     * Moved from {@code FLHolographicUseCase} in the LOW-1 / LOW-2 cleanup so multiple
     * FL demos can share a single device factory instead of each rolling its own.
     *
     * @param name           short human-readable device label (used in logs and as the
     *                       energy-collector column suffix, e.g. {@code device-<name>})
     * @param cores          number of CPU cores
     * @param mipsPerPE      processing speed per core (instructions/tick per core)
     * @param ramBytes       installed RAM in bytes
     * @param repoName       repository name (must match a {@code <latency>} entry in the
     *                       aggregator's XML if you want explicit latency)
     * @param serverRepoId   the aggregator's repository id (latency target)
     * @param centerLoc      reference location used as the centre of the random-walk circle
     * @param rng            shared RNG (use {@code SimRandom.get()} for reproducibility)
     * @param instrPerByte   synthetic compute cost (instructions per byte of training data)
     * @param fileSize       synthetic local training data size in bytes
     * @param latency        one-way latency to the aggregator in ticks
     * @param bandwidth      uplink/downlink bandwidth in bytes/tick
     */
    public static FLEdgeDevice createFLDevice(String name,
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
        long capBytes        = 256 * GB;       // 256 GB capacity for all device repos
        long repoReadEgress  = 100_000_000L;   // ~100 MB/tick
        long repoWriteIngress= 50_000_000L;    // ~50 MB/tick
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
        double rDeg  = 0.1 * Math.sqrt(rng.nextDouble());        // 0.1 degree radius
        double theta = 2 * Math.PI * rng.nextDouble();
        double lat   = centerLoc.latitude + rDeg * Math.cos(theta);
        double lon   = centerLoc.longitude + rDeg * Math.sin(theta);

        GeoLocation location = new GeoLocation(lat, lon);
        RandomWalkMobilityStrategy mobility = new RandomWalkMobilityStrategy(
                location, 0.0027, 0.0055, 10_000);

        RandomDeviceStrategy deviceStrategy = new RandomDeviceStrategy();

        // 6. Device Definition
        long startTime = 0L;
        long stopTime  = 999_999_999L;         // Run for the whole simulation
        long freq      = 60_000;               // 60s sensing freq (not critical for FL)

        // Client DP OFF in this helper; demos that want client DP should construct
        // FLEdgeDevice directly.
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
                + ", instr/B: " + String.format("%.2e", instrPerByte)
                + ", data: " + (fileSize / MB) + "MB"
                + ", BW: " + (bandwidth / 1000) + " KB/tick)");

        return dev;
    }
}
