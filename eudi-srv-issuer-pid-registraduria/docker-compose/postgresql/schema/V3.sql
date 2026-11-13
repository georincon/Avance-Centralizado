-- Titular de cada credencial emitida (el `username` autenticado: en la Registraduría, el número de cédula).
-- Permite revocar por titular. Las credenciales emitidas antes de esta versión quedan con subject NULL.
-- Idempotente: se puede ejecutar sobre una base existente.
ALTER TABLE issued_credential ADD COLUMN IF NOT EXISTS subject VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_issued_credential_subject ON issued_credential (subject);
