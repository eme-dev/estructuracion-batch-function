package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.model.BatchError;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.service.BatchTelemetryService;

import java.util.logging.Logger;

public class TelemetryService implements BatchTelemetryService {
    @Override
    public void trackBatchStarted(Logger logger, ExecutionContext execution) {
        logger.info("Batch started. executionId=" + execution.executionId());
    }

    @Override
    public void trackBatchCompleted(Logger logger, BatchResult result) {
        logger.info("Batch completed. executionId=%s records=%d bytes=%d"
                .formatted(result.executionId(), result.recordCount(), result.contentLength()));
    }

    @Override
    public void trackBatchFailed(Logger logger, BatchError error) {
        logger.severe("Batch failed. executionId=%s errorCode=%s errorType=%s message=%s"
                .formatted(error.executionId(), error.errorCode(), error.errorType(), error.sanitizedMessage()));
    }
}
