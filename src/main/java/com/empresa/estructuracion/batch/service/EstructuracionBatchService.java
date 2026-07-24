package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.config.BatchProperties;
import com.empresa.estructuracion.batch.exception.StorageReconciliationRequiredException;
import com.empresa.estructuracion.batch.model.BatchError;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import com.empresa.estructuracion.batch.model.Manifest;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.util.BusinessDateCalculator;
import com.empresa.estructuracion.batch.util.HashUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class EstructuracionBatchService {
    private static final String CONTENT_TYPE = "text/csv; charset=utf-8";

    private final BatchProperties properties;
    private final ExecutionRepository executionRepository;
    private final OutputService outputService;
    private final StoragePublisherService blobStorageService;
    private final ManifestWriterService manifestService;
    private final TelemetryService telemetryService;

    public EstructuracionBatchService(
            BatchProperties properties,
            EstructuracionBatchRepositories repositories,
            EstructuracionBatchServices services) {
        this.properties = properties;
        this.executionRepository = repositories.executionRepository();
        this.outputService = services.outputService();
        this.blobStorageService = services.storagePublisherService();
        this.manifestService = services.manifestWriterService();
        this.telemetryService = services.telemetryService();
    }

    public void execute(Logger logger) {
        ExecutionContext execution = null;
        try {
            BusinessDateCutoff cutoff = BusinessDateCalculator.previousBusinessDate(
                    Clock.systemUTC(),
                    properties.zoneId());
            Optional<ExecutionContext> recoverable =
                    executionRepository.findRecoverableExecution(properties.staleMinutes());
            if (recoverable.isPresent()) {
                execution = recoverable.get();
                if (execution.status() == ExecutionStatus.PUBLISHING) {
                    throw new StorageReconciliationRequiredException(
                            "Publishing execution found. Storage reconciliation is required before continuing.");
                }
                logger.info("Recoverable execution found. Reusing executionId=" + execution.executionId());
            } else {
                execution = executionRepository.createExecutionWithSnapshot(cutoff);
            }

            executionRepository.markInProgress(execution.executionId());
            telemetryService.trackBatchStarted(logger, execution);

            BatchResult result = outputService.generate(execution);

            executionRepository.markPublishing(execution.executionId());
            blobStorageService.commitBlocks(result.publicationSession(), result.fileName());

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
        } catch (RuntimeException ex) {
            handleFailure(logger, execution == null ? null : execution.executionId(), ex);
            throw ex;
        }
    }

    private void handleFailure(Logger logger, UUID executionId, Exception ex) {
        String sanitized = sanitizeFailureMessage(ex);
        if (executionId != null && !(ex instanceof StorageReconciliationRequiredException)) {
            executionRepository.fail(executionId, ex.getClass().getSimpleName(), sanitized);
        }
        telemetryService.trackBatchFailed(logger, new BatchError(
                executionId,
                ex.getClass().getSimpleName(),
                "TECHNICAL",
                sanitized));
    }

    private String sanitizeFailureMessage(Exception ex) {
        return ex.getClass().getSimpleName();
    }
}
