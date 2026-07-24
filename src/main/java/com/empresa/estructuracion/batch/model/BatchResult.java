package com.empresa.estructuracion.batch.model;

import java.util.UUID;

public record BatchResult(
        UUID executionId,
        String fileName,
        PublicationSession publicationSession,
        long recordCount,
        long contentLength,
        byte[] fileHash) {
}
