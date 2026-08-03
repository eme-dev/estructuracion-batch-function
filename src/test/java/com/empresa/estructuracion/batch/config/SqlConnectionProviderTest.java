package com.empresa.estructuracion.batch.config;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlConnectionProviderTest {
    @Test
    void constructorShouldConfigureLoginTimeout() {
        new SqlConnectionProvider(new SqlConfig("jdbc:sqlserver://localhost;databaseName=demo"));

        assertEquals(30, DriverManager.getLoginTimeout());
    }
}
