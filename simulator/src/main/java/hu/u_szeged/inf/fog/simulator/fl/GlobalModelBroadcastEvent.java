package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;                       // [FL-VM] NEW
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;   // [FL-VM] NEW

import java.util.concurrent.ConcurrentHashMap;


/**
 * Delivers the current global model to one device after a simulated download delay,
 * then optionally schedules local training for participating devices.
 *
 * Uses uplink compression for scheduling LocalTrainingEvent (UL sizing),
 *
 * Compute delay model:
 * The local training compute delay is estimated as:
 *
 *   compDelay = ceil(epochMultiplier * instrPerByte * fileSize / throughput)
 *
 * where {@code epochMultiplier} scales compute time only (e.g., to emulate multiple epochs).
 *
 * <b>Analytic timing + native energy — deliberate dual path.</b><br>
 * The analytic {@code compDelay} above is the <i>only</i> trigger used to schedule
 * the subsequent {@link LocalTrainingEvent}. In addition, when the aggregator's
 * native-transfer metering is enabled, {@link #tryScheduleCpuWorkViaVm} submits a
 * real CPU task to the device's VM (or falls back to {@link #tryScheduleCpuWork}
 * on the {@link PhysicalMachine}) <i>solely</i> so the simulator's energy meters
 * observe the work; the task's {@code ConsumptionEventAdapter#conComplete}
 * callback is a no-op. FL training time is therefore independent of actual VM
 * contention or scheduling state — this is a known abstraction (chosen for
 * tractability and reproducibility), not an integration bug. To rewire FL so it
 * participates in simulator contention, move the {@link LocalTrainingEvent}
 * construction inside the {@code conComplete} callback below.
 *
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

    /** @return current epoch multiplier for compute-delay estimation */
    public static double getEpochMultiplier( ){
        return epochMultiplier;
    }

    private final FLEdgeDevice  device;
    private final double[]      globalModel;
    private final int           round;
    private final FLAggregator  aggregator;
    private final int 			modelVersionAtSend;
    private final long          dlDelayAtSend;
    

    /**
     * @param delayInTicks        download delay (ticks)
     * @param device              target device
     * @param globalModel         global weights (defensively copied for delivery)
     * @param round               round index
     * @param aggregator          orchestrating aggregator
     * @param modelVersionAtSend  model version attached to the broadcast
     */
    public GlobalModelBroadcastEvent(long 		  delayInTicks,
                                     FLEdgeDevice device,
                                     double[] 	  globalModel,
                                     int 		  round,
                                     FLAggregator aggregator,
                                     int 		  modelVersionAtSend,
                                     long 		  dlDelayAtSend) {
        super(delayInTicks);
        this.device      		= device;
        this.globalModel 		= globalModel.clone();   // defensive copy
        this.round       		= round;
        this.aggregator  		= aggregator;
        this.modelVersionAtSend = modelVersionAtSend;
        this.dlDelayAtSend      = dlDelayAtSend; 
    }
    
    // =====================================================================
    // Native CPU modelling helpers
    // =====================================================================

    /**
     * Try to start a native CPU task on the device's VM. This is the preferred
     * path for FL training energy modelling.
     *
     * On success, {@code onComplete} is invoked when the VM compute task finishes.
     */
    private boolean tryScheduleCpuWorkViaVm(FLEdgeDevice dev, long instructions, Runnable onComplete) { // [FL-VM] NEW
        try {
            dev.ensureLocalVmRunning();
            VirtualMachine vm = dev.getLocalVm();
            if (vm == null) {
                return false;
            }
            ResourceConsumption rc = vm.newComputeTask(
                    (double) instructions,
                    ResourceConsumption.unlimitedProcessing,
                    new ConsumptionEventAdapter() {
                        @Override
                        public void conComplete() {
                            onComplete.run();
                        }
                    }
            );
            if (rc == null) {
                System.out.println("GlobalModelBroadcastEvent: VM refused FL compute task on device "
                        + dev.hashCode() + ".");
                return false;
            }
            return true;
        } catch (Throwable t) {
            System.out.println("GlobalModelBroadcastEvent: native VM CPU scheduling failed – " + t.getMessage());
            return false;
        }
    }

    // start a native CPU task on the device's PhysicalMachine (fallback).
    // Bound directly to {@link PhysicalMachine#compute(long, ConsumptionEventAdapter)} —
    // any future rename in DISSECT-CF will fail at compile time instead of at runtime
    // (which the old reflection-based lookup did not).
    private boolean tryScheduleCpuWork(PhysicalMachine pm, long instructions, Runnable onComplete) {
        try {
            pm.compute(instructions, new ConsumptionEventAdapter() {
                @Override public void conComplete() { onComplete.run(); }
            });
            return true;
        } catch (Throwable t) {
            System.out.println("GlobalModelBroadcastEvent: native CPU scheduling failed – " + t.getMessage());
            return false;
        }
    }
    
    // Throttle “no-path” logs to once per (srvId,devId)
    private static final ConcurrentHashMap<String, Boolean> WARNED = new ConcurrentHashMap<>();
    
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
            
            // Prefer native VM CPU task; fallback to host-level compute if VM scheduling fails.
            long instructions = (long) Math.ceil(epochMultiplier * instrPerByte * fileSize);
            boolean scheduledOnVm = tryScheduleCpuWorkViaVm(device, instructions, () -> {
                // no-op: energy accounted natively; FL timing is still analytic via compDelay
            }); // [FL-VM] CHANGED
            if (!scheduledOnVm) {
                tryScheduleCpuWork(device.getLocalMachine(), instructions, () -> {
                    // no-op: energy accounted natively; FL timing is analytic
                });
            }

            new LocalTrainingEvent(
                    compDelay,                        // analytic compute delay
                    device,
                    round,
                    aggregator,
                    aggregator.getPreUploadFailureProbability(), // Failure probabilities
                    aggregator.getInTransitFailureProbability(),
                    aggregator.getUlCompressionFactor(),
                    modelVersionAtSend,
                    dlDelayAtSend + compDelay);      // E2E so far (DL + compute)

            // Trigger native DL transfer to exercise NIC/disk energy (only if enabled)
            if (aggregator.isNativeTransferMeteringEnabled()) {
                try {
                    Repository srvRepo = aggregator.getServerRepository();
                    if (srvRepo != null) {
                        Repository devRepo = device.getLocalMachine().localDisk;
                        long bytes = aggregator.getCompressedModelBytesForDownlink();
                        String objId = "glb-r" + round + "-" + device.hashCode();
                        srvRepo.registerObject(new StorageObject(objId, bytes, false));
                        srvRepo.requestContentDelivery(objId, devRepo, new ConsumptionEventAdapter() {
                            @Override public void conComplete() {
                                try {
                                    StorageObject so = devRepo.lookup(objId);
                                    if (so != null) devRepo.deregisterObject(so);
                                } catch (Exception ignore) {}
                            }
                        });
                    }
                } catch (Exception e) {
                    String key = "DL|" + String.valueOf(aggregator.getServerRepository()) + "|" + device.getLocalMachine().localDisk;
                    if (WARNED.putIfAbsent(key, Boolean.TRUE) == null) {
                        System.out.println("GlobalModelBroadcastEvent: DL energy transfer skipped – " + e.getMessage());
                    }
                }
            }
        }
    }
}
