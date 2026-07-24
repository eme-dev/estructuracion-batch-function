package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.config.BatchProperties;
import com.empresa.estructuracion.batch.exception.StorageReconciliationRequiredException;
import com.empresa.estructuracion.batch.model.BatchError;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.util.BusinessDateCalculator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class EstructuracionBatchService {
    private final BatchProperties properties;
    private final ExecutionRepository executionRepository;
    private final OutputService outputService;
    private final StoragePublisherService blobStorageService;
    private final TelemetryService telemetryService;

    public EstructuracionBatchService(
            BatchProperties properties,
            EstructuracionBatchRepositories repositories,
            EstructuracionBatchServices services) {
        this.properties = properties;
        this.executionRepository = repositories.executionRepository();
        this.outputService = services.outputService();
        this.blobStorageService = services.storagePublisherService();
        this.telemetryService = services.telemetryService();
    }

    public void execute(Logger logger) {
        ExecutionContext execution = null;
        try {
            BusinessDateCutoff cutoff = BusinessDateCalculator.previousBusinessDate(
                    Clock.systemUTC(),
                    properties.zoneId());
            Optional<ExecutionContext> recoverable =
                    executionRepository.findRecoverableExecution(properties.staleMinutes(), now());
            if (recoverable.isPresent()) {
                execution = recoverable.get();
                if (execution.status() == ExecutionStatus.PUBLISHING) {
                    throw new StorageReconciliationRequiredException(
                            "Publishing execution found. Storage reconciliation is required before continuing.");
                }
                logger.info("Recoverable execution found. Reusing executionId=" + execution.executionId());
            } else {
                execution = executionRepository.createExecutionWithSnapshot(cutoff, now());
            }

            executionRepository.markInProgress(execution.executionId(), now());
            telemetryService.trackBatchStarted(logger, execution);

            BatchResult result = outputService.generate(execution);

            executionRepository.markPublishing(execution.executionId(), now());
            blobStorageService.commitBlocks(result.publicationSession(), result.fileName());

            executionRepository.complete(result, now());
            telemetryService.trackBatchCompleted(logger, result);
        } catch (RuntimeException ex) {
            handleFailure(logger, execution == null ? null : execution.executionId(), ex);
            throw ex;
        }
    }

    private void handleFailure(Logger logger, UUID executionId, Exception ex) {
        String sanitized = sanitizeFailureMessage(ex);
        if (executionId != null && !(ex instanceof StorageReconciliationRequiredException)) {
            executionRepository.fail(executionId, ex.getClass().getSimpleName(), sanitized, now());
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

    private LocalDateTime now() {
        return LocalDateTime.now(properties.zoneId());
    }
}
