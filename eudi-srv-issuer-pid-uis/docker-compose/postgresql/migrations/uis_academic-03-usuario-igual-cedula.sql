-- ============================================================
-- MIGRACIÓN 3 (uis_academic): el usuario del estudiante pasa a ser su número de identificación
-- ============================================================
-- Todo el ecosistema identifica al estudiante por su cédula (o su documento): el username de Keycloak y el sujeto de sus
-- credenciales son `numero_identificacion`. `usuario_autenticacion` queda igual a él (se conserva la columna por compatibilidad).
-- Ya no hay contraseñas: la identidad se prueba con el PID (o con un código de activación del registro de la UIS).
-- Idempotente. Hacer respaldo antes (pg_dump).
BEGIN;

UPDATE academic_selections s
SET usuario_autenticacion = e.numero_identificacion
FROM datos_estudiantes e
WHERE s.numero_identificacion = e.numero_identificacion
  AND s.usuario_autenticacion <> e.numero_identificacion;

UPDATE datos_estudiantes
SET usuario_autenticacion = numero_identificacion
WHERE usuario_autenticacion <> numero_identificacion;

-- Sin contraseñas no hace falta que haya clave: se retira la restricción que la exigía.
ALTER TABLE datos_estudiantes DROP CONSTRAINT IF EXISTS ck_estudiante_clave;

COMMIT;
