package hu.u_szeged.inf.fog.simulator.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves project directories independently from the current working
 * directory and operating system.
 */
public final class ProjectPathResolver {

    private static final String PROJECT_ROOT_ENVIRONMENT_VARIABLE =
            "DISSECT_PROJECT_ROOT";

    private static final String MODEL_STORE_ENVIRONMENT_VARIABLE =
            "DISSECT_MODEL_STORE";

    private ProjectPathResolver() {
    }

    /**
     * Returns the DISSECT-CF-Fog repository root.
     *
     * @return absolute normalized project root
     */
    public static Path getProjectRoot() {
        String configuredRoot = System.getenv(
                PROJECT_ROOT_ENVIRONMENT_VARIABLE
        );

        if (configuredRoot != null
                && !configuredRoot.trim().isEmpty()) {
            Path configuredPath = Path.of(
                    configuredRoot.trim()
            ).toAbsolutePath().normalize();

            validateProjectRoot(configuredPath);
            return configuredPath;
        }

        Path candidate = Path.of(
                System.getProperty("user.dir")
        ).toAbsolutePath().normalize();

        while (candidate != null) {
            if (isProjectRoot(candidate)) {
                return candidate;
            }

            candidate = candidate.getParent();
        }

        throw new IllegalStateException(
                "DISSECT-CF-Fog project root could not be found"
        );
    }

    /**
     * Returns the simulator module directory.
     *
     * @return simulator directory
     */
    public static Path getSimulatorDirectory() {
        return getProjectRoot()
                .resolve("simulator")
                .normalize();
    }

    /**
     * Returns the Python predictor scripts directory.
     *
     * @return predictor scripts directory
     */
    public static Path getPredictorScriptsDirectory() {
        return getProjectRoot()
                .resolve("predictor-ui")
                .resolve("scripts")
                .normalize();
    }

    /**
     * Returns the Electron UI directory.
     *
     * @return predictor UI directory
     */
    public static Path getPredictorUiDirectory() {
        return getProjectRoot()
                .resolve("predictor-ui")
                .resolve("ui")
                .normalize();
    }

    /**
     * Returns the configured model store directory.
     *
     * @return model store directory
     */
    public static Path getModelStoreDirectory() {
        String configuredStore = System.getenv(
                MODEL_STORE_ENVIRONMENT_VARIABLE
        );

        if (configuredStore != null
                && !configuredStore.trim().isEmpty()) {
            Path configuredPath = Path.of(
                    configuredStore.trim()
            );

            if (configuredPath.isAbsolute()) {
                return configuredPath.normalize();
            }

            return getPredictorScriptsDirectory()
                    .resolve(configuredPath)
                    .normalize();
        }

        return getProjectRoot()
                .resolve("predictor-ui")
                .resolve("model_store")
                .normalize();
    }

    private static boolean isProjectRoot(Path candidate) {
        return Files.isRegularFile(
                candidate.resolve("simulator").resolve("pom.xml")
        ) && Files.isDirectory(
                candidate.resolve("predictor-ui")
        );
    }

    private static void validateProjectRoot(Path projectRoot) {
        if (!isProjectRoot(projectRoot)) {
            throw new IllegalStateException(
                    "Invalid DISSECT_PROJECT_ROOT: " + projectRoot
            );
        }
    }
}