# Ecosistema EUDI de Avance

La wallet europea de Avance, adaptada a Colombia: dos emisores (identidad y credenciales
académicas), un Wallet Provider, el servicio de confianza (listas y validador), un verificador,
el onboarding, un proxy único y el panel de control.

Todo se levanta con **un comando**.

```bash
./up.sh          # levanta el ecosistema (los diez stacks, UIS incluido)
./up.sh --sin-uis  # solo los nueve del compose raíz, sin el issuer de la UIS
./estado.sh      # qué hay en pie ahora mismo
./down.sh        # lo baja todo
```

Y todo se usa por un único dominio: **https://civica-desarrollo.avance.org.co**

## Qué levanta `./up.sh`

El compose raíz (`docker-compose.yaml`) reúne los nueve stacks del ecosistema con `include`, así
que cada uno conserva su fichero, sus variables y sus secretos:

| Stack | Qué es | Ruta pública |
|---|---|---|
| `eudi-srv-issuer-pid-registraduria` | Emisor de la PID (cédula) | `/pid-registraduria`, `/idp-reg` |
| `eudi-srv-issuer-pid-transito` | Emisor del mDL (licencia de conducción; no es PID) | `/pid-transito`, `/idp-tra` |
| `eudi-srv-wallet-provider` | Wallet Provider (WUA, Key Attestations) | `/wallet-provider` |
| `srv-onboarding` | Onboarding: documento, OCR, selfie, OTP, consentimiento | `/onboarding` |
| `srv-tl-publisher` | Publicador de listas de confianza + su back-office | `/tl-publisher` |
| `eudi-srv-trust-validator` | Validador de confianza | `/trust-validator` |
| `srv-nw-nginx` | Proxy único: 80 y 443, y todo lo demás detrás | — |
| `panel-control` | Panel de operación | `/` |
| `app-new-wallet` | Ancla de la app Android (su servicio vive en la red compartida) | — |

**El décimo stack, el del issuer de la UIS, tiene su propio compose** (no está en el compose raíz:
es el que más recursos consume —su Keycloak, sus dos Postgres y su issuer— y el que se para
cuando se trabaja con el de la Registraduría). `./up.sh` lo levanta igualmente, con su propio
compose, después del resto. Con `--sin-uis` se queda solo con los nueve del compose raíz.

## Los mismos botones en el panel

En https://civica-desarrollo.avance.org.co/ (el panel de operación) la cabecera tiene
**«Levantar todo»** y **«Detener todo»**: la misma orden, pero desde el navegador. Bajan y suben
los diez stacks de una vez; el proxy y el panel se quedan en pie, porque son los que sirven la
orden (y así se ve el resultado).

## Las redes

`avance-eudi` es la red compartida: no pertenece a ningún proyecto, `down` no la borra, y es por
donde el proxy alcanza a todos los servicios.

Cada stack tiene además **su propia red**, con nombre explícito (`registraduria_default`,
`spartans_default`, `onboarding_default`...). No es cosmético: `include` fusiona los proyectos
en uno, y si todos usaran la red `default` acabarían compartiendo red — el issuer de la
Registraduría podría alcanzar el Postgres de la UIS. Los volúmenes llevan también su nombre real,
para que al fusionar los proyectos no cambien de sitio y las bases de datos no arranquen vacías.

## Los proyectos

Cada uno es autónomo: tiene su compose en `<proyecto>/docker-compose/`, sus scripts y su
documentación.

| Proyecto | Rol ARF |
|---|---|
| `eudi-srv-issuer-pid-registraduria` | PID Provider |
| `eudi-srv-issuer-pid-transito` | Attestation Provider (mDL, PuB-EAA) |
| `eudi-srv-issuer-pid-uis` | Attestation Provider (PuB-EAA) |
| `eudi-srv-wallet-provider` | Wallet Provider |
| `eudi-srv-trust-validator` | Validador de confianza (LoTE/TSL) |
| `srv-tl-publisher` | Trusted List Provider (y su back-office) |
| `srv-onboarding` | Identity proofing |
| `srv-nw-nginx` | Gateway |
| `panel-control` | Panel de operación |
| `app-new-wallet`, `app-avance-wallet` | Wallet Units (Android) |

`ECOSISTEMA.md` es el manifiesto de roles: qué proyecto es cada rol, con qué ancla se publica, por
qué URL se accede y cómo se levanta. `ENLACES.md` es el mapa de rutas y credenciales.

## Lo que hay fuera del runtime

| Carpeta | Qué es |
|---|---|
| _(fuera del repo)_ `../eudi-srv-verifier-local-build/` | Verificador genérico (`/verifier`). Retirado el 28-oct-2026. Fuera del ecosistema. |
| _(fuera del repo)_ `../Avance_legacy/` | Instantáneas anteriores a la consolidación, movidas fuera de `Avance/` el 27-oct-2026. No es parte del runtime. Ver su README. |
| `docs/corpus-eudi/` | Corpus normativo ARF/HLR y la implementación de referencia, para consulta |

## Cosas que conviene saber

- **El proxy público reescribe el `Path` de todas las cookies a `/`.** Por eso las dos consolas
  de Keycloak (que viven en el mismo dominio) se pisan la sesión, y por eso el panel ofrece
  "Activar consola". Está explicado en `ECOSISTEMA.md` §6.
- **El stack de la UIS y el de la Registraduría son excluyentes en la consola**, no en los
  emisores. Con los doslevantados, los dos emisores funcionan; solo las consolas se estorban.
- **Cambiar el dominio obliga a republicar las listas**: las URLs van dentro del XML firmado.
