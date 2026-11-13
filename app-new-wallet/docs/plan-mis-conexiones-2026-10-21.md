# "Mis conexiones" — análisis del código actual y plan de trabajo

**Fecha:** 2026-10-21 · **Objetivo:** rediseñar la pantalla *Mis conexiones* (historial de
transacciones) con el diseño de referencia: tres filtros (Todo / Compartido / Recibido) bajo
la barra de búsqueda, una fila por transacción con el icono del sentido (morado pastel =
compartido, verde pastel = recibido), la contraparte, el número de datos, la fecha y la hora,
y una vista de detalle por registro.

---

## 1. Cómo está programada hoy

### 1.1 Pantalla y navegación

| Pieza | Fichero |
|---|---|
| Pestaña "Conexiones" | `dashboard-feature/.../ui/component/BottomNavigation.kt:60` (`BottomNavigationItem.Transactions`) |
| Ruta | `dashboard-feature/.../ui/dashboard/DashboardScreen.kt:133` → `composable(BottomNavigationItem.Transactions.route)` |
| Pantalla | `dashboard-feature/.../ui/transactions/list/TransactionsScreen.kt` (648 líneas) |
| ViewModel | `dashboard-feature/.../ui/transactions/list/TransactionsViewModel.kt` (545 líneas) |
| Interactor | `dashboard-feature/.../interactor/TransactionsInteractor.kt` (580 líneas) |
| Detalle | `dashboard-feature/.../ui/transactions/detail/TransactionDetailsScreen.kt` (481 líneas) + `TransactionDetailsViewModel.kt` + `interactor/TransactionDetailsInteractor.kt` |
| Modelos | `list/model/TransactionUi.kt`, `list/model/TransactionCategoryUi.kt`, `list/model/TransactionsFilterableAttributes.kt`, `model/TransactionLogUi.kt`, `detail/model/TransactionDetailsUi.kt` |

### 1.2 Lo que se ve hoy

`TransactionsScreen.Content()` (líneas 245‑281) monta un `LazyColumn` con:

1. `FiltersSearchBar` (búsqueda + botón de filtros) — líneas 249‑261.
2. Si no hay resultados: `NoResults()` → *"No se encontraron transacciones…"*.
3. Si los hay: un `item` por `TransactionCategoryUi` con su `LazyColumn` interno.

Agrupación (`TransactionCategoryUi`): **Hoy / Esta semana / mes‑año** (`TransactionCategoryUi.kt:37‑57`),
no por día.

Fila (`TransactionCategory`, líneas 361‑393): `WrapListItem` con `WrapListItemDefaults.groupedShape(index, count)`
(estilo tarjeta agrupada). La `ListItemDataUi` la monta el interactor (líneas 247‑262):

| Zona | Contenido actual |
|---|---|
| `mainContentData` | `transaction.name` → **el nombre de la contraparte** (`relyingParty.name`) |
| `overlineText` | estado: "Completada" / "Fallida" (verde / rojo) |
| `supportingContentData` | fecha "inteligente" (`toFormattedDisplayableDate()`, interactor 497‑536): "Justo ahora" / "hace X min" / "10:23 AM" / "21 ene 2025 03:30 PM" |
| `trailingContentData` | `TextWithIcon(texto = tipo de transacción, icono = KeyboardArrowRight)` → "Presentación" + flecha |
| **leading** | **no hay** |

### 1.3 El modelo de dominio

`core-logic/.../model/TransactionLogDataDomain.kt`:

```kotlin
sealed interface TransactionLogDataDomain {
    data class PresentationLog(..., relyingParty: TransactionLog.RelyingParty, documents: List<PresentedDocument>)
    data class IssuanceLog(...)     // sin datos de contraparte
    data class SigningLog(...)      // sin datos de contraparte
}
```

`TransactionLogDataExtensions.toTransactionLogData()` (líneas 30‑42) **solo sabe mapear
`PresentationTransactionLog`**: los otros dos casos lanzan `IllegalArgumentException`, con el
TODO *"once Core supports Issuance, Signing"*.

