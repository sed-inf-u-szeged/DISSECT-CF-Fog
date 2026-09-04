package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.util.Random;

/**
 * Represents local training time on a device and schedules the upload.
 * When fired, it:
 *   - Asks the {@link FLEdgeDevice} to produce an {@link FLModelUpdate}.
 *   - Simulates a possible <b>pre-upload</b> failure (before any bytes are sent).
 *   - Computes upload delay = latency + payload/bandwidth (adding secure-agg overhead if enabled).
 *   - Schedules the {@link ParameterUploadEvent}.
 *
 * Units:
 * Time: ticks; payload sizes: bytes.
 */
public class LocalTrainingEvent extends DeferredEvent {
    private final FLEdgeDevice device;
    private final int          round;
    private final FLAggregator aggregator;
    private final double       preUploadFailureProb;
    private final double       inTransitFailureProb;
    private final double       ulCompressionFactor; // applies to uplink payload

    private final int baseModelVersion;
    
    private final long         e2eSoFarTicks;
    
    /**
     * @param delayInTicks          compute time (ticks) before local training completes
     * @param device                device performing local training
     * @param round                 round index
     * @param aggregator            destination aggregator
     * @param preUploadFailureProb  probability of pre-send loss
     * @param inTransitFailureProb  probability of in-flight loss
     * @param compressionFactor     payload compression factor [0,1]
     * @param baseModelVersion      model version the device trained on
     * @param e2eSoFarTicks         accumulated E2E ticks so far (DL + TRAIN)
     */
    public LocalTrainingEvent(long delayInTicks,
                              FLEdgeDevice device,
                              int round,
                              FLAggregator aggregator,
                              double preUploadFailureProb,
                              double inTransitFailureProb,
                              double compressionFactor,
                              int baseModelVersion,
                              long e2eSoFarTicks) {
        super(delayInTicks);
        this.device = device;
        this.round = round;
        this.aggregator = aggregator;
        this.preUploadFailureProb  = preUploadFailureProb;
        this.inTransitFailureProb  = inTransitFailureProb;
        this.ulCompressionFactor     = compressionFactor;
        this.baseModelVersion      = baseModelVersion;
        this.e2eSoFarTicks         = e2eSoFarTicks;
    }

    /** {@inheritDoc} */
    @Override
    protected void eventAction() {
        // 1) Perform the local training and produce an FLModelUpdate, including baseModelVersion
        FLModelUpdate update = device.performLocalTraining(round, ulCompressionFactor, baseModelVersion);

        // 2) Decide pre-upload failure (before any network delay is scheduled)
        Random rng = SimRandom.get();
        if (rng.nextDouble() < preUploadFailureProb) {
            System.out.println("LocalTrainingEvent: UPDATE LOST *before send* "
                    + "(p=" + preUploadFailureProb + ").");
            aggregator.notePreUploadLoss();
            return; // nothing is scheduled
        }

        /* 3) Schedule ParameterUploadEvent with network delay
         *    Compute upload delay = latency + transfer time
         *    If Secure Aggregation is ON, append ciphertext/MAC bytes before converting to time.
        */
        long modelPayloadBytes = update.getUpdateSize();                     // compressed payload bytes
        long ulBytesForDelay   = modelPayloadBytes;
        if (aggregator.isSecureAggregationEnabled()) {
        	ulBytesForDelay += aggregator.getSecureExtraBytesPerClient();
        }
        long latency    = device.getLatency();
        long bandwidth  = device.getBandwidth();
        long commDelay  = latency + (long) Math.ceil(ulBytesForDelay / (double) bandwidth);

        System.out.println("LocalTrainingEvent: scheduling upload for device "
                + device.hashCode() + " with commDelay=" + commDelay + 
                " (payload=" + modelPayloadBytes + " B, includedSecOverhead=" 
                + (aggregator.isSecureAggregationEnabled()? String.valueOf(aggregator.getSecureExtraBytesPerClient()) : "0") + " B)" );

        new ParameterUploadEvent(
        		commDelay,
                update,
                aggregator,
                inTransitFailureProb,                        // pass in-transit loss probability
                aggregator.isSecureAggregationEnabled(),
                aggregator.getSecureExtraBytesPerClient(),
                commDelay,                                    // pass delay for telemetry
                round,               // roundId
                baseModelVersion,
                e2eSoFarTicks + commDelay,				// total E2E up to UL completion
                device.getLocalMachine().localDisk );   // Source repo for native UL metering
    }
}
