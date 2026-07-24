package com.empresa.estructuracion.batch.service;

public record EstructuracionBatchServices(
        OutputService outputService,
        StoragePublisherService storagePublisherService,
        TelemetryService telemetryService) {
}
