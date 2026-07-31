package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.exception.DataMapInvalidException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonUtilsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void compactsValidJson() {
        String compact = JsonUtils.compact("""
                {
                  "cliente": "Juan Perez",
                  "monto": 100.5
                }
                """, objectMapper);

        assertEquals("{\"cliente\":\"Juan Perez\",\"monto\":100.5}", compact);
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(
                DataMapInvalidException.class,
                () -> JsonUtils.compact("{\"cliente\":", objectMapper));
    }
}
