package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.model.Manifest;

public interface ManifestWriterService {
    String toJson(Manifest manifest);
}

