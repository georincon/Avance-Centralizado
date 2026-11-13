# Guía paso a paso para probar el API de órdenes de emisión

Para quien va a hacer las pruebas de integración (QA). No hace falta saber programar: hace falta seguir los pasos en orden. Tiempo estimado: **30 minutos** las pruebas automáticas y **30 más** si se prueba con la wallet.

## 0. Qué se va a probar (en 1 minuto)

La notaría emite documentos digitales (fe de vida, poder, etc.) que la persona guarda en una **wallet** (una app en el celular). La plataforma de notarías pide esa emisión con **tres llamadas**:

1. **Crear la orden:** «quiero emitir una fe de vida para Ana, con estos datos».
2. **Pedir el QR:** el sistema devuelve un QR (y un PIN) para que Ana lo escanee con su wallet.
3. **Consultar el estado:** ¿ya recibió Ana el documento?

Todo se prueba con **Postman**. Al final se comprueba en la **base de datos** que quedó el registro.

## 1. Qué necesita antes de empezar

| Necesita | Cómo conseguirlo |
|---|---|
| Postman (app de escritorio, gratis) | https://www.postman.com/downloads/ |
| La **clave del API** (`api_key`) | Se la entrega el equipo de la notaría por canal aparte. Sin ella todo responde 401 |
| Los archivos de esta carpeta | `Integracion-Notarias.postman_collection.json` y `Integracion.postman_environment.json` |
| Conexión a internet | Para llegar a `https://civica-desarrollo.avance.org.co` |
| *(Solo para la parte con wallet)* Un celular Android con la wallet «V2» instalada | Pídala al equipo de la notaría |
| *(Solo para ver la base de datos)* DBeaver (gratis) y los datos de conexión | Ver el paso 6 |

## 2. Preparar Postman (una sola vez)

1. Abra Postman → botón **Import** (arriba a la izquierda) → arrastre los dos archivos `.json` → **Import**.
2. Arriba a la derecha, donde dice *No Environment*, despliegue y elija **«Integración notarías · civica-desarrollo»**.
3. Clic en el icono del **ojo** (al lado de ese selector) → **Edit** (en la sección del entorno).
4. En la fila `api_key` escriba la clave en la columna **Current value** → **Save**. *(Si la deja vacía, todas las pruebas darán 401.)*
5. En la fila `referencia` verá `SIGNO-QA-001`. **Es el identificador del trámite**: cámbielo por uno nuevo cada vez que repita toda la serie (por ejemplo `SIGNO-QA-002`, `SIGNO-QA-003`…). **Si no lo cambia, la segunda vuelta fallará** porque la orden de la primera ya quedó cancelada. Guarde con **Save**.

> No toque las filas `base_url`, `orden_id` ni `orden_poder_id`: las llena Postman solo.

## 3. Prueba rápida de que todo conecta

1. En el menú izquierdo abra la colección → carpeta **01 · Conexión y catálogo** → **Catálogo de documentos** → botón **Send**.
2. Debe ver abajo **Status: 200 OK** y una lista de 9 documentos.
3. En la pestaña **Test Results** (junto a *Body*) las pruebas salen en **verde**.

Si ve **401**: la `api_key` está mal escrita o no se guardó (repita el paso 2.4). Si no responde nada: revise su internet o VPN. Más en «Si algo falla» (paso 8).

## 4. Ejecutar todas las pruebas automáticas

1. Clic derecho sobre la colección → **Run collection** (o *Run folder* para una carpeta).
2. Deje todas las carpetas marcadas, en el orden en que están, y clic en **Run**.
3. Al terminar, Postman muestra cada prueba en verde (pasó) o rojo (falló). **Deben pasar las 24.**
4. Tome una **captura** de la pantalla final: es evidencia para el informe.

**El orden importa**: las carpetas se apoyan una en otra (la 03 usa la orden creada en la 02; la 05 cancela la orden de poder creada en la 02). Por eso se ejecutan en orden y en una sola corrida.

### Qué hace cada carpeta

