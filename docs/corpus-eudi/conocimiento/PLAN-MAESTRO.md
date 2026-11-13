# Plan maestro del agente EUDI-CO

> Dirección: el agente como director del proyecto (delegación del usuario, 2026-10-13). Documento vivo: se actualiza en cada sesión.
> Insumos: `ANALISIS-INICIAL.md`, `decisiones.md`, `proyecto/resumen-piloto.md`.

## 1. Objetivo
Que el equipo desarrolle **su propio ecosistema de wallet de identidad para Colombia**, empezando por el piloto de credenciales notariales,
con rigor técnico (ARF/eIDAS), rigor jurídico colombiano verificado y trazabilidad de cada afirmación. El agente debe poder:
**consultar** (corpus con citas), **verificar** (contrastar contra fuentes), **diseñar** (ADR, perfiles, rulebooks), **revisar** (diseño/código contra HLR, CIR y AC-01…23),
**investigar** (derecho e instituciones colombianas) y **recordar** (memoria acumulativa).

## 2. Principios rectores
1. El piloto del equipo manda; la UE es referencia, no mandato. 2. Citar o callar. 3. Separar normativo/técnico/estándar/preliminar/local.
4. No declarar lo no demostrado (certificación, equivalencia, disponibilidad). 5. Cada trabajo termina escrito en `conocimiento/`.

## 3. Mapa de conocimiento y competencias
Estado: ✅ cubierto · 🟡 parcial · 🔴 pendiente. "Cómo se cierra" = acción concreta.
| # | Dominio | Estado | Fuente | Cómo se cierra |
|---|---|---|---|---|
| K1 | Marco legal UE (eIDAS, CIR) | ✅ texto · 🟡 aplicación | `fuentes/regulaciones`, `ue/mapa-normativo.md` | Matriz obligación→rol→fecha (F1-02) |
| K2 | ARF y 725 HLR | ✅ texto · 🟡 aplicación | `scripts/hlr.py` | Matriz de conformidad piloto↔HLR por rol (F1-03) |
| K3 | Protocolos y formatos (OpenID4VCI/VP, HAIP, SD-JWT VC, Token Status List, DCQL) | ✅ texto | `estandares/openid,ietf` | Fichas de perfil y pruebas de interoperabilidad (F2) |
| K4 | mdoc / ISO 18013-5/-7 y 23220-2 | 🔴 (de pago) | — | Adquirir o solicitar a quien las tenga; mientras tanto, solo `mso_mdoc` declarativo |
| K5 | Criptografía, custodia y atestación (WSCA/WSCD, StrongBox, KA/WIA, HSM, EJBCA) | 🟡 | ARF cap. 4/6, TS3, keystore, ETSI | Verificar ES256/COSE en HSM de AndesSCD; ECCG ACM v2.0 (🔴 no hallado) (F2-04) |
| K6 | Modelo de confianza (X.509, TL/LoTE, registro, certificados de acceso/registro, trust-validator) | ✅ texto | ARF cap. 6, ETSI 119 612/602/475 | Diseño del registro colombiano y política por rol (F2-02) |
| K7 | Estado y revocación (TSL, statuslist-py, statium) | 🟡 | ETSI, repos | Revisión de código de los hallazgos 1, 2, 10, 11 (F1-05) |
| K8 | Derecho notarial colombiano | 🟡 | `colombia/derecho-notarial.md` | Resolver preguntas abiertas de A: régimen vigente tras C-159/2021, convenio RNEC, ¿SIGNO operador autorizado? (F1-01) |
| K9 | Identidad digital y SCD en Colombia (RNEC, AND, MinTIC) | 🟡 | `colombia/marco-institucional.md` | Completar reserva de ley, CONPES, estándares del piloto estatal (F1-01) |
| K10 | Firma digital/ECD, ONAC, protección de datos y biometría | 🟡 | `colombia/firma-y-datos.md` | Releer textos íntegros de las leyes (RA), CEA-3.0-07, ampliación de alcance (F1-01) |
| K11 | Fuentes auténticas y sectores colombianos (educación, salud, tránsito, DIAN…) | 🔴 | — | Fase 3 (F3-05) |
| K12 | Código de referencia y 13 hallazgos | 🟡 | `referencia/`, resumen §9.2 | Revisión dirigida del código contra cada hallazgo (F1-05) |
| K13 | Certificación y conformidad (CIR 2024/2981, FCAF, ITB, ONAC) | 🟡 | `referencia/eudi-doc-functional-conformance-assessment`, `eudi-itb` | Plan de evidencias AC↔FCAF (F2-05) |
| K14 | Privacidad, UX y accesibilidad (ARF cap. 8, design guide) | 🟡 | ARF annex-5 | Lista de verificación de la app (F3) |
| K15 | Interoperabilidad internacional (reconocimiento UE, ETSI, LAC) | 🔴 | — | Fase 4 |

