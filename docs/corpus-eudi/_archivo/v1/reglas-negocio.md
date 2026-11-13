# Reglas de negocio del ecosistema Colombia

> Reglas que gobiernan CÓMO funciona el ecosistema en Colombia. Son la traducción local de los
> principios del ARF (sección del ARF entre paréntesis). Cada regla lleva: descripción, origen,
> implicación técnica y estado (propuesta/validada).

## Reglas de gobernanza y confianza
- **RN-001 — Registro obligatorio de actores.** Todo emisor (PID/EAA) y todo verificador
  (relying party) que opere en el ecosistema debe estar registrado ante un Registrador local y
  portar certificados de acceso y de registro (ARF §6.3, §6.4). Estado: propuesta.
- **RN-002 — Proporcionalidad de datos.** Ningún verificador puede solicitar más atributos de los
  que registró para el trámite declarado; la wallet lo verifica antes de presentar (ARF §6.6.3.3).
  Estado: propuesta.
- **RN-003 — Arranque con EAA no cualificados.** El ecosistema colombiano inicia con
  credenciales EAA no cualificadas (education, salud, transito) sobre un análogo de LoTE local,
  dejando el PID nacional para fases siguientes (ARF §6.1, Topic 12). Estado: propuesta.
- **RN-004 — Consentimiento y minimización.** Todo atributo se presenta solo con aprobación
  explícita del ciudadano, con divulgación selectiva (ARF §6.6.3.5). Las políticas de privacidad
  del ecosistema deben cumplir el régimen de protección de datos colombiano (habeas data) para
  cada transferencia. Estado: propuesta.

## Reglas de datos y credenciales
- **RN-010 — Identidad soberana del ciudadano.** El ciudadano es quien decide qué atributos
  comparte, con quién y para qué; el ecosistema no centraliza datos a nivel de wallet (ARF §6.5.3.6:
  el Wallet Provider no accede al contenido de la wallet). Estado: propuesta.
- **RN-011 — Interoperabilidad de formatos.** Credenciales y protocolos en línea se basan en los
  formatos del ARF (SD-JWT VC y/o mDL/ISO 18013-5; OpenID4VCI/OpenID4VP) para poder interoperar
  con otros ecosistemas. Estado: propuesta.

## Reglas de certificación
- **RN-020 — Confianza certificable.** Antes de habilitar un actor como emisor/verificador de alto
  valor, su solución debe pasar una evaluación (análoga a CAB/FCAF) acreditada localmente
  (ARF cap. 7). Estado: propuesta.

> Cuando una regla se valide o cambie, actualizar aquí y referenciar la decisión en `decisiones.md`.