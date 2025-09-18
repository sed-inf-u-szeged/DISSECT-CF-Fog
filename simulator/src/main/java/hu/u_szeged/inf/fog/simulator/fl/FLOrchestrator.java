package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Drives the per-round control flow of a Federated Learning experiment.
 *
 *  Responsibilities
 *     - Sampling &amp; dropout: chooses participating clients (Bernoulli or fixed-{@code k})
 *       and optionally simulates per-round dropout.
 *     - Round setup: informs {@link FLAggregator} about expectations and policy knobs. 
 *     - Broadcast: triggers server-&gt;client model broadcast at round start.
 *     - Pacing: in "fixed-cadence" mode schedules the next round immediately
 *       (start-to-start). Otherwise the aggregator schedules after aggregation/timeout.
 * Time unit: ticks (discrete-event time). 
 *
 * Abstraction note: This class does not simulate control-plane protocols; it
 * only schedules high-level events consistent with DISSECT-CF-Fog’s design.
 */

public class FLOrchestrator extends DeferredEvent {
    /** Shared RNG for sampling/dropout. Reproducible via {@link SimRandom#setSeed(long)}. */
    private final Random rng = SimRandom.get();

    private final int currentRound;              // index of this round (0-based)
    private final long roundInterval;            // ticks between round starts (heartbeat) OR cool-down, depending on policy
    private final List<FLEdgeDevice> devices;    // full devices pool
    private final FLAggregator aggregator;
    private final int maxRounds;
    
    private final boolean fixedCadence;          // true = start-to-start heartbeat; false = cool-down after finish
    private final boolean broadcastSelectedOnly; // true = broadcast only to participants
    
    private final double samplingFraction;       // probability that a device is selected
    private final double dropoutProbability;     // probability that a selected device drops out
    // Failures probabilities:
    private final double  preUploadFailureProb;  // happens before bytes leave
    private final double  inTransitFailureProb;  // happens during upload
    
    private final double minCompletionRate;      // early aggregation threshold (e.g. 0.80 → 80 %)
    // Privacy & Security
    private final boolean secureAggregationEnabled;           
    private final long    secureExtraBytesPerClient;          
    private final double  dlCompressionFactor;   // downlink compression model factor
    private final double  ulCompressionFactor;   // uplink compression model factor               
    private final double  dpNoiseStd;
    
    // Configurable selection strategy controls
    private final boolean useFixedKSampling; // if true, pick exactly fixedK devices before dropout
    private final int     fixedK;            // target number of sampled devices (pre-dropout)
      
    /**
     * Constructs an orchestrator that starts from round 0. See the main constructor for param docs.
     *
     * @param roundInterval ticks between round starts (heartbeat) or cool-down duration.
     * @param maxRounds total number of rounds to run (round indices are 0..maxRounds-1).
     * @param devices candidate pool of FL-enabled devices.
     * @param aggregator central FL aggregator.
     * @param samplingFraction Bernoulli sampling probability per device in each round.
     * @param dropoutProbability dropout probability for a sampled device (independent).
     * @param preUploadFailureProb probability that a local update is lost before any bytes are sent.
     * @param inTransitFailureProb probability that an upload is lost during transfer.
     * @param minCompletionRate early-aggregation threshold as fraction of expected updates [0..1].
     * @param secureAggregationEnabled if true, model secure-agg as uplink overhead + weighted accumulation.
     * @param secureExtraBytesPerClient overhead bytes per client when secure-agg is enabled.
     * @param compressionFactor compression applied to model payloads [0..1].
     * @param dpNoiseStd stddev of server-side DP noise added to the aggregated delta.
     * @param fixedCadence true for start-to-start rounds; false for cool-down scheduling.
     * @param broadcastSelectedOnly true to broadcast only to participants; false to broadcast to all devices.
     */
    
