# srv-auditoria — Centro de auditoría centralizado de las wallets

Un solo lugar donde ver **todo lo que hace cada wallet del ecosistema y cada usuario**, como el extracto de una
aplicación bancaria: quién es, en qué teléfono está, cuándo entró y salió, qué credenciales pidió o presentó, con qué
emisor o verificador, a qué hora, si salió bien y, si falló, por qué.

> Es **monitoreo**: no cambia emisores, listas de confianza ni ningún flujo del ecosistema. La wallet solo *reporta*;
> si el centro no responde, la wallet sigue funcionando y reintenta después.

## Qué incluye

| Pieza | Dónde | Qué hace |
|---|---|---|
| Base PostgreSQL | `db/01-esquema.sql` | Esquema `auditoria`, todo en español. Extracto solo de inserción. |
| API de ingesta | `POST /srv-auditoria/api/v1/eventos` | La usan las wallets (clave `X-Api-Key`). Idempotente por `evento_id`. |
| API de consulta | `GET /srv-auditoria/api/panel/*` | Solo lectura, token `Bearer`. Corre en una transacción `READ ONLY`. |
| Dashboard | `https://civica-desarrollo.avance.org.co/srv-auditoria/` | Panorama, **Actividad Wallet**, **Actividad Emisores**, **Actividad Verificadores**, usuarios, wallets/dispositivos y contrapartes. Búsqueda, filtros y CSV. |
| Ingesta de servicios | `POST http://srv-auditoria:8111/api/v1/servicios/eventos` | La usan los emisores y verificadores. **Solo red interna** (el proxy no la publica). Ver `docs/CONTRATO-EVENTOS-SERVICIOS.md`. |

El dashboard (datos personales) solo se abre desde IPs de administración (`srv-nw-nginx/ips-admin-permitidas.conf`);
la ingesta (`/srv-auditoria/api/v1/`) está abierta porque la usan los teléfonos, protegida por su clave.

## Modelo de datos (resumen)

```
usuario ──< medio_contacto            datos personales (cédula) y celular/correo
   └──< wallet ──1:1── dispositivo    la wallet y el TELÉFONO donde corre (fabricante, modelo, Android, RAM, pantalla…)
          ├──< sesion                 cada ingreso/salida (método, inicio, fin, motivo)
          ├──< credencial             lo que tiene/tuvo en la wallet
          └──< actividad >── contraparte (emisor | verificador)
                  └──< actividad_elemento   nombres de datos compartidos (nunca valores)
tipo_actividad  catálogo · consulta_dashboard  quién consultó qué en el panel
```

`actividad` guarda: fecha/hora, tipo, contraparte, credencial, `resultado` (exitoso/fallido/cancelado/en_proceso),
`tipo_error` (red, autenticación, validación, emisor, verificador, usuario, interno), `codigo_error`, `mensaje_error`,
duración y canal (internet, qr, nfc, ble). Un *trigger* impide UPDATE/DELETE.

## Qué reporta la wallet

| Momento | Actividad |
|---|---|
| Termina el onboarding (aprobado) | `registro_usuario` — **alta automática**: datos personales, celular, correo y datos del teléfono |
| Onboarding rechazado / con error | `registro_usuario` fallido (con el motivo) |
| Crea su PIN | `creacion_pin` + primer `inicio_sesion` |
| Entra con PIN o biometría | `inicio_sesion` (exitoso o fallido) |
| Sale o la app pasa a segundo plano | `cierre_sesion` |
| Pide una credencial a un emisor | `solicitud_credencial` + `emision_credencial` (exitosa / fallida / en proceso) |
| Un verificador le pide datos | `solicitud_presentacion` |
| Comparte datos | `presentacion_credencial` (con los nombres de los datos compartidos) |
| Borra una credencial | `eliminacion_credencial` |

Los eventos se guardan primero en una cola en el teléfono y se envían en orden; sin red se conservan y se reenvían.

## Actividad de emisores y verificadores

Además de lo que reportan las wallets, cada **emisor** y cada **verificador** del ecosistema reporta lo que hace, exitoso o con error,
y se ve en dos menús con la misma estructura (cifras del período, actividad diaria, recorrido del trámite, ranking, credenciales,
motivos de falla, «quién es quién» y un registro filtrable con detalle):

