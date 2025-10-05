package hu.u_szeged.inf.fog.simulator.prediction.parser;

import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.FromJsonFieldAliases;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonFieldName;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonParseIgnore;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ClassUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The class that implements the methods for the handling of
 * JSON mapping from Java classes into JSONObject classes.
 */
public class JsonParser {

    private JsonParser() {  }

    /**
     * The method that converts Java objects to JSONObject using reflection.
     *
     * @param object The object to convert
     * @param clazz The class of the object
     * @return The converted JSONObject object.
     * @throws JSONException If something failed to convert to a JSONObject
     * @throws IllegalAccessException If the Field couldn't be accessed or written to with reflection.
     */
    public static JSONObject toJson(Object object, Class<?> clazz) throws JSONException, IllegalAccessException {
        Object jsonObject = toJsonObject(object, clazz);

        parseResultValidation(jsonObject, JSONObject.class);

        return (JSONObject) jsonObject;
    }

    /**
     * The method that converts Java objects to JSONArray using reflection.
     *
     * @param object The object to convert
     * @param clazz The class of the object
     * @return The converted JSONObject object.
     * @throws JSONException If something failed to convert to a JSONObject
     * @throws IllegalAccessException If the Field couldn't be accessed or written to with reflection.
     */
    public static JSONArray toJsonArray(Object object, Class<?> clazz) throws JSONException, IllegalAccessException {
        Object jsonObject = toJsonObject(object, clazz);

        parseResultValidation(jsonObject, JSONArray.class);

        return (JSONArray) jsonObject;
    }

    /**
     * Checks if the given object is an object of the given class.
     *
     * @param result The object to check the class of.
     * @param parseToClass The class that it is supposed to be.
     * @throws JSONException If the given object is not of the given class
     */
    private static void parseResultValidation(Object result, Class<?> parseToClass) throws JSONException {
        if (result == null) {
            throw new JSONException("Parsed to null");
        }

        if (result.getClass() != parseToClass) {
            String sb = "Parse Failed! Object: " 
                         + result + " couldn't parse to: " + parseToClass;
            throw new JSONException(sb);
        }
    }

    /**
     * Handles the recursive conversion of Java Object to JSONObject object.
     *
     * @param object The Java object to convert
     * @param clazz The class of the object.
     * @return The converted object or null if empty object.
     * @throws IllegalAccessException if the data access with reflection failed
     * @throws JSONException if the conversion of object to JSONObject failed
     */
    private static Object toJsonObject(Object object, Class<?> clazz) throws IllegalAccessException, JSONException {
        if (object == null) {
            if (Collection.class.isAssignableFrom(clazz)) {
                return new JSONArray();
            }
            return null;
        }

        Object primitiveOrStringObject = toJsonFromPrimitiveOrString(object, clazz);

        if (primitiveOrStringObject != null) {
            return primitiveOrStringObject;
        }

        JSONArray arrayObject = toJsonFromArray(object);

        if (arrayObject != null) {
            return arrayObject;
        }

        JSONArray collectionObject = toJsonFromCollection(object);

        if (collectionObject != null) {
            return collectionObject;
        }

        JSONObject mapObject = toJsonFromMap(object);

        if (mapObject != null) {
            return mapObject;
        }

        return toJsonFromReflection(object, clazz);
    }

    /**
     * Handles the conversion of object to String, Primitive or Primitive Wrapper.
     *
     * @param object The object to convert
     * @param clazz the object's class
     * @return The converted object or null if the object wasn't String, Primitive or Primitive Wrapper
     */
    private static Object toJsonFromPrimitiveOrString(Object object, Class<?> clazz) {
        if (clazz == String.class) {
            return object;
        }

        if (ClassUtils.isPrimitiveOrWrapper(object.getClass())) {
            return object;
        }

        return null;
    }

