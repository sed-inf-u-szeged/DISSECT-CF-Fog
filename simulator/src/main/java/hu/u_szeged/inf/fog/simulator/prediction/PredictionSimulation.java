package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.communication.ServerSocket;
import hu.u_szeged.inf.fog.simulator.prediction.communication.applications.ElectronApplication;
import hu.u_szeged.inf.fog.simulator.prediction.communication.applications.IApplication;
import hu.u_szeged.inf.fog.simulator.prediction.settings.simulation.SimulationSettings;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PredictionSimulation {
    public static boolean PREDICTION_ENABLED = false;
    private List<IApplication> applications;
    private boolean running;
    private ISimulation simulation;
    private boolean applicationsEnabled;

    public PredictionSimulation(ISimulation simulation) {
        this.simulation = simulation;
        this.running = true;
        this.applicationsEnabled = true;
        this.applications = new ArrayList<>();
        PredictionSimulation.PREDICTION_ENABLED = true;
    }

    public void disableApplications() {
        this.applicationsEnabled = false;
    }

    public void addApplications(IApplication... applications) {
        for (IApplication application: applications) {
            this.applications.add(application);
        }
    }

    public void startSocket() {
        ServerSocket.getInstance().start();
        ServerSocket.getInstance().waitForConnections(applications);
        ServerSocket.getInstance().waitForPredictionSettings();
    }

    public void openApplications() {
        for (IApplication application: applications) {
            application.open();
        }
    }

    public void simulate() throws Exception {
        if (applicationsEnabled) {
            openApplications();
        }

        startSocket();

        if (IApplication.hasApplication(ElectronApplication.class.getSimpleName())) { // TODO different
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
            FeatureManager.getInstance().exportDatasetToCSV(SimulationSettings.get().getExport().getLocation(), Utils.getFileNameWithDate("dataset", "csv"));
        }

        if (SimulationSettings.get().getExport().canExportPredictions()) {
            //FeatureManager.getInstance().exportPredictionsToCSV(SimulationSettings.get().getExport().getLocation(), Utils.getFileNameWithDate("predictions", "csv"))
        }

        if (SimulationSettings.get().getExport().canExportMetrics()) {
            FeatureManager.getInstance().exportMetricsToCSV(SimulationSettings.get().getExport().getLocation(), Utils.getFileNameWithDate("error_metrics", "csv"));
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
