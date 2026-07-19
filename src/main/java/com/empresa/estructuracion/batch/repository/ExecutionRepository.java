package com.empresa.estructuracion.batch.repository;

import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository {
    Optional<ExecutionContext> findRecoverableExecution(int staleMinutes);

    ExecutionContext createExecutionWithSnapshot(BusinessDateCutoff cutoff);

    void markInProgress(UUID executionId);

    void updateHeartbeat(UUID executionId);

    void markPublishing(UUID executionId);

    void complete(BatchResult result);

    void fail(UUID executionId, String errorCode, String sanitizedMessage);
}

