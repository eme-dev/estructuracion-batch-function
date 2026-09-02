package com.empresa.estructuracion.batch.function;

import com.empresa.estructuracion.batch.service.EstructuracionBatchService;
import com.empresa.estructuracion.batch.model.BatchResult;
import com.empresa.estructuracion.batch.model.PublicationSession;
import com.empresa.estructuracion.batch.model.ReprocessDateRequest;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EstructuracionBatchFunctionTest {
    private static final UUID EXECUTION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private final EstructuracionBatchService batchService = mock(EstructuracionBatchService.class);
    private final EstructuracionBatchFunction function = new EstructuracionBatchFunction(batchService);
    private final Logger logger = Logger.getLogger("test");

    @Test
    void runShouldDelegateScheduledExecution() {
        ExecutionContext context = executionContext();

        function.run("timer-info", context);

        verify(batchService).execute(logger);
    }

    @Test
    void runShouldDeclareProductionRetryPolicy() throws Exception {
        Method runMethod = EstructuracionBatchFunction.class.getMethod(
                "run",
                String.class,
                ExecutionContext.class);

        ExponentialBackoffRetry retry = runMethod.getAnnotation(ExponentialBackoffRetry.class);

        assertNotNull(retry);
        assertEquals(2, retry.maxRetryCount());
        assertEquals("00:00:30", retry.minimumInterval());
        assertEquals("00:10:00", retry.maximumInterval());
    }

    @Test
    void runManualShouldDelegateExecutionAndReturnAccepted() {
        ExecutionContext context = executionContext();
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        when(request.createResponseBuilder(HttpStatus.ACCEPTED)).thenReturn(builder);
        when(builder.body("Estructuracion batch executed.")).thenReturn(builder);
        when(builder.build()).thenReturn(response);

        HttpResponseMessage result = function.runManual(request, context);

        assertSame(response, result);
        verify(batchService).execute(logger);
        verify(request).createResponseBuilder(HttpStatus.ACCEPTED);
    }

    @Test
    void runDateReprocessShouldDelegateRequestAndReturnOk() {
        ExecutionContext context = executionContext();
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        when(request.getBody()).thenReturn(Optional.of("""
                {
                  "businessDate": "2026-08-23",
                  "requestedBy": "soporte01",
                  "reason": "Reproceso solicitado por negocio"
                }
                """));
        when(batchService.executeDateReprocess(any(ReprocessDateRequest.class), eq(logger)))
                .thenReturn(new BatchResult(
                        EXECUTION_ID,
                        "estructuracion_20260823_%s.csv".formatted(EXECUTION_ID),
                        new PublicationSession(),
                        10,
                        500,
                        new byte[]{10, 11}));
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(builder);
        when(builder.body(any())).thenReturn(builder);
        when(builder.build()).thenReturn(response);

        HttpResponseMessage result = function.runDateReprocess(request, context);

        assertSame(response, result);
        verify(batchService).executeDateReprocess(
                eq(new ReprocessDateRequest(
                        LocalDate.of(2026, 8, 23),
                        "soporte01",
                        "Reproceso solicitado por negocio")),
                eq(logger));
        verify(request).createResponseBuilder(HttpStatus.OK);
    }

    @Test
    void runDateReprocessShouldReturnBadRequestWhenBodyIsMissing() {
        ExecutionContext context = executionContext();
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        when(request.getBody()).thenReturn(Optional.empty());
        when(request.createResponseBuilder(HttpStatus.BAD_REQUEST)).thenReturn(builder);
        when(builder.body("Request body is required.")).thenReturn(builder);
        when(builder.build()).thenReturn(response);

        HttpResponseMessage result = function.runDateReprocess(request, context);

        assertSame(response, result);
        verify(request).createResponseBuilder(HttpStatus.BAD_REQUEST);
    }

    private ExecutionContext executionContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(logger);
        return context;
    }
}
