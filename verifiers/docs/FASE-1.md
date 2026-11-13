# Fase 1: PKI y confianza (2026-10-21)

## Hecho

1. **EKU `1.0.18013.5.1.6` en el perfil `rp-access`**, junto a `clientAuth`, crítico.
   - `srv-tl-publisher/pki-servicio/emision.py` (`OID_EKU_READER_AUTH`) y `srv-tl-publisher/pki/perfiles.sh`.
   - Comprobado con una CA temporal desechable que el certificado emitido lleva
     `['1.3.6.1.5.5.7.3.2', '1.0.18013.5.1.6']`. No se emitió ningún certificado real ni se tocó una CA.
   - Imagen `pki` reconstruida y reiniciada. La anterior queda como `srv-tl-publisher-pki:antes-eku`.
   - Los certificados ya emitidos no cambian: el `avance-verifier` actual sigue sin EKU hasta que se reemita.
2. **Endpoint `GET /tl-api/v1/integracion/verifier.env`** (público), nuevo en el tl-publisher.
   - `app/integracion.py` arma `VERIFIER_TRUSTVALIDATOR_SERVICEURL` (por defecto `http://trust-validator:8080/trust`,
     configurable con `TRUST_VALIDATOR_URL`) y `VERIFIER_ATTESTATIONCLASSIFICATIONS`.
   - `admin.py`: la lectura de metadata de un emisor pasó a `leer_emisor()` sin autenticación, y ahora devuelve también
     el `doctype` de los mdoc (antes solo el `vct`).
   - Imagen `tl-publisher` reconstruida y reiniciada. Las listas siguen respondiendo.
3. **Ancla de `avance-verifier`**: su hoja la firma `Avance Verifier Access CA`, que es el actor #19 de RPREG
   (huella `1C:46:50:34…`). Usa su CA propia, no `Civica RP Access CA`.

## Hallazgo pendiente

Las clases salen de la metadata OpenID4VCI de los emisores, leída desde los **puntos de suministro** de cada actor.
Hoy esos puntos son URLs de CRL y OCSP, no la URL del emisor, así que `verifier.env` solo trae la clase PID base
y avisos. Además hay datos mezclados en el registro (actores importados de otro ecosistema):

| Actor | Rol | Punto de suministro actual | Debería apuntar a |
|---|---|---|---|
| #14 PID Registraduria | pid | CRL de `pid-uis-issuer-pid-root-ca` | `https://civica-desarrollo.avance.org.co/pid-registraduria` |
| #13 UIS Issuer (PID) | pid | CRL de `pid-uis-issuer-pid-root-ca` | revisar si la UIS emite PID |
| #16 UIS Issuer | pub-eaa | CRL de `pubeaa-notaria…` | `https://civica-desarrollo.avance.org.co/uis-issuer` |
| #15 EPS Salud Cívica | qeaa | CRL de `qeaa-eps…` | su emisor, cuando exista en este ecosistema |
| #17 Notaría | pub-eaa | CRL de `pubeaa-notaria…` | su emisor, cuando exista |

Cambiar los puntos modifica el contenido de las listas firmadas y obliga a republicar (nueva secuencia).
Pendiente de autorización.

## Actualización: los emisores no pueden estar arriba a la vez

Los Keycloak de la Registraduría y de la UIS comparten dominio y el borde público reescribe el `Path` de las
cookies (`ECOSISTEMA.md` §6), así que solo uno funciona a la vez. Por eso `verifier.env` **recuerda la última
lectura buena** de la metadata de cada emisor (`integracion.py`, archivo `emisores-cache.json` en el almacén del
tl-publisher) y la usa, con un aviso, cuando el emisor está apagado. Cada emisor hay que leerlo una vez estando
arriba (pasos en `corregir-puntos.sh`).

## Resultado de la corrección de puntos (2026-10-21)

- Puntos añadidos: #14 → `/pid-registraduria`, #16 → `/uis-issuer` (se conservaron los existentes).
  Copia del registro previa al cambio: `actores-antes.json`.
- **Bug previo corregido**: `admin.sincronizar()` metía certificado, nombre y SKI en un solo `DigitalId`, y la
  TSL no validaba contra el XSD de TS 119 612 (rompía toda republicación). Ahora es un `DigitalId` por elemento.
- Publicadas: PID 6, PUBEAA 6, QEAA 6, RPREG 6, WP 8. Comparadas con la secuencia anterior: mismas anclas
  (0 perdidas, 0 nuevas) y mismo firmante. `LOTL` sigue en la secuencia 5 (`POST /publicar` no la republica).
- `verificar-cadenas.py` y `probar-ecosistema.py`: todo OK.
- Pendiente: leer la UIS una vez estando arriba para que `verifier.env` clasifique sus credenciales (PubEAA).
