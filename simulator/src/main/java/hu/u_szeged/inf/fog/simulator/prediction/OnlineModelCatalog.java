package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The single Java registration point for models trained by the online lifecycle.
 * Register an adapter only after its Python trainer and prediction route exist.
 */
public final class OnlineModelCatalog {
    private final Map<String, OnlineModelAdapter> adapters = new LinkedHashMap<>();

    public OnlineModelCatalog(Collection<OnlineModelAdapter> registered) {
        if (registered == null || registered.isEmpty()) {
            throw new IllegalArgumentException("At least one online model is required.");
        }
        Set<String> predictorNames = new LinkedHashSet<>();
        for (OnlineModelAdapter adapter : registered) {
            if (adapter == null || adapter.getModelType() == null) {
                throw new IllegalArgumentException("Online model adapters must have a model type.");
            }
            String type = normalize(adapter.getModelType());
            if (!type.equals(adapter.getModelType()) || !type.matches("[A-Z][A-Z0-9_]*")
                    || "ALL".equals(type) || "BOTH".equals(type)) {
                throw new IllegalArgumentException("Invalid or reserved model type: " + type);
            }
            if (adapters.putIfAbsent(type, adapter) != null) {
                throw new IllegalArgumentException("Duplicate online model type: " + type);
            }
            String predictorName = adapter.getVersionName(1);
            if (predictorName == null || !predictorName.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")
                    || !predictorNames.add(predictorName)) {
                throw new IllegalArgumentException(
                        "Each adapter needs a safe, distinct predictor name: " + predictorName);
            }
        }
    }

    /** Add future implemented model adapters here; the scheduler and UI are generic. */
    public static OnlineModelCatalog builtIn() {
        return new OnlineModelCatalog(List.of(
                new LstmModelAdapter(),
                new TcnModelAdapter(),
                new GruModelAdapter(),
                new PatchTstModelAdapter()
        ));
    }

    public List<String> getModelTypes() {
        return List.copyOf(adapters.keySet());
    }

    public boolean contains(String type) {
        return type != null && adapters.containsKey(normalize(type));
    }

    /** ALL is the default. Explicit lists are optional experiment filters. */
    public List<OnlineModelAdapter> select(String selection) {
        String requested = selection == null ? "ALL" : normalize(selection);
        if ("ALL".equals(requested)) {
            return List.copyOf(adapters.values());
        }
        // Compatibility with the previous two-model command, not a scheduler mode.
        if ("BOTH".equals(requested)) {
            requested = "LSTM,TCN";
        }
        Set<String> names = new LinkedHashSet<>();
        for (String item : requested.split(",", -1)) {
            String name = normalize(item);
            if (!adapters.containsKey(name)) {
                throw new IllegalArgumentException("Unknown online model: " + name
                        + ". Available: " + String.join(", ", adapters.keySet()));
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("Duplicate model selection: " + name);
            }
        }
        // Registration order is stable, independent of command-line ordering.
        List<OnlineModelAdapter> selected = new ArrayList<>();
        for (Map.Entry<String, OnlineModelAdapter> entry : adapters.entrySet()) {
            if (names.contains(entry.getKey())) {
                selected.add(entry.getValue());
            }
        }
        return List.copyOf(selected);
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
