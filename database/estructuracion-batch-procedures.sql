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

CREATE OR ALTER PROCEDURE ocrt.usp_FindRecoverableEstructuracionExecution
    @StaleMinutes INT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT TOP (1)
           executionId,
           businessDate,
           cutoffFromUtc,
           cutoffToUtc,
           status,
           attemptCount,
           fileName,
           fileHash
    FROM ocrt.EstructuracionEjecucion
    WHERE status IN ('Preparing', 'InProgress', 'Publishing', 'Failed')
      AND
      (
          status = 'Failed'
          OR heartbeatAt < DATEADD(MINUTE, -@StaleMinutes, SYSUTCDATETIME())
          OR heartbeatAt IS NULL
      )
    ORDER BY startedAt;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_CreateEstructuracionExecutionSnapshot
    @ExecutionId UNIQUEIDENTIFIER,
    @BusinessDate DATE,
    @CutoffFromUtc DATETIME2(3),
    @CutoffToUtc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @LockResult INT;
    DECLARE @LockResource NVARCHAR(255);
    DECLARE @MaxSourceId INT;

    BEGIN TRANSACTION;

    SET @LockResource = CONCAT('EstructuracionBatch:', CONVERT(VARCHAR(10), @BusinessDate, 120));

    EXEC @LockResult = sp_getapplock
        @Resource = @LockResource,
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 0;

    IF @LockResult < 0
        THROW 51001, 'No se pudo obtener lock aplicativo para businessDate.', 1;

    INSERT INTO ocrt.EstructuracionEjecucion
    (
        executionId,
        businessDate,
        cutoffFromUtc,
        cutoffToUtc,
        maxSourceId,
        status,
        startedAt,
        heartbeatAt,
        attemptCount
    )
    VALUES
    (
        @ExecutionId,
        @BusinessDate,
        @CutoffFromUtc,
        @CutoffToUtc,
        NULL,
        'Preparing',
        SYSUTCDATETIME(),
        SYSUTCDATETIME(),
        1
    );

    SELECT @MaxSourceId = MAX(id)
    FROM ocrt.Estructuracion
    WHERE creationDateTime >= @CutoffFromUtc
      AND creationDateTime <  @CutoffToUtc
      AND statusFile = 1;

    UPDATE ocrt.EstructuracionEjecucion
       SET maxSourceId = @MaxSourceId
     WHERE executionId = @ExecutionId;

    INSERT INTO ocrt.EstructuracionStaging
    (
        executionId,
        sourceId,
        fileName,
        statusFile,
        clientName,
        creationDateTime,
        encryptedDataMap,
        listaTables,
        documentType,
        uniqueHash
    )
    SELECT
        @ExecutionId,
        e.id,
        e.fileName,
        e.statusFile,
        e.clientName,
        e.creationDateTime,
        e.dataMap,
        e.listaTables,
        e.documentType,
        e.uniqueHash
    FROM ocrt.Estructuracion e
    WHERE e.statusFile = 1
      AND (@MaxSourceId IS NULL OR e.id <= @MaxSourceId)
      AND e.creationDateTime >= @CutoffFromUtc
      AND e.creationDateTime <  @CutoffToUtc
    ORDER BY e.id;

    SELECT
        executionId,
        businessDate,
        cutoffFromUtc,
        cutoffToUtc,
        status,
        attemptCount,
        fileName,
        fileHash
    FROM ocrt.EstructuracionEjecucion
    WHERE executionId = @ExecutionId;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_MarkEstructuracionInProgress
    @ExecutionId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionEjecucion
       SET status = 'InProgress',
           finishedAt = NULL,
           heartbeatAt = SYSUTCDATETIME(),
           errorMessage = NULL
     WHERE executionId = @ExecutionId
       AND status IN ('Preparing', 'InProgress', 'Failed');

    IF @@ROWCOUNT = 0
        THROW 51002, 'La ejecucion no esta en un estado recuperable para InProgress.', 1;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_UpdateEstructuracionHeartbeat
    @ExecutionId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionEjecucion
       SET heartbeatAt = SYSUTCDATETIME()
     WHERE executionId = @ExecutionId
       AND status = 'InProgress';
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_MarkEstructuracionPublishing
    @ExecutionId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionEjecucion
       SET status = 'Publishing',
           heartbeatAt = SYSUTCDATETIME()
     WHERE executionId = @ExecutionId
       AND status = 'InProgress';

    IF @@ROWCOUNT = 0
        THROW 51003, 'La ejecucion no esta en InProgress.', 1;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_CountEstructuracionStaging
    @ExecutionId UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;

    SELECT COUNT_BIG(1)
    FROM ocrt.EstructuracionStaging
    WHERE executionId = @ExecutionId;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_ReadEstructuracionStagingBatch
    @ExecutionId UNIQUEIDENTIFIER,
    @LastSourceId INT,
    @BatchSize INT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT TOP (@BatchSize)
           stagingId,
           sourceId,
           fileName,
           statusFile,
           clientName,
           creationDateTime,
           encryptedDataMap,
           listaTables,
           documentType,
           uniqueHash
    FROM ocrt.EstructuracionStaging
    WHERE executionId = @ExecutionId
      AND sourceId > @LastSourceId
    ORDER BY sourceId;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_CompleteEstructuracionExecution
    @ExecutionId UNIQUEIDENTIFIER,
    @FileName NVARCHAR(500),
    @FileHash BINARY(32),
    @RecordCount BIGINT,
    @ContentLength BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRANSACTION;

    UPDATE ocrt.EstructuracionEjecucion
       SET status = 'Completed',
           finishedAt = SYSUTCDATETIME(),
           heartbeatAt = SYSUTCDATETIME(),
           fileName = @FileName,
           fileHash = @FileHash,
           errorMessage = NULL
     WHERE executionId = @ExecutionId
       AND status = 'Publishing';

    IF @@ROWCOUNT = 0
        THROW 51004, 'La ejecucion no esta en Publishing.', 1;

    UPDATE ocrt.EstructuracionStaging
       SET processingStatus = 'Processed',
           errorMessage = NULL
     WHERE executionId = @ExecutionId
       AND processingStatus = 'Pending';

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_FailEstructuracionExecution
    @ExecutionId UNIQUEIDENTIFIER,
    @ErrorCode NVARCHAR(100),
    @ErrorMessage NVARCHAR(2000)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionEjecucion
       SET status = 'Failed',
           finishedAt = SYSUTCDATETIME(),
           heartbeatAt = SYSUTCDATETIME(),
           errorMessage = CONCAT(@ErrorCode, ': ', @ErrorMessage)
     WHERE executionId = @ExecutionId
       AND status IN ('Preparing', 'InProgress', 'Publishing');
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_FailEstructuracionStagingRow
    @StagingId BIGINT,
    @ErrorCode NVARCHAR(100),
    @ErrorMessage NVARCHAR(2000)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionStaging
       SET processingStatus = 'Failed',
           errorMessage = CONCAT(@ErrorCode, ': ', @ErrorMessage)
     WHERE stagingId = @StagingId;
END;
GO
