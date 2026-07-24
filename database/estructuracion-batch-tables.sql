IF NOT EXISTS
(
    SELECT 1
    FROM sys.schemas
    WHERE name = 'ocrt'
)
BEGIN
    EXEC('CREATE SCHEMA ocrt');
END;
GO

CREATE TABLE ocrt.Estructuracion
(
    id                  INT IDENTITY(1,1) NOT NULL,
    fileName            NVARCHAR(MAX) NOT NULL,
    statusFile          BIT NOT NULL,
    clientName          NVARCHAR(MAX) NOT NULL,
    creationDateTime    DATETIME NOT NULL,
    dataMap             NVARCHAR(MAX) NOT NULL,
    listaTables         NVARCHAR(MAX) NOT NULL,
    documentType        VARCHAR(50) NOT NULL,
    uniqueHash          BINARY(32) NOT NULL,

    CONSTRAINT PK_Estructuracion
        PRIMARY KEY (id)
);
GO

CREATE INDEX IX_Estructuracion_Cutoff
ON ocrt.Estructuracion
(
    statusFile,
    creationDateTime,
    id
);
GO

CREATE INDEX IX_Estructuracion_UniqueHash
ON ocrt.Estructuracion
(
    uniqueHash
);
GO

CREATE TABLE ocrt.EstructuracionEjecucion
(
    executionId       UNIQUEIDENTIFIER NOT NULL,
    businessDate      DATE NOT NULL,
    cutoffFromUtc     DATETIME2(3) NOT NULL,
    cutoffToUtc       DATETIME2(3) NOT NULL,
    maxSourceId       INT NULL,

    status            VARCHAR(20) NOT NULL,
    startedAt         DATETIME2(3) NOT NULL,
    finishedAt        DATETIME2(3) NULL,
    heartbeatAt       DATETIME2(3) NULL,

    attemptCount      INT NOT NULL
        CONSTRAINT DF_EstructuracionEjecucion_Attempt DEFAULT (1),

    fileName          NVARCHAR(500) NULL,
    fileHash          BINARY(32) NULL,
    errorMessage      NVARCHAR(2000) NULL,

    CONSTRAINT PK_EstructuracionEjecucion
        PRIMARY KEY (executionId),

    CONSTRAINT CK_EstructuracionEjecucion_Status
        CHECK
        (
            status IN
            (
                'Preparing',
                'InProgress',
                'Publishing',
                'Completed',
                'Failed'
            )
        ),

    CONSTRAINT CK_EstructuracionEjecucion_Cutoff
        CHECK (cutoffFromUtc < cutoffToUtc)
);
GO

CREATE INDEX IX_EstructuracionEjecucion_StatusHeartbeat
ON ocrt.EstructuracionEjecucion
(
    status,
    heartbeatAt
);
GO

CREATE UNIQUE INDEX UX_EstructuracionEjecucion_ActiveBusinessDate
ON ocrt.EstructuracionEjecucion (businessDate)
WHERE status IN ('Preparing', 'InProgress', 'Publishing', 'Completed');
GO

CREATE TABLE ocrt.EstructuracionStaging
(
    stagingId           BIGINT IDENTITY(1,1) NOT NULL,
    executionId         UNIQUEIDENTIFIER NOT NULL,
    sourceId            INT NOT NULL,

    fileName            NVARCHAR(MAX) NOT NULL,
    statusFile          BIT NOT NULL,
    clientName          NVARCHAR(MAX) NOT NULL,
    creationDateTime    DATETIME NOT NULL,
    encryptedDataMap    NVARCHAR(MAX) NOT NULL,
    listaTables         NVARCHAR(MAX) NOT NULL,
    documentType        VARCHAR(50) NOT NULL,
    uniqueHash          BINARY(32) NOT NULL,

    CONSTRAINT PK_EstructuracionStaging
        PRIMARY KEY (stagingId),

    CONSTRAINT FK_EstructuracionStaging_Ejecucion
        FOREIGN KEY (executionId)
        REFERENCES ocrt.EstructuracionEjecucion(executionId),

    CONSTRAINT UQ_EstructuracionStaging_ExecutionSource
        UNIQUE (executionId, sourceId),

    CONSTRAINT UQ_EstructuracionStaging_ExecutionHash
        UNIQUE (executionId, uniqueHash)
);
GO

CREATE INDEX IX_EstructuracionStaging_ExecutionSource
ON ocrt.EstructuracionStaging
(
    executionId,
    sourceId
);
GO