### 1.4 ⚠️ El hueco de datos: **el Core no registra emisiones**

En `eudi-lib-android-wallet-core:0.30.2` el único productor de logs es
`transactionLogging/presentation/TransactionsListener.kt`, que observa `TransferEvent`
(**presentaciones**). No existe `IssuanceTransactionLog` ni `SigningTransactionLog` en la librería
(la clase `TransactionLog.Type` declara `Issuance`/`Signing`, pero nadie los emite).

Consecuencia: **la tabla `transactionLogs` solo tiene presentaciones.** Para la pestaña
*Recibido* no hay fuente en el log de transacciones.

### 1.5 De dónde sí salen los datos de "Recibido"

`WalletCoreDocumentsController.getAllDocuments()` → `List<IssuedDocument>`, que trae:

| Dato | Campo |
|---|---|
| Emisor | `issuerMetadata: IssuerMetadata?` → `.display` (lista de `Display` con `name`/`logo`) |
| Nombre legible del emisor | helper **ya existente**: `core-logic/.../extension/DocumentExtensions.kt:28` (`issuerDisplay.getLocalizedValue(...)`) |
| Fecha/hora de la emisión | `issuedAt: Instant` |
| Credencial | `name` (nombre de la credencial) |
| Atributos | `data: DocumentData` |

Es decir: **lo recibido se puede construir desde los documentos emitidos**, que es donde la
app ya tiene la información (y de donde saca el "Found N issued documents for DC API" del log).

### 1.6 Lo que falta para el diseño pedido

| # | Falta | Dificultad |
|---|---|---|
| 1 | Los tres chips **Todo / Compartido / Recibido** bajo la barra de búsqueda | media: no hay chip filtrador reutilizable |
| 2 | Icono **leading**: caja + flecha ↗ morado pastel (compartido) / ↙ verde pastel (recibido) | media: los iconos **no existen** en el repo |
| 3 | El **nº de datos** ("Compartiste N datos") | baja: `documents.sumOf { it.claims.size }` |
| 4 | **Fecha** en la fila (hoy solo hay la fecha "inteligente" en el supporting) | baja |
| 5 | **Hora** en la fila (hoy va en el supporting y el trailing es el tipo de transacción) | baja |
| 6 | La fila de **recibido** (hoy no existe: no hay emisiones en el log) | **alta**: hay que unificar dos fuentes de datos |
| 7 | Agrupar por **día** ("19 de octubre") en vez de Hoy/Semana/Mes | baja |
| 8 | Detalle que distinga compartido/recibido | media |

---

## 2. Inventario de lo que hay en el repo (y lo que no)

### 2.1 Chips

- **Existe** `WrapChip` (`ui-logic/.../wrap/WrapChip.kt:39‑56`) — envuelve el `InputChip` de M3.
  **Trampas**: la forma por defecto es `RoundedCornerShape(8.dp)` (no píldora) y
  `colors`/`border` por defecto **no cubren el estado seleccionado** (hay que pasar
  `selectedContainerColor`, `selectedLabelColor` y `selectedBorderColor` explícitamente).
- **No existe** `FilterChip`, `SegmentedButton`, ni ninguna píldora filtradora.
- **Molde a copiar** (es `private`): `CredentialViewModeToggle` en
  `dashboard-feature/.../ui/documents/list/DocumentsScreen.kt:555‑617` — fila con fondo
  `surfaceContainerHighest`, botón seleccionado con fondo `primary` y el resto transparente.

### 2.2 Iconos (`ui-logic/.../component/AppIcons.kt`, 62 entradas)

