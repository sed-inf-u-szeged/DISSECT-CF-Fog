package hu.u_szeged.inf.fog.simulator.test.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.Feature;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.prediction.Utils;
import java.util.List;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureManagerTest {
    static int lastValue = 0;

    @Test
    void getFeaturesWithEnoughData() throws JSONException {

        final int windowSize = 4;
        final String featureName = "Feature 1";
        List<Feature> features;
        double[] values;
        FeatureManager.getInstance().addFeature(new Feature(featureName) {
            @Override
            public double compute() {
                return FeatureManagerTest.lastValue++;
            }
        });

        features = FeatureManager.getInstance().getFeaturesWithEnoughData(windowSize);
        assertEquals(0, FeatureManager.getInstance().getFeatureByName(featureName).getValues().size());
        assertEquals(0, features.size());
        values = Utils.objectArrayToDoubleArray(FeatureManager.getInstance().getFeatureByName(featureName).getWindowValues(windowSize).toArray());
        Assertions.assertArrayEquals(new double[]{}, values);

        for (int i = 0; i < 4; i++) {
            FeatureManager.getInstance().getFeatureByName(featureName).computeValue();
        }
        features = FeatureManager.getInstance().getFeaturesWithEnoughData(windowSize);
        assertEquals(4, FeatureManager.getInstance().getFeatureByName(featureName).getValues().size());
        assertEquals(1, features.size());
        values = Utils.objectArrayToDoubleArray(FeatureManager.getInstance().getFeatureByName(featureName).getWindowValues(windowSize).toArray());
        Assertions.assertArrayEquals(new double[]{ 0, 1, 2, 3 }, values);

        for (int i = 0; i < 2; i++) {
            FeatureManager.getInstance().getFeatureByName(featureName).computeValue();
        }
        features = FeatureManager.getInstance().getFeaturesWithEnoughData(windowSize);
        assertEquals(6, FeatureManager.getInstance().getFeatureByName(featureName).getValues().size());
        assertEquals(1, features.size());
        values = Utils.objectArrayToDoubleArray(FeatureManager.getInstance().getFeatureByName(featureName).getWindowValues(windowSize).toArray());
        Assertions.assertArrayEquals(new double[]{ 2, 3, 4, 5 }, values);

        for (int i = 0; i < 10; i++) {
            FeatureManager.getInstance().getFeatureByName(featureName).computeValue();
        }
        features = FeatureManager.getInstance().getFeaturesWithEnoughData(windowSize);
        assertEquals(16, FeatureManager.getInstance().getFeatureByName(featureName).getValues().size());
        assertEquals(1, features.size());
        values = Utils.objectArrayToDoubleArray(FeatureManager.getInstance().getFeatureByName(featureName).getWindowValues(windowSize).toArray());
        Assertions.assertArrayEquals(new double[]{ 12, 13, 14, 15 }, values);
    }
}