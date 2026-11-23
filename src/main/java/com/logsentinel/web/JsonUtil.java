package com.logsentinel.web;

import java.util.Map;

/**
 * Minimal JSON serializer. No external dependency is used.
 * Handles strings, numbers, booleans, null, lists, and maps.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            return toJsonMap((Map<?, ?>) value);
        }
        if (value instanceof Iterable) {
            return toJsonList((Iterable<?>) value);
        }
        if (value.getClass().isArray()) {
            return toJsonArray((Object[]) value);
        }
        return quote(String.valueOf(value));
    }

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '/'  -> sb.append("\\/");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String toJsonMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(quote(String.valueOf(entry.getKey())));
            sb.append(':');
            sb.append(toJson(entry.getValue()));
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private static String toJsonList(Iterable<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            sb.append(toJson(item));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private static String toJsonArray(Object[] array) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Object item : array) {
            if (!first) {
                sb.append(',');
            }
            sb.append(toJson(item));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }
}
