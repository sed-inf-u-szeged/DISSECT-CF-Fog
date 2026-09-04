package hu.u_szeged.inf.fog.simulator.fl.cosim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.fl.topology.TopologyFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P3.2 DoD: the Pass-2 trace round-trips through {@link TraceReader} (metrics +
 * float32 signature sidecar), and a topology-hash mismatch is rejected. The
 * fixture is written here in the exact wire format the Python {@code trace_writer}
 * emits (CSV + little-endian float32 + index JSON), so the reader is validated
 * independently of Python.
 */
class TraceReaderTest {

    private static final int N = 6;
    private static final int ROUNDS = 3;
    private static final int D = 4;

    /** Writes a fixture trace matching the contract; returns the live topology it was built for. */
    private FLTopology writeFixture(Path dir, String topologyHash) throws IOException {
        Files.createDirectories(dir);

        // learning_trace.csv
        StringBuilder csv = new StringBuilder();
        csv.append("schema_version,scenario_id,topology_hash,policy_id,merge_rule,gamma_schedule,")
           .append("seed,node,round,acc,heldout_acc,loss,delta_norm,payload_bytes,train_time_ms,")
           .append("n_samples,peers,merge_weights,staleness_max\n");
        for (int node = 0; node < N; node++) {
            for (int round = 0; round < ROUNDS; round++) {
                double acc = 0.5 + 0.01 * (node * ROUNDS + round);
                csv.append(1).append(',')
                   .append("S_fixture").append(',')
                   .append(topologyHash).append(',')
                   .append("COMPOSITE").append(',')
                   .append("DRIFT_SUPPRESSED").append(',')
                   .append("EXPLORE_THEN_EXPLOIT").append(',')
                   .append(42).append(',')
                   .append(node).append(',')
                   .append(round).append(',')
                   .append(acc).append(',')
                   // heldout_acc: the reportable figure, distinct from the
                   // local-shard `acc`; lower-case "nan" on off-cadence rounds
                   // exercises the reader's Python-repr tolerance.
                   .append(round == 0 ? "nan" : String.valueOf(acc - 0.05)).append(',')
                   .append(0.1).append(',')
                   .append(1.5).append(',')
                   .append(4000).append(',')
                   .append(12.5).append(',')
                   .append(100 + node).append(',')
                   .append("1;5").append(',')
                   .append("0.5;0.25;0.25").append(',')
                   .append(round).append('\n');
            }
        }
        Files.write(dir.resolve("learning_trace.csv"), csv.toString().getBytes(StandardCharsets.UTF_8));

        // signatures.bin : float32 LE, offset (node*ROUNDS+round)*D
        ByteBuffer buf = ByteBuffer.allocate(N * ROUNDS * D * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int node = 0; node < N; node++) {
            for (int round = 0; round < ROUNDS; round++) {
                for (int k = 0; k < D; k++) {
                    buf.putFloat((float) (node * 100 + round * 10 + k));
                }
            }
        }
        Files.write(dir.resolve("signatures.bin"), buf.array());

        // signatures_index.json
        String idx = "{\"dtype\":\"float32\",\"d\":" + D + ",\"n\":" + N + ",\"rounds\":" + ROUNDS
                + ",\"node_order\":[0,1,2,3,4,5]}";
        Files.write(dir.resolve("signatures_index.json"), idx.getBytes(StandardCharsets.UTF_8));

        return null;
    }

    @Test
    @DisplayName("trace round-trips: metrics + signature sidecar")
    void roundTrip(@TempDir Path dir) throws IOException {
        FLTopology topo = TopologyFactory.clustered(new int[] { 3, 3 }, 2);
        writeFixture(dir, topo.topologyHash());

        TraceReader reader = TraceReader.load(dir);
        reader.validateAgainst(topo); // must not throw

        assertEquals(1, reader.schemaVersion());
        assertEquals("S_fixture", reader.scenarioId());
        assertEquals("COMPOSITE", reader.policyId());
        assertEquals(42L, reader.seed());
        assertEquals(D, reader.signatureDim());
        assertEquals(N, reader.nodeCount());
        assertEquals(ROUNDS, reader.rounds());

        LearningOutcome o = reader.metrics(2, 1);
        assertEquals(0.5 + 0.01 * (2 * ROUNDS + 1), o.acc, 1e-9);
        // The held-out column is carried separately from the local-shard one …
        assertEquals(0.5 + 0.01 * (2 * ROUNDS + 1) - 0.05, o.heldoutAcc, 1e-9);
        // … and an off-cadence round parses Python's lower-case "nan".
        assertTrue(Double.isNaN(reader.metrics(2, 0).heldoutAcc),
                "off-cadence heldout_acc must round-trip as NaN");
        assertEquals(4000L, o.payloadBytes);
        assertEquals(102, o.nSamples);
        assertArrayEquals(new int[] { 1, 5 }, o.peers);
        assertArrayEquals(new double[] { 0.5, 0.25, 0.25 }, o.mergeWeights, 1e-12);
        assertEquals(1, o.stalenessMax);

        float[] sig = reader.signature(2, 1);
        assertArrayEquals(new float[] { 210f, 211f, 212f, 213f }, sig, 0f);
        // node 0, round 0 = {0,1,2,3}
        assertArrayEquals(new float[] { 0f, 1f, 2f, 3f }, reader.signature(0, 0), 0f);
        // last cell node 5 round 2 = {520,521,522,523}
        assertArrayEquals(new float[] { 520f, 521f, 522f, 523f }, reader.signature(5, 2), 0f);
    }

    @Test
    @DisplayName("topology-hash mismatch is rejected")
    void rejectsHashMismatch(@TempDir Path dir) throws IOException {
        writeFixture(dir, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef0");
        TraceReader reader = TraceReader.load(dir);
        FLTopology topo = TopologyFactory.clustered(new int[] { 3, 3 }, 2);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> reader.validateAgainst(topo));
        assertEquals(true, ex.getMessage().contains("topology_hash"));
    }
}
