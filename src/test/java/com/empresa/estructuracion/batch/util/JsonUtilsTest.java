package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.exception.DataMapInvalidException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void compactShouldReturnCanonicalJsonWithoutWhitespace() {
        String compact = JsonUtils.compact("""
                {
                  "cliente": "Juan Perez",
                  "monto": 100.5,
                  "activo": true
                }
                """, objectMapper);

        assertEquals("{\"cliente\":\"Juan Perez\",\"monto\":100.5,\"activo\":true}", compact);
    }

    @Test
    void compactShouldRejectInvalidJson() {
        assertThrows(
                DataMapInvalidException.class,
                () -> JsonUtils.compact("{\"cliente\":", objectMapper));
    }

    @Test
    void isStructuredJsonShouldAcceptObjectsAndArrays() {
        assertTrue(JsonUtils.isStructuredJson(" {\"cliente\":\"Juan\"} "));
        assertTrue(JsonUtils.isStructuredJson("[{\"cliente\":\"Juan\"}]"));
    }

    @Test
    void isStructuredJsonShouldRejectInvalidJsonAndScalarValues() {
        assertFalse(JsonUtils.isStructuredJson("{\"cliente\":"));
        assertFalse(JsonUtils.isStructuredJson("\"texto\""));
        assertFalse(JsonUtils.isStructuredJson("123"));
        assertFalse(JsonUtils.isStructuredJson(null));
    }
}
