package com.empresa.estructuracion.batch.repository.impl;

import com.empresa.estructuracion.batch.model.StagingRecord;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StagingRecordRowMapper {
    public StagingRecord map(ResultSet rs) throws SQLException {
        return new StagingRecord(
                rs.getLong("stagingId"),
                rs.getInt("sourceId"),
                rs.getString("fileName"),
                rs.getBoolean("statusFile"),
                rs.getString("clientName"),
                rs.getTimestamp("creationDateTime").toInstant(),
                rs.getString("encryptedDataMap"),
                rs.getString("listaTables"),
                rs.getString("documentType"),
                rs.getBytes("uniqueHash"));
    }
}
