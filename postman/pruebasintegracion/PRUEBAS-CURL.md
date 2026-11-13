# Integración con notarías · pruebas con curl

Las mismas pruebas de la colección, para la terminal. Hace falta `jq` para leer las respuestas.

```bash
export BASE_URL=https://civica-desarrollo.avance.org.co
export API_KEY=<la clave que le entregaron>
export REFERENCIA=SIGNO-QA-001   # cámbiala para crear una orden nueva
```

## 01 · Conexión y catálogo

Antes de crear órdenes: comprobar que la clave (`X-API-Key`) funciona y qué documentos se pueden emitir, con los campos que la plataforma debe enviar de cada uno.

### Sin clave: debe rechazar

Toda la API exige la cabecera `X-API-Key`. Sin ella responde **401** y no revela nada.

```bash
curl -sk -w '\nHTTP %{http_code}\n' "$BASE_URL/notaria-issuer/api/v1/documentos"
```

### Catálogo de documentos

Los nueve documentos notariales que emite la notaría. De cada uno: `documento` (el identificador que se usa al crear la orden), `vct`, `nombre`, `campos_requeridos` y `campos_opcionales` (solo los que escribe la plataforma; los demás —número de protocolo, fecha, notario, etc.— los pone la notaría).

```bash
curl -sk -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/documentos" | jq .
```

## 02 · (a) Crear la orden de emisión

La plataforma de notarías envía el documento que quiere emitir y sus datos. La orden queda guardada (vive 24 h) y todavía no hay QR: ese paso es el siguiente. `referencia` es el identificador del trámite **en la plataforma**: sirve para no duplicar (repetir la misma petición devuelve la misma orden).

### Crear una orden (fe de vida)

Crea la orden. **201** la primera vez; **200** si ya existía una orden con esa `referencia` y los mismos datos. Guarda el `id` en la variable `orden_id`. Cambia `referencia` en el entorno si quieres crear una orden nueva.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.fe_de_vida", "referencia": "$REFERENCIA", "datos": {"titular_nombre": "Ana María Pérez Gómez", "titular_documento": "1020304050", "medio_identificacion": "Cédula de ciudadanía", "entidad_destino": "Colpensiones", "vigente_hasta": "2027-12-31"}}'
```

### Repetir la misma petición (idempotencia)

Misma `referencia` y mismos datos: **200** y la misma orden, sin duplicarla. Así la plataforma puede reintentar sin riesgo si perdió la respuesta.

```bash
curl -sk -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.fe_de_vida", "referencia": "$REFERENCIA", "datos": {"titular_nombre": "Ana María Pérez Gómez", "titular_documento": "1020304050", "medio_identificacion": "Cédula de ciudadanía", "entidad_destino": "Colpensiones", "vigente_hasta": "2027-12-31"}}' | jq .
```

### Misma referencia con otros datos: debe rechazar

Una `referencia` ya usada con datos distintos es un conflicto (**409**): evita pisar un trámite por error.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.fe_de_vida", "referencia": "$REFERENCIA", "datos": {"titular_nombre": "Ana María Pérez Gómez", "titular_documento": "1020304050", "medio_identificacion": "Cédula de ciudadanía", "entidad_destino": "Otra entidad", "vigente_hasta": "2027-12-31"}}'
```

### Crear una orden (poder especial)

Otro documento, con sus propios campos (ver el catálogo). Guarda el `id` en `orden_poder_id`; se usa más abajo para probar la cancelación.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.poder_especial", "referencia": "$REFERENCIA-poder", "datos": {"poderdante_nombre": "Carlos Andrés Rojas", "poderdante_documento": "79123456", "apoderado_nombre": "Laura Torres Díaz", "apoderado_documento": "52987654", "facultades": "Representarlo en la venta del vehículo de placas ABC123.", "vigente_hasta": "2027-12-31", "asunto": "Venta de vehículo"}}'
```

### Faltan campos obligatorios: debe rechazar

**400** con la lista de campos que faltan en `error.campos`.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.fe_de_vida", "referencia": "$REFERENCIA-x1", "datos": {"titular_nombre": "Ana"}}'
```

### Campo que pone la notaría: debe rechazar

El número de protocolo, la fecha, el notario… los pone la notaría al emitir. Enviarlos es un error (**400**), no se ignoran en silencio.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.fe_de_vida", "referencia": "$REFERENCIA-x2", "datos": {"titular_nombre": "Ana María Pérez Gómez", "titular_documento": "1020304050", "medio_identificacion": "Cédula de ciudadanía", "entidad_destino": "Colpensiones", "vigente_hasta": "2027-12-31", "numero": "2026-99999"}}'
```

### Documento inexistente: debe rechazar

**400** y la lista de documentos válidos en `error.validos`.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.no_existe", "referencia": "$REFERENCIA-x3", "datos": {"titular_nombre": "Ana María Pérez Gómez", "titular_documento": "1020304050", "medio_identificacion": "Cédula de ciudadanía", "entidad_destino": "Colpensiones", "vigente_hasta": "2027-12-31"}}'
```