| Carpeta | Qué comprueba | Resultado correcto |
|---|---|---|
| **01 · Conexión y catálogo** | Sin clave rechaza; con clave entrega los 9 documentos | 401 y 200 |
| **02 · (a) Crear la orden** | Crea órdenes; repetir no duplica; rechaza datos malos (faltantes, campos prohibidos, documento inexistente, fecha vencida, sin clave) | 201/200 en las buenas; 409, 400, 422, 401 en las malas |
| **03 · (b) Obtener el QR** | Entrega enlace, QR y PIN; pedirlo de nuevo da lo mismo; ids falsos dan 404 | 200 y 404 |
| **04 · (c) Consultar el estado** | Estado `OFERTA_GENERADA` con su bitácora; orden nueva en `CREADA` | 200, 404, 401 |
| **05 · Cancelar** | Cancela; no entrega oferta de una orden cancelada; no se cancela dos veces | 200, 409, 409, 200 |

> Las pruebas «**debe rechazar**» están hechas para recibir un error: si responden 400, 401, 404, 409 o 422 **es lo correcto**. Una prueba así solo falla si el sistema *acepta* algo que no debería.

## 5. Probar con la wallet (de la orden al documento en el celular)

Esta parte no se puede automatizar: la hace una persona con el celular.

1. **Cree una orden nueva y sin cancelar.** Cambie `referencia` en el entorno (por ejemplo `SIGNO-WALLET-001`) → abra **02 · Crear la orden → Crear una orden (fe de vida)** → **Send**. Debe responder **201** y `estado: CREADA`.
2. **Pida el QR.** Abra **03 → Obtener la oferta** → **Send**. Verá en el *Body*: `enlace`, `qr_png_base64`, `tx_code` y `expira_en`. **Apunte el `tx_code`** (5 números: es el PIN). La oferta dura **60 minutos**.
3. **Muestre el QR.** Dos formas:
   - *En el mismo celular:* copie el valor de `enlace` y ábralo con la wallet (envíelo al celular por un chat y toque el enlace).
   - *Con otro dispositivo:* copie todo el valor de `qr_png_base64` (empieza por `data:image/png;base64,`), péguelo en la barra de direcciones del navegador y presione Enter: aparece el QR en pantalla. Con el celular, abra la wallet y escanee.
4. **En la wallet:** acepte la oferta del documento y, cuando pida el código, escriba el `tx_code`.
5. **Verifique que llegó:** el documento aparece en la wallet con los datos que usted envió.
6. **Consulte el estado.** Abra **04 → Estado de la orden** → **Send**. Debe decir `"estado": "EMITIDA"` y tener fecha en `emitida_en`. *(Ojo: esa prueba de la colección espera `OFERTA_GENERADA`; para esta orden ya emitida verá que «falla» ese chequeo, y es normal: lo correcto es `EMITIDA`.)*
7. **Compruebe el cierre.** Vuelva a **03 → Obtener la oferta**: ahora debe responder **409** `orden_emitida` (ya no se entrega otro QR).

Anote en la plantilla del informe (sección 3) qué pasó en cada paso, con capturas de la pantalla de la wallet. **Si algún paso falla, no es un error suyo:** anótelo con captura y la hora; es un hallazgo del informe.

## 6. Ver la base de datos (comprobar que se registró)

El objetivo es confirmar que cada llamada dejó registro. Se hace con **DBeaver**:

1. **Datos de conexión** (se los entrega el equipo de la notaría): tipo **PostgreSQL**, host `127.0.0.1`, puerto `5442`, base `notaria_issuer`, usuario y clave que le entreguen. *El puerto solo es visible desde el servidor: normalmente se entra con un túnel SSH que el equipo le configura, o el equipo ejecuta las consultas con usted.*
2. En DBeaver: **Base de datos → Nueva conexión → PostgreSQL**, escriba los datos y **Probar conexión**.
3. Abra un editor SQL (`Ctrl + ]`) y ejecute:

```sql
-- Las órdenes creadas desde Postman, la más reciente primero
SELECT referencia, documento, estado, creada_en, oferta_generada_en, emitida_en
FROM notaria_orden_emision
ORDER BY creada_en DESC;
```

```sql
-- La bitácora de una orden (cambie el id por el que le devolvió Postman)
SELECT evento, detalle, creado_en
FROM notaria_orden_evento
WHERE orden_id = 'PEGUE-AQUI-EL-ID'
ORDER BY id;
```

