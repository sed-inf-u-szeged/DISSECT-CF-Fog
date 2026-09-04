package hu.u_szeged.inf.fog.simulator.fl.cosim.trackb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Track-B online round-synchronous coupling (§8.4; design decision D3: files
 * over gRPC, for debuggability — the simulator owns virtual time, so blocking
 * the Java thread on the file handshake while real training runs is safe).
 *
 * <p>Per round the connector publishes a request and blocks until the worker
 * publishes a response:</p>
 * <pre>
 *   bridge/&lt;runId&gt;/round_0007/
 *     request.json     written by Java
 *     request.READY    empty marker written last (atomic publish)
 *     response.json    written by the worker
 *     signatures_0007.bin
 *     response.READY   empty marker written last
 * </pre>
 *
 * <p><b>Idempotent</b>: if a round's {@code response.READY} already exists (e.g.
 * the worker was restarted and re-served), the connector reads it directly
 * instead of re-publishing the request. The worker's wall-clock is never used
 * to advance the simulator — only the returned learning outcomes are consumed;
 * timing and energy remain analytic (Track A model).</p>
 */
public final class FLRealTrainingConnector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long POLL_MS = 100L;

    private final Path bridgeDir;
    private final long timeoutMs;

    /**
     * Creates a connector rooted at the given bridge directory.
     *
     * @param bridgeDir the per-run bridge directory ({@code bridge/<runId>}).
     * @param timeoutMs wall-clock timeout per round (abort with a diagnostic on expiry).
     */
    public FLRealTrainingConnector(Path bridgeDir, long timeoutMs) {
        this.bridgeDir = bridgeDir;
        this.timeoutMs = timeoutMs;
    }

    private Path roundDir(int round) {
        return bridgeDir.resolve(String.format("round_%04d", round));
    }

    /**
     * Publishes {@code request} for {@code round} and blocks until the worker's
     * response is ready, then parses and returns it. Idempotent across worker
     * restarts.
     *
     * @throws IOException on I/O failure.
     * @throws IllegalStateException on wall-clock timeout.
     */
    public RoundResponse runRound(int round, RoundRequest request) throws IOException {
        Path dir = roundDir(round);
        Files.createDirectories(dir);
        Path responseReady = dir.resolve("response.READY");

        if (!Files.exists(responseReady)) {
            publishRequest(dir, request);
            waitFor(responseReady);
        }
        return MAPPER.readValue(dir.resolve("response.json").toFile(), RoundResponse.class);
    }

    private void publishRequest(Path dir, RoundRequest request) throws IOException {
        Path reqJson = dir.resolve("request.json");
        // Write the payload first, then the READY marker last (atomic publish).
        if (!Files.exists(dir.resolve("request.READY"))) {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(reqJson.toFile(), request);
            Files.write(dir.resolve("request.READY"), new byte[0]);
        }
    }

    private void waitFor(Path responseReady) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!Files.exists(responseReady)) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(
                        "Track-B handshake timed out after " + timeoutMs + " ms waiting for " + responseReady);
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for Track-B response", e);
            }
        }
    }

    /**
     * Reads a node's model signature from a round's signatures sidecar
     * (little-endian float32, layout {@code node * d}).
     */
    public float[] signature(int round, int node, int d) throws IOException {
        RoundResponse resp = MAPPER.readValue(roundDir(round).resolve("response.json").toFile(),
                RoundResponse.class);
        Path bin = roundDir(round).resolve(resp.signaturesFile);
        float[] out = new float[d];
        try (FileChannel ch = FileChannel.open(bin, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(d * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            ch.read(buf, (long) node * d * Float.BYTES);
            buf.flip();
            for (int i = 0; i < d; i++) {
                out[i] = buf.getFloat();
            }
        }
        return out;
    }

    /** Writes a shutdown sentinel so a long-running worker can exit cleanly. */
    public void signalDone(int totalRounds) throws IOException {
        Files.createDirectories(bridgeDir);
        Files.write(bridgeDir.resolve("DONE"),
                Integer.toString(totalRounds).getBytes(StandardCharsets.UTF_8));
    }

    public Path bridgeDir() {
        return bridgeDir;
    }
}
