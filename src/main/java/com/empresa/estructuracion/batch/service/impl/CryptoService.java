package com.empresa.estructuracion.batch.service.impl;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.empresa.estructuracion.batch.config.KeyVaultConfig;
import com.empresa.estructuracion.batch.exception.CryptoException;
import com.empresa.estructuracion.batch.service.DataMapCryptoService;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class CryptoService implements DataMapCryptoService {
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;

    private final KeyVaultConfig keyVaultConfig;

    public CryptoService(KeyVaultConfig keyVaultConfig) {
        this.keyVaultConfig = keyVaultConfig;
    }

    @Override
    public byte[] unwrapAesKey() {
        try {
            var credential = new DefaultAzureCredentialBuilder().build();
            SecretClient secretClient = new SecretClientBuilder()
                    .vaultUrl(keyVaultConfig.vaultUrl())
                    .credential(credential)
                    .buildClient();
            String wrappedAes = secretClient.getSecret(keyVaultConfig.wrappedAesSecretName()).getValue();
            byte[] wrappedAesBytes = Base64.getDecoder().decode(wrappedAes);

            String keyIdentifier = keyVaultConfig.vaultUrl()
                    + (keyVaultConfig.vaultUrl().endsWith("/") ? "" : "/")
                    + "keys/"
                    + keyVaultConfig.rsaKeyName();
            CryptographyClient cryptographyClient = new CryptographyClientBuilder()
                    .keyIdentifier(keyIdentifier)
                    .credential(credential)
                    .buildClient();

            return cryptographyClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedAesBytes).getKey();
        } catch (Exception ex) {
            throw new CryptoException(
                    "Unable to unwrap AES key from Key Vault. cause=" + ex.getClass().getSimpleName(),
                    ex);
        }
    }

    @Override
    public String decryptDataMap(String encryptedDataMap, byte[] aesKey) {
        try {
            byte[] encryptedPayload = Base64.getDecoder().decode(encryptedDataMap.trim());
            validateEncryptedPayload(encryptedPayload);
            byte[] iv = Arrays.copyOfRange(encryptedPayload, 0, GCM_IV_BYTES);
            byte[] cipherInput = Arrays.copyOfRange(encryptedPayload, GCM_IV_BYTES, encryptedPayload.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherInput);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new CryptoException("Unable to decrypt dataMap.", ex);
        }
    }

    @Override
    public void clearKey(byte[] aesKey) {
        if (aesKey != null) {
            Arrays.fill(aesKey, (byte) 0);
        }
    }

    private void validateEncryptedPayload(byte[] encryptedPayload) {
        if (encryptedPayload.length <= GCM_IV_BYTES + GCM_TAG_BYTES) {
            throw new IllegalArgumentException("Encrypted dataMap payload is too short.");
        }
    }
}
