# Colombia — Ecosistema de Identidad Digital (adaptado de los principios UE)

> Carpeta de conocimiento local del ecosistema. Aquí se acumulan contexto, decisiones,
> backlog de investigación y matrices de brechas. Las fuentes normativas citadas deben
> verificarse con fuentes oficiales antes de usarse en un documento.

## Objetivo
Construir un ecosistema de identidad digital para Colombia partiendo de los principios de la UE
(EUDI Wallet, eIDAS2, ARF v3.0.0) y adaptándolos al marco local.

## Sesiones de trabajo (retomar desde aquí)
- **Sesión 1 (10 oct 2026)**: creado el agente `eudi-experto` con base de conocimientos
  (11 repos UE clonados en `referencia/`), 4 skills y comando `/eudi`. Explicado el
  **modelo de confianza del ARF** (cap. 6): roles de confianza, LoTEs/Trusted Lists,
  certificados de acceso vs registro, KA/WIA, emisión y presentación.
  - **Punto de partida siguiente**: rellenar la fila de *Modelo de confianza* en
    `conocimiento/colombia/matriz-brechas.md` (investigando el marco colombiano real), o
    profundizar en device binding (SD-JWT/mDL) / KA-WIA (TS3), o autoevaluación.
- **Sesión 2 (11 oct 2026) — "entrenamiento" del agente**:
  - Base documental ampliada a **22 repos** (sd-jwt, presentation-exchange, pid-issuer,
    wallet-provider, verifier-endpoint, rp-registration, trustedlist-manager, demos, etc.).
  - Creado `AGENTS.md` (memoria operativa) y la estructura de conocimiento Colombia:
    `decisiones.md`, `reglas-negocio.md`, `arquitectura-objetivo.md`, `diccionario.md`
    (+ `matriz-brechas.md` preexistente).
  - Reglas aprendidas integradas en skills (checklists y "vicios colombia") y nuevo comando
    `/practicar` para autoevaluación.
  - **Siguiente paso natural** (sesión 3): llenar `matriz-brechas.md` capa por capa con
    investigación colombiana real, o arrancar el primer pilotaje técnico (issuer + verifier).
- **Sesión 3 (12 oct 2026) — investigación del marco colombiano real (capas 1 y 2) ✅**:
  - Creado `marco-institucional.md`: inventario verificado de instituciones, normas y
    servicios (AND/SCD + Autenticación Digital con niveles de confianza, RNEC/cédula digital,
    SIC, ONAC, MinTIC) y tabla de mapeo UE→CO.
  - Actualizada `matriz-brechas.md` con fuentes reales: capas 1, 2, 4, 5, 8 y 9 con estado
    **🟡** (analizado en curso) y capa 2 en **🟢**. Reordenada la priorización.
  - Hallazgos clave: (1) hay **reserva de ley** sobre la identificación → la cédula digital
    actual descansa en actos administrativos (brecha normativa, base legal necesaria);
    (2) **AND ya presta Autenticación Digital e Interoperabilidad** (niveles de confianza);
    (3) jun-2026 **MinTIC+AND+CDPI pilotaron credenciales verificables** (Cédula Rural,
    Migración Colombia, Cancillería, Policía) → el concepto ya está en la agenda oficial;
    (4) la PKI local (Ley 527/1999, ONAC, SIC) es la base sobre la que montar el modelo de
    confianza (capa 3).
  - Nueva decisión D-004 (marco institucional como fuente local, pendiente validación).
  - **Siguiente paso trazable**: diseñar el **modelo de confianza local (capa 3)** con la
    estructura del ARF (Registrador + Access CA + lista de confianza análoga) apoyándose en
    ONAC/SIC; o arrancar piloto técnico alineado al VC de MinTIC/AND.

## Backlog de investigación (por verificar con fuentes oficiales del año en curso)
- [x] Marco institucional y de identidad digital (AND/SCD, RNEC/cédula digital, MinTIC, SIC, ONAC) → `marco-institucional.md`.
- [x] Normativa de firma electrónica y servicios de confianza locales (Ley 527/1999, Decreto 1747/2000, Decreto 333/2014).
- [x] Protección de datos personales (Ley Estatutaria 1581/2012 + Decreto 1377/2013) y rol de la SIC.
- [x] Política de Gobierno Digital (Decreto 1078/2015, Decreto 767/2022, END 2023-2026, Ley 2294/2023 PND, CONPES 4144/2025 IA).
- [x] Iniciativas existentes de credenciales verificables / identidad digital en el país (piloto VC MinTIC+AND+CDPI jun-2026).
- [ ] Estado del proyecto de ley sobre cédula digital / reserva de ley (avance legislativo 2026).
- [ ] Detalle de niveles de confianza de la Autenticación Digital de la AND (documento técnico).
- [ ] Cómo se emitirían atributos clave (PID equivalente, educación, salud, tránsito) desde fuentes auténticas (MinEducación, EPS, SIMIT, etc.).
- [ ] Esquema ONAC de acreditación de entidades de certificación (normativa vigente).
- [ ] Detalle del piloto VC 2026 de MinTIC/AND (documento/resultados publicados).

## Mapeo propuesto de roles (ver `marco-institucional.md`, actualizado sesión 3)
| Rol UE | Posible institución en Colombia (propuesta inicial) |
|---|---|
| Registrador / fuentes auténticas | Registraduría Nacional del Estado Civil (RNEC) |
| PID Provider | RNEC (cédula digital) |
| PuB-EAA Providers | Min. Salud/EPS, Min. Educación, Mintransporte/SIMIT, DIAN, Migración Colombia, Cancillería, MinAgricultura |
| Wallet Provider | Estado (MinTIC/AND) y/o privados certificados |
| Supervisory Body / datos | SIC |
| CAB | Entidades acreditadas por ONAC |
| Listas de confianza (LoTEs) | Análogo nacional por definir |

## Entregables en esta carpeta
- `matriz-brechas.md` — comparativa UE (principio) vs Colombia (situación, brecha, acción).
- `marco-institucional.md` — inventario verificado de instituciones, normas y servicios.
- `NOTAS.md` — contexto, sesiones y decisiones (este archivo).
- (próximos) diseño del modelo de confianza (capa 3), rulebooks propuestos, plan de pilotos.

## Decisiones del usuario (preguntar/registrar aquí)
- (pendiente) Alcance: ¿wallet ciudadana, emisores, verificadores, o el ecosistema completo?
- (pendiente) ¿Quién ejerce el rol de PID/identidad en Colombia (RNEC vs. otra autoridad)?
- (pendiente) Prioridad de credenciales/atributos para los primeros pilotos.
- (opcional) Diseñar el modelo de confianza local (capa 3) en la próxima sesión.