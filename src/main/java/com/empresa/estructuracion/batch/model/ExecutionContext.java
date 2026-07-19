package com.empresa.estructuracion.batch.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExecutionContext(
        UUID executionId,
        LocalDate businessDate,
        Instant cutoffFromUtc,
        Instant cutoffToUtc,
        ExecutionStatus status,
        int attemptCount,
        String fileName,
        byte[] fileHash) {
}

