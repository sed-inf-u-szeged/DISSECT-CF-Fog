package hu.u_szeged.inf.fog.simulator.prediction;

import java.io.IOException;
import org.json.JSONObject;

/** Read-only publication of completed load records; never advances simulation time. */
@FunctionalInterface
public interface NodeLoadListener {
    NodeLoadListener NONE = (kind, record) -> { };

    void emit(String kind, JSONObject record) throws IOException;
}
