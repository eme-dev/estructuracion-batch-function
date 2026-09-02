package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.config.BatchProperties;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.PublicationSession;
import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import com.empresa.estructuracion.batch.service.OutputService;
import com.empresa.estructuracion.batch.service.DataMapCryptoService;
import com.empresa.estructuracion.batch.service.OutputWriterService;
import com.empresa.estructuracion.batch.service.StoragePublisherService;
import com.empresa.estructuracion.batch.util.FileNameUtils;
import com.empresa.estructuracion.batch.util.HashUtils;
import com.empresa.estructuracion.batch.util.JsonUtils;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

public class StagedOutputService implements OutputService {
    private static final Logger LOGGER = Logger.getLogger(StagedOutputService.class.getName());

    private final BatchProperties properties;
    private final StagingRepository stagingRepository;
    private final ExecutionRepository executionRepository;
    private final DataMapCryptoService cryptoService;
    private final OutputWriterService outputWriterService;
    private final StoragePublisherService storagePublisherService;

    public StagedOutputService(
            BatchProperties properties,
            StagingRepository stagingRepository,
            ExecutionRepository executionRepository,
            DataMapCryptoService cryptoService,
            OutputWriterService outputWriterService,
            StoragePublisherService storagePublisherService) {
        this.properties = properties;
        this.stagingRepository = stagingRepository;
        this.executionRepository = executionRepository;
        this.cryptoService = cryptoService;
        this.outputWriterService = outputWriterService;
        this.storagePublisherService = storagePublisherService;
    }

    @Override
    public BatchResult generate(ExecutionContext execution) {
        byte[] aesKey = null;
        try {
            long unwrapStartedAt = System.nanoTime();
            aesKey = cryptoService.unwrapAesKey();
            LOGGER.info("Batch performance. executionId=%s phase=unwrapAesKey elapsedMs=%d"
                    .formatted(execution.executionId(), elapsedMillis(unwrapStartedAt)));
            return generate(execution, aesKey);
        } finally {
            cryptoService.clearKey(aesKey);
        }
    }

    private BatchResult generate(ExecutionContext execution, byte[] aesKey) {
        String outputName = FileNameUtils.outputPath(execution);
        PublicationSession publicationSession = storagePublisherService.beginPublication();
        MessageDigest digest = HashUtils.sha256();
        long contentLength = 0;
        long recordCount = 0;
        int blockNumber = 0;
        int lastSourceId = 0;
        boolean headerWritten = false;
        long totalStartedAt = System.nanoTime();
        long totalReadNanos = 0;
        long totalResolveNanos = 0;
        long totalWriteNanos = 0;
        long totalStageNanos = 0;
        long totalHeartbeatNanos = 0;

        while (true) {
            long readStartedAt = System.nanoTime();
            List<StagingRecord> records = stagingRepository.readBatch(
                    execution.executionId(),
                    lastSourceId,
                    properties.batchSize());
            long readNanos = elapsedNanos(readStartedAt);
            totalReadNanos += readNanos;
            if (records.isEmpty()) {
                break;
            }

            int firstSourceId = records.get(0).sourceId();
            ByteArrayOutputStream block = new ByteArrayOutputStream();
            if (!headerWritten) {
                byte[] header = outputWriterService.headerBytes(digest);
                block.writeBytes(header);
                contentLength += header.length;
                headerWritten = true;
            }

            long resolveNanos = 0;
            long writeNanos = 0;
            for (StagingRecord stagingRecord : records) {
                long resolveStartedAt = System.nanoTime();
                String plainDataMap = resolvePlainDataMap(stagingRecord, aesKey);
                resolveNanos += elapsedNanos(resolveStartedAt);

                long writeStartedAt = System.nanoTime();
                byte[] row = outputWriterService.rowBytes(stagingRecord, plainDataMap, digest);
                writeNanos += elapsedNanos(writeStartedAt);
                block.writeBytes(row);
                contentLength += row.length;
                recordCount++;
                lastSourceId = stagingRecord.sourceId();
            }
            totalResolveNanos += resolveNanos;
            totalWriteNanos += writeNanos;

            byte[] blockBytes = block.toByteArray();
            long stageStartedAt = System.nanoTime();
            storagePublisherService.stageBlock(publicationSession, outputName, ++blockNumber, blockBytes);
            long stageNanos = elapsedNanos(stageStartedAt);
            totalStageNanos += stageNanos;

            long heartbeatStartedAt = System.nanoTime();
            executionRepository.updateHeartbeat(execution.executionId(), now());
            long heartbeatNanos = elapsedNanos(heartbeatStartedAt);
            totalHeartbeatNanos += heartbeatNanos;

            LOGGER.info("Batch performance. executionId=%s block=%d records=%d firstSourceId=%d lastSourceId=%d blockBytes=%d readMs=%d resolveDataMapMs=%d writeCsvMs=%d stageBlobMs=%d heartbeatMs=%d totalRecords=%d totalBytes=%d"
                    .formatted(
                            execution.executionId(),
                            blockNumber,
                            records.size(),
                            firstSourceId,
                            lastSourceId,
                            blockBytes.length,
                            millis(readNanos),
                            millis(resolveNanos),
                            millis(writeNanos),
                            millis(stageNanos),
                            millis(heartbeatNanos),
                            recordCount,
                            contentLength));
        }

        if (!headerWritten) {
            byte[] header = outputWriterService.headerBytes(digest);
            storagePublisherService.stageBlock(publicationSession, outputName, ++blockNumber, header);
            contentLength += header.length;
        }

        LOGGER.info("Batch performance summary. executionId=%s records=%d bytes=%d blocks=%d readMs=%d resolveDataMapMs=%d writeCsvMs=%d stageBlobMs=%d heartbeatMs=%d totalMs=%d"
                .formatted(
                        execution.executionId(),
                        recordCount,
                        contentLength,
                        blockNumber,
                        millis(totalReadNanos),
                        millis(totalResolveNanos),
                        millis(totalWriteNanos),
                        millis(totalStageNanos),
                        millis(totalHeartbeatNanos),
                        elapsedMillis(totalStartedAt)));

        return new BatchResult(
                execution.executionId(),
                outputName,
                publicationSession,
                recordCount,
                contentLength,
                digest.digest());
    }

    private String resolvePlainDataMap(StagingRecord stagingRecord, byte[] aesKey) {
        String dataMap = stagingRecord.dataMap();
        if (JsonUtils.isStructuredJson(dataMap)) {
            return dataMap;
        }
        return cryptoService.decryptDataMap(dataMap, aesKey);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(properties.zoneId());
    }

    private static long elapsedNanos(long startedAt) {
        return System.nanoTime() - startedAt;
    }

    private static long elapsedMillis(long startedAt) {
        return millis(elapsedNanos(startedAt));
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000;
    }
}
