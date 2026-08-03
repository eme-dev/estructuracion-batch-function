package com.empresa.estructuracion.batch.repository.impl;

import com.empresa.estructuracion.batch.config.SqlConnectionProvider;
import com.empresa.estructuracion.batch.exception.RepositoryException;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import com.empresa.estructuracion.batch.model.PublicationSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlExecutionRepositoryTest {
    private final SqlConnectionProvider connectionProvider = mock(SqlConnectionProvider.class);
    private final SqlExecutionRepository repository = new SqlExecutionRepository(connectionProvider);

    @Test
    void findRecoverableExecutionShouldCallProcedureAndMapResult() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 0, 10);
        UUID executionId = UUID.randomUUID();
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        ResultSet rs = executionResultSet(executionId, "Failed");
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_FindRecoverableEstructuracionExecution(?, ?)}")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);

        Optional<ExecutionContext> context = repository.findRecoverableExecution(60, now);

        assertTrue(context.isPresent());
        assertEquals(executionId, context.orElseThrow().executionId());
        assertEquals(ExecutionStatus.FAILED, context.orElseThrow().status());
        verify(statement).setInt(1, 60);
        verify(statement).setTimestamp(2, Timestamp.valueOf(now));
    }

    @Test
    void findRecoverableExecutionShouldReturnEmptyWhenNoRowsAreFound() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_FindRecoverableEstructuracionExecution(?, ?)}")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        assertTrue(repository.findRecoverableExecution(60, LocalDateTime.of(2026, 7, 18, 0, 10)).isEmpty());
    }

    @Test
    void createExecutionWithSnapshotShouldCallProcedureAndReturnContext() throws Exception {
        BusinessDateCutoff cutoff = businessDateCutoff();
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 0, 10);
        UUID executionId = UUID.randomUUID();
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        ResultSet rs = executionResultSet(executionId, "Preparing");
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_CreateEstructuracionExecutionSnapshot(?, ?, ?, ?, ?)}")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);

        ExecutionContext context = repository.createExecutionWithSnapshot(cutoff, now);

        assertEquals(executionId, context.executionId());
        assertEquals(ExecutionStatus.PREPARING, context.status());
        verify(statement).setObject(eq(1), any(UUID.class));
        verify(statement).setDate(2, java.sql.Date.valueOf("2026-07-17"));
        verify(statement).setTimestamp(3, Timestamp.from(cutoff.cutoffFromUtc()));
        verify(statement).setTimestamp(4, Timestamp.from(cutoff.cutoffToUtc()));
        verify(statement).setTimestamp(5, Timestamp.valueOf(now));
    }

    @Test
    void createExecutionWithSnapshotShouldFailWhenProcedureReturnsNoContext() throws Exception {
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_CreateEstructuracionExecutionSnapshot(?, ?, ?, ?, ?)}")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);
        BusinessDateCutoff cutoff = businessDateCutoff();

        assertThrowsRepositoryException(() -> repository.createExecutionWithSnapshot(cutoff, LocalDateTime.now()));
    }

    @Test
    void stateMethodsShouldCallExpectedProcedures() throws Exception {
        UUID executionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 0, 10);
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_MarkEstructuracionInProgress(?, ?)}")).thenReturn(statement);

        repository.markInProgress(executionId, now);

        verify(statement).setObject(1, executionId);
        verify(statement).setTimestamp(2, Timestamp.valueOf(now));
        verify(statement).execute();
    }

    @Test
    void updateHeartbeatShouldCallExpectedProcedure() throws Exception {
        UUID executionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 0, 10);
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_UpdateEstructuracionHeartbeat(?, ?)}")).thenReturn(statement);

        repository.updateHeartbeat(executionId, now);

        verify(statement).setObject(1, executionId);
        verify(statement).setTimestamp(2, Timestamp.valueOf(now));
        verify(statement).execute();
    }

    @Test
    void markPublishingShouldCallExpectedProcedure() throws Exception {
        UUID executionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 0, 10);
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_MarkEstructuracionPublishing(?, ?)}")).thenReturn(statement);

        repository.markPublishing(executionId, now);

        verify(statement).setObject(1, executionId);
        verify(statement).setTimestamp(2, Timestamp.valueOf(now));
        verify(statement).execute();
    }

    @Test
    void completeShouldSendResultFields() throws Exception {
        UUID executionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 0, 10);
        byte[] fileHash = new byte[]{10, 20};
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_CompleteEstructuracionExecution(?, ?, ?, ?, ?, ?)}")).thenReturn(statement);
        BatchResult result = new BatchResult(
                executionId,
                "estructuracion_20260717.csv",
                new PublicationSession(),
                200L,
                5000L,
                fileHash);

        repository.complete(result, now);

        verify(statement).setObject(1, executionId);
        verify(statement).setString(2, "estructuracion_20260717.csv");
        verify(statement).setBytes(3, fileHash);
        verify(statement).setLong(4, 200L);
        verify(statement).setLong(5, 5000L);
        verify(statement).setTimestamp(6, Timestamp.valueOf(now));
        verify(statement).execute();
    }

    @Test
    void failShouldSendErrorDetails() throws Exception {
        UUID executionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 0, 10);
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_FailEstructuracionExecution(?, ?, ?, ?)}")).thenReturn(statement);

        repository.fail(executionId, "TECHNICAL_ERROR", "stacktrace", now);

        verify(statement).setObject(1, executionId);
        verify(statement).setString(2, "TECHNICAL_ERROR");
        verify(statement).setString(3, "stacktrace");
        verify(statement).setTimestamp(4, Timestamp.valueOf(now));
        verify(statement).execute();
    }

    @Test
    void repositoryShouldWrapSqlFailures() throws Exception {
        when(connectionProvider.getConnection()).thenThrow(new SQLException("database unavailable"));

        assertThrowsRepositoryException(() -> repository.markPublishing(UUID.randomUUID(), LocalDateTime.now()));
    }

    private ResultSet executionResultSet(UUID executionId, String status) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getObject("executionId", UUID.class)).thenReturn(executionId);
        when(rs.getDate("businessDate")).thenReturn(java.sql.Date.valueOf("2026-07-17"));
        when(rs.getTimestamp("cutoffFromUtc")).thenReturn(Timestamp.from(Instant.parse("2026-07-17T05:00:00Z")));
        when(rs.getTimestamp("cutoffToUtc")).thenReturn(Timestamp.from(Instant.parse("2026-07-18T05:00:00Z")));
        when(rs.getString("status")).thenReturn(status);
        when(rs.getInt("attemptCount")).thenReturn(1);
        when(rs.getString("fileName")).thenReturn("estructuracion_20260717.csv");
        when(rs.getBytes("fileHash")).thenReturn(new byte[]{1, 2, 3});
        return rs;
    }

    private BusinessDateCutoff businessDateCutoff() {
        return new BusinessDateCutoff(
                LocalDate.of(2026, 7, 17),
                LocalDateTime.of(2026, 7, 17, 0, 0),
                LocalDateTime.of(2026, 7, 18, 0, 0),
                Instant.parse("2026-07-17T05:00:00Z"),
                Instant.parse("2026-07-18T05:00:00Z"));
    }

    private void assertThrowsRepositoryException(Executable operation) {
        assertInstanceOf(RepositoryException.class, org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, operation));
    }
}
