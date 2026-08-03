package com.empresa.estructuracion.batch.repository.impl;

import com.empresa.estructuracion.batch.config.SqlConnectionProvider;
import com.empresa.estructuracion.batch.exception.RepositoryException;
import com.empresa.estructuracion.batch.model.StagingRecord;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlStagingRepositoryTest {
    private final SqlConnectionProvider connectionProvider = mock(SqlConnectionProvider.class);
    private final SqlStagingRepository repository = new SqlStagingRepository(connectionProvider);

    @Test
    void countShouldCallCountProcedure() throws Exception {
        UUID executionId = UUID.randomUUID();
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_CountEstructuracionStaging(?)}")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong(1)).thenReturn(15L);

        long count = repository.count(executionId);

        assertEquals(15L, count);
        verify(statement).setObject(1, executionId);
    }

    @Test
    void countShouldReturnZeroWhenProcedureReturnsNoRows() throws Exception {
        UUID executionId = UUID.randomUUID();
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_CountEstructuracionStaging(?)}")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        assertEquals(0L, repository.count(executionId));
    }

    @Test
    void readBatchShouldCallProcedureAndMapRecords() throws Exception {
        UUID executionId = UUID.randomUUID();
        Connection connection = mock(Connection.class);
        CallableStatement statement = mock(CallableStatement.class);
        ResultSet rs = stagingResultSet();
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareCall("{call ocrt.usp_ReadEstructuracionStagingBatch(?, ?, ?)}")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);

        List<StagingRecord> records = repository.readBatch(executionId, 100, 500);

        assertEquals(1, records.size());
        assertEquals(101, records.get(0).sourceId());
        assertEquals("archivo-demo-101.pdf", records.get(0).fileName());
        verify(statement).setObject(1, executionId);
        verify(statement).setInt(2, 100);
        verify(statement).setInt(3, 500);
    }

    @Test
    void readBatchShouldWrapSqlFailures() throws Exception {
        UUID executionId = UUID.randomUUID();
        when(connectionProvider.getConnection()).thenThrow(new SQLException("database unavailable"));

        assertThrows(RepositoryException.class, () -> repository.readBatch(executionId, 0, 100));
    }

    private ResultSet stagingResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("stagingId")).thenReturn(1L);
        when(rs.getInt("sourceId")).thenReturn(101);
        when(rs.getString("fileName")).thenReturn("archivo-demo-101.pdf");
        when(rs.getBoolean("statusFile")).thenReturn(true);
        when(rs.getString("clientName")).thenReturn("Cliente Demo");
        when(rs.getTimestamp("creationDateTime")).thenReturn(Timestamp.from(Instant.parse("2026-07-17T15:30:00Z")));
        when(rs.getString("dataMap")).thenReturn("encrypted-data-map");
        when(rs.getString("documentType")).thenReturn("DNI");
        when(rs.getBoolean("isReprocessed")).thenReturn(false);
        when(rs.getTimestamp("reprocessDateTime")).thenReturn(null);
        when(rs.getInt("reprocessCount")).thenReturn(0);
        return rs;
    }
}
