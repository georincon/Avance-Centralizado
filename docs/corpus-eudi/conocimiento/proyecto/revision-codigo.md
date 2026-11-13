# Revisión de código frente a los 13 hallazgos de §9.2 (F1-05) — avance

> Categoría: **EVIDENCIA-LOCAL**, verificada leyendo el código (2026-10-14). Alcance de esta pasada: hallazgos 1, 2 y 4 (los demás siguen sin revisar).
> El documento de arquitectura (7-sep-2026) describía copias anteriores; algunas correcciones ya existen aguas arriba. **Lo que cuenta es el commit que el equipo fije.**
> Rutas relativas a `referencia/` (clones oficiales) o a `/home/geovani/Descargas/generic/` (copias del equipo).

| # | Hallazgo (§9.2) | Veredicto | Evidencia |
|---|---|---|---|
| 1 | Verificador: validadores de firma de Token Status List devuelven éxito sin verificar (JWT y CWT) | **CONFIRMADO, vigente, bloqueante** | `eudi-srv-verifier-endpoint` @df07af2 (2026-10-08): `adapter/out/tokenstatuslist/StatusListTokenValidator.kt:116` (JWT) y `:125` (CWT) pasan `verifyStatusListTokenSignature = { _, _ -> Result.success(Unit) }`. Idéntico en la copia local `eudi-generic-verifier-local-build/core/...` (origen @11a091f) y **en el fork propio** `eudi-generic-verifier-local-build/generic-verifier/verifier-protocol/src/main/kotlin/co/org/avance/verifier/protocol/adapter/out/tokenstatuslist/StatusListTokenValidator.kt:116,125` |
| 2 | Emisor: cliente Statium `NotValidating` al obtener listas | **NO REPRODUCIDO** en las copias actuales | `eudi-srv-pid-issuer` @944e060: `adapter/out/status/GetStatusListTokenWithStatium.kt:54` usa `VerifyStatusListTokenJwtSignature.usingTrust(...)`; `VerifyStatusListTokenJwtSignatureWithTrust.kt:36-60` verifica la firma con el `x5c` de la cabecera y exige emisor confiable (`isTrustedIssuer`). Igual en `eudi-generic-issuer-local-build` (@717cf40, `...WithTrust.kt`). Sin ocurrencias de `NotValidating` en `src/main` de ninguno |
| 4 | Emisor: rama de atestación con `kid` admite `did:key`/`did:jwk` (`ResolveDidUrl.kt`) | **NO ENCONTRADO** en las copias actuales | Sin coincidencias de `did:key`, `did:jwk` ni `ResolveDidUrl` en `src/main` de `eudi-srv-pid-issuer` (944e060) ni de `eudi-generic-issuer-local-build` (717cf40). Posiblemente corregido o eliminado desde la revisión del 7-sep |

## Matices importantes (para no sobre-concluir)
- **Hallazgo 2:** el emisor verifica solo status lists **JWT**; no hay ruta CWT en `adapter/out/status/` (relevante si se emite mdoc con estado CWT). La verificación de confianza depende de `IsTrustedIssuer.usingTrustValidatorService(...)` (servicio externo `eudi-srv-trust-validator`): si esa URL/servicio no está configurado, **hay que probar qué ocurre** (el hallazgo 3 sigue pendiente de revisar).
- **Hallazgo 1:** es un `TODO` de la referencia UE, no una decisión; en el verificador de producción del piloto debe implementarse la verificación equivalente a `usingTrust` (cadena `x5c` + ancla de confianza por rol) tanto para JWT como para CWT. Cubre AC-08 (lista JWT/CWT con firma inválida ⇒ rechazo/indeterminado).
- Las rutas `/home/geovani/...` son copias del equipo: este repo **no las modifica**.

## Pendientes (siguiente pasada, delegable a `revisor-conformidad`)
3 (confianza permisiva sin evaluador/anclas) · 5 (autenticación y tenant en APIs del verificador) · 6 (`response_code`) · 7 (estado de transacciones en memoria) · 8 (diferida/DPoP en memoria) ·
9 (fuente PID de Keycloak con datos de demo) · 10-11 (revocación programada, `/take` vs `/set`) · 12 (logs con `vp_token`/JWK, TLS inseguro) · 13 (Keycloak demo/secretos).
Anotar para cada uno: commit revisado, `ruta:línea`, veredicto y corrección propuesta.
