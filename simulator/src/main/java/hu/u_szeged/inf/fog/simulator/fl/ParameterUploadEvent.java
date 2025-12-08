package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;  
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject; 
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;

import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Models the network transfer of a single client update to the aggregator.
 * When fired, it may drop the update due to in-transit failure; otherwise,
 * it accounts for telemetry (payload + secure overhead and delay) and delivers
 * the update to {@link FLAggregator#addModelUpdate(FLModelUpdate, int, int)}.
 */
public class ParameterUploadEvent extends DeferredEvent {
    private final FLModelUpdate update;
    private final FLAggregator  aggregator;
    private final double        runtimeFailureProbability; // in-transit loss probability
    private final boolean       secureAggregationEnabled;
    private final long          secureExtraBytes;
    private final long          commDelay;
    private final Repository    sourceRepo;  //source repo of device for UL energy

    private final int 			roundId;
    private final int 			baseModelVersion;
    private final long          totalE2E;

    /**
     * @param delayInTicks             transfer delay (ticks)
     * @param update                   model update to deliver
     * @param aggregator               destination aggregator
     * @param runtimeFailureProbability probability of loss during transfer
     * @param secureAggregationEnabled whether secure aggregation is enabled
     * @param secureExtraBytes         per-client secure-agg overhead in bytes (already included in {@code delayInTicks})
     * @param commDelay                delay used for telemetry
     * @param roundId                  round index
     * @param baseModelVersion         model version used for training
     * @param totalE2E                 total client E2E ticks (DL + TRAIN + UL) for this attempt
     */
    public ParameterUploadEvent(long delayInTicks,
                                FLModelUpdate update,
                                FLAggregator aggregator,
                                double runtimeFailureProbability,
                                boolean secureAggregationEnabled,
                                long secureExtraBytes,
                                long commDelay,
                                int roundId,
                                int baseModelVersion,
                                long totalE2E,
                                Repository sourceRepo) { //Pass device repo to meter UL natively
        super(delayInTicks);
        this.update                     = update;
        this.aggregator                 = aggregator;
        this.runtimeFailureProbability  = runtimeFailureProbability;
        this.secureAggregationEnabled   = secureAggregationEnabled;
        this.secureExtraBytes           = secureExtraBytes;
        this.commDelay                  = commDelay;
        this.sourceRepo                 = sourceRepo;
        this.roundId                    = roundId;
        this.baseModelVersion           = baseModelVersion;
        this.totalE2E                   = totalE2E;
    }
    
    // Throttle “no-path” logs
    private static final ConcurrentHashMap<String, Boolean> WARNED = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    protected void eventAction() {
        Random rng = SimRandom.get();

        // Simulate lost upload (in-transit failure)
        if (rng.nextDouble() < runtimeFailureProbability) {
            System.out.println("ParameterUploadEvent: Update lost in transit "
                    + "(p=" + runtimeFailureProbability + ").");
            aggregator.noteInTransitLoss(); // telemetry
            // Optionally still meter UL energy for failed uploads (NIC/disk),
            // without delivering a usable model to the server.
            // Still meter NIC/disk for failed ULs if requested AND metering enabled
            if (aggregator.isEnergyCountFailedUploads() && aggregator.isNativeTransferMeteringEnabled()) {
                try {
                    Repository srvRepo = aggregator.getServerRepository();
                    if (srvRepo != null && sourceRepo != null) {
                        long ulBytes = update.getUpdateSize() + (secureAggregationEnabled ? secureExtraBytes : 0L);
                        String objId = "upd-DROPPED-r" + roundId + "-" + update.hashCode();
                        sourceRepo.registerObject(new StorageObject(objId, ulBytes, false));
                        // Deliver then immediately discard to avoid affecting FL logic
                        sourceRepo.requestContentDelivery(objId, srvRepo, new ConsumptionEventAdapter() {
                            @Override public void conComplete() {
                                try {
                                    StorageObject so = srvRepo.lookup(objId);
                                    if (so != null) srvRepo.deregisterObject(so); // cleanup
                                } catch (Exception ignore) {}
                            }
                        });
                    }
                } catch (Exception e) {
                    String key = "UL-DROP|" + sourceRepo + "|" + aggregator.getServerRepository();
                    if (WARNED.putIfAbsent(key, Boolean.TRUE) == null) {
                        System.out.println("ParameterUploadEvent: (failed UL energy) transfer skipped – " + e.getMessage());
                    }
                }
            }
            return;
        }

        if (secureAggregationEnabled && secureExtraBytes > 0) {
            // Note: overhead was already included when commDelay was computed.
            System.out.println("ParameterUploadEvent: secure-agg overhead (+"
                    + secureExtraBytes + " B) was already included in commDelay.");
        }

        // Telemetry: Count UL bytes (payload + sec overhead), and delay
        long modelBytes = update.getUpdateSize();
        long secBytes   = secureAggregationEnabled ? secureExtraBytes : 0L;
        long commDelayTicks = commDelay; 
        aggregator.noteUploadSuccess(modelBytes, secBytes, commDelayTicks);
        
        // Trigger a native repository transfer to meter UL energy (only attempt native UL metering if enabled)
        if (aggregator.isNativeTransferMeteringEnabled()) {
            try {
                Repository srvRepo = aggregator.getServerRepository();
                if (srvRepo != null && sourceRepo != null) {
                    long ulBytes = modelBytes + secBytes;
                    String objId = "upd-r" + roundId + "-" + update.hashCode();
                    sourceRepo.registerObject(new StorageObject(objId, ulBytes, false));
                    sourceRepo.requestContentDelivery(objId, srvRepo, new ConsumptionEventAdapter() {
                        @Override public void conComplete() {
                            try {
                                StorageObject so = srvRepo.lookup(objId);
                                if (so != null) srvRepo.deregisterObject(so);
                            } catch (Exception ignore) {}
                        }
                    });
                }
            } catch (Exception e) {
                String key = "UL|" + sourceRepo + "|" + aggregator.getServerRepository();
                if (WARNED.putIfAbsent(key, Boolean.TRUE) == null) {
                    System.out.println("ParameterUploadEvent: UL energy transfer skipped – " + e.getMessage());
                }
            }
        }
        
              
        // Deliver with E2E included; Aggregator will count E2E only if accepted for this round
        aggregator.addModelUpdate(update, roundId, baseModelVersion, totalE2E);    // server stores or accumulates depending on mode
        
    }
}
