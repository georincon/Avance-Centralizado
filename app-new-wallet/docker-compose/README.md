# docker-compose — New Wallet

La app Android es lo único propio de este proyecto. Los servicios que usa (onboarding, wallet
provider, issuer) viven en su propio proyecto y se acceden por el **proxy único** con el dominio
`civica-desarrollo.avance.org.co`.

```bash
./up.sh      # crea la red compartida si falta y levanta lo propio de New Wallet
./down.sh    # detener
```

## Servicios del ecosistema (cada uno en su proyecto)

| Servicio | Proyecto | Ruta pública |
|---|---|---|
| Onboarding de usuario | `Avance/srv-onboarding` | `/onboarding` |
| Wallet Provider (WUA + Key Attestations) | `Avance/eudi-srv-wallet-provider` | `/wallet-provider` |
| Issuer PID + Keycloak | `Avance/eudi-srv-issuer-pid-registraduria` | `/pid-registraduria`, `/idp-reg` |
| Issuer UIS (PuB-EAA) + Keycloak | `Avance/eudi-srv-issuer-pid-uis` | `/uis-issuer`, `/idp-uis` |
| Publicador de listas de confianza | `Avance/srv-tl-publisher` | `/tl-publisher` |
| Validador de confianza | `Avance/eudi-srv-trust-validator` | `/trust-validator` |

Detener cada uno se hace desde su propia carpeta (`./down.sh`).

## Compilar la app con el dominio

Sin IPs locales: el mismo APK sirve dentro y fuera de la LAN.

```bash
cd ..
./gradlew :app:assembleDevDebug \
  -PbackendUrl=https://civica-desarrollo.avance.org.co/onboarding \
  -Pnw.walletprovider.url=https://civica-desarrollo.avance.org.co/wallet-provider \
  -Pnw.issuer.url=https://civica-desarrollo.avance.org.co/pid-registraduria \
  -Pnw.tl.wp=https://civica-desarrollo.avance.org.co/tl-publisher/TL/WP/current.json \
  -Pnw.tl.pid=https://civica-desarrollo.avance.org.co/tl-publisher/TL/PID/current.json \
  -Pnw.tl.pubeaa=https://civica-desarrollo.avance.org.co/tl-publisher/TL/PUBEAA/current.json \
  -Pnw.tl.wrpac=https://civica-desarrollo.avance.org.co/tl-publisher/TL/RPREG/current.json \
  -Pnw.rqes.url=https://civica-desarrollo.avance.org.co/andes-scd \
  -Pnw.rqes.client.id=wallet-client \
  -Pnw.rqes.client.secret=andes-secreto-de-pruebas
```

Los tres `nw.rqes.*` apuntan al prestador de firma simulado (`srv-andes-scd`); sin ellos la app firma contra
`newwallet.invalid` y la firma falla. El secreto es el de `srv-andes-scd/.env` (`CLIENT_SECRET`).

Las listas salen del **publicador local**, no de `civica.avance.org.co`: ese host es el
despliegue de producción y no debe ser una dependencia. El ancla de las partes usuarias
está en `TL/RPREG` (es la lista que publica el tipo de servicio `CA/RPaccess`).

El issuer de la UIS no necesita parámetro: va fijo en el flavor `dev` como
`https://civica-desarrollo.avance.org.co/uis-issuer`.

Sin esos parámetros, la app cae en los hosts de reserva (`newwallet.invalid`) y no contacta
ninguna infraestructura: es deliberado, para que un APK sin configurar no toque nada.
