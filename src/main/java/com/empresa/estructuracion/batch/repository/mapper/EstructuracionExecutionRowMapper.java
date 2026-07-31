package com.empresa.estructuracion.batch.repository.mapper;

import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public class EstructuracionExecutionRowMapper {
    public ExecutionContext map(ResultSet rs) throws SQLException {
        return new ExecutionContext(
                rs.getObject("executionId", java.util.UUID.class),
                rs.getDate("businessDate").toLocalDate(),
                rs.getTimestamp("cutoffFromUtc").toInstant(),
                rs.getTimestamp("cutoffToUtc").toInstant(),
                status(rs.getString("status")),
                rs.getInt("attemptCount"),
                rs.getString("fileName"),
                rs.getBytes("fileHash"));
    }

    private ExecutionStatus status(String value) {
        return Arrays.stream(ExecutionStatus.values())
                .filter(status -> status.databaseValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown execution status: " + value));
    }
}
