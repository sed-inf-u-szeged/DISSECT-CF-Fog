package hu.u_szeged.inf.fog.simulator.fl.gossip;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipNode.ReceivedModel;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Models one <b>directional</b> peer-exchange transfer {@code src → dst} of a
 * gossip round (§8.1, Alg. 1 line 4): the sender's model travels to the
 * receiver, who places it in its inbox for the merge step. A mutual exchange
 * between two nodes is two such events (see {@link #planDirectedTransfers}).
 *
 * <p>Cloned from {@link hu.u_szeged.inf.fog.simulator.fl.ParameterUploadEvent}:
 * a {@link DeferredEvent} that fires after the analytic delay
 * {@code latencyTicks + ceil(payloadBytes / bandwidth)}, may drop the model with
 * an in-transit failure probability, and otherwise delivers it and books
 * telemetry.</p>
 *
 * <h2>Both-endpoint energy metering (§8.1; Risk R1)</h2>
 * On fire, a native DISSECT-CF transfer is initiated from the source repository
 * to the destination repository ({@code requestContentDelivery}). A single such
 * transfer consumes the source's network-out and the destination's network-in
 * bandwidth spreaders, both of which are covered by the per-PM
 * {@code EnergyDataCollectorFL} meter — so sender TX and receiver RX energy are
 * captured at <b>both endpoints, exactly once each</b>, with no double-counting
 * (there is one transfer, not two mirrored halves). The analytic FL timing
 * above is independent of this native transfer's own duration; the native
 * transfer exists only so the energy meters observe the activity, mirroring the
 * uplink path of {@code ParameterUploadEvent}.
 */
public final class FLPeerExchangeEvent extends DeferredEvent {

    /** Receives arrival / failure notifications and books telemetry. */
    public interface ExchangeListener {
        /** Called when the model is delivered to {@code dst}'s inbox. */
        void onArrived(FLPeerExchangeEvent ev);

        /** Called when the transfer is lost in transit. */
        void onFailed(FLPeerExchangeEvent ev);
    }

    private static final AtomicLong SEQ = new AtomicLong();
    private static final ConcurrentHashMap<String, Boolean> WARNED = new ConcurrentHashMap<>();

    private final int round;
    private final FLGossipNode src;
    private final FLGossipNode dst;
    private final float[] payloadSignature;
    private final int payloadSampleCount;
    private final long payloadBytes;
    private final long latencyTicks;
    private final double failureProb;
    private final boolean nativeMeteringEnabled;
    private final ExchangeListener listener;

    /**
     * Schedules one directional model transfer that fires after the analytic
     * delay (clamped to at least one tick).
     *
     * @param delayInTicks analytic FL delay (typically
     *                     {@code latencyTicks + ceil(payloadBytes / bandwidth)}).
     * @param round gossip round.
     * @param src sending node (its repository is the transfer source).
     * @param dst receiving node (its repository is the transfer target; the
     *            model is delivered to its inbox).
     * @param payloadSignature the sender's model signature to deliver (copied).
     * @param payloadSampleCount the sender's local sample count {@code n_src}.
     * @param payloadBytes model payload size in bytes (drives energy/traffic).
     * @param latencyTicks link latency component (telemetry).
     * @param failureProb in-transit loss probability.
     * @param nativeMeteringEnabled whether to fire the native energy-metering transfer.
     * @param listener arrival/failure sink (the orchestrator).
     */
    public FLPeerExchangeEvent(long delayInTicks, int round, FLGossipNode src, FLGossipNode dst,
                               float[] payloadSignature, int payloadSampleCount, long payloadBytes,
                               long latencyTicks, double failureProb,
                               boolean nativeMeteringEnabled, ExchangeListener listener) {
        // NB: DeferredEvent fires eventAction() synchronously inside super(...) when
        // delay <= 0 — before the subclass fields below are assigned, which would NPE.
        // A peer exchange always incurs at least one tick of latency, so clamp to >= 1.
        super(Math.max(1L, delayInTicks));
        this.round = round;
        this.src = src;
        this.dst = dst;
        this.payloadSignature = payloadSignature.clone();
        this.payloadSampleCount = payloadSampleCount;
        this.payloadBytes = payloadBytes;
        this.latencyTicks = latencyTicks;
        this.failureProb = failureProb;
        this.nativeMeteringEnabled = nativeMeteringEnabled;
        this.listener = listener;
    }

    public int round() {
        return round;
    }

    public int srcNode() {
        return src.nodeId();
    }

    public int dstNode() {
        return dst.nodeId();
    }

    public long payloadBytes() {
        return payloadBytes;
    }

    public long latencyTicks() {
        return latencyTicks;
    }

    @Override
    protected void eventAction() {
        // In-transit failure uses the legacy shared stream (failures are a
        // pre-existing stochastic concern, like ParameterUploadEvent).
        Random rng = SimRandom.get();
        if (failureProb > 0.0 && rng.nextDouble() < failureProb) {
            if (listener != null) {
                listener.onFailed(this);
            }
            return;
        }

        // Both-endpoint energy metering via a single native repo->repo transfer.
        if (nativeMeteringEnabled) {
            meterTransfer();
        }

        // Deliver the sender's model to the receiver's inbox + telemetry.
        dst.deliver(new ReceivedModel(src.nodeId(), payloadSignature, payloadSampleCount, payloadBytes));
        if (listener != null) {
            listener.onArrived(this);
        }
    }

    /**
     * Fires a single native {@code srcRepo → dstRepo} content delivery sized at
     * {@link #payloadBytes}, so both endpoints' energy meters observe the
     * transfer once. The transferred object is deregistered on completion so it
     * does not accumulate in the destination repository.
     */
    private void meterTransfer() {
        Repository srcRepo = src.repository();
        Repository dstRepo = dst.repository();
        if (srcRepo == null || dstRepo == null) {
            return;
        }
        try {
            final String objId = "gx-r" + round + "-" + src.nodeId() + "to" + dst.nodeId()
                    + "-" + SEQ.incrementAndGet();
            srcRepo.registerObject(new StorageObject(objId, payloadBytes, false));
            srcRepo.requestContentDelivery(objId, dstRepo, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    cleanup(srcRepo, dstRepo, objId);
                }
            });
        } catch (Exception e) {
            String key = "GX|" + src.nodeId() + "|" + dst.nodeId();
            if (WARNED.putIfAbsent(key, Boolean.TRUE) == null) {
                System.out.println("FLPeerExchangeEvent: peer-exchange energy transfer skipped – " + e.getMessage());
            }
        }
    }

    private static void cleanup(Repository srcRepo, Repository dstRepo, String objId) {
        try {
            StorageObject atDst = dstRepo.lookup(objId);
            if (atDst != null) {
                dstRepo.deregisterObject(atDst);
            }
            StorageObject atSrc = srcRepo.lookup(objId);
            if (atSrc != null) {
                srcRepo.deregisterObject(atSrc);
            }
        } catch (Exception ignore) {
            // best-effort cleanup; never affects FL logic
        }
    }

    /**
     * Plans the de-duplicated set of directed transfers for one round from the
     * per-node peer sets. A logical exchange between {@code i} and {@code j} is a
     * mutual model swap = two directed transfers {@code [i,j]} and {@code [j,i]};
     * if both {@code i} selected {@code j} and {@code j} selected {@code i}, the
     * pair is still scheduled <b>once</b> (one pair of transfers, not two), so no
     * exchange — and no energy — is double-counted.
     *
     * @param peerSets node id → its selected peer ids for this round.
     * @return directed transfers as {@code [src, dst]}, each unordered exchanging
     *         pair contributing exactly the two directions.
     */
    public static List<int[]> planDirectedTransfers(Map<Integer, List<Integer>> peerSets) {
        // Collect unordered exchanging pairs {a<b} where a selected b or b selected a.
        java.util.TreeSet<Long> pairs = new java.util.TreeSet<>();
        for (Map.Entry<Integer, List<Integer>> e : peerSets.entrySet()) {
            int i = e.getKey();
            for (int j : e.getValue()) {
                int a = Math.min(i, j);
                int b = Math.max(i, j);
                pairs.add(((long) a << 32) | (b & 0xFFFFFFFFL));
            }
        }
        List<int[]> transfers = new ArrayList<>(pairs.size() * 2);
        for (long key : pairs) {
            int a = (int) (key >> 32);
            int b = (int) (key & 0xFFFFFFFFL);
            transfers.add(new int[] { a, b });
            transfers.add(new int[] { b, a });
        }
        return transfers;
    }
}
