package com.eugenearch.broadcasts.common.helpers;

import com.google.gson.internal.LinkedTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class EasyJsonHelper {

    public static JsonObject readAbstract(Map<?, ?> map) {
        HashMap<String, Object> values = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String)) return null;
            values.put((String) entry.getKey(), entry.getValue());
        }
        return read(values);
    }

    public static JsonObject read(HashMap<String, ?> map) {
        HashMap<String, JsonObject> values = new HashMap<>();
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Double || value instanceof Float || value instanceof Boolean || value instanceof ArrayList) {
                values.put(entry.getKey(), new JsonObject(value));
            }
            if (value instanceof HashMap) {
                values.put(entry.getKey(), read(values));
            }
            if (value instanceof LinkedTreeMap) {
                HashMap<String, Object> hashMap = new HashMap<>();
                for (Map.Entry<?, ?> e : ((LinkedTreeMap<?, ?>) value).entrySet()) {
                    if (e.getKey() instanceof String) {
                        hashMap.put((String) e.getKey(), e.getValue());
                    }
                }
                values.put(entry.getKey(), read(hashMap));
            }
        }
        return new JsonObject(values);
    }

    public static class JsonObject {
        private Object value;

        public JsonObject(Object value) {
            if (value instanceof String) {
                this.value = value;
            }

            if (value instanceof Float || value instanceof Double) {
                this.value = value;
            }

            if (value instanceof Boolean) {
                this.value = value;
            }

            if (value instanceof ArrayList) {
                boolean valid = true;
                for (Object o : (ArrayList<?>) value) {
                    if (!(o instanceof JsonObject)) {
                        valid = false;
                        break;
                    }
                }
                if (valid) this.value = value;
            }

            if (value instanceof HashMap) {
                boolean valid = true;
                for (Map.Entry<?, ?> k : ((HashMap<?, ?>) value).entrySet()) {
                    if (!(k.getKey() instanceof String) || !(k.getValue() instanceof JsonObject)) {
                        valid = false;
                        break;
                    }
                }
                if (valid) this.value = value;
            }

            if (value == null) throw new RuntimeException("Not a valid JsonObject value given!");
        }

        public JsonObject getSubObject(String keyName) {
            if (value instanceof HashMap) {
                HashMap<?, ?> valueMap = (HashMap<?, ?>) value;

                for (Object key : valueMap.keySet()) {
                    if (!(key instanceof String)) continue;

                    String keyStr = (String) key;
                    if (!keyStr.equals(keyName)) continue;

                    if (!(valueMap.get(keyStr) instanceof JsonObject)) return null;
                    return  (JsonObject) valueMap.get(keyStr);
                }
            }
            return null;
        }

        public boolean hasSubValues() {
            return value instanceof HashMap;
        }

        public String asString() {
            if (value instanceof String) return (String) value;
            return null;
        }

        public Float asFloat() {
            if (value instanceof Float) return (Float) value;
            return null;
        }

        public Double asDouble() {
            if (value instanceof Double) return (Double) value;
            return null;
        }

        public Boolean asBoolean() {
            if (value instanceof Boolean) return (Boolean) value;
            return null;
        }

        public ArrayList<JsonObject> asList() {
            if (value instanceof ArrayList) {
                ArrayList<JsonObject> objects = new ArrayList<>();

                for (Object val : (ArrayList<?>) value) {
                    if (val instanceof JsonObject) {
                        objects.add((JsonObject) val);
                    } else {
                        return null;
                    }
                }
                return objects;
            }
            return null;
        }

        public String getString(String keyName) {
            JsonObject val = getSubObject(keyName);
            if (val == null) return null;
            return val.asString();
        }

        public Float getFloat(String keyName) {
            JsonObject val = getSubObject(keyName);
            if (val == null) return null;
            return val.asFloat();
        }

        public Double getDouble(String keyName) {
            JsonObject val = getSubObject(keyName);
            if (val == null) return null;
            return val.asDouble();
        }

        public Boolean getBoolean(String keyName) {
            JsonObject val = getSubObject(keyName);
            if (val == null) return null;
            return val.asBoolean();
        }

        public ArrayList<JsonObject> getList(String keyName) {
            JsonObject val = getSubObject(keyName);
            if (val == null) return null;
            return val.asList();
        }

        public ArrayList<String> getSubKeys() {
            if (value instanceof HashMap) {
                HashMap<?, ?> valueMap = (HashMap<?, ?>) value;
                ArrayList<String> keys = new ArrayList<>();
                for (Object key : valueMap.keySet()) {
                    if (!(key instanceof String)) continue;
                    keys.add((String)key);
                }
                return keys;
            }
            return null;
        }
    }
}
