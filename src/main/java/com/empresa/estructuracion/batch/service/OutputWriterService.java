package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.model.StagingRecord;

import java.security.MessageDigest;

public interface OutputWriterService {
    byte[] rowBytes(StagingRecord record, String decryptedDataMap, MessageDigest digest);
}
