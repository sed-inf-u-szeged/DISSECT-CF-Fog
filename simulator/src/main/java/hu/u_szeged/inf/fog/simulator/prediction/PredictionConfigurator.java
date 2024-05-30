package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.communication.ServerSocket;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.Launcher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code PredictionConfigurator} class is responsible for configuring and managing
 * the simulation using time series analysis, including starting necessary launchers,
 * handling socket communication, and exporting simulation results.
 */
public class PredictionConfigurator {
    
    /**
     * Indicates whether the prediction functionality is enabled.
     */
    public static boolean PREDICTION_ENABLED = false;
    private List<Launcher> launchers;
    private SimulationDefinition simulationDefinition;

    /**
     * Constructs a new {@code PredictionConfigurator} with the specified simulation definition.
     *
     * @param simulation the simulation definition to be used
     */
    public PredictionConfigurator(SimulationDefinition simulation) {
        this.simulationDefinition = simulation;
        this.launchers = new ArrayList<>();
        PredictionConfigurator.PREDICTION_ENABLED = true;
    }

    /**
     * Adds one or more {@code Launcher} applications to the list of applications to be managed.
     *
     * @param launchers the launchers to add
     */
    public void addLaunchers(Launcher... launchers) {
        for (Launcher application : launchers) {
            this.launchers.add(application);
        }
    }

    /**
     * Starts the server socket and waits for connections and prediction settings.
     */
    public void startSocket() {
        ServerSocket.getInstance().start();
        ServerSocket.getInstance().waitForConnections(launchers);
        ServerSocket.getInstance().waitForPredictionSettings();
    }

    /**
     * Executes the prediction simulation by opening applications, starting the socket, 
     * running the simulation, exporting results, stopping threads, and printing information.
     */
    public void execute() throws Exception {
        for (Launcher application : launchers) {
            application.open();
        }

        startSocket();
        simulationDefinition.simulation();
        export();
        
        ServerSocket.getInstance().stopThreads();
        SimulationSettings.get().printInfo();
        FeatureManager.getInstance().printInfo();
        PredictionLogger.info("simulate", "Simulation has ended!");
    }

    /**
     * Exports simulation results to various formats based on the simulation settings.
     */
    public void export() throws IOException {
        if (SimulationSettings.get().getExport().canExportDataset()) {
            FeatureManager.getInstance().exportDatasetToCsv(SimulationSettings.get()
                    .getExport().getLocation(), Utils.getFileNameWithDate("dataset", "csv"));
        }

        if (SimulationSettings.get().getExport().canExportPredictions()) {
            FeatureManager.getInstance().exportPredictionsToCsv(SimulationSettings.get()
                    .getExport().getLocation(), Utils.getFileNameWithDate("predictions", "csv"));
        }

        if (SimulationSettings.get().getExport().canExportMetrics()) {
            FeatureManager.getInstance().exportErrorMetricsToCsv(SimulationSettings.get()
                    .getExport().getLocation(), Utils.getFileNameWithDate("error_metrics", "csv"));
        }

        if (SimulationSettings.get().getExport().canExportPredictionSettings()) {
            SimulationSettings.get().exportToJSON(SimulationSettings.get()
                    .getExport().getLocation(), Utils.getFileNameWithDate("simulation_settings", "json"));
        }
    }

    public void addSimulationSettings(SimulationSettings simulationSettings) {
        SimulationSettings.set(simulationSettings);
    }

    /**
     * The class implementing this interface must contain 
     * the definition of the IoT, Fog and Cloud-related
     * entities involved in the simulation.
     */
    public interface SimulationDefinition {
        
        /**
         * The method to override that defines the simulated entities. 
         * Ensure to close the Timed.simulateUntilLastEvent() instruction 
         * to begin the simulation.
         */
        void simulation() throws Exception;
    }
}