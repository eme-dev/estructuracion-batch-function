package com.empresa.estructuracion.batch.config;

import com.empresa.estructuracion.batch.config.SqlConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqlConnectionProvider {
    private static final int LOGIN_TIMEOUT_SECONDS = 30;

    private final SqlConfig sqlConfig;

    public SqlConnectionProvider(SqlConfig sqlConfig) {
        this.sqlConfig = sqlConfig;
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(sqlConfig.connectionString());
    }
}
