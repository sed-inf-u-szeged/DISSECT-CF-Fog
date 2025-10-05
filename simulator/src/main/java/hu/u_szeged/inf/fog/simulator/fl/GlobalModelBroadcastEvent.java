package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;

/**
 * Delivers the current global model to one device after a simulated download delay,
 * then optionally schedules local training for participating devices.
 * Uses uplink compression for scheduling LocalTrainingEvent (UL sizing),
 * Compute delay model:
 * The local training compute delay is estimated as:
 *   compDelay = ceil(epochMultiplier * instrPerByte * fileSize / throughput)
 * where {@code epochMultiplier} scales compute time only (e.g., to emulate multiple epochs).
 * Units >
 * Time: ticks; sizes: bytes.
 */
public class GlobalModelBroadcastEvent extends DeferredEvent {
    // Allows easy "one-line” tuning of compute time later.
    private static volatile double epochMultiplier = 1.0;  // One epoch by default

    /**
     * Sets the epoch multiplier used in compute-delay estimation.
     *
     * @param v multiplier (&gt; 0), clamped to a minimal positive value
     */
    public static void   setEpochMultiplier(double v){
        epochMultiplier = Math.max(0.01, v);
    }

    /**
     * @return current epoch multiplier for compute-delay estimation */
    public static double getEpochMultiplier(){
        return epochMultiplier;
    }

    private final FLEdgeDevice device;
    private final double[]     globalModel;

    private final int           round;
    private final FLAggregator  aggregator;

    private final int modelVersionAtSend;
    
    private final long         dlDelayAtSend;
    

    /**
     * @param delayInTicks        download delay (ticks)
     * @param device              target device
     * @param globalModel         global weights (defensively copied for delivery)
     * @param round               round index
     * @param aggregator          orchestrating aggregator
     * @param modelVersionAtSend  model version attached to the broadcast
     */
    public GlobalModelBroadcastEvent(long delayInTicks,
                                     FLEdgeDevice device,
                                     double[] globalModel,
                                     int round,
                                     FLAggregator aggregator,
                                     int modelVersionAtSend,
                                     long dlDelayAtSend) {
        super(delayInTicks);
        this.device      = device;
        this.globalModel = globalModel.clone();   // defensive copy
        this.round       = round;
        this.aggregator  = aggregator;
        this.modelVersionAtSend = modelVersionAtSend;
        this.dlDelayAtSend      = dlDelayAtSend; 
    }

    /** {@inheritDoc} */
    @Override
    protected void eventAction() {
        device.setGlobalModel(globalModel);
        System.out.println("GlobalModelBroadcastEvent: delivered model to device "
                + device.hashCode() + " (modelVersion=" + modelVersionAtSend + ")");

        if (aggregator.isParticipating(device)) {
            double instrPerByte = device.getInstructionPerByte();
            long   fileSize     = device.getFileSize();
            double throughput   = device.getThroughput();
            long   compDelay    = (long) Math.ceil(epochMultiplier * instrPerByte * fileSize / throughput);

            System.out.println("GlobalModelBroadcastEvent: scheduling LocalTrainingEvent "
                    + "for device " + device.hashCode()
                    + " (round " + round + ", baseModelVersion=" + modelVersionAtSend + ") with compDelay=" + compDelay);

            new LocalTrainingEvent(
                    compDelay,
                    device,
                    round,
                    aggregator,
                    aggregator.getPreUploadFailureProbability(), // Failure probabilities
                    aggregator.getInTransitFailureProbability(),
                    aggregator.getUlCompressionFactor(),
                    modelVersionAtSend,
                    dlDelayAtSend + compDelay);          // Compression (uplink)
        }
    }
}
