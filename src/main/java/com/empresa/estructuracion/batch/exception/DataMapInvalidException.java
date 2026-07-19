package com.empresa.estructuracion.batch.exception;

public class DataMapInvalidException extends BatchException {
    public DataMapInvalidException(String message) {
        super(message);
    }

    public DataMapInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
