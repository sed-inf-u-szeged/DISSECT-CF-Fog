package hu.u_szeged.inf.fog.simulator.prediction.settings.simulation;

import org.json.JSONException;
import org.json.JSONObject;

public class Param {
    private String key;
    private Object value;

    public Param(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public JSONObject toJson() {
        try {
            return new JSONObject()
                    .put("key", key)
                    .put("value", value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
