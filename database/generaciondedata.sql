SET NOCOUNT ON;
SET XACT_ABORT ON;
SET IMPLICIT_TRANSACTIONS OFF;

DECLARE @TargetRows     INT = 200000;
DECLARE @BatchSize      INT = 2000;

DECLARE @CurrentRows    INT;
DECLARE @RowsToInsert   INT;
DECLARE @Inserted       INT = 0;
DECLARE @ThisBatch      INT;
DECLARE @SourceRows     INT;

DECLARE @RunTag VARCHAR(32) =
    REPLACE(CONVERT(VARCHAR(36), NEWID()), '-', '');

------------------------------------------------------------
-- 1. Cantidad actual
------------------------------------------------------------
SELECT @CurrentRows = COUNT(*)
FROM ocrt.Estructuracion;

SET @RowsToInsert = @TargetRows - @CurrentRows;

RAISERROR(
    'Registros actuales: %d. Registros a generar: %d',
    0,
    1,
    @CurrentRows,
    @RowsToInsert
) WITH NOWAIT;


IF @RowsToInsert <= 0
BEGIN
    RAISERROR(
        'La tabla ya tiene %d registros.',
        0,
        1,
        @CurrentRows
    ) WITH NOWAIT;

    RETURN;
END;


------------------------------------------------------------
-- 2. Guardar SOLO los IDs de los registros originales
--    No copiamos dataMap ni listaTables a tempdb
------------------------------------------------------------
SELECT
    ROW_NUMBER() OVER (ORDER BY id) AS sourceRow,
    id
INTO #SourceIds
FROM ocrt.Estructuracion;


CREATE UNIQUE CLUSTERED INDEX IX_SourceIds
ON #SourceIds(sourceRow);


SELECT @SourceRows = COUNT(*)
FROM #SourceIds;


IF @SourceRows = 0
BEGIN
    RAISERROR(
        'No existen registros originales para copiar.',
        16,
        1
    );

    RETURN;
END;


------------------------------------------------------------
-- 3. Generar números para cada batch
------------------------------------------------------------
SELECT TOP (@BatchSize)
           ROW_NUMBER() OVER (
        ORDER BY a.object_id, b.object_id
    ) AS n
INTO #Numbers
FROM sys.all_objects a
         CROSS JOIN sys.all_objects b;


CREATE UNIQUE CLUSTERED INDEX IX_Numbers
ON #Numbers(n);


------------------------------------------------------------
-- 4. Insertar por bloques
------------------------------------------------------------
WHILE @Inserted < @RowsToInsert
BEGIN

    SET @ThisBatch =
        CASE
            WHEN (@RowsToInsert - @Inserted) > @BatchSize
                THEN @BatchSize
            ELSE (@RowsToInsert - @Inserted)
END;


INSERT INTO ocrt.Estructuracion
(
    fileName,
    statusFile,
    clientName,
    creationDateTime,
    dataMap,
    listaTables,
    documentType,
    uniqueHash,
    isReprocessed,
    reprocessDateTime,
    reprocessCount
)
SELECT

----------------------------------------------------
-- Único dato diferente
----------------------------------------------------
    CASE
        WHEN LOWER(RIGHT(e.fileName, 4)) = '.pdf'
            THEN
    LEFT(e.fileName, LEN(e.fileName) - 4)
    + '_COPY_'
    + @RunTag
    + '_'
    + CAST(@Inserted + n.n AS VARCHAR(20))
    + '.pdf'

    ELSE
    e.fileName
    + '_COPY_'
    + @RunTag
    + '_'
    + CAST(@Inserted + n.n AS VARCHAR(20))
END,

        ----------------------------------------------------
        -- Todo lo demás se replica
        ----------------------------------------------------
        0,
        e.clientName,
        DATEADD(DAY, -1, GETDATE()),
        e.dataMap,
        '',
        e.documentType,
        e.uniqueHash,
        e.isReprocessed,
        e.reprocessDateTime,
        e.reprocessCount

    FROM #Numbers n

    INNER JOIN #SourceIds s
        ON s.sourceRow =
            (
                (
                    CAST(@Inserted AS BIGINT)
                    + n.n
                    - 1
                ) % @SourceRows
            ) + 1

    INNER JOIN ocrt.Estructuracion e
        ON e.id = s.id

    WHERE n.n <= @ThisBatch;


    --------------------------------------------------------
    -- Cada INSERT ya quedó confirmado
    --------------------------------------------------------
    SET @Inserted = @Inserted + @ThisBatch;


    --------------------------------------------------------
    -- Mostrar progreso inmediatamente
    --------------------------------------------------------
    RAISERROR(
        'Insertados: %d de %d',
        0,
        1,
        @Inserted,
        @RowsToInsert
    ) WITH NOWAIT;

END;


------------------------------------------------------------
-- 5. Limpieza
------------------------------------------------------------
DROP TABLE #Numbers;
DROP TABLE #SourceIds;


------------------------------------------------------------
-- 6. Resultado
------------------------------------------------------------
SELECT
    COUNT(*) AS TotalRegistros
FROM ocrt.Estructuracion;