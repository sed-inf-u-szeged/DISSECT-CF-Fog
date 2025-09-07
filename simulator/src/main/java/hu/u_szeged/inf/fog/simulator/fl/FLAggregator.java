package hu.u_szeged.inf.fog.simulator.fl;

import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.*;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central server-side component that aggregates client updates, maintains the global model,
 * and emits round-level telemetry.
 *
 *  What is modeled? 
 *    - FedAvg of client deltas: (weighted by synthetic sample counts).
 *    - Server-side DP: (Gaussian noise added to aggregated delta).
 *    - Secure aggregation (abstracted): modeled as uplink byte overhead +
 *      accumulating weighted sums; "no cryptographic protocol" is simulated.
 *    - Compression factor:
 *    		- dlCompressionFactor applies to broadcast (downlink)
 *    		- ulCompressionFactor applies to client updates (uplink)
 *    - Timeouts & early aggregation via {@code TIMEOUT_RATIO} and {@code minCompletionRate}.
 *    - Fixed-cadence vs. cool-down pacing (orchestrator vs. aggregator schedules next round).
 *    - Telemetry (per-round and cumulative): bytes, delays (mean/p50/p95), losses, stale arrivals.
 *  What is not modeled?
 *    - Cryptographic details of secure aggregation.
 *    - Cycle-accurate training or packet-level networking.
 *
 *  Time unit: ticks.  Size unit: bytes. 
 */

public class FLAggregator extends ComputingAppliance {
    // Global Model Initialization
    private static final double DEFAULT_INIT_STD = 0.01;

    /** Ratio of round interval to wait before forcing aggregation on timeout. */
    private static volatile double TIMEOUT_RATIO = 0.50;
    
    /** Shared RNG for DP noise and synthetic signals. */
    private final Random rng = SimRandom.get();

    //Timeout Policy helper
    /**
     * Sets {@link #TIMEOUT_RATIO} in range [0.05, 0.95] to avoid degenerate waits.
     *
     * @param v ratio of round interval to wait before timing out the round.
     */
     public static void setTimeoutRatio(double v) {
        double clamped = Math.max(0.05, Math.min(0.95, v));
        TIMEOUT_RATIO = clamped;
     }
     
     
     /**
      * @return current timeout ratio (fraction of round interval).
      */ 
     public static double getTimeoutRatio() {
        return TIMEOUT_RATIO;
     }

     // Aggregator ID
     private final String id;
    
     // Model versioning (increments after each aggregation)
     private int modelVersion = 0; 

     // Secure Aggregation accumulators (current round)
     private int receivedUpdates;          // number of arrivals (updates or shares)
     private double[] secureAggregatedSum; // running ∑(delta_i * n_i)
     private int secureAggregatedSamples;  // running ∑(n_i)

     // Whether aggregation for the currentRound has already taken place.
     private boolean roundClosed = false;

     // Global Model initialization (weights)
     private double[] globalModel;
     private List<FLModelUpdate> updates = new ArrayList<>(); // not secure-agg path only

     // Round‐management fields:
     private int expectedUpdates;
     private int currentRound;      // 0-based round index
     private long roundInterval;
     private int maxRounds;         // total number of rounds
     private List<FLEdgeDevice> devices;

     // Synthetic accuracy history
     private final List<Double> accuracyHistory = new ArrayList<>();

     // Current round’s sampling/dropout policy (for logging/telemetry)
     private double samplingFraction;
     private double dropoutProbability;

     // Failure probabilities
     private double preUploadFailureProbability;
     private double inTransitFailureProbability;

     private Set<FLEdgeDevice> participantsThisRound = new HashSet<>();
     // Minimum fraction of expectedUpdates that must arrive before we aggregate.
     private double minCompletionRate = 1.0;   // default: wait-for-all (i.e., 100%)

     // Privacy & Security Knobs
     private boolean secureAggregationEnabled;
     private long    secureExtraBytesPerClient;
     private double  dlCompressionFactor = 1.0;   // downlink compression
     private double  ulCompressionFactor = 1.0;   // uplink compression
     private double  dpNoiseStd;

     //Pacing & Broadcast policies
     private boolean fixedCadence = true;           // True: overwritten by startRound (default)
     private boolean broadcastSelectedOnly = true;  // True: broadcast only to participants by default

     // Simple telemetry counters
     private int   totalRounds       = 0;
     private long  totalParticipants = 0;

     // Telemetry: Per-round stats (reset each round)
     private long roundDownBytes = 0L;
     private long roundUpModelBytes = 0L;
     private long roundUpSecOverheadBytes = 0L;
     private final List<Long> roundDlDelays = new ArrayList<>();
     private final List<Long> roundUlDelays = new ArrayList<>();
     private int roundPreUploadLosses = 0;
     private int roundInTransitLosses = 0;
     private int roundExpected = 0;
     private int roundStaleArrivals = 0;
     private int roundLateArrivals = 0;
     private long totalLateArrivals = 0L;
     
     private final Map<Integer, Long> staleFromRound = new HashMap<>();

     // Telemetry: Cumulative stats (across all rounds)
     private long totalDownBytes = 0L;
     private long totalUpModelBytes = 0L;
     private long totalUpSecOverheadBytes = 0L;
     private final List<Long> allDlDelays = new ArrayList<>();
     private final List<Long> allUlDelays = new ArrayList<>();
     private long totalPreUploadLosses = 0L;
     private long totalInTransitLosses = 0L;
     private long totalStaleArrivals = 0L;

