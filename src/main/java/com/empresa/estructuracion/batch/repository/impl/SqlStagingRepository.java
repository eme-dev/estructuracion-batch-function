package com.empresa.estructuracion.batch.repository.impl;

import com.empresa.estructuracion.batch.config.SqlConnectionProvider;
import com.empresa.estructuracion.batch.exception.RepositoryException;
import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import com.empresa.estructuracion.batch.repository.mapper.StagingRecordRowMapper;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlStagingRepository implements StagingRepository {
    private final SqlConnectionProvider connectionProvider;
    private final StagingRecordRowMapper rowMapper = new StagingRecordRowMapper();

    public SqlStagingRepository(SqlConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public long count(UUID executionId) {
        String sql = "{call ocrt.usp_CountEstructuracionStaging(?)}";
        try (Connection connection = connectionProvider.getConnection();
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.setObject(1, executionId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception ex) {
            throw new RepositoryException("Unable to count staging records.", ex);
        }
    }

    @Override
    public List<StagingRecord> readBatch(UUID executionId, int lastSourceId, int batchSize) {
        String sql = "{call ocrt.usp_ReadEstructuracionStagingBatch(?, ?, ?)}";
        try (Connection connection = connectionProvider.getConnection();
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.setObject(1, executionId);
            statement.setInt(2, lastSourceId);
            statement.setInt(3, batchSize);
            try (ResultSet rs = statement.executeQuery()) {
                List<StagingRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(rowMapper.map(rs));
                }
                return records;
            }
        } catch (Exception ex) {
            throw new RepositoryException("Unable to read staging batch.", ex);
        }
    }
}
