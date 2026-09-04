package hu.u_szeged.inf.fog.simulator.fl.cosim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a Pass-2 learning trace (§8.4 Pass 2 output / Pass 3 input) and exposes
 * the per-{@code (node, round)} metrics and model signatures to the Pass-3
 * replay. The trace contract is dependency-free CSV + a raw float32 sidecar
 * (no Parquet):
 *
 * <ul>
 *   <li>{@code learning_trace.csv} — one row per {@code (node, round)}, columns:
 *       {@code schema_version, scenario_id, topology_hash, policy_id, merge_rule,
 *       gamma_schedule, seed, node, round, acc, loss, delta_norm, payload_bytes,
 *       train_time_ms, n_samples, peers, merge_weights, staleness_max}. {@code peers}
 *       is {@code ;}-joined ascending ids; {@code merge_weights} is {@code ;}-joined
 *       and aligned with {@code [self, peers…]} (self first).</li>
 *   <li>{@code signatures.bin} — little-endian float32, layout
 *       {@code offset(node, round) = (node*T + round) * d} floats (Python writes
 *       it with {@code numpy.tofile}).</li>
 *   <li>{@code signatures_index.json} — {@code {dtype, d, n, rounds, node_order}}.</li>
 * </ul>
 *
 * <p>{@link #validateAgainst(FLTopology)} enforces the schema version and that
 * the trace's {@code topology_hash} matches the live topology — so a trace can
 * never be replayed against the wrong graph.</p>
 */
public final class TraceReader {

    /** Supported trace schema version. */
    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final String scenarioId;
    private final String topologyHash;
    private final String policyId;
    private final String mergeRule;
    private final String gammaSchedule;
    private final long seed;

    private final Map<Long, LearningOutcome> metrics = new HashMap<>();
    private int maxNode = -1;
    private int maxRound = -1;

    // Signature sidecar.
    private final int sigDim;
    private final int sigN;
    private final int sigRounds;
    private final Path signaturesBin;

    private TraceReader(int schemaVersion, String scenarioId, String topologyHash, String policyId,
                        String mergeRule, String gammaSchedule, long seed,
                        int sigDim, int sigN, int sigRounds, Path signaturesBin) {
        this.schemaVersion = schemaVersion;
        this.scenarioId = scenarioId;
        this.topologyHash = topologyHash;
        this.policyId = policyId;
        this.mergeRule = mergeRule;
        this.gammaSchedule = gammaSchedule;
        this.seed = seed;
        this.sigDim = sigDim;
        this.sigN = sigN;
        this.sigRounds = sigRounds;
        this.signaturesBin = signaturesBin;
    }

    private static long key(int node, int round) {
        return ((long) node << 32) | (round & 0xFFFFFFFFL);
    }

    /**
     * Loads a trace directory containing {@code learning_trace.csv},
     * {@code signatures.bin}, and {@code signatures_index.json}.
     *
     * @param traceDir directory holding the three artefacts.
     * @return a reader over the trace.
     */
    public static TraceReader load(Path traceDir) throws IOException {
        Path csv = traceDir.resolve("learning_trace.csv");
        Path idxJson = traceDir.resolve("signatures_index.json");
        Path bin = traceDir.resolve("signatures.bin");

        // --- signatures_index.json ---
        ObjectMapper mapper = new ObjectMapper();
        JsonNode idx = mapper.readTree(idxJson.toFile());
        String dtype = idx.get("dtype").asText();
        if (!"float32".equals(dtype)) {
            throw new IOException("unsupported signature dtype: " + dtype + " (expected float32)");
        }
        int d = idx.get("d").asInt();
        int n = idx.get("n").asInt();
        int rounds = idx.get("rounds").asInt();

        // --- learning_trace.csv header + rows ---
        List<String> header = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String line = r.readLine();
            if (line == null) {
                throw new IOException("empty learning_trace.csv");
            }
            for (String h : line.split(",", -1)) {
                header.add(h);
            }
            String row;
            while ((row = r.readLine()) != null) {
                if (row.isEmpty()) {
                    continue;
                }
                rows.add(row.split(",", -1));
            }
        }
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i), i);
        }
        requireColumns(col, "schema_version", "scenario_id", "topology_hash", "policy_id",
                "merge_rule", "gamma_schedule", "seed", "node", "round", "acc", "heldout_acc",
                "loss", "delta_norm", "payload_bytes", "train_time_ms", "n_samples", "peers",
                "merge_weights", "staleness_max");

        // Header-level metadata (taken from the first row; identical across rows).
        if (rows.isEmpty()) {
            throw new IOException("learning_trace.csv has no data rows");
        }
        String[] first = rows.get(0);
        int schemaVersion = Integer.parseInt(first[col.get("schema_version")]);
        String scenarioId = first[col.get("scenario_id")];
        String topologyHash = first[col.get("topology_hash")];
        String policyId = first[col.get("policy_id")];
        String mergeRule = first[col.get("merge_rule")];
        String gammaSchedule = first[col.get("gamma_schedule")];
        long seed = Long.parseLong(first[col.get("seed")]);

        TraceReader reader = new TraceReader(schemaVersion, scenarioId, topologyHash, policyId,
                mergeRule, gammaSchedule, seed, d, n, rounds, bin);

        for (String[] f : rows) {
            int node = Integer.parseInt(f[col.get("node")]);
            int round = Integer.parseInt(f[col.get("round")]);
            LearningOutcome o = new LearningOutcome(
                    parseD(f[col.get("acc")]),
                    parseD(f[col.get("heldout_acc")]),
                    parseD(f[col.get("loss")]),
                    parseD(f[col.get("delta_norm")]),
                    Long.parseLong(f[col.get("payload_bytes")]),
                    parseD(f[col.get("train_time_ms")]),
                    Integer.parseInt(f[col.get("n_samples")]),
                    parseIntList(f[col.get("peers")]),
                    parseDoubleList(f[col.get("merge_weights")]),
                    Integer.parseInt(f[col.get("staleness_max")]));
            reader.metrics.put(key(node, round), o);
            reader.maxNode = Math.max(reader.maxNode, node);
            reader.maxRound = Math.max(reader.maxRound, round);
        }
        return reader;
    }

    /**
     * Validates the trace against the live topology: schema version and the
     * topology hash must match, else replay would run against the wrong graph.
     *
     * @throws IllegalStateException on mismatch.
     */
    public void validateAgainst(FLTopology topology) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException("trace schema_version " + schemaVersion
                    + " != supported " + SCHEMA_VERSION);
        }
        String live = topology.topologyHash();
        if (!live.equals(topologyHash)) {
            throw new IllegalStateException("trace topology_hash (" + topologyHash
                    + ") does not match the live topology (" + live + ")");
        }
        if (topology.size() != sigN) {
            throw new IllegalStateException("trace node count " + sigN
                    + " != topology size " + topology.size());
        }
    }

    /** Per-(node, round) learning metrics; null if absent. */
    public LearningOutcome metrics(int node, int round) {
        return metrics.get(key(node, round));
    }

    /**
     * The model signature for {@code (node, round)} read from the float32
     * sidecar at offset {@code (node*rounds + round) * d}.
     */
    public float[] signature(int node, int round) throws IOException {
        long offsetFloats = ((long) node * sigRounds + round) * sigDim;
        long byteOffset = offsetFloats * Float.BYTES;
        float[] out = new float[sigDim];
        try (FileChannel ch = FileChannel.open(signaturesBin, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(sigDim * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            int read = ch.read(buf, byteOffset);
            if (read < sigDim * Float.BYTES) {
                throw new IOException("signature out of range for node=" + node + " round=" + round);
            }
            buf.flip();
            for (int i = 0; i < sigDim; i++) {
                out[i] = buf.getFloat();
            }
        }
        return out;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String scenarioId() {
        return scenarioId;
    }

    public String topologyHash() {
        return topologyHash;
    }

    public String policyId() {
        return policyId;
    }

    public String mergeRule() {
        return mergeRule;
    }

    public String gammaSchedule() {
        return gammaSchedule;
    }

    public long seed() {
        return seed;
    }

    public int signatureDim() {
        return sigDim;
    }

    public int nodeCount() {
        return sigN;
    }

    public int rounds() {
        return sigRounds;
    }

    // ------------------------------------------------------------------
    // Parsing helpers.
    // ------------------------------------------------------------------

    private static double parseD(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        // Python's repr() emits lower-case nan/inf, which Double.parseDouble
        // rejects (it wants NaN/Infinity). Columns measured on a cadence — e.g.
        // heldout_acc between evaluation rounds — legitimately carry NaN.
        String t = s.trim();
        if (t.equalsIgnoreCase("nan")) {
            return Double.NaN;
        }
        if (t.equalsIgnoreCase("inf") || t.equalsIgnoreCase("infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (t.equalsIgnoreCase("-inf") || t.equalsIgnoreCase("-infinity")) {
            return Double.NEGATIVE_INFINITY;
        }
        return Double.parseDouble(t);
    }

    private static int[] parseIntList(String s) {
        if (s == null || s.isEmpty()) {
            return new int[0];
        }
        String[] parts = s.split(";");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }

    private static double[] parseDoubleList(String s) {
        if (s == null || s.isEmpty()) {
            return new double[0];
        }
        String[] parts = s.split(";");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }

    private static void requireColumns(Map<String, Integer> col, String... names) throws IOException {
        for (String n : names) {
            if (!col.containsKey(n)) {
                throw new IOException("learning_trace.csv missing column: " + n);
            }
        }
    }
}
