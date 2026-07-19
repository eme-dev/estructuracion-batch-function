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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class CryptoService implements DataMapCryptoService {
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final KeyVaultConfig keyVaultConfig;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(KeyVaultConfig keyVaultConfig, ObjectMapper objectMapper) {
        this.keyVaultConfig = keyVaultConfig;
        this.objectMapper = objectMapper;
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
                    "Unable to unwrap AES key from Key Vault. cause="
                            + ex.getClass().getSimpleName()
                            + ", message="
                            + safeMessage(ex),
                    ex);
        }
    }

    @Override
    public String encryptDataMap(String dataMapJson, byte[] aesKey) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(dataMapJson.getBytes(StandardCharsets.UTF_8));

            return objectMapper.writeValueAsString(new EncryptedDataMapEnvelope(
                    "AES/GCM/NoPadding",
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext)));
        } catch (Exception ex) {
            throw new CryptoException("Unable to encrypt dataMap.", ex);
        }
    }

    @Override
    public String decryptDataMap(String encryptedDataMap, byte[] aesKey) {
        try {
            JsonNode envelope = objectMapper.readTree(encryptedDataMap);
            byte[] iv = base64(envelope, "iv", "nonce");
            byte[] ciphertext = base64(envelope, "ciphertext", "data");
            byte[] tag = optionalBase64(envelope, "tag");
            byte[] cipherInput = tag.length == 0 ? ciphertext : concat(ciphertext, tag);

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

    private byte[] base64(JsonNode node, String primaryName, String alternativeName) {
        JsonNode value = node.has(primaryName) ? node.get(primaryName) : node.get(alternativeName);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing encrypted dataMap field: " + primaryName);
        }
        return Base64.getDecoder().decode(value.asText());
    }

    private byte[] optionalBase64(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.asText().isBlank()
                ? new byte[0]
                : Base64.getDecoder().decode(value.asText());
    }

    private byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? "No details" : ex.getMessage();
    }

    private record EncryptedDataMapEnvelope(String alg, String iv, String ciphertext) {
    }
}