     // Broadcast to all devices on round 0 so every device has a model copy
     private boolean round0AllBroadcastDone = false;
    
     // Remember selection controls so cool-down mode can preserve them
     private boolean useFixedKSampling = false;
     private int     fixedK = 0;
     
     // Telemetry Export: Per-round snapshots for programmatic export.
     private final List<Integer> prRoundId = new ArrayList<>();
     private final List<Integer> prExpected = new ArrayList<>();
     private final List<Integer> prObserved = new ArrayList<>();
     private final List<Long>    prDownBytes = new ArrayList<>();
     private final List<Long>    prUpModelBytes = new ArrayList<>();
     private final List<Long>    prUpSecOverheadBytes = new ArrayList<>();
     private final List<Double>  prDlMean = new ArrayList<>();
     private final List<Long>    prDlP50  = new ArrayList<>();
     private final List<Long>    prDlP95  = new ArrayList<>();
     private final List<Double>  prUlMean = new ArrayList<>();
     private final List<Long>    prUlP50  = new ArrayList<>();
     private final List<Long>    prUlP95  = new ArrayList<>();
     private final List<Integer> prPreUploadLosses = new ArrayList<>();
     private final List<Integer> prInTransitLosses = new ArrayList<>();
     private final List<Integer> prStaleArrivals = new ArrayList<>();
     private final List<Integer> prLateArrivals = new ArrayList<>();
     private final List<Integer> prModelVersion = new ArrayList<>();
     
     // Telemetry for Client E2E (download + training + upload) and Round wall-clock
     private final List<Long> roundE2E = new ArrayList<>();     // per-round successful contributors’ E2E
     private final List<Long> allE2E   = new ArrayList<>();     // cumulative E2E
     private long roundDurationTicks   = -1;                    // wall-clock round duration (start→aggregate/timeout)
     private long roundTimeoutTicks    = 0;                     // TIMEOUT_RATIO * roundInterval (cached for this round)
     // Telemetry for Per-round export (E2E stats & round duration)
     private final List<Double> prE2EMean      = new ArrayList<>();
     private final List<Long>   prE2EP50       = new ArrayList<>();
     private final List<Long>   prE2EP95       = new ArrayList<>();
     private final List<Long>   prRoundDuration= new ArrayList<>();
     
     // File names
     private String exportCsvPath = "fl_telemetry.csv";
     private String exportPngPath = "fl_telemetry.png";
     
     /**
      * Creates an aggregator with random Gaussian-initialized weights (σ = {@value #DEFAULT_INIT_STD})
      * of default dimension (3).
      *
      * @param id readable identifier for logs/telemetry.
      */
     public FLAggregator(String id) {
         this(id, 3); //
     }

     /**
      * Creates an aggregator with random-initialized weights of a given {@code modelSize}.
      *
      * @param id readable identifier.
      * @param modelSize number of parameters in the global model; must be {@code > 0}.
      */
     public FLAggregator(String id, int modelSize) {
         super(id); 
         this.id = id;
         if (modelSize <= 0) modelSize = 1; // guard
         this.globalModel = new double[modelSize];
         for (int i = 0; i < globalModel.length; i++) {
             globalModel[i] = rng.nextGaussian() * DEFAULT_INIT_STD;
         }
     }

     /**
      * Creates an aggregator with explicit initial weights. If {@code initialWeights} is null or empty,
      * falls back to a 3D random vector.
      *
      * @param id readable identifier.
      * @param initialWeights initial model weights (copied defensively).
      */
     public FLAggregator(String id, double[] initialWeights) {
         super(id); 
         this.id = id;
         if (initialWeights != null && initialWeights.length > 0) {
             this.globalModel = initialWeights.clone();
         } else {
             this.globalModel = new double[3];
             for (int i = 0; i < globalModel.length; i++) {
                 globalModel[i] = rng.nextGaussian() * DEFAULT_INIT_STD;
             }
         }
     }

     /** @return aggregator identifier (for logging). */
     public String getId() {
         return id;
     }
     
     /** @return current model version (increments after successful aggregation). */
     public int getModelVersion() { 
     	return modelVersion; 
     }
     
     /** @return probability of pre-upload loss configured for this round. */
     double getPreUploadFailureProbability() {
         return preUploadFailureProbability;
     }

     /** @return probability of in-transit loss configured for this round. */
     double getInTransitFailureProbability() {
         return inTransitFailureProbability;
     }

     /** @return whether secure aggregation mode is enabled this round. */
     boolean isSecureAggregationEnabled(){
         return secureAggregationEnabled;
     }

     /** @return per-client overhead bytes for secure aggregation in uplink. */
     long getSecureExtraBytesPerClient(){
         return secureExtraBytesPerClient;
     }

     /** @return compression factor [0..1] applied to payloads. */
     double getDlCompressionFactor() { 
    	 return dlCompressionFactor; 
     }
     double getUlCompressionFactor() {
    	 return ulCompressionFactor; 
     } 

     /** @return dimension of the current global model. */
     public int getModelSize() {
         return globalModel.length;
     }

