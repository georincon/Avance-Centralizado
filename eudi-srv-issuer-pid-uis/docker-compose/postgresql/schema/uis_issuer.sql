CREATE TABLE IF NOT EXISTS academic_credential (
    id                          BIGSERIAL PRIMARY KEY,
    credential_format           VARCHAR(255)  NOT NULL,
    credential_type             VARCHAR(255)  NOT NULL,
    issued_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    notification_id             VARCHAR(255),
    status_list_uri             VARCHAR(2048),
    status_list_index           BIGINT,
    client_status_list_uri      VARCHAR(2048) NOT NULL,
    client_status_list_index    BIGINT        NOT NULL,
    key_storage_status_list_uri   VARCHAR(2048),
    key_storage_status_list_index BIGINT,
    credential_identifier       UUID          NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_academic_credential_notification_id ON academic_credential (notification_id);
CREATE INDEX IF NOT EXISTS idx_academic_credential_expires_at ON academic_credential (expires_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_academic_credential_uuid ON academic_credential (credential_identifier);

-- Titular de cada credencial emitida (el `username` autenticado). Permite revocar por titular.
-- Las credenciales emitidas antes de esta versión quedan con subject NULL. Idempotente.
ALTER TABLE academic_credential ADD COLUMN IF NOT EXISTS subject VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_academic_credential_subject ON academic_credential (subject);

-- Revocación sin borrar: el registro de la credencial emitida se conserva, marcado con la fecha y el motivo.
-- Las credenciales no revocadas quedan con revoked_at NULL. Idempotente.
ALTER TABLE academic_credential ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE academic_credential ADD COLUMN IF NOT EXISTS revocation_reason VARCHAR(255);

-- Bloqueo de emisión por titular y avisos de revocación. Tablas propias: no tocan los datos de los estudiantes.
-- Idempotente.
CREATE TABLE IF NOT EXISTS blocked_subject (
    id           BIGSERIAL PRIMARY KEY,
    subject      VARCHAR(255) NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    blocked_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at  TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_blocked_subject_subject ON blocked_subject (subject);

CREATE TABLE IF NOT EXISTS revocation_notice (
    id                BIGSERIAL PRIMARY KEY,
    subject           VARCHAR(255)  NOT NULL,
    credential_types  VARCHAR(1000) NOT NULL,
    reason            VARCHAR(255)  NOT NULL,
    message           VARCHAR(2000) NOT NULL,
    revoked_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    deadline_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    channel           VARCHAR(100),
    notified_at       TIMESTAMP WITH TIME ZONE,
    detail            VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_revocation_notice_status ON revocation_notice (status);
