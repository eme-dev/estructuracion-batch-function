package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.exception.DataMapInvalidException;
import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.service.OutputWriterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonlGenerationService implements OutputWriterService {
    private final ObjectMapper objectMapper;

    public JsonlGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] rowBytes(StagingRecord record, String decryptedDataMap, MessageDigest digest) {
        try {
            JsonNode dataMap = objectMapper.readTree(decryptedDataMap);
            JsonNode listaTables = objectMapper.readTree(record.listaTables());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.sourceId());
            row.put("fileName", record.fileName());
            row.put("statusFile", record.statusFile());
            row.put("clientName", record.clientName());
            row.put("creationDateTime", DateTimeFormatter.ISO_INSTANT.format(record.creationDateTime()));
            row.put("dataMap", dataMap);
            row.put("listaTables", listaTables);
            row.put("documentType", record.documentType());
            row.put("uniqueHash", HexFormat.of().formatHex(record.uniqueHash()));

            byte[] bytes = (objectMapper.writeValueAsString(row) + "\n").getBytes(StandardCharsets.UTF_8);
            digest.update(bytes);
            return bytes;
        } catch (Exception ex) {
            throw new DataMapInvalidException("Unable to generate JSONL row from dataMap/listaTables JSON.", ex);
        }
    }
}
