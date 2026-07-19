package com.empresa.estructuracion.batch.config;

import java.time.ZoneId;

public record BatchProperties(
        ZoneId zoneId,
        int batchSize,
        int staleMinutes,
        int maxAttempts,
        String storageContainer,
        String storageBasePath,
        String keyVaultUrl,
        String rsaKeyName,
        String wrappedAesSecretName) {
}

