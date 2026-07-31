package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.service.OutputWriterService;
import com.empresa.estructuracion.batch.util.CsvUtils;
import com.empresa.estructuracion.batch.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CsvGenerationService implements OutputWriterService {
    private static final String HEADER = CsvUtils.line(List.of(
            "fileName",
            "statusFile",
            "clientName",
            "creationDateTime",
            "dataMap",
            "listaTables",
            "documentType",
            "isReprocessed",
            "reprocessDateTime",
            "reprocessCount"));

    private final ObjectMapper objectMapper;

    public CsvGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] headerBytes(MessageDigest digest) {
        byte[] bytes = HEADER.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes);
        return bytes;
    }

    @Override
    public byte[] rowBytes(StagingRecord record, String decryptedDataMap, MessageDigest digest) {
        String compactDataMap = JsonUtils.compact(decryptedDataMap, objectMapper);
        String line = CsvUtils.line(List.of(
                record.fileName(),
                String.valueOf(record.statusFile()),
                record.clientName(),
                DateTimeFormatter.ISO_INSTANT.format(record.creationDateTime()),
                compactDataMap,
                "",
                record.documentType(),
                String.valueOf(record.isReprocessed()),
                formatInstant(record.reprocessDateTime()),
                String.valueOf(record.reprocessCount())));
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes);
        return bytes;
    }

    private String formatInstant(Instant value) {
        return value == null ? "" : DateTimeFormatter.ISO_INSTANT.format(value);
    }
}
