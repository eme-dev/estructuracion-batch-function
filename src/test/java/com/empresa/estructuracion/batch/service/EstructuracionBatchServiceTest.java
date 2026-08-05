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
        assertEquals(LocalDate.of(2026, 7, 31), executionRepository.createdCutoff.businessDate());
        assertEquals(List.of("markInProgress", "markPublishing", "complete"), executionRepository.events);
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
    }

    @Test
    void executeShouldSupportDefaultClockConstructor() {
        FakeExecutionRepository executionRepository = new FakeExecutionRepository();
        executionRepository.recoverable = Optional.of(execution(ExecutionStatus.FAILED));
        FakeOutputService outputService = new FakeOutputService();
        EstructuracionBatchService service = new EstructuracionBatchService(
                new BatchProperties(ZoneId.of("America/Lima"), 100, 15),
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

    private EstructuracionBatchService service(
            FakeExecutionRepository executionRepository,
            OutputService outputService,
            StoragePublisherService storageService,
            TelemetryService telemetryService) {
        return new EstructuracionBatchService(
                new BatchProperties(ZoneId.of("America/Lima"), 100, 15),
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
        private int createCount;
        private int findRecoverableStaleMinutes;
        private final List<String> events = new ArrayList<>();
        private int failCount;
        private UUID failedExecutionId;
        private String failedErrorCode;
        private String failedDetails;

        @Override
        public Optional<ExecutionContext> findRecoverableExecution(int staleMinutes, LocalDateTime now) {
            findRecoverableStaleMinutes = staleMinutes;
            return recoverable;
        }

        @Override
        public ExecutionContext createExecutionWithSnapshot(BusinessDateCutoff cutoff, LocalDateTime now) {
            createCount++;
            createdCutoff = cutoff;
            createdExecution = execution(ExecutionStatus.PREPARING);
            return createdExecution;
        }

        @Override
        public void markInProgress(UUID executionId, LocalDateTime now) {
            events.add("markInProgress");
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
