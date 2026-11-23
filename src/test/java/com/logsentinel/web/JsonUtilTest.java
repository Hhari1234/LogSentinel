package com.logsentinel.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonUtil minimal JSON serializer.
 */
class JsonUtilTest {

    @Test
    void serializesNull() {
        assertEquals("null", JsonUtil.toJson(null));
    }

    @Test
    void serializesString() {
        assertEquals("\"hello\"", JsonUtil.toJson("hello"));
    }

    @Test
    void serializesStringWithSpecialChars() {
        assertEquals("\"he said \\\"hi\\\"\"", JsonUtil.toJson("he said \"hi\""));
    }

    @Test
    void serializesNumber() {
        assertEquals("42", JsonUtil.toJson(42));
        assertEquals("3.14", JsonUtil.toJson(3.14));
    }

    @Test
    void serializesBoolean() {
        assertEquals("true", JsonUtil.toJson(true));
        assertEquals("false", JsonUtil.toJson(false));
    }

    @Test
    void serializesMap() {
        Map<String, Object> map = Map.of("name", "test", "count", 5, "active", true);
        String json = JsonUtil.toJson(map);
        assertTrue(json.contains("\"name\":\"test\""));
        assertTrue(json.contains("\"count\":5"));
        assertTrue(json.contains("\"active\":true"));
    }

    @Test
    void serializesList() {
        java.util.List<Object> list = java.util.List.of(1, 2, 3);
        assertEquals("[1,2,3]", JsonUtil.toJson(list));
    }

    @Test
    void quoteEscapesNewlines() {
        assertEquals("\"line1\\nline2\"", JsonUtil.quote("line1\nline2"));
    }

    @Test
    void quoteEscapesBackslash() {
        assertEquals("\"a\\\\b\"", JsonUtil.quote("a\\b"));
    }
}
