package hu.u_szeged.inf.fog.simulator.fl.cosim;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology.Edge;
import hu.u_szeged.inf.fog.simulator.fl.topology.Lambda2;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes (and reads back) the Pass-1 {@code system_trace.json} artefact
 * (§8.4 Pass 1). This is the topology-and-costs exporter that
 * {@link FLTopology} produces and the Python harness consumes; it is the
 * realisation of the {@code exportJson} responsibility sketched on
 * {@code FLTopology}, kept in the cosim package so the topology package stays
 * jackson-free and there is no {@code topology → cosim} cycle. The full
 * scenario/device-driven Pass-1 writer (P3.1) will build on this.
 *
 * <h2>Determinism</h2>
 * Output is stable across JVMs and OSes: properties are sorted alphabetically
 * ({@link MapperFeature#SORT_PROPERTIES_ALPHABETICALLY}) and the pretty-printer
 * uses a fixed {@code "\n"} line separator (not the platform default), so the
 * serialised bytes do not depend on the host. The committed sample fixture is
 * compared to fresh output (normalised for {@code \r}) by
 * {@code SystemTraceRoundTripTest}.
 */
public final class SystemTraceWriter {

    private SystemTraceWriter() {
    }

    private static ObjectWriter writer() {
        ObjectMapper mapper = new ObjectMapper();
        // Stable, JVM-independent property order.
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        // Fixed LF line separator so serialised bytes are host-independent.
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        return mapper.writer(pp);
    }

    private static ObjectMapper reader() {
        return new ObjectMapper();
    }

    /**
     * Assembles a {@link SystemTrace} from a topology plus the surrounding
     * envelope. Edges, λ₂, and the topology hash are read from {@code topo} (on
     * its current active state — callers pass an all-active topology); the
     * dynamic schedule and the optional λ̄₂ / union-λ₂ metadata are passed in
     * because they depend on the round count / pLink the scenario chose.
     *
     * @param scenarioId scenario/cell id.
     * @param seed campaign seed.
     * @param topo the (all-active) topology.
     * @param topoType type tag (e.g. {@code "ring"}, {@code "scale_free"}, {@code "dynamic"}).
     * @param params generator params (e.g. {@code {"m":2}}); may be null.
     * @param nodes node metadata (id, profile, compute, geo, cluster).
     * @param hyper hyperparameter block.
     * @param model model descriptor.
     * @param loadProfiles per-node load profiles (node-id string -> per-round fractions); may be null.
     * @param lambda2Expected λ̄₂ for dynamic graphs, or null for static.
     * @param lambda2Union union-graph λ₂ for dynamic graphs, or null.
     * @param dynamicSchedule per-round inactive-edge schedule, or null for static.
     * @return the assembled trace.
     */
    public static SystemTrace build(String scenarioId, long seed, FLTopology topo,
                                    String topoType, Map<String, Object> params,
                                    List<SystemTrace.NodeJson> nodes,
                                    SystemTrace.Hyper hyper, SystemTrace.Model model,
                                    Map<String, double[]> loadProfiles,
                                    Double lambda2Expected, Double lambda2Union,
                                    List<SystemTrace.DynamicRoundJson> dynamicSchedule) {
        SystemTrace t = new SystemTrace();
        t.schemaVersion = 1;
        t.scenarioId = scenarioId;
        t.seed = seed;
        t.n = topo.size();

        SystemTrace.TopologyJson tj = new SystemTrace.TopologyJson();
        tj.type = topoType;
        tj.params = params;
        tj.hash = topo.topologyHash();
        tj.nodes = nodes;
        tj.edges = new ArrayList<>();
        for (Edge e : topo.edgesAscending()) {
            tj.edges.add(new SystemTrace.EdgeJson(e.u, e.v, e.latencyTicks, e.bandwidthBytesPerTick));
        }
        tj.lambda2 = Lambda2.lambda2(topo);
        tj.lambda2Expected = lambda2Expected;
        tj.lambda2Union = lambda2Union;
        tj.dynamicSchedule = dynamicSchedule;
        t.topology = tj;

        t.loadProfiles = loadProfiles;
        t.hyper = hyper;
        t.model = model;
        return t;
    }

    /**
     * Builds the per-round dynamic schedule (inactive edges per round) for a
     * dynamic topology, using {@link FLTopology#applyDynamicRound}. Restores the
     * topology to all-active on return.
     *
     * @param topo the dynamic topology.
     * @param seed campaign seed.
     * @param pLink per-round link-up probability.
     * @param rounds number of rounds to schedule.
     * @return one {@link SystemTrace.DynamicRoundJson} per round.
     */
    public static List<SystemTrace.DynamicRoundJson> buildDynamicSchedule(
            FLTopology topo, long seed, double pLink, int rounds) {
        List<SystemTrace.DynamicRoundJson> schedule = new ArrayList<>();
        try {
            for (int r = 0; r < rounds; r++) {
                topo.applyDynamicRound(r, seed, pLink);
                List<int[]> inactive = topo.inactiveEdges();
                int[][] arr = inactive.toArray(new int[0][]);
                schedule.add(new SystemTrace.DynamicRoundJson(r, arr));
            }
        } finally {
            topo.resetActive();
        }
        return schedule;
    }

    /** Serialises a trace to a deterministic JSON string (LF newlines). */
    public static String toJson(SystemTrace trace) throws IOException {
        return writer().writeValueAsString(trace);
    }

    /** Writes a trace to {@code out} as UTF-8 JSON with a trailing newline. */
    public static void write(Path out, SystemTrace trace) throws IOException {
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        String json = toJson(trace) + "\n";
        Files.write(out, json.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads a trace back from a JSON file. */
    public static SystemTrace read(Path in) throws IOException {
        return reader().readValue(in.toFile(), SystemTrace.class);
    }

    /** Parses a trace from a JSON string (test convenience). */
    public static SystemTrace fromJson(String json) throws IOException {
        return reader().readValue(json, SystemTrace.class);
    }
}
