package hu.u_szeged.inf.fog.simulator.prediction.parser;

import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.FromJsonFieldAliases;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonFieldName;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonParseIgnore;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
 * Converts between Java objects and JSON using reflection.
 */
public class JsonParser {

    private JsonParser() { }

    public static JSONObject toJson(Object object, Class<?> clazz)
            throws JSONException, IllegalAccessException {
        Object result = toJsonObject(object, clazz);
        parseResultValidation(result, JSONObject.class);
        return (JSONObject) result;
    }

    public static JSONArray toJsonArray(Object object, Class<?> clazz)
            throws JSONException, IllegalAccessException {
        Object result = toJsonObject(object, clazz);
        parseResultValidation(result, JSONArray.class);
        return (JSONArray) result;
    }

    private static void parseResultValidation(Object result, Class<?> expected)
            throws JSONException {
        if (result == null) {
            throw new JSONException("Parsed to null");
        }
        if (result.getClass() != expected) {
            throw new JSONException(
                    "Parse Failed! Object: " + result + " couldn't parse to: " + expected);
        }
    }

    private static Object toJsonObject(Object object, Class<?> clazz)
            throws IllegalAccessException, JSONException {
        if (object == null) {
            if (Collection.class.isAssignableFrom(clazz)) {
                return new JSONArray();
            }
            return null;
        }

        Object primitive = toJsonFromPrimitiveOrString(object, clazz);
        if (primitive != null) {
            return primitive;
        }

        JSONArray array = toJsonFromArray(object);
        if (array != null) {
            return array;
        }

        JSONArray collection = toJsonFromCollection(object);
        if (collection != null) {
            return collection;
        }

        JSONObject map = toJsonFromMap(object);
        if (map != null) {
            return map;
        }

        return toJsonFromReflection(object, clazz);
    }

    private static Object toJsonFromPrimitiveOrString(Object object, Class<?> clazz) {
        if (clazz == String.class
                || ClassUtils.isPrimitiveOrWrapper(object.getClass())) {
            return object;
        }
        return null;
    }

    private static JSONArray toJsonFromArray(Object object)
            throws JSONException, IllegalAccessException {
        if (!object.getClass().isArray()) {
            return null;
        }

        JSONArray result = new JSONArray();
        for (int i = 0; i < Array.getLength(object); i++) {
            Object item = Array.get(object, i);
            if (item != null) {
                result.put(toJsonObject(item, item.getClass()));
            }
        }
        return result;
    }

    private static JSONArray toJsonFromCollection(Object object)
            throws JSONException, IllegalAccessException {
        if (!(object instanceof Collection<?>)) {
            return null;
        }

        JSONArray result = new JSONArray();
        for (Object item : (Collection<?>) object) {
            result.put(toJsonObject(item, item.getClass()));
        }
        return result;
    }

