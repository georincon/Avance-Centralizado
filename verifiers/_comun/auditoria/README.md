# Auditoría e historial local de los portales

Cada portal de `verifiers/` (los 12 verifiers y los 6 que emiten credenciales) hace dos cosas con cada hecho
(oferta, autorización y entrega de una credencial; solicitud y resultado de una verificación):

1. **Lo reporta** al centro de auditoría (`srv-auditoria`, menús *Actividad Emisores* y *Actividad Verificadores*). `auditoria.js`.
2. **Lo guarda en su propia base de datos** (un Postgres por proyecto, definido en el `compose.yml` del portal, con su volumen
   `<portal>_postgres_verif`) y lo muestra en una página propia: `https://<dominio>/<portal>/auditoria/` (solo desde IPs de
   administración). `historial.js`. La tabla `evento` es de solo inserción (un disparador impide UPDATE y DELETE) y hay dos vistas,
   `v_emisiones` y `v_verificaciones`. Si la base no responde, el portal sigue funcionando: anota el hecho en
   `<portal>/datos/historial.jsonl` y lo pasa a la base cuando vuelve (el archivo ya procesado queda como `historial-importado-*.jsonl`).

   | Portal | Contenedor | Puerto (solo 127.0.0.1) | Base / usuario / contraseña |
   |---|---|---|---|
   | acceso | `postgres-verif-acceso` | 5461 | `verif_acceso` |
   | aerolinea | `postgres-verif-aerolinea` | 5462 | `verif_aerolinea` |
   | banco | `postgres-verif-banco` | 5463 | `verif_banco` |
   | beca | `postgres-verif-beca` | 5464 | `verif_beca` |
   | cancilleria | `postgres-verif-cancilleria` | 5465 | `verif_cancilleria` |
   | contratacion | `postgres-verif-contratacion` | 5466 | `verif_contratacion` |
   | discoteca | `postgres-verif-discoteca` | 5467 | `verif_discoteca` |
   | eps | `postgres-verif-eps` | 5468 | `verif_eps` |
   | gym | `postgres-verif-gym` | 5469 | `verif_gym` |
   | migracion | `postgres-verif-migracion` | 5470 | `verif_migracion` |
   | notaria | `postgres-verif-notaria` | 5471 | `verif_notaria` |
   | pensiones | `postgres-verif-pensiones` | 5472 | `verif_pensiones` |

   (Usuario y contraseña son iguales al nombre de la base: valores de desarrollo; cámbialos en el `compose.yml` y en `HISTORIAL_DB_URL`
   antes de llevar un portal a producción.)

**Instalar un portal en otro servidor:** el proyecto es autónomo (portal + su base + su volumen en un solo `compose.yml`). Hay que
llevar su carpeta, su `.env`, su verifier (`../_comun/instancias/<portal>`) y ajustar la red `avance-eudi`, `AUDITORIA_URL` (hacia el
centro de auditoría) y `VERIFIER_API`. La base no depende de ningún otro servicio.

Las dos cosas son transparentes: asíncronas, con error ignorado, sin esperar y sin cambiar nada del flujo del portal.
Solo se guardan **nombres** de datos (nunca valores) y, como persona atendida, la cédula/nombre si el portal ya los conocía.
`AUDITORIA_ACTIVA=false` apaga ambas.

## Instalar / reinstalar

```
python3 verifiers/_comun/auditoria/instalar.py            # todos los portales
python3 verifiers/_comun/auditoria/instalar.py banco eps  # solo esos
verifiers/levantar-portales.sh --build                    # reconstruir las imágenes
```

`instalar.py` es idempotente: copia `auditoria.js` e `historial.js` a cada `src/`, engancha `servidor.js` y `emisor.js`
y, si el portal no tenía carpeta de datos, monta `./datos` en `/datos` en su `compose.yml`.
**Esta carpeta es la única fuente** de los dos archivos: no los edites en cada portal.

## Variables

| Variable | Por defecto | Para qué |
|---|---|---|
| `AUDITORIA_ACTIVA` | `true` | `false` apaga el reporte y el historial local |
| `AUDITORIA_URL` | `http://srv-auditoria:8111` | ingesta interna del centro |
| `AUDITORIA_CLAVE_SERVICIOS` | (vacía) | `X-Api-Key`, solo si el centro la exige |
| `HISTORIAL_DB_URL` | (la fija el `compose.yml` de cada portal) | conexión a la base propia del portal; sin ella el historial va solo al archivo |
| `HISTORIAL_DIR` | `/datos` | dónde se escribe el archivo de respaldo `historial.jsonl` |
