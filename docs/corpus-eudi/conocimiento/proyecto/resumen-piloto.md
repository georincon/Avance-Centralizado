# Resumen del piloto (carga rápida; fuente: `arquitectura-piloto-v1.md`, v1.0 del 7-sep-2026)

> Categoría: **PROPUESTA-LOCAL** del equipo + **EVIDENCIA-LOCAL**. No acredita despliegue, certificación ni equivalencia jurídica.
> Para detalle, buscar por sección: `python3 -I scripts/buscar.py "<tema>" -f conocimiento`.

## Qué demuestra el piloto (§1.1)
Un ciudadano obtiene una credencial derivada de una **comprobación notarial**, la guarda en su teléfono y presenta **solo los atributos necesarios**
a una entidad. Debe además demostrar: rechazo de emisor no confiable, rechazo por revocación y recuperación por pérdida del dispositivo.

## Hechos y límites (§1.2-1.3)
- **AndesSCD**: CA que opera el equipo (base EJBCA Community; versión/módulos Enterprise no presupuestos). **SIGNO**: software notarial (PHP) en ~80 % de las notarías de Bogotá; el equipo puede modificar sus integraciones.
- **Sin acceso directo a Registraduría** → no se ofrece "cédula oficial" ni se afirma consultar fuente estatal. Ecosistema inicial: coordinador + AndesSCD + 1-2 notarías + 1 verificador.
- Tecnología: Kotlin/JVM y Android (código EUDI), Java/Spring Boot para D01; Ubuntu + systemd (sin K8s obligatorio). Android primero; iOS/proximidad = evolución.
- "Soberana" = control del usuario sobre sus credenciales; no implica ausencia de autoridades ni que una clave autogenerada sea identidad civil.

## Perfiles técnicos (§2.4)
`CO-EUDI-PILOTO-1` (base): OpenID4VCI 1.0, OpenID4VP 1.0 con **DCQL**, SD-JWT VC (`dc+sd-jwt`) remoto, mdoc (`mso_mdoc`) para compatibilidad/proximidad futura, confianza X.509 + listas, estado firmado.
`EUDI-UE` (objetivo futuro de conformidad, no declarado satisfecho). `CO-W3C-OPCIONAL-1` (VCDM 2.0, DID opcional, Bitstring Status List; apagado por defecto).

## Marcas de componentes (§1.4)
C = existente reutilizable · I = integración/corrección acotada · D = desarrollo propio (**solo D01 Wallet Backend**) · O = opcional apagado · EXT = externo.
Mapa (§3.2): C01 app Android · C02 EUDI Wallet Core · C03 hardware seguro (SecureArea) · C10 emisor (PID Issuer adaptado) · C11 verificador · C12 trust backend · C13 revocación · C14 KMS · C15 firma de credenciales · C16 credential store ·
C17 status list · C18/C19 registro y gobernanza de confianza · **C20 Wallet Provider (WIA/KA)** · C21 Keycloak (IAM) · I01 adaptador SIGNO · **D01 Wallet Backend** · O01/O02 DID/VDR.

## Credenciales (§5)
Catálogo **propio** (no son tipos europeos): `IdentidadVerificadaNotarialV1`, `MayoriaEdadNotarialV1` (solo booleano; expira en 24 h en el piloto), `ActuacionNotarialV1`.
`subjectRef` opaco, sin cédula. Datos desde **SIGNO por orden autorizada de un solo uso**, nunca autoafirmados por el ciudadano. Falta publicar rulebook versionado por tipo.
Cripto base (§5.5): ES256/P-256/SHA-256 (SD-JWT y COSE), holder binding P-256 en hardware, DPoP con clave separada, JWE ECDH-ES/A256GCM, respaldo JWE `dir` A256GCM con clave del usuario, TLS 1.3.

## Confianza y estado (§8-9)
Confianza = registro de entidades + certificados + listas + reglas de rol (no "firma válida" ni DID resoluble). No llamar "EU Trusted List" a una lista colombiana.
**Cuatro dominios de estado**: credencial (emisor), instancia/atestaciones (Wallet Provider), certificado/clave (CA), acreditación de entidad (coordinador).
Verificador acepta solo si se cumplen **todas** las condiciones de §8.4; resultado `ACCEPTED | REJECTED | INDETERMINATE` (indeterminado ⇒ no se permite operación sensible).
Objetivo propuesto: estado publicado <60 s, caché ≤5 min (a medir).

