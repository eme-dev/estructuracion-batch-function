package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;

public record EstructuracionBatchDependencies(
        ExecutionRepository executionRepository,
        StagingRepository stagingRepository,
        DataMapCryptoService cryptoService,
        OutputWriterService outputWriterService,
        StoragePublisherService storagePublisherService,
        ManifestWriterService manifestWriterService,
        BatchTelemetryService telemetryService) {
}
