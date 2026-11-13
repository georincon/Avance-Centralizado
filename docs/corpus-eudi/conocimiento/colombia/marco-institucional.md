# Marco institucional y normativo colombiano (solo hechos verificados)

> Categoría: **NORMATIVO-CO**. Regla D-010: aquí solo entra lo `VERIFICADO` (leído en texto oficial). Fuente: `investigacion/B-identidad-institucional.md`
> (parte 1 y 2, acceso 2026-10-14). Lo no verificado está en `PLAN-MAESTRO.md` §7. **No es asesoría legal**; valida el equipo jurídico.

## 1. Servicios Ciudadanos Digitales (SCD) — VERIFICADO
| Hecho | Norma / artículo | Fuente |
|---|---|---|
| El Decreto 620/2020 subroga el Título 17, Parte 2, Libro 2 del Decreto 1078/2015 (lineamientos generales de los SCD) | Decreto 620/2020, art. 1 | Gestor Normativo (Función Pública) |
| **Articulador** = Agencia Nacional Digital (AND): "encargada de proveer y gestionar de manera integral los SCD, además de apoyar técnica y operativamente al MinTIC" | Decreto 1078/2015 art. 2.2.17.1.4 num. 2 (texto dado por Decreto 620/2020) | Gestor Normativo |
| SCD **base**: interoperabilidad, autenticación digital, carpeta ciudadana digital (más SCD especiales) | art. 2.2.17.2.1.1 | idem |
| La **interoperabilidad** la presta "de forma exclusiva" el Articulador; la carpeta ciudadana, el Articulador y prestadores conectados; la autenticación se rige por la Ley 527/1999 y los lineamientos de MinTIC | arts. 2.2.17.2.2.2 y 2.2.17.2.2.3 | idem |
| MinTIC fija la política y expide las Guías; obligaciones del Articulador, incl. "prestar los SCD cuando se requiera" | arts. 2.2.17.3.2, 2.2.17.4.1, 2.2.17.4.2 (nums. 2 y 4) | idem |
| Niveles de confianza de la **autenticación digital**: bajo / medio / alto / muy alto, definidos por MinTIC en la Guía de lineamientos (Anexo 1). Medio = NdG2, muy alto = NdG4 (ITU X.1254 / ISO 29115); **muy alto = mecanismos de la RNEC** (cédula digital/biometría); **alto = certificados digitales**; "otras credenciales" deben ser estudiadas por MinTIC; protocolo **OpenID Connect 1.0**; referencia a NIST 800-63 | Resolución MinTIC 2160 de 2020 (23-oct; D.O. 51.479) | normograma.mintic.gov.co/mintic/compilacion/docs/resolucion_mintic_2160_2020.htm |
| **Decreto 1263/2022 no desarrolla los SCD**: adiciona el Título 22 (art. 2.2.23.1.x, transformación digital pública); tecnologías emergentes siguen guías de MinTIC (art. 2.2.23.1.5) | Decreto 1263/2022 | funcionpublica.gov.co/eva/gestornormativo/norma.php?i=190206 |

**Lectura para el piloto:** el nivel "alto" de autenticación digital se apoya en **certificados digitales**, que es justo el terreno de AndesSCD; el nivel "muy alto" depende de la RNEC (sin acceso del equipo). Una credencial notarial sería una "otra credencial" que MinTIC tendría que estudiar para ese esquema (no hay norma que la encuadre hoy, por verificar en backlog).

## 2. Cédula de ciudadanía digital (RNEC) — VERIFICADO (parcial)
- La identificación por medios digitales (cédula digital y biometría) "se regirá por las disposiciones que expida la RNEC" (art. 2.2.17.1.3). La **cédula digital** es el "equivalente funcional de la cédula de ciudadanía, expedida por la RNEC" (art. 2.2.17.1.4 num. 3).
- **Resolución RNEC 9539 de 2020** (30-nov-2020; D.O. 51.523): fija **tarifas**; en sus considerandos cita Const. arts. 120 y 266, Decreto-Ley 1010/2000, Ley 1163/2007 y el art. 2.2.17.1.3 del Decreto 620/2020 (lo atribuye erróneamente al "Decreto 1413 de 2017").
- **Circular Externa RNEC 011 del 5-feb-2021** (no 2020): presenta oficialmente la cédula digital como "uno de los medios para la autenticación digital"; válida en ambos formatos. Leída a través de la Circular Única RNEC 1/2023 (compilación de Cancillería, nums. 13.2 y 18); la circular original no se leyó (sitio de la RNEC bloqueado desde este entorno).

## 3. Piloto estatal de credenciales verificables (2026) — SECUNDARIO (fuente oficial leída con resumen automático; releer el texto)
- Bootcamp de **6 días, 4-9 mayo 2026**, MinTIC con CDPI, en Bogotá. Casos: **Cédula Rural Digital** (Ministerio de Agricultura, con Finagro), **Permiso por Protección Temporal** (Migración Colombia), antecedentes digitales (Policía Nacional). La página de MinTIC cita además Cancillería y Banco Agrario sin precisar su papel. Fuentes: mintic.gov.co (noticia 437928, actualizada 11-may-2026), cdpi.dev/?p=1878, GovInsider.
- **No se halló respaldo** de que la AND participara en ese bootcamp ni de que se usaran estándares concretos (OpenID4VC, SD-JWT, mdoc). Cédula Rural presentada por MinTIC el 2-jul-2026 (noticia 439440).

## 4. Correcciones a la v1 (hipótesis sin verificar → resultado)
| Afirmación v1 | Resultado |
|---|---|
| "AND articula y presta los SCD (art. 2.2.17.1.4)" | **Parcialmente correcta**: es el *Articulador* ("proveer y gestionar", apoyo a MinTIC); presta los SCD "cuando se requiera"; la interoperabilidad es exclusiva suya |
| "Circular RNEC 011/2020" | **Corregida**: es del **5-feb-2021** |
| "Decreto 1263/2022 y 767/2022 desarrollan los SCD/Gobierno Digital" | 1263/2022 **no** desarrolla SCD (ver §1); 767/2022 no se verificó |
| "Reserva de ley sobre la identificación (Corte Constitucional)" | **No verificada** (backlog) |
| "MinTIC+AND+CDPI sprint de 5 días en junio" | **Corregida**: 6 días, 4-9 may-2026; participación de AND no respaldada (§3) |

## 5. Sin verificar (no usar como hecho; ver PLAN-MAESTRO §7)
Régimen vigente del notariado electrónico y convenio RNEC (ver `derecho-notarial.md` §4 y backlog) · (firma, ONAC, AndesSCD y datos personales: ver `firma-y-datos.md`) · reserva de ley y avances legislativos 2025-2026 · Estrategia Nacional Digital / CONPES.
