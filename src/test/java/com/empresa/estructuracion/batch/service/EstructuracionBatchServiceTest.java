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
import com.empresa.estructuracion.batch.model.PublicationSession;
import com.empresa.estructuracion.batch.model.ReprocessDateRequest;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstructuracionBatchServiceTest {
    private static final UUID EXECUTION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Logger LOGGER = Logger.getLogger(EstructuracionBatchServiceTest.class.getName());

    @Test
    void executeShouldCreateSnapshotGenerateCommitAndCompleteWhenNoRecoverableExecutionExists() {
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        FakeOutputService outputService = new FakeOutputService();
        FakeStoragePublisherService storageService = new FakeStoragePublisherService();
        FakeTelemetryService telemetryService = new FakeTelemetryService();
        EstructuracionBatchService service = service(
                executionRepository,
                outputService,
                storageService,
                telemetryService);

        service.execute(LOGGER);

        assertEquals(15, executionRepository.findRecoverableStaleMinutes);
        assertEquals(3, executionRepository.findRecoverableMaxAttempts);
        assertEquals(LocalDate.of(2026, 7, 31), executionRepository.createdCutoff.businessDate());
        assertEquals(3, executionRepository.createMaxAttempts);
        assertEquals(List.of("markInProgress", "markPublishing", "complete"), executionRepository.events);
        assertEquals(List.of(false), executionRepository.retryAttempts);
        assertSame(executionRepository.createdExecution, outputService.execution);
        assertEquals(outputService.result.publicationSession(), storageService.committedSession);
        assertEquals(outputService.result.fileName(), storageService.committedBlobName);
        assertEquals(1, telemetryService.startedCount);
        assertEquals(1, telemetryService.completedCount);
        assertEquals(0, telemetryService.failed.size());
    }

    @Test
    void executeShouldReuseRecoverableExecution() {
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        executionRepository.recoverable = Optional.of(execution(ExecutionStatus.FAILED));
        FakeOutputService outputService = new FakeOutputService();
        EstructuracionBatchService service = service(
                executionRepository,
                outputService,
                new FakeStoragePublisherService(),
                new FakeTelemetryService());

        service.execute(LOGGER);

        assertEquals(0, executionRepository.createCount);
        assertSame(executionRepository.recoverable.orElseThrow(), outputService.execution);
        assertEquals(List.of("markInProgress", "markPublishing", "complete"), executionRepository.events);
        assertEquals(List.of(true), executionRepository.retryAttempts);
    }

    @Test
    void executeShouldSupportDefaultClockConstructor() {
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        executionRepository.recoverable = Optional.of(execution(ExecutionStatus.FAILED));
        FakeOutputService outputService = new FakeOutputService();
        EstructuracionBatchService service = new EstructuracionBatchService(
                new BatchProperties(ZoneId.of("America/Lima"), 100, 15, 3),
                new RepositoryDependencies(executionRepository, new UnusedStagingRepository()),
                new ServiceDependencies(outputService, new FakeStoragePublisherService(), new FakeTelemetryService()));

        service.execute(LOGGER);

        assertEquals(0, executionRepository.createCount);
        assertSame(executionRepository.recoverable.orElseThrow(), outputService.execution);
    }

    @Test
    void executeShouldRequireManualReconciliationWhenRecoverableExecutionIsPublishing() {
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        executionRepository.recoverable = Optional.of(execution(ExecutionStatus.PUBLISHING));
        FakeTelemetryService telemetryService = new FakeTelemetryService();
        EstructuracionBatchService service = service(
                executionRepository,
                new FakeOutputService(),
                new FakeStoragePublisherService(),
                telemetryService);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.execute(LOGGER));

        assertInstanceOf(StorageReconciliationRequiredException.class, ex);
        assertEquals(List.of(), executionRepository.events);
        assertEquals(0, executionRepository.failCount);
        assertEquals(1, telemetryService.failed.size());
    }

    @Test
    void executeShouldMarkExecutionFailedWhenOutputGenerationFails() {
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        RuntimeException expected = new IllegalStateException("boom");
        FakeOutputService outputService = new FakeOutputService();
        outputService.failure = expected;
        FakeTelemetryService telemetryService = new FakeTelemetryService();
        EstructuracionBatchService service = service(
                executionRepository,
                outputService,
                new FakeStoragePublisherService(),
                telemetryService);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.execute(LOGGER));

        assertSame(expected, ex);
        assertEquals(1, executionRepository.failCount);
        assertEquals(EXECUTION_ID, executionRepository.failedExecutionId);
        assertEquals("IllegalStateException", executionRepository.failedErrorCode);
        assertTrue(executionRepository.failedDetails.contains("boom"));
        assertEquals(List.of("markInProgress"), executionRepository.events);
        assertEquals(1, telemetryService.failed.size());
        assertTrue(telemetryService.failed.get(0).failureDetails().contains("boom"));
    }

    @Test
    void executeDateReprocessShouldCreateIndependentSnapshotAndPublishOutput() {
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        FakeOutputService outputService = new FakeOutputService();
        FakeStoragePublisherService storageService = new FakeStoragePublisherService();
        FakeTelemetryService telemetryService = new FakeTelemetryService();
        EstructuracionBatchService service = service(
                executionRepository,
                outputService,
                storageService,
                telemetryService);

        BatchResult result = service.executeDateReprocess(
                new ReprocessDateRequest(
                        LocalDate.of(2026, 7, 30),
                        " soporte01 ",
                        " Correccion de corte "),
                LOGGER);

        assertSame(outputService.result, result);
        assertEquals(LocalDate.of(2026, 7, 30), executionRepository.reprocessCutoff.businessDate());
        assertEquals(3, executionRepository.reprocessMaxAttempts);
        assertEquals("soporte01", executionRepository.reprocessRequestedBy);
        assertEquals("Correccion de corte", executionRepository.reprocessReason);
        assertEquals(List.of("markInProgress", "markPublishing", "complete"), executionRepository.events);
        assertEquals(List.of(false), executionRepository.retryAttempts);
        assertSame(executionRepository.createdExecution, outputService.execution);
        assertEquals(outputService.result.publicationSession(), storageService.committedSession);
        assertEquals(1, telemetryService.completedCount);
    }

    @Test
    void executeDateReprocessShouldRejectMissingBusinessDate() {
        EstructuracionBatchService service = service(
                new FakeExecutionRepository(),
                new FakeOutputService(),
                new FakeStoragePublisherService(),
                new FakeTelemetryService());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.executeDateReprocess(new ReprocessDateRequest(null, "soporte01", "motivo"), LOGGER));

        assertEquals("businessDate is required.", ex.getMessage());
    }

    private EstructuracionBatchService service(
            FakeExecutionRepository executionRepository,
            OutputService outputService,
            StoragePublisherService storageService,
            TelemetryService telemetryService) {
        return new EstructuracionBatchService(
                new BatchProperties(ZoneId.of("America/Lima"), 100, 15, 3),
                new RepositoryDependencies(executionRepository, new UnusedStagingRepository()),
                new ServiceDependencies(outputService, storageService, telemetryService),
                Clock.fixed(Instant.parse("2026-08-01T05:10:00Z"), ZoneId.of("UTC")));
    }

    private static ExecutionContext execution(ExecutionStatus status) {
        return new ExecutionContext(
                EXECUTION_ID,
                LocalDate.of(2026, 7, 31),
                Instant.parse("2026-07-31T05:00:00Z"),
                Instant.parse("2026-08-01T05:00:00Z"),
                status,
                1,
                null,
                null);
    }

    private static class FakeExecutionRepository implements ExecutionRepository {
        private Optional<ExecutionContext> recoverable = Optional.empty();
        private ExecutionContext createdExecution;
        private BusinessDateCutoff createdCutoff;
        private BusinessDateCutoff reprocessCutoff;
        private int createCount;
        private int findRecoverableStaleMinutes;
        private int findRecoverableMaxAttempts;
        private int createMaxAttempts;
        private int reprocessMaxAttempts;
        private String reprocessRequestedBy;
        private String reprocessReason;
        private final List<String> events = new ArrayList<>();
        private final List<Boolean> retryAttempts = new ArrayList<>();
        private int failCount;
        private UUID failedExecutionId;
        private String failedErrorCode;
        private String failedDetails;

        @Override
        public Optional<ExecutionContext> findRecoverableExecution(
                int staleMinutes,
                int maxAttempts,
                LocalDateTime now) {
            findRecoverableStaleMinutes = staleMinutes;
            findRecoverableMaxAttempts = maxAttempts;
            return recoverable;
        }

        @Override
        public ExecutionContext createExecutionWithSnapshot(
                BusinessDateCutoff cutoff,
                int maxAttempts,
                LocalDateTime now) {
            createCount++;
            createdCutoff = cutoff;
            createMaxAttempts = maxAttempts;
            createdExecution = execution(ExecutionStatus.PREPARING);
            return createdExecution;
        }

        @Override
        public ExecutionContext createDateReprocessExecutionWithSnapshot(
                BusinessDateCutoff cutoff,
                int maxAttempts,
                String requestedBy,
                String reason,
                LocalDateTime now) {
            reprocessCutoff = cutoff;
            reprocessMaxAttempts = maxAttempts;
            reprocessRequestedBy = requestedBy;
            reprocessReason = reason;
            createdExecution = execution(ExecutionStatus.PREPARING);
            return createdExecution;
        }

        @Override
        public void markInProgress(UUID executionId, boolean retryAttempt, LocalDateTime now) {
            events.add("markInProgress");
            retryAttempts.add(retryAttempt);
        }

        @Override
        public void updateHeartbeat(UUID executionId, LocalDateTime now) {
            events.add("updateHeartbeat");
        }

        @Override
        public void markPublishing(UUID executionId, LocalDateTime now) {
            events.add("markPublishing");
        }

        @Override
        public void complete(BatchResult result, LocalDateTime now) {
            events.add("complete");
        }

        @Override
        public void fail(UUID executionId, String errorCode, String failureDetails, LocalDateTime now) {
            failCount++;
            failedExecutionId = executionId;
            failedErrorCode = errorCode;
            failedDetails = failureDetails;
        }
    }

    private static class FakeOutputService implements OutputService {
        private ExecutionContext execution;
        private RuntimeException failure;
        private final BatchResult result = new BatchResult(
                EXECUTION_ID,
                "output.csv",
                new PublicationSession(),
                10,
                100,
                new byte[]{1, 2, 3});

        @Override
        public BatchResult generate(ExecutionContext execution) {
            this.execution = execution;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static class FakeStoragePublisherService implements StoragePublisherService {
        private PublicationSession committedSession;
        private String committedBlobName;

        @Override
        public PublicationSession beginPublication() {
            return new PublicationSession();
        }

        @Override
        public void stageBlock(PublicationSession session, String blobName, int blockNumber, byte[] content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void commitBlocks(PublicationSession session, String blobName) {
            committedSession = session;
            committedBlobName = blobName;
        }
    }

    private static class FakeTelemetryService implements TelemetryService {
        private int startedCount;
        private int completedCount;
        private final List<BatchError> failed = new ArrayList<>();

        @Override
        public void trackBatchStarted(Logger logger, ExecutionContext execution) {
            startedCount++;
        }

        @Override
        public void trackBatchCompleted(Logger logger, BatchResult result) {
            completedCount++;
        }

        @Override
        public void trackBatchFailed(Logger logger, BatchError error) {
            failed.add(error);
        }
    }

    private static class UnusedStagingRepository implements StagingRepository {
        @Override
        public long count(UUID executionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.empresa.estructuracion.batch.model.StagingRecord> readBatch(
                UUID executionId,
                int lastSourceId,
                int batchSize) {
            throw new UnsupportedOperationException();
        }
    }
}
