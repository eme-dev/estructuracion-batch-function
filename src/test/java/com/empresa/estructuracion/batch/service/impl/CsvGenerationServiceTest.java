package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.exception.DataMapInvalidException;
import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.util.HashUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvGenerationServiceTest {
    private final CsvGenerationService service = new CsvGenerationService(new ObjectMapper());

    @Test
    void headerBytesShouldWriteCurrentCsvContract() {
        MessageDigest digest = HashUtils.sha256();

        String header = new String(service.headerBytes(digest), StandardCharsets.UTF_8);

        assertEquals(
                "fileName|statusFile|clientName|creationDateTime|dataMap|listaTables|documentType|"
                        + "isReprocessed|reprocessDateTime|reprocessCount\n",
                header);
    }

    @Test
    void rowBytesShouldCompactDataMapAndLeaveListaTablesEmpty() {
        MessageDigest digest = HashUtils.sha256();
        StagingRecord record = record(null, false, 0);

        String row = new String(service.rowBytes(
                record,
                """
                {
                  "cliente": "Juan Perez",
                  "monto": 100.5
                }
                """,
                digest), StandardCharsets.UTF_8);

        assertEquals(
                "archivo-demo.pdf|true|Juan Perez|2026-07-17T10:30:00Z|"
                        + "\"{\"\"cliente\"\":\"\"Juan Perez\"\",\"\"monto\"\":100.5}\"||DNI|false||0\n",
                row);
    }

    @Test
    void rowBytesShouldWriteReprocessFieldsWhenPresent() {
        MessageDigest digest = HashUtils.sha256();
        StagingRecord record = record(Instant.parse("2026-07-18T08:15:30Z"), true, 2);

        String row = new String(service.rowBytes(
                record,
                "{\"cliente\":\"Juan Perez\"}",
                digest), StandardCharsets.UTF_8);

        assertEquals(
                "archivo-demo.pdf|true|Juan Perez|2026-07-17T10:30:00Z|"
                        + "\"{\"\"cliente\"\":\"\"Juan Perez\"\"}\"||DNI|true|2026-07-18T08:15:30Z|2\n",
                row);
    }

    @Test
    void rowBytesShouldUpdateDigestWithWrittenBytes() {
        MessageDigest actualDigest = HashUtils.sha256();
        MessageDigest expectedDigest = HashUtils.sha256();
        StagingRecord record = record(null, false, 0);

        byte[] row = service.rowBytes(record, "{\"cliente\":\"Juan Perez\"}", actualDigest);
        expectedDigest.update(row);

        assertEquals(
                HexFormat.of().formatHex(expectedDigest.digest()),
                HexFormat.of().formatHex(actualDigest.digest()));
    }

    @Test
    void rowBytesShouldRejectInvalidDataMapJson() {
        assertThrows(
                DataMapInvalidException.class,
                () -> service.rowBytes(record(null, false, 0), "{\"cliente\":", HashUtils.sha256()));
    }

    private StagingRecord record(Instant reprocessDateTime, boolean isReprocessed, int reprocessCount) {
        return new StagingRecord(
                10,
                20,
                "archivo-demo.pdf",
                true,
                "Juan Perez",
                Instant.parse("2026-07-17T10:30:00Z"),
                "encrypted",
                "DNI",
                isReprocessed,
                reprocessDateTime,
                reprocessCount);
    }
}
