# nw-nginx — punto de entrada único del ecosistema EUDI de Avance

Nginx es el **único** servicio que publica puertos en el host (80 y 443). Todo lo demás vive
detrás de él, en la red Docker compartida `avance-eudi`, y se alcanza por nombre de servicio.

Con esto el ecosistema se usa siempre por el dominio `civica-desarrollo.avance.org.co`, sin IPs
sueltas: el mismo acceso desde la LAN y desde fuera de ella.

## Cómo llega el tráfico

```
Teléfono / navegador
        │  https://civica-desarrollo.avance.org.co
        ▼
Proxy público  (190.29.100.206, nginx, certificado *.avance.org.co)
        │  reenvía 80 y 443 a este equipo
        ▼
nw-nginx  (este proyecto, 192.168.0.240:443)
        │  enruta por prefijo de ruta
        ├── /wallet-provider/*   → spartans-wallet-provider:8080
        ├── /onboarding/*         → onboarding-backend:8095
        ├── /biometrics/*         → onboarding-biometrics:8100
        ├── /pid-registraduria/*  → haproxy-reg:80
        ├── /idp-reg/*             → haproxy-reg:80
        ├── /uis-issuer/*         → haproxy-uis:80
        ├── /idp-uis/*            → haproxy-uis:80
        ├── /trust-validator/*    → trust-validator:8080
        ├── /tl-publisher/*, /TL/*, /LOTL/*  → tl-publisher:8080
        ├── /pki/*                → tl-publisher-pki:8080
        ├── /status-list/wallet-provider/*    → status-list-spartans:5000
        ├── /status-list/pid-registraduria/* → status-list-reg:5000
        ├── /status-list/uis-issuer/*         → status-list-uis:5000
        └── (resto)               → avance-control-panel:4173
```

El proxy público **no invierte TLS**: termina TLS con el certificado de producción
`*.avance.org.co` y reenvía a este puerto 443. Aquí el salto es con el certificado local de
desarrollo (`certs/live/civica-desarrollo.avance.org.co/`), que el borde no verifica.

## Rutas

| Prefijo | Servicio | Proyecto |
|---|---|---|
| `/wallet-provider/` | Wallet Provider (WUA + Key Attestations) | `eudi-srv-wallet-provider` |
| `/.well-known/oauth-protected-resource` | Metadata RFC 9728 del provider | `eudi-srv-wallet-provider` |
| `/jwks` | JWKS del provider | `eudi-srv-wallet-provider` |
| `/onboarding/` | Onboarding de usuario (identity proofing) | `onboarding` |
| `/biometrics/` | Servicio de visión (rostro/OCR) | `onboarding` |
| `/pid-registraduria/` | Issuer PID (OpenID4VCI) | `eudi-srv-pid-registraduria` |
| `/idp/` | Keycloak | `eudi-srv-pid-registraduria` |
| `/status-list/wallet-provider/` | Lista de estado del provider | `eudi-srv-wallet-provider` |
| `/status-list/pid-registraduria/` | Lista de estado del issuer | `eudi-srv-pid-registraduria` |
| `/` | Panel de control local | `panel-control` |

**Regla de oro:** *la URL que declara `ISSUER_PUBLICURL` es exactamente la que enruta este proxy.*

## Uso

```bash
./scripts/generar-certificados.sh   # PKI local del proxy (una vez)
docker-compose/up.sh                 # crea la red compartida si falta y levanta nginx
docker-compose/down.sh               # detiene
```

Cambios en `nginx.conf` se aplican con `docker exec nw-nginx nginx -s reload` (la config es un
bind mount, no hace falta recrear el contenedor).

## Red compartida

La red `avance-eudi` es **externa**: no pertenece a ningún proyecto y no se borra al hacer
`docker compose down`. Cada stack se conecta declarándola:

```yaml
networks:
  avance-eudi:
    name: avance-eudi
    external: true
```

Así ningún proyecto depende del ciclo de vida de otro, y el proxy alcanza todos los servicios por
nombre. La crea `up.sh` de este proyecto (o el de cada stack) si no existe.

## Certificate renewal

`certbot-renew` renueva el certificado local y recarga nginx. Para el entorno de desarrollo
actual el certificado del borde (`*.avance.org.co`) es el que ve el cliente, así que la
renovación local es opcional; el contenedor está por si se quiere un certificado propio válido
para el dominio.

## Acceso desde fuera de la red local y administración

Todo el ecosistema se publica por el dominio (`civica-desarrollo.avance.org.co`) y funciona desde Internet: wallets, emisores, Keycloak
público, onboarding, verificadores, listas de confianza y la descarga de la wallet (`/descargas/*`, `/login`). Es público a propósito.

Lo que **no** es público son las superficies de administración: consolas de Keycloak (`/idp-*/admin`, `/idp-*/realms/master`), paneles de
ciudadanos, estudiantes y licencias, la API del panel de control (`/api`), `/pki/issue` y `/pki/revoke`, y la API del publicador de listas.
Desde una IP no autorizada responden 404 (como si no existieran).

- **Autorizadas por defecto:** la red local, la salida a Internet de este sitio (`190.29.100.206`) y las IP de
  `ips-admin-permitidas.conf`.
- **Entrar desde fuera:** agregar la IP pública del equipo (la muestra https://api.ipify.org) a `ips-admin-permitidas.conf` con el formato
  `<ip> 1;` y recargar: `docker exec nw-nginx nginx -t && docker exec nw-nginx nginx -s reload`.
- **Cómo se sabe la IP real:** todo llega por el borde público, que añade la IP real al FINAL de `X-Forwarded-For`; nginx la toma con
  `real_ip` (`set_real_ip_from 192.168.0.0/24`, `real_ip_recursive off`), así que un `X-Forwarded-For` falso del cliente no sirve. Si el borde
  cambia de dirección fuera de esa red, hay que actualizar `set_real_ip_from`.
- **Tope de intentos:** 20 por minuto y por IP a las pantallas donde se escribe una clave (login admin, token y login del realm master,
  `/api/session`). Además los tres Keycloak tienen protección contra fuerza bruta en el realm master.
