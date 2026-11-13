# Issuer local y dominio civica-desarrollo.avance.org.co

Desde `docker-compose/`:

```bash
docker compose build uis-issuer
docker compose up -d
```

La imagen queda como `eudi-generic-credential-issuer:local`. El build puede descargar dependencias Gradle la primera vez, pero el servicio no usa la imagen GHCR `edge`.

Verificación:

```bash
docker image inspect eudi-generic-credential-issuer:local
docker compose ps
docker compose logs -f uis-issuer
```

URL pública configurada: `https://civica-desarrollo.avance.org.co/uis-issuer`

Requisitos: DNS apuntando al servidor, certificado TLS con el dominio, y reemplazar secretos de ejemplo antes de producción.
