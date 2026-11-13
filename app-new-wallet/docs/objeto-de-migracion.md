# Objeto de Migración (ARF Topic 34, Mig_01…Mig_07b)

Estado: **lógica probada en JVM y cableada en Ajustes (exportar/importar manual). APK debug generado; NO probado en teléfono.**

## Qué es
Lista que la wallet mantiene al día con, por cada PID/atestación, lo necesario para volver a
pedirla (tipo, formato, emisor, URL del Credential Issuer, `credential_configuration_id`).
No guarda atributos ni claves privadas (Mig_03). Se exporta cifrada con una frase de la persona
(Mig_05) para guardarla donde ella elija (Mig_04) e importarla en una wallet nueva (Mig_06).

## Código
`business-logic/src/main/java/eu/europa/ec/businesslogic/migration/`
- `MigrationObject.kt`: modelo (`MigrationObject`, `MigrationEntry`), interfaz `MigratableDocument`,
  `syncWith(documentos)`, `withoutDocument(id)` (Mig_03b) y `restorePlan(ids)` (selección, PID primero, Mig_07).
- `MigrationCrypto.kt`: PBKDF2-HMAC-SHA256 (600 000 it.) + AES-256-GCM.
- `MigrationBackup.kt`: `export(obj, frase)` → bytes; `import(bytes, frase)` → objeto.
- Pruebas: `business-logic/src/test/.../migration/TestMigrationBackup.kt` (16 tests).

## Formato del archivo (v1)
`"NWMO"(4) | versión(1)=1 | iteraciones(4, BE) | sal(16) | nonce(12) | AES-GCM(JSON UTF-8)+tag(16)`.
La cabecera va como AAD: cualquier byte alterado hace fallar la importación. JSON en claro:
`{version, createdAt, updatedAt, entries:[{documentId, attestationType, format, isPid, deviceBound,
issuerIdentifier, credentialIssuerUrl, credentialConfigurationId, issuerDisplayName, documentDisplayName}]}`.

## Qué quedó cableado (30-oct-2026)
- Adaptador `core-logic/.../corelogic/migration/IssuedDocumentMigratable.kt`: `IssuedDocument` -> `MigratableDocument`
  (id, formato `mso_mdoc`/`dc+sd-jwt`, docType/vct, `isPid`, emisor = `issuerMetadata.credentialIssuerIdentifier`,
  `documentConfigurationIdentifier`, nombres visibles). No lee claims ni claves. Documentos sin metadatos de emisor se omiten.
- `dashboard-feature/.../interactor/MigrationInteractor.kt`: filas de Ajustes, validación de frase (mín. 8), exportar
  (cifra con `MigrationBackup.export`), escribir/leer por Uri e importar (`MigrationBackup.import`).
- `SettingsViewModel`/`SettingsScreen`: filas «Exportar copia de migración» e «Importar copia de migración» (antes de
  «Historial de cambios»), diálogos de frase (campo oculto + confirmación al exportar), selectores del sistema
  `CreateDocument` (`wallet-migracion-AAAAMMDD.nwmo`, `application/octet-stream`) y `OpenDocument`, y diálogo con la lista
  de entradas (nombre, emisor, tipo, etiqueta PID) con casillas, PID primero (`restorePlan`).
- Cadenas `migration_*` en `resources-logic/.../values/strings.xml` (único locale del proyecto: español).
- Sin persistencia: el objeto es una **instantánea** que se construye al exportar con `syncWith(documentosActuales)`.
  No hay disparo automático tras emitir/borrar (por eso Mig_03b queda cubierto solo en el sentido de que la copia
  nueva refleja el estado actual).
- «Volver a pedir» lleva a la pantalla EXISTENTE de agregar documento (`IssuanceScreens.AddDocument`, sin filtro de
  tipo). NO se pre-selecciona emisor ni tipo: el flujo existente (`IssuanceFlowType.ExtraDocument(formatType)`) solo
  filtra por tipo y no permite fijar emisor, y no se pudo comprobar sin dispositivo. La lista del diálogo es el
  recordatorio de qué pedir (se pierde al salir de ese diálogo). No se inventó ningún endpoint.
- No se tocaron `allowBackup`, firmas, claves ni dependencias.

## Pruebas automáticas
- `business-logic`: 16 (formato/cifrado). `core-logic` `TestIssuedDocumentMigratable`: 4.
- `dashboard-feature` `TestMigrationInteractor`: 6; `TestSettingsViewModelMigration`: 5; `TestSettingsViewModel` (existente, adaptado): 15.
- Comando: `./gradlew :dashboard-feature:testDevDebugUnitTest :core-logic:testDevDebugUnitTest --offline`.
- APK: `./gradlew :app:assembleDevDebug --offline` -> `app/build/outputs/apk/dev/debug/app-dev-debug.apk`
  (firmado con la clave `newWalletDev` ya configurada para debug).

## Cómo probarlo en el teléfono
1. Instala el APK (`app-new-wallet-migracion-debug.apk`) y ten al menos un documento emitido (idealmente PID + otro).
2. Ajustes -> «Exportar copia de migración». Escribe una frase de 8+ caracteres, repítela, Continuar.
3. En el selector del sistema elige dónde guardar `wallet-migracion-AAAAMMDD.nwmo` (Descargas, Drive...). Debe salir «Copia de migración guardada».
4. Copia el archivo a un lugar que sobreviva (Descargas/nube/otro teléfono). Desinstala la app o abre otro teléfono con una instalación nueva (misma app, wallet recién activada).
5. Ajustes -> «Importar copia de migración», elige el archivo, escribe la frase.
   - Frase mala o archivo alterado: mensaje «frase incorrecta o archivo alterado» y puedes reintentar.
6. Debe aparecer la lista (PID primero) con emisor y tipo y casillas. Desmarca alguna, pulsa «Volver a pedir».
7. Te lleva a «Agregar documento»: elige a mano cada emisor/tipo de la lista y emítelo de nuevo.
8. Pruebas extra: archivo cualquiera (no .nwmo) -> «no es una copia válida»; cancelar el selector -> no pasa nada; wallet sin documentos -> «no hay documentos...».

## Qué falta / límites
- Sin probar en dispositivo ni emulador, ni con un emisor real (los diálogos y selectores son solo compilados).
- Sin pre-selección de emisor/tipo al volver a pedir (ver arriba); sin re-emisión automática por `credentialIssuerUrl`.
- Sin persistencia ni sincronización automática; sin ofrecerlo en el onboarding (Mig_06 solo manual desde Ajustes).
- Sin log de transacciones en el objeto (Mig_01 segunda parte, Mig_07b) ni TS10 completo (Mig_02).
- Mig_03a (atestaciones no ligadas con atributos): solo se soporta la opción «volver a pedir».
- La frase se maneja como `String` en el campo de texto (Compose) y se convierte a `CharArray` que se limpia; no se puede garantizar borrado de la `String` en memoria.
- `allowBackup="false"` no se toca.
