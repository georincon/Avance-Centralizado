# srv-andes-scd — simulador del prestador de firma remota

Simula, **para pruebas del ecosistema**, a un prestador de servicios de certificación como Andes SCD. No es Andes SCD ni sus
certificados tienen respaldo ONAC: toda pantalla lo dice. Etiquetado de firma: **firma electrónica de prueba**.

Qué hace (etapa 1, verificada con `pruebas/probar_andes_scd.py`):

| Pieza | Qué es |
|---|---|
| **API CSC v2** (`/csc/v2/…`) | `info`, `credentials/list`, `credentials/info`, `signatures/signHash`, `signatures/timestamp`. Son los métodos que usa la librería de la wallet (`eudi-lib-jvm-rqes-csc-kt` 0.8.0). |
| **Servidor OAuth2** | Código + PKCE (S256), PAR (RFC 9126) y RAR (`authorization_details`, tipo `https://cloudsignatureconsortium.org/2025/credential`). Metadata RFC 8414 en `/.well-known/oauth-authorization-server`. |
| **Identificación con el PID** | El firmante se identifica presentando su **PID** desde la wallet (no hay PIN). Un verifier propio, `verifier-firma` (`verifiers/_comun`, instancia `firma`), pide solo documento y nombre; el servicio lee el resultado ya validado (firma del emisor, lista de confianza, estado, titularidad). Cada autorización —de servicio y de cada firma— pide el PID, y para firmar **el PID debe ser del titular de esa credencial de firma**. |
| **Certificados de firma** | Los emite la sub-CA **QTSP** del ecosistema (`srv-tl-publisher/pki/subca/qtsp`), perfil `qtsp-user` (keyUsage critical `nonRepudiation`, 825 días), con RSA 2048. Quedan en su `index.txt`, así que la CRL y OCSP de la PKI los ven. |
| **Custodia de claves** | Una clave por firmante, generada aquí y guardada **cifrada** (Fernet). La clave nunca está en el teléfono. En producción sería un HSM. |
| **Sellado de tiempo** | RFC 3161 en `/tsa` (lo llama el teléfono con `tsaUrl`) y `signatures/timestamp`. La respuesta la firma `openssl ts` con un certificado TSA (EKU `timeStamping`) emitido por la misma sub-CA. |
| **Portada** | `/andes-scd/` con el estado y las últimas firmas. Estilo basado en la web de Andes SCD (paleta y tipografía), sin su logo ni marcas. |

## Cómo se firma (lo que hace la wallet)

```
wallet                                       srv-andes-scd
  │── GET  /csc/v2/info ───────────────────────▶ anuncia oauth2Issuer, supportsRar, métodos
  │── GET  /.well-known/oauth-authorization-server
  │── POST /oauth2/par  (scope=service) ───────▶ request_uri
  │── navegador ▶ /oauth2/authorize ───────────▶ el firmante presenta su PID (QR o «Abrir mi wallet»)
  │◀─ rqes://oauth/callback?code=… ─────────────
  │── POST /oauth2/token ──────────────────────▶ token del firmante
  │── POST /csc/v2/credentials/list ───────────▶ credencial + cadena de certificados
  │   (la wallet calcula la huella del PDF EN EL TELÉFONO)
  │── POST /oauth2/par  (authorization_details: credentialID + huellas)
  │── navegador ▶ /oauth2/authorize ───────────▶ el firmante vuelve a presentar su PID y autoriza ESAS huellas
  │── POST /oauth2/token ──────────────────────▶ token con authorization_details
  │── POST /csc/v2/signatures/signHash ────────▶ firma RSA de la huella
  │── POST /tsa ───────────────────────────────▶ sello de tiempo
  └─ la wallet incrusta firma y sello en el PDF
```

**Identificación con el PID, en el mismo teléfono:** un QR en la pantalla del propio teléfono no se puede escanear, así que desde un móvil la
página muestra «Abrir mi wallet» (enlace `haip-vp://`) y pide al verifier que, al terminar, la wallet abra `/oauth2/retorno?rid=…&response_code=…`
(`wallet_response_redirect_uri_template`, el mecanismo del estándar para el mismo dispositivo). Esa página lee el PID con su `response_code`, y
envía sola la autorización, que devuelve a la wallet por `rqes://oauth/callback`. Desde un PC se muestra el QR y la página consulta el resultado.

