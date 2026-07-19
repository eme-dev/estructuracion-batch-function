package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.model.BatchError;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.ExecutionContext;

import java.util.logging.Logger;

public interface BatchTelemetryService {
    void trackBatchStarted(Logger logger, ExecutionContext execution);

    void trackBatchCompleted(Logger logger, BatchResult result);

    void trackBatchFailed(Logger logger, BatchError error);
}