     /**
      * @param dev device to check.
      * @return true if the device was selected to participate this round.
      */
     public boolean isParticipating(FLEdgeDevice dev) {
         return participantsThisRound.contains(dev);
     }

     /**
      * Initializes the new round. Called by {@link FLOrchestrator}.
      *
      * Resets per-round telemetry, (re)initializes secure-agg accumulators or the
      * standard update list, and schedules a {@link RoundTimeoutEvent}.
      *
      * @param round 0-based round id.
      * @param expectedCount number of participating devices (post-dropout).
      * @param roundInterval heartbeat or cool-down interval (ticks).
      * @param maxRounds total number of rounds to run.
      * @param devices full device pool (for broadcast targeting).
      * @param participants selected devices for this round.
      * @param samplingFraction Bernoulli sampling probability used by orchestrator.
      * @param dropoutProbability dropout probability used by orchestrator.
      * @param preUploadFailureProb pre-send loss probability.
      * @param inTransitFailureProb in-flight loss probability.
      * @param minCompletionRate fraction of expected uploads that suffices for aggregation [0..1].
      * @param secureAggregationEnabled secure-agg abstraction on/off.
      * @param secureExtraBytesPerClient uplink overhead bytes per client in secure-agg mode.
      * @param dlCompressionFactor downlink model compression factor [0..1].
      * @param ulCompressionFactor uplink model compression factor [0..1].
      * @param dpNoiseStd server-side DP Gaussian σ applied to aggregated delta.
      * @param fixedCadence true for start-to-start pacing; false for cool-down scheduling.
      * @param broadcastSelectedOnly if true, broadcast only to participants (except round-0 guardrail).
      * @param useFixedKSampling selection strategy flag (for telemetry continuity).
      * @param fixedK fixed-k target (pre-dropout), used only for reporting in this class.
      */
     public void startRound(int round,
                            int expectedCount,
                            long roundInterval,
                            int maxRounds,
                            List<FLEdgeDevice> devices,
                            List<FLEdgeDevice> participants,
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

         this.currentRound                 = round;
         this.expectedUpdates              = expectedCount;
         this.roundInterval                = roundInterval;
         this.maxRounds                    = maxRounds;
         this.devices                      = devices;

         this.samplingFraction             = samplingFraction;
         this.dropoutProbability           = dropoutProbability;
         this.preUploadFailureProbability  = preUploadFailureProb;
         this.inTransitFailureProbability  = inTransitFailureProb;
         this.minCompletionRate            = Math.min(1.0, Math.max(0.0, minCompletionRate));

         //Privacy and Security knobs
         this.secureAggregationEnabled     = secureAggregationEnabled;
         this.secureExtraBytesPerClient    = secureExtraBytesPerClient;
         this.dlCompressionFactor          = Math.max(0.0, Math.min(1.0, dlCompressionFactor));
         this.ulCompressionFactor          = Math.max(0.0, Math.min(1.0, ulCompressionFactor));
         this.dpNoiseStd                   = dpNoiseStd;

         this.fixedCadence                 = fixedCadence;
         this.broadcastSelectedOnly        = broadcastSelectedOnly;
         this.participantsThisRound        = new HashSet<>(participants);
         
         this.useFixedKSampling            = useFixedKSampling;
         this.fixedK                       = Math.max(0, fixedK);

         // Secure Aggregation initialization
         receivedUpdates          = 0;
         secureAggregatedSamples  = 0;
         if (secureAggregationEnabled) {
             secureAggregatedSum  = new double[globalModel.length];
         } else {
             updates.clear();
         }

         // Statistics for later summary
         totalRounds++;
         totalParticipants += expectedCount;

         // Telemetry: reset per-round counters
         roundDownBytes = 0L;
         roundUpModelBytes = 0L;
         roundUpSecOverheadBytes = 0L;
         roundDlDelays.clear();
         roundUlDelays.clear();
         roundPreUploadLosses = 0;
         roundInTransitLosses = 0;
         roundExpected = expectedCount;
         roundStaleArrivals = 0;
         roundLateArrivals = 0;
         // Telemetry: Round duration telemetry
         roundE2E.clear();
         roundDurationTicks = -1;
         roundTimeoutTicks  = Math.max(1, (long)Math.ceil(roundInterval * TIMEOUT_RATIO)); // cache
         
         // Informative logging
         int  estSampled   = (int) Math.round(devices.size() * samplingFraction);
         int  estDropped   = Math.max(0, estSampled - expectedCount);
         System.out.println("Aggregator " + id + ": Round " + round
                 + " — policy sf=" + samplingFraction
                 + ", dp=" + dropoutProbability
                 + ", preUF=" + preUploadFailureProbability
                 + ", inTransF=" + inTransitFailureProbability
                 + " | minCompletionRate=" + this.minCompletionRate
                 + " | estSampled=" + estSampled
                 + ", estDropped≈" + estDropped
                 + ", participating=" + expectedCount
                 + " | secureAgg=" + secureAggregationEnabled
                 + ", dlCompFactor=" + this.dlCompressionFactor 
                 + ", ulCompFactor=" + this.ulCompressionFactor
                 + ", dpNoiseStd=" + dpNoiseStd
                 + " | pacing=" + (fixedCadence ? "Fixed_Cadence" : "Cooldown_After_Finish")
                 + " | broadcastPolicy=" + (broadcastSelectedOnly ? "Participants_Only" : "All_Devices")
                 + " | modelVersion=" + modelVersion + "."
         );

         // Timeout event handling (relative to round start)
         roundClosed = false;
         new RoundTimeoutEvent(roundTimeoutTicks, this, round);

         if (expectedUpdates == 0) { //Early Exit if no participants
             System.out.println("Aggregator " + id + ": No participants selected for round "
                     + round + ". Aggregating (no-op). "
                     + (fixedCadence ? "Next round will start by fixed cadence."
                                     : "Scheduling next round after cool-down."));
             roundDurationTicks = 0; // roundDurationTicks = 0 when no participants
             aggregateModels();      // will keep current weights if none
             if (!fixedCadence) {
                 scheduleNextRound();
             }
         }
     }
     
