# Attestation Rulebook — `MayoriaEdadNotarialV1` (borrador v0)

> **Estado: BORRADOR v0 (2026-10-14), PROPUESTA-LOCAL.** Sigue la estructura del template oficial (v1.5, 2026-07-08; ARB_29) pero **no es un esquema de la UE**. EAA **no cualificada**, catálogo propio del piloto (ADR-12, D-005).
> Fuentes de diseño: `proyecto/arquitectura-piloto-v1.md` §5.1-5.5; requisitos ARB (Topic 12: `python3 -I scripts/hlr.py --topic 12`); condiciones jurídicas en `derecho-notarial.md` y `firma-y-datos.md`.
> Marcas: ⚠ = decisión o verificación pendiente del equipo. Los dominios son marcadores a reemplazar por un dominio controlado (ver arquitectura §5.1).

| Versión | Fecha | Descripción |
|---|---|---|
| 0.1 | 2026-10-14 | Primer borrador a partir del esquema del equipo y los ARB del ARF 3.0.0 |

Autor(es): equipo del piloto (por completar). Feedback: por definir (⚠ sistema de seguimiento).

## 1 Introducción
### 1.1 Alcance y propósito
Permite que una persona demuestre **que es mayor de 18 años** ante una entidad participante **sin revelar** fecha de nacimiento, nombre, número de documento ni datos biométricos. Es una afirmación de la **notaría emisora**: certifica que, en una comprobación notarial definida y en una fecha, la persona superaba el umbral. **No** equivale a cédula, a PID europeo ni a certificación del estado civil (D-005, RN-013).
### 1.2 Estructura
Cap. 2 atributos y metadatos (independientes de la codificación) · cap. 3 codificación SD-JWT VC (mdoc diferido) · cap. 4 uso · cap. 5 anclas de confianza · cap. 6 revocación · cap. 7 cumplimiento.
### 1.3 Palabras clave
SHALL/SHOULD/MAY según RFC 2119; «must» minúscula = restricción externa. ### 1.4 Terminología: Annex 1 del ARF y `colombia/diccionario.md`.

## 2 Atributos y metadatos
### 2.1 Introducción y categorización
Tipo legal de atestación: **EAA no cualificada** (ARB_01a, ARB_12). El template define el atributo `category` (ETSI TS 119 472-1) con valores `urn:etsi:esi:eaa:eu:qualified` / `...:pub`, y `eaa:eu:non-qualified` para no cualificadas. Al no ser esquema de la UE, se propone el valor local **`eaa:co:non-qualified`** ⚠.
> **Inconsistencia detectada en el corpus:** ARB_25 (HLR, ARF 3.0.0) aún exige `attestation_legal_category`, pero el template v1.5 (08-jul-2026) lo renombra `category`. Se sigue el template; registrar la discrepancia al equipo de normalización o revisar la versión vigente del HLR.
**Principio de minimización (RN-011):** el tipo contiene solo el resultado de la comprobación. **Excluidos deliberadamente:** fecha de nacimiento, nombres, número de documento, plantilla o resultado biométrico crudo (RN-015), `subjectRef`, código de notaría (el emisor se identifica por su certificado).

