package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.model.Manifest;
import com.empresa.estructuracion.batch.service.ManifestWriterService;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ManifestService implements ManifestWriterService {
    private final ObjectMapper objectMapper;

    public ManifestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toJson(Manifest manifest) {
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize manifest.", ex);
        }
    }
}