     //record successful contributor’s end-to-end latency
     void noteClientE2E(long ticks) {
         roundE2E.add(ticks);
         allE2E.add(ticks);
     }
     
     //Helper to get kth (1-based) smallest from a list assumed to have >= k elems
     private static long kthSmallest(List<Long> vals, int k) {
         List<Long> copy = new ArrayList<>(vals);
         Collections.sort(copy);
         int idx = Math.max(0, k - 1);
         return copy.get(idx);
     }
     
     public void addModelUpdate(FLModelUpdate update, int roundId, int baseModelVersion) {
         // Backward-compatible path (no E2E known) — keep behavior unchanged
         addModelUpdate(update, roundId, baseModelVersion, -1L); // delegate
     }

     /**
      * Receives a model update (or secure share) from a device upload.
      * Overload that receives the client’s E2E ticks; only counted if accepted
      *
      * Updates from other rounds are considered "stale" and dropped with telemetry.
      * Late arrivals with the correct round after aggregation are ignored (round is closed).
      *
      * @param update client delta and metadata (defensive copies inside).
      * @param roundId round id the client believes it is contributing to.
      * @param baseModelVersion model version used by the client when training its update.
      */
     public void addModelUpdate(FLModelUpdate update, int roundId, int baseModelVersion, long e2eTicks) {
     	if (roundId != currentRound) {
             roundStaleArrivals++;
             totalStaleArrivals++; 
             staleFromRound.merge(roundId, 1L, Long::sum);
             System.out.println("Aggregator " + id + ": STALE update dropped (from round "
                     + roundId + " → current " + currentRound + ", baseModelVersion=" + baseModelVersion + ").");
             return;
        }
     	
     	// Count same-round late arrivals (after aggregation closed)
        if (roundClosed) {
            roundLateArrivals++;
            totalLateArrivals++;
            System.out.println("Aggregator " + id + ": LATE arrival ignored in round "
                    + currentRound + " (baseModelVersion=" + baseModelVersion + ").");
            return;
        }
        
        // Only now (accepted arrival) record E2E if provided
        if (e2eTicks >= 0) noteClientE2E(e2eTicks);
     	
         receivedUpdates++;   //Counter for every arrival

         // Secure Aggregation
         if (secureAggregationEnabled) {
             double[] p = update.getModelParameters();
             int s      = update.getSampleCount();
             int len = Math.min(globalModel.length, p.length);
             for (int i = 0; i < len; i++) {
                 secureAggregatedSum[i] += p[i] * s;
             }
             secureAggregatedSamples += s;
         } else {
             // Traditional FedAvg path (store full update for later)
             updates.add(update);
         }

         System.out.println("Aggregator " + id + ": Received "
                 + (secureAggregationEnabled ? "secure share " : "update ")
                 + receivedUpdates + "/" + expectedUpdates
                 + " (baseModelVersion=" + baseModelVersion + ").");

         // Early aggregation check
         int minNeeded = Math.max(1, (int) Math.ceil(expectedUpdates * minCompletionRate));
         
         // If this arrival pushes us to early-aggregation condition, set roundDuration
         if (roundDurationTicks < 0 && receivedUpdates >= minNeeded) {
             long kth = kthSmallest(roundE2E, minNeeded);                // kth contributor E2E
             roundDurationTicks = Math.min(kth, roundTimeoutTicks);      // guard vs timeout window
             System.out.println("Aggregator " + id + ": roundWallClock determined by early aggregation = "
                     + roundDurationTicks + " ticks (k=" + minNeeded + ").");
         }
         
         if (receivedUpdates >= minNeeded) {
             aggregateModels();
             if (!fixedCadence) {
                 scheduleNextRound();
             }
         }
     }

