package com.empresa.estructuracion.batch.model;

import java.time.Instant;

public record StagingRecord(
        long stagingId,
        int sourceId,
        String fileName,
        boolean statusFile,
        String clientName,
        Instant creationDateTime,
        String encryptedDataMap,
        String listaTables,
        String documentType) {
}
