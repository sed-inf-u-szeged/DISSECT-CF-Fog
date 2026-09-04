package hu.u_szeged.inf.fog.simulator.fl.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The runner expands each §9 scenario file into exactly the {@code cell × seed}
 * grid the paper reports, and never invents cells. Pure / no compute.
 *
 * <p>These counts are the pre-registration: if a scenario file and the reported
 * grid disagree, the campaign is not reproducible from the released setup, so
 * the expected numbers are pinned here rather than left implicit.</p>
 */
class FLScenarioRunnerTest {

    private static FLScenarioRunner runner(String yaml) throws Exception {
        Path p = Paths.get("..", "scenarios", yaml).toAbsolutePath().normalize();
        Map<String, Object> spec = FLScenarioRunner.loadYaml(p);
        return new FLScenarioRunner(spec, "py", true, Paths.get("..", "harness"));
    }

    @Test
    @DisplayName("V: the fidelity anchor is a single cell at a single seed")
    void vExpansion() throws Exception {
        FLScenarioRunner r = runner("V.yaml");
        List<FLScenarioRunner.Run> runs = r.expandRuns(Paths.get("results"), "paper");
        assertEquals(1, runs.size());
        assertEquals(11L, runs.get(0).seed);
        assertTrue(runs.get(0).dir.toString().replace('\\', '/').contains("/V/"));
    }

    @Test
    @DisplayName("S1 (ERQ2): 1 cell × 5 seeds, with all three coordination legs")
    void s1Expansion() throws Exception {
        FLScenarioRunner r = runner("S1.yaml");
        assertEquals(1, r.expandCells().size(), "S1 varies the coordination leg, not a cell factor");
        List<FLScenarioRunner.Run> runs = r.expandRuns(Paths.get("results"), "paper");
        assertEquals(5, runs.size(), "5 seeds");
        assertEquals(5, runs.stream().map(run -> run.seed).distinct().count());
        // The baseline legs must be declared, else there is no A_cen for the TID
        // gap and ERQ2 has nothing to compare against.
        assertEquals(List.of("decentralized", "centralized", "hierarchical"), r.modes());
    }

    @Test
    @DisplayName("S2 (ERQ3): 6 topology families × 5 seeds = 30 runs")
    void s2Expansion() throws Exception {
        FLScenarioRunner r = runner("S2.yaml");
        List<FLScenarioRunner.Cell> cells = r.expandCells();
        assertEquals(6, cells.size(), "ring, mesh, scale-free, dynamic + 2 small-world β");
        assertEquals(30, r.expandRuns(Paths.get("results"), "paper").size());
        for (FLScenarioRunner.Cell c : cells) {
            assertTrue(c.cellId.startsWith("topo="), "S2 cellId encodes the topology: " + c.cellId);
        }
        // ws_beta applies only to the small-world group; the other families must
        // not carry a "…=null" token in their artefact path.
        assertTrue(cells.stream().anyMatch(c -> c.cellId.equals("topo=ring")));
        assertTrue(cells.stream().anyMatch(c -> c.cellId.equals("topo=small_world_deg=6")));
        assertTrue(cells.stream().anyMatch(c -> c.cellId.equals("topo=small_world_deg=14")));
        assertTrue(cells.stream().noneMatch(c -> c.cellId.contains("null")),
                "a group-scoped factor must not leak a null token into the cell id");
    }

    @Test
    @DisplayName("S3 (ERQ4): 2 selectors × 4 merge rules = 8 cells × 5 seeds = 40 runs")
    void s3Expansion() throws Exception {
        FLScenarioRunner r = runner("S3.yaml");
        List<FLScenarioRunner.Cell> cells = r.expandCells();
        assertEquals(8, cells.size());
        assertEquals(40, r.expandRuns(Paths.get("results"), "paper").size());
        // SAMPLE_WEIGHTED is the control that isolates the drift factor; without
        // it the uniform-vs-drift-suppressed contrast confounds two mechanisms.
        assertTrue(cells.stream().anyMatch(c -> c.cellId.contains("mrg=SAMPLE_WEIGHTED")),
                "S3 must include the sample-weighted control");
        assertEquals(8, cells.stream().map(c -> c.cellId).distinct().count());
    }

    @Test
    @DisplayName("S4 (ERQ5): the seven §8.2 selectors × 5 seeds = 35 runs")
    void s4Expansion() throws Exception {
        FLScenarioRunner r = runner("S4.yaml");
        List<FLScenarioRunner.Cell> cells = r.expandCells();
        assertEquals(7, cells.size(), "composite + 4 single-factor + degree + random");
        assertEquals(35, r.expandRuns(Paths.get("results"), "paper").size());
        for (String expected : new String[] { "COMPOSITE", "SINGLE_LATENCY", "SINGLE_LOAD",
                "SINGLE_DIVERGENCE", "SINGLE_BANDWIDTH", "DEGREE", "RANDOM" }) {
            assertTrue(cells.stream().anyMatch(c -> c.cellId.equals("pol=" + expected)),
                    "missing selector cell: " + expected);
        }
    }

    @Test
    @DisplayName("S1_smoke expands to a single decentralized cell × 1 seed")
    void smokeExpansion() throws Exception {
        FLScenarioRunner r = runner("S1_smoke.yaml");
        List<FLScenarioRunner.Run> runs = r.expandRuns(Paths.get("results"), "smoke");
        assertEquals(1, runs.size());
        assertEquals(42L, runs.get(0).seed);
        String dirs = runs.stream().map(run -> run.dir.toString()).collect(Collectors.joining());
        assertTrue(dirs.replace('\\', '/').contains("/S1_smoke/"));
    }

    @Test
    @DisplayName("a scenario without the decentralized leg is rejected")
    void decentralizedLegIsMandatory() {
        Map<String, Object> spec = Map.of("scenario", "bad", "modes", List.of("centralized"));
        // Pass 3 replays the decentralized leg's trace; a scenario that omits it
        // would produce no system results at all, so fail at construction rather
        // than halfway through a campaign.
        assertThrows(IllegalArgumentException.class,
                () -> new FLScenarioRunner(spec, "py", true, Paths.get("..", "harness")));
    }
}
