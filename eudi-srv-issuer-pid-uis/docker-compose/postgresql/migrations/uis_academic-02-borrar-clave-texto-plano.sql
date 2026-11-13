-- ============================================================
-- MIGRACIÓN 2/2: borrar las claves en texto plano (uis_academic)
-- ============================================================
-- IRREVERSIBLE. Ejecutar solo después de la migración 1, de desplegar la nueva versión de uis-issuer y de
-- comprobar que los estudiantes inician sesión. Tras este paso ya no se puede volver a la versión anterior
-- de la aplicación (esa versión lee clave_autenticacion).
--
-- Antes de ejecutarlo, hacer respaldo:
--   docker exec postgres-uis-academic pg_dump -U uis_academic uis_academic > uis_academic-antes-paso2.sql
-- (el respaldo contiene las claves en texto plano: guardarlo cifrado o borrarlo cuando ya no haga falta).

BEGIN;

-- Solo se borra donde ya existe el hash.
UPDATE datos_estudiantes
SET clave_autenticacion = NULL
WHERE clave_hash IS NOT NULL;

COMMIT;

SELECT count(*) AS claves_texto_plano_restantes FROM datos_estudiantes WHERE clave_autenticacion IS NOT NULL;
