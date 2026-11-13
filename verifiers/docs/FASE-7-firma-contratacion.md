# Fase 7: firma del contrato en Contratación (2026-10-23)

El portal de Contratación (`verifiers/contratacion`) ya verificaba la formación académica y, aparte, la identidad. Ahora cierra el
proceso: con las dos cosas verificadas genera el **contrato de trabajo en PDF** y se lo ofrece a la persona para que lo **firme desde su
wallet** (Inicio → Firmar → Escanear QR). Es el primer "servicio solicitante" de firma del ecosistema.

## Flujo

```
Portal (Contratación)                       Wallet                               srv-andes-scd
 1 formación + identidad verificadas
 2 genera el contrato (PDF) con esos datos
 3 QR: eudi-rqes://?client_id=<dominio>&request_uri=<…/firma/solicitud/<id>>
                                            4 GET request_uri  ◀── solicitud firmada (JAR, ES256, x5c)
                                            5 comprueba x5c contra la raíz Civica,
                                              y que response_uri está en el dominio del client_id
                                            6 GET documento; comprueba la huella SHA-256
                                            7 firma la huella ───────────────────────────────▶ (CSC v2 + OAuth2)
                                            8 incrusta la firma en el PDF (PAdES B-B)
 9 POST /firma/respuesta/<id> ◀──────────── devuelve el PDF firmado (direct_post)
10 comprueba el PDF y lo muestra
```

La solicitud (paso 4) es el formato de `eudi-lib-jvm-rqes-csc-kt` (paquete `documentretrieval`): JWT `ES256` con la cadena del certificado en
`x5c`, `client_id_scheme: x509_san_dns`, `response_mode: direct_post`, `documentDigests` (huella en base64 y nombre de archivo),
`documentLocations` (`method.type: public`) y `hashAlgorithmOID`. El portal la firma con el **certificado de acceso del empleador**, el
mismo de su verifier (`../_comun/instancias/contratacion/secretos/`), cuyo nombre DNS es el dominio del portal.

## Qué comprueba el portal del PDF que devuelve la wallet

1. Que empieza por **exactamente** el contrato que envió (la firma se añade al final, sin tocar el contenido).
2. Que la firma cubre todo el archivo y que el hueco de la firma no está vacío.
3. La firma CMS y su cadena hasta la raíz Civica (`openssl cms -verify`).
4. Quién firmó (nombre y documento del certificado).
5. Si lleva sello de tiempo, que es válido para esa firma. **Hoy no lo lleva** (ver límites): se muestra como «Sin sello».

## Cambios en la wallet

`RQESConfigImpl.documentRetrievalConfig` solo confiaba en certificados de pruebas de la UE. Se añadió la raíz Civica
(`resources-logic/src/main/res/raw/civica_root_ca.crt`). Comprobado con la librería real: sin la raíz responde `Untrusted x5c`; con ella,
acepta la solicitud; con un `response_uri` en otro dominio, la rechaza.

## Límites

- **`signatureQualifier: eu_eidas_qes` es obligatorio** en la solicitud: la librería de la wallet rechaza cualquier otro valor. Es una
  etiqueta del protocolo, no una afirmación. En Colombia no existe la firma cualificada: esto es una firma electrónica de prueba, y así lo
  dice el contrato y el portal.
- **Sin sello de tiempo en lo que recibe el portal.** El SDK de la wallet firma en nivel B-B y envía ese PDF al portal tal cual; el sello lo
  añade la wallet solo a la copia que guarda en `Documents/Signed`. Para que el portal reciba B-T habría que añadirlo en el propio portal.
- La identidad se confirma con la credencial académica (nombre y documento) y la firma con documento + PIN del servicio de firma
  simulado: aún no hay vínculo entre ambos (etapa 2: identificar al firmante con el PID).
- Los resultados viven 15 minutos en memoria, como el resto del portal; no se guarda ningún contrato en disco.
