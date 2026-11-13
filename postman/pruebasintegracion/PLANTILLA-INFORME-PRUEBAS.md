# Plantilla del informe de pruebas de integración

Copie este archivo, llene los espacios `___` y marque con **X** el resultado de cada prueba. Sirve de base para el informe a la PMO y a la dirección de tecnología.

## 1. Datos de la prueba

| | |
|---|---|
| Fecha | ___ |
| Responsable | ___ |
| Entorno / URL base | `https://civica-desarrollo.avance.org.co/notaria-issuer/api/v1` |
| Herramienta | Postman versión ___ |
| Referencia usada (variable `referencia`) | ___ |
| Versión de la colección | `Integracion-Notarias` (24 pruebas) |

## 2. Resultado de las pruebas de la colección

«Esperado» es el código HTTP que debe responder. Si el código es el esperado y todas las comprobaciones de la pestaña *Test Results* salen en verde, la prueba está **OK**.

| N.º | Carpeta | Prueba | Método y ruta | HTTP esperado | OK | Falla | Evidencia (captura) / observación |
|---|---|---|---|---|---|---|---|
| 1 | 01 | Sin clave: debe rechazar | `GET /documentos` | 401 | ☐ | ☐ | |
| 2 | 01 | Catálogo de documentos | `GET /documentos` | 200 | ☐ | ☐ | |
| 3 | 02 | Crear una orden (fe de vida) | `POST /ordenes` | 200 o 201 | ☐ | ☐ | |
| 4 | 02 | Repetir la misma petición (idempotencia) | `POST /ordenes` | 200 | ☐ | ☐ | |
| 5 | 02 | Misma referencia con otros datos: debe rechazar | `POST /ordenes` | 409 | ☐ | ☐ | |
| 6 | 02 | Crear una orden (poder especial) | `POST /ordenes` | 200 o 201 | ☐ | ☐ | |
| 7 | 02 | Faltan campos obligatorios: debe rechazar | `POST /ordenes` | 400 | ☐ | ☐ | |
| 8 | 02 | Campo que pone la notaría: debe rechazar | `POST /ordenes` | 400 | ☐ | ☐ | |
| 9 | 02 | Documento inexistente: debe rechazar | `POST /ordenes` | 400 | ☐ | ☐ | |
| 10 | 02 | Fecha ya vencida: debe rechazar | `POST /ordenes` | 422 | ☐ | ☐ | |
| 11 | 02 | Sin clave: debe rechazar | `POST /ordenes` | 401 | ☐ | ☐ | |
| 12 | 03 | Obtener la oferta | `GET /ordenes/{{orden_id}}/oferta` | 200 | ☐ | ☐ | |
| 13 | 03 | Pedirla otra vez (misma oferta) | `GET /ordenes/{{orden_id}}/oferta` | 200 | ☐ | ☐ | |
| 14 | 03 | Orden inexistente | `GET /ordenes/00000000-0000-4000-8000-000000000000/oferta` | 404 | ☐ | ☐ | |
| 15 | 03 | Id mal formado | `GET /ordenes/no-es-un-uuid/oferta` | 404 | ☐ | ☐ | |
| 16 | 03 | Sin clave: debe rechazar | `GET /ordenes/{{orden_id}}/oferta` | 401 | ☐ | ☐ | |
| 17 | 04 | Estado de la orden | `GET /ordenes/{{orden_id}}` | 200 | ☐ | ☐ | |
| 18 | 04 | Estado de una orden sin oferta | `GET /ordenes/{{orden_poder_id}}` | 200 | ☐ | ☐ | |
| 19 | 04 | Orden inexistente | `GET /ordenes/00000000-0000-4000-8000-000000000000` | 404 | ☐ | ☐ | |
| 20 | 04 | Sin clave: debe rechazar | `GET /ordenes/{{orden_id}}` | 401 | ☐ | ☐ | |
| 21 | 05 | Cancelar la orden | `POST /ordenes/{{orden_poder_id}}/cancelar` | 200 | ☐ | ☐ | |
| 22 | 05 | Pedir la oferta de una orden cancelada: debe rechazar | `GET /ordenes/{{orden_poder_id}}/oferta` | 409 | ☐ | ☐ | |
| 23 | 05 | Cancelar otra vez: debe rechazar | `POST /ordenes/{{orden_poder_id}}/cancelar` | 409 | ☐ | ☐ | |
| 24 | 05 | Estado final | `GET /ordenes/{{orden_poder_id}}` | 200 | ☐ | ☐ | |

**Resumen:** pruebas OK ___ de 24 · con falla ___

## 3. Pruebas con la wallet (manuales)

| N.º | Prueba | Resultado esperado | OK | Falla | Observación |
|---|---|---|---|---|---|
| W1 | Mostrar el QR de la oferta (campo `qr_png_base64`) | Se ve un QR legible | ☐ | ☐ | |
| W2 | Escanear el QR / abrir el `enlace` con la wallet | La wallet muestra la oferta del documento | ☐ | ☐ | |
| W3 | Escribir el `tx_code` (PIN) cuando la wallet lo pide | La wallet acepta el PIN | ☐ | ☐ | |
| W4 | El documento aparece en la wallet | Se ve el documento con los datos enviados en `datos` | ☐ | ☐ | |
| W5 | `GET /ordenes/{id}` después de recibirlo | `estado` = `EMITIDA` y `emitida_en` con fecha | ☐ | ☐ | |
| W6 | `GET /ordenes/{id}/oferta` de la orden ya emitida | HTTP 409 `orden_emitida` | ☐ | ☐ | |

## 4. Verificación en la base de datos

| Acción en Postman | Qué debe verse en la base | OK | Falla | Observación |
|---|---|---|---|---|
| `POST /ordenes` | Una fila nueva en `notaria_orden_emision` con `estado` = `CREADA` y la `referencia` enviada; un evento `CREADA` en `notaria_orden_evento` | ☐ | ☐ | |
| Repetir el `POST` igual | **No** aparece una fila nueva (sigue habiendo una sola con esa referencia) | ☐ | ☐ | |
| `GET /ordenes/{id}/oferta` | `estado` = `OFERTA_GENERADA`; `session_id`, `tx_code` y `oferta_uri` con valor; evento `OFERTA_GENERADA` | ☐ | ☐ | |
| `POST /ordenes/{id}/cancelar` | `estado` = `CANCELADA`; evento `CANCELADA` con el motivo | ☐ | ☐ | |
| Peticiones rechazadas (400, 401, 409, 422) | **No** se crea ninguna fila | ☐ | ☐ | |
| Orden recibida en la wallet | `estado` = `EMITIDA`; `emitida_en` con fecha; evento `EMITIDA` | ☐ | ☐ | |

## 5. Hallazgos

| N.º | Descripción | Prueba relacionada | Gravedad (alta / media / baja) | Evidencia |
|---|---|---|---|---|
| 1 | | | | |

## 6. Conclusión

- ¿El API cumple lo acordado (crear orden, obtener QR/enlace, consultar estado)? ☐ Sí ☐ Parcialmente ☐ No
- ¿Los registros en base de datos son coherentes con lo consumido? ☐ Sí ☐ No
- Observaciones y recomendaciones: ___

| Elaboró | Revisó | Fecha |
|---|---|---|
| ___ | ___ | ___ |
