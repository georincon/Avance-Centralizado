# Contrato de eventos de emisores y verificadores

Cada emisor y cada verificador del ecosistema **reporta** al centro de auditoría lo que hace. El centro lo muestra en
los menús **Actividad Emisores** y **Actividad Verificadores** de `/srv-auditoria/`.

> Es **solo monitoreo**. Reportar no debe cambiar ni retrasar nada del servicio: el envío es asíncrono, con tiempo
> máximo corto, y **cualquier error al reportar se ignora**. Si el centro está caído, el servicio sigue igual.

## Dónde se envía

`POST http://srv-auditoria:8111/api/v1/servicios/eventos` (un evento) o `.../eventos/lote` (`{"eventos":[...]}`, máx. 200).

- Es la red interna `avance-eudi` (alias `srv-auditoria`). **No** pasa por el proxy público: desde Internet no se alcanza.
- Cabecera opcional `X-Api-Key: <AUDITORIA_CLAVE_SERVICIOS>`; solo se exige si el centro tiene esa variable definida.
- Respuesta: `202 {"duplicado":false,"actividad_servicio_id":N}`; `400 {"error":"…"}` si el evento no cumple el contrato.
  Reenviar el mismo `evento_id` es seguro (idempotente: responde `duplicado:true`).

## El evento

```json
{
  "evento_id": "uuid-v4 (obligatorio; uno por cosa que ocurre)",
  "servicio": {
    "rol": "emisor | verificador",
    "identificador": "URL pública del servicio (credential_issuer o la del portal verificador)",
    "nombre": "Banco Cívica",
    "proyecto": "verifiers/banco"
  },
  "evento": {
    "tipo": "ver tabla",
    "resultado": "exitoso | fallido | cancelado | en_proceso",
    "ocurrido_en": "2026-11-12T20:09:13Z (ISO-8601)",
    "referencia": "id que une los pasos de un mismo trámite (id de la oferta o de la transacción)",
    "credencial_tipo": "vct / docType / configuración",
    "credencial_nombre": "nombre legible (opcional)",
    "sujeto": { "numero_documento": "cédula si se conoce", "nombre": "opcional" },
    "wallet_id": "uuid, solo si el servicio lo sabe (casi nunca)",
    "canal": "qr | mismo_dispositivo | enlace | api",
    "duracion_ms": 420,
    "tipo_error": "red | autenticacion | validacion | confianza | revocacion | emisor | verificador | usuario | interno",
    "codigo_error": "invalid_proof, invalid_grant, FailedToRetrieve…",
    "mensaje_error": "texto breve y entendible",
    "elementos": [ { "sentido": "solicitado | recibido | emitido", "tipo_credencial": "…", "elemento": "family_name" } ],
    "detalle": { "…": "datos adicionales sin información personal" }
  }
}
```

`tipo_error`, `codigo_error` y `mensaje_error` solo se guardan si `resultado` no es `exitoso`.

## Tipos de evento (`evento.tipo`)

| Rol | Tipo | Cuándo se reporta |
|---|---|---|
| emisor | `oferta_credencial` | El emisor preparó una oferta (QR/enlace) para una wallet. `en_proceso`. |
| emisor | `autorizacion_emision` | La wallet canjeó la oferta (token): código, PIN, atestación de la wallet, DPoP. `exitoso`/`fallido`. |
| emisor | `emision_credencial` | El emisor firmó y entregó la credencial (o la rechazó). `exitoso`/`fallido`. |
| emisor | `revocacion_credencial` | El emisor anuló o reemplazó una credencial emitida. |
| verificador | `solicitud_verificacion` | El verificador abrió una transacción pidiendo datos. `en_proceso` (o `fallido` si no pudo crearla). |
| verificador | `verificacion_credencial` | El resultado: se validó lo presentado (`exitoso`) o se rechazó (`fallido`, con el motivo). |

Un tipo desconocido se rechaza con 400.

## Reglas

1. **Nunca** enviar valores de datos personales en `elementos` ni en `detalle`: solo **nombres** de atributos
   (`family_name`, no «Pérez»). La cédula y el nombre del sujeto van solo en `sujeto`, y solo si el servicio ya los conocía.
2. **Nunca** enviar tokens, códigos de oferta, PIN, claves ni contenido de credenciales.
3. Un solo evento por hecho. Los reintentos reutilizan el mismo `evento_id`.
4. El reporte **no espera** (no `await` en la ruta del usuario), tiene tiempo máximo (≈2 s) y no lanza excepciones hacia el flujo.
5. Si `AUDITORIA_ACTIVA=false`, el servicio no reporta nada.
