package com.empresa.estructuracion.batch.config;

import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import com.empresa.estructuracion.batch.service.DataMapCryptoService;
import com.empresa.estructuracion.batch.service.EstructuracionBatchService;
import com.empresa.estructuracion.batch.repository.impl.SqlExecutionRepository;
import com.empresa.estructuracion.batch.repository.impl.SqlStagingRepository;
import com.empresa.estructuracion.batch.service.impl.BlobStorageService;
import com.empresa.estructuracion.batch.service.impl.CryptoService;
import com.empresa.estructuracion.batch.service.impl.JsonlGenerationService;
import com.empresa.estructuracion.batch.service.impl.ManifestService;
import com.empresa.estructuracion.batch.service.impl.TelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.ZoneId;

public class DependencyConfig {

    private final EstructuracionBatchService estructuracionBatchService;
    private final DataMapCryptoService dataMapCryptoService;
    private final ObjectMapper objectMapper;

    private DependencyConfig(
            EstructuracionBatchService estructuracionBatchService,
            DataMapCryptoService dataMapCryptoService,
            ObjectMapper objectMapper) {
        this.estructuracionBatchService = estructuracionBatchService;
        this.dataMapCryptoService = dataMapCryptoService;
        this.objectMapper = objectMapper;
    }

    public static DependencyConfig fromEnvironment() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        SqlConfig sqlConfig = new SqlConfig(required("BATCH_SQL_CONNECTION_STRING"));
        StorageConfig storageConfig = new StorageConfig(
                required("BATCH_STORAGE_CONNECTION_STRING"),
                required("BATCH_STORAGE_CONTAINER"));
        KeyVaultConfig keyVaultConfig = new KeyVaultConfig(
                required("BATCH_KEY_VAULT_URL"),
                required("BATCH_RSA_KEY_NAME"),
                required("BATCH_WRAPPED_AES_SECRET_NAME"));
        BatchProperties batchProperties = new BatchProperties(
                ZoneId.of(optional("BATCH_ZONE_ID", "America/Lima")),
                integer("BATCH_SQL_BATCH_SIZE", 1000),
                integer("BATCH_SQL_STALE_MINUTES", 15),
                integer("BATCH_SQL_MAX_ATTEMPTS", 3),
                storageConfig.containerName(),
                optional("BATCH_STORAGE_BASE_PATH", "estructuracion"),
                keyVaultConfig.vaultUrl(),
                keyVaultConfig.rsaKeyName(),
                keyVaultConfig.wrappedAesSecretName());

        SqlConnectionProvider sqlConnectionProvider = new SqlConnectionProvider(sqlConfig);
        ExecutionRepository executionRepository = new SqlExecutionRepository(sqlConnectionProvider);
        StagingRepository stagingRepository = new SqlStagingRepository(sqlConnectionProvider);
        CryptoService cryptoService = new CryptoService(keyVaultConfig, objectMapper);
        JsonlGenerationService jsonlGenerationService = new JsonlGenerationService(objectMapper);
        BlobStorageService blobStorageService = new BlobStorageService(storageConfig);
        ManifestService manifestService = new ManifestService(objectMapper);
        TelemetryService telemetryService = new TelemetryService();

        EstructuracionBatchService estructuracionBatchService = new EstructuracionBatchService(
                batchProperties,
                executionRepository,
                stagingRepository,
                cryptoService,
                jsonlGenerationService,
                blobStorageService,
                manifestService,
                telemetryService);

        return new DependencyConfig(estructuracionBatchService, cryptoService, objectMapper);
    }

    public EstructuracionBatchService estructuracionBatchService() {
        return estructuracionBatchService;
    }

    public DataMapCryptoService dataMapCryptoService() {
        return dataMapCryptoService;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
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