### Qué debe ver después de cada llamada

| Lo que hizo en Postman | Lo que debe ver en la base |
|---|---|
| Crear la orden (201) | Una fila nueva, `estado = CREADA`, con su `referencia`; evento `CREADA` |
| Repetir la misma creación | **Ninguna fila nueva** (una sola con esa referencia) |
| Pedir el QR | `estado = OFERTA_GENERADA`; `tx_code`, `session_id` y `oferta_uri` con valor; evento `OFERTA_GENERADA` |
| Cancelar | `estado = CANCELADA`; evento `CANCELADA` |
| Una petición rechazada (400/401/409/422) | **No se crea ninguna fila** |
| La wallet recibe el documento | `estado = EMITIDA`; `emitida_en` con fecha; evento `EMITIDA` |

Tome una captura de cada consulta: son la evidencia del informe. Las tablas y columnas están explicadas en `MODELO-ENTIDAD-RELACION.md`.

## 7. Probar a mano una llamada (opcional)

Para ensayar otros datos: en la carpeta **02** duplique **Crear una orden (fe de vida)** (clic derecho → *Duplicate*), cambie el cuerpo (*Body*) y la `referencia`, y **Send**. Los campos de cada documento están en `API-ORDENES-EMISION.md` §5. Reglas rápidas:

- Las fechas van como `2027-12-31` (año-mes-día).
- No envíe campos que no estén en la lista del documento, ni los que pone la notaría (`numero`, `fecha`, `notario`…).
- Cada `referencia` identifica un trámite: repetirla con otros datos da 409.

## 8. Si algo falla

| Qué ve | Qué significa | Qué hacer |
|---|---|---|
| **401** en todo | Falta la clave o está mal | Paso 2.4: revise `api_key` y que el entorno correcto esté seleccionado |
| **Could not send request / ENOTFOUND / timeout** | No llega al servidor | Revise internet/VPN; abra `https://civica-desarrollo.avance.org.co/notaria-issuer/` en el navegador |
| Una prueba de la carpeta 03/04/05 sale **404** | `orden_id` vacío: la carpeta 02 no se ejecutó antes, o su primera prueba falló | Ejecute otra vez desde la carpeta 02 (con una `referencia` nueva) |
| «Estado de una orden sin oferta» o «Crear… poder» fallan en la segunda vuelta | La `referencia` ya se usó en la vuelta anterior | Cambie `referencia` (paso 2.5) y repita todo |
| **409** al crear | La `referencia` ya existe con otros datos | Use una referencia nueva |
| **410** al pedir la oferta | La orden venció (24 h) | Cree una orden nueva |
| `OFERTA_EXPIRADA` | El QR venció (60 min) o el sistema se reinició | Pida la oferta otra vez: se genera una nueva |
| **500** | Error del sistema | Anote la hora exacta, el cuerpo enviado y la respuesta, y avise al equipo de la notaría |
| La wallet no acepta el QR o el PIN | Puede ser un fallo real (esta parte aún no se ha probado con wallet) | Anótelo con captura y hora en el informe |

## 9. Glosario

| Palabra | Qué es |
|---|---|
| **Wallet** | App del celular donde la persona guarda sus documentos digitales |
| **Emitir** | Entregar el documento digital a la wallet |
| **Orden de emisión** | El pedido «emite este documento con estos datos» |
| **Oferta / QR / enlace** | La invitación que la wallet usa para recibir el documento |
| **`tx_code` (PIN)** | Código de 5 números que la wallet pide para aceptar la oferta; la plataforma lo entrega a la persona por otro medio |
| **`referencia`** | El número del trámite en la plataforma de notarías; evita duplicados |
| **Idempotencia** | Repetir la misma petición no crea nada nuevo |
| **API key** | La «contraseña» de la plataforma ante el API (cabecera `X-API-Key`) |
| **Estado** | En qué va la orden: `CREADA`, `OFERTA_GENERADA`, `OFERTA_EXPIRADA`, `EMITIDA`, `CANCELADA`, `EXPIRADA` |

## 10. Al terminar

Llene `PLANTILLA-INFORME-PRUEBAS.md` (resultados, capturas, hallazgos y conclusión): es la base del informe para la PMO y la dirección de tecnología.
