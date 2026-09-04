package hu.u_szeged.inf.fog.simulator.fl;

import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.util.CloudLoader;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;


import java.util.*;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;


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
 *    - Timeouts & early aggregation via per-instance {@code timeoutRatio} and {@code minCompletionRate}.
 *    - Fixed-cadence vs. cool-down pacing (orchestrator vs. aggregator schedules next round).
 *    - Telemetry (per-round and cumulative): bytes, delays (mean/p50/p95), losses, stale arrivals.
 *  What is not modeled?
 *    - Cryptographic details of secure aggregation.
 *    - Cycle-accurate training or packet-level networking.
 *
 *  <b>Timing vs. energy — deliberate abstraction.</b><br>
 *  FL local-training duration is computed <i>analytically</i> from
 *  {@code epochMultiplier * instructionPerByte * fileSize / throughput} (see
 *  {@link GlobalModelBroadcastEvent}), and the resulting {@code compDelay} is the
 *  scheduling trigger for {@link LocalTrainingEvent}. In parallel, when
 *  {@code nativeTransferMeteringEnabled} is on, a CPU task is also submitted to
 *  the device's VM (or its {@link hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine}
 *  as a fallback) via {@code newComputeTask} / {@code compute} purely so the simulator's
 *  energy meters observe the work; the task's completion callback is intentionally a
 *  no-op. Consequence: FL training time is <i>independent</i> of VM contention and
 *  scheduling. The two paths can diverge under co-tenancy (energy meter sees real CPU
 *  draw; FL events fire at the analytic time). This is a known modeling choice — chosen
 *  for tractability and reproducibility — not a bug. To make FL participate in
 *  simulator contention, move the {@link LocalTrainingEvent} construction inside the
 *  {@code ConsumptionEventAdapter#conComplete} callback in
 *  {@link GlobalModelBroadcastEvent#tryScheduleCpuWorkViaVm}.
 *
 *  Time unit: ticks.  Size unit: bytes.
 */

public class FLAggregator {
    // Global Model Initialization
    private static final double DEFAULT_INIT_STD = 0.01;

    // --- Host-node fields (previously inherited from ComputingAppliance) ---
    // The aggregator no longer extends ComputingAppliance: it is a server-side process,
    // not a fog/cloud node. These fields preserve the field-level API that the demos and
    // events relied on (e.g. {@code aggregator.iaas}) without dragging in the unused
    // fog-node fields (neighbors, parent, applications, broker, range, timeline) and
    // without registering each aggregator in {@code ComputingAppliance.allComputingAppliances}.

    /** Human-readable identifier; also exposed as the prior {@code name} field. */
    public final String name;
    /** Geographic location of the host node (may be {@code null} for IaaS-less constructors). */
    public final GeoLocation geoLocation;
    /** Underlying IaaSService that hosts the aggregator's repository ({@code null} for the no-IaaS constructors). */
    public IaaSService iaas;

    /**
     * Default timeout ratio applied to newly-constructed FLAggregator instances.
     * Mutated by the static {@link #setTimeoutRatio(double)} helper; each new
     * aggregator snapshots this value into its own {@link #timeoutRatio} field
     * at construction, so changes here do not retroactively affect already-running
     * aggregators. This allows running multiple aggregators with different
     * timeout policies in the same simulation.
     */
    private static volatile double defaultTimeoutRatio = 0.50;

    /**
     * Per-instance timeout ratio: fraction of {@code roundInterval} to wait
     * before forcing aggregation on timeout. Captured at construction time
     * from {@link #defaultTimeoutRatio} and immutable thereafter.
     */
    private final double timeoutRatio = defaultTimeoutRatio;

    /** Shared RNG for DP noise and synthetic signals. */
    private final Random rng = SimRandom.get();
    
    private Repository serverRepo;
    
    //Switch to enable/disable native DL/UL transfer metering
    private boolean nativeTransferMeteringEnabled = true;  // default ON

    //
    public void setNativeTransferMeteringEnabled(boolean on) { this.nativeTransferMeteringEnabled = on; }
    // package-private for module use
    boolean isNativeTransferMeteringEnabled() { return nativeTransferMeteringEnabled; }

    /**
     * Optional learning-outcome provider (§8.4). {@code null} (the default) means
     * SYNTHETIC mode — {@link EvaluationEvent} keeps using the legacy
     * {@code 0.50 + 0.40·prog + N(0, 0.02)} formula, byte-identical to the
     * SIMPAT baseline. A non-null TRACE/ONLINE provider routes accuracy through
     * the bridge for Pass-3 replay of the centralized/hierarchical cells.
     */
    private hu.u_szeged.inf.fog.simulator.fl.cosim.LearningProvider learningProvider; // null ⇒ SYNTHETIC

    /** Sets the learning provider (null ⇒ SYNTHETIC, the default). */
    public void setLearningProvider(hu.u_szeged.inf.fog.simulator.fl.cosim.LearningProvider provider) {
        this.learningProvider = provider;
    }

    /** The learning provider, or null in SYNTHETIC mode. */
    public hu.u_szeged.inf.fog.simulator.fl.cosim.LearningProvider getLearningProvider() {
        return learningProvider;
    }

    //Timeout Policy helper
    /**
     * Sets the {@link #defaultTimeoutRatio} used by FLAggregator instances
     * constructed *after* this call, clamped to {@code [0.05, 0.95]} to avoid
     * degenerate waits. Does not affect aggregators already constructed; each
     * aggregator captures its own ratio at construction time.
     *
     * @param v ratio of round interval to wait before timing out the round.
     */
     public static void setTimeoutRatio(double v) {
        double clamped = Math.max(0.05, Math.min(0.95, v));
        defaultTimeoutRatio = clamped;
     }


     /**
      * @return the current default timeout ratio applied to newly-constructed
      *         aggregators. To read an existing aggregator's ratio, use the
      *         instance accessor on that aggregator.
      */
     public static double getTimeoutRatio() {
        return defaultTimeoutRatio;
     }

     // Aggregator ID
     private final String id;

     /**
      * The orchestrator that drives this aggregator's rounds. Set by the orchestrator
      * itself during its construction via {@link #setOrchestrator(FLOrchestrator)}.
      * Used in cool-down pacing mode: when aggregation completes, the aggregator hands
      * control back via {@code orchestrator.scheduleNext()} instead of allocating a new
      * orchestrator instance.
      */
     private FLOrchestrator orchestrator;

     /** Package-private hand-off setter invoked by {@link FLOrchestrator}'s constructor. */
     void setOrchestrator(FLOrchestrator o) {
         this.orchestrator = o;
     }

     /**
      * The currently-armed {@link RoundTimeoutEvent} for the in-progress round, kept so
      * we can {@link RoundTimeoutEvent#cancel()} it when the round closes early via
      * {@code minCompletionRate}. Cleared each round in {@link #startRound}.
      */
     private RoundTimeoutEvent currentRoundTimeout;

     /**
      * True while we are inside the {@link RoundTimeoutEvent}'s own {@code eventAction()}
      * (i.e. inside {@link #handleTimeout}). In that window, {@link #aggregateModels()}
      * must NOT call {@link RoundTimeoutEvent#cancel() currentRoundTimeout.cancel()}:
      * {@code DeferredEvent} only marks an event as {@code received} <i>after</i> its
      * eventAction returns, so cancel would still call
      * {@code simultaneouslyOccurringDEs.remove(this)} on the list that
      * {@code AggregatedEventDispatcher.tick} is currently iterating — producing a
      * {@link java.util.ConcurrentModificationException}. The early-aggregation and
      * no-participant early-exit paths into {@code aggregateModels} run at a different
      * tick than the timeout's {@code eventArrival}, so they cancel safely.
      */
     private boolean handlingTimeout = false;

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
     // Note: roundInterval was previously stored here so the pre-HIGH-4 scheduleNextRound
     // could pass it into a freshly-constructed FLOrchestrator. After HIGH-4 the orchestrator
     // owns its own roundInterval and we just call orchestrator.scheduleNext() with no args,
     // so the field became dead and was removed. It is still received as a parameter by
     // startRound and used locally there to compute roundTimeoutTicks.
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
     private long roundTimeoutTicks    = 0;                     // timeoutRatio * roundInterval (cached for this round)
     // Telemetry for Per-round export (E2E stats & round duration)
     private final List<Double> prE2EMean      = new ArrayList<>();
     private final List<Long>   prE2EP50       = new ArrayList<>();
     private final List<Long>   prE2EP95       = new ArrayList<>();
     private final List<Long>   prRoundDuration= new ArrayList<>();
     
     // File names
     private String exportCsvPath = "fl_telemetry.csv";
     private String exportPngPath = "fl_telemetry.png";
     
     // Finished-signal callback (invoked after last round’s export/plot)
     private Runnable finishedCallback = null;
     
     // Energy per-round capture
     private double baseSrv_mJ = 0.0;                 // Store in mJ (as collector provides)
     private double baseParts_mJ = 0.0;               // Store in mJ
     private final List<Double> prSrvEnergy_J = new ArrayList<>();   // Store/export in Joules
     private final List<Double> prPartEnergy_J = new ArrayList<>();  // Store/export in Joules
     private final List<Double> prSrvAvgPow_JperTick = new ArrayList<>();
     private final List<Double> prPartAvgPow_JperTick = new ArrayList<>();
     private String exportEnergyCsvPath = "fl_energy.csv";
     private String exportEnergyPngPath = "fl_energy.png";
     
     // Fallback energy estimator (enabled only if measured delta is zero)
     private boolean energyFallbackEstimator = false;
     // Very rough default coefficients (tune to your environment)
     private double J_PER_BYTE_DL = 1.5e-7;     // Joules per DL byte (server/device side)
     private double J_PER_BYTE_UL = 1.5e-7;     // Joules per UL byte (server/device side)
     private double J_PER_INSTR   = 1.0e-9;     // Joules per instruction (device compute)
     
     // Public toggles for the estimator
     public void enableEnergyFallbackEstimator(boolean on) { this.energyFallbackEstimator = on; }
     public void setEnergyEstimationCoefficients(double jPerByteDL, double jPerByteUL, double jPerInstr) {
         this.J_PER_BYTE_DL = Math.max(0, jPerByteDL);
         this.J_PER_BYTE_UL = Math.max(0, jPerByteUL);
         this.J_PER_INSTR   = Math.max(0, jPerInstr);
     }
     
     // Option to count uplink energy even when an update is dropped in transit.
     // When true, ParameterUploadEvent will still drive a native repo transfer for failed ULs
     // (for energy realism) but will NOT call noteUploadSuccess nor addModelUpdate.
     private boolean energyCountFailedUploads = false;
     
     // Enable/disable counting energy for failed in-transit uploads.
     public void setEnergyCountFailedUploads(boolean on) { this.energyCountFailedUploads = on; }
     // Query flag for failed-upload energy counting.
     boolean isEnergyCountFailedUploads() { return energyCountFailedUploads; }
     
     // Register a callback invoked when the very last FL step finishes.
     public void setFinishedCallback(Runnable r) { 
         this.finishedCallback = r;    
     }     
     
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
         this.name        = id;
         this.geoLocation = null;
         this.iaas        = null;

         this.id = id;
         this.serverRepo = initServerRepo(id);
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
         this.name        = id;
         this.geoLocation = null;
         this.iaas        = null;

         this.id = id;
         this.serverRepo = initServerRepo(id);
         if (initialWeights != null && initialWeights.length > 0) {
             this.globalModel = initialWeights.clone();
         } else {
             this.globalModel = new double[3];
             for (int i = 0; i < globalModel.length; i++) {
                 globalModel[i] = rng.nextGaussian() * DEFAULT_INIT_STD;
             }
         }
     }
     
     // Full server-backed constructor so the aggregator has an IaaS to meter.
     // Loads the IaaSService from {@code cloudfile} directly (replacing the previous
     // {@code super(cloudfile, id, location, locationCost)} call) so the aggregator
     // does not get registered in {@link ComputingAppliance#allComputingAppliances}:
     // it is a server-side process, not a fog node.
     public FLAggregator(String id,
                         String cloudfile,
                         GeoLocation location,
                         int locationCost,
                         double[] initialWeights) {
         this.name        = id;
         this.geoLocation = location;

         IaaSService loaded = null;
         try {
             loaded = CloudLoader.loadNodes(cloudfile);
         } catch (IOException | SAXException | ParserConfigurationException e) {
             e.printStackTrace();
         }
         this.iaas = loaded;

         // Replicates ComputingAppliance#modifyRepoName so the primary repo carries the
         // aggregator id as a suffix and any inbound latency entries get rewritten to the
         // new name. Inlined here because the original method is package-private.
         if (this.iaas != null && this.iaas.repositories != null && !this.iaas.repositories.isEmpty()) {
             Repository primary = this.iaas.repositories.get(0);
             String oldName = primary.getName();
             String newName = oldName + "-" + id;
             primary.setName(newName);
             for (Repository r : this.iaas.repositories) {
                 if (r.getLatencies().get(oldName) != null) {
                     int latency = r.getLatencies().get(oldName);
                     r.getLatencies().remove(oldName);
                     r.addLatencies(newName, latency);
                 }
             }
         }
         // {@code locationCost} (the parent's "range" field) is unused in the aggregator
         // role; kept as a constructor parameter purely for backward compatibility with
         // existing demos that pass {@code 0}.

         this.id = id;
         this.serverRepo = resolveIaaSRepoForAggregator();
         if (initialWeights != null && initialWeights.length > 0) {
             this.globalModel = initialWeights.clone();
         } else {
             this.globalModel = new double[3];
             for (int i = 0; i < globalModel.length; i++) {
                 globalModel[i] = rng.nextGaussian() * DEFAULT_INIT_STD;
             }
         }
     }

     // Convenience overload if caller wants random init
     public FLAggregator(String id,
                         String cloudfile,
                         GeoLocation location,
                         int locationCost) {
         this(id, cloudfile, location, locationCost, null);
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
         roundTimeoutTicks  = Math.max(1, (long)Math.ceil(roundInterval * timeoutRatio)); // cache
         
         // Informative logging
         // Precise policy logging for sampling
         String policyStr;
         if (useFixedKSampling) {
             policyStr = "selPolicy=fixedK(" + this.fixedK + ")";
         } else {
             int estSampled = (int) Math.round(devices.size() * this.samplingFraction);
             int estDropped = (int) Math.round(estSampled * this.dropoutProbability); // approx
             policyStr = "selPolicy=Bernoulli(sf=" + this.samplingFraction
                     + ", estSampled≈" + estSampled
                     + ", estDropped≈" + estDropped + ")";
         }

         System.out.println("Aggregator " + id + ": Round " + round
                 + " — " + policyStr
                 + " | preUF=" + this.preUploadFailureProbability
                 + ", inTransF=" + this.inTransitFailureProbability
                 + " | minCompletionRate=" + this.minCompletionRate
                 + " | participating=" + expectedCount
                 + " | secureAgg=" + secureAggregationEnabled
                 + ", dlCompFactor=" + this.dlCompressionFactor
                 + ", ulCompFactor=" + this.ulCompressionFactor
                 + ", dpNoiseStd=" + dpNoiseStd
                 + " | pacing=" + (fixedCadence ? "Fixed_Cadence" : "Cooldown_After_Finish")
                 + " | broadcastPolicy=" + (broadcastSelectedOnly ? "Participants_Only" : "All_Devices")
                 + " | modelVersion=" + modelVersion + ".");

         // Timeout event handling (relative to round start)
         roundClosed = false;
         // Stash the timeout event so {@link #aggregateModels()} can cancel it when
         // the round closes early via {@code minCompletionRate}, keeping the event
         // queue clean instead of letting a no-op timeout fire later.
         currentRoundTimeout = new RoundTimeoutEvent(roundTimeoutTicks, this, round);

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
         // Capture energy baselines in mJ at the start of the round
         snapshotRoundEnergyBaseline();         
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

         // Cancel the round-timeout event so it doesn't fire as a no-op after early
         // aggregation — but ONLY when we're not inside the timeout's own eventAction.
         // See {@link #handlingTimeout} for the ConcurrentModificationException case.
         if (currentRoundTimeout != null && !handlingTimeout) {
             currentRoundTimeout.cancel();
         }
         currentRoundTimeout = null;

         // Telemetry capture observed count for this round
         int observedThisRound = receivedUpdates;
         
         // Compute Round Duration
         if (roundDurationTicks < 0) {
             int minNeeded = Math.max(1, (int)Math.ceil(expectedUpdates * minCompletionRate));
             if (observedThisRound >= minNeeded && !roundE2E.isEmpty()) {
                 long kth = kthSmallest(roundE2E, minNeeded);
                 roundDurationTicks = Math.min(kth, roundTimeoutTicks);
                 System.out.println("Aggregator " + id + ": roundWallClock computed in aggregateModels() "
                         + "(late set, early-agg) = " + roundDurationTicks + " ticks.");
             } else {
                 roundDurationTicks = roundTimeoutTicks;
                 System.out.println("Aggregator " + id + ": roundWallClock computed in aggregateModels() "
                         + "(timeout path) = " + roundDurationTicks + " ticks.");
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
                 stashRoundEnergy();
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
             stashRoundEnergy();
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
             stashRoundEnergy();
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
         stashRoundEnergy(); 
         scheduleEvaluationEvent();
     }

     /** @return defensive reference to the current global model array. */
     public double[] getGlobalModel() {
         return globalModel;
     }

     /**
      * Replaces the current global model with the provided weights and bumps the
      * {@link #modelVersion}. Intended for hierarchical FL: a higher-tier
      * coordinator periodically reads the global models from several regional
      * aggregators, synthesises a new model (e.g., uniform or weighted average),
      * and pushes the result back into each regional aggregator via this method.
      *
      * The next {@link #broadcastGlobalModel()} (start of the next regional round)
      * will then carry the new {@code modelVersion}, so client-side staleness
      * tracking stays consistent.
      *
      * @param newWeights the new global model; defensively copied (callers may mutate
      *                   their array after the call). Null or empty inputs are no-ops.
      */
     public synchronized void replaceGlobalModel(double[] newWeights) {
         if (newWeights == null || newWeights.length == 0) return;
         this.globalModel = newWeights.clone();
         this.modelVersion++;
         System.out.println("Aggregator " + id + ": global model replaced by external coordinator "
                 + "(dim=" + this.globalModel.length + ", modelVersion=" + modelVersion + ").");
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
         // Round-0 guardrail: broadcast to ALL devices once so every device has a model copy.
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
             // Hand off to the existing orchestrator instead of allocating a new one
             // per round — the orchestrator already owns the round parameters and
             // simply needs to re-arm its Timed subscription.
             if (orchestrator != null) {
                 orchestrator.scheduleNext();
             } else {
                 System.out.println("Aggregator " + id
                         + ": cool-down scheduleNext skipped — no orchestrator registered.");
             }
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
         handlingTimeout = true;
         try {
             int observed = receivedUpdates; // counts updates or secure shares
             String unit = secureAggregationEnabled ? "shares" : "updates";
             System.out.println("Aggregator " + id + ": TIMEOUT for round " + roundId
                     + ". Proceeding with " + observed + "/" + expectedUpdates + " " + unit + ".");
             if (roundDurationTicks < 0) roundDurationTicks = roundTimeoutTicks;
             aggregateModels();
             if (!fixedCadence) {
                 scheduleNextRound();
             }
         } finally {
             handlingTimeout = false;
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
         // Energy snapshot to demonstrate that metering is active
         printEnergySnapshot();
     }
     
     // Human-friendly energy summary (server + participants). Uses same kWh conversion as EnergyDataCollectorFL CSV.
     private void printEnergySnapshot() {
         try {
        	 double srv_mJ = 0.0;
             EnergyDataCollectorFL srv = EnergyDataCollectorFL.getEnergyCollector(this.iaas);
             // forceSample() instead of reading the cache, so the [ENERGY] log line
             // reflects energy actually consumed up to the round's aggregation time
             // (not the last 60 s sampling tick). Idempotent within the same fire
             // count: if stashRoundEnergy already forced a sample this tick, the
             // meter's internal {@code if (now != lastMetered)} guard makes this a
             // cheap no-op that just re-reads the already-updated value.
             if (srv != null) srv_mJ = srv.forceSample();

             double parts_mJ = 0.0;
             int counted = 0;
             List<String> samples = new ArrayList<>();
             for (FLEdgeDevice dev : participantsThisRound) {
                 EnergyDataCollectorFL edc = EnergyDataCollectorFL.getEnergyCollector(dev.getLocalMachine());
                 if (edc != null) {
                	 double live = edc.forceSample();
                	 parts_mJ += live;
                     counted++;
                     if (samples.size() < 3) {
                         double kWh = (live / 1000.0) / 3_600_000.0; //  mJ→J→kWh
                         samples.add(edc.name + "=" + String.format(Locale.US, "%.6f", kWh) + " kWh");
                     }
                 }
             }
             double srvKWh   = (srv_mJ / 1000.0) / 3_600_000.0; // mJ→J→kWh
             double partsKWh = (parts_mJ / 1000.0) / 3_600_000.0;

             System.out.println("Aggregator " + id + " [ENERGY]: "
                     + "server≈" + String.format(Locale.US, "%.6f", srvKWh) + " kWh"
                     + " | participants(" + counted + ")≈" + String.format(Locale.US, "%.6f", partsKWh) + " kWh"
                     + (samples.isEmpty() ? "" : " | examples " + samples));
         } catch (Throwable t) {
             System.out.println("Aggregator " + id + " [ENERGY]: snapshot unavailable (" + t.getMessage() + ")");
         }
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

     // Telemetry plotting moved to {@link FLTelemetry#plotTelemetry(String, String, String)}
     // as part of the LOW-3 extraction. Call site at the bottom of onFinalEvaluationComplete().


     // Allow demos to redirect outputs
     public void setExportPaths(String csvPath, String pngPath) {
         if (csvPath != null && !csvPath.isEmpty()) this.exportCsvPath = csvPath;
         if (pngPath != null && !pngPath.isEmpty()) this.exportPngPath = pngPath;
     }
     
     public void setEnergyExportPaths(String csvPath, String pngPath) {
         if (csvPath != null && !csvPath.isEmpty()) this.exportEnergyCsvPath = csvPath;
         if (pngPath != null && !pngPath.isEmpty()) this.exportEnergyPngPath = pngPath;
     }
     
     // FL-Energy - Initialize a lightweight repository to meter network/storage energy
     private static Repository initServerRepo(String id) {
         java.util.EnumMap<PowerTransitionGenerator.PowerStateKind, java.util.Map<String, PowerState>> tr =
                 PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
         java.util.Map<String, PowerState> diskStates = tr.get(PowerTransitionGenerator.PowerStateKind.storage);
         java.util.Map<String, PowerState> netStates  = tr.get(PowerTransitionGenerator.PowerStateKind.network);
         java.util.HashMap<String, Integer> lat = new java.util.HashMap<>();
         return new Repository(
                 4_294_967_296L,                 // 4 GB
                 "fl-agg-repo-" + id,
                 3_250_000L, 3_250_000L,         // maxInBW, maxOutBW (bytes/tick)
                 3_250_000L,                     // diskBW (bytes/tick)
                 lat,
                 diskStates,
                 netStates
         );
     }
     
     private Repository resolveIaaSRepoForAggregator() {
         try {
             if (this.iaas != null && this.iaas.repositories != null && !this.iaas.repositories.isEmpty()) {
                 String override = System.getProperty("fl.serverRepoId", "").trim();
                 String preferred = override.isEmpty() ? ("ceph-" + this.id) : override;

                 Repository exact = null;
                 for (Repository r : this.iaas.repositories) {
                     String rid = safeRepoName(r);
                     if (preferred.equals(rid)) { exact = r; break; }
                 }
                 if (exact != null) {
                     System.out.println("Aggregator " + id + ": using IaaS repository '" + safeRepoName(exact) + "'.");
                     return exact;
                 }

                 // As a secondary heuristic, try any repo that starts with "ceph-" if available
                 for (Repository r : this.iaas.repositories) {
                     String rid = safeRepoName(r);
                     if (rid != null && rid.startsWith("ceph-")) {
                         System.out.println("Aggregator " + id + ": preferred repo '" + preferred + "' not found; using '" + rid + "'.");
                         return r;
                     }
                 }

                 // Fallback to first
                 Repository first = this.iaas.repositories.get(0);
                 System.out.println("Aggregator " + id + ": preferred repo '" + preferred + "' not found; falling back to first ('" + safeRepoName(first) + "').");
                 return first;
             }
         } catch (Throwable t) {
             System.out.println("Aggregator " + id + ": repository resolution failed (" + t.getMessage() + "), falling back to local repo.");
         }
         // No IaaS: use a local lightweight repo
         return initServerRepo(this.id);
     }

     /**
      * Returns the repository's name. {@link Repository} inherits {@code getName()}
      * from {@code NetworkNode}, so we can bind directly to it; the previous
      * reflection-based lookup was defensive scaffolding for a non-existent API
      * compatibility problem and is now removed.
      */
     private static String safeRepoName(Repository r) {
         if (r == null) return "null";
         String n = r.getName();
         return n != null ? n : String.valueOf(r);
     }

     // FL-Energy 
     public Repository getServerRepository() { 
    	 return serverRepo; 
     }
     
     public String getServerRepositoryId() {
         return safeRepoName(serverRepo);
     }

     // FL-Energy Convenience helpers for DL sizing
     long getModelBytes() { 
    	 return (long) getModelSize() * (long) Double.BYTES; 
    	 }
     long getCompressedModelBytesForDownlink() {
         double f = Math.max(0.0, Math.min(1.0, getDlCompressionFactor()));
         return (long) Math.ceil(getModelBytes() * f);
     }
     
     public void onFinalEvaluationComplete() {        
         try {
             exportTelemetryCsv(exportCsvPath);
         } catch (IOException e) {
             System.out.println("Aggregator " + id + ": Failed to export telemetry CSV: " + e.getMessage());
         }
         FLTelemetry.plotTelemetry(exportCsvPath, exportPngPath, id);
         // Export and plot energy
         try {
             exportEnergyCsv(exportEnergyCsvPath);
         } catch (IOException e) {
             System.out.println("Aggregator " + id + ": Failed to export energy CSV: " + e.getMessage());
         }
         FLTelemetry.plotEnergy(exportEnergyCsvPath, exportEnergyPngPath, id);
         
         // Notify whoever registered that we’re fully done (after export/plot)
         if (finishedCallback != null) {
             try { finishedCallback.run(); }
             catch (Throwable t) {
                 System.out.println("Aggregator " + id + ": finishedCallback threw: " + t);
             }
         }
     }                                 
     
     // Energy helpers
     //
     // Both methods call EnergyDataCollectorFL#forceSample() before reading the
     // consumption value. The cached field on the collector is only refreshed every
     // FREQ_TICKS (default 60 s) — which is much longer than a typical FL round —
     // so reading the cache directly would give baseline == current and a zero delta
     // for almost every round. forceSample() ticks the underlying DISSECT-CF meter
     // at the current sim time, returning a live value for accurate per-round deltas.
     private void snapshotRoundEnergyBaseline() {
         try {
             baseSrv_mJ = 0.0;
             EnergyDataCollectorFL srv = EnergyDataCollectorFL.getEnergyCollector(this.iaas);
             if (srv != null) baseSrv_mJ = srv.forceSample();

             baseParts_mJ = 0.0;
             for (FLEdgeDevice dev : participantsThisRound) {
                 EnergyDataCollectorFL edc = EnergyDataCollectorFL.getEnergyCollector(dev.getLocalMachine());
                 if (edc != null) baseParts_mJ += edc.forceSample();
             }
         } catch (Throwable t) {
             baseSrv_mJ = 0.0;
             baseParts_mJ = 0.0;
         }
     }

     private void stashRoundEnergy() {
         try {
        	 double curSrv_mJ = 0.0;
             EnergyDataCollectorFL srv = EnergyDataCollectorFL.getEnergyCollector(this.iaas);
             if (srv != null) curSrv_mJ = srv.forceSample();

             double curParts_mJ = 0.0;
             for (FLEdgeDevice dev : participantsThisRound) {
                 EnergyDataCollectorFL edc = EnergyDataCollectorFL.getEnergyCollector(dev.getLocalMachine());
                 if (edc != null) curParts_mJ += edc.forceSample();
             }

             double dSrv_J = Math.max(0.0, (curSrv_mJ - baseSrv_mJ) / 1000.0);
             double dPar_J = Math.max(0.0, (curParts_mJ - baseParts_mJ) / 1000.0);

             double durTicks = Math.max(0.0, (double) Math.max(0, roundDurationTicks));
             double pSrv_Jpt = (durTicks > 0.0) ? (dSrv_J / durTicks) : 0.0;
             double pPar_Jpt = (durTicks > 0.0) ? (dPar_J / durTicks) : 0.0;

             // Fallback estimator if both are 0 (likely due to coarse sampling window)
             if (energyFallbackEstimator && dSrv_J == 0.0 && dPar_J == 0.0 && roundDurationTicks > 0) {
                 double estSrv = estimateServerEnergy_J();
                 double estPar = estimateParticipantsEnergy_J();
                 System.out.println("Aggregator " + id + " [ENERGY ESTIMATOR]: replacing 0 J with "
                         + String.format(Locale.US, "server≈%.6f J, participants≈%.6f J", estSrv, estPar));
                 dSrv_J = estSrv;
                 dPar_J = estPar;
                 pSrv_Jpt = dSrv_J / roundDurationTicks;
                 pPar_Jpt = dPar_J / roundDurationTicks;
             }

             prSrvEnergy_J.add(dSrv_J);
             prPartEnergy_J.add(dPar_J);
             prSrvAvgPow_JperTick.add(pSrv_Jpt);
             prPartAvgPow_JperTick.add(pPar_Jpt);
         } catch (Throwable t) {
             prSrvEnergy_J.add(0.0);
             prPartEnergy_J.add(0.0);
             prSrvAvgPow_JperTick.add(0.0);
             prPartAvgPow_JperTick.add(0.0);
         }
     }
     
     // Server estimate from bytes
     private double estimateServerEnergy_J() {
         // All DL bytes counted on server NIC; UL bytes (model + sec).
         double dlJ = J_PER_BYTE_DL * (double) roundDownBytes;
         double ulJ = J_PER_BYTE_UL * (double) (roundUpModelBytes + roundUpSecOverheadBytes);
         return Math.max(0.0, dlJ + ulJ);
     }
     
     // Participants estimate from compute + their DL/UL bytes
     private double estimateParticipantsEnergy_J() {
         double computeJ = 0.0;
         double dlJ = 0.0;
         double ulJ = 0.0;

         // Compute energy: sum over participating devices
         double epoch = GlobalModelBroadcastEvent.getEpochMultiplier();
         for (FLEdgeDevice dev : participantsThisRound) {
             double instr = epoch * dev.getInstructionPerByte() * (double) dev.getFileSize();
             computeJ += instr * J_PER_INSTR;
         }
         // Attribute DL bytes to participants only (reasonable for participants-only broadcast).
         dlJ = J_PER_BYTE_DL * (double) roundDownBytes;

         // UL bytes are only from participants
         ulJ = J_PER_BYTE_UL * (double) (roundUpModelBytes + roundUpSecOverheadBytes);

         return Math.max(0.0, computeJ + dlJ + ulJ);
     }
     
     public synchronized void exportEnergyCsv(String path) throws IOException {
         int rows = prRoundId.size();
         try (PrintWriter pw = new PrintWriter(
                 new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
        	 pw.println("round,server_joules,participants_joules,round_duration_ticks,"
                     + "server_avg_power_J_per_tick,participants_avg_power_J_per_tick");
             for (int r = 0; r < rows; r++) {
                 double srvJ  = (r < prSrvEnergy_J.size()) ? prSrvEnergy_J.get(r) : 0.0;
                 double parJ  = (r < prPartEnergy_J.size()) ? prPartEnergy_J.get(r) : 0.0;
                 long   dur   = (r < prRoundDuration.size()) ? prRoundDuration.get(r) : 0L;
                 double pSrv  = (r < prSrvAvgPow_JperTick.size()) ? prSrvAvgPow_JperTick.get(r) : 0.0;
                 double pPar  = (r < prPartAvgPow_JperTick.size()) ? prPartAvgPow_JperTick.get(r) : 0.0;

                 pw.println(
                         prRoundId.get(r) + ","
                         + String.format(Locale.US, "%.6f", srvJ) + ","
                         + String.format(Locale.US, "%.6f", parJ) + ","
                         + dur + ","
                         + String.format(Locale.US, "%.9f", pSrv) + ","
                         + String.format(Locale.US, "%.9f", pPar)
                 );
             }
         }
         System.out.println("Aggregator " + id + ": Energy CSV exported to " + path);
     }
     
     // Energy plotting moved to {@link FLTelemetry#plotEnergy(String, String, String)}
     // as part of the LOW-3 extraction.
 }