| Necesario | Estado | Nota |
|---|---|---|
| Caja + flecha ↗ (salir) | ❌ no existe | más cercano: `AppIcons.OpenNew` (`ic_open_new`, "open in new") |
| Caja + flecha ↙ (entrar) | ❌ no existe | — |
| Flecha ↑ / ↓ | ⚠️ chevrons (`KeyboardArrowUp/Down`), no flechas rectas | |
| Caja / bandeja | ❌ no existe | más cercanos: `Download`, `Documents`, `Connections` (⇄), `Transactions` (reloj) |
| **Material icons extended SÍ está disponible** (`resources-logic/build.gradle.kts:36`, `material-icons-extended` 1.7.8) → `Icons.Filled.NorthEast`, `Icons.Filled.SouthWest`… | ✅ | pero el repo **exige** registrarlos en `AppIconKey` + `AppIcons` + `content_description_*` (ver el KDoc de `AppIcons.kt:34‑44`) |

**Oportunidad**: ya existe `resources-logic/src/main/res/drawable/ic_export.xml` (icono Material
"upload": flecha saliendo de una caja), **huérfano** (no está en `AppIconKey` ni se usa). Es
exactamente la forma "caja con flecha hacia arriba" del diseño.

### 2.3 Icono leading en las filas

`ListItemDataUi.leadingContentData: ListItemLeadingContentDataUi?` (`ui-logic/.../component/ListItem.kt:93`)
con subtipos `Icon` (24 dp, `tint: ThemeColorKey?`), `UserImage`, `AsyncImage`, `RadioButton`.

Dos limitaciones:
1. El tint por defecto del icono leading es `colorScheme.primary` (índigo), y **solo admite
   `ThemeColorKey`**, y los `ThemeColorKey` disponibles son 6: `Pending`, `Warning`, `Error`,
   `Success`, `Primary`, `White` → **no hay lavanda ni verde suave**.
2. **No admite fondo**: el diseño pide un cuadrado redondeado con color pastel detrás de la
   flecha. Habría que añadir un tipo nuevo de leading (p. ej. `IconInContainer`).

### 2.4 Colores (ya existen en el tema)

`resources-logic/.../theme/values/ThemeColors.kt`:

| Rol | Hex | Uso |
|---|---|---|
| `secondaryContainer` | `#E8DEF8` | 🟣 **lavanda pastel** → caja de "compartido" |
| `secondary` | `#D6D9F9` | 🟣 lavanda más saturada → **la flecha** |
| `tertiaryContainer` | `#DAEEE0` | 🟢 **verde suave** → caja de "recibido" |
| `tertiary` | `#E4EEE7` | 🟢 verde suave → **la flecha** |
| `primary` | `#3F3FB8` | ⬛ píldora seleccionada |
| `onPrimary` | `#FFFFFF` | texto de la píldora seleccionada |
| `surfaceContainerLowest` | `#FFFFFF` | píldora no seleccionada (con borde) |

El tema es **siempre claro** (`ThemeManager.kt:50‑56`).

### 2.5 Formatos de fecha/hora (`business-logic/.../util/DateUtils.kt`)

| Patrón | Salida | Nota |
|---|---|---|
| `DAY_MONTH_YEAR_FULL_PATTERN = "dd MMMM yyyy"` | "19 de octubre de 2026" | ✅ el más cercano al diseño |
| `FULL_DATETIME_PATTERN = "dd MMM yyyy hh:mm a"` | "21 ene 2025 03:30 PM" | el que usa el detalle hoy |
| `FULL_DATETIME_PATTERN_24H_SEPARATED_BY_DASH = "dd MMMM yyyy - HH:mm"` | "19 de octubre de 2026 - 15:30" | ✅ 24 h |
| `hoursMinutesFormatter` (`"hh:mm a"`) | "10:23 AM" | 12 h, con AM/PM |
| `monthYearFormatter` (`"MMMM yyyy"`) | "octubre 2026" | el `displayName` de `TransactionCategoryUi.Month` |

**Falta** el patrón `"dd MMMM"` ("19 de octubre", sin año) que usa el diseño como cabecera de grupo.

### 2.6 La vista de detalle (ya existe y es reutilizable)

