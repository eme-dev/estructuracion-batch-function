package com.empresa.estructuracion.batch.config;

import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;

public record RepositoryDependencies(
        ExecutionRepository executionRepository,
        StagingRepository stagingRepository) {
}
