package com.empresa.estructuracion.batch.service.impl;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.empresa.estructuracion.batch.exception.StoragePublicationException;
import com.empresa.estructuracion.batch.model.PublicationSession;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlobStorageServiceTest {
    private final BlobContainerClient containerClient = mock(BlobContainerClient.class);
    private final BlobClient blobClient = mock(BlobClient.class);
    private final BlockBlobClient blockBlobClient = mock(BlockBlobClient.class);
    private final BlobStorageService storageService = new BlobStorageService(containerClient);

    @Test
    void beginPublicationShouldCreateEmptySession() {
        assertEquals(List.of(), storageService.beginPublication().blockIds());
    }

    @Test
    void stageBlockShouldUploadContentAndStoreBlockId() {
        when(containerClient.getBlobClient("estructuracion.csv")).thenReturn(blobClient);
        when(blobClient.getBlockBlobClient()).thenReturn(blockBlobClient);
        PublicationSession session = new PublicationSession();
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);

        storageService.stageBlock(session, "estructuracion.csv", 1, content);

        String expectedBlockId = "YmxvY2stMDAwMDAwMDE=";
        assertEquals(List.of(expectedBlockId), session.blockIds());
        verify(blockBlobClient).stageBlock(eq(expectedBlockId), any(ByteArrayInputStream.class), eq(3L));
    }

    @Test
    void commitBlocksShouldCommitPublishedBlockIds() {
        when(containerClient.getBlobClient("estructuracion.csv")).thenReturn(blobClient);
        when(blobClient.getBlockBlobClient()).thenReturn(blockBlobClient);
        PublicationSession session = new PublicationSession();
        session.addBlockId("block-1");
        session.addBlockId("block-2");

        storageService.commitBlocks(session, "estructuracion.csv");

        verify(blockBlobClient).commitBlockList(List.of("block-1", "block-2"));
    }

    @Test
    void stageBlockShouldWrapStorageFailures() {
        when(containerClient.getBlobClient("estructuracion.csv")).thenThrow(new IllegalStateException("storage down"));

        assertThrows(
                StoragePublicationException.class,
                () -> storageService.stageBlock(new PublicationSession(), "estructuracion.csv", 1, new byte[]{1}));
    }

    @Test
    void commitBlocksShouldWrapStorageFailures() {
        when(containerClient.getBlobClient("estructuracion.csv")).thenThrow(new IllegalStateException("storage down"));

        assertThrows(
                StoragePublicationException.class,
                () -> storageService.commitBlocks(new PublicationSession(), "estructuracion.csv"));
    }
}
