# _comun: plantilla de verifiers del ecosistema Avance

Una instancia = el endpoint oficial (`eudi-srv-verifier-endpoint`) + su UI (`eudi-web-verifier`) + un
gateway haproxy, publicada en `https://civica-desarrollo.avance.org.co/verifier-<nombre>/`.

```bash
./levantar.sh <nombre>          # crea la instancia, se registra en el tl-publisher y la levanta
./compose.sh <nombre> logs -f verifier-api
./compose.sh <nombre> down
```

Lo que hace `levantar.sh`: crea `instancias/<nombre>/.env` (fuera de git), genera la clave y el CSR, pide el
certificado de acceso al tl-publisher (`POST /tl-publisher/api/v1/verifiers`, perfil `rp-access` con EKU
`1.0.18013.5.1.6`), descarga `/tl-api/v1/integracion/verifier.env` y levanta el stack.

Antes del primer arranque edita `VERIFIER_NOMBRE` y `VERIFIER_PROPOSITO` en el `.env`: van en el certificado
y la wallet los muestra al usuario. Cambiarlos después obliga a borrar `secretos/cadena.pem` y repetir.

## Cómo encaja en el ecosistema

- **Proxy único**: `srv-nw-nginx/nginx.conf` enruta `/verifier-<nombre>` a `http://verifier-<nombre>:80`, el
  alias del haproxy de la instancia en `avance-eudi`. El prefijo NO se quita en el proxy (lo quita el haproxy).
- **Solo el haproxy se une a `avance-eudi`.** Si `verifier-api` entrara también, el nombre de servicio se
  repetiría entre instancias y el gateway podría hablar con el verifier equivocado. Por eso `verifier-api` usa
  la URL pública para el trust-validator (`https://civica-desarrollo.avance.org.co/trust`) y las listas.
- **Confianza**: la wallet confía en el verifier por la lista RPREG (ancla `Civica RP Access CA`); el verifier
  confía en los emisores por PID/QEAA/PUBEAA a través del trust-validator.
- **Emisores apagados**: la Registraduría y la UIS no pueden estar arriba a la vez. `verifier.env` recuerda la
  última lectura de cada emisor, así que un verifier valida credenciales de ambos aunque uno esté apagado.

## Origen

Adaptado de `wallet/verifier` (ejemplo de otro desarrollo): dominio, red, API del tl-publisher y proxy son los
de Avance. El código del ejemplo no se modificó.
