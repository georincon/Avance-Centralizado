-- Revocación sin borrar: el registro de la credencial emitida se conserva, marcado con la fecha y el motivo.
-- Las credenciales no revocadas quedan con revoked_at NULL. Idempotente.
ALTER TABLE issued_credential ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE issued_credential ADD COLUMN IF NOT EXISTS revocation_reason VARCHAR(255);
