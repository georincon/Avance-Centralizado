# srv-tl-publisher — Publicador de listas de confianza

Sirve las Trusted Lists del ecosistema (ETSI TS 119 612) y su **interfaz de
administración**.

- **URL del dominio:** `https://civica-desarrollo.avance.org.co`
- **Listas:** `/TL/{esquema}/{n}.xml` y `/TL/{esquema}/{n}.json` (la misma, en `tl+jwt`)
- **LOTL:** `/LOTL/current.xml` y `/LOTL/current.json`
- **Administración:** `/tl-publisher/admin`
- **API pública:** `/tl-api/v1/lists`, `/tl-api/v1/lists/{esquema}/trust-anchors`,
  `/tl-api/v1/anchors/lookup`
- **API de administración:** `/tl-publisher/api/v1/...` (exige `X-Api-Key`)

## Qué hay aquí

| Ruta | Qué es |
|---|---|
| `app/` | El servicio: FastAPI. `app.py` (rutas públicas), `admin.py` (la API de administración), `bd.py` (consultas), `publicacion.py` (firmar y publicar), `firma.py` (XAdES), `generador.py` (XML conforme al XSD), `sembrar.py` (la siembra), `anclas.py`, `definiciones.py`, `config.py` |
| `app/static/admin.html` | La interfaz de administración: **un solo fichero** con HTML, CSS y JS. La SPA calcula su base a partir de la URL, así que el mismo fichero sirve bajo cualquier prefijo |
| `app/db/admin-schema.sql` | Las tablas propias de la administración |
| `db/script_db.sql` | El esquema del TLP (el de la base de datos) |
| `db/tlp-local-changes.patch` | Los cambios locales sobre el TLP de referencia |
| `pki/` | La PKI del proyecto: raíz, sub-CAs (`wp`, `pid`, `qeaa`, `qtsp`, `rp`, `tl`), scripts de emisión y revocación |
| `pki-servicio/` | El servicio de PKI: sirve CAs y CRLs, responde OCSP y **emite** los certificados de acceso (`POST /issue/rp-access`) |
| `firma/` | El firmante de las listas (`Civica TL Signing CA`) |
| `listas-publicadas/` | Lo publicado, por esquema y secuencia. **Inmutable**: una secuencia no se reutiliza |
| `scripts/` | `publicar-listas.sh` (sembrar y republicar), `importar-anclas-remotas.py`, `construir-jks-trust-validator.sh`, `emitir-firmante-tl.sh` |

## La administración

`/tl-publisher/admin` abre el back-office del TLP:

- **Actores**: los emisores, wallet providers y verifiers registrados, con su rol, su
  estado, sus certificados y sus supply points. Se pueden dar de alta, añadir o
  rotar certificados, retirarlos, declarar los puntos que publican y republicar.
- **Listas**: qué listas hay publicadas, con qué secuencia, cuándo se emitieron,
  cuándo caducan y los enlaces XML y JWS. Y el botón de republicar todo.

La clave del operador va en el `.env`:

```
TL_OPERATOR_KEY=...      # abre la administracion (cabecera X-Api-Key)
```

## Dar de alta un emisor: la lista no basta, hay que alimentar también al validador

Registrar un actor en `/tl-publisher/admin` (o con `POST /api/v1/actores`) lo publica en las Trusted Lists, pero **el
trust-validator no lee las anclas de las listas**: su biblioteca (DSS 6.5 con etsi1196x2 alpha) devuelve 0 certificados de
la LOTL. Las anclas que de verdad usa están en `eudi-srv-trust-validator/claves/anclas-confianza.jks`, que se construye
con los `.crt` de `pki/publico/ca/`. Un emisor puede salir «concedido» en la lista y, aun así, que el validador diga
«Path does not chain with any of the trust anchors». Pasos completos:

1. Registrar el actor (rol `pid`, `qeaa` o `pub-eaa`; `pub-eaa` para el mDL de Tránsito) o añadir el certificado al actor existente.
2. Copiar la raíz a `pki/publico/ca/<esquema>-<nombre>-<huella8>.crt` (huella8 = primeros 8 hex del SHA-256).
3. `eudi-srv-trust-validator/scripts/construir-jks-anclas.sh` y recrear el validador:
   `docker compose up -d --force-recreate --no-deps trust-validator` (con `docker restart` no basta si cambia la caché).
4. Republicar (`POST /api/v1/publicar`) y comprobar con `eudi-srv-trust-validator/docker/probar-ecosistema.py`.

Si el emisor cambia de raíz **con el mismo nombre** (pasó con la Notaría el 30-oct), la lista y el validador siguen con la
raíz vieja: no basta con que el nombre coincida, hay que añadir la nueva (sin quitar la anterior si otro proyecto aún la usa).

## El modelo de datos, en dos capas

| Capa | Tablas | Para qué |
|---|---|---|
| **Publicada** | `trust_service_providers`, `trust_services`, `trusted_lists`, `published_lists`, … | Lo que acaba dentro de los XML y los JWS firmados. Es lo que consume un validador. |
| **De administración** | `admin_actores`, `admin_certificados`, `admin_puntos` | Lo que el operador sabe y el esquema publicado no guarda: quién dio de alta el actor, su correo, su país, cuándo se emitió cada certificado, cuándo se retiró, si lo pone el propio servicio o una persona. |

Las dos capas se mantienen al día solas:

- `admin.sincronizar()` proyecta la de administración sobre la publicada, al publicar.
- `admin.adoptar()` hace el camino de vuelta: lo que ya está publicado y no tiene
  ficha de administración la recibe, para que la interfaz lo muestre entero.

**Un actor es un servicio, no un proveedor.** En el esquema publicado un proveedor
puede prestar servicios de varios roles a la vez ("Civica EUDI" sale en la lista
de wallet providers, en la de PID y en la de QEAA); en la interfaz cada uno de
esos es un actor distinto. Por eso `admin_actores` cuelga de `service_id`.

## Levantar

```bash
docker-compose/up.sh          # levanta el publicador, la BD y el servicio de PKI
docker-compose/down.sh
```

Variables (en `docker-compose/.env`):

| Variable | Para qué |
|---|---|
| `PUBLIC_DOMAIN` | El dominio que va dentro de las listas firmadas. Si cambia, hay que republicar. |
| `TL_PUBLISHER_API_KEY` | La que protege `POST /tl-api/v1/publish/{esquema}` |
| `TL_OPERATOR_KEY` | La que abre la administración |
| `PKI_ISSUE_API_KEY` | La que el publicador usa para pedir al servicio de PKI que emita un certificado de acceso |
| `TLP_DB_PASSWORD`, `TLP_DB_ROOT_PASSWORD` | La base de datos |

## Lo que hay que tener en cuenta

- **El publicador no guarda ninguna clave privada.** Firma las listas con el
  firmante de `firma/`, que se monta en solo lectura. Las hojas las emite el
  servicio de PKI, que es el único con las claves de las sub-CAs.
- **Una lista publicada es inmutable.** El número de secuencia nunca se reutiliza,
  ni siquiera si la publicación anterior fue un error.
- **Cambiar el dominio obliga a republicar**: las URLs van dentro del XML firmado.
