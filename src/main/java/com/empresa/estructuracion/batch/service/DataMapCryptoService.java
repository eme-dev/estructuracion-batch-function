package com.empresa.estructuracion.batch.service;

public interface DataMapCryptoService {
    byte[] unwrapAesKey();

    String encryptDataMap(String dataMapJson, byte[] aesKey);

    String decryptDataMap(String encryptedDataMap, byte[] aesKey);

    void clearKey(byte[] aesKey);
}
