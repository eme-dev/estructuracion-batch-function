package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.exception.DataMapInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {
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
}
