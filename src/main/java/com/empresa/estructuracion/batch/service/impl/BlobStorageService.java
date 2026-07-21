package com.empresa.estructuracion.batch.service.impl;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.empresa.estructuracion.batch.config.StorageConfig;
import com.empresa.estructuracion.batch.exception.StoragePublicationException;
import com.empresa.estructuracion.batch.model.PublicationSession;
import com.empresa.estructuracion.batch.service.StoragePublisherService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BlobStorageService implements StoragePublisherService {
    private final BlobContainerClient containerClient;

    public BlobStorageService(StorageConfig storageConfig) {
        this.containerClient = new BlobServiceClientBuilder()
                .connectionString(storageConfig.connectionString())
                .buildClient()
                .getBlobContainerClient(storageConfig.containerName());
    }

    @Override
    public PublicationSession beginPublication() {
        return new PublicationSession();
    }

    @Override
    public void stageBlock(PublicationSession session, String blobName, int blockNumber, byte[] content) {
        try {
            BlockBlobClient blockBlobClient = containerClient.getBlobClient(blobName).getBlockBlobClient();
            String blockId = blockId(blockNumber);
            blockBlobClient.stageBlock(blockId, BinaryData.fromBytes(content));
            session.addBlockId(blockId);
        } catch (Exception ex) {
            throw new StoragePublicationException("Unable to stage output block.", ex);
        }
    }

    @Override
    public void commitBlocks(PublicationSession session, String blobName) {
        try {
            BlockBlobClient blockBlobClient = containerClient.getBlobClient(blobName).getBlockBlobClient();
            blockBlobClient.commitBlockList(session.blockIds());
        } catch (Exception ex) {
            throw new StoragePublicationException("Unable to commit output blocks.", ex);
        }
    }

    @Override
    public void uploadEmptyBlob(String blobName) {
        try {
            containerClient.getBlobClient(blobName)
                    .upload(BinaryData.fromBytes(new byte[0]), true);
        } catch (Exception ex) {
            throw new StoragePublicationException("Unable to upload empty output blob.", ex);
        }
    }

    @Override
    public void uploadManifest(String manifestName, String manifestJson) {
        try {
            containerClient.getBlobClient(manifestName)
                    .upload(BinaryData.fromString(manifestJson), true);
        } catch (Exception ex) {
            throw new StoragePublicationException("Unable to upload manifest.", ex);
        }
    }

    private String blockId(int blockNumber) {
        return Base64.getEncoder().encodeToString(
                "block-%08d".formatted(blockNumber).getBytes(StandardCharsets.UTF_8));
    }
}
