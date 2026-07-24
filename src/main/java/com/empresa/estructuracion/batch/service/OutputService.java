package com.empresa.estructuracion.batch.service;

import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.ExecutionContext;

public interface OutputService {
    BatchResult generate(ExecutionContext execution);
}
