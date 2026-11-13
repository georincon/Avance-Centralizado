# Notaría de ejemplo

**Notaría Primera de Cívica** es una notaría ficticia que hace trámites
notariales en línea con la wallet. Muestra las dos caras del ecosistema en un
mismo servicio:

- **Verifier.** Antes de cualquier trámite, la persona se identifica
  presentando su PID desde la wallet, con su propio verifier, `verifier-notaria`.
  También lo usa para comprobar documentos notariales que alguien le presenta.
- **Emisor.** El documento del trámite se entrega como un documento de la
  wallet (SD-JWT VC por OpenID4VCI), firmado por la notaría, registrado en la
  lista **PUBEAA** y anulable con su status list.

```bash
./levantar.sh              # → https://civica.avance.org.co/notaria/
pruebas/probar-notaria.sh  # prueba completa sin teléfono
```

## Qué trámites hace

Están inspirados en lo que hace una notaría en Colombia:

| Trámite | Qué es | Datos del documento |
|---|---|---|
| **Declaración extraproceso** | Declarar bajo juramento un hecho (convivencia, dependencia económica…) para presentarlo ante una entidad (Decreto 1557 de 1989) | declarante, documento, con destino a, declaración |
| **Poder especial** | Autorizar a otra persona para un asunto concreto. Se puede **revocar** | poderdante, apoderado (nombre y documento), facultades, vigente hasta |
| **Reconocimiento de documento** | Reconocer ante notario la firma y el contenido de un documento privado | firmante, nombre del archivo, **huella SHA-256**, tamaño |

Todos llevan además número (consecutivo del protocolo), fecha, notaría y
notario. Los datos de la persona no los escribe nadie: salen del PID verificado.

En el reconocimiento, el archivo **no sale del navegador**: allí se calcula su
SHA-256 y solo la huella queda en el documento notarial. Al verificarlo, quien
tenga el archivo puede comprobar en la misma página que es exactamente ese.

Para otorgar un acto hay que ser mayor de edad: se comprueba con la fecha de
nacimiento del PID.

## Cómo se usa

1. **Elegir el trámite** en https://civica.avance.org.co/notaria/ e
   **identificarse**. La notaría muestra un QR, o un botón en el teléfono, y la
   wallet le presenta el PID: documento, nombres, apellidos, nacimiento y foto.
2. **Llenar el trámite y firmarlo.** Queda en el protocolo con un número.
3. **Recibirlo en la wallet:** la notaría muestra un QR con la oferta y un
   **PIN** de 4 dígitos. La wallet escanea, pide el PIN y guarda el documento.
4. **Mis trámites:** volver a recibir un documento (la oferta dura 15 min y
   sirve una vez) o **anularlo**. En un poder es revocarlo.
5. **Verificar un documento notarial:** cualquiera elige el tipo, la notaría
   se lo pide a la wallet de quien lo muestra, y ve si es auténtico y sigue
   vigente. Un poder revocado no pasa.

> La primera vez, en la app: **Ajustes › Listas de confianza › Actualizar**.
> La app guarda las listas un día. Sin actualizarlas, todavía no ve a la notaría
> en PUBEAA y borra sus documentos al recibirlos.

## Cómo está hecha

```
                    ┌──────────── notaria (Node, red civica, alias "notaria") ────────────┐
navegador ─────────▶│ página y API (servidor.js)                                          │
                    │   identificarse / verificar ──▶ verifier-notaria (verifier.js) ─────┼─▶ trust-validator (PID, PUBEAA)
                    │   trámites ──▶ datos/tramites.json (tramites.js)                    │
wallet ────────────▶│ emisor OpenID4VCI (emisor.js)                                       │
                    │   token: WIA de la wallet (abca.js) ────────────────────────────────┼─▶ trust-validator (WP)
                    │   documento: SD-JWT VC (sdjwt.js), firmado con secretos/ds.key      │
                    │   status list (estado.js): un bit por trámite                       │
                    └─────────────────────────────────────────────────────────────────────┘
```

### El emisor (OpenID4VCI 1.0, código preautorizado)

| Paso | Llamada |
|---|---|
| Oferta (el QR) | `openid-credential-offer://?credential_offer_uri=…/notaria/oferta/{id}`, con `pre-authorized_code` y `tx_code` (PIN de 4 dígitos) |
| Metadata | `/.well-known/openid-credential-issuer/notaria` y `/.well-known/oauth-authorization-server/notaria` (públicas, con CORS) |
| Token | `POST /notaria/token`: código + PIN, **WIA de la wallet** (`OAuth-Client-Attestation` + `-PoP`) y DPoP |
| Nonce | `POST /notaria/nonce` |
| Documento | `POST /notaria/credencial`: `credential_configuration_id` + `proofs.jwt` (clave en `jwk` o en una key attestation) |
| Estado | `GET /notaria/estado/1` (`application/statuslist+jwt`) |

