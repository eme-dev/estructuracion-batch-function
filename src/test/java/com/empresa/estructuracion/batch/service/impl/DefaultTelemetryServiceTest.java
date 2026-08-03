package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.model.BatchError;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import com.empresa.estructuracion.batch.model.PublicationSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTelemetryServiceTest {
    private final DefaultTelemetryService telemetryService = new DefaultTelemetryService();

    @Test
    void trackBatchStartedShouldLogExecutionId() {
        CapturingHandler handler = new CapturingHandler();
        Logger logger = logger(handler);
        UUID executionId = UUID.randomUUID();

        telemetryService.trackBatchStarted(logger, executionContext(executionId));

        assertTrue(handler.message.contains("Batch started. executionId=" + executionId));
    }

    @Test
    void trackBatchCompletedShouldLogResultCounters() {
        CapturingHandler handler = new CapturingHandler();
        Logger logger = logger(handler);
        UUID executionId = UUID.randomUUID();
        BatchResult result = new BatchResult(executionId, "file.csv", new PublicationSession(), 10L, 200L, new byte[]{1});

        telemetryService.trackBatchCompleted(logger, result);

        assertTrue(handler.message.contains("Batch completed. executionId=" + executionId));
        assertTrue(handler.message.contains("records=10"));
        assertTrue(handler.message.contains("bytes=200"));
    }

    @Test
    void trackBatchFailedShouldLogErrorDetails() {
        CapturingHandler handler = new CapturingHandler();
        Logger logger = logger(handler);
        UUID executionId = UUID.randomUUID();
        BatchError error = new BatchError(executionId, "TECHNICAL_ERROR", "RuntimeException", "storage down");

        telemetryService.trackBatchFailed(logger, error);

        assertTrue(handler.message.contains("Batch failed. executionId=" + executionId));
        assertTrue(handler.message.contains("errorCode=TECHNICAL_ERROR"));
        assertTrue(handler.message.contains("errorType=RuntimeException"));
        assertTrue(handler.message.contains("message=storage down"));
    }

    private ExecutionContext executionContext(UUID executionId) {
        return new ExecutionContext(
                executionId,
                LocalDate.of(2026, 7, 17),
                Instant.parse("2026-07-17T05:00:00Z"),
                Instant.parse("2026-07-18T05:00:00Z"),
                ExecutionStatus.IN_PROGRESS,
                1,
                null,
                null);
    }

    private Logger logger(CapturingHandler handler) {
        Logger logger = Logger.getLogger("test-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        return logger;
    }

    private static class CapturingHandler extends Handler {
        private String message = "";

        @Override
        public void publish(LogRecord record) {
            message = record.getMessage();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
