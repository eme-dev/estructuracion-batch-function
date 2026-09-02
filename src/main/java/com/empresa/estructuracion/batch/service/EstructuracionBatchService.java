package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.config.BatchProperties;
import com.empresa.estructuracion.batch.config.RepositoryDependencies;
import com.empresa.estructuracion.batch.config.ServiceDependencies;
import com.empresa.estructuracion.batch.exception.StorageReconciliationRequiredException;
import com.empresa.estructuracion.batch.model.BatchError;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import com.empresa.estructuracion.batch.model.ReprocessDateRequest;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.util.BusinessDateCalculator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class EstructuracionBatchService {
    private static final int FAILURE_DETAILS_MAX_LENGTH = 12000;

    private final BatchProperties properties;
    private final ExecutionRepository executionRepository;
    private final OutputService outputService;
    private final StoragePublisherService blobStorageService;
    private final TelemetryService telemetryService;
    private final Clock clock;

    public EstructuracionBatchService(
            BatchProperties properties,
            RepositoryDependencies repositories,
            ServiceDependencies services) {
        this(properties, repositories, services, Clock.systemUTC());
    }

    EstructuracionBatchService(
            BatchProperties properties,
            RepositoryDependencies repositories,
            ServiceDependencies services,
            Clock clock) {
        this.properties = properties;
        this.executionRepository = repositories.executionRepository();
        this.outputService = services.outputService();
        this.blobStorageService = services.storagePublisherService();
        this.telemetryService = services.telemetryService();
        this.clock = clock;
    }

    public void execute(Logger logger) {
        ExecutionContext execution = null;
        try {
            BusinessDateCutoff cutoff = BusinessDateCalculator.previousBusinessDate(
                    clock,
                    properties.zoneId());
            long recoverableStartedAt = System.nanoTime();
            Optional<ExecutionContext> recoverable =
                    executionRepository.findRecoverableExecution(
                            properties.staleMinutes(),
                            properties.maxAttempts(),
                            now());
            logPerformance(logger, null, "findRecoverableExecution", recoverableStartedAt);
            boolean retryAttempt = recoverable.isPresent();
            if (recoverable.isPresent()) {
                execution = recoverable.get();
                if (execution.status() == ExecutionStatus.PUBLISHING) {
                    throw new StorageReconciliationRequiredException(
                            "Publishing execution found. Storage reconciliation is required before continuing.");
                }
                logger.info("Recoverable execution found. Reusing executionId=" + execution.executionId());
            } else {
                long snapshotStartedAt = System.nanoTime();
                execution = executionRepository.createExecutionWithSnapshot(cutoff, properties.maxAttempts(), now());
                logPerformance(logger, execution.executionId(), "createDailySnapshot", snapshotStartedAt);
            }

            processExecution(logger, execution, retryAttempt);
        } catch (RuntimeException ex) {
            handleFailure(logger, execution == null ? null : execution.executionId(), ex);
            throw ex;
        }
    }

    public BatchResult executeDateReprocess(ReprocessDateRequest request, Logger logger) {
        validateReprocessDateRequest(request);
        ExecutionContext execution = null;
        try {
            BusinessDateCutoff cutoff = BusinessDateCalculator.forBusinessDate(
                    request.businessDate(),
                    properties.zoneId());
            long snapshotStartedAt = System.nanoTime();
            execution = executionRepository.createDateReprocessExecutionWithSnapshot(
                    cutoff,
                    properties.maxAttempts(),
                    request.requestedBy().trim(),
                    request.reason().trim(),
                    now());
            logPerformance(logger, execution.executionId(), "createDateReprocessSnapshot", snapshotStartedAt);
            return processExecution(logger, execution, false);
        } catch (RuntimeException ex) {
            handleFailure(logger, execution == null ? null : execution.executionId(), ex);
            throw ex;
        }
    }

    private BatchResult processExecution(Logger logger, ExecutionContext execution, boolean retryAttempt) {
        long markInProgressStartedAt = System.nanoTime();
        executionRepository.markInProgress(execution.executionId(), retryAttempt, now());
        logPerformance(logger, execution.executionId(), "markInProgress", markInProgressStartedAt);
        telemetryService.trackBatchStarted(logger, execution);

        long generateStartedAt = System.nanoTime();
        BatchResult result = outputService.generate(execution);
        logPerformance(logger, execution.executionId(), "generateOutput", generateStartedAt);

        long markPublishingStartedAt = System.nanoTime();
        executionRepository.markPublishing(execution.executionId(), now());
        logPerformance(logger, execution.executionId(), "markPublishing", markPublishingStartedAt);

        long commitStartedAt = System.nanoTime();
        blobStorageService.commitBlocks(result.publicationSession(), result.fileName());
        logPerformance(logger, execution.executionId(), "commitBlocks", commitStartedAt);

        long completeStartedAt = System.nanoTime();
        executionRepository.complete(result, now());
        logPerformance(logger, execution.executionId(), "completeExecution", completeStartedAt);
        telemetryService.trackBatchCompleted(logger, result);
        return result;
    }

    private void validateReprocessDateRequest(ReprocessDateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (request.businessDate() == null) {
            throw new IllegalArgumentException("businessDate is required.");
        }
        if (request.requestedBy() == null || request.requestedBy().isBlank()) {
            throw new IllegalArgumentException("requestedBy is required.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("reason is required.");
        }
    }

    private void handleFailure(Logger logger, UUID executionId, Exception ex) {
        String failureDetails = buildFailureDetails(ex);
        if (executionId != null && !(ex instanceof StorageReconciliationRequiredException)) {
            executionRepository.fail(executionId, ex.getClass().getSimpleName(), failureDetails, now());
        }
        telemetryService.trackBatchFailed(logger, new BatchError(
                executionId,
                ex.getClass().getSimpleName(),
                "TECHNICAL",
                failureDetails));
    }

    private String buildFailureDetails(Exception ex) {
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            ex.printStackTrace(printWriter);
        }
        String details = writer.toString();
        return details.substring(0, Math.min(details.length(), FAILURE_DETAILS_MAX_LENGTH));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(properties.zoneId());
    }

    private static void logPerformance(Logger logger, UUID executionId, String phase, long startedAt) {
        logger.info("Batch performance. executionId=%s phase=%s elapsedMs=%d"
                .formatted(executionId, phase, elapsedMillis(startedAt)));
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
