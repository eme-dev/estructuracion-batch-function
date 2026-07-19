package com.empresa.estructuracion.batch.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BusinessDateCutoff(
        LocalDate businessDate,
        LocalDateTime cutoffFromLocal,
        LocalDateTime cutoffToLocal,
        Instant cutoffFromUtc,
        Instant cutoffToUtc) {
}