     /**
      * Aggregates received deltas with FedAvg semantics and updates the global model.
      * In secure-agg mode the class accumulates weighted sums online, otherwise it
      * stores updates and aggregates them here.
      */
     private void aggregateModels() {
         roundClosed = true;

         // Telemetry capture observed count for this round
         int observedThisRound = receivedUpdates;
         
         // Compute Round Duration
         if (roundDurationTicks < 0) {
             int minNeeded = Math.max(1, (int)Math.ceil(expectedUpdates * minCompletionRate));
             if (observedThisRound >= minNeeded && !roundE2E.isEmpty()) {
                 long kth = kthSmallest(roundE2E, minNeeded);
                 roundDurationTicks = Math.min(kth, roundTimeoutTicks);
                 System.out.println("Aggregator " + id + ": roundWallClock computed in aggregateModels() "
                         + "(late set, early-agg) = " + roundDurationTicks + " ticks."); // [NEW]
             } else {
                 roundDurationTicks = roundTimeoutTicks;
                 System.out.println("Aggregator " + id + ": roundWallClock computed in aggregateModels() "
                         + "(timeout path) = " + roundDurationTicks + " ticks."); // [NEW]
             }
         }
         
         // Secure Aggregation
         if (secureAggregationEnabled) {
             if (secureAggregatedSamples == 0) {
                 System.out.println("Aggregator " + id + ": No shares aggregated for round "
                         + currentRound + ". Global model unchanged: "
                         + Arrays.toString(globalModel)
                         + " | modelVersion=" + modelVersion);
                 printRoundTelemetry(observedThisRound);
                 stashRoundTelemetry(observedThisRound);
                 scheduleEvaluationEvent();
                 return;
             }

             double[] newGlobal = globalModel.clone();
             for (int i = 0; i < globalModel.length; i++) {
                 double avgDelta = secureAggregatedSum[i] / secureAggregatedSamples;
                 if (dpNoiseStd > 0.0) {
                     avgDelta += rng.nextGaussian() * dpNoiseStd;
                 }
                 newGlobal[i] += avgDelta;
             }
             globalModel = newGlobal;
             modelVersion++;
             System.out.println("Aggregator " + id + " (secure) finished FedAvg Δ for round "
                     + currentRound + ". New global model = "
                     + Arrays.toString(globalModel)
                     + " | modelVersion=" + modelVersion);
             printRoundTelemetry(observedThisRound);
             stashRoundTelemetry(observedThisRound); 
             scheduleEvaluationEvent();
             return;
         }

         // Non Secure Aggregation Path
         if (updates.isEmpty()) {
             System.out.println("Aggregator " + id + ": No updates to aggregate for round "
                     + currentRound + ". Global model unchanged: "
                     + Arrays.toString(globalModel));
             printRoundTelemetry(observedThisRound);
             stashRoundTelemetry(observedThisRound);
             scheduleEvaluationEvent();
             return;
         }

         double[] sumDelta = new double[globalModel.length];
         int totalSamples = updates.stream().mapToInt(FLModelUpdate::getSampleCount).sum();

         for (FLModelUpdate up : updates) {
             double[] p = up.getModelParameters(); // p is Δw
             int       s = up.getSampleCount();
             int len    = Math.min(globalModel.length, p.length);
             for (int i = 0; i < len; i++) {
                 sumDelta[i] += p[i] * s;
             }
         }

         double[] newGlobal = globalModel.clone();
         for (int i = 0; i < globalModel.length; i++) {
             double avgDelta = sumDelta[i] / totalSamples;
             if (dpNoiseStd > 0.0) {
                 avgDelta += rng.nextGaussian() * dpNoiseStd;
             }
             newGlobal[i] += avgDelta;  // apply delta
         }
         globalModel = newGlobal;
         modelVersion++;

         System.out.println("Aggregator " + id
                 + " finished FedAvg Δ for round " + currentRound
                 + ". New global model = " + Arrays.toString(globalModel)
                 + " | modelVersion=" + modelVersion);

         updates.clear();
         printRoundTelemetry(observedThisRound);
         stashRoundTelemetry(observedThisRound);
         scheduleEvaluationEvent();
     }

     /** @return defensive reference to the current global model array. */
     public double[] getGlobalModel() {
         return globalModel;
     }

     /** Schedules a one-tick delayed {@link EvaluationEvent} to record synthetic accuracy. */
     private void scheduleEvaluationEvent() {
         new EvaluationEvent(1, this, currentRound, maxRounds);
     }

     /**
      * Stores and logs synthetic accuracy for external access and post-run analysis.
      *
      * @param round round index.
      * @param acc accuracy in [0,1].
      */
     void recordAccuracy(int round, double acc) {
         while (accuracyHistory.size() <= round) accuracyHistory.add(null);
         accuracyHistory.set(round, acc);
         System.out.println("Aggregator " + id + ": Synthetic accuracy @round "
                 + round + " = " + String.format("%.4f", acc));
     }

     /**
      * Broadcasts the current global model to either participants or the full pool.
      * On round 0, a guardrail ensures a one-time broadcast to all devices so every
      * device has a local copy.
      */
     public void broadcastGlobalModel() {
         // [ADDED] Round-0 guardrail: broadcast to ALL devices once so every device has a model copy.
         boolean forceAllThisRound = (currentRound == 0) && !round0AllBroadcastDone;
         if (forceAllThisRound) {
             System.out.println("Aggregator " + id + ": Round-0 guardrail → broadcasting to ALL devices once.");
             round0AllBroadcastDone = true;
         }

         Collection<FLEdgeDevice> targets = (broadcastSelectedOnly && !forceAllThisRound)
                 ? participantsThisRound
                 : devices;

         long rawSizeBytes  = (long) globalModel.length * Double.BYTES;
         long payloadBytes  = (long) Math.ceil(rawSizeBytes * dlCompressionFactor); // Downlink model compression
         System.out.println("Aggregator " + id + ": broadcasting model to "
                 + targets.size() + " devices ("
                 + ((broadcastSelectedOnly && !forceAllThisRound) ? "participants only" : "all")
                 + ") with payload=" + payloadBytes + " B." + " | modelVersion=" + modelVersion + "."); 

         for (FLEdgeDevice dev : targets) {
             long latency   = dev.getLatency();
             long bandwidth = dev.getBandwidth();
             long dlDelay   = latency + (long) Math.ceil(payloadBytes / (double) bandwidth);

             // Telemetry: track per-target downlink
             noteDownlink(payloadBytes, dlDelay);
             
             // Pass dlDelay twice: as event delay and as explicit value for E2E accumulation
             new GlobalModelBroadcastEvent(dlDelay, dev, globalModel, currentRound, this, modelVersion, dlDelay);
             System.out.println("Aggregator " + id + ": broadcasting model to device "
                     + dev.hashCode() + " with dlDelay=" + dlDelay);
         }
     }

