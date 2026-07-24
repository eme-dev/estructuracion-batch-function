package com.empresa.estructuracion.batch.repository.impl;

import com.empresa.estructuracion.batch.config.SqlConnectionProvider;
import com.empresa.estructuracion.batch.exception.RepositoryException;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class SqlExecutionRepository implements ExecutionRepository {
    private final SqlConnectionProvider connectionProvider;
    private final EstructuracionExecutionRowMapper rowMapper = new EstructuracionExecutionRowMapper();

    public SqlExecutionRepository(SqlConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Optional<ExecutionContext> findRecoverableExecution(int staleMinutes, LocalDateTime now) {
        String sql = "{call ocrt.usp_FindRecoverableEstructuracionExecution(?, ?)}";
        try (Connection connection = connectionProvider.getConnection();
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.setInt(1, staleMinutes);
            statement.setTimestamp(2, timestamp(now));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rowMapper.map(rs)) : Optional.empty();
            }
        } catch (Exception ex) {
            throw new RepositoryException("Unable to find recoverable execution.", ex);
        }
    }

    @Override
    public ExecutionContext createExecutionWithSnapshot(BusinessDateCutoff cutoff, LocalDateTime now) {
        String sql = "{call ocrt.usp_CreateEstructuracionExecutionSnapshot(?, ?, ?, ?, ?)}";
        UUID executionId = UUID.randomUUID();
        try (Connection connection = connectionProvider.getConnection();
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.setObject(1, executionId);
            statement.setDate(2, java.sql.Date.valueOf(cutoff.businessDate()));
            statement.setTimestamp(3, Timestamp.from(cutoff.cutoffFromUtc()));
            statement.setTimestamp(4, Timestamp.from(cutoff.cutoffToUtc()));
            statement.setTimestamp(5, timestamp(now));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new RepositoryException("Snapshot procedure did not return execution context.", null);
                }
                return rowMapper.map(rs);
            }
        } catch (Exception ex) {
            throw new RepositoryException("Unable to create execution snapshot.", ex);
        }
    }

    @Override
    public void markInProgress(UUID executionId, LocalDateTime now) {
        executeStateProcedure("{call ocrt.usp_MarkEstructuracionInProgress(?, ?)}", executionId, now);
    }

    @Override
    public void updateHeartbeat(UUID executionId, LocalDateTime now) {
        executeStateProcedure("{call ocrt.usp_UpdateEstructuracionHeartbeat(?, ?)}", executionId, now);
    }

    @Override
    public void markPublishing(UUID executionId, LocalDateTime now) {
        executeStateProcedure("{call ocrt.usp_MarkEstructuracionPublishing(?, ?)}", executionId, now);
    }

    @Override
    public void complete(BatchResult result, LocalDateTime now) {
        String sql = "{call ocrt.usp_CompleteEstructuracionExecution(?, ?, ?, ?, ?, ?)}";
        try (Connection connection = connectionProvider.getConnection();
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.setObject(1, result.executionId());
            statement.setString(2, result.fileName());
            statement.setBytes(3, result.fileHash());
            statement.setLong(4, result.recordCount());
            statement.setLong(5, result.contentLength());
            statement.setTimestamp(6, timestamp(now));
            statement.execute();
        } catch (Exception ex) {
            throw new RepositoryException("Unable to complete execution.", ex);
        }
    }

    @Override
    public void fail(UUID executionId, String errorCode, String sanitizedMessage, LocalDateTime now) {
        String sql = "{call ocrt.usp_FailEstructuracionExecution(?, ?, ?, ?)}";
        try (Connection connection = connectionProvider.getConnection();
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.setObject(1, executionId);
            statement.setString(2, errorCode);
            statement.setString(3, sanitizedMessage);
            statement.setTimestamp(4, timestamp(now));
            statement.execute();
        } catch (Exception ex) {
            throw new RepositoryException("Unable to fail execution.", ex);
        }
    }

    private void executeStateProcedure(String sql, UUID executionId, LocalDateTime now) {
        try (Connection connection = connectionProvider.getConnection();
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.setObject(1, executionId);
            statement.setTimestamp(2, timestamp(now));
            statement.execute();
        } catch (Exception ex) {
            throw new RepositoryException("Unable to update execution state.", ex);
        }
    }

    private Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }
}