    /**
     * Handles the conversion of an array object to JSONArray.
     *
     * @param object the obejct to convert
     * @return the converted JSONArray object or null if couldn't convert from an array.
     * @throws JSONException If the recursive conversion failed
     * @throws IllegalAccessException If the recursive conversion's reflection failed
     */
    private static JSONArray toJsonFromArray(Object object) throws JSONException, IllegalAccessException {
        if (object.getClass().isArray()) {
            JSONArray jsonArray = new JSONArray();
            int length = Array.getLength(object);

            for (int i = 0; i < length; i++) {
                if (Array.get(object, i) == null) {
                    continue;
                }

                jsonArray.put(toJsonObject(Array.get(object, i), Array.get(object, i).getClass()));
            }

            return jsonArray;
        }

        return null;
    }

    /**
     * Handles the conversion of a collection object to JSONArray.
     *
     * @param object the obejct to convert
     * @return the converted JSONArray object or null if couldn't convert from a collection.
     * @throws JSONException If the recursive conversion failed
     * @throws IllegalAccessException If the recursive conversion's reflection failed
     */
    private static JSONArray toJsonFromCollection(Object object) throws JSONException, IllegalAccessException {
        if (object instanceof Collection<?>) {
            JSONArray jsonArray = new JSONArray();

            for (Object item : (Collection<?>) object) {
                jsonArray.put(toJsonObject(item, item.getClass()));
            }

            return jsonArray;
        }

        return null;
    }

    /**
     * Handles the conversion of a Map object to JSONObject.
     * In the format of key: value -> fieldName: value
     *
     * @param object the obejct to convert
     * @return the converted JSONObject object or null if couldn't convert from a Map.
     * @throws JSONException If the recursive conversion failed
     * @throws IllegalAccessException If the recursive conversion's reflection failed
     */
    private static JSONObject toJsonFromMap(Object object) throws JSONException, IllegalAccessException {
        if (object instanceof Map<?, ?>) {
            JSONObject jsonMap = new JSONObject();
            Map<?, ?> map = (Map<?, ?>) object;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                jsonMap.put(
                    entry.getKey().toString(),
                    toJsonObject(entry.getValue(), entry.getValue().getClass())
                );
            }

            return jsonMap;
        }

