package com.empresa.estructuracion.batch.model;

import java.time.LocalDate;

public record ReprocessDateRequest(
        LocalDate businessDate,
        String requestedBy,
        String reason) {
}
