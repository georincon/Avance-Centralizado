# Reglas de negocio del ecosistema (propuesta local)

> Categoría: **PROPUESTA-LOCAL**, derivada de la arquitectura del piloto y del ARF. No son normativa. Cada regla indica origen y estado.
> Estado: `propuesta` → `validada` cuando el equipo (producto/jurídico/seguridad) la aprueba; registrar en `decisiones.md`.

## Confianza y gobernanza
- **RN-001 Registro de actores.** Todo emisor y todo verificador que opere en el ecosistema piloto está registrado ante el coordinador del piloto, con identidad, roles, tipos/atributos autorizados y certificados. *Origen:* arquitectura §9.1; ARF §6.3-6.4; CIR 2025/848 (referencia comparada). *Estado:* propuesta.
- **RN-002 Estar registrado no es estar autorizado a todo.** El registro técnico no equivale a permiso jurídico para tratar datos ni a facultad para emitir cualquier atributo. *Origen:* arquitectura §9.1. *Estado:* propuesta.
- **RN-003 Aceptación del verificador.** Se acepta solo si se cumplen **todas** las condiciones de §8.4 (solicitud vigente, formato permitido, firma válida, ancla y rol aceptados, tipo dentro de la facultad del emisor, vigencia, estado comprobado, posesión de clave, nonce/audiencia/transcript, transacción no consumida, atributos suficientes para la finalidad). Resultado `ACCEPTED | REJECTED | INDETERMINATE`; indeterminado no habilita operaciones sensibles. *Estado:* propuesta.
- **RN-004 Lista de confianza propia.** La lista colombiana de entidades se publica firmada, con secuencia y vigencia, y los consumidores rechazan *rollback*. No se denomina "EU Trusted List". *Origen:* arquitectura §9.1. *Estado:* propuesta.

## Credenciales y datos
- **RN-010 Fuente única y orden autorizada.** Los atributos de una credencial notarial provienen de SIGNO mediante una **orden de emisión de un solo uso** con titular esperado, tipo, versión, plazo y destinatario; el emisor no acepta atributos autoafirmados. *Origen:* arquitectura §5.2, §6.1. *Estado:* propuesta.
- **RN-011 Minimización y finalidad.** El verificador pide solo los atributos necesarios para su finalidad registrada (p. ej. `ageOver18` sin fecha de nacimiento ni cédula). *Origen:* ADR-15; ARF §6.6.3.3 (HLR sobre no solicitar más de lo registrado). *Estado:* propuesta.
- **RN-012 Control del usuario.** Las claves ligadas a credenciales no salen del hardware del dispositivo; el backend no firma presentaciones ni recibe claves privadas. *Origen:* ADR-03, ADR-07; ARF §6.5.3.6 solo respalda que la cuenta del Wallet Provider no necesita atributos reales y que sus datos no van en KA/WIA. *Estado:* propuesta.
- **RN-013 No es PID ni cédula.** Ninguna credencial notarial se presenta como cédula, PID europeo ni equivalente jurídico (ADR-12). *Estado:* propuesta (ver D-005).
- **RN-014 Consentimiento no suple competencia.** El consentimiento en pantalla no subsana falta de competencia, base jurídica o autorización para tratar datos sensibles. *Origen:* arquitectura §2.5. Requiere validación jurídica (ver `investigacion/C-confianza-datos.md`). *Estado:* propuesta.

- **RN-015 Sin plantilla biométrica.** La credencial no contiene ni deriva plantillas biométricas; a lo sumo, el resultado del cotejo y el procedimiento. La biometría es dato sensible (Ley 1581 art. 5) y seudonimizar o cifrar no la anonimiza (criterio SIC, 2026). *Origen:* `colombia/firma-y-datos.md` §3. *Estado:* propuesta.
- **RN-016 PKI no acreditada.** Mientras no haya ampliación de alcance ONAC, no se presenta la PKI de credenciales como «acreditada» ni como firma digital de la Ley 527 (D-012). *Estado:* propuesta.

## Estado y recuperación
- **RN-020 Cuatro dominios de estado** (credencial, instancia/atestaciones, certificado/clave, acreditación de entidad), cada uno con su responsable; un dominio no cambia el estado de otro sin regla explícita. *Origen:* arquitectura §8.3. *Estado:* propuesta.
- **RN-021 Fallo ≠ válido.** Si no se puede consultar el estado o la lista expiró fuera de la caché admisible ⇒ `INDETERMINATE`. Objetivos a medir: publicación <60 s, caché ≤5 min. *Estado:* propuesta.
- **RN-022 Pérdida del dispositivo.** Recuperación = revocar la vinculación anterior, registrar nueva instancia con claves nuevas y **reemitir**; restaurar respaldos no recupera facultad de firmar. *Origen:* ADR-16. *Estado:* propuesta.

## Seguridad y certificación
- **RN-030 No se declara lo no demostrado.** Ningún material afirma certificación europea, equivalencia jurídica, nivel de garantía ni disponibilidad sin evidencia (§13.4). *Estado:* propuesta.
- **RN-031 Aprobar pruebas no es certificar.** Los AC-01…23 y el FCAF aportan evidencia técnica, no sustituyen la evaluación exigida por el marco jurídico (ARF §7.5). *Estado:* propuesta.
- **RN-032 Defectos bloqueantes cerrados antes de datos reales** (los 6 de §9.2 del documento de arquitectura). *Estado:* propuesta.
