package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.service.OutputWriterService;
import com.empresa.estructuracion.batch.util.CsvUtils;
import com.empresa.estructuracion.batch.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

public class CsvGenerationService implements OutputWriterService {
    private static final String HEADER = CsvUtils.line(List.of(
            "id",
            "fileName",
            "statusFile",
            "clientName",
            "creationDateTime",
            "dataMap",
            "listaTables",
            "documentType",
            "uniqueHash"));

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
        String compactListaTables = JsonUtils.compact(record.listaTables(), objectMapper);
        String line = CsvUtils.line(List.of(
                String.valueOf(record.sourceId()),
                record.fileName(),
                String.valueOf(record.statusFile()),
                record.clientName(),
                DateTimeFormatter.ISO_INSTANT.format(record.creationDateTime()),
                compactDataMap,
                compactListaTables,
                record.documentType(),
                HexFormat.of().formatHex(record.uniqueHash())));
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes);
        return bytes;
    }
}
