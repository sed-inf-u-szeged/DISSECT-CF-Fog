package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobilityUiLauncherTest {
    @TempDir Path temp;

    @Test void pinsExactRunAndKeepsPathsWithSpacesOutOfShellCommands() {
        Path ui = temp.resolve("project with spaces/ui");
        Path run = temp.resolve("run with spaces/mobility-42");
        ProcessBuilder windows = MobilityUiLauncher.createProcess(ui, run, "electron:run", true);
        assertEquals(List.of("cmd.exe", "/d", "/c", "npm.cmd", "run", "electron:run"), windows.command());
        assertEquals(ui.toFile(), windows.directory());
        assertEquals(run.toAbsolutePath().normalize().toString(), windows.environment().get("DISSECT_MOBILITY_RUN_DIR"));
        assertFalse(windows.environment().containsKey("DISSECT_DATABASE_PATH"));
        assertEquals(run.resolve("electron.log").toFile(), windows.redirectOutput().file());
        assertEquals(List.of("npm", "run", "electron:run"), MobilityUiLauncher.createProcess(ui, run, "electron:run", false).command());
    }

    @Test void rebuildsMissingOrStaleFrontendButReusesFreshBuild() throws Exception {
        assertTrue(MobilityUiLauncher.needsBuild(temp));
        Files.createDirectories(temp.resolve("dist/dissect-cf-predictor-ui"));
        Files.createDirectories(temp.resolve("src"));
        for (String file : List.of("angular.json", "package.json", "package-lock.json", "tsconfig.json", "tsconfig.app.json", "src/main.ts")) {
            Path p = Files.writeString(temp.resolve(file), "test");
            Files.setLastModifiedTime(p, FileTime.fromMillis(1000));
        }
        Path index = Files.writeString(temp.resolve("dist/dissect-cf-predictor-ui/index.html"), "test");
        Files.setLastModifiedTime(index, FileTime.fromMillis(2000));
        assertFalse(MobilityUiLauncher.needsBuild(temp));
        Files.setLastModifiedTime(temp.resolve("src/main.ts"), FileTime.fromMillis(3000));
        assertTrue(MobilityUiLauncher.needsBuild(temp));
    }
}
