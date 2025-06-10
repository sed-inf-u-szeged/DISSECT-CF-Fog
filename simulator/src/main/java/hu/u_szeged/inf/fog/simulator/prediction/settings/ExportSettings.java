package hu.u_szeged.inf.fog.simulator.prediction.settings;

import java.io.File;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The class is used to manage settings related to exporting data
 * of the time series analysis.
 */
@AllArgsConstructor
@NoArgsConstructor
public class ExportSettings {
    @Getter
    private boolean enabled;
    @Setter
    @Accessors(chain = true)
    private String location;
    @Getter
    @Setter
    @Accessors(chain = true)
    private boolean saveDataset;
    @Getter
    @Setter
    @Accessors(chain = true)
    private boolean savePredictionSettings;
    @Getter
    @Setter
    @Accessors(chain = true)
    private boolean savePredictions;
    @Getter
    @Setter
    @Accessors(chain = true)
    private boolean saveMetrics;
    
    /**
     * Returns the location of the exported files.
     */
    public String getLocation() {
        String separator = File.separator;
        if (location.endsWith(separator)) {
            return location;
        }
        return location + separator;
    }

    public ExportSettings enable() {
        this.enabled = true;
        return this;
    }
    
    public ExportSettings disable() {
        this.enabled = false;
        return this;
    }

    public boolean canExportDataset() {
        return enabled && saveDataset && !location.equals("");
    }

    public boolean canExportPredictionSettings() {
        return enabled && savePredictionSettings && !location.equals("");
    }

    public boolean canExportPredictions() {
        return enabled && savePredictions && !location.equals("");
    }

    public boolean canExportMetrics() {
        return enabled && saveMetrics && !location.equals("");
    }
}