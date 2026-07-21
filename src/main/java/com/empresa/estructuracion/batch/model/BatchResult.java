package com.empresa.estructuracion.batch.model;

import java.util.UUID;

public record BatchResult(
        UUID executionId,
        String fileName,
        String manifestFileName,
        PublicationSession publicationSession,
        long recordCount,
        long contentLength,
        byte[] fileHash) {
}
