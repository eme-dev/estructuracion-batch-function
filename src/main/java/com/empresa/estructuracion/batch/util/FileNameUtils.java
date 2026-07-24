package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.model.ExecutionContext;

import java.time.format.DateTimeFormatter;

public final class FileNameUtils {
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private FileNameUtils() {
    }

    public static String outputPath(ExecutionContext execution) {
        String yyyymmdd = execution.businessDate().format(BASIC_DATE);
        return "estructuracion_%s_%s.csv".formatted(yyyymmdd, execution.executionId());
    }

}
