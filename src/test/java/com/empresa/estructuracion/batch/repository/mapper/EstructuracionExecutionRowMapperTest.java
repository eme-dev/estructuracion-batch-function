package com.empresa.estructuracion.batch.repository.mapper;

import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EstructuracionExecutionRowMapperTest {
    private final EstructuracionExecutionRowMapper rowMapper = new EstructuracionExecutionRowMapper();

    @Test
    void mapShouldReadExecutionContext() throws Exception {
        UUID executionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        byte[] fileHash = new byte[]{1, 2, 3};
        ResultSet rs = resultSet(executionId, "Preparing", fileHash);

        ExecutionContext context = rowMapper.map(rs);

        assertEquals(executionId, context.executionId());
        assertEquals(LocalDate.of(2026, 7, 17), context.businessDate());
        assertEquals(Instant.parse("2026-07-17T05:00:00Z"), context.cutoffFromUtc());
        assertEquals(Instant.parse("2026-07-18T05:00:00Z"), context.cutoffToUtc());
        assertEquals(ExecutionStatus.PREPARING, context.status());
        assertEquals(1, context.attemptCount());
        assertEquals("estructuracion_20260717.csv", context.fileName());
        assertArrayEquals(fileHash, context.fileHash());
    }

    @Test
    void mapShouldRejectUnknownStatus() throws Exception {
        ResultSet rs = resultSet(UUID.randomUUID(), "Unexpected", null);

        assertThrows(IllegalArgumentException.class, () -> rowMapper.map(rs));
    }

    private ResultSet resultSet(UUID executionId, String status, byte[] fileHash) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("executionId", UUID.class)).thenReturn(executionId);
        when(rs.getDate("businessDate")).thenReturn(java.sql.Date.valueOf("2026-07-17"));
        when(rs.getTimestamp("cutoffFromUtc")).thenReturn(Timestamp.from(Instant.parse("2026-07-17T05:00:00Z")));
        when(rs.getTimestamp("cutoffToUtc")).thenReturn(Timestamp.from(Instant.parse("2026-07-18T05:00:00Z")));
        when(rs.getString("status")).thenReturn(status);
        when(rs.getInt("attemptCount")).thenReturn(1);
        when(rs.getString("fileName")).thenReturn("estructuracion_20260717.csv");
        when(rs.getBytes("fileHash")).thenReturn(fileHash);
        return rs;
    }
}
