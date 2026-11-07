package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.util.ProjectPathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/** Opens the existing Predictor UI for exactly one completed mobility run. */
public final class MobilityUiLauncher {
    private MobilityUiLauncher() { }

    public static Process open(Path resultDirectory) throws IOException {
        Path ui = ProjectPathResolver.getPredictorUiDirectory();
        if (!Files.isDirectory(ui.resolve("node_modules"))) {
            throw new IOException("UI dependencies are missing. Run npm ci in " + ui);
        }
        String script = needsBuild(ui) ? "electron:build_and_run" : "electron:run";
        ProcessBuilder process = createProcess(ui, resultDirectory, script,
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));
        System.out.println("Opening Predictor UI for: " + resultDirectory);
        System.out.println("UI log: " + resultDirectory.resolve("electron.log"));
        return process.start();
    }

    static ProcessBuilder createProcess(Path ui, Path resultDirectory, String script, boolean windows) {
        ProcessBuilder process = windows
                ? new ProcessBuilder("cmd.exe", "/d", "/c", "npm.cmd", "run", script)
                : new ProcessBuilder("npm", "run", script);
        process.directory(ui.toFile());
        process.environment().put("DISSECT_MOBILITY_RUN_DIR", resultDirectory.toAbsolutePath().normalize().toString());
        process.environment().remove("DISSECT_DATABASE_PATH");
        process.redirectErrorStream(true);
        process.redirectOutput(resultDirectory.resolve("electron.log").toFile());
        return process;
    }

    static boolean needsBuild(Path ui) throws IOException {
        Path index = ui.resolve("dist/dissect-cf-predictor-ui/index.html");
        if (!Files.isRegularFile(index)) { return true; }
        long built = Files.getLastModifiedTime(index).toMillis();
        for (String file : new String[] {"angular.json", "package.json", "package-lock.json", "tsconfig.json", "tsconfig.app.json"}) {
            if (Files.getLastModifiedTime(ui.resolve(file)).toMillis() > built) { return true; }
        }
        try (Stream<Path> sources = Files.walk(ui.resolve("src"))) {
            for (Path source : (Iterable<Path>) sources.filter(Files::isRegularFile)::iterator) {
                if (Files.getLastModifiedTime(source).toMillis() > built) { return true; }
            }
        }
        return false;
    }
}
