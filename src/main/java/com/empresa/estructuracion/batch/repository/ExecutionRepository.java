package com.empresa.estructuracion.batch.repository;

import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository {
    Optional<ExecutionContext> findRecoverableExecution(int staleMinutes, int maxAttempts, LocalDateTime now);

    ExecutionContext createExecutionWithSnapshot(BusinessDateCutoff cutoff, int maxAttempts, LocalDateTime now);

    ExecutionContext createDateReprocessExecutionWithSnapshot(
            BusinessDateCutoff cutoff,
            int maxAttempts,
            String requestedBy,
            String reason,
            LocalDateTime now);

    void markInProgress(UUID executionId, boolean retryAttempt, LocalDateTime now);

    void updateHeartbeat(UUID executionId, LocalDateTime now);

    void markPublishing(UUID executionId, LocalDateTime now);

    void complete(BatchResult result, LocalDateTime now);

    void fail(UUID executionId, String errorCode, String failureDetails, LocalDateTime now);
}
