package hu.u_szeged.inf.fog.simulator.fl;

import hu.u_szeged.inf.fog.simulator.iot.EdgeDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.Random;

/**
 * FL-enabled edge device that can perform synthetic local training and
 * produce a model update. Extends {@link EdgeDevice} to reuse mobility,
 * scheduling and local compute context.
 *
 *   Modeled behavior 
 *     - Local training time derived from input size and throughput (see {@link GlobalModelBroadcastEvent}). 
 *     - Client-side DP: L2 clipping and Gaussian noise over a synthetic delta vector. 
 *     - Uplink payload size computed from vector length and compression factor. 
 *
 * Abstraction: This class generates synthetic deltas; it does not run ML. 
 */
public class FLEdgeDevice extends EdgeDevice {

    // Re‐declare as private fields (EdgeDevice may store them internally, but without public getters)
    private final double instructionPerByte;
    private final long fileSize;
    private final PhysicalMachine localMachine;
    private final int latency;
    private final long bandwidth;
    private final double throughput;

    // Client-side Differential Privacy (DP) parameters
    /** Gaussian σ for client-side DP noise (0 → off). */
    private final double dpSigma;

    /** L2-norm clipping bound for the model update (0 → no clipping). */
    private final double clipNorm;

    private double[] localModel;

    /**
     * Constructs an FL-capable edge device.
     *
     * @param startTime device activation time (ticks).
     * @param stopTime device deactivation time (ticks).
     * @param fileSize synthetic local data size (bytes) used in compute-delay formula.
     * @param freq sensing frequency (ms/ticks, forwarded to {@link EdgeDevice}).
     * @param mobilityStrategy mobility model for movement (affects base EdgeDevice behavior).
     * @param deviceStrategy device behavior strategy (base EdgeDevice).
     * @param localMachine attached {@link PhysicalMachine}.
     * @param instructionPerByte synthetic cost (instructions per byte) for training.
     * @param latency one-way network latency (ticks).
     * @param bandwidth throughput (bytes/tick) for comms.
     * @param throughput device compute throughput (instructions/tick).
     * @param clipNorm L2 clipping bound for client DP (0 disables).
     * @param dpSigma Gaussian σ for client DP (0 disables).
     * @param pathLogging enable/disable path logging in the base class.
     */
    public FLEdgeDevice(long startTime,
                        long stopTime,
                        long fileSize,
                        long freq,
                        MobilityStrategy mobilityStrategy,
                        DeviceStrategy deviceStrategy,
                        PhysicalMachine localMachine,
                        double instructionPerByte,
                        int latency,
                        long bandwidth,
                        double throughput,
                        double clipNorm,
                        double dpSigma,
                        boolean pathLogging) {
        super(startTime, stopTime, fileSize, freq,
              mobilityStrategy, deviceStrategy, localMachine,
              instructionPerByte, latency, pathLogging);

        this.instructionPerByte = instructionPerByte;
        this.fileSize           = fileSize;
        this.localMachine       = localMachine;
        this.latency            = latency;
        this.bandwidth          = bandwidth;
        this.throughput         = throughput;
        this.clipNorm           = clipNorm;
        this.dpSigma            = dpSigma;
    }

    /** @return instructions per byte used for compute-delay estimation. */
    public double getInstructionPerByte() {
        return instructionPerByte;
    }

    /** @return synthetic local data size (bytes). */
    public long getFileSize() {
        return fileSize;
    }

    /** @return physical machine backing this device. */
    public PhysicalMachine getLocalMachine() {
        return localMachine;
    }

    /** @return network latency (ticks). */
    public int getLatency() {
        return latency;
    }

    /** @return bandwidth (bytes/tick). */
    public long getBandwidth() {
        return bandwidth;
    }

    /** @return compute throughput (instructions/tick). */
    public double getThroughput() {
        return throughput;
    }

    /**
     * Receives a new global model (after downlink delay) and stores a local copy.
     *
     * @param model global weights delivered by the aggregator.
     */
    public void setGlobalModel(double[] model) {
        this.localModel = model.clone();
    }

    /**
     * Performs synthetic local training and returns a {@link FLModelUpdate}.
     *
     * Steps:
     * 
     *   - Create a synthetic delta vector (dimension = model length if available, else 1).
     *   - Apply L2 clipping (if {@code clipNorm > 0}).
     *   - Apply client-side Gaussian noise (if {@code dpSigma > 0}).
     *   - Compute compressed payload size for uplink.
     *
     * @param round round id for which this update is produced.
     * @param ulCompressionFactor uplink compression factor [0..1]
     * @param baseModelVersion model version the device used to train.
     * @return immutable update object (defensive copies inside).
     */
    public FLModelUpdate performLocalTraining(int round, 
    										  double ulCompressionFactor, 
    										  int baseModelVersion) {
        ulCompressionFactor = Math.max(0.0, Math.min(1.0, ulCompressionFactor));
        
        Random rng = SimRandom.get();

        // 1) Build a synthetic update delta, then apply clipping + client-side DP Gaussian noise
        if (localModel == null || localModel.length == 0) {
            System.out.println("WARN: Device " + this.hashCode()
                    + " training without a received global model; defaulting dim=1.");
        }
        int dim = (localModel == null || localModel.length == 0) ? 1 : localModel.length;

        double[] delta = new double[dim];
        for (int i = 0; i < delta.length; i++) {
            delta[i] = rng.nextDouble() * 0.05;    // synthetic local delta
        }

        // L2 Clipping
        if (clipNorm > 0.0) {
            double l2 = 0.0;
            for (double v : delta) l2 += v * v;
            l2 = Math.sqrt(l2);
            if (l2 > clipNorm) {
                double scale = clipNorm / l2;
                for (int i = 0; i < delta.length; i++) delta[i] *= scale;
            }
        }

        // Client-side Noise
        if (dpSigma > 0.0) {
            for (int i = 0; i < delta.length; i++) {
                delta[i] += rng.nextGaussian() * dpSigma;
            }
        }

        // 2)  Build the update object (payload size computed with compression)
        double[] parameters = delta.clone();
        int sampleCount = rng.nextInt(10) + 1;          // Synthetic sample size
        long rawBytes   = (long) parameters.length * Double.BYTES;
        long finalBytes = (long) Math.ceil(rawBytes * ulCompressionFactor);

        FLModelUpdate update = new FLModelUpdate(parameters, sampleCount, finalBytes, round, baseModelVersion);
        System.out.println("Device " + this.hashCode()
                + " completed training round " + round
                + " | baseModelVersion=" + baseModelVersion
                + " | clipNorm=" + clipNorm
                + ", dpSigma=" + dpSigma
                + " → update: samples=" + sampleCount
                + " | rawBytes=" + rawBytes
                + ", sentBytes=" + finalBytes);
        return update;
    }
}
