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
fileName,statusFile,clientName,creationDateTime,dataMap,listaTables,documentType,isReprocessed,reprocessDateTime,reprocessCount
```

`dataMap` se escribe como JSON compacto dentro de una columna CSV. `listaTables` se emite vacio por contrato del consumidor. Las comillas internas se escapan segun el formato CSV estandar.

En la tabla origen, `dataMap` debe llegar como un string Base64 que contiene `IV + ciphertext`. Para AES/GCM el IV esperado es de 12 bytes y el `ciphertext` debe incluir el tag de autenticacion.

Ejemplo de una fila:

```csv
archivo-demo-001.pdf,1,Juan Perez,2026-07-17T10:30:00Z,"{""cliente"":""Juan Perez"",""tipoDocumento"":""DNI""}",,DNI,false,,0
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

El build fija explicitamente la misma version para `jackson-databind`, `jackson-datatype-jsr310` y `jackson-dataformat-xml`. No se debe bajar Jackson por debajo de `2.21.5`, porque versiones anteriores de la linea `2.21.x` y `2.22.0` vuelven a quedar expuestas a vulnerabilidades reportadas en `jackson-databind`.

Tambien usa Netty BOM para forzar una version corregida de las dependencias transitivas usadas por Azure SDK. Esto evita volver a empaquetar versiones vulnerables como `netty-codec` anteriores a `4.1.125.Final`.

Las dependencias de Azure SDK, Reactor Netty, Reactor Core y Netty se mantienen alineadas desde `dependencyManagement`. No se debe bajar `reactor-netty-http` por debajo de `1.2.18`, porque versiones anteriores vuelven a quedar expuestas a fugas de credenciales en escenarios de redirects configurados explicitamente.

El perfil `security` ejecuta OWASP Dependency-Check y falla el build cuando encuentra vulnerabilidades con CVSS `>= 7.0`. Este perfil requiere salida a internet o un mirror corporativo con certificados configurados en el truststore del JDK.
