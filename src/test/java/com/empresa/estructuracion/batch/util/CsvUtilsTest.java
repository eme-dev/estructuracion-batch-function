package com.empresa.estructuracion.batch.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvUtilsTest {

    @Test
    void escapesValuesUsingStandardCsvRules() {
        String line = CsvUtils.line(List.of(
                "simple",
                "Juan, Perez",
                "texto \"entre comillas\"",
                "linea\nnueva"));

        assertEquals("simple,\"Juan, Perez\",\"texto \"\"entre comillas\"\"\",\"linea\nnueva\"\n", line);
    }

    @Test
    void writesNullAsEmptyCsvValue() {
        String line = CsvUtils.line(List.of("inicio", null, "fin"));

        assertEquals("inicio,,fin\n", line);
    }
}