     /** In cool-down mode, schedules the next round after aggregation/timeout. */
     private void scheduleNextRound() {
         // Rounds are 0..maxRounds-1
         if ((currentRound + 1) < maxRounds) {
             int nextRound = currentRound + 1;
             System.out.println("Aggregator " + id + ": Scheduling round "
                     + nextRound + " after " + roundInterval + " ticks.");
             new FLOrchestrator(roundInterval, maxRounds, devices, this,
                     nextRound,
                     samplingFraction,
                     dropoutProbability,
                     preUploadFailureProbability,
                     inTransitFailureProbability,
                     minCompletionRate,
                     //Privacy and Security
                     secureAggregationEnabled,
                     secureExtraBytesPerClient,
                     dlCompressionFactor,
                     ulCompressionFactor,
                     dpNoiseStd,
                     false,
                     broadcastSelectedOnly,
                     useFixedKSampling,
                     fixedK);
         } else {
             //Final Summary
             double avgPart = (double) totalParticipants / totalRounds;
             Double finalAcc = accuracyHistory.isEmpty()? null
                     : accuracyHistory.get(accuracyHistory.size() - 1);
             String accMsg   = finalAcc == null? "n/a"
                     : String.format("%.4f", finalAcc);

             // Telemetry: cumulative delay stats
             String dlStats = formatDelayStats(allDlDelays);
             String ulStats = formatDelayStats(allUlDelays);

             System.out.println("Aggregator " + id + ": Reached max rounds ("
                     + maxRounds + "). Avg participants/round ≈ "
                     + String.format("%.2f", avgPart)
                     + ", final accuracy = " + accMsg);

             // Telemetry: totals
             System.out.println("Aggregator " + id + " [TOTALS]: "
                     + "DL=" + totalDownBytes + " B"
                     + ", UL=" + totalUpModelBytes + " B"
                     + " (secOverhead=" + totalUpSecOverheadBytes + " B)"
                     + " | DL delays " + dlStats
                     + " | UL delays " + ulStats
                     + " | losses: pre-upload=" + totalPreUploadLosses
                     + ", in-transit=" + totalInTransitLosses 
                     + " | staleArrivals=" + totalStaleArrivals
                     + " | lateArrivals=" + totalLateArrivals
                     + " | modelVersion=" + modelVersion);  
             if (!staleFromRound.isEmpty()) {
                 System.out.println("Aggregator " + id + " [STALE HISTOGRAM] (sourceRound -> count): " + staleFromRound);
             }
         }
     }

     // Timeout Callback -- Package-private called by RoundTimeoutEvent
     /**
      * Forces aggregation when the timeout for {@code roundId} fires and the round
      * is still open. No effect if the round already closed or the round id mismatches.
      */
     void handleTimeout(int roundId) {
         if (roundId != currentRound || roundClosed) return;
         int observed = receivedUpdates; // counts updates or secure shares
         String unit = secureAggregationEnabled ? "shares" : "updates";
         System.out.println("Aggregator " + id + ": TIMEOUT for round " + roundId
                 + ". Proceeding with " + observed + "/" + expectedUpdates + " " + unit + ".");
         if (roundDurationTicks < 0) roundDurationTicks = roundTimeoutTicks;
         aggregateModels();
         if (!fixedCadence) {
             scheduleNextRound();
         }
     }

     // Telemetry: Helpers & hooks 

     /** Records downlink payload and delay for this round and totals. */
     void noteDownlink(long bytes, long delayTicks) {
         roundDownBytes += bytes;
         totalDownBytes += bytes;
         roundDlDelays.add(delayTicks);
         allDlDelays.add(delayTicks);
     }

     /** Records an upload success (model bytes, security overhead, and delay). */
     void noteUploadSuccess(long modelBytes, long secOverheadBytes, long delayTicks) {
         roundUpModelBytes += modelBytes;
         roundUpSecOverheadBytes += secOverheadBytes;
         totalUpModelBytes += modelBytes;
         totalUpSecOverheadBytes += secOverheadBytes;
         roundUlDelays.add(delayTicks);
         allUlDelays.add(delayTicks);
     }

     /** Increments pre-upload loss counters. */
     void notePreUploadLoss() {
         roundPreUploadLosses++;
         totalPreUploadLosses++;
     }

