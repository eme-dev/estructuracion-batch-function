package com.empresa.estructuracion.batch.service;

public interface DataMapCryptoService {
    byte[] unwrapAesKey();

    String decryptDataMap(String dataMap, byte[] aesKey);

    void clearKey(byte[] aesKey);
}
