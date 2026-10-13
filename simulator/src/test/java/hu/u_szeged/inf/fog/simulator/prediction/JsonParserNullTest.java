package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

class JsonParserNullTest {

    public static class Response {
        public Status future;
        public Status test;
        public int count = 7;
    }

    public static class Status {
        public String status;
        public Boolean converged;
        public Boolean finite;
    }

    @Test
    void readsNormalAndNonConvergedStatuses() throws Exception {
        Response result = JsonParser.fromJsonString(
                "{\"future\":{\"status\":\"not_converged\","
                + "\"converged\":false,\"finite\":true},"
                + "\"test\":{\"status\":\"converged\",\"converged\":true}}",
                Response.class);

        assertEquals("not_converged", result.future.status);
        assertFalse(result.future.converged);
        assertTrue(result.future.finite);
        assertTrue(result.test.converged);
    }

    @Test
    void acceptsNullStatusesForOtherModels() throws Exception {
        Response result = JsonParser.fromJsonString(
                "{\"future\":null,\"test\":null}", Response.class);

        assertNull(result.future);
        assertNull(result.test);
    }

    @Test
    void preservesUnknownConvergenceAsNull() throws Exception {
        Response result = JsonParser.fromJsonString(
                "{\"future\":{\"status\":\"unknown\","
                + "\"converged\":null,\"finite\":true}}", Response.class);

        assertEquals("unknown", result.future.status);
        assertNull(result.future.converged);
        assertTrue(result.future.finite);
    }

    @Test
    void missingFieldsKeepTheirDefaults() throws Exception {
        Response result = JsonParser.fromJsonString("{}", Response.class);

        assertNull(result.future);
        assertEquals(7, result.count);
    }

    @Test
    void rejectsNullForPrimitiveNumbers() {
        assertThrows(JSONException.class, () ->
                JsonParser.fromJsonString("{\"count\":null}", Response.class));
    }
}