     /** Increments in-transit loss counters. */
     void noteInTransitLoss() {
         roundInTransitLosses++;
         totalInTransitLosses++;
     }

     /** Prints per-round telemetry summary (bytes, delays, losses, stale arrivals). */
     private void printRoundTelemetry(int observed) {
         String dlStats = formatDelayStats(roundDlDelays);
         String ulStats = formatDelayStats(roundUlDelays);
         System.out.println("Aggregator " + id + " [ROUND " + currentRound + " TELEMETRY]: "
                 + "expected=" + roundExpected + ", observed=" + observed
                 + " | DL=" + roundDownBytes + " B"
                 + " | UL=" + roundUpModelBytes + " B (secOverhead=" + roundUpSecOverheadBytes + " B)"
                 + " | DL delays " + dlStats
                 + " | UL delays " + ulStats
                 + " | losses: pre-upload=" + roundPreUploadLosses
                 + ", in-transit=" + roundInTransitLosses
                 + " | staleArrivals=" + roundStaleArrivals
                 + " | lateArrivals=" + roundLateArrivals);
     }

     /**
      * Formats delay statistics (mean, p50, p95) for logging.
      *
      * @param delays list of delay samples (ticks).
      * @return formatted summary string.
      */
     private static String formatDelayStats(List<Long> delays) {
         if (delays.isEmpty()) return "(n=0)";
         List<Long> sorted = new ArrayList<>(delays);
         Collections.sort(sorted);
         double mean = 0.0;
         for (long d : sorted) mean += d;
         mean /= sorted.size();
         long p50 = percentile(sorted, 0.50);
         long p95 = percentile(sorted, 0.95);
         return "(n=" + sorted.size() + ", mean=" + String.format("%.2f", mean)
                 + ", p50=" + p50 + ", p95=" + p95 + ")";
     }

     /**
      * Computes an interpolated percentile from a sorted list of values.
      *
      * @param sortedAsc sorted list (ascending).
      * @param q quantile in [0,1].
      * @return interpolated percentile.
      */
     private static long percentile(List<Long> sortedAsc, double q) {
         if (sortedAsc.isEmpty()) return 0L;
         double idx = q * (sortedAsc.size() - 1);
         int lo = (int) Math.floor(idx);
         int hi = (int) Math.ceil(idx);
         if (lo == hi) return sortedAsc.get(lo);
         double frac = idx - lo;
         return Math.round(sortedAsc.get(lo) * (1 - frac) + sortedAsc.get(hi) * frac);
     }
     
     // Per-round snapshot + CSV export + Python plotting hook
     private static double meanOf(List<Long> vals) {
         if (vals.isEmpty()) return 0.0;
         double s = 0;
         for (long v : vals) s += v;
         return s / vals.size();
     }

     // Capture per-round counters into arrays for later export
     private void stashRoundTelemetry(int observed) {
    	 prRoundId.add(currentRound);
    	 prExpected.add(roundExpected);
         prObserved.add(observed);
         prDownBytes.add(roundDownBytes);
         prUpModelBytes.add(roundUpModelBytes);
         prUpSecOverheadBytes.add(roundUpSecOverheadBytes);
         List<Long> dl = new ArrayList<>(roundDlDelays);
         Collections.sort(dl);
         List<Long> ul = new ArrayList<>(roundUlDelays);
         Collections.sort(ul);
         prDlMean.add(meanOf(dl));
         prDlP50.add(percentile(dl, 0.50));
         prDlP95.add(percentile(dl, 0.95));
         prUlMean.add(meanOf(ul));
         prUlP50.add(percentile(ul, 0.50));
         prUlP95.add(percentile(ul, 0.95));
         prPreUploadLosses.add(roundPreUploadLosses);
         prInTransitLosses.add(roundInTransitLosses);
         prStaleArrivals.add(roundStaleArrivals);
         prLateArrivals.add(roundLateArrivals);
         prModelVersion.add(modelVersion);
         
         List<Long> e2e = new ArrayList<>(roundE2E);
         Collections.sort(e2e);
         prE2EMean.add(meanOf(e2e));
         prE2EP50.add(percentile(e2e, 0.50));
         prE2EP95.add(percentile(e2e, 0.95));
         prRoundDuration.add(Math.max(0, roundDurationTicks));
     }

     /** Returns a copy of the accuracy history for programmatic use. */
     public synchronized List<Double> getAccuracyHistorySnapshot() {
         return new ArrayList<>(accuracyHistory);
     }

