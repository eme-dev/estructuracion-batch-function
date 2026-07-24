package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;

public record EstructuracionBatchRepositories(
        ExecutionRepository executionRepository,
        StagingRepository stagingRepository) {
}
