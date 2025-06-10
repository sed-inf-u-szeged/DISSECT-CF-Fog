package hu.u_szeged.inf.fog.simulator.prediction.settings;

import lombok.*;
import org.json.JSONException;
import org.json.JSONObject;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class PredictionSettings {
    
    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class SmoothingSettings {
        private int windowSize;
        private int polynomialDegree;
    }

    private int length;
    private int testSize;
    private int batchSize;
    private SmoothingSettings smoothing;
    private boolean scale;
    private int minPredictionTime;
}
