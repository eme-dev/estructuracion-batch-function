package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.ExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileNameUtilsTest {

    @Test
    void outputPathShouldUseBusinessDateAndExecutionId() {
        UUID executionId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        ExecutionContext execution = new ExecutionContext(
                executionId,
                LocalDate.of(2026, 7, 17),
                Instant.parse("2026-07-17T05:00:00Z"),
                Instant.parse("2026-07-18T05:00:00Z"),
                ExecutionStatus.PREPARING,
                1,
                null,
                null);

        assertEquals(
                "estructuracion_20260717_11111111-2222-3333-4444-555555555555.csv",
                FileNameUtils.outputPath(execution));
    }
}