### 2.2 Atributos obligatorios
| Identificador | Definición | Tipo | Ejemplo |
|---|---|---|---|
| `age_over_18` ⚠ | La notaría afirma que la persona tiene 18 años o más en `verification_date`. Solo existe con valor `true` | boolean | `true` |
| `verification_date` | Momento de la comprobación notarial | date-time (RFC 3339, UTC) | `2026-09-07T15:00:00Z` |
| `verification_procedure` | Procedimiento notarial aplicado | string (constante) | `CO-NOTARIAL-PILOTO-1` |
| `jurisdiction` | Jurisdicción de la afirmación | string (constante ISO 3166-1 alfa-2) | `CO` |
⚠ **Nombre del atributo:** el esquema del equipo usa `ageOver18`; se propone `age_over_18` por alineación con la convención de ISO/IEC 18013-5 (ARB_07/ARB_08: reutilizar convenciones de identificadores existentes). Requiere aprobación del equipo; el nombre es privado al tipo (ARB_06b).
### 2.3 Atributos opcionales: ninguno. ### 2.4 Condicionales: ninguno.
### 2.5 Metadatos obligatorios
| Identificador | Definición | Tipo | Ejemplo |
|---|---|---|---|
| `category` | Categoría legal de la atestación (ver 2.1) | string | `eaa:co:non-qualified` |
| emisor | Identifica a la notaría/entidad emisora (certificado y metadatos) | URI HTTPS + cadena X.509 | `https://emisor.example.org` |
| emisión / vencimiento | Instante de emisión y caducidad | NumericDate | `iat`, `exp` ≤ **24 h** |
| tipo | Identificador único del tipo (ARB_05) | URI | ver §3.1 |
| clave del titular | Vínculo criptográfico a la clave del dispositivo | JWK | `cnf.jwk` |
| referencia de estado | Posición en la lista de estado (cap. 6) | objeto | `status.status_list` |
### 2.6 Metadatos opcionales: `cryptographically_bound_to` **no se usa** (no existe PID en el piloto; ARB_28). ### 2.7 Condicionales: ninguno.

## 3 Codificación
### 3.1 SD-JWT VC (formato del piloto; ARB_01a, ARB_01b)
- Debe cumplir el perfil **IETF SD-JWT VC de HAIP §6.1** (ARB_01b). SD-JWT VC: draft-19 en el corpus (`estandares/ietf/sd-jwt-vc`); **fijar revisión** en el manifiesto.
- `vct` (único en el ecosistema, ARB_05): `https://<dominio-controlado>/types/mayoria-edad-notarial/v1` ⚠. Un tipo = una versión de perfil; no emitir variantes incompatibles bajo el mismo `vct`.
- Nombres de claim: privados al tipo (ARB_06b): `age_over_18`, `verification_date`, `verification_procedure`, `jurisdiction`.
- **Divulgación selectiva (ARB_30):** `age_over_18`, `verification_date` → el emisor **MUST** hacerlos divulgables selectivamente; `verification_procedure`, `jurisdiction` → **MAY**; `iss`, `iat`, `exp`, `vct`, `cnf`, `status` → **MUST NOT** (claims de protocolo).
- Firma del emisor **ES256 / P-256 / SHA-256** (arquitectura §5.5). ⚠ La acreditación ONAC de Andes SCD cubre **RSA, no ECDSA**: la PKI del piloto se declara **no acreditada** (D-012, RN-016).
- Se recomienda publicar un **Type Metadata Document** (SD-JWT VC cap. 6) con los metadatos de divulgación (ARB_31). Esquema JSON del sujeto: arquitectura §5.2 (se valida contra la consulta solicitada, no contra la credencial completa).
Ejemplo esquemático (sin firma válida): `{"iss":"https://emisor.example.org","vct":"https://<dominio>/types/mayoria-edad-notarial/v1","iat":1788786000,"exp":1788872400,"_sd_alg":"sha-256","_sd":["<digest>"],"cnf":{"jwk":{"kty":"EC","crv":"P-256","x":"…","y":"…"}},"status":{"status_list":{"idx":42,"uri":"https://estado.example.org/lists/notarial-2026-09"}}}`
### 3.2 mdoc ISO/IEC 18013-5 — **diferido** (ARB_02)
Análisis de proximidad (ARB_02 SHALL): el piloto es **solo remoto** (OpenID4VP). Pero la verificación de edad es un caso típico **presencial** (sin conexión): si se habilita la proximidad, el tipo **debe** emitirse también como mdoc (`mso_mdoc`). Reservado: `docType` y namespace propios únicos en el ecosistema (ARB_05, ARB_06a, ARB_10) ⚠ por definir. ISO/IEC 18013-5 **no está en el corpus** (de pago).
### 3.3 W3C VCDM 2.0 — fuera del perfil base (`CO-W3C-OPCIONAL-1`; ARB_04 exigiría documentos aprobados por un organismo de normalización de la UE).

