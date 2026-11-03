package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleState;
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
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowBatchControllerTest {

    private static final String MODEL = "LSTM256-v1";
    private static final String NODE = "cpu";

    private LstmOnlineLifecycleController controller;
    private LifecycleManager manager;
    private LifecycleCoordinator coordinator;
    private Class<?> versionClass;
    private Object version;
    private Feature feature;
    private StringWriter requests;

    private SimulationSettings previousSettings;
    private SqLiteManager previousDatabase;
    private Map<String, BufferedReader> previousReaders;
    private Map<String, BufferedWriter> previousWriters;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        previousSettings = SimulationSettings.get();
        previousDatabase = PredictionConfigurator.sqLiteManager;
        previousReaders = PredictionConfigurator.predictor_reader;
        previousWriters = PredictionConfigurator.predictor_writer;

        LifecycleSettings lifecycle =
                new LifecycleSettings(true, 4, 0, 0, 0, 0, 100, 0);
        SimulationSettings settings = new SimulationSettings();
        settings.setLifecycleSettings(lifecycle);
        settings.setPredictionSettings(new ArrayList<>());
        SimulationSettings.set(settings);

        PredictionConfigurator.sqLiteManager = null;
        PredictionConfigurator.predictor_reader = new HashMap<>();
        PredictionConfigurator.predictor_writer = new HashMap<>();

        manager = LifecycleManager.getInstance();
        manager.reset();

        Constructor<LstmOnlineLifecycleController> constructor =
                LstmOnlineLifecycleController.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        controller = constructor.newInstance();

        versionClass = Class.forName(
                LstmOnlineLifecycleController.class.getName() + "$ModelVersion");
        Constructor<?> versionConstructor = versionClass.getDeclaredConstructor(
                String.class, PairPredictionSettings.class, JSONObject.class);
        versionConstructor.setAccessible(true);

        PairPredictionSettings predictionSettings = new PairPredictionSettings(
                MODEL, new PredictionSettings(2, 2, 4, null, false, 0), null);
        version = versionConstructor.newInstance(
                MODEL, predictionSettings, new JSONObject());

        ((Map<String, Object>) field(controller, "versions")).put(MODEL, version);

        feature = new Feature(NODE) {
            @Override
            public double compute() {
                return 100.0;
            }
        };
        for (int i = 0; i < 4; i++) {
            feature.getValues().add(100.0);
        }
        ((Map<String, Feature>) field(version, "features")).put(NODE, feature);
        ((Set<String>) field(version, "pending")).add(NODE);

        coordinator = new LifecycleCoordinator(MODEL, NODE, lifecycle.toPolicy());
        coordinator.setAutomaticPromotionAllowed(false);
        manager.registerCoordinator(coordinator);
        coordinator.startTraining();
        coordinator.startEvaluation();

        requests = new StringWriter();
        PredictionConfigurator.predictor_writer.put(
                MODEL, new BufferedWriter(requests));
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.reset();
        }
        SimulationSettings.set(previousSettings);
        PredictionConfigurator.sqLiteManager = previousDatabase;
        PredictionConfigurator.predictor_reader = previousReaders;
        PredictionConfigurator.predictor_writer = previousWriters;
    }

    @Test
    void shortBatchIsAcceptedAndNextBatchUsesLatestInput() throws Exception {
        reply(1, 100);
        call("requestShadowPrediction", version, feature);

        assertEquals(1L, requestCount());
        assertEquals(2, coordinator.getPendingShadowPredictionCount());
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());

        appendActual(101);
        appendActual(102);
        assertEquals(2, coordinator.getLatestMetrics().getMatchedSamples());
        assertEquals(0, coordinator.getPendingShadowPredictionCount());

        reply(2, 100);
        call("continueShadowEvaluation", version);

        assertEquals(2L, requestCount());
        assertEquals(2, coordinator.getPendingShadowPredictionCount());

        String lastRequest = requests.toString().lines()
                .reduce((first, second) -> second).orElseThrow();
        JSONArray values = new JSONObject(lastRequest)
                .getJSONObject("feature").getJSONArray("values");
        assertEquals(4, values.length());
        assertEquals(101.0, values.getDouble(2), 0.000001);
        assertEquals(102.0, values.getDouble(3), 0.000001);
    }

    @Test
    void doesNotRequestWhilePreviousTargetsArePending() throws Exception {
        reply(1, 100);
        call("requestShadowPrediction", version, feature);
        appendActual(100);

        call("continueShadowEvaluation", version);
        call("continueShadowEvaluation", version);

        assertEquals(1L, requestCount());
        assertEquals(1, coordinator.getPendingShadowPredictionCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotRequestWithoutNewInputData() throws Exception {
        // Seed the last request position to exercise the unchanged-input guard.
        ((Map<String, Integer>) field(version, "lastShadowRequestSizes"))
                .put(NODE, feature.getValues().size());

        call("continueShadowEvaluation", version);

        assertEquals(0L, requestCount());
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());
    }

    @Test
    void stopsRequestingWhenEnoughAccurateSamplesAreMatched() throws Exception {
        reply(1, 100);
        call("requestShadowPrediction", version, feature);
        appendActual(100);
        appendActual(100);

        reply(2, 100);
        call("continueShadowEvaluation", version);
        appendActual(100);
        appendActual(100);

        assertEquals(4, coordinator.getLatestMetrics().getMatchedSamples());
        assertEquals(LifecycleState.READY, coordinator.getState());
        assertTrue(coordinator.isPolicySatisfied());

        call("continueShadowEvaluation", version);
        assertEquals(2L, requestCount());

        call("evaluateVersion", version);
        assertEquals(LifecycleState.ACTIVE, coordinator.getState());
        assertTrue(manager.isActive(MODEL, NODE));

        call("continueShadowEvaluation", version);
        assertEquals(2L, requestCount());
    }

    @Test
    void completeFailedEvaluationDoesNotRequestMoreBatches() throws Exception {
        reply(1, 200);
        call("requestShadowPrediction", version, feature);
        appendActual(100);
        appendActual(100);

        reply(2, 200);
        call("continueShadowEvaluation", version);
        appendActual(100);
        appendActual(100);

        assertTrue(coordinator.hasCompleteEvaluationWindow());
        assertFalse(coordinator.isPolicySatisfied());
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());

        call("continueShadowEvaluation", version);
        assertEquals(2L, requestCount());

        call("evaluateVersion", version);
        assertEquals(LifecycleState.FAILED, coordinator.getState());
        assertFalse(manager.isActive(MODEL, NODE));
    }

    @Test
    void emptyFutureOutputRaisesAnError() throws Exception {
        JSONObject prediction = new JSONObject()
                .put("prediction_number", 1)
                .put("original_data", data(
                        new int[]{0, 1, 2, 3},
                        new double[]{100, 100, 100, 100}))
                .put("prediction_future", data(new int[0], new double[0]));
        setResponse(new JSONObject().put("prediction", prediction));

        assertThrows(IllegalStateException.class,
                () -> call("requestShadowPrediction", version, feature));
        assertEquals(0, coordinator.getPendingShadowPredictionCount());
    }

    private void appendActual(double value) {
        int target = feature.getValues().size();
        feature.getValues().add(value);
        manager.resolveActualValue(NODE, target, value);
    }

    private void reply(int number, double predicted) throws Exception {
        JSONObject prediction = new JSONObject()
                .put("feature_name", NODE)
                .put("prediction_number", number)
                .put("original_data", data(
                        new int[]{0, 1, 2, 3},
                        new double[]{100, 100, 100, 100}))
                .put("prediction_future", data(
                        new int[]{4, 5},
                        new double[]{predicted, predicted}));
        setResponse(new JSONObject().put("prediction", prediction));
    }

    private JSONObject data(int[] timestamps, double[] values) throws Exception {
        JSONArray times = new JSONArray();
        JSONArray points = new JSONArray();
        for (int timestamp : timestamps) {
            times.put(timestamp);
        }
        for (double value : values) {
            points.put(value);
        }
        return new JSONObject().put("timestamp", times).put("data", points);
    }

    private void setResponse(JSONObject response) {
        PredictionConfigurator.predictor_reader.put(
                MODEL, new BufferedReader(new StringReader(response + "\n")));
    }

    private long requestCount() {
        return requests.toString().lines().count();
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void call(String name, Object... arguments) throws Exception {
        Class<?>[] types = arguments.length == 2
                ? new Class<?>[]{versionClass, Feature.class}
                : new Class<?>[]{versionClass};
        Method method = LstmOnlineLifecycleController.class
                .getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            method.invoke(controller, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new AssertionError(cause);
        }
    }
}