## 4. Fases y entregables
**F0 — Reinicio (hecho 2026-10-13/14).** Corpus (`fuentes/`), índice FTS (10.460 fragmentos) y consultas de HLR, 20 repos de referencia, análisis inicial,
memoria (`decisiones`, `reglas`, `diccionario`, `mapa-normativo`, `resumen-piloto`), agente (`CLAUDE.md`, subagentes, skills, comandos).

**F1 — Conocimiento verificado (próximas sesiones).** Criterio de salida: ningún hecho clave del piloto depende de memoria o de v1.
- F1-01 Investigación jurídica colombiana A/B/C con fuentes oficiales → integrar en `colombia/`. **Avance:** B parcial (SCD, AND, cédula digital) integrada en `marco-institucional.md`; **A (notarial) hecha** → `colombia/derecho-notarial.md` (2026-10-14); **C (ECD/ONAC/datos) hecha** → `colombia/firma-y-datos.md`.
- F1-02 Matriz obligación→rol→fecha de los CIR (wallet provider, emisor, verificador, CA) → `ue/matriz-obligaciones.md`.
- F1-03 Matriz de conformidad piloto↔HLR: para cada Topic aplicable, estado cumple/parcial/no/NA y evidencia → `ue/matriz-hlr-piloto.md`.
- F1-04 Rulebooks v0 de `IdentidadVerificadaNotarialV1`, `MayoriaEdadNotarialV1`, `ActuacionNotarialV1` con el template del catálogo (skill `rulebook-co`). **Hecho 3/3 (borradores v0):** `colombia/rulebooks/` — `mayoria-edad-notarial-v1.md`, `identidad-verificada-notarial-v1.md`, `actuacion-notarial-v1.md` (discrepancia ARB_25 vs template v1.5 `category`; decisiones pendientes marcadas ⚠).
- F1-05 Revisión de código por hallazgo (13): ¿vigente en el commit fijado? evidencia `ruta:línea`, corrección propuesta → `proyecto/revision-codigo.md`. **Avance: 3/13 revisados** (nº 1 confirmado; nº 2 y 4 no reproducidos).
- F1-06 Manifiesto de versiones del piloto (commits de `referencia/` y de los builds locales).

**F2 — Línea base endurecida (etapas 0-1 de §13.1).** ADRs formales, perfil de interoperabilidad fijado, política de confianza por rol, plan de pruebas AC-01…23↔HLR↔FCAF, verificación de HSM/EJBCA.

**F3 — Emisión notarial, presentación y D01 (etapas 2-4).** Rulebooks finales, I01 SIGNO, DCQL de mayoría de edad, D01 (Java/Spring Boot) con contrato §7, recuperación, fuentes auténticas sectoriales.

**F4 — Operación y camino a conformidad.** Runbooks, métricas, acuerdos de participantes, matriz por rol/versión, ruta de evaluación (ONAC/CAB), proximidad mdoc, reconocimiento internacional.

## 5. Capacidades del agente por fase
F1: investigar y citar · F2: diseñar y revisar · F3: codificar y probar · F4: auditar y preparar evidencias.

