package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import hu.u_szeged.inf.fog.simulator.util.ProjectPathResolver;
import java.io.File;

/**
 * Starts a Python trainer that accepts TrainSettings as one JSON argument.
 */
public class PythonTrainLauncher extends Launcher {

    private final String trainerScript;
    private final String logFileName;
    private final TrainSettings settings;

    public PythonTrainLauncher(String trainerScript, String logFileName,
            TrainSettings settings) {
        if (trainerScript == null || trainerScript.isBlank()) {
            throw new IllegalArgumentException("Trainer script must not be blank.");
        }
        if (logFileName == null || logFileName.isBlank()) {
            throw new IllegalArgumentException("Training log name must not be blank.");
        }
        if (settings == null) {
            throw new IllegalArgumentException("Training settings must not be null.");
        }
        this.trainerScript = trainerScript;
        this.logFileName = logFileName;
        this.settings = settings;
    }

    @Override
    public String getProjectLocation() {
        return ProjectPathResolver.getPredictorScriptsDirectory().toString();
    }

    @Override
    public Process openWindows() throws Exception {
        return openTrainer(resolvePythonExecutable(
                "venv" + File.separator + "Scripts"
                        + File.separator + "python.exe",
                "venv" + File.separator + "python.exe"), true);
    }

    @Override
    public Process openLinux() throws Exception {
        return openTrainer(resolvePythonExecutable(
                "venv" + File.separator + "bin"
                        + File.separator + "python"), false);
    }

    private Process openTrainer(String pythonExecutable,
            boolean windows) throws Exception {
        File projectDirectory = new File(getProjectLocation());
        String relativeScript = trainerScript.replace('/', File.separatorChar);
        File script = new File(projectDirectory, relativeScript);

        if (!script.isFile()) {
            throw new IllegalStateException(
                    "Trainer script was not found: " + script.getAbsolutePath());
        }

        File trainingLog = new File(settings.getModelOutputLocation(), logFileName);
        java.nio.file.Files.createDirectories(trainingLog.toPath().toAbsolutePath().getParent());
        String settingsJson = JsonParser.toJson(
                settings, TrainSettings.class).toString();

        ProcessBuilder processBuilder;
        if (windows) {
            String relativePython = projectDirectory.toPath()
                    .toAbsolutePath().normalize()
                    .relativize(new File(pythonExecutable).toPath()
                            .toAbsolutePath().normalize())
                    .toString();

            String command = String.format("%s %s \"%s\"",
                    relativePython, relativeScript,
                    settingsJson.replace("\"", "\\\""));

            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder(
                    pythonExecutable, script.getAbsolutePath(), settingsJson);
        }

        processBuilder.directory(projectDirectory);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(trainingLog);

        PredictionLogger.info("Python trainer", "Opening " + trainerScript);
        return processBuilder.start();
    }

    private String resolvePythonExecutable(String... relativePaths) {
        for (String relativePath : relativePaths) {
            File candidate = new File(getProjectLocation(), relativePath);
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        throw new IllegalStateException(
                "Python executable was not found. Checked paths: "
                        + String.join(", ", relativePaths));
    }
}