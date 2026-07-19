package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.model.ExecutionContext;

import java.time.format.DateTimeFormatter;

public final class FileNameUtils {
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private FileNameUtils() {
    }

    public static String outputPath(String basePath, ExecutionContext execution) {
        String date = execution.businessDate().format(DateTimeFormatter.ISO_DATE);
        String yyyymmdd = execution.businessDate().format(BASIC_DATE);
        return "%s/businessDate=%s/estructuracion_%s_%s.jsonl"
                .formatted(basePath, date, yyyymmdd, execution.executionId());
    }

    public static String manifestPath(String outputPath) {
        return outputPath.replace(".jsonl", ".manifest.json");
    }
}
