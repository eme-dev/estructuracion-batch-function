package com.empresa.estructuracion.batch.repository;

import com.empresa.estructuracion.batch.model.StagingRecord;

import java.util.List;
import java.util.UUID;

public interface StagingRepository {
    long count(UUID executionId);

    List<StagingRecord> readBatch(UUID executionId, int lastSourceId, int batchSize);
}
