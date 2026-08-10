package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.config.BatchProperties;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import com.empresa.estructuracion.batch.model.PublicationSession;
import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import com.empresa.estructuracion.batch.service.DataMapCryptoService;
import com.empresa.estructuracion.batch.service.OutputWriterService;
import com.empresa.estructuracion.batch.service.StoragePublisherService;
import com.empresa.estructuracion.batch.util.HashUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagedOutputServiceTest {
    private static final UUID EXECUTION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void generateShouldReadBatchesPublishBlocksAndUpdateResult() {
        FakeStagingRepository stagingRepository = new FakeStagingRepository(List.of(
                record(10, "cipher-a"),
                record(20, "cipher-b"),
                record(30, "cipher-c")));
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        FakeCryptoService cryptoService = new FakeCryptoService();
        FakeOutputWriterService writerService = new FakeOutputWriterService();
        FakeStoragePublisherService storageService = new FakeStoragePublisherService();
        StagedOutputService service = service(
                stagingRepository,
                executionRepository,
                cryptoService,
                writerService,
                storageService,
                2);

        BatchResult result = service.generate(execution());

        assertEquals(EXECUTION_ID, result.executionId());
        assertEquals("estructuracion_20260717_11111111-2222-3333-4444-555555555555.csv", result.fileName());
        assertEquals(3, result.recordCount());
        assertEquals(2, storageService.blocks.size());
        assertEquals(2, executionRepository.heartbeatCount);
        assertEquals(List.of(0, 20, 30), stagingRepository.lastSourceIds);
        assertTrue(cryptoService.clearKeyCalled);

        String publishedContent = storageService.blocks.get(0) + storageService.blocks.get(1);
        assertEquals("header\nrow-10-plain-cipher-a\nrow-20-plain-cipher-b\nrow-30-plain-cipher-c\n", publishedContent);
        assertEquals(publishedContent.getBytes(StandardCharsets.UTF_8).length, result.contentLength());

        MessageDigest expectedDigest = HashUtils.sha256();
        expectedDigest.update(publishedContent.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expectedDigest.digest(), result.fileHash());
    }

    @Test
    void generateShouldPublishOnlyHeaderWhenStagingIsEmpty() {
        FakeStagingRepository stagingRepository = new FakeStagingRepository(List.of());
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        FakeStoragePublisherService storageService = new FakeStoragePublisherService();
        StagedOutputService service = service(
                stagingRepository,
                executionRepository,
                new FakeCryptoService(),
                new FakeOutputWriterService(),
                storageService,
                100);

        BatchResult result = service.generate(execution());

        assertEquals(0, result.recordCount());
        assertEquals("header\n".getBytes(StandardCharsets.UTF_8).length, result.contentLength());
        assertEquals(List.of("header\n"), storageService.blocks);
        assertEquals(0, executionRepository.heartbeatCount);
    }

    @Test
    void generateShouldUsePlainJsonDataMapWithoutDecrypting() {
        FakeStagingRepository stagingRepository = new FakeStagingRepository(List.of(
                record(10, "{\"cliente\":\"Juan Perez\"}")));
        FakeCryptoService cryptoService = new FakeCryptoService();
        FakeStoragePublisherService storageService = new FakeStoragePublisherService();
        StagedOutputService service = service(
                stagingRepository,
                new FakeExecutionRepository(),
                cryptoService,
                new FakeOutputWriterService(),
                storageService,
                100);

        service.generate(execution());

        assertEquals("header\nrow-10-{\"cliente\":\"Juan Perez\"}\n", storageService.blocks.get(0));
        assertEquals(0, cryptoService.decryptCount);
    }

    private StagedOutputService service(
            StagingRepository stagingRepository,
            ExecutionRepository executionRepository,
            DataMapCryptoService cryptoService,
            OutputWriterService outputWriterService,
            StoragePublisherService storagePublisherService,
            int batchSize) {
        return new StagedOutputService(
                new BatchProperties(ZoneId.of("America/Lima"), batchSize, 15, 3),
                stagingRepository,
                executionRepository,
                cryptoService,
                outputWriterService,
                storagePublisherService);
    }

    private ExecutionContext execution() {
        return new ExecutionContext(
                EXECUTION_ID,
                LocalDate.of(2026, 7, 17),
                Instant.parse("2026-07-17T05:00:00Z"),
                Instant.parse("2026-07-18T05:00:00Z"),
                ExecutionStatus.PREPARING,
                1,
                null,
                null);
    }

    private static StagingRecord record(int sourceId, String dataMap) {
        return new StagingRecord(
                sourceId,
                sourceId,
                "file-" + sourceId + ".pdf",
                true,
                "Cliente",
                Instant.parse("2026-07-17T10:30:00Z"),
                dataMap,
                "DNI",
                false,
                null,
                0);
    }

    private static class FakeStagingRepository implements StagingRepository {
        private final List<StagingRecord> records;
        private final List<Integer> lastSourceIds = new ArrayList<>();

        private FakeStagingRepository(List<StagingRecord> records) {
            this.records = records;
        }

        @Override
        public long count(UUID executionId) {
            return records.size();
        }

        @Override
        public List<StagingRecord> readBatch(UUID executionId, int lastSourceId, int batchSize) {
            lastSourceIds.add(lastSourceId);
            return records.stream()
                    .filter(record -> record.sourceId() > lastSourceId)
                    .limit(batchSize)
                    .toList();
        }
    }

    private static class FakeExecutionRepository implements ExecutionRepository {
        private int heartbeatCount;

        @Override
        public Optional<ExecutionContext> findRecoverableExecution(
                int staleMinutes,
                int maxAttempts,
                LocalDateTime now) {
            return Optional.empty();
        }

        @Override
        public ExecutionContext createExecutionWithSnapshot(
                BusinessDateCutoff cutoff,
                int maxAttempts,
                LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markInProgress(UUID executionId, boolean retryAttempt, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateHeartbeat(UUID executionId, LocalDateTime now) {
            heartbeatCount++;
        }

        @Override
        public void markPublishing(UUID executionId, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(BatchResult result, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fail(UUID executionId, String errorCode, String failureDetails, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeCryptoService implements DataMapCryptoService {
        private final byte[] key = new byte[]{1, 2, 3};
        private boolean clearKeyCalled;
        private int decryptCount;

        @Override
        public byte[] unwrapAesKey() {
            return key;
        }

        @Override
        public String decryptDataMap(String dataMap, byte[] aesKey) {
            assertArrayEquals(key, aesKey);
            decryptCount++;
            return "plain-" + dataMap;
        }

        @Override
        public void clearKey(byte[] aesKey) {
            clearKeyCalled = true;
            assertArrayEquals(key, aesKey);
        }
    }

    private static class FakeOutputWriterService implements OutputWriterService {
        @Override
        public byte[] headerBytes(MessageDigest digest) {
            return bytes("header\n", digest);
        }

        @Override
        public byte[] rowBytes(StagingRecord record, String decryptedDataMap, MessageDigest digest) {
            return bytes("row-%d-%s\n".formatted(record.sourceId(), decryptedDataMap), digest);
        }

        private byte[] bytes(String value, MessageDigest digest) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(bytes);
            return bytes;
        }
    }

    private static class FakeStoragePublisherService implements StoragePublisherService {
        private final List<String> blocks = new ArrayList<>();

        @Override
        public PublicationSession beginPublication() {
            return new PublicationSession();
        }

        @Override
        public void stageBlock(PublicationSession session, String blobName, int blockNumber, byte[] content) {
            blocks.add(new String(content, StandardCharsets.UTF_8));
            session.addBlockId(HexFormat.of().formatHex(new byte[]{(byte) blockNumber}));
        }

        @Override
        public void commitBlocks(PublicationSession session, String blobName) {
            throw new UnsupportedOperationException();
        }
    }
}
