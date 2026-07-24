package com.empresa.estructuracion.batch.service.impl;

import com.empresa.estructuracion.batch.config.BatchProperties;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.ExecutionContext;
import com.empresa.estructuracion.batch.model.PublicationSession;
import com.empresa.estructuracion.batch.model.StagingRecord;
import com.empresa.estructuracion.batch.repository.ExecutionRepository;
import com.empresa.estructuracion.batch.repository.StagingRepository;
import com.empresa.estructuracion.batch.service.OutputService;
import com.empresa.estructuracion.batch.service.DataMapCryptoService;
import com.empresa.estructuracion.batch.service.OutputWriterService;
import com.empresa.estructuracion.batch.service.StoragePublisherService;
import com.empresa.estructuracion.batch.util.FileNameUtils;
import com.empresa.estructuracion.batch.util.HashUtils;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.List;

public class StagedOutputService implements OutputService {

    private final BatchProperties properties;
    private final StagingRepository stagingRepository;
    private final ExecutionRepository executionRepository;
    private final DataMapCryptoService cryptoService;
    private final OutputWriterService outputWriterService;
    private final StoragePublisherService storagePublisherService;

    public StagedOutputService(
            BatchProperties properties,
            StagingRepository stagingRepository,
            ExecutionRepository executionRepository,
            DataMapCryptoService cryptoService,
            OutputWriterService outputWriterService,
            StoragePublisherService storagePublisherService) {
        this.properties = properties;
        this.stagingRepository = stagingRepository;
        this.executionRepository = executionRepository;
        this.cryptoService = cryptoService;
        this.outputWriterService = outputWriterService;
        this.storagePublisherService = storagePublisherService;
    }

    @Override
    public BatchResult generate(ExecutionContext execution) {
        byte[] aesKey = null;
        try {
            aesKey = cryptoService.unwrapAesKey();
            return generate(execution, aesKey);
        } finally {
            cryptoService.clearKey(aesKey);
        }
    }

    private BatchResult generate(ExecutionContext execution, byte[] aesKey) {
        String outputName = FileNameUtils.outputPath(properties.storageBasePath(), execution);
        PublicationSession publicationSession = storagePublisherService.beginPublication();
        MessageDigest digest = HashUtils.sha256();
        long contentLength = 0;
        long recordCount = 0;
        int blockNumber = 0;
        int lastSourceId = 0;
        boolean headerWritten = false;

        while (true) {
            List<StagingRecord> records = stagingRepository.readBatch(
                    execution.executionId(),
                    lastSourceId,
                    properties.batchSize());
            if (records.isEmpty()) {
                break;
            }

            ByteArrayOutputStream block = new ByteArrayOutputStream();
            if (!headerWritten) {
                byte[] header = outputWriterService.headerBytes(digest);
                block.writeBytes(header);
                contentLength += header.length;
                headerWritten = true;
            }

            for (StagingRecord stagingRecord : records) {
                String decryptedDataMap = cryptoService.decryptDataMap(stagingRecord.encryptedDataMap(), aesKey);
                byte[] row = outputWriterService.rowBytes(stagingRecord, decryptedDataMap, digest);
                block.writeBytes(row);
                contentLength += row.length;
                recordCount++;
                lastSourceId = stagingRecord.sourceId();
            }

            storagePublisherService.stageBlock(publicationSession, outputName, ++blockNumber, block.toByteArray());
            executionRepository.updateHeartbeat(execution.executionId());
        }

        if (!headerWritten) {
            byte[] header = outputWriterService.headerBytes(digest);
            storagePublisherService.stageBlock(publicationSession, outputName, ++blockNumber, header);
            contentLength += header.length;
        }

        return new BatchResult(
                execution.executionId(),
                outputName,
                publicationSession,
                recordCount,
                contentLength,
                digest.digest());
    }
}
