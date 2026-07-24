package com.empresa.estructuracion.batch.service;

public record EstructuracionBatchServices(
        DataMapCryptoService cryptoService,
        OutputWriterService outputWriterService,
        StoragePublisherService storagePublisherService,
        ManifestWriterService manifestWriterService,
        BatchTelemetryService telemetryService) {
}
