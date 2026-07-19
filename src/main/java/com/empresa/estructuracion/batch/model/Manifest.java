package com.empresa.estructuracion.batch.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Manifest(
        UUID executionId,
        LocalDate businessDate,
        long recordCount,
        long contentLength,
        String sha256,
        String contentType,
        Instant generatedAtUtc) {
}

