package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.exception.DataMapInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String compact(String json, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new DataMapInvalidException("dataMap decrypted content is not valid JSON.", ex);
        }
    }

    public static boolean isStructuredJson(String json) {
        if (json == null) {
            return false;
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return false;
        }
        try {
            DEFAULT_OBJECT_MAPPER.readTree(trimmed);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
