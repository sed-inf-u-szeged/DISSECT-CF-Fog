package hu.u_szeged.inf.fog.simulator.prediction.settings.predictor;

import org.json.JSONException;
import org.json.JSONObject;

public class Option {
    private String id;
    private String label;

    public Option(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public Option(String label) {
        this.id = label.toLowerCase().replaceAll(" ", "_");
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public JSONObject toJSON() {
        try {
            return new JSONObject()
                    .put("id", id)
                    .put("label", label);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
