package hu.u_szeged.inf.fog.simulator.prediction.communication;

import org.json.JSONException;
import org.json.JSONObject;

public class SocketMessage {

    public enum SocketApplication {

        UNKNOWN("UNKNOWN"),
        APPLICATION_FEATURE_HANDLER("APPLICATION_FEATURE_HANDLER"),
        APPLICATION_PREDICTOR("APPLICATION_PREDICTOR"),
        APPLICATION_INTERFACE("APPLICATION_INTERFACE");
        public final String value;

        SocketApplication(final String value) {
            this.value = value;
        }
    }

    private String event;
    private JSONObject data;

    public SocketMessage(String event, JSONObject data) {
        this.event = event;
        this.data = data;
    }

    public SocketMessage(String jsonString) throws JSONException {
        fromJsonString(jsonString);
    }

    public SocketMessage copy() {
        return new SocketMessage(event, data);
    }

    public void fromJsonString(String jsonString) throws JSONException {
        JSONObject jsonObject = new JSONObject(jsonString);

        this.event = jsonObject.getString("event");
        this.data = jsonObject.getJSONObject("data");
    }

    private JSONObject toJson() throws JSONException {
        return new JSONObject()
            .put("event", event)
            .put("data", data);
    }

    @Override
    public String toString() {
        try {
            return toJson().toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    public boolean hasError() {
        return data.has("error");
    }
}