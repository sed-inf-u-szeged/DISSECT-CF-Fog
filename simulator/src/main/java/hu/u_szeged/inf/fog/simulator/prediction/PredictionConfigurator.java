package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.communication.ServerSocket;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.ElectronLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.Launcher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.simulation.SimulationSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PredictionConfigurator {
    public static boolean PREDICTION_ENABLED = false;
    private List<Launcher> applications;
    //private boolean running;
    private ISimulation simulation;
    private boolean applicationsEnabled;

    public PredictionConfigurator(ISimulation simulation) {
        this.simulation = simulation;
        //this.running = true;
        this.applicationsEnabled = true;
        this.applications = new ArrayList<>();
        PredictionConfigurator.PREDICTION_ENABLED = true;
    }

    public void disableApplications() {
        this.applicationsEnabled = false;
    }

    public void addApplications(Launcher... applications) {
        for (Launcher application: applications) {
            this.applications.add(application);
        }
    }

    public void startSocket() {
        ServerSocket.getInstance().start();
        ServerSocket.getInstance().waitForConnections(applications);
        ServerSocket.getInstance().waitForPredictionSettings();
    }

    public void openApplications() {
        for (Launcher application: applications) {
            application.open();
        }
    }

    public void simulate() throws Exception {
        if (applicationsEnabled) {
            openApplications();
        }

        startSocket();

        if (Launcher.hasApplication(ElectronLauncher.class.getSimpleName())) { // TODO different
            try {
                simulation.simulation();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                simulation.simulation();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        export();
        ServerSocket.getInstance().stopThreads();
        SimulationSettings.get().printInfo();
        FeatureManager.getInstance().printInfo();
        PredictionLogger.info("simulate", "Simulation has ended!");
    }

    public void export() throws IOException {
        if (SimulationSettings.get().getExport().canExportDataset()) {
            FeatureManager.getInstance().exportDatasetToCsv(SimulationSettings.get().getExport().getLocation(), Utils.getFileNameWithDate("dataset", "csv"));
        }

        if (SimulationSettings.get().getExport().canExportPredictions()) {
            FeatureManager.getInstance().exportPredictionsToCsv(SimulationSettings.get().getExport().getLocation(), Utils.getFileNameWithDate("predictions", "csv"));
        }

        if (SimulationSettings.get().getExport().canExportMetrics()) {
            FeatureManager.getInstance().exportErrorMetricsToCsv(SimulationSettings.get().getExport().getLocation(), Utils.getFileNameWithDate("error_metrics", "csv"));
        }

        if (SimulationSettings.get().getExport().canExportPredictionSettings()) {
            SimulationSettings.get().exportToJSON(SimulationSettings.get().getExport().getLocation(), Utils.getFileNameWithDate("simulation_settings", "json"));
        }
    }

    public void addSimulationSettings(SimulationSettings simulationSettings) {
        SimulationSettings.set(simulationSettings);
    }

    public interface ISimulation {
        void simulation() throws Exception;
    }
}
