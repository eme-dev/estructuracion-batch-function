package com.empresa.estructuracion.batch.util;

import com.empresa.estructuracion.batch.model.BusinessDateCutoff;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class BusinessDateCalculator {
    private BusinessDateCalculator() {
    }

    public static BusinessDateCutoff previousBusinessDate(Clock clock, ZoneId zoneId) {
        LocalDate executionDate = LocalDate.now(clock.withZone(zoneId));
        LocalDate businessDate = executionDate.minusDays(1);
        LocalDateTime fromLocal = businessDate.atStartOfDay();
        LocalDateTime toLocal = businessDate.plusDays(1).atStartOfDay();
        return new BusinessDateCutoff(
                businessDate,
                fromLocal,
                toLocal,
                fromLocal.atZone(zoneId).toInstant(),
                toLocal.atZone(zoneId).toInstant());
    }
}

