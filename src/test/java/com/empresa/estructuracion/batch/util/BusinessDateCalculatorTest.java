package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.model.BusinessDateCutoff;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessDateCalculatorTest {

    @Test
    void previousBusinessDateShouldUseConfiguredZone() {
        ZoneId lima = ZoneId.of("America/Lima");
        Clock clock = Clock.fixed(Instant.parse("2026-07-18T05:10:00Z"), lima);

        BusinessDateCutoff cutoff = BusinessDateCalculator.previousBusinessDate(clock, lima);

        assertEquals(LocalDate.of(2026, 7, 17), cutoff.businessDate());
        assertEquals(LocalDateTime.of(2026, 7, 17, 0, 0), cutoff.cutoffFromLocal());
        assertEquals(LocalDateTime.of(2026, 7, 18, 0, 0), cutoff.cutoffToLocal());
        assertEquals(Instant.parse("2026-07-17T05:00:00Z"), cutoff.cutoffFromUtc());
        assertEquals(Instant.parse("2026-07-18T05:00:00Z"), cutoff.cutoffToUtc());
    }

    @Test
    void forBusinessDateShouldUseRequestedDate() {
        ZoneId lima = ZoneId.of("America/Lima");

        BusinessDateCutoff cutoff = BusinessDateCalculator.forBusinessDate(LocalDate.of(2026, 8, 23), lima);

        assertEquals(LocalDate.of(2026, 8, 23), cutoff.businessDate());
        assertEquals(LocalDateTime.of(2026, 8, 23, 0, 0), cutoff.cutoffFromLocal());
        assertEquals(LocalDateTime.of(2026, 8, 24, 0, 0), cutoff.cutoffToLocal());
        assertEquals(Instant.parse("2026-08-23T05:00:00Z"), cutoff.cutoffFromUtc());
        assertEquals(Instant.parse("2026-08-24T05:00:00Z"), cutoff.cutoffToUtc());
    }
}
