package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecyclePolicy;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleState;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.ShadowPrediction;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LstmOnlineLifecycleControllerTest {

    private LstmOnlineLifecycleController controller;
    private LifecycleManager manager;
    private Class<?> versionClass;
    private SimulationSettings previousSettings;
    private SqLiteManager previousDatabase;
    private Map<String, Process> previousProcesses;
    private Map<String, BufferedReader> previousReaders;
    private Map<String, BufferedWriter> previousWriters;
    private final Map<String, FakeProcess> processes = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        previousSettings = SimulationSettings.get();
        previousDatabase = PredictionConfigurator.sqLiteManager;
        previousProcesses = PredictionConfigurator.predictor;
        previousReaders = PredictionConfigurator.predictor_reader;
        previousWriters = PredictionConfigurator.predictor_writer;

        SimulationSettings settings = new SimulationSettings();
        settings.setPredictionSettings(new ArrayList<>());
        SimulationSettings.set(settings);
        PredictionConfigurator.sqLiteManager = null;
        PredictionConfigurator.predictor = new HashMap<>();
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
        setField(controller, "trainingWindowPoints", 287);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (controller != null) {
                controller.finish();
            }
        } finally {
            manager.reset();
            SimulationSettings.set(previousSettings);
            PredictionConfigurator.sqLiteManager = previousDatabase;
            PredictionConfigurator.predictor = previousProcesses;
            PredictionConfigurator.predictor_reader = previousReaders;
            PredictionConfigurator.predictor_writer = previousWriters;
        }
    }

    @Test
    void rejectedCandidateKeepsOldVersionServing() throws Exception {
        Object old = newVersion("LSTM256-v1");
        serve(old, "A");
        Object candidate = newVersion("LSTM256-v2");
        LifecycleCoordinator coordinator = addNode(candidate, "A");

        resolve(candidate, "A", 20, 200.0);
        call("evaluateVersion", candidate);

        assertEquals(LifecycleState.FAILED, coordinator.getState());
        assertTrue(manager.isActive("LSTM256-v1", "A"));
        assertFalse(manager.isActive("LSTM256-v2", "A"));
        assertTrue(processes.get("LSTM256-v1").isAlive());
        assertTrue(isConfigured("LSTM256-v1"));
        assertFalse(isConfigured("LSTM256-v2"));
    }

    @Test
    void failedCandidateClosesOnlyItsOwnProcess() throws Exception {
        Object old = newVersion("LSTM256-v1");
        serve(old, "A");
        Object candidate = newVersion("LSTM256-v2");
        addNode(candidate, "A");

        call("failVersion", candidate, new IllegalStateException("Test failure"));

        assertTrue(manager.isActive("LSTM256-v1", "A"));
        assertTrue(processes.get("LSTM256-v1").isAlive());
        assertFalse(processes.get("LSTM256-v2").isAlive());
        assertTrue(isConfigured("LSTM256-v1"));
        assertFalse(PredictionConfigurator.predictor.containsKey("LSTM256-v2"));
        assertFalse(versions().containsKey("LSTM256-v2"));
    }

    @Test
    void nodesSwitchIndependentlyAndOldProcessClosesAfterLastSwitch()
            throws Exception {
        Object old = newVersion("LSTM256-v1");
        serve(old, "A");
        serve(old, "B");

        Object candidate = newVersion("LSTM256-v2");
        addNode(candidate, "A");
        LifecycleCoordinator pendingB = addNode(candidate, "B");
        setField(controller, "latestVersion", candidate);

        resolve(candidate, "A", 20, 100.0);
        call("evaluateVersion", candidate);
        call("retireUnusedVersions");

        assertTrue(manager.isActive("LSTM256-v2", "A"));
        assertTrue(manager.isActive("LSTM256-v1", "B"));
        assertEquals(LifecycleState.EVALUATING, pendingB.getState());
        assertTrue(processes.get("LSTM256-v1").isAlive());
        assertTrue(isConfigured("LSTM256-v1"));
        assertTrue(isConfigured("LSTM256-v2"));

        resolve(candidate, "B", 21, 100.0);
        call("evaluateVersion", candidate);
        call("retireUnusedVersions");

        assertTrue(manager.isActive("LSTM256-v2", "A"));
        assertTrue(manager.isActive("LSTM256-v2", "B"));
        assertFalse(processes.get("LSTM256-v1").isAlive());
        assertFalse(isConfigured("LSTM256-v1"));
        assertFalse(versions().containsKey("LSTM256-v1"));
        assertTrue(processes.get("LSTM256-v2").isAlive());
    }

    @Test
    void pendingShadowEvaluationPreventsEarlyProcessCleanup() throws Exception {
        Object old = newVersion("LSTM256-v1");
        addNode(old, "A");
        Object latest = newVersion("LSTM256-v2");
        setField(controller, "latestVersion", latest);

        call("retireUnusedVersions");

        assertTrue(versions().containsKey("LSTM256-v1"));
        assertTrue(processes.get("LSTM256-v1").isAlive());
        assertEquals(LifecycleState.EVALUATING,
                manager.getCoordinator("LSTM256-v1", "A").getState());
    }

    @Test
    void servingVersionFailureDoesNotStopOtherVersion() throws Exception {
        Object old = newVersion("LSTM256-v1");
        serve(old, "A");
        serve(old, "B");

        Object newer = newVersion("LSTM256-v2");
        addNode(newer, "A");
        resolve(newer, "A", 20, 100.0);
        call("evaluateVersion", newer);

        call("failVersion", newer, new IllegalStateException("Test process crash"));

        assertTrue(manager.isActive("baseline", "A"));
        assertEquals(LifecycleState.FAILED,
                manager.getCoordinator("LSTM256-v2", "A").getState());
        assertTrue(manager.isActive("LSTM256-v1", "B"));
        assertTrue(processes.get("LSTM256-v1").isAlive());
        assertFalse(processes.get("LSTM256-v2").isAlive());
        assertTrue(isConfigured("LSTM256-v1"));
        assertFalse(isConfigured("LSTM256-v2"));
    }

        @Test
    @SuppressWarnings("unchecked")
    void rejectionSchedulesRetryAndGrowsWindowOncePerVersion() throws Exception {
        Object candidate = newVersion("LSTM256-v1");
        LifecycleCoordinator first = addNode(candidate, "A");
        LifecycleCoordinator second = addNode(candidate, "B");

        resolve(candidate, "A", 20, 200.0);
        call("evaluateVersion", candidate);

        assertEquals(LifecycleState.FAILED, first.getState());
        assertTrue(manager.isActive("baseline", "A"));
        assertEquals(351, getField(controller, "trainingWindowPoints"));
        Set<String> retries =
                (Set<String>) getField(controller, "retryFeatures");
        assertTrue(retries.contains("A"));

        resolve(candidate, "B", 20, 200.0);
        call("evaluateVersion", candidate);

        assertEquals(LifecycleState.FAILED, second.getState());
        assertTrue(manager.isActive("baseline", "B"));
        assertTrue(retries.contains("B"));
        assertEquals(351, getField(controller, "trainingWindowPoints"));
        assertTrue(((Set<String>) getField(candidate, "pending")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectedFeatureWaitsForEnoughNewDataBeforeRetry() throws Exception {
        Object candidate = newVersion("LSTM256-v1");
        addNode(candidate, "A");
        setField(controller, "versionNumber", 1);

        Feature feature = ((Map<String, Feature>)
                getField(candidate, "features")).get("A");
        for (int i = 0; i < 287; i++) {
            feature.getValues().add(100.0);
        }
        ((Map<String, Integer>) getField(controller, "lastAttemptSizes"))
                .put("A", 287);
        ((Map<String, Integer>) getField(controller, "lastEvaluationSizes"))
                .put("A", 300);

        resolve(candidate, "A", 20, 200.0);
        call("evaluateVersion", candidate);

        Method needsTraining = LstmOnlineLifecycleController.class
                .getDeclaredMethod("needsTraining", Feature.class);
        needsTraining.setAccessible(true);

        assertFalse((Boolean) needsTraining.invoke(controller, feature));

        // 363 - 300 = 63 new points: not enough.
        while (feature.getValues().size() < 363) {
            feature.getValues().add(100.0);
        }
        assertFalse((Boolean) needsTraining.invoke(controller, feature));

        // 364 - 300 = 64 new points: retry becomes eligible.
        feature.getValues().add(100.0);
        assertTrue((Boolean) needsTraining.invoke(controller, feature));

        // A pending evaluation must prevent another training attempt.
        Set<String> pending = (Set<String>) getField(candidate, "pending");
        pending.add("A");
        assertFalse((Boolean) needsTraining.invoke(controller, feature));
        pending.remove("A");
        assertTrue((Boolean) needsTraining.invoke(controller, feature));
    }

    @Test
    void rejectedVersionsCannotGrowWindowBeyondMaximum() throws Exception {
        setField(controller, "trainingWindowPoints", 4080);
        Object first = newVersion("LSTM256-v1");
        addNode(first, "A");
        resolve(first, "A", 20, 200.0);
        call("evaluateVersion", first);
        assertEquals(4096, getField(controller, "trainingWindowPoints"));

        Object second = newVersion("LSTM256-v2");
        addNode(second, "A");
        resolve(second, "A", 21, 200.0);
        call("evaluateVersion", second);
        assertEquals(4096, getField(controller, "trainingWindowPoints"));
    }


    private Object newVersion(String name) throws Exception {
        PairPredictionSettings settings = new PairPredictionSettings(name, null, null);
        Constructor<?> constructor = versionClass.getDeclaredConstructor(
                String.class, PairPredictionSettings.class, JSONObject.class);
        constructor.setAccessible(true);
        Object version = constructor.newInstance(name, settings, new JSONObject());
        versions().put(name, version);

        FakeProcess process = new FakeProcess();
        processes.put(name, process);
        PredictionConfigurator.addPredictorProcess(name, process);
        return version;
    }

    @SuppressWarnings("unchecked")
    private LifecycleCoordinator addNode(Object version, String feature)
            throws Exception {
        LifecyclePolicy policy =
                new LifecyclePolicy(1, 1.0, 1.0, 1.0, 5.0, 100.0, 0.1);
        if (manager.getCoordinator("baseline", feature) == null) {
            manager.registerCoordinator(
                    new LifecycleCoordinator("baseline", feature, policy));
            manager.activateBaseline("baseline", feature);
        }

        String name = (String) getField(version, "name");
        LifecycleCoordinator coordinator =
                new LifecycleCoordinator(name, feature, policy);
        coordinator.setAutomaticPromotionAllowed(false);
        manager.registerCoordinator(coordinator);
        coordinator.startTraining();
        coordinator.startEvaluation();

        Feature input = new Feature(feature) {
            @Override
            public double compute() {
                return 100.0;
            }
        };
        ((Map<String, Feature>) getField(version, "features")).put(feature, input);
        ((Set<String>) getField(version, "pending")).add(feature);
        return coordinator;
    }

    private void serve(Object version, String feature) throws Exception {
        addNode(version, feature);
        resolve(version, feature, 10, 100.0);
        call("evaluateVersion", version);
    }

    private void resolve(Object version, String feature, int timestamp,
            double predicted) throws Exception {
        String name = (String) getField(version, "name");
        manager.registerPrediction(
                new ShadowPrediction(name, feature, 1, timestamp, predicted));
        manager.resolveActualValue(feature, timestamp, 100.0);
    }

    private boolean isConfigured(String name) {
        return SimulationSettings.get().getPredictionSettings().stream()
                .anyMatch(settings -> name.equals(settings.getPredictorName()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> versions() throws Exception {
        return (Map<String, Object>) getField(controller, "versions");
    }

    private Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void call(String name, Object... arguments) throws Exception {
        for (Method method : LstmOnlineLifecycleController.class.getDeclaredMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == arguments.length) {
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
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static class FakeProcess extends Process {
        private boolean alive = true;
        private final OutputStream input = new ByteArrayOutputStream();
        private final InputStream output = new ByteArrayInputStream(new byte[0]);
        private final InputStream error = new ByteArrayInputStream(new byte[0]);

        @Override
        public OutputStream getOutputStream() {
            return input;
        }

        @Override
        public InputStream getInputStream() {
            return output;
        }

        @Override
        public InputStream getErrorStream() {
            return error;
        }

        @Override
        public int waitFor() {
            return exitValue();
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("Test process is running");
            }
            return 0;
        }

        @Override
        public void destroy() {
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}