    private static JSONObject toJsonFromMap(Object object)
            throws JSONException, IllegalAccessException {
        if (!(object instanceof Map<?, ?>)) {
            return null;
        }

        JSONObject result = new JSONObject();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
            result.put(entry.getKey().toString(),
                    toJsonObject(entry.getValue(), entry.getValue().getClass()));
        }
        return result;
    }

    private static JSONObject toJsonFromReflection(Object object, Class<?> clazz)
            throws IllegalAccessException, JSONException {
        JSONObject result = new JSONObject();

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(ToJsonParseIgnore.class)
                    || Modifier.isStatic(field.getModifiers())
                    || field.getName().startsWith("this$")) {
                continue;
            }

            String name = field.getName();
            if (field.isAnnotationPresent(ToJsonFieldName.class)) {
                name = field.getAnnotation(ToJsonFieldName.class).value();
            }
            result.put(name, toJsonObject(field.get(object), field.getType()));
        }
        return result;
    }

    public static <T> T fromJsonString(String json, Class<T> clazz)
            throws JSONException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        return fromJsonObject(new JSONObject(json), clazz, null);
    }

    public static <T> T fromJsonObject(JSONObject json, Class<T> clazz, Object parent)
            throws JSONException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        T instance = createInstance(clazz, parent);
        if (Map.class.isAssignableFrom(clazz)) {
            return (T) fromJsonToMap(json, clazz);
        }

        for (Field field : clazz.getDeclaredFields()) {
            assignValueToField(json, field, instance);
        }
        return instance;
    }

    private static <T> T createInstance(Class<T> clazz, Object parent)
            throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        if (Map.class.isAssignableFrom(clazz)) {
            return (T) new HashMap<>();
        }
        if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            return clazz.getConstructor(parent.getClass()).newInstance(parent);
        }
        return clazz.getDeclaredConstructor().newInstance();
    }

    private static Map<Object, Object> fromJsonToMap(JSONObject json, Class<?> clazz)
            throws JSONException {
        if (!Map.class.isAssignableFrom(clazz)) {
            return new HashMap<>();
        }

        Map<Object, Object> result = new HashMap<>();
        for (Iterator<?> it = json.keys(); it.hasNext(); ) {
            Object key = it.next();
            Object value = json.get(key.toString());

            if (value.toString().matches("^\\d+$")) {
                try {
                    value = Integer.parseInt(value.toString());
                } catch (NumberFormatException ignored) {
                    // Keep the original value when it is not an integer.
                }
            } else if (value.toString().matches("^[0-9.,]+$")) {
                try {
                    value = Double.parseDouble(value.toString());
                } catch (NumberFormatException ignored) {
                    // Keep the original value when it is not a double.
                }
            }
            result.put(key, value);
        }
        return result;
    }

    private static void assignValueToField(JSONObject json, Field field, Object instance)
            throws JSONException, InvocationTargetException, NoSuchMethodException,
            InstantiationException, IllegalAccessException {
        field.setAccessible(true);

        List<String> names = new ArrayList<>();
        names.add(field.getName());
        if (field.isAnnotationPresent(FromJsonFieldAliases.class)) {
            Collections.addAll(names,
                    field.getAnnotation(FromJsonFieldAliases.class).fieldNames());
        }

        for (String name : names) {
            if (!json.has(name)) {
                continue;
            }

            Object value = json.get(name);

            if (value == JSONObject.NULL) {
                if (field.getType().isPrimitive()) {
                    throw new JSONException(
                            "JSON null cannot be assigned to primitive field: " + name);
                }
                field.set(instance, null);
                continue;
            }

            if (value instanceof JSONObject) {
                field.set(instance, fromJsonObject(
                        new JSONObject(value.toString()), field.getType(), instance));
                continue;
            }

            if (value instanceof JSONArray) {
                field.set(instance, convertJsonArray(
                        (JSONArray) value, field.getType(), field.getGenericType()));
                continue;
            }

            String typeName = field.getType().getName().toLowerCase();
            if (typeName.startsWith("int")) {
                value = Integer.parseInt(value.toString());
            } else if (typeName.startsWith("double")) {
                value = Double.parseDouble(value.toString());
            } else if (typeName.startsWith("long")) {
                value = Long.parseLong(value.toString());
            }
            field.set(instance, value);
        }
    }

    /**
     * Converts numeric list elements to the declared Java element type.
     */
    private static Object convertListValue(Object value, Type elementType) {
        if (value == JSONObject.NULL) {
            return null;
        }
        if (!(value instanceof Number)) {
            return value;
        }

        Number number = (Number) value;
        if (elementType == Double.class) {
            return number.doubleValue();
        }
        if (elementType == Float.class) {
            return number.floatValue();
        }
        if (elementType == Integer.class) {
            return number.intValue();
        }
        if (elementType == Long.class) {
            return number.longValue();
        }
        return value;
    }

    private static Object convertJsonArray(
            JSONArray jsonArray, Class<?> clazz, Type genericType)
            throws JSONException, InvocationTargetException, NoSuchMethodException,
            InstantiationException, IllegalAccessException {
        Class<?> componentType = clazz.getComponentType();

        if (clazz.isArray()) {
            Object array = Array.newInstance(componentType, jsonArray.length());
            for (int i = 0; i < jsonArray.length(); i++) {
                Array.set(array, i,
                        fromJsonString(jsonArray.get(i).toString(), componentType));
            }
            return array;
        }

        if (clazz == List.class) {
            Type elementType = Object.class;
            if (genericType instanceof ParameterizedType) {
                elementType = ((ParameterizedType) genericType)
                        .getActualTypeArguments()[0];
            }

            List<Object> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(convertListValue(jsonArray.get(i), elementType));
            }
            return list;
        }
        return jsonArray;
    }
}