# Attestation Rulebook — `ActuacionNotarialV1` (borrador v0)

> **Estado: BORRADOR v0 (2026-10-15), PROPUESTA-LOCAL.** Estructura y reglas comunes: ver `mayoria-edad-notarial-v1.md`. Aquí solo las **diferencias**. ⚠ = pendiente.
> Fuentes: arquitectura §5.1-5.2; `derecho-notarial.md`; D-005, D-011, RN-010…RN-016.

| Versión | Fecha | Descripción |
|---|---|---|
| 0.1 | 2026-10-15 | Primer borrador a partir del esquema del equipo |

## 1 Introducción
Una notaría afirma que **existe una actuación notarial finalizada** (registrada en SIGNO) y aporta su **referencia**, sin transportar el expediente. Sirve para acreditar «el trámite X se realizó» ante una entidad. **No** es copia autorizada, escritura ni instrumento público, y **no sustituye** el documento original: el valor jurídico es el de una **constancia/testimonio** (DL 960/1970 art. 3 y arts. 72-78); ⚠ **concepto jurídico pendiente** (no existe tipo legal propio; `derecho-notarial.md` §1).

## 2 Atributos y metadatos
`category` = `eaa:co:non-qualified` ⚠.
### 2.2 Obligatorios
| Identificador | Definición | Tipo | Ejemplo |
|---|---|---|---|
| `subject_ref` ⚠ | Referencia opaca del titular (como en identidad verificada) | string 16-128 | `b7f3…` |
| `act_reference` | Referencia opaca y verificable de la actuación en SIGNO; **no** es el número de escritura legible ni permite reconstruir el expediente | string 16-128 | `a91c…` |
| `act_type` | Tipo de actuación | enum | `autenticacion` · `declaracion_extrajuicio` · `constancia_registro` |
| `act_date` | Fecha de la actuación | date | `2026-09-07` |
| `notary_code` | Notaría que actuó | string | `NOT-11-024` |
| `record_version` | Versión del registro en SIGNO (cambia si se corrige la actuación) | integer ≥1 | `1` |
### 2.3 Opcionales / 2.4 condicionales: ninguno. **Excluidos:** contenido de la actuación, partes, datos del documento, cédula. ⚠ Si se amplía `act_type`, cada valor nuevo exige su propia revisión jurídica.
### 2.5-2.7 Metadatos: iguales (`category`, emisor, `iat`/`exp`, `vct`, `cnf.jwk`, `status`). Vigencia ⚠ por decidir.

## 3 Codificación
SD-JWT VC (HAIP §6.1), ES256, `vct` = `https://<dominio-controlado>/types/actuacion-notarial/v1` ⚠. **Divulgación selectiva (ARB_30):** `act_type`, `act_date`, `act_reference`, `subject_ref` → **MUST**; `notary_code`, `record_version` → **MAY**; protocolo → **MUST NOT**. mdoc diferido; W3C fuera del perfil base.

## 4 Uso
Acreditar la existencia de un trámite notarial finalizado, con finalidad declarada. Obligaciones del verificador: RN-003 + comprobar el **estado actual** (si la actuación se corrige o anula en SIGNO, la credencial se revoca y se reemite con `record_version` mayor). El verificador **no** debe interpretarla como certificado oficial del registro civil (arquitectura §8.4) ni como copia del documento notarial.

## 5 Anclas y 6 Revocación
Como mayoría de edad. Motivos: corrección o anulación en SIGNO (orden de revocación idempotente que identifica las emisiones afectadas), error de emisión, compromiso del vínculo. Esta credencial es la **candidata natural para la demostración de revocación** (AC-09), porque su fuente puede cambiar.

## 7 Cumplimiento y riesgos
- **Emisión:** orden SIGNO de un solo uso, consumo atómico, versión de expediente verificada (RN-010; AC-03, AC-04, AC-19).
- **Datos:** `act_reference` y `subject_ref` son dato personal; no incluyen contenido de la actuación. Roles y base jurídica: como identidad verificada. Retención de evidencia notarial: sigue en el sistema notarial; el emisor no duplica expedientes (arquitectura §9.4).
- **Riesgo legal estructural:** la base del notariado electrónico se vio afectada por la C-159/2021 (arts. 59-63 DL 2106/2019 inexequibles desde 20-jun-2023); el régimen vigente **no está verificado** ⚠ — esta credencial depende de él más que las otras.
- No es esquema UE ni QEAA/PuB-EAA (RN-030). PKI no acreditada (D-012).
## Pendientes
1. ⚠ Concepto jurídico del valor (constancia/testimonio) y régimen notarial vigente. 2. ⚠ Lista cerrada de `act_type` por actuación. 3. ⚠ Vigencia y política de reemisión. 4. Pruebas: AC-03, AC-04, AC-09, AC-19.
