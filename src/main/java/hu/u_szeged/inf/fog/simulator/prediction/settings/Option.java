package hu.u_szeged.inf.fog.simulator.prediction.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.json.JSONException;
import org.json.JSONObject;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Option {
    private String id;
    private String label;

    public Option(String label) {
        this.id = label.toLowerCase().replaceAll(" ", "_");
        this.label = label;
    }
}
