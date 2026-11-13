# Attestation Rulebook — `IdentidadVerificadaNotarialV1` (borrador v0)

> **Estado: BORRADOR v0 (2026-10-15), PROPUESTA-LOCAL.** Misma estructura y reglas que `mayoria-edad-notarial-v1.md` (leerlo primero: ARB, `category`, divulgación, revocación, anclas). Aquí solo se detallan las **diferencias**.
> Fuentes: arquitectura §5.1-5.2; `derecho-notarial.md`; `firma-y-datos.md`; D-005, D-011, D-012, RN-010…RN-016. ⚠ = decisión o verificación pendiente.

| Versión | Fecha | Descripción |
|---|---|---|
| 0.1 | 2026-10-15 | Primer borrador a partir del esquema del equipo |

## 1 Introducción
Una **notaría** afirma que, en una comprobación notarial definida y en una fecha, **identificó a la persona** con los documentos y el cotejo biométrico aplicables. Sirve para que una entidad participante reciba una identificación **derivada de un trámite notarial**. **No** es cédula, PID ni certificación absoluta de identidad civil (DL 960/1970 arts. 24, 73, 77-78: la notaría da **testimonio de hechos percibidos**; la identidad civil la administra la RNEC). Debe redactarse como «se identificó a X con documentos Y y cotejo biométrico RNEC en fecha Z» (D-011).

## 2 Atributos y metadatos
`category` = `eaa:co:non-qualified` ⚠ (ver rulebook de mayoría de edad §2.1).
### 2.2 Obligatorios
| Identificador | Definición | Tipo | Ejemplo |
|---|---|---|---|
| `subject_ref` ⚠ | Referencia **opaca**, específica del emisor y del tipo; no es número de cédula y se oculta en presentaciones que no la necesitan | string 16-128 | `b7f3…` |
| `given_names` | Nombres de pila tal como los comprobó la notaría | string 1-150 | `María Camila` |
| `family_names` | Apellidos | string 1-150 | `Rojas Pérez` |
| `verification_date` | Momento de la comprobación | date-time UTC | `2026-09-07T15:00:00Z` |
| `verification_procedure` | Procedimiento aplicado (constante) | string | `CO-NOTARIAL-PILOTO-1` |
| `notary_code` | Código de la notaría que comprobó | string `^[A-Z0-9-]{2,32}$` | `NOT-11-024` |
### 2.3 Opcionales
| `birth_date` | Fecha de nacimiento; solo se emite si la finalidad lo requiere (preferir `MayoriaEdadNotarialV1`) | date | `1990-05-14` |
| `identification_means` ⚠ | Medio de identificación tenido en cuenta (cf. fe de vida, DL 960 art. 78): tipo de documento y si hubo cotejo biométrico RNEC (**sin** plantilla ni resultado crudo; RN-015) | string (enumeración) | `cedula+cotejo_rnec` |
### 2.4 Condicionales: ninguno.
**Excluidos:** número de documento, plantilla/resultado biométrico, fotografía, dirección.
### 2.5-2.7 Metadatos: iguales a mayoría de edad (`category`, emisor, `iat`/`exp`, `vct`, `cnf.jwk`, `status`). Vigencia ⚠ **por decidir**: la comprobación es puntual; se recomienda vigencia corta renovable con nueva orden SIGNO (valor propuesto, no normativo).

## 3 Codificación
SD-JWT VC (HAIP §6.1), ES256, `vct` = `https://<dominio-controlado>/types/identidad-verificada-notarial/v1` ⚠. Nombres de claim privados al tipo. **Divulgación selectiva (ARB_30):** todos los atributos de negocio (`given_names`, `family_names`, `birth_date`, `subject_ref`, `identification_means`, `verification_date`) → **MUST**; `verification_procedure`, `notary_code` → **MAY**; claims de protocolo → **MUST NOT**. mdoc diferido (ARB_02); W3C fuera del perfil base. Esquema JSON de sujeto: arquitectura §5.2.

## 4 Uso
Acreditar que una notaría identificó a la persona ante una entidad registrada con **finalidad declarada**. Ligada al dispositivo (SHALL). Obligaciones del verificador: RN-003 + **comprobar que el emisor está autorizado para este `vct`**. El verificador **no** debe tratarla como cédula ni como PID (RN-013) y debe pedir solo los atributos necesarios (RN-011).

## 5 Anclas y 6 Revocación
Como mayoría de edad. Motivos de revocación: error de emisión, **anulación o corrección del trámite en SIGNO** (orden de revocación idempotente), compromiso del vínculo con la clave, suspensión del emisor.

## 7 Cumplimiento y riesgos
- **Datos personales:** nombres y fecha son dato **común**; el cotejo biométrico es **sensible** (Ley 1581 arts. 5-6) y no se almacena en la credencial. Roles probables: notaría = **responsable**; operador del emisor = **encargado**; verificador = responsable propio (`firma-y-datos.md` §5).
- **Emisión:** solo desde orden de SIGNO de un solo uso, atómica y con titular esperado (RN-010; AC-03, AC-04).
- **Correlación:** `subject_ref` es el único identificador estable; debe ser por emisor/tipo y no reutilizarse entre contextos.
- **No** es esquema UE ni QEAA/PuB-EAA (RN-030). PKI no acreditada (D-012, RN-016).
## Pendientes
1. ⚠ Validación jurídica: alcance de la afirmación y reutilización del cotejo RNEC (convenio). 2. ⚠ Vigencia, `identification_means`, nombres de atributos (`subject_ref` vs `subjectRef`). 3. ⚠ Política de tratamiento y retención. 4. Pruebas: AC-03, AC-04, AC-05, AC-07, AC-13.
