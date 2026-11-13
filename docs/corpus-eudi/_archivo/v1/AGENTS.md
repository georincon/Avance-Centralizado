# AGENTS.md — Proyecto Agente86 (Ecosistema de Identidad Digital para Colombia)

> Este archivo es la **memoria operativa** del proyecto. Lo leen todos los agentes de opencode.
> Informa al agente de trabajo sobre: qué es este proyecto, dónde vive el conocimiento y cómo
> trabajar en él sin pisarse los árboles.

## Qué es este proyecto
Construir y documentar un **ecosistema de identidad digital para Colombia** partiendo de los
principios y estándares de la Unión Europea (EUDI Wallet, Reglamento eIDAS 910/2014 modificado por
eIDAS2 2024/1183, sus Commission Implementing Regulations y el ARF v3.0.0), adaptándolos al marco
legal, institucional y cultural colombiano.

## Estructura del repositorio

```
Agente86/
├── opencode.json              # Configuración del agente; declara las 21 "references" (repos UE)
├── GUIA-AGENTE.md             # Cómo funciona el agente y cómo extenderlo
├── referencia/                # Repos oficiales de eu-digital-identity-wallet clonados (--depth 1)
│   ├── eudi-doc-architecture-and-reference-framework   # ARF v3.0.0 (fuente principal técnica)
│   ├── eudi-doc-standards-and-technical-specifications # STS / brechas de estándares, TS1..TS14
│   ├── eudi-doc-attestation-rulebooks-catalog          # Catálogo + template de rulebooks
│   ├── eudi-doc-functional-conformance-assessment     # FCAF (casos de prueba de conformidad)
│   ├── eudi-lib-*  eudi-srv-*  eudi-web-*             # Código de referencia (wallets, issuer, verifier, trust)
│   └── ...
├── .opencode/
│   ├── agent/eudi-experto.md  # El agente especialista (respuesta por defecto del proyecto)
│   ├── skills/                # arf-ue, eidas2-ue, eudiw-implementacion, colombia-adaptacion
│   └── command/               # /eudi, /practicar
└── conocimiento/
    ├── colombia/              # LA MEMORIA DEL ECOSISTEMA LOCAL (fuente de verdad adaptada a CO)
    │   ├── NOTAS.md           # Contexto, sesiones de trabajo, backlog de investigación
    │   ├── decisiones.md      # Decisiones tomadas (con fecha y motivo) — se acumulan
    │   ├── reglas-negocio.md  # Reglas de negocio que gobiernan el ecosistema colombiano
    │   ├── arquitectura-objetivo.md  # Arquitectura técnica objetivo (por capas)
    │   ├── matriz-brechas.md  # Matriz de brechas UE → Colombia (por capas)
    │   └── diccionario.md     # Glosario bilingüe UE→CO (términos clave)
    └── (próximo) investigacion/, pilotos/
```

## Reglas de trabajo (para cualquier agente que toque este repo)

1. **La fuente de verdad técnica es `referencia/`** (repos UE clonados). Antes de afirmar un
   requisito, un número de artículo, un formato o un API, verificar en esas carpetas o en fuentes
   oficiales (EUR-Lex, conformance.eudi.dev). No inventar requisitos R…, números de reglamento ni
   firmas de API.
2. **La fuente de verdad "Colombia" es `conocimiento/colombia/`**. Al hablar de Colombia, leer
   primero esas carpetas y respetar decisiones y reglas ya registradas; si una decisión cambia,
   actualizar el archivo y anotar el motivo.
3. **Toda conclusión de un trabajo debe capturarse en `conocimiento/`** (regla, decisión, brecha,
   arquitectura o backlog). El proyecto "se entrena" acumulando memoria, no apagando incendios.
4. **Separar siempre**: normativo (reglamento) / técnico (ARF, TS) / estándar (ISO, ETSI, OIDF) /
   preliminar (discussion topics) / propuesta local (nuestra adaptación). Dejar explícito en qué
   categoría está cada afirmación.
5. **Marco legal colombiano**: verificar siempre con fuentes oficiales vigentes (MinTIC, SIC,
   Registraduría, Congreso) vía búsqueda web. No afirmar normativa de memoria.
6. Los repos de `referencia/` están clonados sin historial (`--depth 1`). No se editan; se usan
   como referencia y se pueden actualizar con `git pull`.

## Estilo
- Responde en español, claro y didáctico cuando se enseña; directo cuando se ejecuta.
- Terminología bilingüe: término del estándar en inglés entre paréntesis la primera vez.
- End-to-end: cada trabajo concluye con un siguiente paso concreto y trazable.