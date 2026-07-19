package com.empresa.estructuracion.batch.model;

public enum ExecutionStatus {
    PREPARING("Preparing"),
    IN_PROGRESS("InProgress"),
    PUBLISHING("Publishing"),
    COMPLETED("Completed"),
    FAILED("Failed");

    private final String databaseValue;

    ExecutionStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}

