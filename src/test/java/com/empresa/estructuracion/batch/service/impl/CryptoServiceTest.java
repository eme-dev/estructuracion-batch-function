package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.config.KeyVaultConfig;
import com.empresa.estructuracion.batch.exception.CryptoException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoServiceTest {
    private final CryptoService cryptoService = new CryptoService(
            new KeyVaultConfig("https://kv-demo.vault.azure.net/", "rsa-key", "wrapped-aes"));

    @Test
    void decryptDataMapShouldReturnPlainJson() throws Exception {
        byte[] aesKey = randomBytes(32);
        String json = "{\"cliente\":\"Juan Perez\",\"monto\":100.5}";
        String dataMap = encryptedDataMap(json, aesKey);

        String decrypted = cryptoService.decryptDataMap(dataMap, aesKey);

        assertEquals(json, decrypted);
    }

    @Test
    void decryptDataMapShouldTrimBase64Value() throws Exception {
        byte[] aesKey = randomBytes(32);
        String json = "{\"documentType\":\"DNI\"}";
        String dataMap = " " + encryptedDataMap(json, aesKey) + " ";

        assertEquals(json, cryptoService.decryptDataMap(dataMap, aesKey));
    }

    @Test
    void decryptDataMapShouldRejectInvalidPayload() {
        String shortPayload = Base64.getEncoder().encodeToString(new byte[12]);

        assertThrows(CryptoException.class, () -> cryptoService.decryptDataMap(shortPayload, randomBytes(32)));
    }

    @Test
    void decryptDataMapShouldRejectWrongKey() throws Exception {
        String dataMap = encryptedDataMap("{\"cliente\":\"Juan Perez\"}", randomBytes(32));

        assertThrows(CryptoException.class, () -> cryptoService.decryptDataMap(dataMap, randomBytes(32)));
    }

    @Test
    void clearKeyShouldOverwriteProvidedKey() {
        byte[] aesKey = randomBytes(32);

        cryptoService.clearKey(aesKey);

        assertArrayEquals(new byte[32], aesKey);
    }

    @Test
    void clearKeyShouldAcceptNull() {
        cryptoService.clearKey(null);
    }

    private String encryptedDataMap(String plainText, byte[] aesKey) throws Exception {
        byte[] iv = randomBytes(12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] payload = Arrays.copyOf(iv, iv.length + cipherText.length);
        System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
        return Base64.getEncoder().encodeToString(payload);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
