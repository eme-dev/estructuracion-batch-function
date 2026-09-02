package com.empresa.estructuracion.batch.function;

import com.empresa.estructuracion.batch.config.DependencyConfig;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.ManualExecutionResponse;
import com.empresa.estructuracion.batch.model.ReprocessDateRequest;
import com.empresa.estructuracion.batch.service.EstructuracionBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import java.util.Optional;

public class EstructuracionBatchFunction {
    private static final int SCHEDULED_MAX_RETRY_COUNT = 2;
    private static final String SCHEDULED_MIN_RETRY_INTERVAL = "00:00:30";
    private static final String SCHEDULED_MAX_RETRY_INTERVAL = "00:10:00";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final EstructuracionBatchService batchService;

    public EstructuracionBatchFunction() {
        DependencyConfig dependencyConfig = DependencyConfig.fromEnvironment();
        this.batchService = dependencyConfig.estructuracionBatchService();
    }

    EstructuracionBatchFunction(EstructuracionBatchService batchService) {
        this.batchService = batchService;
    }

    @FunctionName("EstructuracionBatch")
    @ExponentialBackoffRetry(
            maxRetryCount = SCHEDULED_MAX_RETRY_COUNT,
            minimumInterval = SCHEDULED_MIN_RETRY_INTERVAL,
            maximumInterval = SCHEDULED_MAX_RETRY_INTERVAL)
    public void run(
            @TimerTrigger(name = "timer", schedule = "%BATCH_TIMER_CRON%") String timerInfo,
            ExecutionContext context) {
        context.getLogger().info("Starting scheduled estructuracion batch.");
        batchService.execute(context.getLogger());
    }

    @FunctionName("EstructuracionBatchManual")
    public HttpResponseMessage runManual(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "estructuracion/batch/run") HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {
        context.getLogger().info("Starting manual estructuracion batch.");
        batchService.execute(context.getLogger());
        return request.createResponseBuilder(HttpStatus.ACCEPTED)
                .body("Estructuracion batch executed.")
                .build();
    }

    @FunctionName("EstructuracionBatchReprocessDate")
    public HttpResponseMessage runDateReprocess(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "estructuracion/batch/reprocess/date") HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {
        try {
            ReprocessDateRequest reprocessRequest = readReprocessDateRequest(request.getBody());
            context.getLogger().info("Starting date reprocess for businessDate="
                    + reprocessRequest.businessDate());
            BatchResult result = batchService.executeDateReprocess(reprocessRequest, context.getLogger());
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(ManualExecutionResponse.from(result))
                    .build();
        } catch (IllegalArgumentException ex) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ex.getMessage())
                    .build();
        }
    }

    private ReprocessDateRequest readReprocessDateRequest(Optional<String> body) {
        String requestBody = body
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Request body is required."));
        try {
            return OBJECT_MAPPER.readValue(requestBody, ReprocessDateRequest.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Request body must be valid JSON.", ex);
        }
    }
}