## 4 Uso
- **Caso de uso:** acreditar mayoría de edad ante una entidad verificadora registrada, con finalidad declarada (ADR-15). Presentación **remota**: OpenID4VP 1.0 con DCQL, solicitando solo `age_over_18` (y, si hace falta, `jurisdiction`).
- **Ligada al dispositivo:** SHALL (clave P-256 en hardware seguro; prueba por Key Binding JWT con nonce/audiencia). **No** está ligada a otra atestación.
- **Obligaciones del verificador (RN-003):** firma válida; cadena y ancla aceptadas; **emisor autorizado para este `vct`**; vigencia; estado comprobado (indeterminado ⇒ no opera); posesión de clave; nonce/audiencia/transacción; transacción no consumida; atributo suficiente para la finalidad. **No** debe pedir un PID (ARB_27 vacío).
- **Datos transaccionales (Topic 20):** no aplica en el piloto.

## 5 Anclas de confianza (ARB_21, ARB_26)
Para EAA no cualificadas el rulebook define cómo el verificador obtiene las anclas. TS11 §4.3.1 recomienda el modelo **LoTE (ETSI TS 119 602)**. Propuesta ⚠: registro de entidades del piloto **firmado**, con secuencia, tiempo de emisión/expiración y rechazo de *rollback* (RN-004), publicado en una URL fija; el verificador valida cadena X.509 y **rol/tipo autorizado** del emisor. **No** llamarlo "EU Trusted List". La ubicación legible por máquina de las anclas se publica en los metadatos del emisor.

## 6 Revocación (ARB_24, ARB_24a)
**Revocable: sí.** Aunque vence en 24 h, se mantiene la referencia de estado para ensayar la revocación (AC-09; arquitectura §5.3). Mecanismo: **Token Status List** (IETF draft-21; Topic 7). URL base de las listas: `https://<estado-dominio>/lists/` ⚠. Objetivos propuestos, a medir: publicación <60 s, caché ≤5 min; lista ausente o expirada ⇒ `INDETERMINATE`. Motivos de revocación: error de emisión, anulación de la fuente (SIGNO), compromiso del vínculo con la clave. ⚠ **Hallazgo bloqueante nº 1** (`proyecto/revision-codigo.md`): el verificador de referencia no valida la firma de la lista; corregir antes de usar este mecanismo.

## 7 Cumplimiento
- **Emisión:** solo a partir de una **orden de emisión de un solo uso** de SIGNO (RN-010); el titular no autoafirma atributos.
- **ARB aplicables a EAA no cualificada** (estado del borrador): ARB_01a ✓ SD-JWT VC · ARB_01b ⚠ verificar perfil HAIP §6.1 en el emisor · ARB_02 ✓ analizado (proximidad diferida) · ARB_05 ⚠ dominio por fijar · ARB_06/06b ✓ · ARB_09 ✓ · ARB_12/25 ⚠ `category` local · ARB_21/26 ⚠ propuesta LoTE · ARB_24/24a ✓ revocable, URL por fijar · ARB_29 ✓ estructura del template · ARB_30 ✓ · ARB_31 ⚠ Type Metadata (recomendado) · ARB_33 NA (no se registra en el catálogo de la UE, CIR 2025/1569 art. 8) · ARB_34 ✓ ligada al dispositivo.
- **No es** esquema de la UE, QEAA ni PuB-EAA; no se declara conformidad EUDI-UE, equivalencia jurídica ni nivel de garantía (RN-030).

## Pendientes antes de v1
1. ⚠ Validación jurídica: qué afirma exactamente la notaría (testimonio, no certificación absoluta; `derecho-notarial.md` §1) y si el convenio RNEC permite reutilizar el cotejo (D-011).
2. ⚠ Nombre del atributo (`age_over_18` vs `ageOver18`), dominio y `vct`, valor local de `category`.
3. ⚠ Responsable de base de datos/retención y política de tratamiento (Ley 1581; roles notaría=responsable, operador=encargado).
4. ⚠ Decisión de proximidad (mdoc) y obtención de ISO/IEC 18013-5.
5. Pruebas: AC-05 (solo atributos necesarios), AC-06, AC-09, AC-10.