`TransactionDetailsScreen` muestra: tipo de transacción, estado (chip), fecha, contraparte +
icono `Certified` si está verificado, y una sección expandible con los **claims compartidos**
(`ExpandableDataSection` sobre `ExpandableListItemUi.NestedListItem`).

`TransactionDetailsUi` ya tiene `transactionDate: String` (formateada) y
`relyingPartyName`/`relyingPartyIsVerified`. `transactionDetailsDataSigned` es **siempre `null`**
(`TransactionDetailsInteractor.kt:155`, otro TODO del Core).

---

## 3. Plan de trabajo

### Fase 0 — Cerrar el hueco de datos (bloqueante para "Recibido")

Decidir y documentar la fuente de la pestaña *Recibido*. **Propuesta: los documentos emitidos**
(`IssuedDocument`), no el log de transacciones, porque el Core no registra emisiones.

Añadir a `core-logic`:
- `TransactionLogDataDomain.ReceivedCredentialLog(issuerName, issuerLogo, credentialName, issuedAt, attributesCount)`.
  (O un `sealed` nuevo; mantener `IssuanceLog`/`SigningLog` para cuando el Core los soporte.)
- Un método en `WalletCoreDocumentsController` que devuelva esas emisiones ya tipadas
  (`getReceivedCredentials(): List<ReceivedCredentialLog>`), usando el helper de nombre de emisor
  que ya existe (`DocumentExtensions.kt:28`).

**Decisión a confirmar**: qué es "N datos recibidos". Propuesta: el nº de claims de la credencial
recibida (`IssuedDocument.data`). Alternativa: mostrar el nombre de la credencial en vez de un
número (más honesto: en una emisión no "recibes datos", recibes una credencial).

### Fase 1 — Modelo de dominio unificado

Crear en `dashboard-feature` un modelo único que sirve para los dos sentidos:

```kotlin
enum class ConnectionDirection { SHARED, RECEIVED }

data class ConnectionUi(
    val id: String,
    val counterpartyName: String,
    val counterpartyIsVerified: Boolean?,
    val direction: ConnectionDirection,
    val dataCount: Int?,            // "Compartiste 7 datos" / "Recibiste 4 datos"
    val credentialName: String?,     // solo en RECEIVED
    val creationLocalDateTime: LocalDateTime,
    val documents: List<ExpandableListItemUi.NestedListItem>, // para el detalle
)
```

`TransactionsInteractor` pasa a devolver `presentations + issuances` unificados y ordenados por
fecha. `TransactionsFilterableAttributes` gana el campo `direction` para poder filtrar.

### Fase 2 — Iconos y color (2 días)

1. Crear los vectores `ic_share_out.xml` (caja + flecha ↗) y `ic_share_in.xml` (caja + flecha ↙).
   Se pueden hacer con `material-icons-extended` (`NorthEast`/`SouthWest` sobre un `Inbox`) o a
   mano como XML; **registrarlos en `AppIconKey` + `AppIcons` + `content_description_*`**
   (regla del repo). Reutilizar `ic_export.xml` si encaja.
2. Añadir dos `ThemeColorKey` (`Lavender`, `Mint` ↔ `secondaryContainer`/`tertiaryContainer`) o
   un `ThemeColorKey` por defecto del color de la flecha.
3. Añadir a `ListItemLeadingContentDataUi` un tipo **`IconInContainer(iconData, containerColor, tint)`**
   que pinte el cuadrado redondeado pastel con el icono dentro (es lo que pide el diseño).
   → tocar `ui-logic/.../component/ListItem.kt` (el `when` de render, líneas 306‑342).

### Fase 3 — Los tres chips de filtro

1. **Componente nuevo reutilizable** en `ui-logic`: `FilterPillRow` (o generalizar `WrapChip` con
   los colores del estado seleccionado). Modelo:
   - No seleccionado: fondo `surfaceContainerLowest` (blanco), borde `outlineVariant`/blanco.
   - Seleccionado: fondo `primary`, texto `onPrimary`, sin borde.
   - Forma: `RoundedCornerShape(percent = 50)` (píldora real, no los 8 dp de `WrapChip`).