    public FLOrchestrator(long   roundInterval,
        int    maxRounds,
        List<FLEdgeDevice> devices,
        FLAggregator       aggregator,
        double  samplingFraction,
        double  dropoutProbability,
        double  preUploadFailureProb,
        double  inTransitFailureProb,
        double  minCompletionRate,
        boolean secureAggregationEnabled,
        long    secureExtraBytesPerClient,
        double  dlCompressionFactor,
        double  ulCompressionFactor,   
        double  dpNoiseStd,
        boolean fixedCadence,
        boolean broadcastSelectedOnly) {
        this(roundInterval, maxRounds, devices, aggregator,
            0, samplingFraction, dropoutProbability, preUploadFailureProb, 
            inTransitFailureProb, minCompletionRate, secureAggregationEnabled, 
            secureExtraBytesPerClient, dlCompressionFactor, ulCompressionFactor, 
            dpNoiseStd, fixedCadence, broadcastSelectedOnly, false, 0);
    }
    
    /**
     * Convenience constructor enabling fixed-{@code k} sampling.
     */
    public FLOrchestrator(long   roundInterval,
            int    maxRounds,
            List<FLEdgeDevice> devices,
            FLAggregator       aggregator,
            double  samplingFraction,
            double  dropoutProbability,
            double  preUploadFailureProb,
            double  inTransitFailureProb,
            double  minCompletionRate,
            boolean secureAggregationEnabled,
            long    secureExtraBytesPerClient,
            double  dlCompressionFactor,
            double  ulCompressionFactor,
            double  dpNoiseStd,
            boolean fixedCadence,
            boolean broadcastSelectedOnly,
            boolean useFixedKSampling,
            int     fixedK) {
        this(roundInterval, maxRounds, devices, aggregator,
            0, samplingFraction, dropoutProbability, preUploadFailureProb, inTransitFailureProb,
            minCompletionRate, secureAggregationEnabled, secureExtraBytesPerClient,
            dlCompressionFactor, ulCompressionFactor, dpNoiseStd, fixedCadence, 
            broadcastSelectedOnly, useFixedKSampling, fixedK);
    }

    /**
     * Primary constructor used both for round 0 and for subsequent rounds.
     *
     * @param currentRound 0-based round index for this orchestrator instance.
     */
    public FLOrchestrator(long roundInterval,
        int maxRounds,
        List<FLEdgeDevice> devices,
        FLAggregator aggregator,
        int     currentRound,
        double  samplingFraction,
        double  dropoutProbability,
        double  preUploadFailureProb,
        double  inTransitFailureProb,
        double  minCompletionRate,
        boolean secureAggregationEnabled,
        long    secureExtraBytesPerClient,
        double  dlCompressionFactor,
        double  ulCompressionFactor,  
        double  dpNoiseStd,
        boolean fixedCadence,
        boolean broadcastSelectedOnly,
        boolean useFixedKSampling, 
        int     fixedK) {
        super(roundInterval);
        this.currentRound       = currentRound;
        this.roundInterval      = roundInterval;
        this.devices            = devices;
        this.aggregator         = aggregator;
        this.maxRounds          = maxRounds;
        this.samplingFraction   = samplingFraction;
        this.dropoutProbability = dropoutProbability;
        this.preUploadFailureProb = preUploadFailureProb;
        this.inTransitFailureProb = inTransitFailureProb;
        this.minCompletionRate  = minCompletionRate;
        // Security & Privacy Knobs
        this.secureAggregationEnabled   = secureAggregationEnabled;
        this.secureExtraBytesPerClient  = secureExtraBytesPerClient;
        this.dlCompressionFactor        = Math.max(0.0, Math.min(1.0, dlCompressionFactor));
        this.ulCompressionFactor        = Math.max(0.0, Math.min(1.0, ulCompressionFactor));
        this.dpNoiseStd                 = dpNoiseStd;
        this.fixedCadence               = fixedCadence;
        this.broadcastSelectedOnly      = broadcastSelectedOnly;
        this.useFixedKSampling          = useFixedKSampling;
        this.fixedK                     = Math.max(0, fixedK);
    }
    
