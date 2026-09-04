package hu.u_szeged.inf.fog.simulator.fl.cosim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * POJO tree for the Pass-1 {@code system_trace.json} artefact (§8.4 Pass 1;
 * schema documented in docs/REPRODUCIBILITY.md). Jackson maps these public
 * fields by name; snake_case keys in the schema are pinned with
 * {@link JsonProperty}. Nullable metadata (e.g. {@code lambda2_expected} for a
 * static graph) is omitted from output via {@link JsonInclude}.
 *
 * <p>This is the system pre-run export consumed by the Python learning harness
 * (Pass 2): the topology, per-link cost tables, per-round dynamic schedule, and
 * per-node load profiles. The matching Python loader is
 * {@code harness/system_trace.py}; the round-trip is locked by
 * {@code SystemTraceRoundTripTest} (Java) and {@code test_system_trace_loader.py}
 * (pytest) against a committed sample fixture.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SystemTrace {

    @JsonProperty("schema_version")
    public int schemaVersion = 1;

    @JsonProperty("scenario_id")
    public String scenarioId;

    public long seed;
    public int n;

    public TopologyJson topology;

    /** node-id (as string) -> per-round background-load fractions. */
    @JsonProperty("load_profiles")
    public Map<String, double[]> loadProfiles;

    @JsonProperty("cost_tables_note")
    public String costTablesNote = "L,B derivable from edges; exported flat for convenience";

    public Hyper hyper;
    public Model model;

    public SystemTrace() {
    }

    /** The {@code topology} sub-object. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class TopologyJson {
        public String type;
        /** Generator params, e.g. {@code {"m":2}} for scale-free. */
        public Map<String, Object> params;
        public String hash;
        public List<NodeJson> nodes;
        public List<EdgeJson> edges;
        public double lambda2;

        @JsonProperty("lambda2_expected")
        public Double lambda2Expected;

        @JsonProperty("lambda2_union")
        public Double lambda2Union;

        @JsonProperty("dynamic_schedule")
        public List<DynamicRoundJson> dynamicSchedule;

        public TopologyJson() {
        }
    }

    /** A node descriptor in {@code topology.nodes}. */
    public static final class NodeJson {
        public int id;
        public String profile;
        public int cores;
        public double mips;
        public double ramGB;
        public GeoJson geo;
        public int cluster;

        public NodeJson() {
        }

        /** All-fields constructor (jackson uses the no-arg one). */
        public NodeJson(int id, String profile, int cores, double mips, double ramGB,
                        GeoJson geo, int cluster) {
            this.id = id;
            this.profile = profile;
            this.cores = cores;
            this.mips = mips;
            this.ramGB = ramGB;
            this.geo = geo;
            this.cluster = cluster;
        }
    }

    /** A lat/lon pair. */
    public static final class GeoJson {
        public double lat;
        public double lon;

        public GeoJson() {
        }

        public GeoJson(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** A cost-annotated edge in {@code topology.edges} (canonical {@code u<v}). */
    public static final class EdgeJson {
        public int u;
        public int v;
        public long latencyTicks;
        public long bandwidthBytesPerTick;

        public EdgeJson() {
        }

        /** All-fields constructor (jackson uses the no-arg one). */
        public EdgeJson(int u, int v, long latencyTicks, long bandwidthBytesPerTick) {
            this.u = u;
            this.v = v;
            this.latencyTicks = latencyTicks;
            this.bandwidthBytesPerTick = bandwidthBytesPerTick;
        }
    }

    /** One round's inactive edges in {@code topology.dynamic_schedule}. */
    public static final class DynamicRoundJson {
        public int round;

        @JsonProperty("inactive_edges")
        public int[][] inactiveEdges;

        public DynamicRoundJson() {
        }

        public DynamicRoundJson(int round, int[][] inactiveEdges) {
            this.round = round;
            this.inactiveEdges = inactiveEdges;
        }
    }

    /** Training/gossip hyperparameters block. */
    public static final class Hyper {
        public int k;
        public int rounds;
        public int localEpochs;
        public String gammaSchedule;
        public String mergeRule;
        public String policy;
        public double dirichletRho;
        public int signatureDim;
        public long signatureSeed;

        public Hyper() {
        }
    }

    /** Model descriptor block (payload bytes drive every traffic/energy figure). */
    public static final class Model {
        public String name;
        public long paramCount;
        public long payloadBytesFloat32;

        public Model() {
        }
    }
}
