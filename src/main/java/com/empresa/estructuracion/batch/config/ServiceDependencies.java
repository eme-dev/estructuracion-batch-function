package com.empresa.estructuracion.batch.config;

import com.empresa.estructuracion.batch.service.OutputService;
import com.empresa.estructuracion.batch.service.StoragePublisherService;
import com.empresa.estructuracion.batch.service.TelemetryService;

public record ServiceDependencies(
        OutputService outputService,
        StoragePublisherService storagePublisherService,
        TelemetryService telemetryService) {
}
