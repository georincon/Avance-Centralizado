# Inventario del corpus (actualizado 2026-10-14)

Todo lo de `fuentes/` y `referencia/` es **solo lectura**. Actualizar: `bash scripts/actualizar_fuentes.sh`. Índice: `python3 -I scripts/construir_indice.py`.

## `fuentes/` (documentos oficiales)
| Carpeta | Contenido | Notas |
|---|---|---|
| `arf/` | ARF v3.0.0 PDF oficial (552 págs.) + `.txt` | Build 2026-07-23 |
| `arf-repo/` | Repo fuente de eudi.dev (docs Markdown, `hltr/`, CHANGELOG) | Commit `f5789a7` (2026-10-02), posterior al PDF |
| `hlr-arf-3.0.0.csv` | 725 HLR estructurados | 558 SHALL · 20 SHALL NOT · 28 SHOULD · 2 SHOULD NOT · 14 MAY · 103 sin palabra normativa. Consulta: `scripts/hlr.py` |
| `standards-repo/` | TS1-TS14 con esquemas JSON/XSD/OpenAPI | Fuente de las especificaciones técnicas de la Comisión |
| `eidas1/` | Reglamento 910/2014 original en **español** (DOUE L 257) | Sin las reformas de 2024 |
| `regulaciones/` | 44 actos UE en inglés (Cellar): eIDAS orig. y **consolidado 2024-10-18**, 2024/1183, CIR 2024-2026 (incl. 1730/1731/1735), CID 2015/1505 y 2025/2164, RGPD, NIS2, CSA, DSA, Dir. 2016/2102 y 2019/882 | Mapa: `conocimiento/ue/mapa-normativo.md`. Falta consolidado posterior a 2024-10-18 (no publicado en Cellar al consultar) |
| `estandares/etsi/` | 23 normas ETSI (PDF+txt): TS 119 101, 602, 612, 411-8, 412-6, 431-1/-2, 432, 471, 472-1/-2/-3, 475, 478, 182-1; EN 319 411-1/-2, 132-1, 122-1, 162-1/-2, 142-1; EN 301 549 | Últimas versiones publicadas halladas |
| `estandares/ietf/` | RFC 2119, 3339, 3647, 5280, 5322, 7519, 8259, 8392, 8610, 8943, 8949, 9562, 9901 + SD-JWT VC (draft-19) + Token Status List (draft-21) | RFC 2119 omitido del índice (muy corto) |
| `estandares/openid/` | OpenID4VCI 1.0, OpenID4VP 1.0, HAIP 1.0, OIDC Core, eKYC/Identity Assurance | |
| `estandares/w3c/` | VCDM 2.0, VC-JOSE-COSE, Data Integrity, Digital Credentials API | WebAuthn L3: conversión fallida (JS), no indexado |

## `referencia/` (código oficial EUDI, clonado `--depth 1`)
| Repo | Commit | Rol en el piloto |
|---|---|---|
| `eudi-lib-android-wallet-core` | 0d7c5b1 · 2026-10-02 | C02 core de la wallet |
| `eudi-app-android-wallet-ui` | 7f40440 · 2026-10-09 | C01 app (el equipo tiene su variante en el laboratorio) |
| `eudi-srv-pid-issuer` | 944e060 · 2026-10-09 | C10 emisor |
| `eudi-srv-verifier-endpoint` | df07af2 · 2026-10-08 | C11 verificador |
| `eudi-srv-wallet-provider` | 2eeec23 · 2026-10-08 | C20 Wallet Provider (WIA/KA); validadores simulados por defecto |
| `eudi-lib-jvm-openid4vci-kt` · `-openid4vp-kt` · `-sdjwt-kt` | 6-oct-2026 | Protocolos y formato |
| `eudi-lib-kmp-statium` | cac41d2 | Verificación de estado (Token Status List) |
| `eudi-lib-kmp-etsi-1196x2` · `eudi-srv-trust-validator` | 8-oct-2026 | Validación de cadenas contra listas de confianza ETSI 119 612/602 |
| `eudi-srv-statuslist-py` · `eudi-srv-status-validator-py` | 2025-11-24 · 2026-08-04 | Servidor y validador de estado (statuslist-py sin commits desde nov-2025: revisar mantenimiento) |
| `keycloak-client-attestation-auth-ext` | 606ae99 | Extensión Keycloak (autenticación de cliente por atestación, "ABCA") |
| `eudi-srv-web-relyingparty-registration-py` · `eudi-srv-web-trustedlist-manager-py` | sep-2026 · jun-2025 | Registro de partes usuarias / gestor de listas (patrones) |
| `eudi-doc-attestation-rulebooks-catalog` | 36f8adc | Template y rulebooks PID/mDL |
| `eudi-doc-functional-conformance-assessment` · `eudi-itb` | 09cff88 · b0e2224 | FCAF y banco de pruebas de interoperabilidad |
| `eudi-wallet-reference-implementation-roadmap` | 2024-10-17 | Roadmap (desactualizado) |
**Eliminados (reclonables):** iOS wallet kit, python issuer, web-verifier, booking demo, multiplatform verifier UI, android-verifier-core, presentation-exchange-kt (obsoleto: OpenID4VP 1.0 usa DCQL), y los duplicados del ARF/TS (ya en `fuentes/`).

## No disponible (de pago o no hallado)
ISO/IEC 18013-5/-7, 23220-2, 19794-5, 39794-5, 5218, 3166, CEN EN 419 241-1 · **ECCG Agreed Cryptographic Mechanisms v2.0** (HLR `OIA_03`) · GlobalPlatform (OMAPI, CS), GSMA SAM, CSC API, CTAP · PSD2 (2015/2366) · FCAF publicado (conformance.eudi.dev) · ETSI TS 119 461 · WebAuthn L3 (texto).

## Otros
`imagen/Figure_2_High-Level_Architecture.png` — Figura 2 del ARF (explicada en la sesión del 2026-10-09). `_archivo/v1/` — configuración y memoria del agente anterior (solo como lista de hipótesis).
