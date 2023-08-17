package hu.u_szeged.inf.fog.simulator.prediction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class Utils {
    public static DateTimeFormatter DTF_CSV = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
    public static <T> JSONArray listToJSONArray(List<T> list) {
        JSONArray result = new JSONArray();
        for (T item: list) {
            result.put(item);
        }
        return result;
    }

    public static <T> List<T> JSONArrayToList(JSONArray array) throws JSONException {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            result.add((T) array.get(i));
        }
        return result;
    }

    public static List<String> getJSONObjectKeys(JSONObject jsonObject) {
        List<String> result = new ArrayList<>();
        Iterator it = jsonObject.keys();
        while (it.hasNext()) {
            result.add((String) it.next());
        }
        return result;
    }

    public static String getFileNameWithDate(String name, String extension) {
        return String.format("%s_%s.%s", name, DTF_CSV.format(LocalDateTime.now()), extension);
    }

    public static double[] objectArrayToDoubleArray(Object[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (double) arr[i];
        }
        return result;
    }
}
