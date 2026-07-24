package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.model.PublicationSession;

public interface StoragePublisherService {
    PublicationSession beginPublication();

    void stageBlock(PublicationSession session, String blobName, int blockNumber, byte[] content);

    void commitBlocks(PublicationSession session, String blobName);

    void uploadManifest(String manifestName, String manifestJson);
}
