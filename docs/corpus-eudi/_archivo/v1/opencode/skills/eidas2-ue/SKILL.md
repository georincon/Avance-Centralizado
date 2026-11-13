---
name: eidas2-ue
description: >-
  Usar cuando se consulten las bases legales del ecosistema EUDI Wallet: Reglamento (UE) 910/2014
  (eIDAS) modificado por eIDAS2 (2024/1183), Implementing Acts (2024/2977, 2979, 2980, 2982,
  2024/2981, 2025/848), roles legales, obligaciones de los estados, avisos de notificacion,
  deadlines. Activar con "eIDAS2", "910/2014", "implementing act", "reglamento", "delegated act".
---

# eIDAS2 y las Implementing Acts de la EUDI Wallet

## Fuentes
- Local: `referencia/eudi-doc-standards-and-technical-specifications/` (análisis de brechas STS y mapa de TS).
- Autoritativa (siempre que sea posible): edición consolidada del Reglamento (UE) 910/2014 en EUR-Lex
  (https://eur-lex.europa.eu/eli/reg/2014/910/2024_.../oj), que incorpora las modificaciones del
  Reglamento (UE) 2024/1183 (eIDAS2).

## Mapa de reglamentación (verificar citas en EUR-Lex antes de afirmar números)
La base legal de la EUDI Wallet se compone de:
- **Reglamento (UE) No 910/2014 (eIDAS)** modificado por **Reglamento (UE) 2024/1183** (marco de
  identidad digital europea). Añade el artículo sobre la EUDI Wallet, roles (wallet provider, PID
  provider, attestation providers, wallet-relying parties, QES), obligaciones de los Estados miembros
  (FAQ → cada Estado debe ofrecer una wallet; *deadline* orientativo final 2026) y el régimen de
  notificación (avisos + un "eIDAS cooperation network").
- **Comisión Implementing Acts** (adoptadas por la Comisión sobre la base de la ley eIDAS2), entre las
  que se incluyen las relacionadas con PID/EAA, integridad y funcionalidades, avisos del ecosistema,
  protocolos e interfaces, certificación funcional y registro de Wallet-Relying Parties. En el ARF
  3.0.0 se mencionan explícitamente: CIR 2024/2977 (PID y EAA), CIR 2024/2979 (integridad y
  funcionalidades principales), CIR 2024/2980 (avisos del ecosistema), CIR 2024/2982 (protocolos e
  interfaces), CIR 2024/2981 (certificación funcional → FCAF) y CIR 2025/848 (registro de
  Wallet-Relying Parties). El catálogo de rulebooks referencia también CIR 2024/2977.
- Regímenes asociados: firma electrónica (eSignature/QES), seales (eSeal), notificaciones y servicios
  de confianza; ETSI como organismo de normalización (ETSI TS 119 612 trusted lists, 119 602 LoTEs).

## Reglas de uso
- **Nunca inventes números de artículos ni de reglamentos.** Si no está verificado localmente,
  consulta EUR-Lex o el mapa de STS antes de responder.
- Distingue: (1) obligación legal (reglamento), (2) especificación técnica (ARF/TS), (3) estándar
  (ISO/ETSI/OpenID Foundation), (4) preliminar (borradores/discussion topics).
- Explica las implicaciones para un estado miembro y luego deriva las lecciones para Colombia
  (qué instituciones ejercerían cada rol, qué ley local la sustentaría).
- Si el usuario pide cumplir normativa para un producto concreto en Colombia, indica primero que la
  normativa UE no aplica directamente y guiar el mapeo hacia el marco local.