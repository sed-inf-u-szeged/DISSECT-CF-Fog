package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.*;

import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.*;
import hu.u_szeged.inf.fog.simulator.prediction.settings.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DualOnlineLifecycleTest {
    private SimulationSettings previousSettings;
    private SqLiteManager previousDatabase;
    private final LifecycleManager manager = LifecycleManager.getInstance();
    private LstmOnlineLifecycleController lstm;
    private LstmOnlineLifecycleController tcn;
    private LstmOnlineLifecycleController gru;
    private final LifecyclePolicy policy =
            new LifecyclePolicy(2, 1000, 1e6, 1000, 200, 0, 0.1);

    @BeforeEach
    void setUp() {
        previousSettings = SimulationSettings.get();
        previousDatabase = PredictionConfigurator.sqLiteManager;
        SimulationSettings settings = new SimulationSettings();
        settings.setPredictionSettings(new ArrayList<>());
        settings.setTrainSettings(new TrainSettings("original.keras", "input", "output",
                false, null, 192, 64, "combined_mae_mean10", "Adam", 40, true));
        SimulationSettings.set(settings);
        PredictionConfigurator.sqLiteManager = null;
        manager.reset();
        lstm = new LstmOnlineLifecycleController(new LstmModelAdapter(),
                OnlineTrainingSettings.getDefault(), true);
        tcn = new LstmOnlineLifecycleController(new TcnModelAdapter(),
                OnlineTrainingSettings.getDefault(), true);
        gru = new LstmOnlineLifecycleController(new GruModelAdapter(),
                OnlineTrainingSettings.getDefault(), true);
    }

    @AfterEach
    void tearDown() {
        manager.reset();
        SimulationSettings.set(previousSettings);
        PredictionConfigurator.sqLiteManager = previousDatabase;
    }

    @Test
    void trainingPathsAreIndependentAcrossFamiliesAndVersions() {
        TrainSettings template = SimulationSettings.get().getTrainSettings();
        TrainSettings l1 = lstm.prepareTrainingSettings("LSTM256-v1");
        TrainSettings t1 = tcn.prepareTrainingSettings("TCN256-v1");
        TrainSettings t2 = tcn.prepareTrainingSettings("TCN256-v2");
        TrainSettings g1 = gru.prepareTrainingSettings("GRU256-v1");
        TrainSettings g2 = gru.prepareTrainingSettings("GRU256-v2");
        assertNotSame(template, l1);
        assertNotSame(template, t1);
        assertNotEquals(l1.getDatasetsDirectory(), t1.getDatasetsDirectory());
        assertNotEquals(t1.getDatasetsDirectory(), t2.getDatasetsDirectory());
        assertNotEquals(l1.getModelOutputLocation(), t1.getModelOutputLocation());
        assertNotEquals(t1.getModelOutputLocation(), t2.getModelOutputLocation());
        assertEquals("lstm_i192_o64.keras", l1.getModelName());
        assertEquals("tcn_i192_o64.keras", t1.getModelName());
        assertEquals(l1.getInputSize(), t1.getInputSize());
        assertEquals(l1.getOutputSize(), t1.getOutputSize());
        assertEquals(l1.getEpochs(), t1.getEpochs());
        assertEquals("gru_i192_o64.keras", g1.getModelName());
        assertEquals(l1.getInputSize(), g1.getInputSize());
        assertEquals(l1.getOutputSize(), g1.getOutputSize());
        assertEquals(l1.getEpochs(), g1.getEpochs());
        assertEquals(5, Set.of(l1.getDatasetsDirectory(), t1.getDatasetsDirectory(),
                t2.getDatasetsDirectory(), g1.getDatasetsDirectory(), g2.getDatasetsDirectory()).size());
        assertEquals(5, Set.of(l1.getModelOutputLocation(), t1.getModelOutputLocation(),
                t2.getModelOutputLocation(), g1.getModelOutputLocation(), g2.getModelOutputLocation()).size());
        l1.setDatasetsDirectory("changed");
        t1.setModelOutputLocation("changed");
        assertEquals("input", template.getDatasetsDirectory());
        assertEquals("output", template.getModelOutputLocation());
        assertEquals("original.keras", template.getModelName());
        assertTrue(template.isCollectAllCsvAndTrainOnIt());
    }

    @Test
    void threeFamiliesRemainEligibleAndGruFailureDoesNotDisableTheOthers() throws Exception {
        Object lv = version(lstm, "LSTM256-v1");
        Object tv = version(tcn, "TCN256-v1");
        Object gv = version(gru, "GRU256-v1");
        LifecycleCoordinator lc = node(lv, "LSTM256-v1", "A");
        LifecycleCoordinator tc = node(tv, "TCN256-v1", "A");
        LifecycleCoordinator gc = node(gv, "GRU256-v1", "A");
        resolve("GRU256-v1", "A", 0, 110);
        resolve("TCN256-v1", "A", 0, 130);
        resolve("LSTM256-v1", "A", 0, 100);
        call(gru, "evaluateVersion", gv);
        call(tcn, "evaluateVersion", tv);
        call(lstm, "evaluateVersion", lv);
        assertEquals(LifecycleState.ACTIVE, lc.getState());
        assertEquals(LifecycleState.READY, tc.getState());
        assertEquals(LifecycleState.READY, gc.getState());
        assertEquals(3, SimulationSettings.get().getPredictionSettings().size());
        for (String name : Set.of("LSTM256-v1", "TCN256-v1", "GRU256-v1")) {
            assertTrue(manager.isPredictionAllowed(name, "A"));
            assertFalse(manager.isPredictionAllowed(name, "unknown"));
        }
        assertTrue((Boolean) call(gru, "hasUsableModel", "A"));
        assertTrue(manager.registerPredictionIfEvaluating(
                new ShadowPrediction("GRU256-v1", "A", 2, 2, 10000)));
        manager.resolveActualValue("A", 2, 100);
        call(gru, "evaluateVersion", gv);
        assertEquals(LifecycleState.DEGRADED, gc.getState());
        assertFalse(manager.isPredictionAllowed("GRU256-v1", "A"));
        assertTrue(manager.isActive("LSTM256-v1", "A"));
        assertTrue(manager.isPredictionAllowed("TCN256-v1", "A"));
        assertTrue(((Set<String>) get(gru, "retryFeatures")).contains("A"));
    }

    @Test
    void bothPassedModelsRemainEligibleWithoutForcingTheLastOneActive() throws Exception {
        Object lv = version(lstm, "LSTM256-v1");
        Object tv = version(tcn, "TCN256-v1");
        LifecycleCoordinator lc = node(lv, "LSTM256-v1", "A");
        LifecycleCoordinator tc = node(tv, "TCN256-v1", "A");
        resolve("LSTM256-v1", "A", 0, 100);
        resolve("TCN256-v1", "A", 0, 130);
        call(lstm, "evaluateVersion", lv);
        call(tcn, "evaluateVersion", tv);
        assertEquals(LifecycleState.ACTIVE, lc.getState());
        assertEquals(LifecycleState.READY, tc.getState());
        assertEquals(2, SimulationSettings.get().getPredictionSettings().size());
        assertTrue(manager.isPredictionAllowed("LSTM256-v1", "A"));
        assertTrue(manager.isPredictionAllowed("TCN256-v1", "A"));
        assertFalse(manager.isPredictionAllowed("TCN256-v1", "unknown"));
        assertTrue((Boolean) call(tcn, "hasUsableModel", "A"));
        set(tcn, "versionNumber", 1);
        ((Set<String>) get(tcn, "retryFeatures")).add("A");
        Feature feature = feature("A");
        for (int i = 0; i < 500; i++) { feature.getValues().add(100.0); }
        assertFalse((Boolean) call(tcn, "needsTraining", feature));
    }

    @Test
    void promotionResultDoesNotDependOnControllerOrder() throws Exception {
        Object lv = version(lstm, "LSTM256-v1");
        Object tv = version(tcn, "TCN256-v1");
        node(lv, "LSTM256-v1", "A");
        node(tv, "TCN256-v1", "A");
        resolve("TCN256-v1", "A", 0, 130);
        resolve("LSTM256-v1", "A", 0, 100);
        call(tcn, "evaluateVersion", tv);
        call(lstm, "evaluateVersion", lv);
        assertTrue(manager.isActive("LSTM256-v1", "A"));
        assertTrue(manager.isPredictionAllowed("TCN256-v1", "A"));
    }

    @Test
    void passedFeatureDoesNotExposePendingOrFailedFeatures() throws Exception {
        Object tv = version(tcn, "TCN256-v1");
        node(tv, "TCN256-v1", "A");
        LifecycleCoordinator pending = node(tv, "TCN256-v1", "B");
        resolve("TCN256-v1", "A", 0, 100);
        call(tcn, "evaluateVersion", tv);
        assertTrue(manager.isPredictionAllowed("TCN256-v1", "A"));
        assertFalse(manager.isPredictionAllowed("TCN256-v1", "B"));
        pending.markFailed();
        assertFalse(manager.isPredictionAllowed("TCN256-v1", "B"));
    }

    @Test
    void readyCandidateKeepsFreshMetricsAndDegradesWithoutReplacingOtherFamily() throws Exception {
        Object lv = version(lstm, "LSTM256-v1");
        Object tv = version(tcn, "TCN256-v1");
        node(lv, "LSTM256-v1", "A");
        LifecycleCoordinator tc = node(tv, "TCN256-v1", "A");
        resolve("LSTM256-v1", "A", 0, 100);
        resolve("TCN256-v1", "A", 0, 130);
        call(lstm, "evaluateVersion", lv);
        call(tcn, "evaluateVersion", tv);
        assertEquals(30.0, tc.getLatestMetrics().getMae());
        assertTrue(manager.registerPredictionIfEvaluating(
                new ShadowPrediction("TCN256-v1", "A", 2, 2, 110)));
        manager.resolveActualValue("A", 2, 100);
        assertEquals(20.0, tc.getLatestMetrics().getMae());
        assertEquals(LifecycleState.READY, tc.getState());
        assertTrue(manager.registerPredictionIfEvaluating(
                new ShadowPrediction("TCN256-v1", "A", 3, 3, 10000)));
        manager.resolveActualValue("A", 3, 100);
        assertEquals(LifecycleState.DEGRADED, tc.getState());
        call(tcn, "evaluateVersion", tv);
        assertFalse(manager.isPredictionAllowed("TCN256-v1", "A"));
        assertTrue(manager.isActive("LSTM256-v1", "A"));
        assertTrue(((Set<String>) get(tcn, "retryFeatures")).contains("A"));
    }

    @Test
    void legacyReadyCandidatesDoNotOptIntoBackgroundMonitoring() {
        LifecycleCoordinator lc = new LifecycleCoordinator("legacy", "A", policy);
        lc.setAutomaticPromotionAllowed(false);
        manager.registerCoordinator(lc);
        lc.startTraining(); lc.startEvaluation();
        resolve("legacy", "A", 0, 100);
        assertEquals(LifecycleState.READY, lc.getState());
        assertFalse(manager.isPredictionAllowed("legacy", "A"));
        assertFalse(manager.registerPredictionIfEvaluating(
                new ShadowPrediction("legacy", "A", 2, 2, 100)));
    }

    private Object version(LstmOnlineLifecycleController controller, String name) throws Exception {
        Class<?> type = Class.forName(LstmOnlineLifecycleController.class.getName() + "$ModelVersion");
        Constructor<?> ctor = type.getDeclaredConstructor(String.class, PairPredictionSettings.class, JSONObject.class);
        ctor.setAccessible(true);
        Object value = ctor.newInstance(name, new PairPredictionSettings(name, null, null), new JSONObject());
        ((Map<String, Object>) get(controller, "versions")).put(name, value);
        return value;
    }

    private LifecycleCoordinator node(Object version, String name, String featureName) throws Exception {
        LifecycleCoordinator lc = new LifecycleCoordinator(name, featureName, policy);
        lc.setAutomaticPromotionAllowed(false);
        lc.setMonitorWhileReady(true);
        manager.registerCoordinator(lc);
        lc.startTraining(); lc.startEvaluation();
        ((Map<String, Feature>) get(version, "features")).put(featureName, feature(featureName));
        ((Set<String>) get(version, "pending")).add(featureName);
        return lc;
    }

    private Feature feature(String name) {
        return new Feature(name) { public double compute() { return 100; } };
    }

    private void resolve(String name, String feature, int start, double predicted) {
        for (int i = start; i < start + 2; i++) {
            manager.registerPrediction(new ShadowPrediction(name, feature, 1, i, predicted));
            manager.resolveActualValue(feature, i, 100);
        }
    }

    private Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(target);
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    private Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true); return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }
}
