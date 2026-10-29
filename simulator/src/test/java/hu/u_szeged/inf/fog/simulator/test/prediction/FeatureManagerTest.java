package hu.u_szeged.inf.fog.simulator.test.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.Feature;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.prediction.Utils;
import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureManagerTest {
    static int lastValue = 0;
    private FeatureManager manager;
    private SqLiteManager previousDatabase;
    private SimulationSettings previousSettings;
    private boolean previousCreateDatabase;
    private boolean previousDatabaseEnabled;
    private Field enabledField;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<FeatureManager> constructor = FeatureManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        manager = constructor.newInstance();
        previousDatabase = PredictionConfigurator.sqLiteManager;
        previousCreateDatabase = PredictionConfigurator.CREATE_DATABASE;
        previousSettings = SimulationSettings.get();
        enabledField = SqLiteManager.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        previousDatabaseEnabled = enabledField.getBoolean(null);
        PredictionConfigurator.CREATE_DATABASE = false;
        PredictionConfigurator.sqLiteManager = new SqLiteManager();
        SimulationSettings.set(null);
        lastValue = 0;
    }

    @AfterEach
    void tearDown() {
        PredictionConfigurator.sqLiteManager = previousDatabase;
        PredictionConfigurator.CREATE_DATABASE = previousCreateDatabase;
        SqLiteManager.setEnabled(previousDatabaseEnabled);
        SimulationSettings.set(previousSettings);
    }


    @Test
    void getFeaturesWithEnoughData() throws JSONException {

        final int windowSize = 4;
        final String featureName = "Feature 1";
        List<Feature> features;
        double[] values;
        manager.addFeature(new Feature(featureName) {
            @Override
            public double compute() {
                return FeatureManagerTest.lastValue++;
            }
        });

        features = manager.getFeaturesWithEnoughData(windowSize);
        assertEquals(0, manager.getFeatureByName(featureName).getValues().size());
        assertEquals(0, features.size());
        values = Utils.objectArrayToDoubleArray(manager.getFeatureByName(featureName).getWindowValues(windowSize).toArray());
        Assertions.assertArrayEquals(new double[]{}, values);

        for (int i = 0; i < 4; i++) {
            manager.getFeatureByName(featureName).computeValue();
        }
        features = manager.getFeaturesWithEnoughData(windowSize);
        assertEquals(4, manager.getFeatureByName(featureName).getValues().size());
        assertEquals(1, features.size());
        values = Utils.objectArrayToDoubleArray(manager.getFeatureByName(featureName).getWindowValues(windowSize).toArray());
        Assertions.assertArrayEquals(new double[]{ 0, 1, 2, 3 }, values);

        for (int i = 0; i < 2; i++) {
            manager.getFeatureByName(featureName).computeValue();
        }
        features = manager.getFeaturesWithEnoughData(windowSize);
        assertEquals(6, manager.getFeatureByName(featureName).getValues().size());
        assertEquals(1, features.size());
        values = Utils.objectArrayToDoubleArray(manager.getFeatureByName(featureName).getWindowValues(windowSize).toArray());
        Assertions.assertArrayEquals(new double[]{ 2, 3, 4, 5 }, values);

        for (int i = 0; i < 10; i++) {
            manager.getFeatureByName(featureName).computeValue();
        }
        features = manager.getFeaturesWithEnoughData(windowSize);
        assertEquals(16, manager.getFeatureByName(featureName).getValues().size());
        assertEquals(1, features.size());
        values = Utils.objectArrayToDoubleArray(manager.getFeatureByName(featureName).getWindowValues(windowSize).toArray());
        Assertions.assertArrayEquals(new double[]{ 12, 13, 14, 15 }, values);
    }
}