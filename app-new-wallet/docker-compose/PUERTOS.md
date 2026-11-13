# Mapa de puertos (auditoría 2026-10-16)

## New Wallet (todos únicos frente a los demás composes de `generic/`)
| Puerto | Servicio | Parametrizable (`.env`) |
|---|---|---|
| 8195 | backend de onboarding | `BACKEND_PORT` |
| 8200 | visión (rostro/OCR), solo localhost | `BIOMETRICS_PORT` |
| 5443 | Postgres de onboarding, solo localhost | `ONBOARDING_DB_PORT` |
| 8097 | New Wallet Provider | `NW_PROVIDER_PORT` |
| 5439 | Postgres del provider | `NW_PROVIDER_DB_PORT` |
| 9543 | issuer Registraduría + Keycloak (HTTPS) | no (la URL pública lo lleva incrustado) |
| 5441 | Postgres del issuer Registraduría | `REG_DB_PORT` (en su `.env`) |
| 8447 | gateway HTTPS opcional (apagado) | — |

`./up.sh` verifica los puertos y, si alguno choca, mueve a uno libre los parametrizables y relanza.
Si choca 9543 (no se mueve solo) avisa y aborta.

## Colisiones que quedan entre OTROS proyectos (no se tocan; solo si se levantan a la vez)
- 80/443: `eudi-srv-pid-issuer`, `eudi-generic-issuer-local-build`, `eudi-generic-verifier-local-build/core` (haproxy).
- 5432: `eudi-srv-pid-issuer` y el compose raíz de `eudi-srv-wallet-provider`.
- 9000: adminer del compose raíz de `eudi-srv-wallet-provider`.
- 8443 / 9443 / 8444: `bitacora/new_proyect` (la New Wallet ya no usa 8443 ni 9443).
- 5433 / 8095 / 8100: `addi/OnboardingAddi` (la New Wallet usa 5443 / 8195 / 8200, ya no choca).
