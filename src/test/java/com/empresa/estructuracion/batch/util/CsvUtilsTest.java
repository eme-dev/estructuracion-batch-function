package com.empresa.estructuracion.batch.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvUtilsTest {

    @Test
    void lineShouldJoinSimpleValuesWithPipeAndLineFeed() {
        String line = CsvUtils.line(List.of("file.pdf", "true", "Cliente"));

        assertEquals("file.pdf|true|Cliente\n", line);
    }

    @Test
    void lineShouldEscapeValuesThatContainPipeQuoteOrLineBreak() {
        String line = CsvUtils.line(List.of(
                "Juan|Perez",
                "texto \"con comillas\"",
                "linea\nnueva"));

        assertEquals("\"Juan|Perez\"|\"texto \"\"con comillas\"\"\"|\"linea\nnueva\"\n", line);
    }

    @Test
    void lineShouldRenderNullAsEmptyColumn() {
        List<String> values = new ArrayList<>();
        values.add("inicio");
        values.add(null);
        values.add("fin");

        String line = CsvUtils.line(values);

        assertEquals("inicio||fin\n", line);
    }

    @Test
    void escapeShouldLeaveCommaWithoutQuotesWhenDelimiterIsPipe() {
        assertEquals("Juan, Perez", CsvUtils.escape("Juan, Perez"));
    }

    @Test
    void escapeShouldLeaveSimpleValueWithoutQuotes() {
        assertEquals("DNI", CsvUtils.escape("DNI"));
    }

    @Test
    void escapeShouldQuoteCarriageReturn() {
        assertEquals("\"linea\rretorno\"", CsvUtils.escape("linea\rretorno"));
    }
}
