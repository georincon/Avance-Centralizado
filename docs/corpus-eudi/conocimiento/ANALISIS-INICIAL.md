# Análisis de la información inicial (2026-10-13)

Insumos analizados: (1) agente v1 `agent_eudi` (AGENTS.md, opencode.json, 4 skills, 7 archivos de `conocimiento/colombia/`,
21 repos); (2) corpus recopilado en `fuentes/`; (3) documentación del equipo en `bitacora/` (arquitectura colombiana v1.0 del
7-sep-2026, requerimiento original, informe de keystore, ERSo 001-008, bitácoras de implementación, PDF interno del 24-sep).
Todo lo marcado ✔ se contrastó contra el corpus; lo marcado ⏳ espera la investigación normativa (`conocimiento/investigacion/`).

## 1. Hallazgo principal: el agente v1 no estaba alineado con el proyecto real
| | Agente v1 | Proyecto real del equipo |
|---|---|---|
| Enfoque | Descendente: RNEC como PID Provider, MinTIC/AND como Wallet Provider | **Ascendente**: piloto de credenciales **notariales** (AndesSCD + SIGNO + 1-2 notarías + 1 verificador) |
| Fuente de identidad | Registraduría | **Sin acceso a Registraduría** (la identidad la comprueba la notaría) |
| Credenciales | PID colombiano, EAA genéricos | IdentidadVerificadaNotarial, MayoriaEdadNotarial, ActuacionNotarial (`CO-EUDI-PILOTO-1`) |
| Backend | No existía | Wallet Backend (D01) propio, Java/Spring Boot; reutiliza wallet Android, emisor, verificador y wallet-provider EUDI |
| Conocimiento de código | Lista de repos | 13 hallazgos de seguridad en el código (6 bloqueantes) y 23 criterios de aceptación (AC-01…23) |
| DID/VDR | Ausente | Extensión **opcional** aislada (ERSo 004-008 implementadas en laboratorio: 56+121 comprobaciones E2E) |
**Consecuencia:** se rehace el centro de gravedad: el agente sirve a ese piloto y a su ruta hacia conformidad UE, no a un diseño abstracto.

## 2. Errores del agente v1 (todos corregidos en esta versión)
1. ✔ **Numeración `R…`** en skills (`R187`): no existe en el ARF 3.0.0. Los requisitos son HLR con ID por tema (`OIA_01`); `R10–R14` son riesgos (§7).
2. ✔ **RN-010** citaba ARF §6.5.3.6 como "el Wallet Provider no accede al contenido": esa sección trata de la cuenta de usuario (no necesita atributos reales; sus datos no van en KA/WIA). Cita corregida.
3. ✔ **D-003** hablaba de "PID calificado": no existe; los calificados son QEAA. El PID lo emite un PID Provider **notificado** (nivel *High*).
4. ✔ Numeración de capas distinta entre `arquitectura-objetivo` (6) y `matriz-brechas` (9). Ahora hay una sola taxonomía (ver PLAN-MAESTRO §3).
5. ✔ `opencode.json` prometía en `eidas2-sts` el texto de eIDAS y los CIR; ese repo solo contiene TS1-TS14. Los textos legales se incorporan ahora desde Cellar (43 actos).
6. ✔ La carpeta `.opencode/` (agente, skills, comandos) no estaba en `agent_eudi/`.
7. ✔ Repo `presentation-exchange-kt` obsoleto (OpenID4VP 1.0 usa **DCQL**; el repo `openid4vp-kt` lo confirma). Eliminado.
8. ⏳ Piloto MinTIC: fue un bootcamp de **6 días (4-9 may-2026)** con CDPI, no "sprint de 5 días en junio". Casos: Cédula Rural (Agricultura/Finagro), PPT (Migración), antecedentes (Policía). La participación de la AND **no está respaldada** en las fuentes halladas.
9. ⏳ Afirmaciones colombianas sin verificar en v1 (art. 2.2.17.1.4 Decreto 620/2020, Circular RNEC 011/2020, reserva de ley, Ley 527 arts. 28/30-31, rol de ONAC/SIC): se contrastan en `investigacion/`.