2. `TransactionsViewModel.State`: `directionFilter: ConnectionDirectionFilter { ALL, SHARED, RECEIVED }`
   + `Event.OnDirectionFilterSelected(...)`.
3. `TransactionsInteractor`: aplica el filtro sobre la lista unificada.
4. Colocación: justo debajo de `FiltersSearchBar` en `TransactionsScreen.Content()` (línea 260,
   donde hoy hay un `VSpacer.Large()`), con scroll horizontal si no caben.

### Fase 4 — La fila

1. `leadingContentData = ListItemLeadingContentDataUi.IconInContainer(...)`:
   - Compartido → caja `secondaryContainer` + flecha `secondary`.
   - Recibido → caja `tertiaryContainer` + flecha `tertiary`.
2. `mainContentData` = nombre de la contraparte (**ya es el caso**).
3. `supportingContentData` = `"Compartiste 7 datos"` / `"Recibiste 4 datos"` (o el nombre de la
   credencial recibida).
4. `trailingContentData`: la **hora** (`HH:mm`, 24 h) y, si no es de hoy, la **fecha**.
   Se sustituye el actual "tipo de transacción + flecha" (el tipo ya se deduce del icono).
5. `overlineText`: se mantiene el estado, pero en el diseño no aparece → valorar quitarlo de la
   fila (el detalle ya lo muestra).

### Fase 5 — Agrupación por día

- Nuevo `TransactionCategoryUi.Day(date)` con `displayName` = `"19 de octubre"` (patrón nuevo
  `"dd MMMM"` + `monthYearFormatter` cuando el año no sea el actual).
- Reemplazar `Today / ThisWeek / Month` en `getTransactionCategory()`
  (`TransactionsInteractor.kt:304‑311`).
- Si se quiere mantener "Hoy" como caso especial, `Day` con `stringResId` de "Hoy" cuando
  `isToday()`.

### Fase 6 — El detalle

- Cabecera con el mismo icono pastel + nombre + "Compartiste N datos" + fecha y hora larga
  ("Lunes, 19 de octubre de 2026, 10:16").
- Para *recibido*: mostrar la credencial recibida (nombre, emisor, fecha de emisión) en lugar de
  los claims compartidos.
- `TransactionDetailsUi` gana `direction` y `dataCount`; `TransactionDetailsInteractor` los
  rellena desde el modelo unificado.
- Los botones "Solicitar eliminación de datos" / "Denunciar transacción" (hoy deshabilitados)
  se pueden activar solo para *compartido*.

### Fase 7 — Verificación

1. Generar una **presentación real** contra el verifier local (`avance-verifier-api`, en pie) para
   tener una fila de "Compartido" con claims de verdad.
2. Las emisiones ya existen (4 credenciales emitidas hoy) → filas de "Recibido" con datos reales.
3. Comprobar: los tres filtros, la agrupación por día, la navegación al detalle y el retorno.

---

## 6. Estado de implementación

