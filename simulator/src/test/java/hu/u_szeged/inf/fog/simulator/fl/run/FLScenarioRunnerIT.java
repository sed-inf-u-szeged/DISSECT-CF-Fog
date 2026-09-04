package hu.u_szeged.inf.fog.simulator.fl.run;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P6 DoD (end-to-end half): one full S1-shaped decentralized cell runs
 * Pass 1 → Pass 2 → Pass 3 from a single command, producing the released
 * artefact tree; a second run resumes (skips the completed cell). Skipped when
 * the Python harness is unavailable.
 */
class FLScenarioRunnerIT {

    private static Path harnessDir() {
        return Paths.get("..", "harness").toAbsolutePath().normalize();
    }

    private static boolean pythonAvailable() {
        try {
            Process p = new ProcessBuilder("py", "-3.12", "-c", "import torch, numpy")
                    .redirectErrorStream(true).start();
            return p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("S1_smoke: one cell runs Pass1→Pass2→Pass3 end-to-end; rerun resumes")
    void oneCellEndToEnd(@TempDir Path out) throws Exception {
        Assumptions.assumeTrue(pythonAvailable(), "Python harness unavailable — skipping runner IT");

        Path yaml = Paths.get("..", "scenarios", "S1_smoke.yaml").toAbsolutePath().normalize();
        Map<String, Object> spec = FLScenarioRunner.loadYaml(yaml);
        FLScenarioRunner runner = new FLScenarioRunner(spec, "py", true, harnessDir());

        List<FLScenarioRunner.Run> runs = runner.expandRuns(out, "smoke");
        org.junit.jupiter.api.Assertions.assertEquals(1, runs.size());
        FLScenarioRunner.Run run = runs.get(0);

        runner.runCell(run);

        // Released artefact tree: pass1 / pass2 / pass3.
        assertTrue(Files.exists(run.dir.resolve("pass1/system_trace.json")), "pass1 system_trace");
        assertTrue(Files.exists(run.dir.resolve("pass2/learning_trace.csv")), "pass2 learning_trace");
        assertTrue(Files.exists(run.dir.resolve("pass2/signatures.bin")), "pass2 signatures");
        for (String f : new String[] { "run_metadata.json", "per_exchange.csv", "idle_time.csv",
                "gossip_round.csv", "tid_timeseries.csv" }) {
            assertTrue(Files.exists(run.dir.resolve("pass3").resolve(f)), "pass3 " + f);
        }

        // Resume: a second runCell on the completed cell is skipped (no exception,
        // run_metadata unchanged).
        long mtimeBefore = Files.getLastModifiedTime(run.dir.resolve("pass3/run_metadata.json")).toMillis();
        runner.runCell(run);
        long mtimeAfter = Files.getLastModifiedTime(run.dir.resolve("pass3/run_metadata.json")).toMillis();
        org.junit.jupiter.api.Assertions.assertEquals(mtimeBefore, mtimeAfter,
                "resume must skip the completed cell (run_metadata untouched)");
    }
}
