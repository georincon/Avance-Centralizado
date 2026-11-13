---
name: arf-ue
description: >-
  Usar cuando se consulte el Architecture and Reference Framework (ARF) de la EUDI Wallet:
  capitulos, anexos, requisitos funcionales (R...), rulebooks PID/mDL, modelo de confianza,
  data model, certificacion, discussion topics. Activar con palabras como "ARF", "annex",
  "rulebook", "requisito del ARF", "high-level requirement", "Topic N".
---

# ARF (Architecture and Reference Framework) — EUDI Wallet v3.0.0

Fuente local: `referencia/eudi-doc-architecture-and-reference-framework/`
En línea: `https://eu-digital-identity-wallet.github.io/eudi-doc-architecture-and-reference-framework/latest/`

## Cómo navegar

El ARF es la línea base técnica común de la EUDI Wallet. Está dividido en capítulos y anexos:

### Capítulos (`docs/main/`)
| Cap. | Contenido | Archivo |
|------|-----------|---------|
| 1 | Introducción | `docs/main/01-introduction.md` |
| 2 | Funcionalidades de la wallet | `docs/main/02-eudi-wallet-functionalities.md` |
| 3 | Roles del ecosistema (PID providers, wallet providers, attestation providers, WRPs, DPAs, Relying Party Services) | `docs/main/03-roles-within-the-eudi-wallet-ecosystem.md` |
| 4 | Arquitectura de alto nivel (componentes, interfaces, keys) | `docs/main/04-high-level-architecture.md` |
| 5 | Modelo de datos y protocolos de intercambio (SD-JWT, mDL/ISO 18013-5, OpenID4VCI, OpenID4VP, SCA) | `docs/main/05-data-model-and-data-exchange-protocols.md` |
| 6 | Modelo de confianza (trust model, trusted lists, LoTEs) | `docs/main/06-trust-model.md` |
| 7 | Certificación de la wallet solution y gestión de riesgo (incl. sección 7.5 FCAF) | `docs/main/07-wallet-solution-certification-and-risk-management.md` |
| 8 | Accesibilidad | `docs/main/08-accessibility.md` |
| 9 | Desarrollo del documento | `docs/main/09-document-development.md` |
| 10-11 | Referencias y anexos | `docs/main/10-references.md`, `docs/main/11-annexes.md` |

### Anexos (`docs/annexes/`)
- `annex-1` — definiciones (`annex-1-definitions.md`)
- `annex-2` — requisitos de alto nivel: 2.01 listado, 2.02 por tema, 2.03 por categoría
- `annex-3` — rulebooks: 3.01 PID rulebook, 3.02 mDL rulebook
- `annex-4` — flujos de interacción (PDF): 4.01 init/activación, 4.02 identificación/autenticación en línea, 4.03 emisión de mDL, 4.04/4.05 presentación proximity, 4.06–4.12 QES (remota/local) y firma
- `annex-5` — diseño: 5.01 design guide, 5.02 escenarios de compartición de datos
- `annex-6` — requisitos de certificación (PDF)

### Otros
- `docs/discussion-topics/` — temas abiertos a discusión; NO son normativa. Citar como "preliminar".
- `CHANGELOG.md` — verificar la versión vigente del ARF antes de afirmar.

## Reglas de uso
- Distingue: requisitos normativos (del Reglamento / Implementing Acts) vs. requisitos del ARF (R…,
  HLR) vs. discusión (topics). Preséntalo siempre.
- Los números de requisito (p. ej. `R187` de HLR 2.01, o `Topic N`) deben extraerse del texto local,
  nunca suponerse. Verifica el archivo antes de citar.
- Para escenarios de compartición de datos, apóyate en `annex-5.02` y en los flujos del `annex-4`.
- Al adaptar a Colombia, usa este ARF como la referencia técnica de partida y marca el delta local.