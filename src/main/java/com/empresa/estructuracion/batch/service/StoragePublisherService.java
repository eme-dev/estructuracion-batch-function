package com.empresa.estructuracion.batch.service;

public interface StoragePublisherService {
    void beginPublication();

    void stageBlock(String blobName, int blockNumber, byte[] content);

    void commitBlocks(String blobName);

    void uploadEmptyBlob(String blobName);

    void uploadManifest(String manifestName, String manifestJson);
}
