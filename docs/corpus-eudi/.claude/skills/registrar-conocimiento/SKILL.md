---
name: registrar-conocimiento
description: Registra en la memoria del proyecto (conocimiento/) las conclusiones de un trabajo: decisiones, hechos colombianos verificados, brechas UE→CO, reglas de negocio, términos y avance de sesión. Úsala al cerrar cualquier tarea con conclusiones o cuando el usuario diga "guarda", "registra" o "anota".
---

# Registrar conocimiento (el trabajo no termina hasta quedar escrito)

| Qué | Dónde | Formato |
|---|---|---|
| Decisión | `conocimiento/decisiones.md` | Fila `D-0NN | fecha | decisión | motivo | estado`. Si revisa otra, marca la anterior **revisada** y enlaza la nueva |
| Hecho jurídico/institucional colombiano | `conocimiento/colombia/` (archivo temático) | Solo `VERIFICADO`: norma+artículo+URL+fecha de acceso. Lo no verificado → backlog §7 del PLAN-MAESTRO |
| Investigación con fuentes | `conocimiento/investigacion/<ID>-<tema>.md` | Resumen · tabla de hallazgos · implicaciones · preguntas abiertas · fuentes |
| Brecha UE→CO | `conocimiento/colombia/matriz-brechas.md` | Fila: capa · principio UE (cita) · situación CO (fuente) · brecha · acción · estado |
| Regla de negocio | `conocimiento/colombia/reglas-negocio.md` | `RN-0NN` + origen + estado (propuesta/validada) |
| Término | `conocimiento/colombia/diccionario.md` | Término UE · sigla · uso en el piloto |
| Matriz/ficha UE | `conocimiento/ue/` | Con citas a HLR/artículos |
| Avance y retomada | `conocimiento/PLAN-MAESTRO.md` §8 | Fecha · qué se hizo · siguiente paso |

Reglas: no edites `conocimiento/proyecto/` (documentos del equipo); resume en otro archivo. Usa IDs consecutivos. Tras registrar, reconstruye el índice: `python3 -I scripts/construir_indice.py`.
Verifica la cita de cada hecho antes de escribirlo; no registres afirmaciones de memoria.