        return null;
    }

    /**
     * Handles the conversion of a Object object to JSONObject based on the given class using reflection.
     *
     * @param object the object to convert
     * @param clazz the class of the object
     * @return the converted JSONObject.
     * @throws JSONException If the recursive conversion failed
     * @throws IllegalAccessException If the data mapping with reflection failed
     */
    private static JSONObject toJsonFromReflection(Object object, Class<?> clazz) throws IllegalAccessException, JSONException {
        JSONObject result = new JSONObject();

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            String fieldName = field.getName();

            if (field.isAnnotationPresent(ToJsonParseIgnore.class)
                    || Modifier.isStatic(field.getModifiers())
                    || field.getName().startsWith("this$")) {
                continue;
            }

            if (field.isAnnotationPresent(ToJsonFieldName.class)) {
                fieldName = field.getAnnotation(ToJsonFieldName.class).value();
            }

            result.put(fieldName, toJsonObject(field.get(object), field.getType()));
        }

        return result;
    }

    /**
     * Converts a JSON string to the given class using reflection and generic methods.
     * Requires a zero parameter constructor for object initialization.
     *
     * @param json the json string to convert from
     * @param clazz the class to convert into
     * @param <T> The class of the object to convert to
     * @return The object with the given class from the JSON string.
     */
    public static <T> T fromJsonString(String json, Class<T> clazz) 
            throws JSONException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return fromJsonObject(new JSONObject(json), clazz, null);
    }

    /**
     * Converts a JSONObject to the given class using reflection and generic methods.
     * Requires a zero parameter constructor for object initialization.
     *
     * @param json the JSONObject to convert from
     * @param clazz the class to convert to
     * @param parent the parent of the object to convert.
     */
    public static <T> T fromJsonObject(JSONObject json, Class<T> clazz, Object parent) 
            throws JSONException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        T instance = createInstance(clazz, parent);

        if (Map.class.isAssignableFrom(clazz)) {
            return (T) fromJsonToMap(json, clazz);
        }

        for (Field field : clazz.getDeclaredFields()) {
            assignValueToField(json, field, instance);
        }

        return instance;
    }

    /**
     * Creates the default instance of the given class.
     *
     * @param clazz the class to create the instance of
     * @param parent the parent object which the class needs to be created in.
     * @return The created object.
     */
    private static <T> T createInstance(Class<T> clazz, Object parent) 
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        T instance;

        if (Map.class.isAssignableFrom(clazz)) {
            instance = (T) new HashMap<>();
        } else if (clazz.isMemberClass()) {
            if (Modifier.isStatic(clazz.getModifiers())) {
                instance = clazz.getDeclaredConstructor().newInstance();
            } else {
                instance = clazz.getConstructor(parent.getClass()).newInstance(parent);
            }
        } else {
            instance = clazz.getDeclaredConstructor().newInstance();
        }

        return instance;
    }

    /**
     * Creates a Map from the given JSONObject.
     *
     * @param json the JSONObject to create from
     * @param clazz the class of the object to create to.
     * @return The map from the JSONObject.
     */
    private static Map<Object, Object> fromJsonToMap(JSONObject json, Class<?> clazz) throws JSONException {
        if (Map.class.isAssignableFrom(clazz)) {
            Map<Object, Object> instance = new HashMap<>();

            for (Iterator it = json.keys(); it.hasNext(); ) {
                var key = it.next();
                var value = json.get(key.toString());
                if (json.get(key.toString()).toString().matches("^\\d+$")) { //Integer checking regex
                    try {
                        value = Integer.parseInt(value.toString());
                    } catch (NumberFormatException ignored){
                        //Not an Integer
                    }
                } else if (json.get(key.toString()).toString().matches("^[0-9.,]+$")) { //Double checking regex
                    try {
                        value = Double.parseDouble(value.toString());
                    } catch (NumberFormatException ignored) {
                        //Not a Double
                    }
                }

                instance.put(key, value);
            }
            return instance;
        }

        return new HashMap<>();
    }

    /**
     * Method used for assigning the json object's value to the Java object's corresponding field.
     *
     * @param json the JSONObject to assign from.
     * @param field the Java field to assign to.
     * @param instance The instance of the object. (Reflection requires)
     */
    private static void assignValueToField(JSONObject json, Field field, Object instance) 
            throws JSONException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        field.setAccessible(true);

        List<String> jsonFieldNames = new ArrayList<>();
        jsonFieldNames.add(field.getName());
        if (field.isAnnotationPresent(FromJsonFieldAliases.class)) {
            Collections.addAll(jsonFieldNames, field.getAnnotation(FromJsonFieldAliases.class).fieldNames());
        }
            
        for (String jsonFieldName : jsonFieldNames) {
            if (json.has(jsonFieldName)) {
                Object value = json.get(jsonFieldName);

                if (value instanceof JSONObject) {
                    field.set(instance, fromJsonObject(new JSONObject(value.toString()), field.getType(), instance));
                    continue;
                }

                if (value instanceof JSONArray) {
                    field.set(instance, convertJsonArray((JSONArray) value, field.getType()));
                    continue;
                }

                if (field.getType().getName().toLowerCase().startsWith("int")) {
                    value = Integer.parseInt(value.toString());
                } else if (field.getType().getName().toLowerCase().startsWith("double")) {
                    value = Double.parseDouble(value.toString());
                } else if (field.getType().getName().toLowerCase().startsWith("long")) {
                    value = Long.parseLong(value.toString());
                }

                field.set(instance, value);
            }
        }

    }

    /**
     * Converts the given JSONArray to the given class.
     *
     * @param jsonArray the JSONArray to convert from.
     * @param clazz the class to convert to.
     * @return the created Object.
     */
    private static Object convertJsonArray(JSONArray jsonArray, Class<?> clazz) 
            throws JSONException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        Class<?> componentType = clazz.getComponentType();

        if (clazz.isArray()) {
            Object array = Array.newInstance(componentType, jsonArray.length());
            for (int i = 0; i < jsonArray.length(); i++) {
                Array.set(array, i, fromJsonString(jsonArray.get(i).toString(), componentType));
            }
            return array;
        }

        if (clazz == List.class) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(jsonArray.get(i));
            }
            return list;
        }

        return jsonArray;
    }
    
}
