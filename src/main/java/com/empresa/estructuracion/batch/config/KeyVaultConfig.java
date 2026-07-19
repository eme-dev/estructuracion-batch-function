package com.empresa.estructuracion.batch.config;

public record KeyVaultConfig(String vaultUrl, String rsaKeyName, String wrappedAesSecretName) {
}