**Una sola presentación del PID por sesión:** tras presentar el PID, el servicio recuerda quién es el firmante 10 minutos en ese navegador
(cookie `andes_sesion`, firmada con HMAC con la llave maestra, `HttpOnly`). Con la sesión vigente, la autorización de servicio continúa sola y la de
cada firma solo pide **confirmar** («Firmar»), sin teclear ni presentar nada. «No soy yo» (`nueva=1`) borra la sesión y pide el PID. Se comprueba que el
PID sea el titular de la credencial con la que se firma. Nota: el proxy público reescribe el `Path` de las cookies a `/`, así que la cookie viaja a
todo el dominio (contiene solo documento y nombre, firmados, no cifrados).

`signHash` solo firma con un token que autorizó **esas** huellas: un token de simple inicio de sesión, o una huella distinta, responde 403.

## Uso

```bash
./levantar.sh              # → https://civica-desarrollo.avance.org.co/andes-scd/   (necesita ../up.sh y la PKI)
./levantar.sh --build      # reconstruye la imagen
./levantar.sh --parar

# Prueba de punta a punta, sin teléfono (verifica firma, cadena y sello):
RAIZ_PEM=../srv-tl-publisher/pki/publico/ca/root.crt pruebas/probar_andes_scd.py https://civica-desarrollo.avance.org.co/andes-scd
```

Aparece en el panel de control (grupo «Servicios de confianza»). En el `nginx` central van dos rutas: `/andes-scd` y
`/.well-known/oauth-authorization-server/andes-scd`.

La prueba crea un firmante de prueba (por defecto documento `1098765432`, o el de `PRUEBA_DOCUMENTO`) y **emite certificados reales en la sub-CA QTSP**:
no es inocua si se repite sin necesidad.

## Configuración (`.env`)

`DOMINIO`, `PREFIJO`, `CLIENT_ID` / `CLIENT_SECRET` (el par OAuth2 de la wallet, el mismo que va en `RQESConfigImpl.kt`) y `REDIRECT_URIS` (`rqes://oauth/callback`).
`AUTENTICACION` = `pid` (por defecto) o `pin` (documento + PIN, **solo** para pruebas automáticas sin teléfono) y `VERIFIER_INSTANCIA` (`firma`).
El verifier se crea una vez con: `VERIFIER_NOMBRE=… VERIFIER_PROPOSITO=… WRPRC_VCTS=urn:eudi:pid:1 ../verifiers/_comun/levantar.sh firma`. No hace falta tocar la wallet: confía en los verifiers por la autoridad que emite sus certificados de acceso («Civica RP Access CA», ancla de la lista RPREG), y el certificado del verifier nuevo sale de esa misma autoridad.
El volumen `datos/` guarda la base SQLite, la llave maestra de custodia y la TSA: **no se versiona**.

## Límites del prototipo (etapa 1)

- **La identificación con el PID** (etapa 2) está probada hasta el verifier (el servicio crea la solicitud y lee su resultado) y el enlace abre la wallet; la presentación completa del PID desde el teléfono hay que probarla con la wallet desbloqueada. El modo `pin` conserva la prueba automática del resto del recorrido.
- Firmar con la wallet (etapa 3) está probado con un teléfono real: PDF firmado ECDSA P-256, con sello de tiempo en la copia de la wallet y, en Contratación, sellado por el portal.
- Sin revocación desde la interfaz, sin restablecer PIN, sin ECDSA (solo RSA), sin firma asíncrona.
- Llave maestra en disco, no en un HSM.

## Revocación por http (visores de PDF en Linux)

Los certificados de firmante anuncian su OCSP, CRL y CA emisora por **http** (`ANDES_URL_PKI_REVOCACION`, por defecto
`http://<dominio>/pki`). La biblioteca NSS (Poppler/`pdfsig`, Okular) no descarga la revocación por https y marcaba
«problema desconocido con el certificado» aunque la firma fuese válida. Los certificados antiguos (https) se reemplazan
solos en la siguiente autorización, sin revocarlos; los PDF ya firmados conservan sus direcciones https.
