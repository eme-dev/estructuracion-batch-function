package com.empresa.estructuracion.batch.function;

import com.empresa.estructuracion.batch.config.DependencyConfig;
import com.empresa.estructuracion.batch.exception.DataMapInvalidException;
import com.empresa.estructuracion.batch.service.DataMapCryptoService;
import com.empresa.estructuracion.batch.service.EstructuracionBatchService;
import com.empresa.estructuracion.batch.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final DataMapCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public EstructuracionBatchFunction() {
        DependencyConfig dependencyConfig = DependencyConfig.fromEnvironment();
        this.batchService = dependencyConfig.estructuracionBatchService();
        this.cryptoService = dependencyConfig.dataMapCryptoService();
        this.objectMapper = dependencyConfig.objectMapper();
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

    @FunctionName("EstructuracionDataMapEncrypt")
    public HttpResponseMessage encryptDataMap(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "estructuracion/crypto/encrypt-datamap") HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {
        if (!cryptoTestFunctionEnabled()) {
            return request.createResponseBuilder(HttpStatus.FORBIDDEN)
                    .body("Crypto test function is disabled.")
                    .build();
        }

        byte[] aesKey = null;
        try {
            String body = request.getBody()
                    .filter(value -> !value.isBlank())
                    .orElseThrow(() -> new DataMapInvalidException("Request body is required."));
            String dataMapJson = extractDataMapJson(body);
            String compactDataMap = JsonUtils.compact(dataMapJson, objectMapper);

            aesKey = cryptoService.unwrapAesKey();
            String encryptedDataMap = cryptoService.encryptDataMap(compactDataMap, aesKey);
            String response = objectMapper.writeValueAsString(new EncryptDataMapResponse(encryptedDataMap));

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(response)
                    .build();
        } catch (DataMapInvalidException ex) {
            context.getLogger().warning("Invalid dataMap JSON received for encryption.");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ex.getMessage())
                    .build();
        } catch (Exception ex) {
            context.getLogger().severe("Unable to encrypt dataMap: " + ex.getClass().getSimpleName());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to encrypt dataMap.")
                    .build();
        } finally {
            cryptoService.clearKey(aesKey);
        }
    }

    private String extractDataMapJson(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode dataMap = root.get("dataMap");
            return dataMap == null ? body : objectMapper.writeValueAsString(dataMap);
        } catch (Exception ex) {
            throw new DataMapInvalidException("Request body must be valid JSON.", ex);
        }
    }

    private boolean cryptoTestFunctionEnabled() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("CRYPTO_TEST_FUNCTION_ENABLED", "false"));
    }

    private record EncryptDataMapResponse(String encryptedDataMap) {
    }
}
