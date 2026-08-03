package com.empresa.estructuracion.batch.function;

import com.empresa.estructuracion.batch.config.DependencyConfig;
import com.empresa.estructuracion.batch.service.EstructuracionBatchService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import java.util.Optional;

public class EstructuracionBatchFunction {
    private final EstructuracionBatchService batchService;

    public EstructuracionBatchFunction() {
        DependencyConfig dependencyConfig = DependencyConfig.fromEnvironment();
        this.batchService = dependencyConfig.estructuracionBatchService();
    }

    EstructuracionBatchFunction(EstructuracionBatchService batchService) {
        this.batchService = batchService;
    }

    @FunctionName("EstructuracionBatch")
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
}