## 3. Hallazgos sobre la documentación del equipo
**Fortalezas.** La arquitectura v1.0 es sólida: perfiles separados (`CO-EUDI-PILOTO-1` / `EUDI-UE` / `CO-W3C-OPCIONAL-1`), límites de responsabilidad claros,
estado en cuatro dominios, resultado `ACCEPTED/REJECTED/INDETERMINATE`, pruebas adversarias y declaración honesta de lo no demostrado.
**Verificado contra el corpus ✔:** 2026/1731 art. 3(4) aplica desde 11-ago-2028; 2026/1735 art. 1(3) desde 1-ene-2027; ambos entran en vigor el vigésimo día tras su publicación (22-jul → 11-ago-2026);
2026/1730 modifica 2025/848 (que se aplica desde 24-dic-2026); 2026/1731 modifica 2024/2977, 2979, 2980 y 2982.
**Para corregir en el PDF interno "Identidad Digital EUDI Wallet" (24-sep):**
- Cita "Trusted Lists (Art. 22a)": en el eIDAS consolidado (2024-10-18) las listas de confianza están en el **art. 22**; no hay 22a.
- Presenta "W3C DID y VC" como "estándares de base" del ARF: el ARF 3.0.0 **no menciona DID** y trata W3C VCDM como opcional (§5.4.4: "this ARF does not currently require this").
- "Cada Estado miembro debe ofrecer una wallet en 2026": el art. 5a(1) dice *dentro de 24 meses desde la entrada en vigor de los actos de ejecución*; los CIR 2024/2977-2982 se publicaron el 4-dic-2024 y entraron en vigor a los 20 días → plazo ≈ 24-dic-2026 (cálculo derivado).
- ERSo 005 exige Multikey y el PDF usa `JsonWebKey2020` (discrepancia ya anotada por el equipo en la bitácora).
**Riesgo técnico declarado por el propio equipo:** Base58/Multikey propios sin vectores oficiales; claves del emisor por DID (desvío respecto a x5c de EUDI); custodio simulado; ninguna prueba cruzada contra emisor/wallet EUDI.

## 4. Cobertura del corpus (lo que el agente ya "tiene")
| Área | Estado |
|---|---|
| ARF 3.0.0 (PDF + 70 páginas Markdown + 725 HLR + discussion topics + TS1-14) | ✔ completo, indexado |
| Normas UE: eIDAS orig. (ES/EN) y consolidado 2024-10-18, 2024/1183, CIR 2024-2026, RGPD, NIS2, CSA, DSA | ✔ 43 actos |
| ETSI (23), IETF (13 RFC + SD-JWT VC + Token Status List), OpenID (VCI, VP, HAIP, OIDC, eKYC), W3C (VCDM, VC-JOSE-COSE, Data Integrity, DC API) | ✔ |
| Código de referencia | ✔ 20 repos (12 conservados + 8 nuevos que la arquitectura necesita) |
| **No disponible** (de pago o no hallado) | ISO/IEC 18013-5/-7, 23220-2 (clave para mdoc/proximidad), ECCG Agreed Cryptographic Mechanisms v2.0 (HLR `OIA_03` lo exige), GlobalPlatform, CSC API, FCAF publicado, WebAuthn L3 (conversión fallida) |

## 5. Brechas de conocimiento (qué faltaba para ser experto en el caso colombiano)
1. Derecho colombiano **relevante al piloto** (notarial, firma digital/ECD, datos personales/biometría): v1 solo tenía datos generales sin verificar. → Investigación A/B/C en curso.
2. **Matriz de conformidad** piloto↔HLR↔CIR por rol (Wallet Provider, Emisor, Verificador): no existía.
3. **Revisión de código** de los repos de referencia contra los 13 hallazgos (¿siguen vigentes en las versiones actuales?).
4. **Rulebooks** de las tres credenciales con el template oficial.
5. **Fuentes auténticas** colombianas (SNR, RNEC vía notarías, sectores) y régimen de acceso.
6. Ruta de **certificación/reconocimiento** (CIR 2024/2981, FCAF, ONAC) y equivalencias eIDAS↔Colombia.
7. Criptografía: perfil aceptable (ECCG ACM v2.0 no disponible) y HSM de AndesSCD (ES256/COSE no demostrados).
Plan para cerrarlas: `conocimiento/PLAN-MAESTRO.md`.
