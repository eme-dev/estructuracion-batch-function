package com.empresa.estructuracion.batch.config;

import java.time.ZoneId;
import java.util.Objects;

public record BatchProperties(
        ZoneId zoneId,
        int batchSize,
        int staleMinutes,
        int maxAttempts) {
    public BatchProperties {
        Objects.requireNonNull(zoneId, "zoneId must not be null.");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero.");
        }
        if (staleMinutes <= 0) {
            throw new IllegalArgumentException("staleMinutes must be greater than zero.");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be greater than zero.");
        }
    }
}
