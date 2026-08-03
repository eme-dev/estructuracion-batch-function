package com.empresa.estructuracion.batch.repository.mapper;

import com.empresa.estructuracion.batch.model.StagingRecord;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StagingRecordRowMapperTest {
    private final StagingRecordRowMapper rowMapper = new StagingRecordRowMapper();

    @Test
    void mapShouldReadAllCsvContractFields() throws Exception {
        Instant creationDateTime = Instant.parse("2026-07-17T15:30:00Z");
        Instant reprocessDateTime = Instant.parse("2026-07-18T01:10:00Z");
        ResultSet rs = resultSet(creationDateTime, Timestamp.from(reprocessDateTime));

        StagingRecord stagingRecord = rowMapper.map(rs);

        assertEquals(10L, stagingRecord.stagingId());
        assertEquals(25, stagingRecord.sourceId());
        assertEquals("archivo-demo-001.pdf", stagingRecord.fileName());
        assertEquals(true, stagingRecord.statusFile());
        assertEquals("Juan Perez", stagingRecord.clientName());
        assertEquals(creationDateTime, stagingRecord.creationDateTime());
        assertEquals("encrypted-data-map", stagingRecord.dataMap());
        assertEquals("DNI", stagingRecord.documentType());
        assertEquals(true, stagingRecord.isReprocessed());
        assertEquals(reprocessDateTime, stagingRecord.reprocessDateTime());
        assertEquals(2, stagingRecord.reprocessCount());
    }

    @Test
    void mapShouldAllowNullableReprocessDateTime() throws Exception {
        ResultSet rs = resultSet(Instant.parse("2026-07-17T15:30:00Z"), null);

        StagingRecord stagingRecord = rowMapper.map(rs);

        assertNull(stagingRecord.reprocessDateTime());
    }

    private ResultSet resultSet(Instant creationDateTime, Timestamp reprocessDateTime) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("stagingId")).thenReturn(10L);
        when(rs.getInt("sourceId")).thenReturn(25);
        when(rs.getString("fileName")).thenReturn("archivo-demo-001.pdf");
        when(rs.getBoolean("statusFile")).thenReturn(true);
        when(rs.getString("clientName")).thenReturn("Juan Perez");
        when(rs.getTimestamp("creationDateTime")).thenReturn(Timestamp.from(creationDateTime));
        when(rs.getString("dataMap")).thenReturn("encrypted-data-map");
        when(rs.getString("documentType")).thenReturn("DNI");
        when(rs.getBoolean("isReprocessed")).thenReturn(true);
        when(rs.getTimestamp("reprocessDateTime")).thenReturn(reprocessDateTime);
        when(rs.getInt("reprocessCount")).thenReturn(2);
        return rs;
    }
}
