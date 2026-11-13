# Fase 2: plantilla común (2026-10-21)

## Hecho
- `verifiers/_comun/`: `compose.yml`, `compose.sh`, `levantar.sh`, `.env.example`, `config/haproxy`,
  `config/ui`, `scripts/generar-claves.sh`, `scripts/registrar.py`, `README.md`. Adaptados a Avance:
  dominio `civica-desarrollo.avance.org.co`, red `avance-eudi`, clave `TL_OPERATOR_KEY` de
  `srv-tl-publisher/docker-compose/.env`, API `/tl-publisher/api/v1/verifiers` y
  `/tl-api/v1/integracion/verifier.env`.
- `srv-nw-nginx/nginx.conf`: regla `~^/verifier-<instancia>` → `http://verifier-<instancia>:80`. Validada con
  `nginx -t` antes de recargar. Comprobado: una instancia inexistente da 502 (la regla coincide), y
  `/verifier`, `/tl-api`, `/` siguen igual. Copia previa de la configuración en el scratchpad de la sesión.
- `srv-tl-publisher`: `TRUST_VALIDATOR_URL` por defecto ahora es la URL pública (`/trust`): `verifier-api` no se
  une a `avance-eudi`. Comprobado que un contenedor externo llega al validador por esa URL.

## Pendiente
- `scripts/probar-presentacion` (hace de wallet sin teléfono): se porta con la primera instancia real.
- Primera instancia real: la discoteca (Fase 3). No se creó una instancia de prueba para no dejar un actor
  sobrante en el registro del tl-publisher.
- Al registrar un verifier, `POST /verifiers` añade su hoja como servicio en RPREG: hace falta republicar
  (`POST /publicar`) para que la hoja aparezca como ancla. Con la CA ya publicada no es imprescindible.
