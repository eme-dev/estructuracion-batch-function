package com.empresa.estructuracion.batch.model;

import java.time.Instant;

public record StagingRecord(
        long stagingId,
        int sourceId,
        String fileName,
        boolean statusFile,
        String clientName,
        Instant creationDateTime,
        String dataMap,
        String documentType,
        boolean isReprocessed,
        Instant reprocessDateTime,
        int reprocessCount) {
}
