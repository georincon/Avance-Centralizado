---
name: rulebook-co
description: Redacta o revisa un Attestation Rulebook para una credencial colombiana (IdentidadVerificadaNotarialV1, MayoriaEdadNotarialV1, ActuacionNotarialV1 u otras) usando el template oficial del catálogo EUDI y los requisitos ARB de Topic 12. Disparadores: "rulebook", "esquema de credencial", "atributos", "vct", "docType", "catálogo de atributos".
---

# Rulebook de credencial colombiana

**Insumos:** template `referencia/eudi-doc-attestation-rulebooks-catalog/template/attestation-rulebook-template.md` (v1.5, 2026-07-08); ejemplos `rulebooks/pid` y `rulebooks/mdl`;
requisitos: `python3 -I scripts/hlr.py --topic 12` (ARB_xx); esquemas propuestos del equipo: `conocimiento/proyecto/arquitectura-piloto-v1.md` §5.1-5.3.

## Estructura (7 capítulos del template)
1 Introducción (alcance, estructura, palabras clave RFC 2119, terminología Annex 1) · 2 Atributos y metadatos, **independientes de la codificación** (obligatorio/opcional/condicional; ARB_06, ARB_09; reutilizar catálogo, ARB_07) ·
3 Codificación por formato: SD-JWT VC (`vct`, claims, cuáles son divulgables selectivamente) y mdoc (`docType`, namespace); W3C solo en el perfil opcional · 4 Uso (¿ligada a dispositivo o no? `cryptographically_bound_to`, ARB_28) ·
5 Anclas de confianza (cómo obtenerlas) · 6 Revocación (Token Status List) · 7 Cumplimiento.

## Reglas para el piloto colombiano
- Categoría: **EAA no cualificada** de un catálogo **propio**; no se denomina PID ni se afirma equivalencia con cédula (ADR-12, D-005). El template pide `category` (ETSI TS 119 472-1) y no `attestation_legal_category` (cambio v1.5).
- Cada rulebook define: **emisor competente**, procedimiento de comprobación, **fuente** (SIGNO, orden de emisión de un solo uso), significado de cada atributo, caducidad (p. ej. 24 h para mayoría de edad como parámetro de diseño), motivos de revocación y aceptantes previstos.
- `subjectRef` opaco y específico del emisor/tipo; sin número de cédula. `MayoriaEdad`: solo `ageOver18` (booleano), sin fecha de nacimiento.
- Mapeo por formato (§5.3 del documento): tipo → `vct` / `docType`; vigencia → `exp` / `validityInfo`; vínculo → `cnf.jwk` / `deviceKey`; estado → `status.status_list`.
- Un tipo = una versión de perfil. No emitir dos variantes incompatibles bajo el mismo identificador.
- Lo jurídico (valor de la atestación, competencia de la notaría) se marca **pendiente de validación jurídica** hasta tener `investigacion/A-notarial.md` verificado.
Entrega el borrador en `conocimiento/colombia/rulebooks/<tipo>.md` y registra la decisión.
