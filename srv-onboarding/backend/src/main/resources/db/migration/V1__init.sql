-- Esquema inicial del flujo de onboarding.
-- Todos los identificadores son UUID generados por la aplicacion.

CREATE TABLE onboarding_session (
    id                      UUID PRIMARY KEY,
    status                  VARCHAR(40)  NOT NULL,
    decision                VARCHAR(40),
    phone_number            VARCHAR(20),
    email                   VARCHAR(255),
    contact_channel         VARCHAR(20),
    contact_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    consent_accepted        BOOLEAN      NOT NULL DEFAULT FALSE,
    consent_accepted_at     TIMESTAMPTZ,
    document_type           VARCHAR(40)  NOT NULL DEFAULT 'national-id',
    document_number         VARCHAR(30),
    first_name              VARCHAR(80),
    middle_name             VARCHAR(80),
    last_name               VARCHAR(80),
    second_last_name        VARCHAR(80),
    birth_date              DATE,
    gender                  VARCHAR(10),
    blood_type              VARCHAR(5),
    expedition_place        VARCHAR(120),
    issue_date              DATE,
    failure_reason          VARCHAR(255),
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    expires_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_session_status ON onboarding_session (status);
CREATE INDEX idx_session_document_number ON onboarding_session (document_number);

CREATE TABLE otp_challenge (
    id              UUID PRIMARY KEY,
    session_id      UUID         NOT NULL REFERENCES onboarding_session (id) ON DELETE CASCADE,
    channel         VARCHAR(20)  NOT NULL,
    destination     VARCHAR(255) NOT NULL,
    code_hash       VARCHAR(128) NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    max_attempts    INTEGER      NOT NULL DEFAULT 5,
    verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    verified_at     TIMESTAMPTZ
);

CREATE INDEX idx_otp_session ON otp_challenge (session_id);

CREATE TABLE document_capture (
    id                  UUID PRIMARY KEY,
    session_id          UUID         NOT NULL REFERENCES onboarding_session (id) ON DELETE CASCADE,
    side                VARCHAR(10)  NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    original_filename   VARCHAR(255),
    content_type        VARCHAR(100),
    size_bytes          BIGINT,
    sha256              VARCHAR(64),
    ocr_confidence      DOUBLE PRECISION,
    validation_status   VARCHAR(40),
    ocr_data            TEXT,
    created_at          TIMESTAMPTZ  NOT NULL,
    UNIQUE (session_id, side)
);

CREATE INDEX idx_document_session ON document_capture (session_id);

CREATE TABLE biometric_capture (
    id                  UUID PRIMARY KEY,
    session_id          UUID         NOT NULL REFERENCES onboarding_session (id) ON DELETE CASCADE,
    kind                VARCHAR(20)  NOT NULL DEFAULT 'selfie',
    storage_key         VARCHAR(500) NOT NULL,
    content_type        VARCHAR(100),
    size_bytes          BIGINT,
    provider            VARCHAR(40),
    liveness_status     VARCHAR(40),
    liveness_score      DOUBLE PRECISION,
    face_match_status   VARCHAR(40),
    face_match_score    DOUBLE PRECISION,
    details             TEXT,
    created_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_biometric_session ON biometric_capture (session_id);

CREATE TABLE verification_result (
    id              UUID PRIMARY KEY,
    session_id      UUID         NOT NULL REFERENCES onboarding_session (id) ON DELETE CASCADE,
    decision        VARCHAR(40)  NOT NULL,
    reason          VARCHAR(500),
    details         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_verification_session ON verification_result (session_id);

CREATE TABLE audit_event (
    id              UUID PRIMARY KEY,
    session_id      UUID REFERENCES onboarding_session (id) ON DELETE SET NULL,
    type            VARCHAR(60)  NOT NULL,
    message         VARCHAR(500),
    metadata        TEXT,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_session ON audit_event (session_id);
CREATE INDEX idx_audit_type ON audit_event (type);
