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
9. Publicacion del CSV y cierre SQL.

## Salida CSV

El archivo final usa extension `.csv` y content type `text/csv; charset=utf-8`.

Columnas:

```csv
id,fileName,statusFile,clientName,creationDateTime,dataMap,listaTables,documentType,uniqueHash
```

`dataMap` y `listaTables` se escriben como JSON compacto dentro de una columna CSV. Las comillas internas se escapan segun el formato CSV estandar.

Ejemplo de una fila:

```csv
1,archivo-demo-001.pdf,1,Juan Perez,2026-07-17T10:30:00Z,"{""cliente"":""Juan Perez"",""tipoDocumento"":""DNI""}","{""tables"":[""tabla_demo""]}",DNI,abc123
```

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

## Calidad y seguridad

Compilar:

```powershell
mvn -q -DskipTests compile
```

Empaquetar:

```powershell
mvn -q -DskipTests package
```

Validar convergencia de dependencias:

```powershell
mvn -Pquality validate
```

Escanear vulnerabilidades conocidas:

```powershell
mvn -Psecurity verify
```

El build usa Jackson BOM para mantener alineadas las versiones de `jackson-core`, `jackson-databind`, `jackson-dataformat-xml` y modulos relacionados.

Tambien usa Netty BOM para forzar una version corregida de las dependencias transitivas usadas por Azure SDK. Esto evita volver a empaquetar versiones vulnerables como `netty-codec` anteriores a `4.1.125.Final`.

Las dependencias de Azure SDK, Reactor Netty, Reactor Core y Netty se mantienen alineadas desde `dependencyManagement`. No se debe bajar `reactor-netty-http` por debajo de `1.2.18`, porque versiones anteriores vuelven a quedar expuestas a fugas de credenciales en escenarios de redirects configurados explicitamente.
