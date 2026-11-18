package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleState;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.ShadowPrediction;
import hu.u_szeged.inf.fog.simulator.prediction.settings.LifecycleSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingEvaluationIsolationTest {

    private LstmOnlineLifecycleController controller;
    private LifecycleManager manager;
    private Class<?> versionClass;
    private Object oldVersion;
    private Object newVersion;
    private Feature nodeA;
    private LifecycleCoordinator oldCoordinator;

    private SimulationSettings previousSettings;
    private SqLiteManager previousDatabase;
    private Map<String, BufferedReader> previousReaders;
    private Map<String, BufferedWriter> previousWriters;
    private List<Feature> previousFeatures;

    @BeforeEach
    void setUp() throws Exception {
        previousSettings = SimulationSettings.get();
        previousDatabase = PredictionConfigurator.sqLiteManager;
        previousReaders = PredictionConfigurator.predictor_reader;
        previousWriters = PredictionConfigurator.predictor_writer;
        previousFeatures = new ArrayList<>(
                FeatureManager.getInstance().getFeatures());

        SimulationSettings settings = new SimulationSettings();
        settings.setPredictionSettings(new ArrayList<>());
        settings.setLifecycleSettings(
                new LifecycleSettings(true, 1, 0, 0, 0, 0, 100, 0));
        SimulationSettings.set(settings);

        PredictionConfigurator.sqLiteManager = null;
        PredictionConfigurator.predictor_reader = new HashMap<>();
        PredictionConfigurator.predictor_writer = new HashMap<>();
        FeatureManager.getInstance().getFeatures().clear();

        manager = LifecycleManager.getInstance();
        manager.reset();

        Constructor<LstmOnlineLifecycleController> constructor =
                LstmOnlineLifecycleController.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        controller = constructor.newInstance();
        versionClass = Class.forName(
                LstmOnlineLifecycleController.class.getName() + "$ModelVersion");

        setField(controller, "trainingWindowPoints", 4);
        setField(controller, "versionNumber", 2);

        oldVersion = newVersion("LSTM256-v1");
        newVersion = newVersion("LSTM256-v2");
        setField(controller, "latestVersion", newVersion);

        nodeA = feature("A", 4);
        FeatureManager.getInstance().getFeatures().add(nodeA);

        oldCoordinator = new LifecycleCoordinator(
                "LSTM256-v1", "A",
                settings.getLifecycleSettings().toPolicy());
        oldCoordinator.setAutomaticPromotionAllowed(false);
        manager.registerCoordinator(oldCoordinator);
        oldCoordinator.startTraining();
        oldCoordinator.startEvaluation();

        features(oldVersion).put("A", nodeA);
        pending(oldVersion).add("A");
        manager.registerPrediction(
                new ShadowPrediction("LSTM256-v1", "A", 1, 4, 200.0));
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.reset();
        }
        FeatureManager.getInstance().getFeatures().clear();
        FeatureManager.getInstance().getFeatures().addAll(previousFeatures);
        SimulationSettings.set(previousSettings);
        PredictionConfigurator.sqLiteManager = previousDatabase;
        PredictionConfigurator.predictor_reader = previousReaders;
        PredictionConfigurator.predictor_writer = previousWriters;
    }

    @Test
    void newVersionEvaluatesBWithoutInterruptingA() throws Exception {
        FeatureManager.getInstance().getFeatures().add(feature("B", 4));
        StringWriter requests = responseFor("LSTM256-v2", "B");

        call("joinAvailableFeatures", newVersion);

        assertEquals(LifecycleState.EVALUATING, oldCoordinator.getState());
        assertEquals(1, oldCoordinator.getPendingShadowPredictionCount());
        assertTrue(pending(oldVersion).contains("A"));
        assertFalse(features(newVersion).containsKey("A"));
        assertNull(manager.getCoordinator("LSTM256-v2", "A"));

        assertTrue(pending(newVersion).contains("B"));
        assertEquals(1L, requests.toString().lines().count());
        JSONObject request = new JSONObject(requests.toString().trim());
        assertEquals("B", request.getJSONObject("feature").getString("name"));
    }

    @Test
    void nodeCanJoinNewVersionAfterOldEvaluationFinishes() throws Exception {
        call("joinAvailableFeatures", newVersion);
        assertFalse(features(newVersion).containsKey("A"));

        nodeA.getValues().add(100.0);
        manager.resolveActualValue("A", 4, 100.0);
        call("evaluateVersion", oldVersion);

        assertEquals(LifecycleState.FAILED, oldCoordinator.getState());
        assertFalse(pending(oldVersion).contains("A"));

        responseFor("LSTM256-v2", "A");
        call("joinAvailableFeatures", newVersion);

        assertTrue(pending(newVersion).contains("A"));
        assertEquals(LifecycleState.EVALUATING,
                manager.getCoordinator("LSTM256-v2", "A").getState());
        assertEquals(LifecycleState.FAILED, oldCoordinator.getState());
    }

    @Test
    void shadowJoinDoesNotOverwriteTrainingAttemptSize() throws Exception {
        FeatureManager.getInstance().getFeatures().add(feature("B", 4));
        sizes("lastAttemptSizes").put("B", 2);
        responseFor("LSTM256-v2", "B");

        call("joinAvailableFeatures", newVersion);

        assertEquals(2, sizes("lastAttemptSizes").get("B").intValue());
        assertEquals(4, sizes("lastEvaluationSizes").get("B").intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryRequiresNewDataSinceTrainingAndEvaluation() throws Exception {
        Feature nodeB = feature("B", 67);
        ((Set<String>) field(controller, "retryFeatures")).add("B");
        sizes("lastAttemptSizes").put("B", 2);
        sizes("lastEvaluationSizes").put("B", 4);

        // Only 63 points have arrived since evaluation began.
        assertFalse((Boolean) call("needsTraining", nodeB));

        nodeB.getValues().add(100.0);
        assertTrue((Boolean) call("needsTraining", nodeB));

        // A newer training attempt also prevents an immediate repeat.
        sizes("lastAttemptSizes").put("B", 68);
        assertFalse((Boolean) call("needsTraining", nodeB));
    }

    @SuppressWarnings("unchecked")
    private Object newVersion(String name) throws Exception {
        Constructor<?> constructor = versionClass.getDeclaredConstructor(
                String.class, PairPredictionSettings.class, JSONObject.class);
        constructor.setAccessible(true);
        PairPredictionSettings settings = new PairPredictionSettings(
                name, new PredictionSettings(2, 2, 4, null, false, 0), null);
        Object version = constructor.newInstance(name, settings, new JSONObject());
        ((Map<String, Object>) field(controller, "versions")).put(name, version);
        return version;
    }

    private Feature feature(String name, int count) {
        Feature feature = new Feature(name) {
            @Override
            public double compute() {
                return 100.0;
            }
        };
        for (int i = 0; i < count; i++) {
            feature.getValues().add(100.0);
        }
        return feature;
    }

    private StringWriter responseFor(String model, String name) throws Exception {
        JSONObject original = new JSONObject()
                .put("timestamp", new JSONArray(new int[]{0, 1, 2, 3}))
                .put("data", new JSONArray(new double[]{100, 100, 100, 100}));
        JSONObject future = new JSONObject()
                .put("timestamp", new JSONArray(new int[]{4, 5}))
                .put("data", new JSONArray(new double[]{100, 100}));
        JSONObject prediction = new JSONObject()
                .put("feature_name", name)
                .put("prediction_number", 1)
                .put("original_data", original)
                .put("prediction_future", future);
        JSONObject response = new JSONObject().put("prediction", prediction);

        StringWriter requests = new StringWriter();
        PredictionConfigurator.predictor_writer.put(
                model, new BufferedWriter(requests));
        PredictionConfigurator.predictor_reader.put(
                model, new BufferedReader(new StringReader(response + "\n")));
        return requests;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Feature> features(Object version) throws Exception {
        return (Map<String, Feature>) field(version, "features");
    }

    @SuppressWarnings("unchecked")
    private Set<String> pending(Object version) throws Exception {
        return (Set<String>) field(version, "pending");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> sizes(String name) throws Exception {
        return (Map<String, Integer>) field(controller, name);
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object call(String name, Object argument) throws Exception {
        Class<?> type = argument instanceof Feature ? Feature.class : versionClass;
        Method method = LstmOnlineLifecycleController.class
                .getDeclaredMethod(name, type);
        method.setAccessible(true);
        try {
            return method.invoke(controller, argument);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new AssertionError(cause);
        }
    }
}