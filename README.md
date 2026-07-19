# Estructuracion Batch Function

Azure Function Java 21 para generar un archivo CSV diario a partir del snapshot de `ocrt.Estructuracion`.

## Flujo inicial

1. Timer Trigger a las 00:10.
2. Calculo de `businessDate` del dia anterior.
3. Registro de ejecucion en `Preparing`.
4. Creacion del snapshot en staging.
5. Lectura por lotes.
6. Desencriptado de `dataMap`.
7. Validacion de JSON.
8. Generacion CSV por bloques no confirmados.
9. Publicacion del CSV, manifiesto y cierre SQL.

## Configuracion local

Copiar `local.settings.sample.json` como `local.settings.json` y completar valores reales.

El cron del Timer Trigger se resuelve desde `BATCH_TIMER_CRON`. Para la ejecucion diaria a las 00:10 usar `0 10 0 * * *`.

## Ejecucion manual

Para pruebas a demanda existe un HTTP Trigger administrativo:

```text
POST /api/estructuracion/batch/run
```

El endpoint usa authorization level `FUNCTION`, por lo que requiere function key cuando se ejecuta fuera del host local.

## Nota de runtime

El proyecto esta configurado para Java 21. Para compilar localmente, el `JAVA_HOME` debe apuntar a un JDK 21.
