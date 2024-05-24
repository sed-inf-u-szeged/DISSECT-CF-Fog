package hu.u_szeged.inf.fog.simulator.prediction.settings.predictor;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Parameter {
    
    public enum ParameterType {
        TEXT("text"), BOOLEAN("boolean"), SELECT("select"), OPEN_FILE("openFile"), BUTTON("button");
        private final String type;
        
        ParameterType(final String type) {
            this.type = type;
        }
    }

    private String id;
    private String label;
    private ParameterType type;
    private boolean required;
    private List<Option> options;
    private String defaultValue;

    public Parameter(String id, String label, ParameterType type, boolean required, 
            List<Option> options, String defaultValue) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.required = required;
        this.options = options;
        this.defaultValue = defaultValue;
    }

    public Parameter(String label, ParameterType type, boolean required, List<Option> options, String defaultValue) {
        this.id = label.toLowerCase().replaceAll(" ", "_");
        this.label = label;
        this.type = type;
        this.required = required;
        this.options = options;
        this.defaultValue = defaultValue;
    }

    public Parameter(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public Parameter(String label) {
        this.id = label.toLowerCase().replaceAll(" ", "_");
        this.label = label;
    }

    public Parameter required() {
        this.required = true;
        return this;
    }

    public String getId() {
        return id;
    }

    public Parameter setId(String id) {
        this.id = id;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public Parameter setLabel(String label) {
        this.label = label;
        return this;
    }

    public ParameterType getType() {
        return type;
    }

    public Parameter setType(ParameterType type) {
        this.type = type;
        return this;
    }

    public boolean isRequired() {
        return required;
    }

    public List<Option> getOptions() {
        return options;
    }

    public Parameter setOptions(List<Option> options) {
        this.options = options;
        return this;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public Parameter setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public JSONObject toJson() {
        try {
            JSONArray array = new JSONArray();
            if (options != null) {
                for (Option o : options) {
                    array.put(o.toJson());
                }
            }

            return new JSONObject()
                    .put("id", id)
                    .put("label", label)
                    .put("type", type.type)
                    .put("required", required)
                    .put("defaultValue", defaultValue)
                    .put("options", options == null ? new JSONArray() : array);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
