package hu.u_szeged.inf.fog.simulator.fl.cosim;

import hu.u_szeged.inf.fog.simulator.fl.FLLearningSource;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Track-A learning source: serves Pass-2 outcomes and signatures from a
 * {@link TraceReader} (§8.4 Pass 3). The trace is authoritative for Pass-3
 * physics (read-first, D4).
 */
public final class TraceProvider implements LearningProvider {

    private final TraceReader reader;

    public TraceProvider(TraceReader reader) {
        this.reader = reader;
    }

    /** The underlying reader (for topology validation and signature access). */
    public TraceReader reader() {
        return reader;
    }

    @Override
    public FLLearningSource source() {
        return FLLearningSource.TRACE;
    }

    @Override
    public LearningOutcome outcome(int node, int round) {
        LearningOutcome o = reader.metrics(node, round);
        if (o == null) {
            throw new IllegalStateException("no trace metrics for node=" + node + " round=" + round);
        }
        return o;
    }

    @Override
    public float[] signature(int node, int round) {
        try {
            return reader.signature(node, round);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