## 6. Riesgos del propio agente y mitigaciones
| Riesgo | Mitigación |
|---|---|
| Afirmar derecho colombiano de memoria | D-010; subagente `investigador-normativo-co`; estado de verificación obligatorio |
| Citas ARF obsoletas o inventadas (p. ej. "R187") | IDs de HLR desde `scripts/hlr.py`; sin numeración propia |
| Corpus desactualizado (ARF y repos cambian a diario) | `scripts/actualizar_fuentes.sh` + reindexar; anotar commit en `CORPUS.md` |
| Fuentes de pago ausentes (ISO 18013, ECCG) | Declararlo; no completar de memoria |
| Mezclar propuesta local con requisito | Clasificación en 7 categorías; frase "no es requisito" |
| Alcance sin límite | Fases y criterios de salida; cambios de alcance = nueva decisión |

## 7. Backlog de verificación (hechos que NO pueden darse por ciertos aún)
- **(de C)** Releer texto íntegro de Ley 527, DL 19/2012 arts. 160-162, Ley 1581 y Decreto 1074/2015 art. 3.1.1 (hoy solo RA) · contenido de **CEA-3.0-07 V02** (pedir a ONAC/Andes SCD) · ¿Andes SCD puede ampliar alcance a ECDSA P-256 y certificados de emisor? · buscador oficial del directorio ONAC (QR del 16-ECD-004) · papel actual de la SIC con notarías · guías SIC sobre biometría; Res. SIC 45710/2026 y 52185/2025 · estado del PLE 282/2026C · normativa Superfinanciera sobre algoritmos · ¿la credencial es acto de firma Ley 527 o solo mensaje de datos?
- **(de A)** Régimen vigente del notariado electrónico tras C-159/2021 (SNR Oficina Jurídica / SUIN) y vigencia de Res. SNR 11/2021 · convenio RNEC-notarías: ¿permite reutilizar el cotejo para credenciales? · ¿SIGNO es operador/aliado autorizado por la SNR y qué devuelve el cotejo? · texto de Res. SNR 14681/2015, Circ. 3296/2019, Circ. 203/2025, Res. RNEC 3341/2013 · ¿alguna notaría es entidad de certificación (Ley 588 art. 1 par. 1)?
- Decreto 620/2020 (artículos, rol de la AND), Decreto 1263/2022 y resoluciones de SCD. *Cómo:* Gestor Normativo de Función Pública / SUIN.
- Piloto MinTIC mayo 2026: fechas y participantes (bootcamp 4-9 may; Agricultura/Finagro, Migración, Policía; Cancillería sin confirmar; rol de la AND sin respaldo).
- Reserva de ley y avances legislativos 2025-2026 sobre cédula/identidad digital.
- Régimen de ECD y acreditación ONAC; ¿AndesSCD figura acreditada y con qué alcance?; tratamiento de biometría (Ley 1581 y SIC).
- Mecanismo notarial de identificación biométrica (SNR/RNEC) y quién es responsable del dato.
- Valor jurídico de una "atestación" notarial electrónica distinta del instrumento público.

## 8. Registro de sesiones (retomar desde aquí)
- **2026-10-13/14 — Sesión 1 (reinicio).** Reorganizado el proyecto; analizada la información inicial; incorporados los documentos del equipo; construido el índice y el agente (CLAUDE.md, 4 subagentes, 5 skills, 7 comandos). Primera revisión de código (3/13 hallazgos). Investigación B parcial integrada; **A y C no se ejecutaron** (límite de sesión + decisión de ahorrar recursos).
- **2026-10-14 — Sesión 2.** Investigación A (notarial) completada e integrada (`colombia/derecho-notarial.md`); C-159/2021 contrastada en la parte resolutiva (arts. 59-63). Decisión D-011 (premisa «sin acceso a Registraduría» corregida). - **2026-10-14 — Sesión 3.** Investigación C integrada (`colombia/firma-y-datos.md`); alcance de Andes SCD verificado en el certificado ONAC (solo RSA); D-012, RN-015/016; convención de estados `colombia/README.md`. - **2026-10-14 — Sesión 4.** Rulebook v0 de `MayoriaEdadNotarialV1`. - **2026-10-15 — Sesión 5.** Rulebooks v0 de `IdentidadVerificadaNotarialV1` y `ActuacionNotarialV1` (F1-04 completo en borrador). **Siguiente paso:** decisiones ⚠ del equipo sobre los rulebooks; luego los 10 hallazgos de código restantes (`/revisar`, F1-05) y F1-03 (matriz de conformidad por rol).
