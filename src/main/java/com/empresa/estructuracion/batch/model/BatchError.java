package com.empresa.estructuracion.batch.model;

import java.util.UUID;

public record BatchError(
        UUID executionId,
        String errorCode,
        String errorType,
        String sanitizedMessage) {
}

