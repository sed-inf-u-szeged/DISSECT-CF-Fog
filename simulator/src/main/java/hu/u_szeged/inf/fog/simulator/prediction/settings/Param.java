package hu.u_szeged.inf.fog.simulator.prediction.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.json.JSONException;
import org.json.JSONObject;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Param {
    private String key;
    private Object value;
}
