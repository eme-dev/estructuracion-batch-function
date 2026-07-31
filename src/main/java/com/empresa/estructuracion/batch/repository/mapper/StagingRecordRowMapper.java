package com.empresa.estructuracion.batch.repository.mapper;

import com.empresa.estructuracion.batch.model.StagingRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class StagingRecordRowMapper {
    public StagingRecord map(ResultSet rs) throws SQLException {
        return new StagingRecord(
                rs.getLong("stagingId"),
                rs.getInt("sourceId"),
                rs.getString("fileName"),
                rs.getBoolean("statusFile"),
                rs.getString("clientName"),
                rs.getTimestamp("creationDateTime").toInstant(),
                rs.getString("dataMap"),
                rs.getString("documentType"),
                rs.getBoolean("isReprocessed"),
                instantOrNull(rs.getTimestamp("reprocessDateTime")),
                rs.getInt("reprocessCount"));
    }

    private java.time.Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
