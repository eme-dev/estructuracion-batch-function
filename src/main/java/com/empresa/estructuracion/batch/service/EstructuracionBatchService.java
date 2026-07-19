package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.config.BatchProperties;
import com.empresa.estructuracion.batch.exception.BatchException;
import com.empresa.estructuracion.batch.model.BatchError;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import com.empresa.estructuracion.batch.model.Manifest;
import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import com.empresa.estructuracion.batch.util.BusinessDateCalculator;
import com.empresa.estructuracion.batch.util.FileNameUtils;
import com.empresa.estructuracion.batch.util.HashUtils;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class EstructuracionBatchService {
    private static final String CONTENT_TYPE = "text/csv; charset=utf-8";

    private final BatchProperties properties;
    private final ExecutionRepository executionRepository;
    private final StagingRepository stagingRepository;
    private final DataMapCryptoService cryptoService;
    private final CsvWriterService csvGenerationService;
    private final StoragePublisherService blobStorageService;
    private final ManifestWriterService manifestService;
    private final BatchTelemetryService telemetryService;

    public EstructuracionBatchService(
            BatchProperties properties,
            ExecutionRepository executionRepository,
            StagingRepository stagingRepository,
            DataMapCryptoService cryptoService,
            CsvWriterService csvGenerationService,
            StoragePublisherService blobStorageService,
            ManifestWriterService manifestService,
            BatchTelemetryService telemetryService) {
        this.properties = properties;
        this.executionRepository = executionRepository;
        this.stagingRepository = stagingRepository;
        this.cryptoService = cryptoService;
        this.csvGenerationService = csvGenerationService;
        this.blobStorageService = blobStorageService;
        this.manifestService = manifestService;
        this.telemetryService = telemetryService;
    }

    public void execute(Logger logger) {
        ExecutionContext execution = null;
        byte[] aesKey = null;
        try {
            BusinessDateCutoff cutoff = BusinessDateCalculator.previousBusinessDate(
                    Clock.systemUTC(),
                    properties.zoneId());
            Optional<ExecutionContext> recoverable =
                    executionRepository.findRecoverableExecution(properties.staleMinutes());
            if (recoverable.isPresent()) {
                execution = recoverable.get();
                if (execution.status() == ExecutionStatus.PUBLISHING) {
                    throw new BatchException("Publishing execution found. Storage reconciliation is required before continuing.");
                }
                logger.info("Recoverable execution found. Reusing executionId=" + execution.executionId());
            } else {
                execution = executionRepository.createExecutionWithSnapshot(cutoff);
            }

            executionRepository.markInProgress(execution.executionId());
            telemetryService.trackBatchStarted(logger, execution);

            aesKey = cryptoService.unwrapAesKey();
            BatchResult result = generateAndPublishCsv(execution, aesKey);

            executionRepository.markPublishing(execution.executionId());
            blobStorageService.commitBlocks(result.fileName());

            Manifest manifest = new Manifest(
                    execution.executionId(),
                    execution.businessDate(),
                    result.recordCount(),
                    result.contentLength(),
                    HashUtils.hex(result.fileHash()),
                    CONTENT_TYPE,
                    Instant.now());
            blobStorageService.uploadManifest(result.manifestFileName(), manifestService.toJson(manifest));
            executionRepository.complete(result);
            telemetryService.trackBatchCompleted(logger, result);
        } catch (Exception ex) {
            handleFailure(logger, execution == null ? null : execution.executionId(), ex);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new BatchException("Batch failed.", ex);
        } finally {
            cryptoService.clearKey(aesKey);
        }
    }

    private BatchResult generateAndPublishCsv(ExecutionContext execution, byte[] aesKey) {
        String csvName = FileNameUtils.csvPath(properties.storageBasePath(), execution);
        String manifestName = FileNameUtils.manifestPath(csvName);
        MessageDigest digest = HashUtils.sha256();
        long contentLength = 0;
        long recordCount = 0;
        int blockNumber = 0;
        int lastSourceId = 0;
        boolean headerWritten = false;
        blobStorageService.beginPublication();

        while (true) {
            List<StagingRecord> records = stagingRepository.readBatch(
                    execution.executionId(),
                    lastSourceId,
                    properties.batchSize());
            if (records.isEmpty()) {
                break;
            }

            ByteArrayOutputStream block = new ByteArrayOutputStream();
            if (!headerWritten) {
                byte[] header = csvGenerationService.headerBytes(digest);
                block.writeBytes(header);
                contentLength += header.length;
                headerWritten = true;
            }

            for (StagingRecord record : records) {
                String decryptedDataMap = cryptoService.decryptDataMap(record.encryptedDataMap(), aesKey);
                byte[] row = csvGenerationService.rowBytes(record, decryptedDataMap, digest);
                block.writeBytes(row);
                contentLength += row.length;
                recordCount++;
                lastSourceId = record.sourceId();
            }

            blobStorageService.stageBlock(csvName, ++blockNumber, block.toByteArray());
            executionRepository.updateHeartbeat(execution.executionId());
        }

        if (!headerWritten) {
            byte[] header = csvGenerationService.headerBytes(digest);
            blobStorageService.stageBlock(csvName, ++blockNumber, header);
            contentLength += header.length;
        }

        return new BatchResult(
                execution.executionId(),
                csvName,
                manifestName,
                recordCount,
                contentLength,
                digest.digest());
    }

    private void handleFailure(Logger logger, UUID executionId, Exception ex) {
        String sanitized = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (executionId != null) {
            executionRepository.fail(executionId, ex.getClass().getSimpleName(), sanitized);
        }
        telemetryService.trackBatchFailed(logger, new BatchError(
                executionId,
                ex.getClass().getSimpleName(),
                "TECHNICAL",
                sanitized));
    }
}