     /** Export per-round telemetry (including accuracy if available) to CSV. */
     public synchronized void exportTelemetryCsv(String path) throws IOException {
         int rows = prExpected.size();
         try (PrintWriter pw = new PrintWriter(
                 new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
        	 pw.println("round,expected,observed,down_bytes,up_model_bytes,up_sec_overhead_bytes,"
                     + "dl_mean,dl_p50,dl_p95,ul_mean,ul_p50,ul_p95,pre_upload_losses,in_transit_losses,"
                     + "stale_arrivals,late_arrivals,accuracy,model_version,"
                     + "e2e_mean,e2e_p50,e2e_p95,round_duration");
             for (int r = 0; r < rows; r++) {
                 Double acc = (r < accuracyHistory.size()) ? accuracyHistory.get(r) : null;
                 String accStr = (acc == null) ? "" : String.format(Locale.US, "%.6f", acc);
                 
                 double e2eMean = (r < prE2EMean.size()) ? prE2EMean.get(r) : 0.0;
                 long   e2eP50  = (r < prE2EP50.size())  ? prE2EP50.get(r)  : 0L;
                 long   e2eP95  = (r < prE2EP95.size())  ? prE2EP95.get(r)  : 0L;
                 long   rDur    = (r < prRoundDuration.size()) ? prRoundDuration.get(r) : 0L;
                 
                 pw.println(
                         prRoundId.get(r) + "," 
                         + prExpected.get(r) + ","
                         + prObserved.get(r) + ","
                         + prDownBytes.get(r) + ","
                         + prUpModelBytes.get(r) + ","
                         + prUpSecOverheadBytes.get(r) + ","
                         + String.format(Locale.US, "%.6f", prDlMean.get(r)) + ","
                         + prDlP50.get(r) + ","
                         + prDlP95.get(r) + ","
                         + String.format(Locale.US, "%.6f", prUlMean.get(r)) + ","
                         + prUlP50.get(r) + ","
                         + prUlP95.get(r) + ","
                         + prPreUploadLosses.get(r) + ","
                         + prInTransitLosses.get(r) + ","
                         + prStaleArrivals.get(r) + ","
                         + prLateArrivals.get(r) + ","
                         + accStr + ","
                         + prModelVersion.get(r) + ","
                         + String.format(Locale.US, "%.6f", e2eMean) + ","
                         + e2eP50 + ","
                         + e2eP95 + ","
                         + rDur
                 );
             }
         }
         System.out.println("Aggregator " + id + ": Telemetry CSV exported to " + path);
     }

     /** Attempts to plot the exported CSV using Python (matplotlib). */
     private void tryPlotWithPython(String csvPath, String outPngPath) {
         String py = ""
             + "import csv, sys\n"
             + "import matplotlib.pyplot as plt\n"
             + "csv_path=sys.argv[1]; out_path=sys.argv[2]\n"
             + "R=[]; ACC=[]; DL=[]; UL=[]\n"
             + "with open(csv_path, newline='') as f:\n"
             + "    r=csv.DictReader(f)\n"
             + "    for row in r:\n"
             + "        R.append(int(row['round']))\n"
             + "        acc=row.get('accuracy','')\n"
             + "        ACC.append(float(acc) if acc not in ('', None) else float('nan'))\n"
             + "        DL.append((float(row['down_bytes']))/1e6)\n"
             + "        UL.append((float(row['up_model_bytes'])+float(row['up_sec_overhead_bytes']))/1e6)\n"
             + "plt.figure(figsize=(8,5))\n"
             + "ax1=plt.gca()\n"
             + "ax1.plot(R, ACC, marker='o', label='Accuracy')\n"
             + "ax1.set_xlabel('Round'); ax1.set_ylabel('Accuracy'); ax1.set_ylim(0,1)\n"
             + "ax2=ax1.twinx()\n"
             + "ax2.plot(R, DL, linestyle='--', label='Down MB')\n"
             + "ax2.plot(R, UL, linestyle=':', label='Up MB (model+sec)')\n"
             + "ax2.set_ylabel('Traffic (MB)')\n"
             + "lines1, labels1 = ax1.get_legend_handles_labels()\n"
             + "lines2, labels2 = ax2.get_legend_handles_labels()\n"
             + "ax2.legend(lines1+lines2, labels1+labels2, loc='best')\n"
             + "ax1.grid(True, linestyle='--', alpha=0.3)\n"
             + "plt.title('FL Telemetry: Accuracy and Traffic')\n"
             + "plt.tight_layout(); plt.savefig(out_path, dpi=150)\n";

         try {
             Path script = Paths.get("fl_plot_tmp.py");
             Files.write(script, py.getBytes(StandardCharsets.UTF_8));
             List<String> cmd = new ArrayList<>();
             cmd.add("python3"); cmd.add(script.toString()); cmd.add(csvPath); cmd.add(outPngPath);
             Process p = new ProcessBuilder(cmd).inheritIO().start();
             int exit = p.waitFor();
             if (exit != 0) {
                 cmd.set(0, "python");
                 Process p2 = new ProcessBuilder(cmd).inheritIO().start();
                 int exit2 = p2.waitFor();
                 if (exit2 != 0) {
                     System.out.println("Aggregator " + id + ": Python plotting failed (matplotlib missing?). CSV is available at " + csvPath);
                 } else {
                     System.out.println("Aggregator " + id + ": Plot saved to " + outPngPath);
                 }
             } else {
                 System.out.println("Aggregator " + id + ": Plot saved to " + outPngPath);
             }
             try { Files.deleteIfExists(script); } catch (Exception ignore) {}
         } catch (Exception e) {
             System.out.println("Aggregator " + id + ": Skipping plot – " + e.getMessage() + ". CSV at " + csvPath);
         }
     }

     public void onFinalEvaluationComplete() {
         try {
             exportTelemetryCsv(exportCsvPath);
         } catch (IOException e) {
             System.out.println("Aggregator " + id + ": Failed to export telemetry CSV: " + e.getMessage());
             return;
         }
         tryPlotWithPython(exportCsvPath, exportPngPath);
     }
 }