| Fase | Qué se hizo | Dónde |
|---|---|---|
| 0 | `TransactionLogDataDomain.IssuanceLog` enriquecido con `issuerName` y `attributesCount`; `IssuedDocument.toIssuanceLogData()`; `WalletCoreDocumentsController.getReceivedCredentials()` y `getTransactionLog(id)` resuelve también las emisiones | `core-logic` |
| 1 | `ConnectionDirection` (SHARED/RECEIVED) y `ConnectionDirectionFilter` (ALL/SHARED/RECEIVED); `TransactionUi` y `TransactionsFilterableAttributes` llevan la dirección; el interactor mezcla presentaciones y emisiones y filtra por dirección | `dashboard-feature` |
| 2 | `ic_share_out.xml` / `ic_share_in.xml` (flecha ↗ / ↙), registrados en `AppIconKey` + `AppIcons` + `content_description_*`; `ThemeColorKey.SecondaryContainer` / `TertiaryContainer`; nuevo leading `ListItemLeadingContentDataUi.IconInContainer` (recuadro pastel + icono centrado) | `resources-logic`, `ui-logic` |
| 3 | `WrapFilterPill` (píldora con el seleccionado en `primary` y los demás en blanco con borde); fila de los tres filtros bajo el buscador; `Event.OnDirectionFilterSelected` → `interactor.setDirectionFilter()` | `ui-logic`, `dashboard-feature` |
| 4 | Fila: icono pastel con la flecha, nombre de la contraparte, "Compartiste/ Recibiste N datos" y la hora a la derecha (`ListItemTrailingContentDataUi.Text`, nuevo) | `dashboard-feature`, `ui-logic` |
| 5 | `TransactionCategoryUi.Day` con la fecha ("19 de octubre"); patrón `dd MMMM` nuevo (`dayMonthFormatter`, `LocalDateTime.toDayMonthDisplayable()`) | `business-logic`, `dashboard-feature` |
| 6 | La tarjeta del detalle muestra el icono pastel, el contador de datos y, para una credencial recibida, la sección "CREDENCIAL RECIBIDA" | `dashboard-feature` |

### Ajustes pedidos después de la primera versión

| Pedido | Cambio |
|---|---|
| "fondo que separa cada conexión en un blanco más fuerte" | la tarjeta pasa a `surfaceContainerLowest` (blanco puro) sobre el fondo gris de la app |
| "el nombre de la institución en letras negritas más oscuras" | `mainContentTextStyle` con `FontWeight.Bold` y `onSurface` |
| "en el recuadro de color verdesito solo la flecha bien centrada" | los drawables son solo la flecha (sin la caja) y el `IconInContainer` la centra en el recuadro pastel |
| "el mismo mensaje 'Abre cualquier registro…'" | `transactions_screen_footer_hint` al final de la lista |
| "agrandarlo un poco más de ancho" | la pantalla deja de aplicar el padding horizontal global (`start`/`end` a `false`) y la lista usa 8dp de margen |

## 4. Riesgos y preguntas abiertas

1. **"N datos recibidos"**: una emisión no es "recibir datos". ¿Nº de claims de la credencial, o
   el nombre de la credencial?
2. **Duplicados**: la misma contraparte puede aparecer varias veces (el diseño de referencia
   repite "EPS Salud Cívica" y "Banco Cívica"). El historial lo permite (son transacciones
   distintas), pero conviene decidir si se agrupan.
3. **El log de transacciones no guarda emisiones**: si mañana el Core las emite, habría que
   migrar la fuente de "Recibido" y evitar duplicar con los documentos emitidos.
4. **Tocar `ui-logic`**: el nuevo tipo de leading (`IconInContainer`) y los `ThemeColorKey` afectan
   a un módulo compartido → revisar que no rompa otras pantallas.
5. El `verifier` local sirve para probar, pero el historial real se generará contra el verifier
   de la Registraduría cuando esté desplegado.

## 5. Dependencias de UI reutilizadas (sin crear de cero)

| Necesidad | Se reutiliza |
|---|---|
| Chip píldora | `WrapChip` (con `shape`/`colors`/`border` explícitos) o el molde `CredentialViewModeToggle` |
| Fila agrupada | `WrapListItem` + `WrapListItemDefaults.groupedShape(index, count)` (ya en uso) |
| Cabecera de sección | `SectionTitle` (ya en uso) |
| Buscador | `FiltersSearchBar` (ya en uso) |
| Chip de estado | `WrapChip` con `containerColor = success/error` (el del detalle) |
| Icono verificado | `AppIcons.Certified` (el del detalle) |
| Lista de claims | `ExpandableListItemUi.NestedListItem` + `WrapExpandableListItem` (el del detalle) |
| Formato fecha larga | `FULL_DATETIME_PATTERN_24H_SEPARATED_BY_DASH` |
| Colores pastel | `secondaryContainer`, `secondary`, `tertiaryContainer`, `tertiary` (ya en el tema) |
