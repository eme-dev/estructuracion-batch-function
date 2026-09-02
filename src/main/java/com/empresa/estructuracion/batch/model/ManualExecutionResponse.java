package com.empresa.estructuracion.batch.model;

import java.util.HexFormat;
import java.util.UUID;

public record ManualExecutionResponse(
        UUID executionId,
        String fileName,
        long recordCount,
        long contentLength,
        String fileHash) {

    public static ManualExecutionResponse from(BatchResult result) {
        return new ManualExecutionResponse(
                result.executionId(),
                result.fileName(),
                result.recordCount(),
                result.contentLength(),
                HexFormat.of().formatHex(result.fileHash()).toUpperCase());
    }
}
