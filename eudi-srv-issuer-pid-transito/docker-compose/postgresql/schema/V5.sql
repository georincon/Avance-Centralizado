-- Bloqueo de emisión por titular y avisos de revocación. Tablas propias: no tocan los datos de los ciudadanos.
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
