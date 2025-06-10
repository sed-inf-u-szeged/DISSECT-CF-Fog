package hu.u_szeged.inf.fog.simulator.prediction.settings;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Getter
@NoArgsConstructor
public class Parameter {

    @AllArgsConstructor
    public enum ParameterType {

        TEXT("text"), BOOLEAN("boolean"), SELECT("select"), OPEN_FILE("openFile"), BUTTON("button");
        private final String type;
    }

    @Setter
    @Accessors(chain = true)
    private String id;
    @Setter
    @Accessors(chain = true)
    private String label;
    @Setter
    @Accessors(chain = true)
    private ParameterType type;
    private boolean required;
    @Setter
    @Accessors(chain = true)
    private List<Option> options;
    @Setter
    @Accessors(chain = true)
    private String defaultValue;

    public Parameter(String id, String label, ParameterType type, 
            boolean required, List<Option> options, String defaultValue) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.required = required;
        this.options = options;
        this.defaultValue = defaultValue;
    }

    public Parameter(String label, ParameterType type, boolean required, 
            List<Option> options, String defaultValue) {
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
}