### Fecha ya vencida: debe rechazar

La vigencia de una fe de vida no puede estar en el pasado: **422**. Las fechas van como `AAAA-MM-DD`.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.fe_de_vida", "referencia": "$REFERENCIA-x4", "datos": {"titular_nombre": "Ana María Pérez Gómez", "titular_documento": "1020304050", "medio_identificacion": "Cédula de ciudadanía", "entidad_destino": "Colpensiones", "vigente_hasta": "2020-01-01"}}'
```

### Sin clave: debe rechazar

Sin `X-API-Key` no se crea nada (**401**).

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes" \
  -d '{"documento": "co.notaria.fe_de_vida", "referencia": "$REFERENCIA-x5", "datos": {"titular_nombre": "Ana María Pérez Gómez", "titular_documento": "1020304050", "medio_identificacion": "Cédula de ciudadanía", "entidad_destino": "Colpensiones", "vigente_hasta": "2027-12-31"}}'
```

## 03 · (b) Obtener el QR / enlace de oferta

Con la orden creada, la plataforma pide la oferta de emisión. Devuelve el `enlace` (para abrirlo con la wallet en el mismo teléfono), el `qr_png_base64` (para mostrarlo y que otro dispositivo lo escanee) y el `tx_code`: un PIN de 5 dígitos que **se entrega a la persona por otro medio** (la wallet lo pide al recibir el documento). La oferta vive 60 minutos; si vence, esta misma llamada genera una nueva mientras la orden siga vigente.

### Obtener la oferta

Pasa la orden a `OFERTA_GENERADA`. **Con la wallet:** abre el `enlace` o escanea el QR (guárdalo como PNG: el texto después de `base64,`), y escribe el `tx_code` cuando lo pida.

```bash
curl -sk -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_ID/oferta" | jq .
```

### Pedirla otra vez (misma oferta)

Mientras la oferta no venza, devuelve la misma (`regenerada: false`): se puede consultar las veces que haga falta.

```bash
curl -sk -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_ID/oferta" | jq .
```

### Orden inexistente

**404**: el id no existe (o es de otro cliente).

```bash
curl -sk -w '\nHTTP %{http_code}\n' -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/00000000-0000-4000-8000-000000000000/oferta"
```

### Id mal formado

También **404**, sin error interno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/no-es-un-uuid/oferta"
```

### Sin clave: debe rechazar

**401**.

```bash
curl -sk -w '\nHTTP %{http_code}\n' "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_ID/oferta"
```

## 04 · (c) Consultar el estado del proceso

La plataforma pregunta cuándo quiera en qué va la orden. `estado` es uno de: `CREADA` (sin oferta aún) · `OFERTA_GENERADA` (esperando a la wallet) · `OFERTA_EXPIRADA` (el QR venció; pedir la oferta otra vez) · `EMITIDA` (la wallet ya recibió el documento) · `CANCELADA` · `EXPIRADA` (la orden venció a las 24 h). `eventos` es la bitácora de la orden.

### Estado de la orden

Estado actual, fechas y la bitácora (`eventos`). Tras pedir la oferta debe verse `OFERTA_GENERADA`. **Con la wallet:** después de recibir el documento pasa a `EMITIDA` y aparece `emitida_en`.

```bash
curl -sk -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_ID" | jq .
```

### Estado de una orden sin oferta

Una orden recién creada está en `CREADA` y `oferta` es `null`.

```bash
curl -sk -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_PODER_ID" | jq .
```

### Orden inexistente

**404**.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/00000000-0000-4000-8000-000000000000"
```

### Sin clave: debe rechazar

**401**.

```bash
curl -sk -w '\nHTTP %{http_code}\n' "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_ID"
```

## 05 · Cancelar una orden

Si el trámite se anula en la plataforma antes de que la persona reciba el documento, la orden se cancela: la oferta deja de servir.

### Cancelar la orden

Pasa a `CANCELADA`. El `motivo` es opcional.

```bash
curl -sk -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_PODER_ID/cancelar" \
  -d '{"motivo": "Trámite anulado en SIGNO"}' | jq .
```

### Pedir la oferta de una orden cancelada: debe rechazar

**409**: no se entrega oferta de una orden cancelada.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_PODER_ID/oferta"
```

### Cancelar otra vez: debe rechazar

**409**: ya está en un estado final.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_PODER_ID/cancelar"
```

### Estado final

La bitácora termina en `CANCELADA`.

```bash
curl -sk -H "X-API-Key: $API_KEY" "$BASE_URL/notaria-issuer/api/v1/ordenes/$ORDEN_PODER_ID" | jq .
```
