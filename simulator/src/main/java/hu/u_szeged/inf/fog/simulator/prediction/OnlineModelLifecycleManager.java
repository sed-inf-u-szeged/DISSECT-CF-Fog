package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Schedules an arbitrary number of independently trained model families. */
public final class OnlineModelLifecycleManager {
    private static final OnlineModelLifecycleManager INSTANCE =
            new OnlineModelLifecycleManager(OnlineModelCatalog.builtIn());

    private final OnlineModelCatalog catalog;
    private List<LstmOnlineLifecycleController> controllers = List.of();

    public OnlineModelLifecycleManager(OnlineModelCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("An online model catalog is required.");
        }
        this.catalog = catalog;
        configure("ALL");
    }

    public static OnlineModelLifecycleManager getInstance() {
        return INSTANCE;
    }

    /** Model selection is optional; a normal Run uses every registered model. */
    public synchronized void configure(String selection) {
        List<OnlineModelAdapter> selected = catalog.select(selection);
        for (LstmOnlineLifecycleController controller : controllers) {
            if (controller.hasStarted()) {
                throw new IllegalStateException(
                        "Online models must be configured before data collection starts.");
            }
        }
        List<LstmOnlineLifecycleController> configured = new ArrayList<>();
        for (OnlineModelAdapter adapter : selected) {
            configured.add(new LstmOnlineLifecycleController(
                    adapter, OnlineTrainingSettings.getDefault(), true));
        }
        controllers = List.copyOf(configured);
    }

    public List<LstmOnlineLifecycleController> getControllers() {
        return controllers;
    }

    public List<String> getModelTypes() {
        return controllers.stream().map(LstmOnlineLifecycleController::getModelType)
                .collect(Collectors.toUnmodifiableList());
    }

    public boolean isManagedModelType(String modelType) {
        return catalog.contains(modelType);
    }

    /** Training runs sequentially while simulation time is paused. */
    public void checkLifecycle() throws Exception {
        for (LstmOnlineLifecycleController controller : controllers) {
            controller.checkLifecycle();
        }
    }

    public void finish() throws Exception {
        Exception failure = null;
        for (LstmOnlineLifecycleController controller : controllers) {
            try {
                controller.finish();
            } catch (Exception exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
