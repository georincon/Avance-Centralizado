-- ============================================================
-- MIGRACIÓN 1/2: claves de estudiantes con hash bcrypt (uis_academic)
-- ============================================================
-- Qué hace: agrega datos_estudiantes.clave_hash y la rellena con bcrypt a partir de las claves en texto plano.
-- NO borra la clave en texto plano (clave_autenticacion): así se puede volver a la versión anterior de la
-- aplicación si algo sale mal. Es idempotente: se puede ejecutar más de una vez.
--
-- ORDEN DE DESPLIEGUE:
--   1. Ejecutar ESTE script en la base uis_academic.
--   2. Desplegar la nueva versión de uis-issuer (la nueva consulta lee clave_hash; sin la columna, el login falla).
--   3. Comprobar que los estudiantes inician sesión (en logs no debe aparecer "legacy plaintext password").
--   4. Ejecutar uis_academic-02-borrar-clave-texto-plano.sql (irreversible).
--
-- Uso (ejemplo con docker):
--   docker exec -i postgres-uis-academic psql -v ON_ERROR_STOP=1 -U uis_academic -d uis_academic \
--     < docker-compose/postgresql/migrations/uis_academic-01-clave-hash.sql
--
-- Requiere permiso para CREATE EXTENSION pgcrypto (en el contenedor, el usuario es superusuario).

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE datos_estudiantes ADD COLUMN IF NOT EXISTS clave_hash VARCHAR(100);

-- La columna heredada deja de ser obligatoria (las altas nuevas solo llevan hash).
ALTER TABLE datos_estudiantes ALTER COLUMN clave_autenticacion DROP NOT NULL;

UPDATE datos_estudiantes
SET clave_hash = crypt(clave_autenticacion, gen_salt('bf', 10))
WHERE clave_hash IS NULL
  AND clave_autenticacion IS NOT NULL;

ALTER TABLE datos_estudiantes DROP CONSTRAINT IF EXISTS ck_estudiante_clave;
ALTER TABLE datos_estudiantes
    ADD CONSTRAINT ck_estudiante_clave CHECK (clave_hash IS NOT NULL OR clave_autenticacion IS NOT NULL);

COMMIT;

-- Verificación: ningún estudiante debe quedar sin hash.
SELECT count(*) AS estudiantes_sin_hash FROM datos_estudiantes WHERE clave_hash IS NULL;