    /**
     * Schedules one FL round:
     * - Sample devices (and apply dropout).</li>
     * - Call {@link FLAggregator#startRound(int, int, long, int, List, List, double, double, double, double, double, boolean, long, double, double, double, boolean, boolean, boolean, int)}.
     * - Trigger {@link FLAggregator#broadcastGlobalModel()} (which schedules {@code GlobalModelBroadcastEvent}s).
     * - If fixed cadence and more rounds remain, schedule the next {@code FLOrchestrator} now.
     */
    @Override
    protected void eventAction() {
        final int round = currentRound;
        System.out.println("Orchestrator: Starting FL Round " + round
                + " | pacing=" + (fixedCadence ? "Fixed_Cadence" : "Cooldown_After_Finish"));

        // 1) Client-sampling + drop-outs
        List<FLEdgeDevice> participating = new ArrayList<>();
        //    Support fixed-k sampling (pre-dropout) as an alternative to Bernoulli
        if (useFixedKSampling) {
            List<FLEdgeDevice> pool = new ArrayList<>(devices);
            Collections.shuffle(pool, rng);
            int k = Math.min(fixedK, pool.size());
            for (int i = 0; i < k; i++) {
                FLEdgeDevice dev = pool.get(i);
                if (rng.nextDouble() >= dropoutProbability) {
                    participating.add(dev);
                } else {
                    System.out.println("Orchestrator: Device " + dev.hashCode()
                            + " DROPPED OUT in round " + round + " (fixed-k).");
                }
            }
        } else {
            for (FLEdgeDevice dev : devices) {
                if (rng.nextDouble() < samplingFraction) {          // sampled
                    if (rng.nextDouble() >= dropoutProbability) {   // not dropped
                        participating.add(dev);
                    } else {
                        System.out.println("Orchestrator: Device " + dev.hashCode()
                                + " DROPPED OUT in round " + round);
                    }
                }
            }
        }
        // 2) Notify aggregator how many uploads to expect for THIS round + Policy knobs
        aggregator.startRound(
                round,
                participating.size(),
                roundInterval,
                maxRounds,
                devices,                 // full pool (used for broadcast targeting)
                participating,
                samplingFraction,
                dropoutProbability,
                //failure probabilities
                preUploadFailureProb, 
                inTransitFailureProb, 
                minCompletionRate,
                //Privacy & Security
                secureAggregationEnabled,
                secureExtraBytesPerClient,
                dlCompressionFactor,
                ulCompressionFactor,
                dpNoiseStd,
                fixedCadence,              // pass pacing policy
                broadcastSelectedOnly,
                useFixedKSampling,
                fixedK);

        // 3) Broadcast the current global model at round start
        System.out.println("Orchestrator: Broadcasting global model for round " + round + ".");
        aggregator.broadcastGlobalModel(); // schedules per-device download events
        
        // 4) In FIXED cadence, schedule the next round now (start-to-start)
        if (fixedCadence && (round + 1) < maxRounds) {  
            int nextRound = round + 1;
            System.out.println("Orchestrator: Scheduling next round " + nextRound
                    + " to start in " + roundInterval + " ticks (fixed cadence).");
            new FLOrchestrator(roundInterval, maxRounds, devices, aggregator,
                    nextRound, samplingFraction, dropoutProbability,
                    preUploadFailureProb, 
                    inTransitFailureProb,
                    minCompletionRate,
                    // Privacy and Security
                    secureAggregationEnabled,
                    secureExtraBytesPerClient,
                    dlCompressionFactor,
                    ulCompressionFactor,
                    dpNoiseStd,
                    true,                   // fixed cadence
                    broadcastSelectedOnly,
                    useFixedKSampling,
                    fixedK); // broadcast model policy
        }
    }
}