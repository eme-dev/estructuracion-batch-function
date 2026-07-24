package com.empresa.estructuracion.batch.config;

import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import com.empresa.estructuracion.batch.service.EstructuracionBatchRepositories;
import com.empresa.estructuracion.batch.service.EstructuracionBatchService;
import com.empresa.estructuracion.batch.service.EstructuracionBatchServices;
import com.empresa.estructuracion.batch.repository.impl.SqlExecutionRepository;
import com.empresa.estructuracion.batch.repository.impl.SqlStagingRepository;
import com.empresa.estructuracion.batch.service.impl.BlobStorageService;
import com.empresa.estructuracion.batch.service.impl.CryptoService;
import com.empresa.estructuracion.batch.service.impl.CsvGenerationService;
import com.empresa.estructuracion.batch.service.impl.DefaultTelemetryService;
import com.empresa.estructuracion.batch.service.impl.ManifestService;
import com.empresa.estructuracion.batch.service.impl.StagedOutputService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.ZoneId;

public class DependencyConfig {

    private final EstructuracionBatchService estructuracionBatchService;

    private DependencyConfig(EstructuracionBatchService estructuracionBatchService) {
        this.estructuracionBatchService = estructuracionBatchService;
    }

    public static DependencyConfig fromEnvironment() {
        return new DependencyConfig(batchService());
    }

    public EstructuracionBatchService estructuracionBatchService() {
        return estructuracionBatchService;
    }

    private static EstructuracionBatchService batchService() {
        ObjectMapper objectMapper = objectMapper();
        SqlConfig sqlConfig = sqlConfig();
        StorageConfig storageConfig = storageConfig();
        KeyVaultConfig keyVaultConfig = keyVaultConfig();
        BatchProperties batchProperties = batchProperties(storageConfig, keyVaultConfig);
        EstructuracionBatchRepositories repositories = repositories(sqlConfig);
        EstructuracionBatchServices services = services(
                batchProperties,
                repositories,
                storageConfig,
                keyVaultConfig,
                objectMapper);

        return new EstructuracionBatchService(batchProperties, repositories, services);
    }

    private static EstructuracionBatchRepositories repositories(SqlConfig sqlConfig) {
        SqlConnectionProvider sqlConnectionProvider = new SqlConnectionProvider(sqlConfig);
        ExecutionRepository executionRepository = new SqlExecutionRepository(sqlConnectionProvider);
        StagingRepository stagingRepository = new SqlStagingRepository(sqlConnectionProvider);

        return new EstructuracionBatchRepositories(executionRepository, stagingRepository);
    }

    private static EstructuracionBatchServices services(
            BatchProperties batchProperties,
            EstructuracionBatchRepositories repositories,
            StorageConfig storageConfig,
            KeyVaultConfig keyVaultConfig,
            ObjectMapper objectMapper) {
        CryptoService cryptoService = new CryptoService(keyVaultConfig, objectMapper);
        CsvGenerationService csvGenerationService = new CsvGenerationService(objectMapper);
        BlobStorageService blobStorageService = new BlobStorageService(storageConfig);
        StagedOutputService outputService = new StagedOutputService(
                batchProperties,
                repositories.stagingRepository(),
                repositories.executionRepository(),
                cryptoService,
                csvGenerationService,
                blobStorageService);
        ManifestService manifestService = new ManifestService(objectMapper);
        DefaultTelemetryService telemetryService = new DefaultTelemetryService();

        return new EstructuracionBatchServices(
                outputService,
                blobStorageService,
                manifestService,
                telemetryService);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static SqlConfig sqlConfig() {
        return new SqlConfig(required("BATCH_SQL_CONNECTION_STRING"));
    }

    private static StorageConfig storageConfig() {
        return new StorageConfig(
                required("BATCH_STORAGE_CONNECTION_STRING"),
                required("BATCH_STORAGE_CONTAINER"));
    }

    private static KeyVaultConfig keyVaultConfig() {
        return new KeyVaultConfig(
                required("BATCH_KEY_VAULT_URL"),
                required("BATCH_RSA_KEY_NAME"),
                required("BATCH_WRAPPED_AES_SECRET_NAME"));
    }

    private static BatchProperties batchProperties(StorageConfig storageConfig, KeyVaultConfig keyVaultConfig) {
        return new BatchProperties(
                ZoneId.of(optional("BATCH_ZONE_ID", "America/Lima")),
                integer("BATCH_SQL_BATCH_SIZE", 1000),
                integer("BATCH_SQL_STALE_MINUTES", 15),
                integer("BATCH_SQL_MAX_ATTEMPTS", 3),
                storageConfig.containerName(),
                optional("BATCH_STORAGE_BASE_PATH", "estructuracion"),
                keyVaultConfig.vaultUrl(),
                keyVaultConfig.rsaKeyName(),
                keyVaultConfig.wrappedAesSecretName());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required setting: " + name);
        }
        return value;
    }

    private static String optional(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int integer(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }
}
