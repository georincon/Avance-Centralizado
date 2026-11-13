# pruebasintegracion — respuesta al equipo de apoyo en integración

Material para las **pruebas de integración de la plataforma de notarías (SIGNO QA)** con el emisor de la notaría de ejemplo. Generado el 4-nov-2026.

## Qué pidió el equipo y dónde está

| Requerimiento | Respuesta | Archivo |
|---|---|---|
| **URL del API REST** para las pruebas de integración | `https://civica-desarrollo.avance.org.co/notaria-issuer/api/v1` (autenticación con `X-API-Key`; la clave va por canal aparte) | [`API-ORDENES-EMISION.md`](API-ORDENES-EMISION.md) §1 |
| **Documentación técnica del API REST** | Documento de consumo con flujo, estados, los tres endpoints, ejemplos, errores y campos de cada documento | [`API-ORDENES-EMISION.md`](API-ORDENES-EMISION.md) |
| **Modelo entidad-relación** del prototipo | Diagrama, diccionario de datos y cardinalidades de las 6 tablas, más el DDL real | [`MODELO-ENTIDAD-RELACION.md`](MODELO-ENTIDAD-RELACION.md) · [`esquema-bd.sql`](esquema-bd.sql) |
| **Ver la base de datos** al consumir desde Postman y comprobar que se registra información | Qué fila/evento escribe cada llamada y consultas SQL para verlo | [`MODELO-ENTIDAD-RELACION.md`](MODELO-ENTIDAD-RELACION.md) §4 |
| **Cómo hacer las pruebas** (paso a paso, para quien las ejecuta) | Guía en pasos: preparar Postman, correr las 24 pruebas, probar con la wallet, ver la base de datos, qué hacer si algo falla y glosario | [`GUIA-PASO-A-PASO.md`](GUIA-PASO-A-PASO.md) |
| **Informe de pruebas** (para la PMO y dirección de tecnología) | Plantilla con las 24 pruebas, las de wallet, la verificación en base de datos, hallazgos y conclusión | [`PLANTILLA-INFORME-PRUEBAS.md`](PLANTILLA-INFORME-PRUEBAS.md) |
| **Consumir desde Postman** | Colección de 24 pruebas con comprobaciones automáticas y su entorno; la misma guía con `curl` | `Integracion-Notarias.postman_collection.json` · `Integracion.postman_environment.json` · [`PRUEBAS-CURL.md`](PRUEBAS-CURL.md) |

## Acuerdos de la reunión técnica

| Actividad | Responsable | Estado |
|---|---|---|
| Crear repositorio en Subversión | Mauricio Cortés | Cumplido (27/10/2026) |
| Pasar los desarrollos a Subversión | Luis González / Geovani Rincón | **Cumplido en lo principal:** ambos ecosistemas ya están en `http://192.168.0.110:7080/proyectos/svn/Identidad_Digital/`. **Pendiente:** subir la última actualización (incluye la API de órdenes de emisión y esta carpeta) |
| Definir y documentar los endpoints (a) crear la orden, (b) obtener QR/enlace de oferta, (c) consultar estado; la notaría no implementa el protocolo | Luis González / Geovani Rincón | **Hecho:** API implementada, desplegada y documentada (esta carpeta) |
| Enviar el documento de consumo a la ingeniera Claudia | Luis González / Geovani Rincón | **Listo para enviar:** [`API-ORDENES-EMISION.md`](API-ORDENES-EMISION.md) + la colección y el entorno de Postman |
| Consumir los endpoints desde SIGNO QA | Claudia Mantilla | Pendiente |

## Qué se construyó para esto

El emisor de la notaría (`eudi-srv-issuer-pid-notaria`) **no tenía** un API para que otra plataforma pidiera emisiones: su portal exigía que la persona se identificara con la wallet. Se añadió:

- `backend/app/ordenes.py` — el API (`/v1/…`, publicado bajo `/notaria-issuer/api/v1`), con clave por cliente, validación de datos, idempotencia por `referencia` y bitácora.
- Dos tablas nuevas en PostgreSQL (`notaria_orden_emision`, `notaria_orden_evento`), creadas solas al arrancar.
- Un gancho en `/credential` (`route_oidc.py`): cuando la wallet recibe el documento, la orden pasa a `EMITIDA`.
- La clave del cliente `SIGNO` en `eudi-srv-issuer-pid-notaria/secretos/api-ordenes.json` (no se versiona), montada en el backend.

## Verificación (4-nov-2026)

- **24 pruebas automáticas contra el sistema real, todas bien** (`python3 postman/pruebasintegracion/generar.py --verificar`): autenticación, creación, idempotencia, conflicto, validaciones, oferta con QR y PIN, estado, cancelación y rechazos.
- **Sin probar:** la entrega real a una wallet (`OFERTA_GENERADA` → `EMITIDA`). El cierre de la orden se comprobó con la sesión de una oferta real, pero sin que una wallet recibiera el documento: esa prueba necesita un teléfono. Está anotado en el documento de consumo (§7).

## Entregar

- **Compartir:** `GUIA-PASO-A-PASO.md`, `PLANTILLA-INFORME-PRUEBAS.md`, `API-ORDENES-EMISION.md`, `MODELO-ENTIDAD-RELACION.md`, `esquema-bd.sql`, `Integracion-Notarias.postman_collection.json`, `Integracion.postman_environment.json`, `PRUEBAS-CURL.md`.
- **No compartir** (en `.gitignore`): `Integracion-local.postman_environment.json` — lleva la clave. La clave se entrega aparte; se rota cambiando `secretos/api-ordenes.json` y reiniciando `notaria-issuer-backend`.
- **Regenerar:** `python3 postman/pruebasintegracion/generar.py [--verificar]`.
