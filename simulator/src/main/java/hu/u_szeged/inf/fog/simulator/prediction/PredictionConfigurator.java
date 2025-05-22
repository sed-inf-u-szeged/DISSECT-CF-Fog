package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.ElectronLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.LSTMTrainLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.Launcher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;

import java.io.*;
import java.util.*;

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
    public static boolean CREATE_DATABASE = false;
    public static SqLiteManager sqLiteManager;
    public static Map<String, Process> predictor;
    public static Map<String, BufferedWriter> predictor_writer;
    public static Map<String, BufferedReader> predictor_reader;
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
        predictor = new HashMap<>();
        predictor_reader = new HashMap<>();
        predictor_writer = new HashMap<>();
        PredictionConfigurator.PREDICTION_ENABLED = true;
        PredictionConfigurator.CREATE_DATABASE = true;
        sqLiteManager = new SqLiteManager();
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

    public static void addPredictorProcess(String name, Process predictorProcess) {
        predictor.put(name, predictorProcess);
        predictor_reader.put(name, new BufferedReader(new InputStreamReader(predictorProcess.getInputStream()), 8192 * 8));
        predictor_writer.put(name, new BufferedWriter(new OutputStreamWriter(predictorProcess.getOutputStream()), 8192 * 8));
    }

    /**
     * Executes the prediction simulation by opening applications, starting the socket, 
     * running the simulation, exporting results, stopping threads, and printing information.
     */
    public void execute() throws Exception {
        for (Launcher application : launchers) {
            application.open();

            if (application.getClass() == ElectronLauncher.class) {
                PredictionConfigurator.CREATE_DATABASE = true;
                SqLiteManager.setEnabled(true);
            }
        }

        sqLiteManager.setUpDatabase();

        simulationDefinition.simulation();

        for (BufferedReader reader : predictor_reader.values())
            reader.close();
        for (BufferedWriter writer : predictor_writer.values())
            writer.close();

        export();

        if (SimulationSettings.get().getTrainSettings() != null) {
            Launcher lstmTrainLauncher = new LSTMTrainLauncher();
            lstmTrainLauncher.open();
        }

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
            SimulationSettings.get().exportToJson(SimulationSettings.get()
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