| Menú | Qué muestra | Eventos |
|---|---|---|
| Actividad Wallet | Lo que hace cada wallet (antes «Extracto de actividad») | inicio de sesión, solicitud/recepción/presentación de credenciales… |
| Actividad Emisores | Cada oferta, autorización y entrega de una credencial | `oferta_credencial` → `autorizacion_emision` → `emision_credencial` (`revocacion_credencial`) |
| Actividad Verificadores | Cada solicitud de datos y su validación | `solicitud_verificacion` → `verificacion_credencial` |

Los pasos de un mismo trámite comparten `referencia` (id de la oferta o de la transacción) y el detalle los muestra como recorrido.
Un trámite abierto que nunca se cierra aparece como «sin respuesta» (30 min para emisores, 10 para verificadores).
Tablas: `servicio`, `tipo_evento_servicio`, `actividad_servicio`, `actividad_servicio_elemento` y la vista `v_actividad_servicio`
(`db/03-servicios.sql`; en una base ya creada se aplica a mano: `docker exec -i auditoria-db psql -U auditoria -d auditoria < db/03-servicios.sql`).

**Quién reporta.** Los portales de `verifiers/` (12 verifiers y 6 emisores) con `verifiers/_comun/auditoria/auditoria.js`
(`python3 verifiers/_comun/auditoria/instalar.py` lo engancha de forma idempotente), y los emisores PID de Registraduría, Tránsito, UIS y la Notaría EUDI con su propio enganche.
**Es transparente:** el reporte es asíncrono, con tiempo máximo de 2 s y los errores se ignoran; `AUDITORIA_ACTIVA=false` lo apaga y
si el centro está caído los servicios siguen igual (reintentan desde una cola en memoria).
**Privacidad:** solo viajan nombres de atributos; la cédula y el nombre van solo como «persona atendida» si el servicio ya los conocía.

## Levantar

Entra en el compose raíz (`./up.sh`). Secretos en el `.env` de la raíz (no versionado):
`AUDITORIA_CLAVE_WALLET` (la que lleva la wallet), `AUDITORIA_TOKEN_PANEL` (clave del dashboard), `AUDITORIA_DB_CLAVE`.

Solo este servicio: `docker compose -f srv-auditoria/docker-compose/docker-compose.yaml --env-file .env up -d --build`

La wallet recibe URL y clave al compilar (`app-new-wallet/publicar-wallet.sh` las toma del `.env`):
`-Pnw.auditoria.url=https://<dominio>/srv-auditoria -Pnw.auditoria.clave=<clave>`. Sin ellas el reporte queda apagado.

## Datos de demostración

`AUDITORIA_URL=... AUDITORIA_CLAVE_WALLET=... node scripts/sembrar-demo.mjs 24 30` crea usuarios **ficticios** con 30 días de
actividad usando la propia API de ingesta. No lo uses sobre la base real.

## Privacidad (importante)

Esta base reúne datos personales (cédula, nombre, contactos) y el historial de uso de cada persona. Está pensada para el
entorno de desarrollo/pruebas del ecosistema. Antes de llevarla a producción hay que resolver base legal y
consentimiento (Ley 1581 de 2012), retención, cifrado en reposo y el control de acceso del dashboard; ver
`docs/PROPUESTA-BD-AUDITORIA.md` (sección 10), que advierte de la tensión con el principio de *no vinculación* de eIDAS/ARF.
La clave de la wallet va dentro del APK: sirve para el entorno de pruebas, no es un secreto fuerte.

## Modelo entidad-relación

`docs/modelo-entidad-relacion.png` / `.svg` / `.pdf` — modelo físico generado desde la base real
(`python3 docs/generar-modelo.py`, requiere el contenedor `auditoria-db` y `google-chrome`).

## Conectar con DBeaver (o psql) desde esta máquina

PostgreSQL escucha solo en `127.0.0.1:5450` (nunca expuesto a la red). Base `auditoria`, **esquema `auditoria`**.
- Solo lectura (recomendado): usuario `auditoria_consulta`. Se creó con `CREATE ROLE auditoria_consulta LOGIN IN ROLE auditoria_lectura;`
  y `ALTER ROLE ... PASSWORD '...'`.
- Propietario: usuario `auditoria`, contraseña `AUDITORIA_DB_CLAVE` del `.env` de la raíz.
Desde otro equipo, por túnel: `ssh -L 5450:127.0.0.1:5450 usuario@192.168.0.240`.