## Wallet Backend D01 (§7)
Gestiona cuentas↔instancias, revocación, recuperación y respaldo cifrado opaco. **No** recibe claves del holder ni firma presentaciones (`POST /v1/vp/sign` no existe).
Endpoints nuevos `/v1/config`, `/instance-challenges`, `/instances`, `/recovery-requests`, `/operations`, `/backups`, `/credential-items`, `/presentation-intents`.
Seguridad: OIDC+PKCE+DPoP (Keycloak), `Idempotency-Key`, `If-Match`/ETag, `application/problem+json` (RFC 9457), prueba de posesión JWS (JCS RFC 8785).

## 13 hallazgos de código (§9.2; 6 bloqueantes) — aplicar a las versiones fijadas
> **Actualización 2026-10-14** (`proyecto/revision-codigo.md`): el nº 1 está **confirmado y vigente** (también en el fork propio del verificador); los nº 2 y 4 **no se reproducen** en las copias actuales del emisor (ya verifica la firma con `usingTrust`; sin `did:key`/`ResolveDidUrl`). Los demás siguen sin revisar.
Bloqueantes: (1) validadores de firma de **Token Status List** del verificador devuelven éxito sin verificar (JWT y CWT); (2) cliente **Statium** `NotValidating` en el emisor;
(3) confianza permisiva si falta evaluador/anclas; (5) APIs del verificador sin autenticación/pertenencia por tenant; (9) fuente PID de **Keycloak con datos de demostración**; (12) logs con `vp_token`/JWK y perfiles TLS inseguros.
Otros: kid `did:key/did:jwk` como atajo de atestación; `response_code` opcional; estado de transacciones/DPoP en memoria; revocación con fallos interpretados como válida; configuraciones `/take` vs `/set`; Keycloak demo/secretos de ejemplo.

## Decisiones ADR-01…16 (§12) — las que más condicionan
ADR-01 perfil base **sin DID/VDR** · ADR-02 SD-JWT VC+mdoc, W3C separado · ADR-03 claves del holder no exportables, recuperación = reemisión · ADR-05 confianza por registro/certificados/listas/roles ·
ADR-06 WIA/KA vía Wallet Provider reutilizado (validadores reales) · ADR-07 D01 auxiliar, no custodial · ADR-08 SIGNO fuente, IAM separado · ADR-09 estado firmado con fallo explícito ·
ADR-12 catálogo notarial propio, **no llamarlo PID** · ADR-14 reutilizar código UE fijando versiones y corrigiendo · ADR-16 recuperación con reidentificación y claves nuevas.

## Secuencia (§13.1) y aceptación (§13.2)
Etapas: 0 línea base reproducible → 1 cierre de controles de confianza → 2 emisión notarial → 3 presentación útil (mayoría de edad sin fecha) → 4 D01 y pérdida → 5 operación piloto → 6 evolución.
Criterios `AC-01…AC-23` (alta técnica, titular distinto, firma alterada, emisor sin rol, lista JWT/CWT inválida, lista expirada ⇒ INDETERMINATE, nonce/audiencia, concurrencia, tenant, SSRF, pérdida, respaldo, logs, interoperabilidad, DID opcional). **Aprobar pruebas ≠ certificación.**

## Normas de la UE consideradas (§2.2) — verificadas en `fuentes/regulaciones/` (ver `conocimiento/ue/mapa-normativo.md`)
2024/2977, 2979, 2980, 2981, 2982; 2025/846-849, 1566, 1569; 2026/798; **2026/1730** (modifica 2025/848), **2026/1731** (modifica 2977/2979/2980/2982), **2026/1735** (modifica 2025/1569).

## Decisiones organizativas pendientes (§13.4)
Participantes y facultad de cada notaría (jurídico/producto) · capacidades HSM y teléfonos (seguridad) · versiones y perfil de estado (arquitectura) · finalidad/retención (privacidad) ·
responsables de operación y ensayo de recuperación · si hay respaldo en el piloto · si se activa el laboratorio DID.
