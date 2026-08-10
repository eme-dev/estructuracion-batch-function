package com.empresa.estructuracion.batch.config;

import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import com.empresa.estructuracion.batch.service.OutputService;
import com.empresa.estructuracion.batch.service.StoragePublisherService;
import com.empresa.estructuracion.batch.service.TelemetryService;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ConfigurationRecordsTest {
    @Test
    void sqlConfigShouldExposeConnectionString() {
        assertEquals("jdbc:sqlserver://localhost", new SqlConfig("jdbc:sqlserver://localhost").connectionString());
    }

    @Test
    void storageConfigShouldExposeStorageSettings() {
        StorageConfig storageConfig = new StorageConfig("UseDevelopmentStorage=true", "output");

        assertEquals("UseDevelopmentStorage=true", storageConfig.connectionString());
        assertEquals("output", storageConfig.containerName());
    }

    @Test
    void keyVaultConfigShouldExposeKeySettings() {
        KeyVaultConfig keyVaultConfig = new KeyVaultConfig("https://kv.vault.azure.net/", "rsa", "wrapped-aes");

        assertEquals("https://kv.vault.azure.net/", keyVaultConfig.vaultUrl());
        assertEquals("rsa", keyVaultConfig.rsaKeyName());
        assertEquals("wrapped-aes", keyVaultConfig.wrappedAesSecretName());
    }

    @Test
    void batchPropertiesShouldExposeOperationalSettings() {
        BatchProperties batchProperties = new BatchProperties(ZoneId.of("America/Lima"), 1000, 15, 3);

        assertEquals(ZoneId.of("America/Lima"), batchProperties.zoneId());
        assertEquals(1000, batchProperties.batchSize());
        assertEquals(15, batchProperties.staleMinutes());
        assertEquals(3, batchProperties.maxAttempts());
    }

    @Test
    void batchPropertiesShouldRejectInvalidOperationalSettings() {
        ZoneId zoneId = ZoneId.of("America/Lima");

        assertThrows(IllegalArgumentException.class, () -> new BatchProperties(zoneId, 0, 15, 3));
        assertThrows(IllegalArgumentException.class, () -> new BatchProperties(zoneId, 1000, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new BatchProperties(zoneId, 1000, 15, 0));
    }

    @Test
    void dependencyRecordsShouldExposeCollaborators() {
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        StagingRepository stagingRepository = mock(StagingRepository.class);
        OutputService outputService = mock(OutputService.class);
        StoragePublisherService storagePublisherService = mock(StoragePublisherService.class);
        TelemetryService telemetryService = mock(TelemetryService.class);

        RepositoryDependencies repositories = new RepositoryDependencies(executionRepository, stagingRepository);
        ServiceDependencies services = new ServiceDependencies(outputService, storagePublisherService, telemetryService);

        assertSame(executionRepository, repositories.executionRepository());
        assertSame(stagingRepository, repositories.stagingRepository());
        assertSame(outputService, services.outputService());
        assertSame(storagePublisherService, services.storagePublisherService());
        assertSame(telemetryService, services.telemetryService());
    }
}
