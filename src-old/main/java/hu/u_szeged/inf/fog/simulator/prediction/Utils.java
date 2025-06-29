package hu.u_szeged.inf.fog.simulator.prediction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Helper class providing methods related to JSONArray converting.
 */
public class Utils {
    
    public static DateTimeFormatter DTF_CSV = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
    
    /**
     * Converts a list to a JSONArray.
     *
     * @param <T> the type of elements in the list
     * @param list the list to be converted
     * @return the JSONArray representing the list
     */
    public static <T> JSONArray listToJsonArray(List<T> list) {
        JSONArray result = new JSONArray();
        for (T item : list) {
            result.put(item);
        }
        return result;
    }

    /**
     * Converts a JSONArray to a List.
     *
     * @param <T> the type of elements in the list
     * @param array the JSONArray to be converted
     * @return the List representing the JSONArray
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> jsonArrayToList(JSONArray array) throws JSONException {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            result.add((T) array.get(i));
        }
        return result;
    }

    /**
     * Retrieves the keys from a JSONObject.
     *
     * @param jsonObject the JSONObject from which keys are to be retrieved
     * @return the list of keys in the JSONObject
     */
    public static List<String> getJsonObjectKeys(JSONObject jsonObject) {
        List<String> result = new ArrayList<>();
        Iterator<?> it = jsonObject.keys();
        while (it.hasNext()) {
            result.add((String) it.next());
        }
        return result;
    }

    /**
     * Generates a file name with the current date and time.
     *
     * @param name the base name of the file
     * @param extension the file extension
     * @return the file name with date and time appended
     */
    public static String getFileNameWithDate(String name, String extension) {
        return String.format("%s_%s.%s", name, DTF_CSV.format(LocalDateTime.now()), extension);
    }

    /**
     * Converts an array of objects to an array of doubles.
     *
     * @param arr the array of objects to be converted
     * @return the array of doubles
     */
    public static double[] objectArrayToDoubleArray(Object[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (double) arr[i];
        }
        return result;
    }
}