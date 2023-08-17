package hu.u_szeged.inf.fog.simulator.prediction.settings.simulation;

import org.json.JSONException;
import org.json.JSONObject;

public class ExportSettings {
    private boolean enabled;
    private String location;
    private boolean saveDataset;
    private boolean savePredictionSettings;
    private boolean savePredictions;
    private boolean saveMetrics;

    public ExportSettings(boolean enabled, String location, boolean saveDataset, boolean savePredictionSettings, boolean savePredictions, boolean saveMetrics) {
        this.enabled = enabled;
        this.location = location;
        this.saveDataset = saveDataset;
        this.savePredictionSettings = savePredictionSettings;
        this.savePredictions = savePredictions;
        this.saveMetrics = saveMetrics;
    }

    public ExportSettings(JSONObject jsonObject) throws JSONException {
        fromJSONObject(jsonObject);
    }

    private void fromJSONObject(JSONObject jsonObject) throws JSONException {
        this.enabled = jsonObject.getBoolean("enabled");
        this.location = jsonObject.getString("location");
        this.saveDataset = jsonObject.getBoolean("saveDataset");
        this.savePredictionSettings = jsonObject.getBoolean("savePredictionSettings");
        this.savePredictions = jsonObject.getBoolean("savePredictions");
        this.saveMetrics = jsonObject.getBoolean("saveMetrics");
    }

    public ExportSettings() {
        this.enabled = false;
        this.location = "";
        this.saveDataset = false;
        this.savePredictionSettings = false;
        this.savePredictions = false;
        this.saveMetrics = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ExportSettings enable() {
        this.enabled = true;
        return this;
    }
    public ExportSettings disable() {
        this.enabled = false;
        return this;
    }

    public String getLocation() {
        if (location.endsWith("/") || location.endsWith("\\")) {
            return location;
        }
        return location + "/";
    }

    public ExportSettings setLocation(String location) {
        this.location = location;
        return this;
    }

    public boolean isSaveDataset() {
        return saveDataset;
    }

    public ExportSettings setSaveDataset(boolean saveDataset) {
        this.saveDataset = saveDataset;
        return this;
    }

    public boolean isSavePredictionSettings() {
        return savePredictionSettings;
    }

    public ExportSettings setSavePredictionSettings(boolean savePredictionSettings) {
        this.savePredictionSettings = savePredictionSettings;
        return this;
    }

    public boolean isSavePredictions() {
        return savePredictions;
    }

    public ExportSettings setSavePredictions(boolean savePredictions) {
        this.savePredictions = savePredictions;
        return this;
    }

    public boolean isSaveMetrics() {
        return saveMetrics;
    }

    public ExportSettings setSaveMetrics(boolean saveMetrics) {
        this.saveMetrics = saveMetrics;
        return this;
    }

    public JSONObject toJSON() throws JSONException {
        return new JSONObject()
                .put("enabled", enabled)
                .put("location", location)
                .put("saveDataset", saveDataset)
                .put("savePredictionSettings", savePredictionSettings)
                .put("savePredictions", savePredictions)
                .put("saveMetrics", saveMetrics);
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
