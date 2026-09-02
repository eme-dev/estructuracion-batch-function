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
    @StaleMinutes INT,
    @MaxAttempts INT,
    @Now DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;

    SELECT TOP (1)
           executionId,
           executionType,
           businessDate,
           cutoffFromUtc,
           cutoffToUtc,
           status,
           attemptCount,
           fileName,
           fileHash
    FROM ocrt.EstructuracionEjecucion
    WHERE executionType = 'DAILY_CUTOFF'
      AND
    (
    (
        status = 'Failed'
        AND attemptCount < @MaxAttempts
    )
    OR
    (
        status IN ('Preparing', 'InProgress')
        AND attemptCount < @MaxAttempts
        AND
        (
            heartbeatAt < DATEADD(MINUTE, -@StaleMinutes, @Now)
            OR heartbeatAt IS NULL
        )
    )
    OR
    (
        status = 'Publishing'
        AND
        (
            heartbeatAt < DATEADD(MINUTE, -@StaleMinutes, @Now)
            OR heartbeatAt IS NULL
        )
    )
    )
    ORDER BY startedAt;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_CreateEstructuracionExecutionSnapshot
    @ExecutionId UNIQUEIDENTIFIER,
    @BusinessDate DATE,
    @CutoffFromUtc DATETIME2(3),
    @CutoffToUtc DATETIME2(3),
    @Now DATETIME2(3),
    @MaxAttempts INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @LockResult INT;
    DECLARE @LockResource NVARCHAR(255);
    DECLARE @MaxSourceId INT;
    DECLARE @FirstSourceId INT;
    DECLARE @LastSourceId INT;
    DECLARE @SnapshotRecordCount BIGINT;

    BEGIN TRANSACTION;

    SET @LockResource = 'EstructuracionBatch:Snapshot';

    EXEC @LockResult = sp_getapplock
        @Resource = @LockResource,
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 0;

    IF @LockResult < 0
        THROW 51001, 'No se pudo obtener lock aplicativo para businessDate.', 1;

    IF EXISTS
    (
        SELECT 1
        FROM ocrt.EstructuracionEjecucion
        WHERE status IN ('Preparing', 'InProgress', 'Publishing')
    )
        THROW 51008, 'Existe una ejecucion activa. No se puede crear una nueva foto de staging.', 1;

    IF EXISTS
    (
        SELECT 1
        FROM ocrt.EstructuracionEjecucion
        WHERE businessDate = @BusinessDate
          AND executionType = 'DAILY_CUTOFF'
          AND status = 'Failed'
          AND attemptCount >= @MaxAttempts
    )
        THROW 51006, 'La ejecucion ya agoto el numero maximo de intentos.', 1;

    IF EXISTS
    (
        SELECT 1
        FROM ocrt.EstructuracionEjecucion
        WHERE businessDate = @BusinessDate
          AND executionType = 'DAILY_CUTOFF'
          AND status = 'Failed'
          AND attemptCount < @MaxAttempts
    )
        THROW 51007, 'Existe una ejecucion fallida recuperable para la fecha de negocio.', 1;

    TRUNCATE TABLE ocrt.EstructuracionStaging;

    INSERT INTO ocrt.EstructuracionEjecucion
    (
        executionId,
        executionType,
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
        'DAILY_CUTOFF',
        @BusinessDate,
        @CutoffFromUtc,
        @CutoffToUtc,
        NULL,
        'Preparing',
        @Now,
        @Now,
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

    IF @MaxSourceId IS NOT NULL
    BEGIN
        INSERT INTO ocrt.EstructuracionStaging WITH (TABLOCK)
        (
            executionId,
            sourceId
        )
        SELECT
            @ExecutionId,
            e.id
        FROM ocrt.Estructuracion e
        WHERE e.statusFile = 1
          AND e.id <= @MaxSourceId
          AND e.creationDateTime >= @CutoffFromUtc
          AND e.creationDateTime <  @CutoffToUtc;
    END;

    SELECT
        @FirstSourceId = MIN(sourceId),
        @LastSourceId = MAX(sourceId),
        @SnapshotRecordCount = COUNT_BIG(1)
    FROM ocrt.EstructuracionStaging
    WHERE executionId = @ExecutionId;

    UPDATE ocrt.EstructuracionEjecucion
       SET firstSourceId = @FirstSourceId,
           lastSourceId = @LastSourceId,
           snapshotRecordCount = @SnapshotRecordCount
     WHERE executionId = @ExecutionId;

    SELECT
        executionId,
        executionType,
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

CREATE OR ALTER PROCEDURE ocrt.usp_CreateEstructuracionDateReprocessSnapshot
    @ExecutionId UNIQUEIDENTIFIER,
    @BusinessDate DATE,
    @CutoffFromUtc DATETIME2(3),
    @CutoffToUtc DATETIME2(3),
    @Now DATETIME2(3),
    @MaxAttempts INT,
    @RequestedBy NVARCHAR(150),
    @ReprocessReason NVARCHAR(500)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @LockResult INT;
    DECLARE @MaxSourceId INT;
    DECLARE @FirstSourceId INT;
    DECLARE @LastSourceId INT;
    DECLARE @SnapshotRecordCount BIGINT;

    BEGIN TRANSACTION;

    EXEC @LockResult = sp_getapplock
        @Resource = 'EstructuracionBatch:Snapshot',
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 0;

    IF @LockResult < 0
        THROW 51009, 'No se pudo obtener lock aplicativo para snapshot de reproceso.', 1;

    IF EXISTS
    (
        SELECT 1
        FROM ocrt.EstructuracionEjecucion
        WHERE status IN ('Preparing', 'InProgress', 'Publishing')
    )
        THROW 51010, 'Existe una ejecucion activa. No se puede crear un reproceso por fecha.', 1;

    TRUNCATE TABLE ocrt.EstructuracionStaging;

    INSERT INTO ocrt.EstructuracionEjecucion
    (
        executionId,
        executionType,
        businessDate,
        cutoffFromUtc,
        cutoffToUtc,
        maxSourceId,
        status,
        startedAt,
        heartbeatAt,
        attemptCount,
        requestedBy,
        requestedAt,
        reprocessReason
    )
    VALUES
    (
        @ExecutionId,
        'REPROCESS_DATE',
        @BusinessDate,
        @CutoffFromUtc,
        @CutoffToUtc,
        NULL,
        'Preparing',
        @Now,
        @Now,
        1,
        @RequestedBy,
        @Now,
        @ReprocessReason
    );

    SELECT @MaxSourceId = MAX(id)
    FROM ocrt.Estructuracion
    WHERE creationDateTime >= @CutoffFromUtc
      AND creationDateTime <  @CutoffToUtc
      AND statusFile = 1;

    UPDATE ocrt.EstructuracionEjecucion
       SET maxSourceId = @MaxSourceId
     WHERE executionId = @ExecutionId;

    IF @MaxSourceId IS NOT NULL
    BEGIN
        INSERT INTO ocrt.EstructuracionStaging WITH (TABLOCK)
        (
            executionId,
            sourceId
        )
        SELECT
            @ExecutionId,
            e.id
        FROM ocrt.Estructuracion e
        WHERE e.statusFile = 1
          AND e.id <= @MaxSourceId
          AND e.creationDateTime >= @CutoffFromUtc
          AND e.creationDateTime <  @CutoffToUtc;
    END;

    SELECT
        @FirstSourceId = MIN(sourceId),
        @LastSourceId = MAX(sourceId),
        @SnapshotRecordCount = COUNT_BIG(1)
    FROM ocrt.EstructuracionStaging
    WHERE executionId = @ExecutionId;

    UPDATE ocrt.EstructuracionEjecucion
       SET firstSourceId = @FirstSourceId,
           lastSourceId = @LastSourceId,
           snapshotRecordCount = @SnapshotRecordCount
     WHERE executionId = @ExecutionId;

    SELECT
        executionId,
        executionType,
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
    @ExecutionId UNIQUEIDENTIFIER,
    @RetryAttempt BIT,
    @Now DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionEjecucion
       SET status = 'InProgress',
           finishedAt = NULL,
           heartbeatAt = @Now,
           attemptCount = attemptCount + CASE WHEN @RetryAttempt = 1 THEN 1 ELSE 0 END,
           errorMessage = NULL
     WHERE executionId = @ExecutionId
       AND status IN ('Preparing', 'InProgress', 'Failed');

    IF @@ROWCOUNT = 0
        THROW 51002, 'La ejecucion no esta en un estado recuperable para InProgress.', 1;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_UpdateEstructuracionHeartbeat
    @ExecutionId UNIQUEIDENTIFIER,
    @Now DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionEjecucion
       SET heartbeatAt = @Now
     WHERE executionId = @ExecutionId
       AND status = 'InProgress';
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_MarkEstructuracionPublishing
    @ExecutionId UNIQUEIDENTIFIER,
    @Now DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionEjecucion
       SET status = 'Publishing',
           heartbeatAt = @Now
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
           CAST(s.sourceId AS BIGINT) AS stagingId,
           s.sourceId,
           e.fileName,
           e.statusFile,
           e.clientName,
           e.creationDateTime,
           e.dataMap,
           e.documentType,
           e.isReprocessed,
           e.reprocessDateTime,
           e.reprocessCount
    FROM ocrt.EstructuracionStaging s
    INNER JOIN ocrt.Estructuracion e
        ON e.id = s.sourceId
    WHERE s.executionId = @ExecutionId
      AND s.sourceId > @LastSourceId
    ORDER BY s.sourceId;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_CompleteEstructuracionExecution
    @ExecutionId UNIQUEIDENTIFIER,
    @FileName NVARCHAR(500),
    @FileHash BINARY(32),
    @RecordCount BIGINT,
    @ContentLength BIGINT,
    @Now DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRANSACTION;

    UPDATE ocrt.EstructuracionEjecucion
       SET status = 'Completed',
           finishedAt = @Now,
           heartbeatAt = @Now,
           fileName = @FileName,
           fileHash = @FileHash,
           recordCount = @RecordCount,
           contentLength = @ContentLength,
           errorMessage = NULL
     WHERE executionId = @ExecutionId
       AND status = 'Publishing';

    IF @@ROWCOUNT = 0
        THROW 51004, 'La ejecucion no esta en Publishing.', 1;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ocrt.usp_FailEstructuracionExecution
    @ExecutionId UNIQUEIDENTIFIER,
    @ErrorCode NVARCHAR(100),
    @ErrorMessage NVARCHAR(MAX),
    @Now DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE ocrt.EstructuracionEjecucion
       SET status = 'Failed',
           finishedAt = @Now,
           heartbeatAt = @Now,
           errorMessage = CONCAT(@ErrorCode, ': ', @ErrorMessage)
     WHERE executionId = @ExecutionId
       AND status IN ('Preparing', 'InProgress', 'Publishing');
END;
GO