Lo que exige y por qué:

- **Solo wallets certificadas.** El token se niega (401) si la WIA no la firmó
  un wallet-provider de la lista WP, según el trust-validator, o si la PoP no
  prueba la clave de la WIA. Una PoP no se puede repetir. Es lo mismo que la
  extensión ABCA de Keycloak hace para el pid-issuer.
- **PIN.** Se muestra en la página, no va en el QR: quien vea el QR de reojo no
  puede quedarse con el documento. Tres intentos.
- **DPoP.** Si la wallet lo usa, que es lo que anuncia la metadata, el token
  queda ligado a su clave y no sirve robado.
- **El documento queda ligado** a la clave del dispositivo (`cnf.jwk`). Al
  presentarlo, la wallet firma un KB-JWT con ella.

No hay `authorization_code`: los documentos se piden en la página de la
notaría, no desde la wallet. La app lo detecta en «Pedir un documento» y
ofrece abrir la página.

### Confianza

- `scripts/generar-claves.sh` crea en `secretos/` la **CA raíz** de la notaría y
  el **certificado de firma (DS)** que firma los documentos y la status list y
  va en su `x5c`.
- `scripts/registrar.py` registra la raíz en el tl-publisher como **pub-eaa**,
  porque las notarías son particulares con función pública. Su punto de
  suministro es la URL del emisor. Lo hace solo con la clave de operador
  (`.env` o `../tl-publisher/.env`); el id queda en `secretos/actor`.
- Con eso, sin tocar nada más:
  - **la wallet** acepta sus documentos, porque el `x5c` llega a una CA de PUBEAA;
  - **los verifiers** los clasifican como PubEAA (`integracion/verifier.env` lee
    su metadata) y preguntan por el emisor al trust-validator.

### El verifier

Es una instancia normal de `../verifier`, `verifier-notaria`, creada por
`levantar.sh` con el nombre de la notaría y su propósito. Se crea después del
registro, para que ya sepa que los documentos notariales son PubEAA. La
notaría le habla por la red interna (`http://verifier-notaria/…`).

## Archivos

| Archivo | Qué es |
|---|---|
| `src/servidor.js` | Página, sesiones, identificación, trámites, anulación, verificación |
| `src/emisor.js` | Emisor OpenID4VCI: metadata, oferta, token, nonce, credencial, status list |
| `src/abca.js` | Comprobación de la WIA de la wallet (trust-validator, contexto WalletProviderAttestation) |
| `src/sdjwt.js` · `src/estado.js` | SD-JWT VC y Token Status List |
| `src/documentos.js` | Los tres tipos de documento: `vct`, nombre, colores y datos |
| `src/verifier.js` | Cliente del verifier: pedir el PID o un documento notarial y leer el resultado |
| `src/tramites.js` | El protocolo: `datos/tramites.json` |
| `src/publico/` | La página (`index.html`) y el logo, que también usa la wallet |
| `scripts/` | PKI y registro en el tl-publisher |
| `pruebas/probar-notaria.sh` | Prueba completa sin teléfono (ver abajo) |
| `.env` | Dominio, prefijo, nombres, instancia del verifier, URLs de la capa de confianza |

Fuera de git: `.env`, `secretos/` (CA y clave de firma) y `datos/` (protocolo).

## Probarlo sin teléfono

`pruebas/probar-notaria.sh` hace de persona y de wallet:

1. Se identifica: presenta el PID de prueba de María, con `../verifier/scripts/probar-presentacion/probar.mjs --enlace`.
2. Otorga un poder.
3. Lo recibe con OpenID4VCI y comprueba:
   - que sin WIA da 401 y con un PIN equivocado da 400;
   - que el token sale con DPoP;
   - que la firma llega a PUBEAA y el documento está ligado a la clave;
   - que la status list lo marca vigente.
4. Alguien verifica el poder y sale auténtico.
5. Lo revoca. La status list lo marca anulado y la nueva verificación se rechaza
   con «el documento fue anulado».

## Problemas conocidos

| Síntoma | Causa | Arreglo |
|---|---|---|
| Verificar un documento notarial falla justo después del primer registro | El trust-validator relee las listas cada 10 min (con caché, hasta 20) | Esperar, o reiniciar el trust-validator |
| La wallet borra el documento al recibirlo: «no aparece en la lista» | La app guarda las listas un día | Ajustes › Listas de confianza › Actualizar |
| `invalid_client` en el token | WIA de un wallet-provider fuera de la lista WP, o PoP repetida o vencida | Ver `docker compose -p notaria logs` |
| Tras reiniciar, «solicitud desconocida» | Sesiones, ofertas y solicitudes viven en memoria | Volver a empezar; los trámites siguen en el protocolo |

No es una notaría real y sus documentos no tienen validez legal.
