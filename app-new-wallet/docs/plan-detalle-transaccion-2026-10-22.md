# Detalle de la transacción: solo el dato compartido, con su nombre en español

**Fecha:** 2026-10-22 · **Objetivo:** que «Información de la transacción» muestre únicamente los datos que el
verifier pidió (p. ej. «Mayor de 18 años»), con su nombre en español, al estilo de la otra wallet:
una fila por dato con ✓ y «Enviado», sin hashes ni campos técnicos.

**Decisiones del usuario:** cada fila lleva **nombre + ✓ Enviado** (sin repetir el valor); la traducción se hace
**en el emisor y en la wallet**; el APK con solo el filtro **no se instala todavía** (se entrega todo junto).

## Veredicto: viable, riesgo bajo-medio

Todo el trabajo cabe en la wallet y en el emisor de la Registraduría. No toca el SDK ni el protocolo.

## 1. Qué ya está hecho (en el árbol, sin instalar)

- `core-logic/.../extension/PresentedDocumentExtensions.kt`: `PresentedDocument.sharedClaims()` quita los claims
  técnicos del SD-JWT (`iss, sub, aud, iat, nbf, exp, jti, nonce, cnf, status, vct, vct#integrity, _sd, _sd_alg`).
- Usado en `TransactionDetailsInteractor` (lista y contador) y en `TransactionsInteractor` (contador de la lista).
- 5 pruebas nuevas (`TestPresentedDocumentExtensions`) y los tests de ambos interactores pasan.
- APK compilado (`app/build/outputs/apk/dev/debug/app-dev-debug.apk`, paquete `.dev.v2`), **sin instalar**.

## 2. Qué falta

### 2.1 Pantalla (wallet)

| Pieza | Cambio |
|---|---|
| `TransactionDetailsUi` | Nuevo modelo de filas: `SharedDataRowUi(label)` agrupadas por documento. El modelo actual (`NestedListItem` expandible) se conserva para firmas y emisiones |
| `TransactionDetailsInteractor.toGroupedNestedClaims` | Para presentaciones, generar las filas desde `sharedClaims()` en vez de `createKeyValue` (que construye pares clave/valor y grupos) |
| `TransactionDetailsScreen` | Nuevo composable de tarjeta: `✓  <nombre>  Enviado`, sin expandir. La cabecera («Compartiste N datos», fecha, verificado) ya existe |
| `strings.xml` | «Enviado», título de sección («De tu identidad digital» o el nombre del documento) |
| Tests | `TestTransactionDetailsInteractor` tiene 8 casos que fijan la estructura expandible de las presentaciones: hay que actualizarlos |

Notas de diseño:
- Los datos anidados (p. ej. `address.street_address`) se aplanan a hojas; si hay ambigüedad se antepone el grupo.
- El título de documento hoy sale con sufijo de formato («Cédula de Ciudadanía (SD-JWT VC Compact)»). Conviene
  mostrar solo «Cédula de Ciudadanía» o el título de sección genérico.
- Al no mostrar el valor, el historial ya no deja datos personales a la vista (aunque el SDK los siga guardando en
  su registro local).

### 2.2 Nombres en español

- **La wallet ya traduce.** `DocumentHelper.kt` tiene `LOCAL_CLAIM_LABELS` (≈75 claims del PID y de la credencial
  académica) con los textos en `values/strings.xml`, que es en español (la app es monolingüe). Lo usa
  `getReadableNameFromIdentifier`.
- **Huecos que hay que cerrar en esa tabla:**
  - `age_equal_or_over` y sus umbrales (`18`, `21`): hoy el nombre sale bien solo porque el emisor ya declara el
    español de `Mayor de 18/21 años` (parche de la edad).
  - `age_over_21` (mdoc).
  - En el detalle de la transacción la clave es la **ruta completa** (`address.street_address`) y la tabla está
    indexada por la **hoja** (`street_address`): para los datos anidados cae al inglés. Hay que buscar por hoja.
- **Emisor (Registraduría):** `SdJwtVcPidClaims.kt` / `MsoMdocPidClaims.kt` declaran solo `ENGLISH` en el
  `display` de cada claim. Añadir `es` es un cambio central (≈40 claims), pero solo se ve en credenciales
  **emitidas después**: los PID ya emitidos guardan los metadatos del momento de la emisión.

## 3. Riesgos y cómo se cubren

| Riesgo | Cobertura |
|---|---|
| Claim real con nombre técnico en la raíz (p. ej. `sub`) quedaría oculto | No existe en este PID. Si aparece, se saca de la lista fija |
| Credencial sin metadatos de display en español | Respaldo: tabla local → metadatos del emisor → clave técnica |
| Cambia el árbol de tests de presentaciones | Se actualizan los 8 casos; no se tocan los de emisión/firma |
| Reemitir el PID para ver el español del emisor | Mientras tanto la tabla local cubre los documentos actuales |

## 4. Orden de trabajo propuesto

1. Cerrar los huecos de `LOCAL_CLAIM_LABELS` (+ búsqueda por hoja) y sus pruebas.
2. Modelo y composable de filas «✓ nombre · Enviado»; actualizar `TestTransactionDetailsInteractor`.
3. Español en el emisor de la Registraduría (y su prueba unitaria), reconstruir imagen y reiniciar solo el emisor.
4. Compilar un solo APK con todo; instalar con `adb install -r` (conserva documentos) y revisar el historial con las
   presentaciones de la discoteca de hoy.

## Datos de contacto en Información personal (2026-10-22)

- El onboarding pide teléfono y correo, pero los borraba al terminar (`OnboardingStore.clear()`). Ahora
  `markCompleted()` los copia a `PersonalInfo` (`phone`, `email`, opcionales), que se guarda **cifrada** en
  `PersonalInfoStore`. Los registros anteriores se leen igual (las claves nuevas pueden faltar).
- Pantalla «Info Personal»: sección **«Otros datos»** con Celular y Correo electrónico, y un botón Agregar/Editar
  (diálogo con validación: celular de 10 dígitos, correo válido). Sirve para onboardings anteriores, que no
  guardaron esos datos.
- Son datos de contacto de la persona: **no los firma ningún emisor, así que no se pueden presentar a un verifier**.
  Además la «verificación» por código del onboarding es una simulación (el código lo genera y muestra la app).
- No se escriben en el código: se ingresan desde la pantalla y quedan en el almacenamiento cifrado de la app.
- Pruebas: `TestPersonalInfo` (3 casos, con Robolectric).
