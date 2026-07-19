package com.empresa.estructuracion.batch.util;

import java.util.List;
import java.util.stream.Collectors;

public final class CsvUtils {
    private CsvUtils() {
    }

    public static String line(List<String> values) {
        return values.stream()
                .map(CsvUtils::escape)
                .collect(Collectors.joining(",")) + "\n";
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean mustQuote = value.contains(",")
                || value.contains("\"")
                || value.contains("\r")
                || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return mustQuote ? "\"" + escaped + "\"" : escaped;
    }
}

