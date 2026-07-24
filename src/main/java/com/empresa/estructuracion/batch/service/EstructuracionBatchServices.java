package com.empresa.estructuracion.batch.service;

public record EstructuracionBatchServices(
        OutputService outputService,
        StoragePublisherService storagePublisherService,
        ManifestWriterService manifestWriterService,
        TelemetryService telemetryService) {
}
